import SwiftUI
import MapKit

enum ActiveSheet: Identifiable {
    case achievements, shop, pinList
    case addPin(lat: Double, lon: Double, fromLongPress: Bool)
    case pinDetail(Pin)
    case poiDetail(Poi)

    var id: String {
        switch self {
        case .achievements: return "ach"
        case .shop: return "shop"
        case .pinList: return "pins"
        case .addPin(let lat, let lon, _): return "add\(lat),\(lon)"
        case .pinDetail(let p): return "pin\(p.id)"
        case .poiDetail(let p): return "poi\(p.id)"
        }
    }
}

struct Banner: Equatable {
    let id = UUID()
    let text: String
    let color: Color
}

struct ContentView: View {
    @ObservedObject var game = GameState.shared
    @Environment(\.scenePhase) private var scenePhase
    @State private var sheet: ActiveSheet?
    @State private var banners: [Banner] = []
    @State private var current: Banner?
    @State private var confirmReset = false
    @State private var askDownload = false
    private let commands = MapCommands()

    var body: some View {
        let s = game.stats()
        ZStack {
            GameMapView(
                game: game,
                commands: commands,
                onPoi: { poi, found in
                    if found { sheet = .poiDetail(poi) }
                    else { show("Lugar oculto en la niebla\nAcércate para revelarlo", V.gold) }
                },
                onPin: { sheet = .pinDetail($0) },
                onLongPress: { sheet = .addPin(lat: $0.latitude, lon: $0.longitude, fromLongPress: true) }
            )
            .ignoresSafeArea()

            VStack(spacing: 0) {
                hud(s)
                if let b = current {
                    Text(b.text)
                        .font(V.cinzelBold(15))
                        .multilineTextAlignment(.center)
                        .foregroundStyle(b.color)
                        .padding(.horizontal, 22).padding(.vertical, 14)
                        .woodPanel()
                        .padding(.top, 16)
                        .padding(.horizontal, 28)
                        .transition(.scale(scale: 0.92).combined(with: .opacity))
                        .id(b.id)
                }
                Spacer()
            }

            VStack {
                Spacer()
                HStack {
                    Spacer()
                    buttons
                }
            }
            .padding(16)

            // Créditos del mapa (obligatorios)
            VStack {
                Spacer()
                HStack {
                    Text("© OpenStreetMap")
                        .font(.system(size: 10))
                        .foregroundStyle(V.muted)
                        .padding(.horizontal, 6).padding(.vertical, 2)
                        .background(V.woodDark.opacity(0.7))
                    Spacer()
                }
            }
            .padding(.leading, 6).padding(.bottom, 4)
            .allowsHitTesting(false)
        }
        .preferredColorScheme(.dark)
        .sheet(item: $sheet) { which in
            sheetView(which)
                .presentationBackground { SheetBackground() }
                .presentationDragIndicator(.visible)
        }
        .alert("Borrar progreso", isPresented: $confirmReset) {
            Button("Cancelar", role: .cancel) {}
            Button("Borrar todo", role: .destructive) {
                game.resetProgress()
                show("Progreso borrado", V.muted)
            }
        } message: {
            Text("La niebla vuelve a cubrir el mapa y se pierden lugares, kilómetros y hazañas. Tus pines, tus Ojos de Greydwarf y lo que compraste se conservan.")
        }
        .alert(game.offlineReady ? "Actualizar mapa" : "Mapa sin conexión", isPresented: $askDownload) {
            Button("Cancelar", role: .cancel) {}
            Button("Descargar") { startDownload() }
        } message: {
            Text(game.offlineReady
                 ? "Vuelve a descargar las calles (con sus nombres), senderos, agua, parques y bosques de Hannover para tener los cambios más recientes."
                 : "Se descargan las calles con sus nombres, senderos, ríos, lagos, parques y bosques de Hannover (unos 5 a 15 MB). Después el mapa funciona sin internet. Usa Wi‑Fi si puedes; tarda 1 o 2 minutos.")
        }
        .alert("Ubicación desactivada", isPresented: $game.locationDenied) {
            Button("Abrir Ajustes") {
                if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
            }
            Button("Cerrar", role: .cancel) {}
        } message: {
            Text("Para despejar la niebla, permite la ubicación de Mapheim en Ajustes.")
        }
        .onReceive(game.events.receive(on: DispatchQueue.main)) { ev in
            for p in ev.discovered {
                show("ᛟ Descubriste ᛟ\n\(p.name)\n+\(game.reward(for: p)) Ojos de Greydwarf", p.isGreen ? V.moss : V.gold)
            }
            for a in ev.achievements {
                show("ᛉ Nueva hazaña ᛉ\n\(a.title)\n+\(a.reward) Ojos de Greydwarf", V.ember)
            }
            if ev.discovered.isEmpty && ev.achievements.isEmpty && ev.eyes > 0 && ev.moved {
                show("+\(ev.eyes) Ojos de Greydwarf\npor caminar", V.eye)
            }
        }
        .onChange(of: scenePhase) { _, phase in
            game.appActive = (phase == .active)
        }
    }

    // MARK: HUD

    private func hud(_ s: Stats) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .bottom) {
                VStack(alignment: .leading, spacing: 0) {
                    Text("Hannover").font(V.uncial(22)).foregroundStyle(V.parchment)
                    Text("ᚺᚨᚾᚾᛟᚹᛖᚱ").font(.system(size: 11)).tracking(3).foregroundStyle(V.bronzeDark)
                }
                Spacer()
                Text("\(fmt(s.hPct, 2)) %").font(V.cinzelBold(24)).foregroundStyle(V.gold)
            }
            RuneBar(value: s.hPct / 100)
            HStack(alignment: .top) {
                stat(fmt(s.walkedKm, 1), "KM CAMINADOS")
                stat("\(s.hPois)/\(s.hPoisTotal)", "LUGARES")
                stat("\(game.unlocked.count)/\(Achievements.all.count)", "HAZAÑAS")
                Button { sheet = .shop } label: {
                    VStack(alignment: .leading, spacing: 1) {
                        HStack(spacing: 5) {
                            EyeIcon(size: 18)
                            Text("\(game.eyes)").font(V.cinzelBold(18)).foregroundStyle(V.eye)
                        }
                        Text("OJOS").font(V.cinzel(10)).foregroundStyle(V.muted)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .buttonStyle(.plain)
            }
            .padding(.top, 4)
        }
        .padding(.horizontal, 18).padding(.vertical, 14)
        .woodPanel()
        .padding(.horizontal, 10)
        .padding(.top, 4)
    }

    private func stat(_ value: String, _ label: String) -> some View {
        VStack(alignment: .leading, spacing: 1) {
            Text(value).font(V.cinzelBold(18)).foregroundStyle(V.parchment).lineLimit(1).minimumScaleFactor(0.7)
            Text(label).font(V.cinzel(10)).foregroundStyle(V.muted).lineLimit(1).minimumScaleFactor(0.7)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: Botones

    private var buttons: some View {
        VStack(alignment: .trailing, spacing: 10) {
            Menu {
                Button { sheet = .pinList } label: { Label("Mis pines", systemImage: "mappin.and.ellipse") }
                Button {
                    show("Consultando a los cartógrafos…", V.muted)
                    Task {
                        let msg = await game.refreshPois()
                        show(msg, V.moss)
                    }
                } label: { Label("Actualizar lugares de Hannover", systemImage: "arrow.clockwise") }
                Button { askDownload = true } label: {
                    Label(!game.offlineReady ? "Descargar mapa de Hannover"
                          : (OfflineMap.missingNames ? "Actualizar mapa (agrega nombres de calles)" : "Actualizar mapa descargado"),
                          systemImage: "arrow.down.circle")
                }
                if game.offlineReady {
                    Toggle(isOn: $game.offlinePreferred) { Label("Usar siempre el mapa descargado", systemImage: "map") }
                }
                Toggle(isOn: $game.testMode) { Label("Modo prueba", systemImage: "wand.and.stars") }
                Button(role: .destructive) { confirmReset = true } label: { Label("Borrar progreso", systemImage: "trash") }
            } label: {
                ShieldButton(label: "Menú", action: {}) {
                    Image(systemName: "ellipsis").font(.system(size: 18, weight: .bold)).foregroundStyle(V.parchment)
                }
                .allowsHitTesting(false)
            }

            ShieldButton(label: "Mercader", action: { sheet = .shop }) { EyeIcon(size: 26) }

            ShieldButton(label: "Hazañas", action: { sheet = .achievements }) {
                Image(systemName: "trophy.fill").font(.system(size: 18)).foregroundStyle(V.gold)
            }

            ShieldButton(label: "Poner un pin aquí", action: {
                if game.hasPos { sheet = .addPin(lat: game.posLat, lon: game.posLon, fromLongPress: false) }
                else { show("Mantén presionado el mapa\ndonde quieras poner el pin", V.gold) }
            }) {
                Image(systemName: "mappin").font(.system(size: 20, weight: .bold)).foregroundStyle(V.ember)
            }

            ShieldButton(label: "Centrar en mi ubicación", action: {
                commands.follow = true
                if game.hasPos {
                    commands.center.send((CLLocationCoordinate2D(latitude: game.posLat, longitude: game.posLon), 700))
                } else {
                    show("Aún no hay ubicación\nToca Explorar para empezar", V.muted)
                }
            }) {
                Image(systemName: "location.fill").font(.system(size: 18)).foregroundStyle(V.parchment)
            }
            .padding(.bottom, 4)

            Button {
                if game.tracking {
                    game.stopTracking()
                } else {
                    commands.follow = true
                    game.startTracking()
                    show("La antorcha está encendida\nPuedes bloquear el teléfono", V.ember)
                }
            } label: {
                Label(game.tracking ? "DETENER" : "EXPLORAR", systemImage: game.tracking ? "stop.fill" : "play.fill")
            }
            .buttonStyle(EmberButtonStyle())
        }
    }

    // MARK: Hojas

    @ViewBuilder
    private func sheetView(_ which: ActiveSheet) -> some View {
        switch which {
        case .achievements:
            AchievementsSheet(game: game).presentationDetents([.large])
        case .shop:
            ShopSheet(game: game, onMessage: show).presentationDetents([.large])
        case .pinList:
            PinListSheet(game: game) { p in
                sheet = nil
                commands.follow = false
                commands.center.send((CLLocationCoordinate2D(latitude: p.lat, longitude: p.lon), 350))
            }
            .presentationDetents([.medium, .large])
        case .addPin(let lat, let lon, let fromLongPress):
            AddPinSheet(testMode: game.testMode && fromLongPress) { type, note in
                let p = game.addPin(type, lat, lon, note)
                sheet = nil
                show("Pin guardado\n\(p.title)", V.gold)
            } onSimulate: {
                sheet = nil
                game.simulate(lat, lon)
            }
            .presentationDetents([.large])
        case .pinDetail(let p):
            PinDetailSheet(pin: p, game: game) {
                game.removePin(p)
                sheet = nil
                show("Pin eliminado", V.muted)
            }
            .presentationDetents([.height(330)])
        case .poiDetail(let p):
            PoiDetailSheet(poi: p) {
                sheet = .addPin(lat: p.lat, lon: p.lon, fromLongPress: false)
            }
            .presentationDetents([.height(260)])
        }
    }

    // MARK: Mapa sin conexión

    private func startDownload() {
        if game.downloadingMap { show("El mapa ya se está descargando", V.muted); return }
        show("Descargando el mapa de Hannover\nTarda 1 o 2 minutos", V.muted)
        Task {
            let msg = await game.downloadOfflineMap { step in show(step, V.muted) }
            show(msg, msg.hasPrefix("ᛟ") ? V.moss : V.muted)
        }
    }

    // MARK: Avisos

    private func show(_ text: String, _ color: Color) {
        banners.append(Banner(text: text, color: color))
        if current == nil { next() }
    }

    private func next() {
        guard !banners.isEmpty else { withAnimation(.easeOut(duration: 0.25)) { current = nil }; return }
        let b = banners.removeFirst()
        withAnimation(.spring(duration: 0.3)) { current = b }
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.8) {
            withAnimation(.easeOut(duration: 0.25)) { current = nil }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { next() }
        }
    }
}
