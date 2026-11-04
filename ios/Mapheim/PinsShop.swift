import Foundation
import UIKit
import CoreImage

// MARK: - Pines

enum PinType: String, CaseIterable, Codable {
    case food, home, train, bus, tram, cafe, mead, shop, wc, parking, favorite, note

    var label: String {
        switch self {
        case .food: return "Comida"
        case .home: return "Casa"
        case .train: return "Tren"
        case .bus: return "Bus"
        case .tram: return "Stadtbahn"
        case .cafe: return "Café"
        case .mead: return "Taberna"
        case .shop: return "Tienda"
        case .wc: return "Baño"
        case .parking: return "Parqueo"
        case .favorite: return "Favorito"
        case .note: return "Nota"
        }
    }

    var symbol: String {
        switch self {
        case .food: return "fork.knife"
        case .home: return "house.fill"
        case .train: return "train.side.front.car"
        case .bus: return "bus.fill"
        case .tram: return "tram.fill"
        case .cafe: return "cup.and.saucer.fill"
        case .mead: return "mug.fill"
        case .shop: return "cart.fill"
        case .wc: return "toilet.fill"
        case .parking: return "parkingsign"
        case .favorite: return "star.fill"
        case .note: return "pencil"
        }
    }

    var color: UIColor {
        switch self {
        case .food: return UIColor(r: 200, g: 100, b: 43)
        case .home: return UIColor(r: 184, g: 135, b: 74)
        case .train: return UIColor(r: 78, g: 110, b: 142)
        case .bus: return UIColor(r: 63, g: 127, b: 122)
        case .tram: return UIColor(r: 106, g: 90, b: 142)
        case .cafe: return UIColor(r: 122, g: 82, b: 54)
        case .mead: return UIColor(r: 166, g: 61, b: 47)
        case .shop: return UIColor(r: 142, g: 122, b: 58)
        case .wc: return UIColor(r: 90, g: 122, b: 154)
        case .parking: return UIColor(r: 74, g: 90, b: 142)
        case .favorite: return UIColor(r: 217, g: 161, b: 59)
        case .note: return UIColor(r: 110, g: 142, b: 78)
        }
    }
}

struct Pin: Identifiable, Codable, Equatable {
    let id: Int64
    let type: PinType
    let lat: Double
    let lon: Double
    let note: String
    let created: Date
    var title: String { note.isEmpty ? type.label : note }
}

// MARK: - Mercader

enum ShopCat: String, CaseIterable {
    case map, fog, cursor, pin
    var title: String {
        switch self {
        case .map: return "Estilos de mapa"
        case .fog: return "Niebla"
        case .cursor: return "Tu marcador"
        case .pin: return "Estilo de pines"
        }
    }
    var hint: String {
        switch self {
        case .map: return "Cambia el color de todo el mapa"
        case .fog: return "El color de lo que aún no exploras"
        case .cursor: return "Cómo te ves en el mapa"
        case .pin: return "El marco de todos tus pines"
        }
    }
}

struct ShopItem: Identifiable {
    let id: String
    let cat: ShopCat
    let name: String
    let desc: String
    let price: Int
}

enum ShopCatalog {
    static let items: [ShopItem] = [
        ShopItem(id: "map_parchment", cat: .map, name: "Pergamino", desc: "Mapa de explorador, tonos de cuero viejo", price: 0),
        ShopItem(id: "map_meadows", cat: .map, name: "Praderas", desc: "Verde cálido de las primeras tierras", price: 150),
        ShopItem(id: "map_blackforest", cat: .map, name: "Selva Negra", desc: "Oscuro y musgoso, como bajo los abetos", price: 300),
        ShopItem(id: "map_swamp", cat: .map, name: "Pantano", desc: "Aguas turbias y barro verdoso", price: 400),
        ShopItem(id: "map_mountain", cat: .map, name: "Montaña", desc: "Escarcha y piedra azulada", price: 500),
        ShopItem(id: "map_plains", cat: .map, name: "Llanuras", desc: "Trigo dorado bajo el sol", price: 650),
        ShopItem(id: "map_mist", cat: .map, name: "Tierras de Bruma", desc: "Violeta y misterio", price: 900),
        ShopItem(id: "map_ash", cat: .map, name: "Tierras de Ceniza", desc: "Brasas y roca volcánica", price: 1200),

        ShopItem(id: "fog_cold", cat: .fog, name: "Niebla fría", desc: "La bruma del norte", price: 0),
        ShopItem(id: "fog_swamp", cat: .fog, name: "Vapor del pantano", desc: "Verde y espesa", price: 200),
        ShopItem(id: "fog_blizzard", cat: .fog, name: "Ventisca", desc: "Blanca como la nieve de la montaña", price: 350),
        ShopItem(id: "fog_mist", cat: .fog, name: "Bruma violeta", desc: "Niebla que esconde secretos", price: 500),
        ShopItem(id: "fog_ash", cat: .fog, name: "Humo de ceniza", desc: "Oscuridad rojiza de los volcanes", price: 800),

        ShopItem(id: "cur_torch", cat: .cursor, name: "Antorcha", desc: "Una llama sencilla para abrir camino", price: 0),
        ShopItem(id: "cur_shield", cat: .cursor, name: "Escudo redondo", desc: "Madera, bronce y un umbo al centro", price: 250),
        ShopItem(id: "cur_rune", cat: .cursor, name: "Piedra rúnica", desc: "Una runa ᛉ que brilla en la piedra", price: 400),
        ShopItem(id: "cur_wisp", cat: .cursor, name: "Fuego fatuo", desc: "Una luz azul que aparta la niebla", price: 700),
        ShopItem(id: "cur_ember", cat: .cursor, name: "Núcleo ardiente", desc: "Un corazón de fuego con llamaradas", price: 1000),

        ShopItem(id: "pin_bronze", cat: .pin, name: "Bronce", desc: "Aro de bronce clásico", price: 0),
        ShopItem(id: "pin_iron", cat: .pin, name: "Hierro", desc: "Aro gris, sobrio y fuerte", price: 150),
        ShopItem(id: "pin_shieldrim", cat: .pin, name: "Escudo remachado", desc: "Doble aro con cuatro remaches", price: 300),
        ShopItem(id: "pin_runestone", cat: .pin, name: "Runa", desc: "Pines en forma de piedra rúnica", price: 450),
        ShopItem(id: "pin_gold", cat: .pin, name: "Oro de jarl", desc: "Aro de oro grueso, digno de un jarl", price: 800)
    ]

    static func defaultId(_ cat: ShopCat) -> String { items.first { $0.cat == cat && $0.price == 0 }!.id }

    /// Matriz de color 4x5 (como en Android): [r, g, b, a, offset] por fila, offsets en 0..255.
    static func mapMatrix(_ id: String) -> [CGFloat] {
        switch id {
        case "map_meadows": return tint(0.85, 0.95, 1.05, 0.78, 6, 10, -8)
        case "map_blackforest": return tint(0.30, 0.62, 0.74, 0.60, -6, 0, -8)
        case "map_swamp": return tint(0.30, 0.78, 0.82, 0.52, -4, 0, -16)
        case "map_mountain": return tint(0.12, 0.88, 0.95, 1.10, 18, 22, 30)
        case "map_plains": return tint(0.50, 1.05, 0.94, 0.58, 22, 10, -14)
        case "map_mist": return tint(0.20, 0.80, 0.70, 1.02, 0, -6, 14)
        case "map_ash": return tint(0.15, 0.95, 0.55, 0.48, -8, -14, -16)
        default: return tint(0.35, 0.95, 0.88, 0.70, 14, 4, -12)
        }
    }

    private static func tint(_ sat: CGFloat, _ r: CGFloat, _ g: CGFloat, _ b: CGFloat,
                             _ or: CGFloat, _ og: CGFloat, _ ob: CGFloat) -> [CGFloat] {
        let inv = 1 - sat
        let wr: CGFloat = 0.213, wg: CGFloat = 0.715, wb: CGFloat = 0.072
        let s: [[CGFloat]] = [
            [wr * inv + sat, wg * inv, wb * inv],
            [wr * inv, wg * inv + sat, wb * inv],
            [wr * inv, wg * inv, wb * inv + sat]
        ]
        let k = [r, g, b], off = [or, og, ob]
        var m: [CGFloat] = []
        for i in 0..<3 { m += [s[i][0] * k[i], s[i][1] * k[i], s[i][2] * k[i], 0, off[i]] }
        m += [0, 0, 0, 1, 0]
        return m
    }

    /// Aplica la matriz a una imagen (para los mosaicos del mapa y las vistas previas).
    static func filter(_ image: CIImage, matrix m: [CGFloat]) -> CIImage {
        guard let f = CIFilter(name: "CIColorMatrix") else { return image }
        f.setValue(image, forKey: kCIInputImageKey)
        f.setValue(CIVector(x: m[0], y: m[1], z: m[2], w: m[3]), forKey: "inputRVector")
        f.setValue(CIVector(x: m[5], y: m[6], z: m[7], w: m[8]), forKey: "inputGVector")
        f.setValue(CIVector(x: m[10], y: m[11], z: m[12], w: m[13]), forKey: "inputBVector")
        f.setValue(CIVector(x: m[15], y: m[16], z: m[17], w: m[18]), forKey: "inputAVector")
        f.setValue(CIVector(x: m[4] / 255, y: m[9] / 255, z: m[14] / 255, w: 0), forKey: "inputBiasVector")
        return f.outputImage ?? image
    }

    static func fogColor(_ id: String) -> UIColor {
        switch id {
        case "fog_swamp": return UIColor(r: 26, g: 34, b: 24, a: 0.94)
        case "fog_blizzard": return UIColor(r: 200, g: 210, b: 224, a: 0.93)
        case "fog_mist": return UIColor(r: 36, g: 28, b: 52, a: 0.94)
        case "fog_ash": return UIColor(r: 36, g: 18, b: 14, a: 0.95)
        default: return UIColor(r: 20, g: 24, b: 31, a: 0.94)
        }
    }
}

extension UIColor {
    convenience init(r: CGFloat, g: CGFloat, b: CGFloat, a: CGFloat = 1) {
        self.init(red: r / 255, green: g / 255, blue: b / 255, alpha: a)
    }
}
