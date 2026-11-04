package de.entdecker.hannover

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

enum class ShopCat(val title: String, val hint: String) {
    MAP("Estilos de mapa", "Cambia el color de todo el mapa"),
    FOG("Niebla", "El color de lo que aún no exploras"),
    CURSOR("Tu marcador", "Cómo te ves en el mapa"),
    PIN("Estilo de pines", "El marco de todos tus pines")
}

class ShopItem(
    val id: String,
    val cat: ShopCat,
    val name: String,
    val desc: String,
    val price: Int
)

/** Mercader: catálogo, compras y lo que tienes equipado. */
object Shop {

    val items: List<ShopItem> = listOf(
        // Mapas (inspirados en los biomas nórdicos)
        ShopItem("map_parchment", ShopCat.MAP, "Pergamino", "Mapa de explorador, tonos de cuero viejo", 0),
        ShopItem("map_meadows", ShopCat.MAP, "Praderas", "Verde cálido de las primeras tierras", 150),
        ShopItem("map_blackforest", ShopCat.MAP, "Selva Negra", "Oscuro y musgoso, como bajo los abetos", 300),
        ShopItem("map_swamp", ShopCat.MAP, "Pantano", "Aguas turbias y barro verdoso", 400),
        ShopItem("map_mountain", ShopCat.MAP, "Montaña", "Escarcha y piedra azulada", 500),
        ShopItem("map_plains", ShopCat.MAP, "Llanuras", "Trigo dorado bajo el sol", 650),
        ShopItem("map_mist", ShopCat.MAP, "Tierras de Bruma", "Violeta y misterio", 900),
        ShopItem("map_ash", ShopCat.MAP, "Tierras de Ceniza", "Brasas y roca volcánica", 1200),

        // Niebla
        ShopItem("fog_cold", ShopCat.FOG, "Niebla fría", "La bruma del norte", 0),
        ShopItem("fog_swamp", ShopCat.FOG, "Vapor del pantano", "Verde y espesa", 200),
        ShopItem("fog_blizzard", ShopCat.FOG, "Ventisca", "Blanca como la nieve de la montaña", 350),
        ShopItem("fog_mist", ShopCat.FOG, "Bruma violeta", "Niebla que esconde secretos", 500),
        ShopItem("fog_ash", ShopCat.FOG, "Humo de ceniza", "Oscuridad rojiza de los volcanes", 800),

        // Marcador del jugador
        ShopItem("cur_torch", ShopCat.CURSOR, "Antorcha", "Una llama sencilla para abrir camino", 0),
        ShopItem("cur_shield", ShopCat.CURSOR, "Escudo redondo", "Madera, bronce y un umbo al centro", 250),
        ShopItem("cur_rune", ShopCat.CURSOR, "Piedra rúnica", "Una runa ᛉ que brilla en la piedra", 400),
        ShopItem("cur_wisp", ShopCat.CURSOR, "Fuego fatuo", "Una luz azul que aparta la niebla", 700),
        ShopItem("cur_ember", ShopCat.CURSOR, "Núcleo ardiente", "Un corazón de fuego con llamaradas", 1000),

        // Pines
        ShopItem("pin_bronze", ShopCat.PIN, "Bronce", "Aro de bronce clásico", 0),
        ShopItem("pin_iron", ShopCat.PIN, "Hierro", "Aro gris, sobrio y fuerte", 150),
        ShopItem("pin_shieldrim", ShopCat.PIN, "Escudo remachado", "Doble aro con cuatro remaches", 300),
        ShopItem("pin_runestone", ShopCat.PIN, "Runa", "Pines en forma de piedra rúnica", 450),
        ShopItem("pin_gold", ShopCat.PIN, "Oro de jarl", "Aro de oro grueso, digno de un jarl", 800)
    )

    private lateinit var prefs: SharedPreferences
    private val owned = HashSet<String>()
    private val equipped = HashMap<ShopCat, String>()

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("shop", Context.MODE_PRIVATE)
        owned.clear()
        owned.addAll(prefs.getStringSet("owned", emptySet()) ?: emptySet())
        for (i in items) if (i.price == 0) owned.add(i.id)
        for (c in ShopCat.values()) {
            val id = prefs.getString("eq_" + c.name, null)
            equipped[c] = if (id != null && id in owned) id else items.first { it.cat == c && it.price == 0 }.id
        }
    }

    fun owns(item: ShopItem) = item.id in owned
    fun isEquipped(item: ShopItem) = equipped[item.cat] == item.id
    fun equippedId(cat: ShopCat): String = equipped[cat] ?: items.first { it.cat == cat }.id

    /** Compra y equipa. false si faltan ojos. */
    fun buy(item: ShopItem): Boolean {
        if (owns(item)) { equip(item); return true }
        if (!GameState.spend(item.price)) return false
        owned.add(item.id)
        equip(item)
        return true
    }

    fun equip(item: ShopItem) {
        if (!owns(item)) return
        equipped[item.cat] = item.id
        val e = prefs.edit().putStringSet("owned", HashSet(owned))
        for ((c, id) in equipped) e.putString("eq_" + c.name, id)
        e.apply()
    }

    // ------------------------------------------------------------ estilos

    private fun tint(sat: Float, r: Float, g: Float, b: Float, or: Float, og: Float, ob: Float): ColorMatrix {
        val cm = ColorMatrix().apply { setSaturation(sat) }
        cm.postConcat(
            ColorMatrix(
                floatArrayOf(
                    r, 0f, 0f, 0f, or,
                    0f, g, 0f, 0f, og,
                    0f, 0f, b, 0f, ob,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        return cm
    }

    fun mapMatrix(id: String = equippedId(ShopCat.MAP)): ColorMatrix = when (id) {
        "map_meadows" -> tint(0.85f, 0.95f, 1.05f, 0.78f, 6f, 10f, -8f)
        "map_blackforest" -> tint(0.30f, 0.62f, 0.74f, 0.60f, -6f, 0f, -8f)
        "map_swamp" -> tint(0.30f, 0.78f, 0.82f, 0.52f, -4f, 0f, -16f)
        "map_mountain" -> tint(0.12f, 0.88f, 0.95f, 1.10f, 18f, 22f, 30f)
        "map_plains" -> tint(0.50f, 1.05f, 0.94f, 0.58f, 22f, 10f, -14f)
        "map_mist" -> tint(0.20f, 0.80f, 0.70f, 1.02f, 0f, -6f, 14f)
        "map_ash" -> tint(0.15f, 0.95f, 0.55f, 0.48f, -8f, -14f, -16f)
        else -> tint(0.35f, 0.95f, 0.88f, 0.70f, 14f, 4f, -12f) // pergamino
    }

    /** Color base de la niebla y color de sus vetas. */
    fun fogColors(id: String = equippedId(ShopCat.FOG)): Pair<Int, Int> = when (id) {
        "fog_swamp" -> Color.argb(240, 26, 34, 24) to Color.rgb(120, 160, 100)
        "fog_blizzard" -> Color.argb(236, 200, 210, 224) to Color.rgb(255, 255, 255)
        "fog_mist" -> Color.argb(240, 36, 28, 52) to Color.rgb(170, 140, 220)
        "fog_ash" -> Color.argb(242, 36, 18, 14) to Color.rgb(220, 90, 50)
        else -> Color.argb(240, 20, 24, 31) to Color.rgb(150, 170, 200)
    }
}

/** Dibujo de los marcadores del jugador (se usa en el mapa y en la tienda). */
object CursorArt {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val path = Path()
    private val edge = Color.rgb(13, 9, 5)

    fun draw(c: Canvas, x: Float, y: Float, d: Float, style: String, active: Boolean) {
        p.shader = null
        when (style) {
            "cur_shield" -> shield(c, x, y, d, active)
            "cur_rune" -> rune(c, x, y, d, active)
            "cur_wisp" -> wisp(c, x, y, d, active)
            "cur_ember" -> ember(c, x, y, d, active)
            else -> torch(c, x, y, d, active)
        }
    }

    private fun glow(c: Canvas, x: Float, y: Float, d: Float, color: Int, big: Float) {
        p.color = color
        p.alpha = 35; c.drawCircle(x, y, big * d, p)
        p.alpha = 70; c.drawCircle(x, y, big * 0.6f * d, p)
        p.alpha = 255
    }

    private fun torch(c: Canvas, x: Float, y: Float, d: Float, on: Boolean) {
        val base = if (on) Color.rgb(224, 120, 47) else Color.rgb(168, 151, 124)
        if (on) glow(c, x, y, d, base, 28f)
        p.color = base; c.drawCircle(x, y, 8f * d, p)
        stroke.color = edge; stroke.strokeWidth = 2.5f * d; c.drawCircle(x, y, 8f * d, stroke)
        if (on) { p.color = Color.rgb(255, 226, 160); c.drawCircle(x, y, 3.5f * d, p) }
    }

    private fun shield(c: Canvas, x: Float, y: Float, d: Float, on: Boolean) {
        if (on) glow(c, x, y, d, Color.rgb(224, 120, 47), 26f)
        val r = 12f * d
        p.color = edge; c.drawCircle(x, y, r + 2f * d, p)
        p.color = Color.rgb(110, 70, 38); c.drawCircle(x, y, r, p)
        stroke.color = Color.rgb(70, 44, 24); stroke.strokeWidth = 1.2f * d
        for (i in -1..1) {
            val dx = i * r * 0.45f
            c.drawLine(x + dx, y - r * 0.92f, x + dx, y + r * 0.92f, stroke)
        }
        stroke.color = Color.rgb(184, 135, 74); stroke.strokeWidth = 2.5f * d; c.drawCircle(x, y, r, stroke)
        p.color = Color.rgb(200, 160, 90); c.drawCircle(x, y, 3.8f * d, p)
        stroke.color = edge; stroke.strokeWidth = 1f * d; c.drawCircle(x, y, 3.8f * d, stroke)
    }

    private fun rune(c: Canvas, x: Float, y: Float, d: Float, on: Boolean) {
        val runeColor = if (on) Color.rgb(127, 224, 200) else Color.rgb(150, 160, 150)
        if (on) glow(c, x, y, d, runeColor, 26f)
        val w = 10f * d; val h = 13f * d
        path.reset()
        path.moveTo(x - w * 0.8f, y + h)
        path.lineTo(x - w, y - h * 0.4f)
        path.quadTo(x, y - h * 1.35f, x + w, y - h * 0.4f)
        path.lineTo(x + w * 0.8f, y + h)
        path.close()
        p.color = Color.rgb(96, 98, 96); c.drawPath(path, p)
        stroke.color = edge; stroke.strokeWidth = 2f * d; c.drawPath(path, stroke)
        // runa ᛉ (algiz)
        stroke.color = runeColor; stroke.strokeWidth = 2.2f * d
        c.drawLine(x, y + h * 0.65f, x, y - h * 0.65f, stroke)
        c.drawLine(x, y - h * 0.05f, x - w * 0.55f, y - h * 0.6f, stroke)
        c.drawLine(x, y - h * 0.05f, x + w * 0.55f, y - h * 0.6f, stroke)
    }

    private fun wisp(c: Canvas, x: Float, y: Float, d: Float, on: Boolean) {
        val r = (if (on) 30f else 18f) * d
        p.shader = RadialGradient(
            x, y, r,
            intArrayOf(Color.argb(255, 235, 250, 255), Color.argb(170, 120, 200, 255), Color.argb(0, 80, 140, 255)),
            floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(x, y, r, p)
        p.shader = null
        p.color = Color.WHITE; c.drawCircle(x, y, 4.5f * d, p)
    }

    private fun ember(c: Canvas, x: Float, y: Float, d: Float, on: Boolean) {
        val hot = if (on) Color.rgb(255, 110, 30) else Color.rgb(150, 90, 60)
        if (on) glow(c, x, y, d, Color.rgb(255, 80, 20), 30f)
        p.color = hot
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0 + 22.5)
            val a1 = a - 0.28; val a2 = a + 0.28
            path.reset()
            path.moveTo(x + (cos(a1) * 8 * d).toFloat(), y + (sin(a1) * 8 * d).toFloat())
            path.lineTo(x + (cos(a) * 16 * d).toFloat(), y + (sin(a) * 16 * d).toFloat())
            path.lineTo(x + (cos(a2) * 8 * d).toFloat(), y + (sin(a2) * 8 * d).toFloat())
            path.close()
            c.drawPath(path, p)
        }
        p.color = edge; c.drawCircle(x, y, 9.5f * d, p)
        p.color = Color.rgb(200, 60, 20); c.drawCircle(x, y, 8f * d, p)
        p.color = Color.rgb(255, 200, 90); c.drawCircle(x, y, 4f * d, p)
    }
}

/** Marco de los pines según el estilo comprado. */
object PinArt {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()
    private val edge = Color.rgb(13, 9, 5)

    /** Dibuja el fondo del pin con centro en (x, cy) y punta en (x, tipY). */
    fun drawFrame(c: Canvas, x: Float, cy: Float, tipY: Float, r: Float, d: Float, fill: Int, style: String) {
        // punta
        path.reset()
        path.moveTo(x - r * 0.55f, cy + r * 0.6f)
        path.lineTo(x, tipY)
        path.lineTo(x + r * 0.55f, cy + r * 0.6f)
        path.close()
        p.color = edge
        c.drawPath(path, p)

        if (style == "pin_runestone") {
            diamond(x, cy, r + 2.5f * d); p.color = edge; c.drawPath(path, p)
            diamond(x, cy, r); p.color = fill; c.drawPath(path, p)
            stroke.color = Color.rgb(127, 224, 200); stroke.strokeWidth = 1.8f * d; c.drawPath(path, stroke)
            return
        }
        val ringColor = when (style) {
            "pin_iron" -> Color.rgb(160, 166, 172)
            "pin_gold" -> Color.rgb(232, 176, 74)
            else -> Color.rgb(184, 135, 74)
        }
        val ringW = if (style == "pin_gold") 3.2f else 2f
        p.color = edge; c.drawCircle(x, cy, r + 2f * d, p)
        p.color = fill; c.drawCircle(x, cy, r, p)
        stroke.color = ringColor; stroke.strokeWidth = ringW * d; c.drawCircle(x, cy, r, stroke)
        if (style == "pin_shieldrim") {
            stroke.color = Color.rgb(110, 78, 42); stroke.strokeWidth = 1.2f * d
            c.drawCircle(x, cy, r - 3.5f * d, stroke)
            p.color = Color.rgb(200, 160, 90)
            for (i in 0 until 4) {
                val a = Math.toRadians(i * 90.0 + 45.0)
                c.drawCircle(x + (cos(a) * r).toFloat(), cy + (sin(a) * r).toFloat(), 2.2f * d, p)
            }
        }
    }

    private fun diamond(x: Float, y: Float, r: Float) {
        path.reset()
        path.moveTo(x, y - r * 1.2f)
        path.lineTo(x + r, y)
        path.lineTo(x, y + r * 1.2f)
        path.lineTo(x - r, y)
        path.close()
    }
}

/** Vista pequeña que dibuja una vista previa con un lambda. */
class PreviewView(ctx: Context, private val painter: (Canvas, Float, Float) -> Unit) : View(ctx) {
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        painter(canvas, width.toFloat(), height.toFloat())
    }
}
