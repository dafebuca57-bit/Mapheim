import Foundation

let esLocale = Locale(identifier: "es_ES")

func fmt(_ v: Double, _ decimals: Int) -> String {
    let f = NumberFormatter()
    f.locale = esLocale
    f.minimumFractionDigits = decimals
    f.maximumFractionDigits = decimals
    return f.string(from: NSNumber(value: v)) ?? "\(v)"
}

/// Malla global de celdas (~50 x 50 m en Hannover). Cada celda visitada se despeja.
enum Geo {
    static let cellLat = 0.00045
    static let cellLon = 0.00075
    static let mPerDeg = 111_320.0
    static let revealM = 55.0
    static let drawM = 44.0
    static let blockShift: Int64 = 5

    static let hanS = 52.305, hanW = 9.605, hanN = 52.455, hanE = 9.918
    static let hannoverCells = 204.0e6 / cellArea(52.37)
    static let germanyCells = 357_600.0e6 / cellArea(51.0)

    static func cellArea(_ lat: Double) -> Double {
        (cellLat * mPerDeg) * (cellLon * mPerDeg * cos(lat * .pi / 180))
    }

    static func cellKey(_ lat: Double, _ lon: Double) -> Int64 {
        let y = Int64(floor((lat + 90) / cellLat))
        let x = Int64(floor((lon + 180) / cellLon))
        return (y << 32) | (x & 0xffffffff)
    }

    static func cellY(_ k: Int64) -> Int64 { k >> 32 }
    static func cellX(_ k: Int64) -> Int64 { k & 0xffffffff }
    static func centerLat(_ k: Int64) -> Double { Double(cellY(k)) * cellLat + cellLat / 2 - 90 }
    static func centerLon(_ k: Int64) -> Double { Double(cellX(k)) * cellLon + cellLon / 2 - 180 }

    static func blockKey(_ k: Int64) -> Int64 {
        ((cellY(k) >> blockShift) << 32) | (cellX(k) >> blockShift)
    }

    static func inHannover(_ lat: Double, _ lon: Double) -> Bool {
        lat >= hanS && lat <= hanN && lon >= hanW && lon <= hanE
    }

    static func cellsAround(_ lat: Double, _ lon: Double, _ radius: Double) -> [Int64] {
        let dLat = radius / mPerDeg
        let dLon = radius / (mPerDeg * cos(lat * .pi / 180))
        let y0 = Int64(floor((lat - dLat + 90) / cellLat)), y1 = Int64(floor((lat + dLat + 90) / cellLat))
        let x0 = Int64(floor((lon - dLon + 180) / cellLon)), x1 = Int64(floor((lon + dLon + 180) / cellLon))
        var out: [Int64] = []
        for y in y0...y1 {
            for x in x0...x1 {
                let cLat = Double(y) * cellLat + cellLat / 2 - 90
                let cLon = Double(x) * cellLon + cellLon / 2 - 180
                if distance(lat, lon, cLat, cLon) <= radius + 15 {
                    out.append((y << 32) | (x & 0xffffffff))
                }
            }
        }
        return out
    }

    /// Distancia en metros (haversine).
    static func distance(_ lat1: Double, _ lon1: Double, _ lat2: Double, _ lon2: Double) -> Double {
        let r = 6_371_000.0
        let dLat = (lat2 - lat1) * .pi / 180
        let dLon = (lon2 - lon1) * .pi / 180
        let a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1 * .pi / 180) * cos(lat2 * .pi / 180) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(sqrt(min(max(a, 0), 1)))
    }
}

/// Celdas exploradas, protegidas con candado porque MapKit dibuja la niebla en otros hilos.
final class FogStore {
    static let shared = FogStore()
    private let lock = NSLock()
    private var cells = Set<Int64>()
    private var blocks: [Int64: [Int64]] = [:]

    var count: Int { lock.lock(); defer { lock.unlock() }; return cells.count }

    @discardableResult
    func insert(_ k: Int64) -> Bool {
        lock.lock(); defer { lock.unlock() }
        guard cells.insert(k).inserted else { return false }
        blocks[Geo.blockKey(k), default: []].append(k)
        return true
    }

    func contains(_ k: Int64) -> Bool {
        lock.lock(); defer { lock.unlock() }
        return cells.contains(k)
    }

    func removeAll() {
        lock.lock(); defer { lock.unlock() }
        cells.removeAll(); blocks.removeAll()
    }

    /// Celdas dentro de un rectángulo de latitud/longitud.
    func cells(s: Double, w: Double, n: Double, e: Double) -> [Int64] {
        lock.lock(); defer { lock.unlock() }
        let bLat = Geo.cellLat * Double(1 << Geo.blockShift)
        let bLon = Geo.cellLon * Double(1 << Geo.blockShift)
        var out: [Int64] = []
        for (bk, list) in blocks {
            let la = Double(bk >> 32) * bLat - 90
            let lo = Double(bk & 0xffffffff) * bLon - 180
            if la > n || la + bLat < s || lo > e || lo + bLon < w { continue }
            out.append(contentsOf: list)
        }
        return out
    }
}
