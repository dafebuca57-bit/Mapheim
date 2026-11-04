package de.entdecker.hannover

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.hypot
import kotlin.math.max
import kotlin.random.Random

/** Capa de niebla: cubre todo y abre claros en cada celda visitada. */
class FogOverlay : Overlay() {

    private val fogPaint = Paint()
    private var fogStyle = ""

    private fun ensureShader() {
        val id = Shop.equippedId(ShopCat.FOG)
        if (id == fogStyle) return
        fogStyle = id
        fogPaint.shader = makeMistShader(Shop.fogColors(id))
    }
    private val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }
    private val pt = Point()
    private val gp = GeoPoint(0.0, 0.0)

    private fun makeMistShader(colors: Pair<Int, Int>): BitmapShader {
        val size = 256
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(colors.first)
        val vein = colors.second
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val rnd = Random(7)
        repeat(70) {
            p.color = Color.argb(rnd.nextInt(6, 18), Color.red(vein), Color.green(vein), Color.blue(vein))
            val x = rnd.nextFloat() * size
            val y = rnd.nextFloat() * size
            val r = rnd.nextFloat() * 40f + 12f
            // dibuja también en los bordes para que el patrón se repita sin cortes
            for (dx in intArrayOf(-size, 0, size)) for (dy in intArrayOf(-size, 0, size)) {
                c.drawCircle(x + dx, y + dy, r, p)
            }
        }
        return BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    override fun draw(c: Canvas, mv: MapView, shadow: Boolean) {
        if (shadow) return
        ensureShader()
        val pj = mv.projection
        val bb = pj.boundingBox
        val w = mv.width.toFloat()
        val h = mv.height.toFloat()
        val save = c.saveLayer(0f, 0f, w, h, null)
        c.drawPaint(fogPaint)

        val r = max(1.4f, pj.metersToPixels(Geo.DRAW_M))
        val padLat = 0.002
        val padLon = 0.003
        val s = bb.latSouth - padLat
        val n = bb.latNorth + padLat
        val west = bb.lonWest - padLon
        val east = bb.lonEast + padLon
        val blockLat = Geo.CELL_LAT * (1 shl Geo.BLOCK_SHIFT)
        val blockLon = Geo.CELL_LON * (1 shl Geo.BLOCK_SHIFT)

        for ((bk, list) in GameState.blocks) {
            val bLat = (bk ushr 32) * blockLat - 90.0
            val bLon = (bk and 0xffffffffL) * blockLon - 180.0
            if (bLat > n || bLat + blockLat < s || bLon > east || bLon + blockLon < west) continue
            for (k in list) {
                gp.setCoords(Geo.centerLat(k), Geo.centerLon(k))
                pj.toPixels(gp, pt)
                val x = pt.x.toFloat()
                val y = pt.y.toFloat()
                if (r > 3f) {
                    clear.alpha = 70; c.drawCircle(x, y, r * 1.45f, clear)
                    clear.alpha = 150; c.drawCircle(x, y, r * 1.15f, clear)
                }
                clear.alpha = 255; c.drawCircle(x, y, r * 0.9f, clear)
            }
        }
        c.restoreToCount(save)
    }
}

/** Lugares descubiertos como piedras rúnicas (y un "?" para los cercanos sin descubrir). */
class PoiOverlay(
    private val density: Float,
    labelFace: Typeface?,
    private val onTap: (Poi, Boolean) -> Unit
) : Overlay() {

    private class Hit(val poi: Poi, val x: Float, val y: Float, val found: Boolean)

    private val hits = ArrayList<Hit>()
    private val pt = Point()
    private val gp = GeoPoint(0.0, 0.0)
    private val stone = Path()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(13, 9, 5) }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = Color.rgb(234, 220, 192)
    }
    private val unknown = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = Color.argb(210, 232, 176, 74)
    }
    private val qText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, 232, 176, 74)
        textSize = 13f * density
        textAlign = Paint.Align.CENTER
        typeface = labelFace ?: Typeface.DEFAULT_BOLD
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(234, 220, 192)
        textSize = 11.5f * density
        textAlign = Paint.Align.CENTER
        typeface = labelFace ?: Typeface.create("serif", Typeface.BOLD)
    }
    private val halo = Paint(label).apply {
        color = Color.argb(230, 27, 19, 12)
        style = Paint.Style.STROKE
        strokeWidth = 4f * density
    }

    private fun colorFor(cat: Cat): Int = when (cat) {
        Cat.PARK -> Color.rgb(141, 170, 94)
        Cat.FOREST -> Color.rgb(79, 122, 69)
        Cat.SIGHT -> Color.rgb(232, 176, 74)
        Cat.LANDMARK -> Color.rgb(166, 61, 47)
    }

    private fun diamond(x: Float, y: Float, r: Float) {
        stone.reset()
        stone.moveTo(x, y - r * 1.25f)
        stone.lineTo(x + r, y)
        stone.lineTo(x, y + r * 1.25f)
        stone.lineTo(x - r, y)
        stone.close()
    }

    override fun draw(c: Canvas, mv: MapView, shadow: Boolean) {
        if (shadow) return
        hits.clear()
        val pj = mv.projection
        val bb = pj.boundingBox
        val zoom = mv.zoomLevelDouble
        val pad = 0.01
        for (p in GameState.pois) {
            if (p.lat < bb.latSouth - pad || p.lat > bb.latNorth + pad) continue
            if (p.lon < bb.lonWest - pad || p.lon > bb.lonEast + pad) continue
            val found = p.id in GameState.discovered
            if (!found) {
                if (zoom < 14.5 || !GameState.hasPos) continue
                if (p.distanceTo(GameState.posLat, GameState.posLon) > 400) continue
            } else if (!p.famous && zoom < 13.5) {
                continue
            }
            gp.setCoords(p.lat, p.lon)
            pj.toPixels(gp, pt)
            val x = pt.x.toFloat()
            val y = pt.y.toFloat()
            val r = (if (p.famous) 8f else 5.5f) * density
            if (found) {
                diamond(x, y, r + 2.5f * density)
                c.drawPath(stone, edge)
                diamond(x, y, r)
                fill.color = colorFor(p.cat)
                c.drawPath(stone, fill)
                c.drawPath(stone, ring)
                if (zoom >= 16.5 || (p.famous && zoom >= 14.5)) {
                    val ty = y - r * 1.25f - 7f * density
                    c.drawText(p.name, x, ty, halo)
                    c.drawText(p.name, x, ty, label)
                }
            } else {
                diamond(x, y, r + 3f * density)
                c.drawPath(stone, edge)
                c.drawPath(stone, unknown)
                c.drawText("?", x, y + qText.textSize / 3f, qText)
            }
            hits.add(Hit(p, x, y, found))
        }
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        val tol = 24f * density
        val best = hits.minByOrNull { hypot(it.x - e.x, it.y - e.y) } ?: return false
        if (hypot(best.x - e.x, best.y - e.y) > tol) return false
        onTap(best.poi, best.found)
        return true
    }
}

/** El jugador, con el marcador que tengas equipado en la tienda. */
class PlayerOverlay(private val density: Float) : Overlay() {
    private val pt = Point()
    private val gp = GeoPoint(0.0, 0.0)

    override fun draw(c: Canvas, mv: MapView, shadow: Boolean) {
        if (shadow || !GameState.hasPos) return
        gp.setCoords(GameState.posLat, GameState.posLon)
        mv.projection.toPixels(gp, pt)
        CursorArt.draw(c, pt.x.toFloat(), pt.y.toFloat(), density, Shop.equippedId(ShopCat.CURSOR), GameState.tracking)
    }
}
