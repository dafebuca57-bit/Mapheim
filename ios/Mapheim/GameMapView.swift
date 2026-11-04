import SwiftUI
import MapKit
import Combine
import CoreImage

// MARK: - Niebla

final class FogOverlay: NSObject, MKOverlay {
    let coordinate = CLLocationCoordinate2D(latitude: 0, longitude: 0)
    let boundingMapRect = MKMapRect.world
}

/// Cubre el mundo de niebla y abre claros en las celdas visitadas. MapKit lo dibuja en otros hilos.
final class FogRenderer: MKOverlayRenderer {
    private let lock = NSLock()
    private var _color = ShopCatalog.fogColor("fog_cold").cgColor
    var fogColor: CGColor {
        get { lock.lock(); defer { lock.unlock() }; return _color }
        set { lock.lock(); _color = newValue; lock.unlock() }
    }

    override func draw(_ mapRect: MKMapRect, zoomScale: MKZoomScale, in context: CGContext) {
        context.setFillColor(fogColor)
        context.fill(rect(for: mapRect))

        let nw = MKMapPoint(x: mapRect.minX, y: mapRect.minY).coordinate
        let se = MKMapPoint(x: mapRect.maxX, y: mapRect.maxY).coordinate
        let cells = FogStore.shared.cells(s: se.latitude - 0.002, w: nw.longitude - 0.003,
                                          n: nw.latitude + 0.002, e: se.longitude + 0.003)
        if cells.isEmpty { return }

        context.setBlendMode(.destinationOut)
        let midLat = (nw.latitude + se.latitude) / 2
        let rBase = max(1.4 / Double(zoomScale), Geo.drawM * MKMapPointsPerMeterAtLatitude(midLat))
        let soft = rBase * Double(zoomScale) > 3
        let grow = rBase * 1.5
        let area = mapRect.insetBy(dx: -grow, dy: -grow)
        let rings: [(Double, CGFloat)] = soft ? [(1.45, 0.27), (1.15, 0.59), (0.9, 1)] : [(0.9, 1)]
        for (scale, alpha) in rings {
            context.setFillColor(UIColor(white: 1, alpha: alpha).cgColor)
            let rr = rBase * scale
            for k in cells {
                let mp = MKMapPoint(CLLocationCoordinate2D(latitude: Geo.centerLat(k), longitude: Geo.centerLon(k)))
                if !area.contains(mp) { continue }
                context.fillEllipse(in: rect(for: MKMapRect(x: mp.x - rr, y: mp.y - rr, width: rr * 2, height: rr * 2)))
            }
        }
    }
}

// MARK: - Mapa con estilo (mosaicos de OpenStreetMap teñidos)

final class ThemedTileOverlay: MKTileOverlay {
    let styleId: String
    private let matrix: [CGFloat]
    private static let ci = CIContext(options: [.workingColorSpace: NSNull(), .outputColorSpace: NSNull()])

    init(styleId: String) {
        self.styleId = styleId
        self.matrix = ShopCatalog.mapMatrix(styleId)
        super.init(urlTemplate: "https://tile.openstreetmap.org/{z}/{x}/{y}.png")
        canReplaceMapContent = true
        maximumZ = 19
    }

    override func loadTile(at path: MKTileOverlayPath, result: @escaping (Data?, Error?) -> Void) {
        var req = URLRequest(url: url(forTilePath: path))
        req.cachePolicy = .returnCacheDataElseLoad
        req.setValue("MapheimHannover/1.0 (iOS)", forHTTPHeaderField: "User-Agent")
        let m = matrix
        URLSession.shared.dataTask(with: req) { data, _, error in
            guard let data, let img = CIImage(data: data) else { result(data, error); return }
            let out = ShopCatalog.filter(img, matrix: m)
            if let cg = ThemedTileOverlay.ci.createCGImage(out, from: img.extent),
               let png = UIImage(cgImage: cg).pngData() {
                result(png, nil)
            } else {
                result(data, nil)
            }
        }.resume()
    }
}

// MARK: - Marcadores

final class PoiAnnotation: NSObject, MKAnnotation {
    let poi: Poi
    let found: Bool
    let coordinate: CLLocationCoordinate2D
    init(_ poi: Poi, found: Bool) {
        self.poi = poi
        self.found = found
        self.coordinate = CLLocationCoordinate2D(latitude: poi.lat, longitude: poi.lon)
    }
    var key: String { "\(poi.id)|\(found)" }
}

final class PinAnnotation: NSObject, MKAnnotation {
    let pin: Pin
    let coordinate: CLLocationCoordinate2D
    init(_ pin: Pin) {
        self.pin = pin
        self.coordinate = CLLocationCoordinate2D(latitude: pin.lat, longitude: pin.lon)
    }
}

final class PlayerAnnotation: NSObject, MKAnnotation {
    @objc dynamic var coordinate = CLLocationCoordinate2D(latitude: 0, longitude: 0)
}

/// Órdenes desde la interfaz hacia el mapa.
final class MapCommands {
    let center = PassthroughSubject<(CLLocationCoordinate2D, Double), Never>()
    var follow = true
}

// MARK: - Vista

struct GameMapView: UIViewRepresentable {
    @ObservedObject var game: GameState
    let commands: MapCommands
    var onPoi: (Poi, Bool) -> Void
    var onPin: (Pin) -> Void
    var onLongPress: (CLLocationCoordinate2D) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIView(context: Context) -> MKMapView {
        let map = MKMapView()
        map.delegate = context.coordinator
        map.showsCompass = false
        map.showsUserLocation = false
        map.pointOfInterestFilter = .excludingAll
        map.isPitchEnabled = false
        map.overrideUserInterfaceStyle = .dark

        let c = context.coordinator
        c.applyTheme(map)
        map.addOverlay(c.fog, level: .aboveLabels)

        let start = game.hasPos
            ? CLLocationCoordinate2D(latitude: game.posLat, longitude: game.posLon)
            : CLLocationCoordinate2D(latitude: 52.3745, longitude: 9.7385) // Kröpcke
        map.setRegion(MKCoordinateRegion(center: start, latitudinalMeters: game.hasPos ? 900 : 3500,
                                         longitudinalMeters: game.hasPos ? 900 : 3500), animated: false)
        c.centeredOnce = game.hasPos

        let long = UILongPressGestureRecognizer(target: c, action: #selector(Coordinator.longPress(_:)))
        long.minimumPressDuration = 0.5
        map.addGestureRecognizer(long)
        let pan = UIPanGestureRecognizer(target: c, action: #selector(Coordinator.panned(_:)))
        pan.delegate = c
        map.addGestureRecognizer(pan)

        c.bind(map)
        c.sync(map)
        return map
    }

    func updateUIView(_ map: MKMapView, context: Context) {
        context.coordinator.parent = self
        context.coordinator.sync(map)
    }

    final class Coordinator: NSObject, MKMapViewDelegate, UIGestureRecognizerDelegate {
        var parent: GameMapView
        let fog = FogOverlay()
        private var fogRenderer: FogRenderer?
        private var tiles: ThemedTileOverlay?
        private let player = PlayerAnnotation()
        private var playerAdded = false
        private var playerStyle = ""
        private var poiAnns: [String: PoiAnnotation] = [:]
        private var pinAnns: [Int64: PinAnnotation] = [:]
        private var pinStyle = ""
        private var lastCellCount = -1
        private var lastFog = ""
        private var bag = Set<AnyCancellable>()
        var centeredOnce = false
        weak var map: MKMapView?

        init(_ parent: GameMapView) { self.parent = parent }

        func bind(_ map: MKMapView) {
            self.map = map
            parent.game.events.receive(on: DispatchQueue.main).sink { [weak self] ev in
                guard let self, let map = self.map, ev.moved else { return }
                let g = self.parent.game
                let here = CLLocationCoordinate2D(latitude: g.posLat, longitude: g.posLon)
                if !self.centeredOnce {
                    self.centeredOnce = true
                    map.setRegion(MKCoordinateRegion(center: here, latitudinalMeters: 700, longitudinalMeters: 700), animated: true)
                } else if self.parent.commands.follow && !g.testMode {
                    map.setCenter(here, animated: true)
                }
            }.store(in: &bag)
            parent.commands.center.receive(on: DispatchQueue.main).sink { [weak self] value in
                let (coord, meters) = value
                self?.map?.setRegion(MKCoordinateRegion(center: coord, latitudinalMeters: meters, longitudinalMeters: meters), animated: true)
            }.store(in: &bag)
        }

        func applyTheme(_ map: MKMapView) {
            let id = parent.game.equippedId(.map)
            if tiles?.styleId == id { return }
            if let old = tiles { map.removeOverlay(old) }
            let t = ThemedTileOverlay(styleId: id)
            tiles = t
            map.insertOverlay(t, at: 0, level: .aboveLabels)
        }

        func sync(_ map: MKMapView) {
            let g = parent.game
            applyTheme(map)

            // Niebla
            let fogId = g.equippedId(.fog)
            let count = FogStore.shared.count
            if let r = fogRenderer, count != lastCellCount || fogId != lastFog {
                if fogId != lastFog { r.fogColor = ShopCatalog.fogColor(fogId).cgColor }
                lastCellCount = count
                lastFog = fogId
                r.setNeedsDisplay()
            }

            // Lugares: descubiertos y "?" cercanos
            var wanted: [String: PoiAnnotation] = [:]
            for p in g.pois {
                let found = g.discovered.contains(p.id)
                if !found {
                    guard g.hasPos, Geo.distance(p.lat, p.lon, g.posLat, g.posLon) <= 400 else { continue }
                }
                let key = "\(p.id)|\(found)"
                wanted[key] = poiAnns[key] ?? PoiAnnotation(p, found: found)
            }
            let removeP = poiAnns.filter { wanted[$0.key] == nil }.map { $0.value }
            let addP = wanted.filter { poiAnns[$0.key] == nil }.map { $0.value }
            if !removeP.isEmpty { map.removeAnnotations(removeP) }
            if !addP.isEmpty { map.addAnnotations(addP) }
            poiAnns = wanted

            // Pines (se recrean si cambia el estilo)
            let ps = g.equippedId(.pin)
            if ps != pinStyle {
                map.removeAnnotations(Array(pinAnns.values))
                pinAnns.removeAll()
                pinStyle = ps
            }
            var wantedPins: [Int64: PinAnnotation] = [:]
            for p in g.pins { wantedPins[p.id] = pinAnns[p.id] ?? PinAnnotation(p) }
            let removeN = pinAnns.filter { wantedPins[$0.key] == nil }.map { $0.value }
            let addN = wantedPins.filter { pinAnns[$0.key] == nil }.map { $0.value }
            if !removeN.isEmpty { map.removeAnnotations(removeN) }
            if !addN.isEmpty { map.addAnnotations(addN) }
            pinAnns = wantedPins

            // Jugador
            if g.hasPos {
                player.coordinate = CLLocationCoordinate2D(latitude: g.posLat, longitude: g.posLon)
                if !playerAdded { map.addAnnotation(player); playerAdded = true }
                let style = "\(g.equippedId(.cursor))|\(g.tracking)"
                if style != playerStyle, let v = map.view(for: player) {
                    v.image = Art.cursor(g.equippedId(.cursor), active: g.tracking)
                    playerStyle = style
                }
            }
        }

        // MARK: MKMapViewDelegate

        func mapView(_ mapView: MKMapView, rendererFor overlay: MKOverlay) -> MKOverlayRenderer {
            if let t = overlay as? MKTileOverlay { return MKTileOverlayRenderer(tileOverlay: t) }
            if overlay is FogOverlay {
                let r = FogRenderer(overlay: overlay)
                r.fogColor = ShopCatalog.fogColor(parent.game.equippedId(.fog)).cgColor
                lastFog = parent.game.equippedId(.fog)
                fogRenderer = r
                return r
            }
            return MKOverlayRenderer(overlay: overlay)
        }

        func mapView(_ mapView: MKMapView, viewFor annotation: MKAnnotation) -> MKAnnotationView? {
            let g = parent.game
            if annotation is PlayerAnnotation {
                let v = mapView.dequeueReusableAnnotationView(withIdentifier: "player")
                    ?? MKAnnotationView(annotation: annotation, reuseIdentifier: "player")
                v.annotation = annotation
                v.image = Art.cursor(g.equippedId(.cursor), active: g.tracking)
                playerStyle = "\(g.equippedId(.cursor))|\(g.tracking)"
                v.displayPriority = .required
                v.zPriority = .max
                v.isEnabled = false
                return v
            }
            if let a = annotation as? PinAnnotation {
                let v = mapView.dequeueReusableAnnotationView(withIdentifier: "pin")
                    ?? MKAnnotationView(annotation: annotation, reuseIdentifier: "pin")
                v.annotation = a
                v.image = Art.pin(a.pin.type, style: g.equippedId(.pin))
                v.centerOffset = CGPoint(x: 0, y: -Art.pinSize.height / 2)
                v.displayPriority = .required
                v.zPriority = .defaultSelected
                return v
            }
            if let a = annotation as? PoiAnnotation {
                let v = mapView.dequeueReusableAnnotationView(withIdentifier: "poi")
                    ?? MKAnnotationView(annotation: annotation, reuseIdentifier: "poi")
                v.annotation = a
                v.image = Art.poi(a.poi.cat, famous: a.poi.famous, found: a.found)
                v.displayPriority = a.poi.famous || !a.found ? .defaultHigh : .defaultLow
                v.collisionMode = .circle
                return v
            }
            return nil
        }

        func mapView(_ mapView: MKMapView, didSelect view: MKAnnotationView) {
            if let a = view.annotation as? PinAnnotation { parent.onPin(a.pin) }
            if let a = view.annotation as? PoiAnnotation { parent.onPoi(a.poi, a.found) }
            mapView.deselectAnnotation(view.annotation, animated: false)
        }

        // MARK: Gestos

        @objc func longPress(_ g: UILongPressGestureRecognizer) {
            guard g.state == .began, let map = g.view as? MKMapView else { return }
            let coord = map.convert(g.location(in: map), toCoordinateFrom: map)
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
            parent.onLongPress(coord)
        }

        @objc func panned(_ g: UIPanGestureRecognizer) {
            if g.state == .began { parent.commands.follow = false }
        }

        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer,
                               shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool { true }
    }
}
