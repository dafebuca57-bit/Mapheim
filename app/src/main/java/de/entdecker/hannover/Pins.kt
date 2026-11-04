package de.entdecker.hannover

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.hypot

/** Tipos de pin propios del usuario. */
enum class PinType(val label: String, val icon: Int, val color: Int) {
    FOOD("Comida", R.drawable.pin_food, Color.rgb(200, 100, 43)),
    HOME("Casa", R.drawable.pin_home, Color.rgb(184, 135, 74)),
    TRAIN("Tren", R.drawable.pin_train, Color.rgb(78, 110, 142)),
    BUS("Bus", R.drawable.pin_bus, Color.rgb(63, 127, 122)),
    TRAM("Stadtbahn", R.drawable.pin_tram, Color.rgb(106, 90, 142)),
    CAFE("Café", R.drawable.pin_cafe, Color.rgb(122, 82, 54)),
    MEAD("Taberna", R.drawable.pin_mead, Color.rgb(166, 61, 47)),
    SHOP("Tienda", R.drawable.pin_shop, Color.rgb(142, 122, 58)),
    WC("Baño", R.drawable.pin_wc, Color.rgb(90, 122, 154)),
    PARKING("Parqueo", R.drawable.pin_parking, Color.rgb(74, 90, 142)),
    FAVORITE("Favorito", R.drawable.pin_star, Color.rgb(217, 161, 59)),
    NOTE("Nota", R.drawable.pin_note, Color.rgb(110, 142, 78))
}

class Pin(
    val id: Long,
    val type: PinType,
    val lat: Double,
    val lon: Double,
    val note: String,
    val created: Long
) {
    val title: String get() = note.ifBlank { type.label }
}

/** Pines guardados en el teléfono. */
object PinStore {
    private lateinit var prefs: SharedPreferences
    val pins = ArrayList<Pin>()

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("pins", Context.MODE_PRIVATE)
        pins.clear()
        try {
            val arr = JSONArray(prefs.getString("list", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val type = try { PinType.valueOf(o.getString("type")) } catch (e: Exception) { PinType.NOTE }
                pins.add(
                    Pin(
                        o.getLong("id"), type, o.getDouble("lat"), o.getDouble("lon"),
                        o.optString("note", ""), o.optLong("created", 0L)
                    )
                )
            }
        } catch (_: Exception) { }
    }

    fun add(type: PinType, lat: Double, lon: Double, note: String): Pin {
        val now = System.currentTimeMillis()
        val p = Pin(now, type, lat, lon, note.trim(), now)
        pins.add(p)
        save()
        return p
    }

    fun remove(p: Pin) {
        pins.removeAll { it.id == p.id }
        save()
    }

    private fun save() {
        val arr = JSONArray()
        for (p in pins) {
            arr.put(
                JSONObject()
                    .put("id", p.id).put("type", p.type.name)
                    .put("lat", p.lat).put("lon", p.lon)
                    .put("note", p.note).put("created", p.created)
            )
        }
        prefs.edit().putString("list", arr.toString()).apply()
    }
}

/** Dibuja los pines (siempre visibles, aunque haya niebla). */
class PinOverlay(
    ctx: Context,
    private val density: Float,
    private val onTap: (Pin) -> Unit
) : Overlay() {

    private class Hit(val pin: Pin, val x: Float, val y: Float)

    private val icons: Map<PinType, Drawable> = PinType.values().associateWith { t ->
        ContextCompat.getDrawable(ctx, t.icon)!!.mutate().apply { setTint(Color.rgb(234, 220, 192)) }
    }
    private val hits = ArrayList<Hit>()
    private val pt = Point()
    private val gp = GeoPoint(0.0, 0.0)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(234, 220, 192)
        textAlign = Paint.Align.CENTER
    }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 27, 19, 12)
        textAlign = Paint.Align.CENTER
        style = Paint.Style.STROKE
    }

    override fun draw(c: Canvas, mv: MapView, shadow: Boolean) {
        if (shadow) return
        hits.clear()
        val pj = mv.projection
        val r = 14f * density
        val lift = 22f * density
        val iconHalf = (9.5f * density).toInt()
        label.textSize = 11f * density
        halo.textSize = 11f * density
        halo.strokeWidth = 4f * density
        val showLabels = mv.zoomLevelDouble >= 16.0
        val style = Shop.equippedId(ShopCat.PIN)

        for (p in PinStore.pins) {
            gp.setCoords(p.lat, p.lon)
            pj.toPixels(gp, pt)
            val x = pt.x.toFloat()
            val y = pt.y.toFloat()
            if (x < -60 || y < -60 || x > mv.width + 60 || y > mv.height + 60) continue
            val cy = y - lift

            PinArt.drawFrame(c, x, cy, y, r, density, p.type.color, style)

            val icon = icons[p.type]
            if (icon != null) {
                icon.setBounds(x.toInt() - iconHalf, cy.toInt() - iconHalf, x.toInt() + iconHalf, cy.toInt() + iconHalf)
                icon.draw(c)
            }
            if (showLabels && p.note.isNotBlank()) {
                val t = if (p.note.length > 22) p.note.take(21) + "…" else p.note
                val ty = cy - r - 6f * density
                c.drawText(t, x, ty, halo)
                c.drawText(t, x, ty, label)
            }
            hits.add(Hit(p, x, cy))
        }
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        val tol = 26f * density
        val best = hits.minByOrNull { hypot(it.x - e.x, it.y - e.y) } ?: return false
        if (hypot(best.x - e.x, best.y - e.y) > tol) return false
        onTap(best.pin)
        return true
    }
}
