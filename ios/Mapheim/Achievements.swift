import Foundation

struct Stats {
    var walkedKm = 0.0
    var hPct = 0.0
    var gPct = 0.0
    var hPois = 0, hPoisTotal = 0
    var hGreen = 0, hGreenTotal = 0
    var gLand = 0, gLandTotal = 0
    var cities = 0, citiesTotal = 0
    var days = 0
    var herrenhausen = 0, altstadt = 0, eilenriede = 0, maschsee = 0
}

enum AchSection: String, CaseIterable {
    case hannover = "Hannover"
    case walk = "Caminata"
    case germany = "Alemania"
}

struct Achievement: Identifiable {
    let id: String
    let section: AchSection
    let title: String
    let desc: String
    let target: Double
    let unit: String
    let decimals: Int
    let reward: Int
    let value: (Stats) -> Double
}

enum Achievements {
    private static func a(_ id: String, _ s: AchSection, _ title: String, _ desc: String, _ target: Double,
                          _ unit: String, _ reward: Int, _ decimals: Int = 0, _ v: @escaping (Stats) -> Double) -> Achievement {
        Achievement(id: id, section: s, title: title, desc: desc, target: target, unit: unit, decimals: decimals, reward: reward, value: v)
    }

    static let all: [Achievement] = [
        // Hannover
        a("h_first", .hannover, "Primer hallazgo", "Descubre tu primer lugar en Hannover", 1, "lugares", 50) { Double($0.hPois) },
        a("h_pois10", .hannover, "Turista curioso", "Descubre 10 lugares de Hannover", 10, "lugares", 150) { Double($0.hPois) },
        a("h_pois25", .hannover, "Conocedor de Hannover", "Descubre 25 lugares de Hannover", 25, "lugares", 300) { Double($0.hPois) },
        a("h_pois50", .hannover, "Hannoveraner de honor", "Descubre 50 lugares de Hannover", 50, "lugares", 600) { Double($0.hPois) },
        a("h_pois100", .hannover, "Leyenda de Hannover", "Descubre 100 lugares de Hannover", 100, "lugares", 1200) { Double($0.hPois) },
        a("h_altstadt", .hannover, "Casco antiguo", "Marktkirche, Altes Rathaus, Leineschloss, Beginenturm y Leibnizhaus", 5, "de 5", 400) { Double($0.altstadt) },
        a("h_herren", .hannover, "Jardines reales", "Visita Großer Garten, Berggarten y Georgengarten", 3, "de 3", 400) { Double($0.herrenhausen) },
        a("h_maschsee", .hannover, "Orillas del Maschsee", "Llega al lago Maschsee", 1, "", 150) { Double($0.maschsee) },
        a("h_eilen", .hannover, "Corazón de la Eilenriede", "Entra a la Eilenriede, uno de los bosques urbanos más grandes de Europa", 1, "", 250) { Double($0.eilenriede) },
        a("h_green3", .hannover, "Pulmón verde", "Descubre 3 parques o bosques", 3, "zonas verdes", 150) { Double($0.hGreen) },
        a("h_green10", .hannover, "Amante de los árboles", "Descubre 10 parques o bosques", 10, "zonas verdes", 350) { Double($0.hGreen) },
        a("h_green25", .hannover, "Guardabosques", "Descubre 25 parques o bosques", 25, "zonas verdes", 800) { Double($0.hGreen) },
        a("h_pct1", .hannover, "Cartógrafo I", "Despeja el 1 % de Hannover", 1, "%", 200, 2) { $0.hPct },
        a("h_pct5", .hannover, "Cartógrafo II", "Despeja el 5 % de Hannover", 5, "%", 600, 2) { $0.hPct },
        a("h_pct10", .hannover, "Cartógrafo III", "Despeja el 10 % de Hannover", 10, "%", 1200, 2) { $0.hPct },
        a("h_pct25", .hannover, "Cartógrafo IV", "Despeja el 25 % de Hannover", 25, "%", 2500, 2) { $0.hPct },
        a("h_pct50", .hannover, "Maestro cartógrafo", "Despeja la mitad de Hannover", 50, "%", 5000, 2) { $0.hPct },
        // Caminata
        a("w1", .walk, "Primeros pasos", "Camina 1 km", 1, "km", 50, 1) { $0.walkedKm },
        a("w10", .walk, "Caminante", "Camina 10 km", 10, "km", 200, 1) { $0.walkedKm },
        a("w42", .walk, "Maratón", "Camina 42,2 km", 42.2, "km", 600, 1) { $0.walkedKm },
        a("w100", .walk, "Peregrino", "Camina 100 km", 100, "km", 1200, 1) { $0.walkedKm },
        a("w250", .walk, "Trotamundos", "Camina 250 km", 250, "km", 2500, 1) { $0.walkedKm },
        a("w500", .walk, "Leyenda andante", "Camina 500 km", 500, "km", 5000, 1) { $0.walkedKm },
        a("d3", .walk, "Constancia", "Explora en 3 días distintos", 3, "días", 100) { Double($0.days) },
        a("d7", .walk, "Semana exploradora", "Explora en 7 días distintos", 7, "días", 300) { Double($0.days) },
        a("d30", .walk, "Hábito de explorador", "Explora en 30 días distintos", 30, "días", 1000) { Double($0.days) },
        // Alemania
        a("g_city1", .germany, "Más allá de Hannover", "Visita otra ciudad alemana", 1, "ciudades", 300) { Double($0.cities) },
        a("g_city5", .germany, "Viajero", "Visita 5 ciudades alemanas", 5, "ciudades", 800) { Double($0.cities) },
        a("g_city10", .germany, "Nómada", "Visita 10 ciudades alemanas", 10, "ciudades", 1500) { Double($0.cities) },
        a("g_city20", .germany, "Conquistador", "Visita 20 ciudades alemanas", 20, "ciudades", 3000) { Double($0.cities) },
        a("g_land1", .germany, "Postal alemana", "Descubre un monumento famoso de Alemania", 1, "monumentos", 300) { Double($0.gLand) },
        a("g_land5", .germany, "Coleccionista", "Descubre 5 monumentos de Alemania", 5, "monumentos", 800) { Double($0.gLand) },
        a("g_land15", .germany, "Gran tour", "Descubre 15 monumentos de Alemania", 15, "monumentos", 2000) { Double($0.gLand) },
        a("g_land30", .germany, "Alemania de punta a punta", "Descubre 30 monumentos de Alemania", 30, "monumentos", 4000) { Double($0.gLand) },
        a("g_pct1", .germany, "Huella en Alemania", "Despeja el 0,001 % de Alemania", 0.001, "%", 500, 4) { $0.gPct },
        a("g_pct2", .germany, "Marca en el mapa", "Despeja el 0,01 % de Alemania", 0.01, "%", 2000, 4) { $0.gPct }
    ]
}
