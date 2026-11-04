package de.entdecker.hannover

class Stats(
    val walkedKm: Double,
    val hPct: Double,
    val gPct: Double,
    val hPois: Int,
    val hPoisTotal: Int,
    val hGreen: Int,
    val hGreenTotal: Int,
    val gLand: Int,
    val gLandTotal: Int,
    val cities: Int,
    val citiesTotal: Int,
    val days: Int,
    val herrenhausen: Int,
    val altstadt: Int,
    val eilenriede: Int,
    val maschsee: Int
)

enum class Section(val title: String) {
    HANNOVER("Hannover"),
    WALK("Caminata"),
    GERMANY("Alemania")
}

class Achievement(
    val id: String,
    val section: Section,
    val title: String,
    val desc: String,
    val target: Double,
    val unit: String,
    val decimals: Int,
    val value: (Stats) -> Double
) {
    /** Ojos de Greydwarf que da al desbloquearse. */
    val reward: Int get() = Achievements.REWARDS[id] ?: 100
}

object Achievements {

    val REWARDS: Map<String, Int> = mapOf(
        "h_first" to 50, "h_pois10" to 150, "h_pois25" to 300, "h_pois50" to 600, "h_pois100" to 1200,
        "h_altstadt" to 400, "h_herren" to 400, "h_maschsee" to 150, "h_eilen" to 250,
        "h_green3" to 150, "h_green10" to 350, "h_green25" to 800,
        "h_pct1" to 200, "h_pct5" to 600, "h_pct10" to 1200, "h_pct25" to 2500, "h_pct50" to 5000,
        "w1" to 50, "w10" to 200, "w42" to 600, "w100" to 1200, "w250" to 2500, "w500" to 5000,
        "d3" to 100, "d7" to 300, "d30" to 1000,
        "g_city1" to 300, "g_city5" to 800, "g_city10" to 1500, "g_city20" to 3000,
        "g_land1" to 300, "g_land5" to 800, "g_land15" to 2000, "g_land30" to 4000,
        "g_pct1" to 500, "g_pct2" to 2000
    )

    private fun a(
        id: String, section: Section, title: String, desc: String,
        target: Double, unit: String, decimals: Int = 0, value: (Stats) -> Double
    ) = Achievement(id, section, title, desc, target, unit, decimals, value)

    val all: List<Achievement> = listOf(
        // ---------- Hannover ----------
        a("h_first", Section.HANNOVER, "Primer hallazgo", "Descubre tu primer lugar en Hannover", 1.0, "lugares") { it.hPois.toDouble() },
        a("h_pois10", Section.HANNOVER, "Turista curioso", "Descubre 10 lugares de Hannover", 10.0, "lugares") { it.hPois.toDouble() },
        a("h_pois25", Section.HANNOVER, "Conocedor de Hannover", "Descubre 25 lugares de Hannover", 25.0, "lugares") { it.hPois.toDouble() },
        a("h_pois50", Section.HANNOVER, "Hannoveraner de honor", "Descubre 50 lugares de Hannover", 50.0, "lugares") { it.hPois.toDouble() },
        a("h_pois100", Section.HANNOVER, "Leyenda de Hannover", "Descubre 100 lugares de Hannover", 100.0, "lugares") { it.hPois.toDouble() },
        a("h_altstadt", Section.HANNOVER, "Casco antiguo", "Marktkirche, Altes Rathaus, Leineschloss, Beginenturm y Leibnizhaus", 5.0, "de 5") { it.altstadt.toDouble() },
        a("h_herren", Section.HANNOVER, "Jardines reales", "Visita Großer Garten, Berggarten y Georgengarten", 3.0, "de 3") { it.herrenhausen.toDouble() },
        a("h_maschsee", Section.HANNOVER, "Orillas del Maschsee", "Llega al lago Maschsee", 1.0, "") { it.maschsee.toDouble() },
        a("h_eilen", Section.HANNOVER, "Corazón de la Eilenriede", "Entra a la Eilenriede, uno de los bosques urbanos más grandes de Europa", 1.0, "") { it.eilenriede.toDouble() },
        a("h_green3", Section.HANNOVER, "Pulmón verde", "Descubre 3 parques o bosques", 3.0, "zonas verdes") { it.hGreen.toDouble() },
        a("h_green10", Section.HANNOVER, "Amante de los árboles", "Descubre 10 parques o bosques", 10.0, "zonas verdes") { it.hGreen.toDouble() },
        a("h_green25", Section.HANNOVER, "Guardabosques", "Descubre 25 parques o bosques", 25.0, "zonas verdes") { it.hGreen.toDouble() },
        a("h_pct1", Section.HANNOVER, "Cartógrafo I", "Despeja el 1 % de Hannover", 1.0, "%", 2) { it.hPct },
        a("h_pct5", Section.HANNOVER, "Cartógrafo II", "Despeja el 5 % de Hannover", 5.0, "%", 2) { it.hPct },
        a("h_pct10", Section.HANNOVER, "Cartógrafo III", "Despeja el 10 % de Hannover", 10.0, "%", 2) { it.hPct },
        a("h_pct25", Section.HANNOVER, "Cartógrafo IV", "Despeja el 25 % de Hannover", 25.0, "%", 2) { it.hPct },
        a("h_pct50", Section.HANNOVER, "Maestro cartógrafo", "Despeja la mitad de Hannover", 50.0, "%", 2) { it.hPct },

        // ---------- Caminata ----------
        a("w1", Section.WALK, "Primeros pasos", "Camina 1 km", 1.0, "km", 1) { it.walkedKm },
        a("w10", Section.WALK, "Caminante", "Camina 10 km", 10.0, "km", 1) { it.walkedKm },
        a("w42", Section.WALK, "Maratón", "Camina 42,2 km", 42.2, "km", 1) { it.walkedKm },
        a("w100", Section.WALK, "Peregrino", "Camina 100 km", 100.0, "km", 1) { it.walkedKm },
        a("w250", Section.WALK, "Trotamundos", "Camina 250 km", 250.0, "km", 1) { it.walkedKm },
        a("w500", Section.WALK, "Leyenda andante", "Camina 500 km", 500.0, "km", 1) { it.walkedKm },
        a("d3", Section.WALK, "Constancia", "Explora en 3 días distintos", 3.0, "días") { it.days.toDouble() },
        a("d7", Section.WALK, "Semana exploradora", "Explora en 7 días distintos", 7.0, "días") { it.days.toDouble() },
        a("d30", Section.WALK, "Hábito de explorador", "Explora en 30 días distintos", 30.0, "días") { it.days.toDouble() },

        // ---------- Alemania ----------
        a("g_city1", Section.GERMANY, "Más allá de Hannover", "Visita otra ciudad alemana", 1.0, "ciudades") { it.cities.toDouble() },
        a("g_city5", Section.GERMANY, "Viajero", "Visita 5 ciudades alemanas", 5.0, "ciudades") { it.cities.toDouble() },
        a("g_city10", Section.GERMANY, "Nómada", "Visita 10 ciudades alemanas", 10.0, "ciudades") { it.cities.toDouble() },
        a("g_city20", Section.GERMANY, "Conquistador", "Visita 20 ciudades alemanas", 20.0, "ciudades") { it.cities.toDouble() },
        a("g_land1", Section.GERMANY, "Postal alemana", "Descubre un monumento famoso de Alemania", 1.0, "monumentos") { it.gLand.toDouble() },
        a("g_land5", Section.GERMANY, "Coleccionista", "Descubre 5 monumentos de Alemania", 5.0, "monumentos") { it.gLand.toDouble() },
        a("g_land15", Section.GERMANY, "Gran tour", "Descubre 15 monumentos de Alemania", 15.0, "monumentos") { it.gLand.toDouble() },
        a("g_land30", Section.GERMANY, "Alemania de punta a punta", "Descubre 30 monumentos de Alemania", 30.0, "monumentos") { it.gLand.toDouble() },
        a("g_pct1", Section.GERMANY, "Huella en Alemania", "Despeja el 0,001 % de Alemania", 0.001, "%", 4) { it.gPct },
        a("g_pct2", Section.GERMANY, "Marca en el mapa", "Despeja el 0,01 % de Alemania", 0.01, "%", 4) { it.gPct }
    )
}
