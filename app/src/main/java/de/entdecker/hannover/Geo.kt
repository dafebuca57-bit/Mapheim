package de.entdecker.hannover

import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

val ES: Locale = Locale("es", "ES")

fun fmt(v: Double, decimals: Int): String = String.format(ES, "%.${decimals}f", v)

/**
 * Malla global de celdas (~50 x 50 m en Hannover). Cada celda visitada se
 * despeja de la niebla y se guarda como un Long.
 */
object Geo {
    const val CELL_LAT = 0.00045
    const val CELL_LON = 0.00075
    const val M_PER_DEG = 111_320.0

    /** Radio alrededor de cada punto GPS que se marca como visitado. */
    const val REVEAL_M = 55.0

    /** Radio con que se dibuja el claro de cada celda en el mapa. */
    const val DRAW_M = 44f

    const val BLOCK_SHIFT = 5 // bloques de 32 x 32 celdas para dibujar rápido

    // Ciudad de Hannover (aprox. por rectángulo) y su superficie real
    const val HAN_S = 52.305
    const val HAN_W = 9.605
    const val HAN_N = 52.455
    const val HAN_E = 9.918
    private const val HAN_AREA_M2 = 204.0e6
    private const val GER_AREA_M2 = 357_600.0e6

    val HANNOVER_CELLS: Double = HAN_AREA_M2 / cellArea(52.37)
    val GERMANY_CELLS: Double = GER_AREA_M2 / cellArea(51.0)

    private fun cellArea(lat: Double): Double =
        (CELL_LAT * M_PER_DEG) * (CELL_LON * M_PER_DEG * cos(Math.toRadians(lat)))

    fun cellKey(lat: Double, lon: Double): Long {
        val y = floor((lat + 90.0) / CELL_LAT).toLong()
        val x = floor((lon + 180.0) / CELL_LON).toLong()
        return (y shl 32) or (x and 0xffffffffL)
    }

    fun cellY(key: Long): Long = key ushr 32
    fun cellX(key: Long): Long = key and 0xffffffffL
    fun centerLat(key: Long): Double = cellY(key) * CELL_LAT + CELL_LAT / 2 - 90.0
    fun centerLon(key: Long): Double = cellX(key) * CELL_LON + CELL_LON / 2 - 180.0

    fun blockKey(key: Long): Long =
        ((cellY(key) shr BLOCK_SHIFT) shl 32) or (cellX(key) shr BLOCK_SHIFT)

    fun inHannover(lat: Double, lon: Double): Boolean =
        lat in HAN_S..HAN_N && lon in HAN_W..HAN_E

    fun cellsAround(lat: Double, lon: Double, radiusM: Double): List<Long> {
        val dLat = radiusM / M_PER_DEG
        val dLon = radiusM / (M_PER_DEG * cos(Math.toRadians(lat)))
        val y0 = floor((lat - dLat + 90.0) / CELL_LAT).toLong()
        val y1 = floor((lat + dLat + 90.0) / CELL_LAT).toLong()
        val x0 = floor((lon - dLon + 180.0) / CELL_LON).toLong()
        val x1 = floor((lon + dLon + 180.0) / CELL_LON).toLong()
        val out = ArrayList<Long>()
        for (y in y0..y1) {
            for (x in x0..x1) {
                val cLat = y * CELL_LAT + CELL_LAT / 2 - 90.0
                val cLon = x * CELL_LON + CELL_LON / 2 - 180.0
                if (distance(lat, lon, cLat, cLon) <= radiusM + 15.0) {
                    out.add((y shl 32) or (x and 0xffffffffL))
                }
            }
        }
        return out
    }

    /** Distancia en metros (haversine). */
    fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }
}
