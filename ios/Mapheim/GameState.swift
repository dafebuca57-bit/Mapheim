import Foundation
import Combine
import CoreLocation
import UserNotifications

struct GameEvent {
    var discovered: [Poi] = []
    var achievements: [Achievement] = []
    var moved = false
    var eyes = 0
}

/// Todo el progreso del jugador. Se usa desde el hilo principal.
final class GameState: NSObject, ObservableObject, CLLocationManagerDelegate {
    static let shared = GameState()

    // Ojos de Greydwarf: cuánto da cada cosa
    static let eyesPassivePerMin = 1
    static let eyesPerWalkStep = 3
    static let walkStepM = 200.0
    static let eyesPoi = 10
    static let eyesPoiFamous = 25
    static let eyesLandmark = 50
    static let eyesCity = 40

    @Published private(set) var revision = 0
    @Published private(set) var eyes = 0
    @Published private(set) var tracking = false
    @Published private(set) var pois: [Poi] = Curated.hannover + Curated.germany
    @Published private(set) var pins: [Pin] = []
    @Published private(set) var equipped: [String: String] = [:]
    @Published var testMode = false { didSet { defaults.set(testMode, forKey: "test") } }
    @Published var locationDenied = false
    @Published private(set) var offlineReady = false
    @Published var offlinePreferred = false { didSet { defaults.set(offlinePreferred, forKey: "offlinePreferred") } }
    @Published private(set) var downloadingMap = false

    let events = PassthroughSubject<GameEvent, Never>()

    private(set) var hasPos = false
    private(set) var posLat = 0.0
    private(set) var posLon = 0.0
    private(set) var walkedM = 0.0
    private(set) var eyesEarned = 0
    private(set) var discovered = Set<String>()
    private(set) var unlocked: [String: Date] = [:]
    private var days = Set<String>()
    private var cities = Set<String>()
    private var owned = Set<String>()
    private var hannoverCells = 0
    private var walkBucket = 0.0
    private var pendingWalkEyes = 0

    private var hasAnchor = false
    private var anchorLat = 0.0, anchorLon = 0.0
    private var anchorTime = Date()

    var appActive = true
    private var wantsTracking = false
    private let lm = CLLocationManager()
    private let defaults = UserDefaults.standard
    private var cellHandle: FileHandle?
    private var timer: Timer?

    private let dayFmt: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()

    private var docs: URL { FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0] }
    private var cellsURL: URL { docs.appendingPathComponent("cells.bin") }

    private override init() {
        super.init()
        lm.delegate = self
        load()
        timer = Timer.scheduledTimer(withTimeInterval: 60, repeats: true) { [weak self] _ in
            guard let self else { return }
            if self.appActive || self.tracking {
                self.earn(GameState.eyesPassivePerMin)
                self.save()
                self.publish(GameEvent())
            }
        }
        Task { await loadPois() }
        offlinePreferred = defaults.bool(forKey: "offlinePreferred")
        Task.detached(priority: .utility) {
            if let d = OfflineMap.load() {
                OfflineMap.setCurrent(d)
                await MainActor.run { GameState.shared.offlineReady = true }
            }
        }
    }

    // MARK: - Mapa sin conexión

    /// Descarga el mapa de Hannover (y los lugares si faltan). Devuelve el mensaje para el usuario.
    func downloadOfflineMap(progress: @escaping @MainActor (String) -> Void) async -> String {
        if downloadingMap { return "El mapa ya se está descargando" }
        await MainActor.run { downloadingMap = true }
        defer { Task { @MainActor in GameState.shared.downloadingMap = false } }
        if !PoiRepository.hasCache {
            await progress("Descargando lugares de Hannover")
            if (try? await PoiRepository.fetch()) != nil {
                let all = PoiRepository.loadAll()
                await MainActor.run { setPois(all) }
            }
        }
        do {
            _ = try await OfflineMap.download { step in await progress(step) }
            guard let d = OfflineMap.load() else { throw URLError(.cannotParseResponse) }
            OfflineMap.setCurrent(d)
            await MainActor.run {
                offlineReady = true
                revision += 1
            }
            return "ᛟ Mapa de Hannover descargado ᛟ\n\(fmt(OfflineMap.fileSizeMB, 1)) MB · ya funciona sin internet"
        } catch {
            return "No se pudo descargar el mapa.\nRevisa tu conexión e inténtalo otra vez."
        }
    }

    // MARK: - Lugares

    private func loadPois() async {
        let all = PoiRepository.loadAll()
        await MainActor.run { setPois(all) }
        if !PoiRepository.hasCache {
            if (try? await PoiRepository.fetch()) != nil {
                let fresh = PoiRepository.loadAll()
                await MainActor.run { setPois(fresh) }
            }
        }
    }

    private func setPois(_ all: [Poi]) {
        pois = all
        for p in all where !discovered.contains(p.id) && FogStore.shared.contains(Geo.cellKey(p.lat, p.lon)) && (p.bounds == nil || p.cat == .sight) {
            discovered.insert(p.id)
        }
        let ach = checkAchievements()
        let gained = ach.reduce(0) { $0 + $1.reward }
        earn(gained)
        save()
        publish(GameEvent(achievements: ach, eyes: gained))
    }

    func refreshPois() async -> String {
        do {
            let n = try await PoiRepository.fetch()
            let all = PoiRepository.loadAll()
            await MainActor.run { setPois(all) }
            return "Se cargaron \(n) lugares, parques y bosques de Hannover"
        } catch {
            return "No se pudieron descargar los lugares. Revisa tu conexión e inténtalo otra vez."
        }
    }

    // MARK: - GPS

    func startTracking() {
        wantsTracking = true
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound]) { _, _ in }
        switch lm.authorizationStatus {
        case .notDetermined: lm.requestWhenInUseAuthorization()
        case .denied, .restricted: locationDenied = true; wantsTracking = false
        default: beginUpdates()
        }
    }

    func stopTracking() {
        wantsTracking = false
        lm.stopUpdatingLocation()
        lm.allowsBackgroundLocationUpdates = false
        tracking = false
        publish(GameEvent())
    }

    private func beginUpdates() {
        lm.desiredAccuracy = kCLLocationAccuracyBest
        lm.distanceFilter = 5
        lm.activityType = .fitness
        lm.pausesLocationUpdatesAutomatically = false
        lm.allowsBackgroundLocationUpdates = true
        lm.showsBackgroundLocationIndicator = true
        lm.startUpdatingLocation()
        tracking = true
        publish(GameEvent())
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        switch manager.authorizationStatus {
        case .authorizedWhenInUse, .authorizedAlways:
            locationDenied = false
            if wantsTracking && !tracking { beginUpdates() }
        case .denied, .restricted:
            locationDenied = wantsTracking
            wantsTracking = false
            if tracking { stopTracking() }
        default: break
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        for l in locations {
            onLocation(l.coordinate.latitude, l.coordinate.longitude, l.horizontalAccuracy, l.timestamp)
        }
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {}

    /// Punto GPS real. Solo cuenta como caminado a paso de persona (hasta ~16 km/h).
    func onLocation(_ lat: Double, _ lon: Double, _ accuracy: Double, _ time: Date) {
        if accuracy < 0 || accuracy > 50 { return }
        hasPos = true; posLat = lat; posLon = lon
        if !hasAnchor {
            setAnchor(lat, lon, time)
        } else {
            let d = Geo.distance(anchorLat, anchorLon, lat, lon)
            if d >= max(10, accuracy * 0.6) {
                let dt = time.timeIntervalSince(anchorTime)
                if dt > 0 && d / dt <= 4.5 && accuracy <= 30 { addWalk(d) }
                setAnchor(lat, lon, time)
            }
        }
        publish(process(lat, lon))
    }

    /// Modo prueba: "teletransporta" al jugador.
    func simulate(_ lat: Double, _ lon: Double) {
        if hasPos { addWalk(min(Geo.distance(posLat, posLon, lat, lon), 3000)) }
        hasPos = true; posLat = lat; posLon = lon
        setAnchor(lat, lon, Date())
        publish(process(lat, lon))
    }

    private func setAnchor(_ lat: Double, _ lon: Double, _ t: Date) {
        hasAnchor = true; anchorLat = lat; anchorLon = lon; anchorTime = t
    }

    private func addWalk(_ d: Double) {
        walkedM += d
        walkBucket += d
        while walkBucket >= GameState.walkStepM {
            walkBucket -= GameState.walkStepM
            pendingWalkEyes += GameState.eyesPerWalkStep
        }
    }

    private func process(_ lat: Double, _ lon: Double) -> GameEvent {
        var newCells: [Int64] = []
        for k in Geo.cellsAround(lat, lon, Geo.revealM) where FogStore.shared.insert(k) {
            newCells.append(k)
            if Geo.inHannover(Geo.centerLat(k), Geo.centerLon(k)) { hannoverCells += 1 }
        }
        appendCells(newCells)

        var gained = pendingWalkEyes
        pendingWalkEyes = 0
        var found: [Poi] = []
        for p in pois where !discovered.contains(p.id) && p.covers(lat, lon) {
            discovered.insert(p.id)
            found.append(p)
            gained += reward(for: p)
        }
        for c in Curated.cities where !cities.contains(c.id) && Geo.distance(c.lat, c.lon, lat, lon) <= c.radiusKm * 1000 {
            cities.insert(c.id)
            gained += GameState.eyesCity
        }
        days.insert(dayFmt.string(from: Date()))
        let ach = checkAchievements()
        gained += ach.reduce(0) { $0 + $1.reward }
        earn(gained)
        save()
        return GameEvent(discovered: found, achievements: ach, moved: true, eyes: gained)
    }

    func reward(for p: Poi) -> Int {
        if p.scope == .germany { return GameState.eyesLandmark }
        return p.famous ? GameState.eyesPoiFamous : GameState.eyesPoi
    }

    private func publish(_ ev: GameEvent) {
        revision += 1
        events.send(ev)
        if !appActive { notifyInBackground(ev) }
    }

    private func notifyInBackground(_ ev: GameEvent) {
        var items: [(String, String)] = ev.discovered.map { ("Descubriste: \($0.name)", "\($0.cat.label) · +\(reward(for: $0)) Ojos de Greydwarf") }
        items += ev.achievements.map { ("Nueva hazaña: \($0.title)", "\($0.desc) · +\($0.reward) ojos") }
        for (title, body) in items {
            let c = UNMutableNotificationContent()
            c.title = title
            c.body = body
            c.sound = .default
            UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: UUID().uuidString, content: c, trigger: nil))
        }
    }

    // MARK: - Hazañas

    func stats() -> Stats {
        var s = Stats()
        for p in pois {
            let d = discovered.contains(p.id)
            if p.scope == .hannover {
                s.hPoisTotal += 1; if d { s.hPois += 1 }
                if p.isGreen { s.hGreenTotal += 1; if d { s.hGreen += 1 } }
            } else {
                s.gLandTotal += 1; if d { s.gLand += 1 }
            }
        }
        s.walkedKm = walkedM / 1000
        s.hPct = min(100, 100 * Double(hannoverCells) / Geo.hannoverCells)
        s.gPct = min(100, 100 * Double(FogStore.shared.count) / Geo.germanyCells)
        s.cities = cities.count
        s.citiesTotal = Curated.cities.count
        s.days = days.count
        s.herrenhausen = Curated.herrenhausen.filter { discovered.contains($0) }.count
        s.altstadt = Curated.altstadt.filter { discovered.contains($0) }.count
        s.eilenriede = discovered.contains(Curated.eilenriede) ? 1 : 0
        s.maschsee = discovered.contains(Curated.maschsee) ? 1 : 0
        return s
    }

    private func checkAchievements() -> [Achievement] {
        let s = stats()
        let fresh = Achievements.all.filter { unlocked[$0.id] == nil && $0.value(s) >= $0.target }
        for a in fresh { unlocked[a.id] = Date() }
        return fresh
    }

    // MARK: - Ojos y mercader

    private func earn(_ n: Int) {
        guard n > 0 else { return }
        eyes += n
        eyesEarned += n
    }

    func owns(_ item: ShopItem) -> Bool { item.price == 0 || owned.contains(item.id) }
    func equippedId(_ cat: ShopCat) -> String { equipped[cat.rawValue] ?? ShopCatalog.defaultId(cat) }
    func isEquipped(_ item: ShopItem) -> Bool { equippedId(item.cat) == item.id }

    /// Compra y equipa. false si faltan ojos.
    @discardableResult
    func buy(_ item: ShopItem) -> Bool {
        if owns(item) { equip(item); return true }
        guard eyes >= item.price else { return false }
        eyes -= item.price
        owned.insert(item.id)
        equip(item)
        return true
    }

    func equip(_ item: ShopItem) {
        guard owns(item) else { return }
        equipped[item.cat.rawValue] = item.id
        save()
        publish(GameEvent())
    }

    // MARK: - Pines

    @discardableResult
    func addPin(_ type: PinType, _ lat: Double, _ lon: Double, _ note: String) -> Pin {
        let p = Pin(id: Int64(Date().timeIntervalSince1970 * 1000), type: type, lat: lat, lon: lon,
                    note: note.trimmingCharacters(in: .whitespacesAndNewlines), created: Date())
        pins.append(p)
        savePins()
        return p
    }

    func removePin(_ p: Pin) {
        pins.removeAll { $0.id == p.id }
        savePins()
    }

    private func savePins() {
        if let data = try? JSONEncoder().encode(pins) { defaults.set(data, forKey: "pins") }
        revision += 1
    }

    // MARK: - Guardado

    private func load() {
        FogStore.shared.removeAll()
        hannoverCells = 0
        if let data = try? Data(contentsOf: cellsURL) {
            let n = data.count / 8
            data.withUnsafeBytes { raw in
                for i in 0..<n {
                    let k = Int64(littleEndian: raw.loadUnaligned(fromByteOffset: i * 8, as: Int64.self))
                    if FogStore.shared.insert(k), Geo.inHannover(Geo.centerLat(k), Geo.centerLon(k)) { hannoverCells += 1 }
                }
            }
        }
        walkedM = defaults.double(forKey: "walked")
        walkBucket = defaults.double(forKey: "walkBucket")
        eyes = defaults.integer(forKey: "eyes")
        eyesEarned = defaults.integer(forKey: "eyesEarned")
        discovered = Set(defaults.stringArray(forKey: "disc") ?? [])
        days = Set(defaults.stringArray(forKey: "days") ?? [])
        cities = Set(defaults.stringArray(forKey: "cities") ?? [])
        owned = Set(defaults.stringArray(forKey: "owned") ?? [])
        equipped = defaults.dictionary(forKey: "equipped") as? [String: String] ?? [:]
        if let u = defaults.dictionary(forKey: "unlocked") as? [String: Double] {
            unlocked = u.mapValues { Date(timeIntervalSince1970: $0) }
        }
        testMode = defaults.bool(forKey: "test")
        if let data = defaults.data(forKey: "pins"), let list = try? JSONDecoder().decode([Pin].self, from: data) {
            pins = list
        }
    }

    private func save() {
        defaults.set(walkedM, forKey: "walked")
        defaults.set(walkBucket, forKey: "walkBucket")
        defaults.set(eyes, forKey: "eyes")
        defaults.set(eyesEarned, forKey: "eyesEarned")
        defaults.set(Array(discovered), forKey: "disc")
        defaults.set(Array(days), forKey: "days")
        defaults.set(Array(cities), forKey: "cities")
        defaults.set(Array(owned), forKey: "owned")
        defaults.set(equipped, forKey: "equipped")
        defaults.set(unlocked.mapValues { $0.timeIntervalSince1970 }, forKey: "unlocked")
    }

    private func appendCells(_ keys: [Int64]) {
        guard !keys.isEmpty else { return }
        var data = Data(capacity: keys.count * 8)
        for k in keys {
            var le = k.littleEndian
            withUnsafeBytes(of: &le) { data.append(contentsOf: $0) }
        }
        if !FileManager.default.fileExists(atPath: cellsURL.path) {
            FileManager.default.createFile(atPath: cellsURL.path, contents: nil)
        }
        if cellHandle == nil { cellHandle = try? FileHandle(forWritingTo: cellsURL) }
        guard let h = cellHandle else { return }
        _ = try? h.seekToEnd()
        try? h.write(contentsOf: data)
    }

    /// Borra la exploración. Conserva pines, ojos y compras.
    func resetProgress() {
        try? cellHandle?.close()
        cellHandle = nil
        try? FileManager.default.removeItem(at: cellsURL)
        FogStore.shared.removeAll()
        hannoverCells = 0
        walkedM = 0; walkBucket = 0
        discovered.removeAll(); unlocked.removeAll(); days.removeAll(); cities.removeAll()
        hasAnchor = false
        save()
        publish(GameEvent())
    }
}
