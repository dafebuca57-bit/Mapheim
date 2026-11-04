import SwiftUI
import MapKit

private func sheetHeading(_ t: String) -> some View {
    Text(t).font(V.uncial(22)).foregroundStyle(V.gold).padding(.top, 18).padding(.bottom, 4)
}

private func caption(_ t: String) -> some View {
    Text(t).font(.system(size: 13)).foregroundStyle(V.muted).fixedSize(horizontal: false, vertical: true)
}

// MARK: - Hazañas

struct AchievementsSheet: View {
    @ObservedObject var game: GameState

    var body: some View {
        let s = game.stats()
        ScrollView {
            VStack(alignment: .leading, spacing: 6) {
                sheetHeading("Tu saga")
                row("Hannover explorado", "\(fmt(s.hPct, 2)) %")
                row("Lugares de Hannover", "\(s.hPois) de \(s.hPoisTotal)")
                row("Parques y bosques", "\(s.hGreen) de \(s.hGreenTotal)")
                row("Kilómetros caminados", fmt(s.walkedKm, 1))
                row("Días explorando", "\(s.days)")
                row("Alemania explorada", "\(fmt(s.gPct, 4)) %")
                row("Monumentos de Alemania", "\(s.gLand) de \(s.gLandTotal)")
                row("Ciudades visitadas", "\(s.cities) de \(s.citiesTotal)")
                row("Pines guardados", "\(game.pins.count)")
                row("Ojos de Greydwarf ganados", "\(game.eyesEarned)")

                ForEach(AchSection.allCases, id: \.self) { sec in
                    let list = Achievements.all.filter { $0.section == sec }
                    let done = list.filter { game.unlocked[$0.id] != nil }.count
                    sheetHeading("ᛟ \(sec.rawValue) · \(done)/\(list.count)")
                    ForEach(list) { a in achievement(a, s) }
                }
            }
            .padding(.horizontal, 20).padding(.bottom, 32)
        }
    }

    private func row(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label).font(.system(size: 14)).foregroundStyle(V.muted)
            Spacer()
            Text(value).font(V.cinzelBold(14)).foregroundStyle(V.parchment)
        }
        .padding(.vertical, 2)
    }

    private func achievement(_ a: Achievement, _ s: Stats) -> some View {
        let date = game.unlocked[a.id]
        let v = min(a.value(s), a.target)
        let progress: String
        if let date {
            progress = date.formatted(.dateTime.day().month(.abbreviated).year().locale(esLocale))
        } else if a.unit.hasPrefix("de ") {
            progress = "\(fmt(v, 0)) \(a.unit)"
        } else {
            progress = "\(fmt(v, a.decimals)) / \(fmt(a.target, a.decimals))\(a.unit.isEmpty ? "" : " \(a.unit)")"
        }
        return VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: .firstTextBaseline) {
                Text(date != nil ? "ᛉ \(a.title)" : a.title)
                    .font(V.cinzelBold(15))
                    .foregroundStyle(date != nil ? V.gold : V.parchment)
                Spacer()
                Text(progress).font(.system(size: 12)).foregroundStyle(V.muted)
            }
            Text("\(a.desc) · +\(a.reward) ojos").font(.system(size: 13)).foregroundStyle(V.muted)
            RuneBar(value: v / a.target, color: date != nil ? V.gold : V.ember, height: 5)
        }
        .padding(.vertical, 6)
    }
}

// MARK: - Mercader

struct ShopSheet: View {
    @ObservedObject var game: GameState
    var onMessage: (String, Color) -> Void
    @State private var confirm: ShopItem?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    sheetHeading("El mercader")
                    Spacer()
                    EyeIcon(size: 22).padding(.top, 14)
                    Text("\(game.eyes)").font(V.cinzelBold(20)).foregroundStyle(V.eye).padding(.top, 14)
                }
                caption("Ganas ojos con la app abierta (1 por minuto), caminando (\(GameState.eyesPerWalkStep) cada \(Int(GameState.walkStepM)) m), descubriendo lugares y, sobre todo, con las hazañas.")

                ForEach(ShopCat.allCases, id: \.self) { cat in
                    sheetHeading("ᛟ \(cat.title)")
                    caption(cat.hint)
                    ForEach(ShopCatalog.items.filter { $0.cat == cat }) { item in itemRow(item) }
                }
            }
            .padding(.horizontal, 20).padding(.bottom, 32)
        }
        .alert(item: $confirm) { item in
            Alert(
                title: Text(item.name),
                message: Text("¿Cambiar \(item.price) Ojos de Greydwarf por este estilo? Te quedarían \(game.eyes - item.price)."),
                primaryButton: .default(Text("Comprar")) {
                    if game.buy(item) { onMessage("ᛟ Comprado ᛟ\n\(item.name)", V.gold) }
                },
                secondaryButton: .cancel(Text("Cancelar"))
            )
        }
    }

    private func itemRow(_ item: ShopItem) -> some View {
        HStack(spacing: 12) {
            Image(uiImage: Previews.image(for: item))
                .resizable()
                .frame(width: 60, height: 60)
                .clipShape(RoundedRectangle(cornerRadius: 6))
                .overlay(RoundedRectangle(cornerRadius: 6).stroke(V.bronzeDark, lineWidth: 1))
            VStack(alignment: .leading, spacing: 2) {
                Text(item.name).font(V.cinzelBold(15)).foregroundStyle(V.parchment)
                Text(item.desc).font(.system(size: 12)).foregroundStyle(V.muted)
            }
            Spacer(minLength: 4)
            action(item)
        }
        .padding(10)
        .background(
            RoundedRectangle(cornerRadius: 8).fill(Color.black.opacity(0.14))
                .overlay(RoundedRectangle(cornerRadius: 8).stroke(V.bronzeDark, lineWidth: 1))
        )
    }

    @ViewBuilder
    private func action(_ item: ShopItem) -> some View {
        if game.isEquipped(item) {
            Button("EN USO") {}.buttonStyle(PlankButtonStyle(selected: true)).disabled(true)
        } else if game.owns(item) {
            Button("USAR") { game.equip(item) }.buttonStyle(PlankButtonStyle())
        } else if game.eyes >= item.price {
            Button { confirm = item } label: { Text("\(item.price) ojos").font(V.cinzelBold(13)) }
                .buttonStyle(EmberCompactStyle())
        } else {
            Button("\(item.price) ojos") {
                onMessage("Te faltan \(item.price - game.eyes) Ojos de Greydwarf\nSal a explorar", V.muted)
            }
            .buttonStyle(PlankButtonStyle())
            .opacity(0.7)
        }
    }
}

struct EmberCompactStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .foregroundStyle(V.onEmber)
            .padding(.horizontal, 12)
            .frame(minHeight: 38)
            .background(
                Capsule().fill(LinearGradient(colors: [V.ember, V.emberDark], startPoint: .top, endPoint: .bottom))
                    .overlay(Capsule().stroke(V.gold, lineWidth: 1.5))
            )
            .opacity(configuration.isPressed ? 0.85 : 1)
    }
}

// MARK: - Pines

struct AddPinSheet: View {
    let testMode: Bool
    var onSave: (PinType, String) -> Void
    var onSimulate: () -> Void
    @State private var type: PinType = .food
    @State private var note = ""

    private let columns = Array(repeating: GridItem(.flexible(), spacing: 6), count: 4)

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 10) {
                sheetHeading("Nuevo pin")
                caption("Elige qué quieres recordar en este punto")
                LazyVGrid(columns: columns, spacing: 8) {
                    ForEach(PinType.allCases, id: \.self) { t in
                        Button { type = t } label: {
                            VStack(spacing: 6) {
                                PinBadge(type: t, size: 40)
                                Text(t.label).font(V.cinzelBold(11)).foregroundStyle(V.parchment)
                                    .lineLimit(1).minimumScaleFactor(0.7)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                            .background(
                                RoundedRectangle(cornerRadius: 8)
                                    .fill(type == t ? V.gold.opacity(0.2) : Color.black.opacity(0.14))
                                    .overlay(RoundedRectangle(cornerRadius: 8)
                                        .stroke(type == t ? V.gold : V.bronzeDark, lineWidth: type == t ? 2 : 1))
                            )
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(t.label)
                    }
                }
                TextField("", text: $note, prompt: Text("Nota rápida (opcional): ej. Döner bueno y barato").foregroundColor(V.muted))
                    .foregroundStyle(V.parchment)
                    .padding(12)
                    .background(
                        RoundedRectangle(cornerRadius: 6).fill(Color.black.opacity(0.25))
                            .overlay(RoundedRectangle(cornerRadius: 6).stroke(V.bronzeDark, lineWidth: 1))
                    )
                    .padding(.top, 6)
                Button { onSave(type, note) } label: { Text("GUARDAR PIN").frame(maxWidth: .infinity) }
                    .buttonStyle(EmberButtonStyle())
                    .padding(.top, 8)
                if testMode {
                    Button { onSimulate() } label: { Text("Modo prueba: simular que estoy aquí").frame(maxWidth: .infinity) }
                        .buttonStyle(PlankButtonStyle())
                }
            }
            .padding(.horizontal, 20).padding(.bottom, 32)
        }
    }
}

struct PinBadge: View {
    let type: PinType
    var size: CGFloat = 40
    var body: some View {
        ZStack {
            Circle().fill(Color(type.color))
            Circle().stroke(V.bronze, lineWidth: 2)
            Image(systemName: type.symbol).font(.system(size: size * 0.4, weight: .bold)).foregroundStyle(V.parchment)
        }
        .frame(width: size, height: size)
    }
}

struct PinListSheet: View {
    @ObservedObject var game: GameState
    var onSelect: (Pin) -> Void

    var body: some View {
        let sorted: [Pin] = game.hasPos
            ? game.pins.sorted { Geo.distance(game.posLat, game.posLon, $0.lat, $0.lon) < Geo.distance(game.posLat, game.posLon, $1.lat, $1.lon) }
            : game.pins.sorted { $0.created > $1.created }
        ScrollView {
            VStack(alignment: .leading, spacing: 4) {
                sheetHeading("Mis pines · \(game.pins.count)")
                if game.pins.isEmpty {
                    caption("Todavía no tienes pines. Mantén presionado el mapa o toca el escudo del pin para poner uno.")
                }
                ForEach(sorted) { p in
                    Button { onSelect(p) } label: {
                        HStack(spacing: 12) {
                            PinBadge(type: p.type, size: 36)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(p.title).font(.system(size: 15, weight: .bold)).foregroundStyle(V.parchment)
                                Text(p.type.label + distanceText(p)).font(.system(size: 12)).foregroundStyle(V.muted)
                            }
                            Spacer()
                        }
                        .padding(.vertical, 6)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 20).padding(.bottom, 32)
        }
    }

    private func distanceText(_ p: Pin) -> String {
        guard game.hasPos else { return "" }
        let m = Geo.distance(game.posLat, game.posLon, p.lat, p.lon)
        return " · " + (m < 1000 ? "\(Int(m)) m" : "\(fmt(m / 1000, 1)) km")
    }
}

struct PinDetailSheet: View {
    let pin: Pin
    @ObservedObject var game: GameState
    var onDelete: () -> Void
    @State private var confirmDelete = false

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 12) {
                PinBadge(type: pin.type, size: 44)
                Text(pin.title).font(V.uncial(22)).foregroundStyle(V.gold).lineLimit(2)
            }
            .padding(.top, 22)
            if !pin.note.isEmpty && pin.note != pin.title {
                Text(pin.note).foregroundStyle(V.parchment)
            }
            Text(detail).font(.system(size: 13)).foregroundStyle(V.muted)
            HStack(spacing: 10) {
                Button {
                    let item = MKMapItem(placemark: MKPlacemark(coordinate: CLLocationCoordinate2D(latitude: pin.lat, longitude: pin.lon)))
                    item.name = pin.title
                    item.openInMaps(launchOptions: [MKLaunchOptionsDirectionsModeKey: MKLaunchOptionsDirectionsModeWalking])
                } label: { Text("CÓMO LLEGAR").frame(maxWidth: .infinity) }
                    .buttonStyle(EmberButtonStyle())
                Button { confirmDelete = true } label: { Text("Eliminar") }
                    .buttonStyle(PlankButtonStyle())
            }
            .padding(.top, 8)
            Spacer()
        }
        .padding(.horizontal, 20)
        .alert("Eliminar pin", isPresented: $confirmDelete) {
            Button("Cancelar", role: .cancel) {}
            Button("Eliminar", role: .destructive) { onDelete() }
        } message: {
            Text("Se borra «\(pin.title)» del mapa.")
        }
    }

    private var detail: String {
        let date = pin.created.formatted(.dateTime.day().month(.abbreviated).year().hour().minute().locale(esLocale))
        var t = "\(pin.type.label) · guardado el \(date)"
        if game.hasPos {
            let m = Geo.distance(game.posLat, game.posLon, pin.lat, pin.lon)
            t += "\nA " + (m < 1000 ? "\(Int(m)) m" : "\(fmt(m / 1000, 1)) km") + " de ti"
        }
        return t
    }
}

struct PoiDetailSheet: View {
    let poi: Poi
    var onAddPin: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(poi.name).font(V.uncial(22)).foregroundStyle(V.gold).padding(.top, 22)
            Text("\(poi.cat.label) · \(poi.scope == .hannover ? "Hannover" : "Alemania")\nDescubierto")
                .font(.system(size: 14)).foregroundStyle(V.muted)
            Button { onAddPin() } label: { Text("Poner pin aquí").frame(maxWidth: .infinity) }
                .buttonStyle(PlankButtonStyle())
                .padding(.top, 8)
            Spacer()
        }
        .padding(.horizontal, 20)
    }
}
