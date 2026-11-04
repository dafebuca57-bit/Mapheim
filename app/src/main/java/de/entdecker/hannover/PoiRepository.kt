package de.entdecker.hannover

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** Descarga lugares, parques y bosques de Hannover desde OpenStreetMap (Overpass). */
object PoiRepository {

    private const val CACHE = "overpass_hannover.json"
    private const val ENDPOINT = "https://overpass-api.de/api/interpreter"

    private val BBOX = "${Geo.HAN_S},${Geo.HAN_W},${Geo.HAN_N},${Geo.HAN_E}"

    private val QUERY = """
        [out:json][timeout:120];
        (
          node["tourism"~"^(attraction|museum|viewpoint|zoo|gallery|artwork)$"]["name"]($BBOX);
          node["historic"~"^(monument|castle|church|building|city_gate|tower|ruins|manor|memorial)$"]["name"]["memorial"!="stolperstein"]($BBOX);
        )->.n;
        (
          way["tourism"~"^(attraction|museum|zoo|gallery)$"]["name"]($BBOX);
          way["historic"~"^(monument|castle|church|building|city_gate|tower|ruins|manor)$"]["name"]($BBOX);
          way["leisure"~"^(park|garden|nature_reserve)$"]["name"]($BBOX);
          relation["leisure"~"^(park|garden|nature_reserve)$"]["name"]($BBOX);
          way["landuse"="forest"]["name"]($BBOX);
          relation["landuse"="forest"]["name"]($BBOX);
          way["natural"="wood"]["name"]($BBOX);
          relation["natural"="wood"]["name"]($BBOX);
        )->.a;
        .n out body;
        .a out tags bb;
    """.trimIndent()

    fun hasCache(ctx: Context): Boolean = File(ctx.filesDir, CACHE).exists()

    fun loadAll(ctx: Context): List<Poi> {
        val file = File(ctx.filesDir, CACHE)
        val osm = if (file.exists()) {
            try { parse(file.readText()) } catch (e: Exception) { emptyList() }
        } else emptyList()
        return merge(Curated.hannover, osm) + Curated.germany
    }

    /** Descarga y guarda. Devuelve cuántos lugares trajo. */
    fun fetch(ctx: Context): Int {
        val con = URL(ENDPOINT).openConnection() as HttpURLConnection
        try {
            con.requestMethod = "POST"
            con.doOutput = true
            con.connectTimeout = 20_000
            con.readTimeout = 150_000
            con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            con.setRequestProperty("User-Agent", "MapheimHannover/1.0 (Android)")
            con.outputStream.use { it.write(("data=" + URLEncoder.encode(QUERY, "UTF-8")).toByteArray()) }
            if (con.responseCode != 200) throw IOException("Overpass respondió ${con.responseCode}")
            val text = con.inputStream.bufferedReader().use { it.readText() }
            val parsed = parse(text) // valida antes de guardar
            File(ctx.filesDir, CACHE).writeText(text)
            return parsed.size
        } finally {
            con.disconnect()
        }
    }

    private fun norm(s: String) = s.lowercase(Locale.GERMAN).trim()

    private class Acc(
        val id: String, val name: String, val cat: Cat,
        var s: Double, var w: Double, var n: Double, var e: Double, var area: Boolean
    ) {
        val lat get() = (s + n) / 2
        val lon get() = (w + e) / 2
    }

    fun parse(text: String): List<Poi> {
        val arr = JSONObject(text).optJSONArray("elements") ?: return emptyList()
        val groups = LinkedHashMap<String, MutableList<Acc>>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val tags = o.optJSONObject("tags") ?: continue
            val name = tags.optString("name", "").trim()
            if (name.isEmpty()) continue
            if (tags.optString("memorial") == "stolperstein") continue
            if (tags.optString("access") == "private") continue

            val leisure = tags.optString("leisure")
            val cat = when {
                leisure == "park" || leisure == "garden" || leisure == "nature_reserve" -> Cat.PARK
                tags.optString("landuse") == "forest" || tags.optString("natural") == "wood" -> Cat.FOREST
                else -> Cat.SIGHT
            }
            val type = o.optString("type")
            val id = "osm_" + type.take(1) + o.optLong("id")
            val acc: Acc = if (type == "node") {
                val la = o.optDouble("lat"); val lo = o.optDouble("lon")
                if (la.isNaN() || lo.isNaN()) continue
                Acc(id, name, cat, la, lo, la, lo, false)
            } else {
                val b = o.optJSONObject("bounds") ?: continue
                // margen: ~40 m para edificios, ~15 m para parques
                val padLat = if (cat == Cat.SIGHT) 0.00036 else 0.00014
                val padLon = if (cat == Cat.SIGHT) 0.00058 else 0.00022
                Acc(
                    id, name, cat,
                    b.getDouble("minlat") - padLat, b.getDouble("minlon") - padLon,
                    b.getDouble("maxlat") + padLat, b.getDouble("maxlon") + padLon, true
                )
            }

            val list = groups.getOrPut(norm(name) + "|" + cat.name) { ArrayList() }
            val near = list.firstOrNull { Geo.distance(it.lat, it.lon, acc.lat, acc.lon) < 2500 }
            if (near == null) {
                list.add(acc)
            } else if (acc.area || near.area) {
                // mismo parque/bosque partido en varias piezas: unir límites
                near.s = minOf(near.s, acc.s); near.w = minOf(near.w, acc.w)
                near.n = maxOf(near.n, acc.n); near.e = maxOf(near.e, acc.e)
                near.area = true
            }
        }
        val out = ArrayList<Poi>()
        for (list in groups.values) for (a in list) {
            out.add(
                if (a.area) Poi(a.id, a.name, a.cat, Scope.HANNOVER, a.lat, a.lon, 0.0, a.s, a.w, a.n, a.e)
                else Poi(a.id, a.name, a.cat, Scope.HANNOVER, a.s, a.w, 60.0)
            )
        }
        return out
    }

    /** Los lugares escogidos conservan su id (y tu progreso); OSM corrige su ubicación. */
    fun merge(curated: List<Poi>, osm: List<Poi>): List<Poi> {
        val used = HashSet<String>()
        val result = ArrayList<Poi>()
        for (c in curated) {
            val key = c.matchKey
            val match = if (key == null) null else osm
                .filter {
                    it.id !in used && it.isGreen == c.isGreen &&
                        norm(it.name).contains(key) &&
                        Geo.distance(c.lat, c.lon, it.lat, it.lon) < 1500
                }
                .minByOrNull { Geo.distance(c.lat, c.lon, it.lat, it.lon) }
            if (match != null) {
                used.add(match.id)
                result.add(if (c.isArea) c else c.withGeometry(match))
            } else {
                result.add(c)
            }
        }
        for (o in osm) if (o.id !in used) result.add(o)
        return result
    }
}
