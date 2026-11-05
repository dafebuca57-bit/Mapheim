import Foundation
import MapKit

/// Tipos de figura del mapa descargado (mismos números que en Android).
enum MapKind {
    static let water: UInt8 = 0, park: UInt8 = 1, forest: UInt8 = 2, grass: UInt8 = 3
    static let river: UInt8 = 10, stream: UInt8 = 11, rail: UInt8 = 12
    static let path: UInt8 = 20, minor: UInt8 = 21, secondary: UInt8 = 22, major: UInt8 = 23
    static let count = 24
}

/// Datos del mapa ya cargados. No cambian después de crearse, así que MapKit puede leerlos desde cualquier hilo.
final class OfflineMapData {
    let kinds: [UInt8]
    let names: [String?]
    /// Punto medio, ángulo y largo de cada calle con nombre (en puntos de mapa).
    let labelX: [Double], labelY: [Double], labelAngle: [Double], labelLen: [Double]
    let starts: [Int]
    let counts: [Int]
    let xs: [Double]
    let ys: [Double]
    let minX: [Double], minY: [Double], maxX: [Double], maxY: [Double]
    let grid: [Int64: [Int32]]
    static let cell = 10_000.0 // ~1 km en puntos de mapa

    init(kinds: [UInt8], names: [String?], starts: [Int], counts: [Int], xs: [Double], ys: [Double]) {
        self.kinds = kinds; self.names = names; self.starts = starts; self.counts = counts; self.xs = xs; self.ys = ys
        var lx = [Double](repeating: .nan, count: kinds.count), ly = lx, la = lx, ll = lx
        for i in 0..<kinds.count where names[i] != nil && counts[i] >= 2 {
            let s = starts[i], n = counts[i]
            var total = 0.0
            for k in (s + 1)..<(s + n) { total += hypot(xs[k] - xs[k - 1], ys[k] - ys[k - 1]) }
            var acc = 0.0
            for k in (s + 1)..<(s + n) {
                let seg = hypot(xs[k] - xs[k - 1], ys[k] - ys[k - 1])
                if acc + seg >= total / 2 || k == s + n - 1 {
                    let t = seg > 0 ? min(1, max(0, (total / 2 - acc) / seg)) : 0
                    lx[i] = xs[k - 1] + (xs[k] - xs[k - 1]) * t
                    ly[i] = ys[k - 1] + (ys[k] - ys[k - 1]) * t
                    var a = atan2(ys[k] - ys[k - 1], xs[k] - xs[k - 1])
                    if a > .pi / 2 { a -= .pi } else if a < -.pi / 2 { a += .pi }
                    la[i] = a
                    break
                }
                acc += seg
            }
            ll[i] = total
        }
        labelX = lx; labelY = ly; labelAngle = la; labelLen = ll
        var x0 = [Double](repeating: 0, count: kinds.count), y0 = x0, x1 = x0, y1 = x0
        var g: [Int64: [Int32]] = [:]
        for i in 0..<kinds.count {
            var a = Double.greatestFiniteMagnitude, b = a, c = -a, d = -a
            for k in starts[i]..<(starts[i] + counts[i]) {
                a = min(a, xs[k]); c = max(c, xs[k]); b = min(b, ys[k]); d = max(d, ys[k])
            }
            x0[i] = a; y0[i] = b; x1[i] = c; y1[i] = d
            let cell = OfflineMapData.cell
            for gx in Int64(floor(a / cell))...Int64(floor(c / cell)) {
                for gy in Int64(floor(b / cell))...Int64(floor(d / cell)) {
                    g[(gx << 32) | gy, default: []].append(Int32(i))
                }
            }
        }
        minX = x0; minY = y0; maxX = x1; maxY = y1; grid = g
    }

    /// Elementos dentro del rectángulo, sin repetir.
    func query(_ r: MKMapRect) -> [Int] {
        let cell = OfflineMapData.cell
        let gx0 = Int64(floor(r.minX / cell)), gx1 = Int64(floor(r.maxX / cell))
        let gy0 = Int64(floor(r.minY / cell)), gy1 = Int64(floor(r.maxY / cell))
        if (gx1 - gx0) * (gy1 - gy0) > 40_000 { return [] }
        var seen = Set<Int32>()
        var out: [Int] = []
        for gx in gx0...gx1 {
            for gy in gy0...gy1 {
                guard let list = grid[(gx << 32) | gy] else { continue }
                for i32 in list where !seen.contains(i32) {
                    seen.insert(i32)
                    let i = Int(i32)
                    if maxX[i] < r.minX || minX[i] > r.maxX || maxY[i] < r.minY || minY[i] > r.maxY { continue }
                    out.append(i)
                }
            }
        }
        return out
    }
}

/// Descarga y guardado del mapa de Hannover sin conexión.
enum OfflineMap {
    private static let lock = NSLock()
    private static var _current: OfflineMapData?
    static var current: OfflineMapData? {
        lock.lock(); defer { lock.unlock() }; return _current
    }
    static func setCurrent(_ d: OfflineMapData?) {
        lock.lock(); _current = d; lock.unlock()
    }

    /// true si el mapa descargado es de antes y no trae nombres de calles.
    static var missingNames: Bool {
        guard let d = current else { return false }
        return !d.names.contains { $0 != nil }
    }

    static var fileURL: URL {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("hannover_map.bin")
    }

    static var fileSizeMB: Double {
        let size = (try? FileManager.default.attributesOfItem(atPath: fileURL.path)[.size] as? NSNumber)?.doubleValue ?? 0
        return size / 1_048_576
    }

    private static let endpoints = [
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter"
    ]

    private static let bbox = "\(Geo.hanS),\(Geo.hanW),\(Geo.hanN),\(Geo.hanE)"

    private static var steps: [(String, String)] { [
        ("zonas verdes y agua", """
        [out:json][timeout:180];
        (
          way["natural"~"^(water|wood|scrub|grassland)$"](\(bbox));
          relation["natural"~"^(water|wood)$"](\(bbox));
          way["waterway"="riverbank"](\(bbox));
          way["leisure"~"^(park|garden|playground)$"](\(bbox));
          relation["leisure"~"^(park|garden)$"](\(bbox));
          way["landuse"~"^(forest|grass|meadow|recreation_ground|cemetery|allotments|village_green)$"](\(bbox));
          relation["landuse"~"^(forest|grass|meadow)$"](\(bbox));
        );
        out geom qt;
        """),
        ("calles y vías", """
        [out:json][timeout:180];
        (
          way["highway"~"^(motorway|motorway_link|trunk|trunk_link|primary|primary_link|secondary|secondary_link|tertiary|tertiary_link|unclassified|residential|living_street|pedestrian)$"](\(bbox));
          way["railway"~"^(rail|light_rail|tram)$"](\(bbox));
          way["waterway"~"^(river|canal|stream)$"](\(bbox));
        );
        out geom qt;
        """),
        ("senderos", """
        [out:json][timeout:180];
        (
          way["highway"~"^(path|cycleway|track|bridleway|steps)$"](\(bbox));
          way["highway"="footway"]["footway"!~"^(sidewalk|crossing)$"](\(bbox));
        );
        out geom qt;
        """)
    ] }

    // MARK: Descarga

    /// Descarga todo, guarda el archivo y devuelve cuántas figuras trajo.
    static func download(progress: (String) async -> Void) async throws -> Int {
        var out = Data()
        out.append(contentsOf: Array("MPH2".utf8))
        var total = 0
        for (i, step) in steps.enumerated() {
            await progress("Descargando \(step.0)\n(\(i + 1) de \(steps.count))")
            let (chunk, n) = try await fetchStep(step.1)
            out.append(chunk)
            total += n
        }
        guard total >= 100 else { throw URLError(.cannotParseResponse) }
        try out.write(to: fileURL, options: .atomic)
        return total
    }

    private static func fetchStep(_ query: String) async throws -> (Data, Int) {
        var last: Error = URLError(.cannotConnectToHost)
        for e in endpoints {
            do { return try await fetch(e, query) } catch { last = error }
        }
        throw last
    }

    private static func fetch(_ endpoint: String, _ query: String) async throws -> (Data, Int) {
        var req = URLRequest(url: URL(string: endpoint)!)
        req.httpMethod = "POST"
        req.timeoutInterval = 200
        req.setValue("application/x-www-form-urlencoded; charset=UTF-8", forHTTPHeaderField: "Content-Type")
        req.setValue("MapheimHannover/1.0 (iOS)", forHTTPHeaderField: "User-Agent")
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-._~")
        req.httpBody = ("data=" + (query.addingPercentEncoding(withAllowedCharacters: allowed) ?? "")).data(using: .utf8)
        let (data, resp) = try await URLSession.shared.data(for: req)
        guard (resp as? HTTPURLResponse)?.statusCode == 200 else { throw URLError(.badServerResponse) }
        guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let elements = root["elements"] as? [[String: Any]] else { throw URLError(.cannotParseResponse) }
        var out = Data()
        var count = 0
        for el in elements {
            guard let tags = el["tags"] as? [String: String] else { continue }
            let kind = classify(tags)
            if kind == 255 { continue }
            let isArea = kind < MapKind.river
            let named = [MapKind.river, MapKind.path, MapKind.minor, MapKind.secondary, MapKind.major].contains(kind)
            let name = named ? String((tags["name"] ?? "").prefix(60)) : ""
            if (el["type"] as? String) == "relation" {
                guard isArea, let members = el["members"] as? [[String: Any]] else { continue }
                var outers: [[Int64]] = []
                for m in members where (m["type"] as? String) == "way" {
                    let role = m["role"] as? String ?? ""
                    guard role == "outer" || role.isEmpty, let g = m["geometry"] as? [Any] else { continue }
                    let pts = points(g)
                    if pts.count >= 2 { outers.append(pts) }
                }
                for ring in rings(outers) { write(&out, kind, ring, ""); count += 1 }
            } else {
                guard let g = el["geometry"] as? [Any] else { continue }
                let pts = points(g)
                if pts.count < 2 { continue }
                if isArea && (pts.count < 4 || pts.first != pts.last) { continue }
                write(&out, kind, pts, name); count += 1
            }
        }
        return (out, count)
    }

    private static func points(_ g: [Any]) -> [Int64] {
        var out: [Int64] = []
        out.reserveCapacity(g.count)
        for case let p as [String: Any] in g {
            guard let lat = (p["lat"] as? NSNumber)?.doubleValue, let lon = (p["lon"] as? NSNumber)?.doubleValue else { continue }
            let a = Int64(Int32(truncatingIfNeeded: Int64((lat * 1e7).rounded())))
            let b = Int64(Int32(truncatingIfNeeded: Int64((lon * 1e7).rounded())))
            out.append((a << 32) | (b & 0xffffffff))
        }
        return out
    }

    private static func classify(_ t: [String: String]) -> UInt8 {
        if let hw = t["highway"] {
            switch hw {
            case "motorway", "motorway_link", "trunk", "trunk_link", "primary", "primary_link": return MapKind.major
            case "secondary", "secondary_link", "tertiary", "tertiary_link": return MapKind.secondary
            case "unclassified", "residential", "living_street", "pedestrian": return MapKind.minor
            case "footway", "path", "cycleway", "track", "bridleway", "steps": return MapKind.path
            default: return 255
            }
        }
        if let r = t["railway"], ["rail", "light_rail", "tram"].contains(r) { return MapKind.rail }
        if let w = t["waterway"] {
            switch w {
            case "river", "canal": return MapKind.river
            case "stream": return MapKind.stream
            case "riverbank": return MapKind.water
            default: return 255
            }
        }
        switch t["natural"] {
        case "water": return MapKind.water
        case "wood", "scrub": return MapKind.forest
        case "grassland": return MapKind.grass
        default: break
        }
        switch t["landuse"] {
        case "forest": return MapKind.forest
        case "grass", "meadow", "recreation_ground", "cemetery", "allotments", "village_green": return MapKind.grass
        default: break
        }
        if let l = t["leisure"], ["park", "garden", "playground"].contains(l) { return MapKind.park }
        return 255
    }

    /// Une los tramos exteriores de una relación en anillos cerrados.
    private static func rings(_ segs: [[Int64]]) -> [[Int64]] {
        var pool = segs
        var out: [[Int64]] = []
        while let first = pool.popLast() {
            var ring = first
            var guardCount = 0
            while ring.first != ring.last && guardCount < 5000 {
                guardCount += 1
                let end = ring.last!
                guard let i = pool.firstIndex(where: { $0.first == end || $0.last == end }) else { break }
                let s = pool.remove(at: i)
                if s.first == end { ring.append(contentsOf: s.dropFirst()) }
                else { ring.append(contentsOf: s.reversed().dropFirst()) }
            }
            if ring.count >= 4 && ring.first == ring.last { out.append(ring) }
        }
        return out
    }

    private static func write(_ out: inout Data, _ kind: UInt8, _ pts: [Int64], _ name: String) {
        out.append(kind)
        let nameBytes = Array(name.utf8.prefix(1000))
        var len = UInt16(nameBytes.count).littleEndian
        withUnsafeBytes(of: &len) { out.append(contentsOf: $0) }
        out.append(contentsOf: nameBytes)
        var n = UInt32(pts.count).littleEndian
        withUnsafeBytes(of: &n) { out.append(contentsOf: $0) }
        for p in pts {
            var a = Int32(truncatingIfNeeded: p >> 32).littleEndian
            var b = Int32(truncatingIfNeeded: p).littleEndian
            withUnsafeBytes(of: &a) { out.append(contentsOf: $0) }
            withUnsafeBytes(of: &b) { out.append(contentsOf: $0) }
        }
    }

    // MARK: Lectura

    static func load() -> OfflineMapData? {
        guard let data = try? Data(contentsOf: fileURL), data.count > 4 else { return nil }
        let magic = data.prefix(4)
        let withNames = magic == Data("MPH2".utf8)
        guard withNames || magic == Data("MPH1".utf8) else { return nil }
        var kinds: [UInt8] = [], starts: [Int] = [], counts: [Int] = []
        var names: [String?] = []
        var xs: [Double] = [], ys: [Double] = []
        data.withUnsafeBytes { (raw: UnsafeRawBufferPointer) in
            var o = 4
            let end = raw.count
            while o + 5 <= end {
                let kind = raw[o]
                o += 1
                var name: String? = nil
                if withNames {
                    guard o + 2 <= end else { break }
                    let len = Int(UInt16(littleEndian: raw.loadUnaligned(fromByteOffset: o, as: UInt16.self)))
                    o += 2
                    guard o + len <= end else { break }
                    if len > 0 { name = String(decoding: UnsafeRawBufferPointer(rebasing: raw[o..<(o + len)]), as: UTF8.self) }
                    o += len
                }
                guard o + 4 <= end else { break }
                let n = Int(UInt32(littleEndian: raw.loadUnaligned(fromByteOffset: o, as: UInt32.self)))
                o += 4
                if o + n * 8 > end { break }
                kinds.append(kind); names.append(name); starts.append(xs.count); counts.append(n)
                for _ in 0..<n {
                    let lat = Double(Int32(littleEndian: raw.loadUnaligned(fromByteOffset: o, as: Int32.self))) / 1e7
                    let lon = Double(Int32(littleEndian: raw.loadUnaligned(fromByteOffset: o + 4, as: Int32.self))) / 1e7
                    o += 8
                    let mp = MKMapPoint(CLLocationCoordinate2D(latitude: lat, longitude: lon))
                    xs.append(mp.x); ys.append(mp.y)
                }
            }
        }
        guard !kinds.isEmpty else { return nil }
        return OfflineMapData(kinds: kinds, names: names, starts: starts, counts: counts, xs: xs, ys: ys)
    }

    // MARK: Colores

    struct Palette {
        var land, water, park, forest, grass, road, secondary, major, casing, path, rail: CGColor
    }

    /// Colores base (estilo OpenStreetMap) teñidos con el estilo de mapa equipado.
    static func palette(_ styleId: String) -> Palette {
        let m = ShopCatalog.mapMatrix(styleId)
        func t(_ r: CGFloat, _ g: CGFloat, _ b: CGFloat) -> CGColor {
            func ch(_ row: Int) -> CGFloat {
                let v = m[row * 5] * r + m[row * 5 + 1] * g + m[row * 5 + 2] * b + m[row * 5 + 4]
                return min(255, max(0, v)) / 255
            }
            return UIColor(red: ch(0), green: ch(1), blue: ch(2), alpha: 1).cgColor
        }
        return Palette(
            land: t(242, 239, 233), water: t(170, 211, 223), park: t(200, 230, 180),
            forest: t(173, 209, 158), grass: t(205, 235, 176), road: t(255, 255, 255),
            secondary: t(247, 250, 191), major: t(252, 214, 164), casing: t(190, 180, 170),
            path: t(150, 120, 90), rail: t(130, 130, 130)
        )
    }
}

// MARK: - Capa del mapa

final class OfflineMapOverlay: NSObject, MKOverlay {
    let coordinate = CLLocationCoordinate2D(latitude: 52.37, longitude: 9.74)
    /// Solo el área de Hannover, para no gastar batería dibujando fuera de ella.
    let boundingMapRect: MKMapRect = {
        let nw = MKMapPoint(CLLocationCoordinate2D(latitude: Geo.hanN + 0.02, longitude: Geo.hanW - 0.03))
        let se = MKMapPoint(CLLocationCoordinate2D(latitude: Geo.hanS - 0.02, longitude: Geo.hanE + 0.03))
        return MKMapRect(x: nw.x, y: nw.y, width: se.x - nw.x, height: se.y - nw.y)
    }()
}

/// Dibuja el mapa descargado. Queda debajo de los mosaicos: se ve cuando no hay internet.
final class OfflineMapRenderer: MKOverlayRenderer {
    private let lock = NSLock()
    private var _palette = OfflineMap.palette("map_parchment")
    var palette: OfflineMap.Palette {
        get { lock.lock(); defer { lock.unlock() }; return _palette }
        set { lock.lock(); _palette = newValue; lock.unlock() }
    }

    override func draw(_ mapRect: MKMapRect, zoomScale: MKZoomScale, in ctx: CGContext) {
        guard let d = OfflineMap.current else { return }
        let pal = palette
        ctx.setFillColor(pal.land)
        ctx.fill(rect(for: mapRect))

        let zs = Double(zoomScale)
        let z = log2(zs * MKMapSize.world.width / 256)
        let ppm = MKMapPointsPerMeterAtLatitude(52.37)
        func w(_ meters: Double, _ minPts: Double) -> CGFloat { CGFloat(max(minPts / zs, meters * ppm)) }
        let margin = max(30 * ppm, 10 / zs)

        var buckets = [[Int]](repeating: [], count: MapKind.count)
        for i in d.query(mapRect.insetBy(dx: -margin, dy: -margin)) {
            let k = d.kinds[i]
            switch k {
            case MapKind.path: if z < 15 { continue }
            case MapKind.minor: if z < 13.5 { continue }
            case MapKind.stream: if z < 14 { continue }
            case MapKind.secondary: if z < 11.5 { continue }
            default: break
            }
            buckets[Int(k)].append(i)
        }

        func addPaths(_ kind: UInt8, close: Bool) {
            for i in buckets[Int(kind)] {
                let s = d.starts[i]
                for k in 0..<d.counts[i] {
                    let p = point(for: MKMapPoint(x: d.xs[s + k], y: d.ys[s + k]))
                    if k == 0 { ctx.move(to: p) } else { ctx.addLine(to: p) }
                }
                if close { ctx.closePath() }
            }
        }
        func fill(_ kind: UInt8, _ color: CGColor) {
            guard !buckets[Int(kind)].isEmpty else { return }
            ctx.setFillColor(color)
            addPaths(kind, close: true)
            ctx.fillPath()
        }
        func stroke(_ kind: UInt8, _ color: CGColor, _ width: CGFloat) {
            guard !buckets[Int(kind)].isEmpty else { return }
            ctx.setStrokeColor(color)
            ctx.setLineWidth(width)
            addPaths(kind, close: false)
            ctx.strokePath()
        }

        ctx.setLineCap(.round)
        ctx.setLineJoin(.round)
        fill(MapKind.grass, pal.grass)
        fill(MapKind.park, pal.park)
        fill(MapKind.forest, pal.forest)
        fill(MapKind.water, pal.water)

        stroke(MapKind.stream, pal.water, w(3, 1))
        stroke(MapKind.river, pal.water, w(22, 2))
        ctx.setLineDash(phase: 0, lengths: [CGFloat(4 / zs), CGFloat(3 / zs)])
        stroke(MapKind.path, pal.path, w(1.5, 1))
        ctx.setLineDash(phase: 0, lengths: [])
        stroke(MapKind.rail, pal.rail, w(2.5, 1))

        let minorW = w(8, 1.3), secW = w(12, 2), majW = w(16, 2.6)
        let casing = max(CGFloat(1.2 / zs), minorW * 0.25)
        if z >= 14 {
            stroke(MapKind.minor, pal.casing, minorW + casing)
            stroke(MapKind.secondary, pal.casing, secW + casing)
            stroke(MapKind.major, pal.casing, majW + casing)
        }
        stroke(MapKind.minor, pal.road, minorW)
        stroke(MapKind.secondary, pal.secondary, secW)
        stroke(MapKind.major, pal.major, majW)

        if z >= 14 { drawLabels(d, mapRect, zs: zs, z: z, ctx: ctx) }
    }

    private static let roadFont = UIFont(name: "Cinzel-Bold", size: 11.5) ?? .boldSystemFont(ofSize: 11.5)
    private static let smallFont = UIFont(name: "Cinzel-Bold", size: 10.5) ?? .boldSystemFont(ofSize: 10.5)
    private static let inkColor = UIColor(red: 70 / 255, green: 50 / 255, blue: 32 / 255, alpha: 1)
    private static let riverInk = UIColor(red: 40 / 255, green: 85 / 255, blue: 120 / 255, alpha: 1)
    private static let haloColor = UIColor(red: 248 / 255, green: 244 / 255, blue: 236 / 255, alpha: 0.88)

    /// Nombres de calles. La elección depende solo de la zona y el zoom, para que
    /// los mosaicos vecinos dibujen las mismas etiquetas y no se corten.
    private func drawLabels(_ d: OfflineMapData, _ mapRect: MKMapRect, zs: Double, z: Double, ctx: CGContext) {
        let cell = 300 / zs
        let half = 170 / zs
        let x0 = floor((mapRect.minX - half) / cell) * cell
        let y0 = floor((mapRect.minY - half) / cell) * cell
        let x1 = ceil((mapRect.maxX + half) / cell) * cell
        let y1 = ceil((mapRect.maxY + half) / cell) * cell
        let area = MKMapRect(x: x0, y: y0, width: x1 - x0, height: y1 - y0)

        func minZoom(_ k: UInt8) -> Double? {
            switch k {
            case MapKind.major, MapKind.secondary: return 14.5
            case MapKind.river: return 14
            case MapKind.minor: return 16
            case MapKind.path: return 17
            default: return nil
            }
        }

        // Por celda: una etiqueta por tipo de vía (la calle más larga)
        var best: [String: (Int, Double)] = [:]
        for i in d.query(area) {
            guard let name = d.names[i], let mz = minZoom(d.kinds[i]), z >= mz, !d.labelX[i].isNaN else { continue }
            let lx = d.labelX[i], ly = d.labelY[i]
            guard lx >= x0, lx < x1, ly >= y0, ly < y1 else { continue }
            let big = d.kinds[i] == MapKind.major || d.kinds[i] == MapKind.secondary
            let font = big ? Self.roadFont : Self.smallFont
            let widthPts = (name as NSString).size(withAttributes: [.font: font]).width
            if d.labelLen[i] * zs < Double(widthPts) * 1.15 { continue }
            let key = "\(Int(floor(lx / cell)))|\(Int(floor(ly / cell)))|\(d.kinds[i] == MapKind.minor || d.kinds[i] == MapKind.path ? 0 : 1)"
            if let cur = best[key], cur.1 >= d.labelLen[i] { continue }
            best[key] = (i, d.labelLen[i])
        }
        if best.isEmpty { return }

        UIGraphicsPushContext(ctx)
        defer { UIGraphicsPopContext() }
        let visible = mapRect.insetBy(dx: -half, dy: -half)
        for (_, entry) in best {
            let i = entry.0
            let mp = MKMapPoint(x: d.labelX[i], y: d.labelY[i])
            guard visible.contains(mp), let name = d.names[i] else { continue }
            let big = d.kinds[i] == MapKind.major || d.kinds[i] == MapKind.secondary
            let font = big ? Self.roadFont : Self.smallFont
            let ink = d.kinds[i] == MapKind.river ? Self.riverInk : Self.inkColor
            let haloText = NSAttributedString(string: name, attributes: [
                .font: font, .strokeColor: Self.haloColor, .strokeWidth: 9, .foregroundColor: UIColor.clear
            ])
            let inkText = NSAttributedString(string: name, attributes: [.font: font, .foregroundColor: ink])
            let size = inkText.size()
            let p = point(for: mp)
            ctx.saveGState()
            ctx.translateBy(x: p.x, y: p.y)
            ctx.rotate(by: CGFloat(d.labelAngle[i]))
            ctx.scaleBy(x: CGFloat(1 / zs), y: CGFloat(1 / zs))
            let origin = CGPoint(x: -size.width / 2, y: -size.height / 2)
            haloText.draw(at: origin)
            inkText.draw(at: origin)
            ctx.restoreGState()
        }
    }
}
