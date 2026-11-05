package de.entdecker.hannover

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.DashPathEffect
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.os.Handler
import android.os.Looper
import android.util.JsonReader
import android.util.JsonToken
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/**
 * Mapa de Hannover sin conexión. Se descargan los datos de OpenStreetMap
 * (calles, senderos, agua, parques y bosques) y la app los dibuja.
 */
object OfflineMap {
    const val WATER = 0
    const val PARK = 1
    const val FOREST = 2
    const val GRASS = 3
    const val RIVER = 10
    const val STREAM = 11
    const val RAIL = 12
    const val PATH = 20
    const val MINOR = 21
    const val SECONDARY = 22
    const val MAJOR = 23
    const val KIND_COUNT = 24

    private const val FILE = "hannover_map.bin"
    private const val MAGIC = 0x4D504831 // "MPH1" (sin nombres)
    private const val MAGIC2 = 0x4D504832 // "MPH2" (con nombres de calles)
    const val CELL = 0.0002f

    private val ENDPOINTS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter"
    )

    private val BBOX = "${Geo.HAN_S},${Geo.HAN_W},${Geo.HAN_N},${Geo.HAN_E}"

    private val STEPS = listOf(
        "zonas verdes y agua" to """
            [out:json][timeout:180];
            (
              way["natural"~"^(water|wood|scrub|grassland)$"]($BBOX);
              relation["natural"~"^(water|wood)$"]($BBOX);
              way["waterway"="riverbank"]($BBOX);
              way["leisure"~"^(park|garden|playground)$"]($BBOX);
              relation["leisure"~"^(park|garden)$"]($BBOX);
              way["landuse"~"^(forest|grass|meadow|recreation_ground|cemetery|allotments|village_green)$"]($BBOX);
              relation["landuse"~"^(forest|grass|meadow)$"]($BBOX);
            );
            out geom qt;
        """.trimIndent(),
        "calles y vías" to """
            [out:json][timeout:180];
            (
              way["highway"~"^(motorway|motorway_link|trunk|trunk_link|primary|primary_link|secondary|secondary_link|tertiary|tertiary_link|unclassified|residential|living_street|pedestrian)$"]($BBOX);
              way["railway"~"^(rail|light_rail|tram)$"]($BBOX);
              way["waterway"~"^(river|canal|stream)$"]($BBOX);
            );
            out geom qt;
        """.trimIndent(),
        "senderos" to """
            [out:json][timeout:180];
            (
              way["highway"~"^(path|cycleway|track|bridleway|steps)$"]($BBOX);
              way["highway"="footway"]["footway"!~"^(sidewalk|crossing)$"]($BBOX);
            );
            out geom qt;
        """.trimIndent()
    )

    class Data(
        val kinds: ByteArray,
        val names: Array<String?>,
        val starts: IntArray,
        val counts: IntArray,
        val xs: FloatArray,
        val ys: FloatArray,
        val minX: FloatArray, val minY: FloatArray, val maxX: FloatArray, val maxY: FloatArray,
        val grid: HashMap<Int, IntArray>
    ) {
        val size get() = kinds.size
        private val stamp = IntArray(kinds.size)
        private var frame = 0

        /** Llama a [body] con cada elemento que cae en el rectángulo (sin repetir). Solo hilo principal. */
        inline fun forEachIn(x0: Float, y0: Float, x1: Float, y1: Float, body: (Int) -> Unit) {
            val f = nextFrame()
            val gx0 = floor(x0 / CELL).toInt(); val gx1 = floor(x1 / CELL).toInt()
            val gy0 = floor(y0 / CELL).toInt(); val gy1 = floor(y1 / CELL).toInt()
            if ((gx1 - gx0).toLong() * (gy1 - gy0) > 40_000) return // demasiado lejos para dibujar
            for (gx in gx0..gx1) for (gy in gy0..gy1) {
                val list = grid[key(gx, gy)] ?: continue
                for (i in list) {
                    if (!mark(i, f)) continue
                    if (maxX[i] < x0 || minX[i] > x1 || maxY[i] < y0 || minY[i] > y1) continue
                    body(i)
                }
            }
        }

        fun nextFrame(): Int { frame++; return frame }
        fun mark(i: Int, f: Int): Boolean { if (stamp[i] == f) return false; stamp[i] = f; return true }
    }

    fun key(gx: Int, gy: Int): Int = gx * 100_000 + gy

    /** Coordenadas Mercator en radianes (x = longitud, y = crece hacia el norte). */
    fun mx(lon: Double): Float = (lon * PI / 180.0).toFloat()
    fun my(lat: Double): Float = ln(tan(PI / 4 + lat * PI / 360.0)).toFloat()

    @Volatile var data: Data? = null
        private set
    @Volatile var downloading = false
        private set

    private lateinit var app: Context
    private val main = Handler(Looper.getMainLooper())
    var onChange: (() -> Unit)? = null

    var preferOffline: Boolean
        get() = app.getSharedPreferences("offline", Context.MODE_PRIVATE).getBoolean("prefer", false)
        set(v) { app.getSharedPreferences("offline", Context.MODE_PRIVATE).edit().putBoolean("prefer", v).apply() }

    val ready: Boolean get() = data != null

    /** true si el mapa descargado es de antes y no trae nombres de calles. */
    val missingNames: Boolean get() = data?.names?.all { it == null } ?: false

    fun fileSizeMb(): Double = File(app.filesDir, FILE).length() / 1_048_576.0

    fun init(ctx: Context) {
        app = ctx.applicationContext
        Thread {
            val d = try { load() } catch (e: Exception) { null }
            if (d != null) main.post { data = d; onChange?.invoke() }
        }.start()
    }

    // ------------------------------------------------------------ descarga

    /** Descarga en segundo plano. [progress] y [done] se llaman en el hilo principal. */
    fun download(progress: (String) -> Unit, done: (Boolean, String) -> Unit) {
        if (downloading) return
        downloading = true
        Thread {
            try {
                val tmp = File(app.filesDir, "$FILE.tmp")
                var features = 0
                DataOutputStream(BufferedOutputStream(FileOutputStream(tmp))).use { out ->
                    out.writeInt(MAGIC2)
                    STEPS.forEachIndexed { i, (label, query) ->
                        main.post { progress("Descargando $label\n(${i + 1} de ${STEPS.size})") }
                        val chunk = fetchStep(query)
                        out.write(chunk.bytes)
                        features += chunk.count
                    }
                }
                if (features < 100) throw IOException("Respuesta vacía")
                val final = File(app.filesDir, FILE)
                if (!tmp.renameTo(final)) {
                    tmp.copyTo(final, overwrite = true); tmp.delete()
                }
                val d = load()
                main.post {
                    data = d
                    downloading = false
                    onChange?.invoke()
                    done(true, "ᛟ Mapa de Hannover descargado ᛟ\n${fmt(fileSizeMb(), 1)} MB · ya funciona sin internet")
                }
            } catch (e: Exception) {
                File(app.filesDir, "$FILE.tmp").delete()
                main.post {
                    downloading = false
                    done(false, "No se pudo descargar el mapa.\nRevisa tu conexión e inténtalo otra vez.")
                }
            }
        }.start()
    }

    private class Chunk(val bytes: ByteArray, val count: Int)

    private fun fetchStep(query: String): Chunk {
        var last: Exception? = null
        for (endpoint in ENDPOINTS) {
            try {
                return fetchFrom(endpoint, query)
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IOException("Sin servidor")
    }

    private fun fetchFrom(endpoint: String, query: String): Chunk {
        val con = URL(endpoint).openConnection() as HttpURLConnection
        try {
            con.requestMethod = "POST"
            con.doOutput = true
            con.connectTimeout = 20_000
            con.readTimeout = 200_000
            con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            con.setRequestProperty("User-Agent", "MapheimHannover/1.0 (Android)")
            con.outputStream.use { it.write(("data=" + URLEncoder.encode(query, "UTF-8")).toByteArray()) }
            if (con.responseCode != 200) throw IOException("Overpass ${con.responseCode}")
            val buf = ByteArrayOutputStream()
            val out = DataOutputStream(buf)
            var count = 0
            JsonReader(InputStreamReader(BufferedInputStream(con.inputStream), Charsets.UTF_8)).use { r ->
                r.beginObject()
                while (r.hasNext()) {
                    if (r.nextName() == "elements") {
                        r.beginArray()
                        while (r.hasNext()) count += readElement(r, out)
                        r.endArray()
                    } else r.skipValue()
                }
                r.endObject()
            }
            out.flush()
            return Chunk(buf.toByteArray(), count)
        } finally {
            con.disconnect()
        }
    }

    private class LongList {
        var a = LongArray(64); var n = 0
        fun add(v: Long) { if (n == a.size) a = a.copyOf(n * 2); a[n++] = v }
        fun toArray() = a.copyOf(n)
    }

    private fun pack(lat: Double, lon: Double): Long =
        (Math.round(lat * 1e7) shl 32) or (Math.round(lon * 1e7) and 0xffffffffL)

    private fun readGeom(r: JsonReader): LongArray {
        val list = LongList()
        r.beginArray()
        while (r.hasNext()) {
            if (r.peek() == JsonToken.NULL) { r.nextNull(); continue }
            var lat = Double.NaN; var lon = Double.NaN
            r.beginObject()
            while (r.hasNext()) {
                when (r.nextName()) {
                    "lat" -> lat = r.nextDouble()
                    "lon" -> lon = r.nextDouble()
                    else -> r.skipValue()
                }
            }
            r.endObject()
            if (!lat.isNaN() && !lon.isNaN()) list.add(pack(lat, lon))
        }
        r.endArray()
        return list.toArray()
    }

    /** Lee un elemento de Overpass y escribe sus figuras. Devuelve cuántas escribió. */
    private fun readElement(r: JsonReader, out: DataOutputStream): Int {
        var type = ""
        val tags = HashMap<String, String>()
        var geom: LongArray? = null
        val outers = ArrayList<LongArray>()
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "type" -> type = r.nextString()
                "tags" -> {
                    r.beginObject()
                    while (r.hasNext()) {
                        val k = r.nextName()
                        if (r.peek() == JsonToken.STRING) tags[k] = r.nextString() else r.skipValue()
                    }
                    r.endObject()
                }
                "geometry" -> geom = readGeom(r)
                "members" -> {
                    r.beginArray()
                    while (r.hasNext()) {
                        var mType = ""; var role = ""; var g: LongArray? = null
                        r.beginObject()
                        while (r.hasNext()) {
                            when (r.nextName()) {
                                "type" -> mType = r.nextString()
                                "role" -> role = r.nextString()
                                "geometry" -> g = readGeom(r)
                                else -> r.skipValue()
                            }
                        }
                        r.endObject()
                        if (mType == "way" && (role == "outer" || role == "") && g != null && g.size >= 2) outers.add(g)
                    }
                    r.endArray()
                }
                else -> r.skipValue()
            }
        }
        r.endObject()

        val kind = classify(tags)
        if (kind < 0) return 0
        val isArea = kind < RIVER
        val named = kind == RIVER || kind == PATH || kind == MINOR || kind == SECONDARY || kind == MAJOR
        val name = if (named) (tags["name"] ?: "").take(60) else ""
        var written = 0
        if (type == "relation") {
            if (!isArea) return 0
            for (ring in rings(outers)) { write(out, kind, ring, ""); written++ }
        } else {
            val g = geom ?: return 0
            if (g.size < 2) return 0
            if (isArea) {
                if (g.size < 4 || g.first() != g.last()) return 0
            }
            write(out, kind, g, name); written++
        }
        return written
    }

    private fun classify(t: Map<String, String>): Int {
        t["highway"]?.let { hw ->
            return when (hw) {
                "motorway", "motorway_link", "trunk", "trunk_link", "primary", "primary_link" -> MAJOR
                "secondary", "secondary_link", "tertiary", "tertiary_link" -> SECONDARY
                "unclassified", "residential", "living_street", "pedestrian" -> MINOR
                "footway", "path", "cycleway", "track", "bridleway", "steps" -> PATH
                else -> -1
            }
        }
        t["railway"]?.let { if (it == "rail" || it == "light_rail" || it == "tram") return RAIL }
        t["waterway"]?.let {
            return when (it) {
                "river", "canal" -> RIVER
                "stream" -> STREAM
                "riverbank" -> WATER
                else -> -1
            }
        }
        when (t["natural"]) {
            "water" -> return WATER
            "wood", "scrub" -> return FOREST
            "grassland" -> return GRASS
        }
        when (t["landuse"]) {
            "forest" -> return FOREST
            "grass", "meadow", "recreation_ground", "cemetery", "allotments", "village_green" -> return GRASS
        }
        when (t["leisure"]) {
            "park", "garden", "playground" -> return PARK
        }
        return -1
    }

    /** Une los tramos exteriores de una relación en anillos cerrados. */
    private fun rings(segs: List<LongArray>): List<LongArray> {
        val pool = ArrayList(segs)
        val out = ArrayList<LongArray>()
        while (pool.isNotEmpty()) {
            val ring = LongList()
            for (v in pool.removeAt(pool.size - 1)) ring.add(v)
            var guard = 0
            while (ring.a[0] != ring.a[ring.n - 1] && guard++ < 5000) {
                val end = ring.a[ring.n - 1]
                val i = pool.indexOfFirst { it.first() == end || it.last() == end }
                if (i < 0) break
                val s = pool.removeAt(i)
                if (s.first() == end) for (k in 1 until s.size) ring.add(s[k])
                else for (k in s.size - 2 downTo 0) ring.add(s[k])
            }
            if (ring.n >= 4 && ring.a[0] == ring.a[ring.n - 1]) out.add(ring.toArray())
        }
        return out
    }

    private fun write(out: DataOutputStream, kind: Int, pts: LongArray, name: String) {
        out.writeByte(kind)
        out.writeUTF(name)
        out.writeInt(pts.size)
        for (p in pts) {
            out.writeInt((p shr 32).toInt())
            out.writeInt(p.toInt())
        }
    }

    // ------------------------------------------------------------ lectura

    private class FloatList {
        var a = FloatArray(1024); var n = 0
        fun add(v: Float) { if (n == a.size) a = a.copyOf(n * 2); a[n++] = v }
    }

    private fun load(): Data? {
        val f = File(app.filesDir, FILE)
        if (!f.exists()) return null
        val kinds = ArrayList<Byte>()
        val names = ArrayList<String?>()
        val starts = ArrayList<Int>()
        val counts = ArrayList<Int>()
        val xs = FloatList()
        val ys = FloatList()
        DataInputStream(BufferedInputStream(FileInputStream(f), 1 shl 16)).use { inp ->
            val magic = inp.readInt()
            if (magic != MAGIC && magic != MAGIC2) return null
            val withNames = magic == MAGIC2
            while (true) {
                val kind = try { inp.readByte() } catch (e: EOFException) { break }
                val name = if (withNames) inp.readUTF() else ""
                val n = inp.readInt()
                kinds.add(kind); names.add(name.ifEmpty { null }); starts.add(xs.n); counts.add(n)
                for (i in 0 until n) {
                    val lat = inp.readInt() / 1e7
                    val lon = inp.readInt() / 1e7
                    xs.add(mx(lon)); ys.add(my(lat))
                }
            }
        }
        val count = kinds.size
        val minX = FloatArray(count); val minY = FloatArray(count)
        val maxX = FloatArray(count); val maxY = FloatArray(count)
        val cells = HashMap<Int, MutableList<Int>>()
        for (i in 0 until count) {
            val s = starts[i]; val n = counts[i]
            var x0 = Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
            for (k in s until s + n) {
                x0 = min(x0, xs.a[k]); x1 = max(x1, xs.a[k])
                y0 = min(y0, ys.a[k]); y1 = max(y1, ys.a[k])
            }
            minX[i] = x0; minY[i] = y0; maxX[i] = x1; maxY[i] = y1
            for (gx in floor(x0 / CELL).toInt()..floor(x1 / CELL).toInt()) {
                for (gy in floor(y0 / CELL).toInt()..floor(y1 / CELL).toInt()) {
                    cells.getOrPut(key(gx, gy)) { ArrayList() }.add(i)
                }
            }
        }
        val grid = HashMap<Int, IntArray>(cells.size * 2)
        for ((k, v) in cells) grid[k] = v.toIntArray()
        return Data(
            kinds.toByteArray(), names.toTypedArray(), starts.toIntArray(), counts.toIntArray(),
            xs.a.copyOf(xs.n), ys.a.copyOf(ys.n), minX, minY, maxX, maxY, grid
        )
    }

    // ------------------------------------------------------------ colores

    /** Colores base (estilo OpenStreetMap) teñidos con el estilo de mapa equipado. */
    fun palette(m: ColorMatrix): IntArray {
        val a = m.array
        fun t(r: Int, g: Int, b: Int): Int {
            fun ch(row: Int) = (a[row * 5] * r + a[row * 5 + 1] * g + a[row * 5 + 2] * b + a[row * 5 + 4])
                .toInt().coerceIn(0, 255)
            return Color.rgb(ch(0), ch(1), ch(2))
        }
        return intArrayOf(
            t(242, 239, 233), // 0 tierra
            t(170, 211, 223), // 1 agua
            t(200, 230, 180), // 2 parque
            t(173, 209, 158), // 3 bosque
            t(205, 235, 176), // 4 pasto
            t(255, 255, 255), // 5 calle
            t(247, 250, 191), // 6 secundaria
            t(252, 214, 164), // 7 principal
            t(190, 180, 170), // 8 borde de calle
            t(150, 120, 90),  // 9 sendero
            t(130, 130, 130)  // 10 tren
        )
    }
}

/** Dibuja el mapa descargado cuando no hay internet (o si se prefiere). */
class OfflineMapOverlay(private val density: Float, labelFace: android.graphics.Typeface?) : Overlay() {
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = labelFace
    }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = labelFace
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        color = Color.argb(225, 248, 244, 236)
    }
    private val placed = ArrayList<android.graphics.RectF>()
    private val placedNames = HashMap<String, MutableList<Float>>()
    private var lx = 0f; private var ly = 0f; private var la = 0f; private var llen = 0f
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val dash = DashPathEffect(floatArrayOf(4f * density, 3f * density), 0f)
    private val path = Path()
    private val pt = Point()
    private val gp = GeoPoint(0.0, 0.0)
    private var styleId = ""
    private var colors = IntArray(11)
    private val buckets = Array(OfflineMap.KIND_COUNT) { IntArray(256) }
    private val sizes = IntArray(OfflineMap.KIND_COUNT)

    private var tx0 = 0f; private var ty0 = 0f; private var sx = 1f; private var sy = 1f
    private var gx0 = 0f; private var gy0 = 0f

    private fun add(kind: Int, i: Int) {
        var b = buckets[kind]
        if (sizes[kind] == b.size) { b = b.copyOf(b.size * 2); buckets[kind] = b }
        b[sizes[kind]++] = i
    }

    override fun draw(c: Canvas, mv: MapView, shadow: Boolean) {
        if (shadow) return
        val d = OfflineMap.data ?: return
        val id = Shop.equippedId(ShopCat.MAP)
        if (id != styleId) { styleId = id; colors = OfflineMap.palette(Shop.mapMatrix(id)) }
        c.drawColor(colors[0])

        val pj = mv.projection
        val bb = pj.boundingBox
        gp.setCoords(bb.latNorth, bb.lonWest); pj.toPixels(gp, pt)
        tx0 = pt.x.toFloat(); ty0 = pt.y.toFloat()
        gp.setCoords(bb.latSouth, bb.lonEast); pj.toPixels(gp, pt)
        val tx1 = pt.x.toFloat(); val ty1 = pt.y.toFloat()
        gx0 = OfflineMap.mx(bb.lonWest); gy0 = OfflineMap.my(bb.latNorth)
        val gx1 = OfflineMap.mx(bb.lonEast); val gy1 = OfflineMap.my(bb.latSouth)
        if (gx1 == gx0 || gy1 == gy0) return
        sx = (tx1 - tx0) / (gx1 - gx0)
        sy = (ty1 - ty0) / (gy1 - gy0)

        val z = mv.zoomLevelDouble
        val padX = (gx1 - gx0) * 0.08f
        val padY = (gy0 - gy1) * 0.08f
        java.util.Arrays.fill(sizes, 0)
        d.forEachIn(gx0 - padX, gy1 - padY, gx1 + padX, gy0 + padY) { i ->
            val k = d.kinds[i].toInt()
            val show = when (k) {
                OfflineMap.PATH -> z >= 15.0
                OfflineMap.MINOR -> z >= 13.5
                OfflineMap.STREAM -> z >= 14.0
                OfflineMap.SECONDARY -> z >= 11.5
                else -> true
            }
            if (show) add(k, i)
        }

        fun w(meters: Float, minDp: Float) = max(minDp * density, pj.metersToPixels(meters))

        // áreas
        areas(c, d, OfflineMap.GRASS, colors[4])
        areas(c, d, OfflineMap.PARK, colors[2])
        areas(c, d, OfflineMap.FOREST, colors[3])
        areas(c, d, OfflineMap.WATER, colors[1])

        // agua y vías
        lines(c, d, OfflineMap.STREAM, colors[1], w(3f, 1f))
        lines(c, d, OfflineMap.RIVER, colors[1], w(22f, 2f))
        line.pathEffect = dash
        lines(c, d, OfflineMap.PATH, colors[9], w(1.5f, 1f))
        line.pathEffect = null
        lines(c, d, OfflineMap.RAIL, colors[10], w(2.5f, 1f))

        val minorW = w(8f, 1.3f); val secW = w(12f, 2f); val majW = w(16f, 2.6f)
        val casing = max(1.2f * density, minorW * 0.25f)
        if (z >= 14.0) {
            lines(c, d, OfflineMap.MINOR, colors[8], minorW + casing)
            lines(c, d, OfflineMap.SECONDARY, colors[8], secW + casing)
            lines(c, d, OfflineMap.MAJOR, colors[8], majW + casing)
        }
        lines(c, d, OfflineMap.MINOR, colors[5], minorW)
        lines(c, d, OfflineMap.SECONDARY, colors[6], secW)
        lines(c, d, OfflineMap.MAJOR, colors[7], majW)

        if (z >= 14.0) labels(c, d, z, mv.width.toFloat(), mv.height.toFloat())
    }

    /** Punto medio de la calle en pantalla, con su ángulo y largo. */
    private fun midpoint(d: OfflineMap.Data, i: Int) {
        val s = d.starts[i]; val n = d.counts[i]
        var total = 0f
        for (k in s + 1 until s + n) {
            val dx = (d.xs[k] - d.xs[k - 1]) * sx; val dy = (d.ys[k] - d.ys[k - 1]) * sy
            total += kotlin.math.sqrt(dx * dx + dy * dy)
        }
        llen = total
        var acc = 0f
        for (k in s + 1 until s + n) {
            val ax = tx0 + (d.xs[k - 1] - gx0) * sx; val ay = ty0 + (d.ys[k - 1] - gy0) * sy
            val bx = tx0 + (d.xs[k] - gx0) * sx; val by = ty0 + (d.ys[k] - gy0) * sy
            val seg = kotlin.math.sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay))
            if (acc + seg >= total / 2 || k == s + n - 1) {
                val t = if (seg > 0f) ((total / 2 - acc) / seg).coerceIn(0f, 1f) else 0f
                lx = ax + (bx - ax) * t; ly = ay + (by - ay) * t
                var deg = Math.toDegrees(kotlin.math.atan2((by - ay).toDouble(), (bx - ax).toDouble())).toFloat()
                if (deg > 90f) deg -= 180f else if (deg < -90f) deg += 180f
                la = deg
                return
            }
            acc += seg
        }
        lx = tx0 + (d.xs[s] - gx0) * sx; ly = ty0 + (d.ys[s] - gy0) * sy; la = 0f
    }

    private fun labels(c: Canvas, d: OfflineMap.Data, z: Double, w: Float, h: Float) {
        placed.clear()
        placedNames.clear()
        var count = 0
        val order = intArrayOf(OfflineMap.MAJOR, OfflineMap.SECONDARY, OfflineMap.RIVER, OfflineMap.MINOR, OfflineMap.PATH)
        for (kind in order) {
            val minZ = when (kind) {
                OfflineMap.MAJOR, OfflineMap.SECONDARY -> 14.5
                OfflineMap.RIVER -> 14.0
                OfflineMap.MINOR -> 16.0
                else -> 17.0
            }
            if (z < minZ) continue
            val big = kind == OfflineMap.MAJOR || kind == OfflineMap.SECONDARY
            text.textSize = (if (big) 11.5f else 10.5f) * density
            halo.textSize = text.textSize
            halo.strokeWidth = 3.5f * density
            text.color = if (kind == OfflineMap.RIVER) Color.rgb(40, 85, 120) else Color.rgb(70, 50, 32)
            val th = text.textSize
            val b = buckets[kind]
            for (j in 0 until sizes[kind]) {
                if (count >= 90) return
                val i = b[j]
                val name = d.names[i] ?: continue
                midpoint(d, i)
                if (lx < -20 || ly < -20 || lx > w + 20 || ly > h + 20) continue
                val tw = text.measureText(name)
                if (llen < tw * 1.15f) continue
                // no repetir la misma calle muy cerca
                val prev = placedNames[name]
                if (prev != null) {
                    var near = false
                    var k = 0
                    while (k < prev.size) {
                        val dx = prev[k] - lx; val dy = prev[k + 1] - ly
                        if (dx * dx + dy * dy < (260f * density) * (260f * density)) { near = true; break }
                        k += 2
                    }
                    if (near) continue
                }
                val rad = Math.toRadians(la.toDouble())
                val cs = kotlin.math.abs(kotlin.math.cos(rad)).toFloat()
                val sn = kotlin.math.abs(kotlin.math.sin(rad)).toFloat()
                val bw = cs * tw + sn * th; val bh = sn * tw + cs * th
                val r = android.graphics.RectF(lx - bw / 2, ly - bh / 2, lx + bw / 2, ly + bh / 2)
                if (placed.any { android.graphics.RectF.intersects(it, r) }) continue
                placed.add(r)
                placedNames.getOrPut(name) { ArrayList() }.apply { add(lx); add(ly) }
                c.save()
                c.rotate(la, lx, ly)
                c.drawText(name, lx, ly + th * 0.35f, halo)
                c.drawText(name, lx, ly + th * 0.35f, text)
                c.restore()
                count++
            }
        }
    }

    private fun build(d: OfflineMap.Data, i: Int, close: Boolean) {
        path.rewind()
        val s = d.starts[i]
        val n = d.counts[i]
        path.moveTo(tx0 + (d.xs[s] - gx0) * sx, ty0 + (d.ys[s] - gy0) * sy)
        for (k in s + 1 until s + n) path.lineTo(tx0 + (d.xs[k] - gx0) * sx, ty0 + (d.ys[k] - gy0) * sy)
        if (close) path.close()
    }

    private fun areas(c: Canvas, d: OfflineMap.Data, kind: Int, color: Int) {
        fill.color = color
        val b = buckets[kind]
        for (j in 0 until sizes[kind]) { build(d, b[j], true); c.drawPath(path, fill) }
    }

    private fun lines(c: Canvas, d: OfflineMap.Data, kind: Int, color: Int, width: Float) {
        line.color = color
        line.strokeWidth = width
        val b = buckets[kind]
        for (j in 0 until sizes[kind]) { build(d, b[j], false); c.drawPath(path, line) }
    }
}
