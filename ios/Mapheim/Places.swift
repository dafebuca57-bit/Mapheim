import Foundation

enum Cat: String, Codable {
    case sight, park, forest, landmark
    var label: String {
        switch self {
        case .sight: return "Lugar de interés"
        case .park: return "Parque o jardín"
        case .forest: return "Bosque"
        case .landmark: return "Monumento de Alemania"
        }
    }
}

enum Scope: String, Codable { case hannover, germany }

/// Punto de interés. Con límites es un área (se descubre al entrar); sin ellos, por cercanía.
struct Poi: Identifiable {
    let id: String
    let name: String
    let cat: Cat
    let scope: Scope
    var lat: Double
    var lon: Double
    var radius: Double = 70
    var bounds: (s: Double, w: Double, n: Double, e: Double)? = nil
    var famous = false
    var matchKey: String? = nil

    var isGreen: Bool { cat == .park || cat == .forest }

    func covers(_ la: Double, _ lo: Double) -> Bool {
        if let b = bounds { return la >= b.s && la <= b.n && lo >= b.w && lo <= b.e }
        return Geo.distance(lat, lon, la, lo) <= radius
    }

    static func node(_ id: String, _ name: String, _ lat: Double, _ lon: Double, _ key: String? = nil, _ radius: Double = 70) -> Poi {
        Poi(id: id, name: name, cat: .sight, scope: .hannover, lat: lat, lon: lon, radius: radius, famous: true, matchKey: key)
    }

    static func area(_ id: String, _ name: String, _ cat: Cat, _ s: Double, _ w: Double, _ n: Double, _ e: Double, _ key: String? = nil) -> Poi {
        Poi(id: id, name: name, cat: cat, scope: .hannover, lat: (s + n) / 2, lon: (w + e) / 2, radius: 0,
            bounds: (s, w, n, e), famous: true, matchKey: key)
    }

    static func landmark(_ id: String, _ name: String, _ lat: Double, _ lon: Double, _ radius: Double = 350) -> Poi {
        Poi(id: id, name: name, cat: .landmark, scope: .germany, lat: lat, lon: lon, radius: radius, famous: true)
    }
}

struct City {
    let id: String, name: String, lat: Double, lon: Double, radiusKm: Double
}

/// Lugares escogidos a mano (los mismos que en Android).
enum Curated {
    static let herrenhausen = ["h_grosser_garten", "h_berggarten", "h_georgengarten"]
    static let altstadt = ["h_marktkirche", "h_altes_rathaus", "h_leineschloss", "h_beginenturm", "h_leibnizhaus"]
    static let eilenriede = "h_eilenriede"
    static let maschsee = "h_maschsee"

    static let hannover: [Poi] = [
        .node("h_neues_rathaus", "Neues Rathaus", 52.3671, 9.7383, "neues rathaus", 90),
        .node("h_marktkirche", "Marktkirche", 52.3717, 9.7354, "marktkirche"),
        .node("h_altes_rathaus", "Altes Rathaus", 52.3724, 9.7359, "altes rathaus"),
        .node("h_leineschloss", "Leineschloss", 52.3703, 9.7330, "leineschloss"),
        .node("h_beginenturm", "Beginenturm", 52.3720, 9.7318, "beginenturm"),
        .node("h_leibnizhaus", "Leibnizhaus", 52.3707, 9.7338, "leibnizhaus"),
        .node("h_ballhof", "Ballhof", 52.3727, 9.7325, "ballhof"),
        .node("h_kreuzkirche", "Kreuzkirche", 52.3734, 9.7328, "kreuzkirche"),
        .node("h_nanas", "Las Nanas de Niki de Saint Phalle", 52.3729, 9.7296, "nanas"),
        .node("h_aegidienkirche", "Aegidienkirche", 52.3693, 9.7390, "aegidienkirche"),
        .node("h_kroepcke", "Kröpcke", 52.3745, 9.7385, "kröpcke"),
        .node("h_oper", "Staatsoper Hannover", 52.3738, 9.7410, "opernhaus", 90),
        .node("h_hbf", "Hauptbahnhof y Ernst-August", 52.3767, 9.7413, nil, 100),
        .node("h_waterloo", "Waterloosäule", 52.3664, 9.7294, "waterloo"),
        .node("h_landesmuseum", "Landesmuseum Hannover", 52.3647, 9.7393, "landesmuseum"),
        .node("h_sprengel", "Sprengel Museum", 52.3620, 9.7434, "sprengel", 90),
        .node("h_kestner", "Museum August Kestner", 52.3687, 9.7362, "kestner"),
        .node("h_historisches", "Historisches Museum", 52.3712, 9.7322, "historisches museum"),
        .node("h_welfenschloss", "Welfenschloss (Universidad)", 52.3823, 9.7178, "welfenschloss", 100),
        .node("h_schloss_herrenhausen", "Schloss Herrenhausen", 52.3915, 9.6990, "schloss herrenhausen", 100),
        .node("h_zoo", "Erlebnis-Zoo Hannover", 52.3843, 9.7712, "zoo", 250),
        .node("h_arena", "Heinz von Heiden Arena", 52.3601, 9.7312, "arena", 160),
        .node("h_christuskirche", "Christuskirche", 52.3836, 9.7290, "christuskirche"),
        .node("h_lindener_berg", "Lindener Berg", 52.3640, 9.7090, nil, 300),
        .node("h_expo", "Expo Plaza", 52.3195, 9.8170, "expo plaza", 180),
        .area("h_grosser_garten", "Großer Garten", .park, 52.3880, 9.6945, 52.3935, 9.7020, "großer garten"),
        .area("h_berggarten", "Berggarten", .park, 52.3925, 9.7010, 52.3965, 9.7075, "berggarten"),
        .area("h_georgengarten", "Georgengarten", .park, 52.3815, 9.7075, 52.3905, 9.7175, "georgengarten"),
        .area("h_welfengarten", "Welfengarten", .park, 52.3805, 9.7160, 52.3845, 9.7215, "welfengarten"),
        .area("h_maschpark", "Maschpark", .park, 52.3640, 9.7350, 52.3675, 9.7420, "maschpark"),
        .area("h_maschsee", "Maschsee", .park, 52.3420, 9.7365, 52.3650, 9.7540, "maschsee"),
        .area("h_stadtpark", "Stadtpark", .park, 52.3730, 9.7900, 52.3800, 9.8020, "stadtpark"),
        .area("h_hermann_loens", "Hermann-Löns-Park", .park, 52.3740, 9.8100, 52.3830, 9.8300, "hermann-löns"),
        .area("h_eilenriede", "Eilenriede", .forest, 52.3600, 9.7480, 52.4040, 9.8030, "eilenriede"),
        .area("h_tiergarten", "Tiergarten", .forest, 52.3570, 9.8280, 52.3690, 9.8560, "tiergarten")
    ]

    static let germany: [Poi] = [
        .landmark("g_marienburg", "Schloss Marienburg", 52.1700, 9.7639),
        .landmark("g_steinhuder_meer", "Steinhuder Meer", 52.4580, 9.3450, 2500),
        .landmark("g_hameln", "Casco antiguo de Hameln", 52.1036, 9.3583, 450),
        .landmark("g_hildesheim_dom", "Catedral de Hildesheim", 52.1491, 9.9468),
        .landmark("g_celle", "Castillo de Celle", 52.6247, 10.0786),
        .landmark("g_autostadt", "Autostadt Wolfsburg", 52.4301, 10.7915, 500),
        .landmark("g_braunschweig", "Burg Dankwarderode", 52.2638, 10.5233),
        .landmark("g_goslar", "Kaiserpfalz Goslar", 51.9034, 10.4268),
        .landmark("g_brocken", "Brocken (Harz)", 51.7991, 10.6156, 600),
        .landmark("g_goettingen", "Gänseliesel de Göttingen", 51.5334, 9.9356),
        .landmark("g_hermann", "Hermannsdenkmal", 51.9113, 8.8394),
        .landmark("g_externsteine", "Externsteine", 51.8688, 8.9173),
        .landmark("g_elbphilharmonie", "Elbphilharmonie", 53.5413, 9.9842),
        .landmark("g_wunderland", "Miniatur Wunderland", 53.5437, 9.9886, 250),
        .landmark("g_bremen", "Plaza del Mercado de Bremen", 53.0758, 8.8072),
        .landmark("g_holstentor", "Holstentor (Lübeck)", 53.8664, 10.6797),
        .landmark("g_schwerin", "Castillo de Schwerin", 53.6242, 11.4185),
        .landmark("g_ruegen", "Acantilados de Rügen", 54.5735, 13.6627, 800),
        .landmark("g_brandenburger", "Puerta de Brandeburgo", 52.5163, 13.3777),
        .landmark("g_reichstag", "Reichstag", 52.5186, 13.3762),
        .landmark("g_fernsehturm", "Fernsehturm de Berlín", 52.5208, 13.4094),
        .landmark("g_eastside", "East Side Gallery", 52.5050, 13.4397, 600),
        .landmark("g_sanssouci", "Palacio de Sanssouci", 52.4035, 13.0389, 500),
        .landmark("g_frauenkirche", "Frauenkirche de Dresde", 51.0519, 13.7415),
        .landmark("g_zwinger", "Zwinger de Dresde", 51.0530, 13.7337),
        .landmark("g_wartburg", "Castillo de Wartburg", 50.9665, 10.3060),
        .landmark("g_koelner_dom", "Catedral de Colonia", 50.9413, 6.9583),
        .landmark("g_aachen", "Catedral de Aquisgrán", 50.7747, 6.0839),
        .landmark("g_loreley", "Loreley", 50.1395, 7.7287, 600),
        .landmark("g_porta_nigra", "Porta Nigra (Tréveris)", 49.7597, 6.6440),
        .landmark("g_roemer", "Römer de Frankfurt", 50.1106, 8.6821),
        .landmark("g_heidelberg", "Castillo de Heidelberg", 49.4106, 8.7153),
        .landmark("g_nuernberg", "Castillo Imperial de Núremberg", 49.4577, 11.0755),
        .landmark("g_ulm", "Catedral de Ulm", 48.3985, 9.9925),
        .landmark("g_marienplatz", "Marienplatz (Múnich)", 48.1374, 11.5755),
        .landmark("g_neuschwanstein", "Castillo de Neuschwanstein", 47.5576, 10.7498, 450),
        .landmark("g_zugspitze", "Zugspitze", 47.4211, 10.9853, 800),
        .landmark("g_koenigssee", "Königssee", 47.5536, 12.9786, 1200)
    ]

    static let cities: [City] = [
        City(id: "c_berlin", name: "Berlín", lat: 52.5200, lon: 13.4050, radiusKm: 12),
        City(id: "c_hamburg", name: "Hamburgo", lat: 53.5511, lon: 9.9937, radiusKm: 10),
        City(id: "c_muenchen", name: "Múnich", lat: 48.1372, lon: 11.5756, radiusKm: 9),
        City(id: "c_koeln", name: "Colonia", lat: 50.9375, lon: 6.9603, radiusKm: 8),
        City(id: "c_frankfurt", name: "Fráncfort", lat: 50.1109, lon: 8.6821, radiusKm: 7),
        City(id: "c_stuttgart", name: "Stuttgart", lat: 48.7758, lon: 9.1829, radiusKm: 6),
        City(id: "c_duesseldorf", name: "Düsseldorf", lat: 51.2277, lon: 6.7735, radiusKm: 6),
        City(id: "c_leipzig", name: "Leipzig", lat: 51.3397, lon: 12.3731, radiusKm: 6),
        City(id: "c_dortmund", name: "Dortmund", lat: 51.5136, lon: 7.4653, radiusKm: 6),
        City(id: "c_essen", name: "Essen", lat: 51.4556, lon: 7.0116, radiusKm: 6),
        City(id: "c_bremen", name: "Bremen", lat: 53.0793, lon: 8.8017, radiusKm: 7),
        City(id: "c_dresden", name: "Dresde", lat: 51.0504, lon: 13.7373, radiusKm: 7),
        City(id: "c_nuernberg", name: "Núremberg", lat: 49.4521, lon: 11.0767, radiusKm: 6),
        City(id: "c_braunschweig", name: "Brunswick", lat: 52.2689, lon: 10.5268, radiusKm: 5),
        City(id: "c_wolfsburg", name: "Wolfsburgo", lat: 52.4227, lon: 10.7865, radiusKm: 4),
        City(id: "c_hildesheim", name: "Hildesheim", lat: 52.1508, lon: 9.9511, radiusKm: 4),
        City(id: "c_celle", name: "Celle", lat: 52.6226, lon: 10.0805, radiusKm: 3),
        City(id: "c_goettingen", name: "Gotinga", lat: 51.5413, lon: 9.9158, radiusKm: 4),
        City(id: "c_hameln", name: "Hameln", lat: 52.1039, lon: 9.3560, radiusKm: 3),
        City(id: "c_bielefeld", name: "Bielefeld", lat: 52.0302, lon: 8.5325, radiusKm: 5),
        City(id: "c_muenster", name: "Münster", lat: 51.9607, lon: 7.6261, radiusKm: 5),
        City(id: "c_bonn", name: "Bonn", lat: 50.7374, lon: 7.0982, radiusKm: 5),
        City(id: "c_heidelberg", name: "Heidelberg", lat: 49.3988, lon: 8.6724, radiusKm: 4),
        City(id: "c_luebeck", name: "Lübeck", lat: 53.8655, lon: 10.6866, radiusKm: 4),
        City(id: "c_kiel", name: "Kiel", lat: 54.3233, lon: 10.1228, radiusKm: 5),
        City(id: "c_rostock", name: "Rostock", lat: 54.0924, lon: 12.0991, radiusKm: 5),
        City(id: "c_kassel", name: "Kassel", lat: 51.3127, lon: 9.4797, radiusKm: 5),
        City(id: "c_magdeburg", name: "Magdeburgo", lat: 52.1205, lon: 11.6276, radiusKm: 5),
        City(id: "c_erfurt", name: "Erfurt", lat: 50.9848, lon: 11.0299, radiusKm: 4),
        City(id: "c_potsdam", name: "Potsdam", lat: 52.3906, lon: 13.0645, radiusKm: 4),
        City(id: "c_freiburg", name: "Friburgo", lat: 47.9990, lon: 7.8421, radiusKm: 4),
        City(id: "c_aachen", name: "Aquisgrán", lat: 50.7753, lon: 6.0839, radiusKm: 4)
    ]
}

/// Descarga lugares, parques y bosques de Hannover desde OpenStreetMap.
enum PoiRepository {
    private static var cacheURL: URL {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("overpass_hannover.json")
    }

    private static let bbox = "\(Geo.hanS),\(Geo.hanW),\(Geo.hanN),\(Geo.hanE)"

    private static var query: String { """
        [out:json][timeout:120];
        (
          node["tourism"~"^(attraction|museum|viewpoint|zoo|gallery|artwork)$"]["name"](\(bbox));
          node["historic"~"^(monument|castle|church|building|city_gate|tower|ruins|manor|memorial)$"]["name"]["memorial"!="stolperstein"](\(bbox));
        )->.n;
        (
          way["tourism"~"^(attraction|museum|zoo|gallery)$"]["name"](\(bbox));
          way["historic"~"^(monument|castle|church|building|city_gate|tower|ruins|manor)$"]["name"](\(bbox));
          way["leisure"~"^(park|garden|nature_reserve)$"]["name"](\(bbox));
          relation["leisure"~"^(park|garden|nature_reserve)$"]["name"](\(bbox));
          way["landuse"="forest"]["name"](\(bbox));
          relation["landuse"="forest"]["name"](\(bbox));
          way["natural"="wood"]["name"](\(bbox));
          relation["natural"="wood"]["name"](\(bbox));
        )->.a;
        .n out body;
        .a out tags bb;
        """ }

    static var hasCache: Bool { FileManager.default.fileExists(atPath: cacheURL.path) }

    static func loadAll() -> [Poi] {
        var osm: [Poi] = []
        if let data = try? Data(contentsOf: cacheURL) { osm = parse(data) }
        return merge(Curated.hannover, osm) + Curated.germany
    }

    /// Descarga y guarda. Devuelve cuántos lugares trajo.
    static func fetch() async throws -> Int {
        var req = URLRequest(url: URL(string: "https://overpass-api.de/api/interpreter")!)
        req.httpMethod = "POST"
        req.timeoutInterval = 150
        req.setValue("application/x-www-form-urlencoded; charset=UTF-8", forHTTPHeaderField: "Content-Type")
        req.setValue("MapheimHannover/1.0 (iOS)", forHTTPHeaderField: "User-Agent")
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-._~")
        let body = "data=" + (query.addingPercentEncoding(withAllowedCharacters: allowed) ?? "")
        req.httpBody = body.data(using: .utf8)
        let (data, resp) = try await URLSession.shared.data(for: req)
        guard (resp as? HTTPURLResponse)?.statusCode == 200 else { throw URLError(.badServerResponse) }
        let parsed = parse(data)
        guard !parsed.isEmpty else { throw URLError(.cannotParseResponse) }
        try data.write(to: cacheURL)
        return parsed.count
    }

    private static func norm(_ s: String) -> String { s.lowercased(with: Locale(identifier: "de_DE")).trimmingCharacters(in: .whitespaces) }

    private struct Acc {
        let id: String, name: String, cat: Cat
        var s: Double, w: Double, n: Double, e: Double, area: Bool
        var lat: Double { (s + n) / 2 }
        var lon: Double { (w + e) / 2 }
    }

    static func parse(_ data: Data) -> [Poi] {
        guard let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let elements = root["elements"] as? [[String: Any]] else { return [] }
        var groups: [String: [Acc]] = [:]
        var order: [String] = []
        for o in elements {
            guard let tags = o["tags"] as? [String: String],
                  let rawName = tags["name"]?.trimmingCharacters(in: .whitespaces), !rawName.isEmpty else { continue }
            if tags["memorial"] == "stolperstein" || tags["access"] == "private" { continue }
            let leisure = tags["leisure"] ?? ""
            let cat: Cat
            if ["park", "garden", "nature_reserve"].contains(leisure) { cat = .park }
            else if tags["landuse"] == "forest" || tags["natural"] == "wood" { cat = .forest }
            else { cat = .sight }
            let type = o["type"] as? String ?? ""
            let osmId = (o["id"] as? NSNumber)?.int64Value ?? 0
            let id = "osm_\(type.prefix(1))\(osmId)"
            var acc: Acc
            if type == "node" {
                guard let la = (o["lat"] as? NSNumber)?.doubleValue, let lo = (o["lon"] as? NSNumber)?.doubleValue else { continue }
                acc = Acc(id: id, name: rawName, cat: cat, s: la, w: lo, n: la, e: lo, area: false)
            } else {
                guard let b = o["bounds"] as? [String: Any],
                      let s = (b["minlat"] as? NSNumber)?.doubleValue, let w = (b["minlon"] as? NSNumber)?.doubleValue,
                      let n = (b["maxlat"] as? NSNumber)?.doubleValue, let e = (b["maxlon"] as? NSNumber)?.doubleValue else { continue }
                let padLat = cat == .sight ? 0.00036 : 0.00014
                let padLon = cat == .sight ? 0.00058 : 0.00022
                acc = Acc(id: id, name: rawName, cat: cat, s: s - padLat, w: w - padLon, n: n + padLat, e: e + padLon, area: true)
            }
            let key = norm(rawName) + "|" + cat.rawValue
            if groups[key] == nil { groups[key] = []; order.append(key) }
            if let i = groups[key]!.firstIndex(where: { Geo.distance($0.lat, $0.lon, acc.lat, acc.lon) < 2500 }) {
                if acc.area || groups[key]![i].area {
                    var g = groups[key]![i]
                    g.s = min(g.s, acc.s); g.w = min(g.w, acc.w); g.n = max(g.n, acc.n); g.e = max(g.e, acc.e)
                    g.area = true
                    groups[key]![i] = g
                }
            } else {
                groups[key]!.append(acc)
            }
        }
        var out: [Poi] = []
        for key in order {
            for a in groups[key] ?? [] {
                if a.area {
                    out.append(Poi(id: a.id, name: a.name, cat: a.cat, scope: .hannover, lat: a.lat, lon: a.lon, radius: 0, bounds: (a.s, a.w, a.n, a.e)))
                } else {
                    out.append(Poi(id: a.id, name: a.name, cat: a.cat, scope: .hannover, lat: a.s, lon: a.w, radius: 60))
                }
            }
        }
        return out
    }

    /// Los lugares escogidos conservan su id; OSM corrige su ubicación.
    static func merge(_ curated: [Poi], _ osm: [Poi]) -> [Poi] {
        var used = Set<String>()
        var result: [Poi] = []
        for c in curated {
            var match: Poi? = nil
            if let key = c.matchKey {
                match = osm.filter {
                    !used.contains($0.id) && $0.isGreen == c.isGreen && norm($0.name).contains(key) &&
                        Geo.distance(c.lat, c.lon, $0.lat, $0.lon) < 1500
                }.min { Geo.distance(c.lat, c.lon, $0.lat, $0.lon) < Geo.distance(c.lat, c.lon, $1.lat, $1.lon) }
            }
            if let m = match {
                used.insert(m.id)
                if c.bounds != nil {
                    result.append(c)
                } else {
                    var fixed = c
                    fixed.lat = m.lat; fixed.lon = m.lon; fixed.bounds = m.bounds
                    result.append(fixed)
                }
            } else {
                result.append(c)
            }
        }
        for o in osm where !used.contains(o.id) { result.append(o) }
        return result
    }
}
