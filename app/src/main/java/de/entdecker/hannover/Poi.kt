package de.entdecker.hannover

enum class Cat(val label: String) {
    SIGHT("Lugar de interés"),
    PARK("Parque o jardín"),
    FOREST("Bosque"),
    LANDMARK("Monumento de Alemania")
}

enum class Scope { HANNOVER, GERMANY }

/**
 * Punto de interés. Si tiene límites (south..east) es un área (parque, bosque):
 * se descubre al entrar. Si no, se descubre al pasar a menos de [radiusM].
 */
class Poi(
    val id: String,
    val name: String,
    val cat: Cat,
    val scope: Scope,
    val lat: Double,
    val lon: Double,
    val radiusM: Double = 70.0,
    val south: Double = Double.NaN,
    val west: Double = Double.NaN,
    val north: Double = Double.NaN,
    val east: Double = Double.NaN,
    val famous: Boolean = false,
    val matchKey: String? = null
) {
    val isArea: Boolean get() = !south.isNaN()
    val isGreen: Boolean get() = cat == Cat.PARK || cat == Cat.FOREST

    fun covers(la: Double, lo: Double): Boolean =
        if (isArea) la in south..north && lo in west..east
        else Geo.distance(lat, lon, la, lo) <= radiusM

    fun distanceTo(la: Double, lo: Double): Double = Geo.distance(lat, lon, la, lo)

    fun withGeometry(from: Poi): Poi = Poi(
        id, name, cat, scope, from.lat, from.lon, radiusM,
        from.south, from.west, from.north, from.east, famous, matchKey
    )

    companion object {
        fun node(
            id: String, name: String, lat: Double, lon: Double,
            key: String? = null, radius: Double = 70.0, cat: Cat = Cat.SIGHT
        ) = Poi(id, name, cat, Scope.HANNOVER, lat, lon, radius, famous = true, matchKey = key)

        fun area(
            id: String, name: String, cat: Cat,
            s: Double, w: Double, n: Double, e: Double, key: String? = null
        ) = Poi(id, name, cat, Scope.HANNOVER, (s + n) / 2, (w + e) / 2, 0.0, s, w, n, e, true, key)

        fun landmark(id: String, name: String, lat: Double, lon: Double, radius: Double = 350.0) =
            Poi(id, name, Cat.LANDMARK, Scope.GERMANY, lat, lon, radius, famous = true)
    }
}

class City(val id: String, val name: String, val lat: Double, val lon: Double, val radiusKm: Double)
