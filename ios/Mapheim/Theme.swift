import SwiftUI
import UIKit
import CoreImage

/// Paleta: niebla fría, madera quemada y bronce.
enum V {
    static let woodDark = Color(red: 27 / 255, green: 19 / 255, blue: 12 / 255)
    static let wood = Color(red: 44 / 255, green: 31 / 255, blue: 20 / 255)
    static let woodLight = Color(red: 63 / 255, green: 44 / 255, blue: 28 / 255)
    static let edge = Color(red: 13 / 255, green: 9 / 255, blue: 5 / 255)
    static let bronze = Color(red: 184 / 255, green: 135 / 255, blue: 74 / 255)
    static let bronzeDark = Color(red: 110 / 255, green: 78 / 255, blue: 42 / 255)
    static let gold = Color(red: 232 / 255, green: 176 / 255, blue: 74 / 255)
    static let ember = Color(red: 224 / 255, green: 120 / 255, blue: 47 / 255)
    static let emberDark = Color(red: 169 / 255, green: 80 / 255, blue: 28 / 255)
    static let onEmber = Color(red: 26 / 255, green: 15 / 255, blue: 6 / 255)
    static let parchment = Color(red: 234 / 255, green: 220 / 255, blue: 192 / 255)
    static let muted = Color(red: 168 / 255, green: 151 / 255, blue: 124 / 255)
    static let track = Color(red: 58 / 255, green: 42 / 255, blue: 28 / 255)
    static let moss = Color(red: 141 / 255, green: 170 / 255, blue: 94 / 255)
    static let eye = Color(red: 127 / 255, green: 224 / 255, blue: 200 / 255)

    static func uncial(_ size: CGFloat) -> Font { .custom("UncialAntiqua-Regular", size: size) }
    static func cinzel(_ size: CGFloat) -> Font { .custom("Cinzel-Regular", size: size) }
    static func cinzelBold(_ size: CGFloat) -> Font { .custom("Cinzel-Bold", size: size) }
}

/// Tablilla de madera tallada con borde de bronce.
struct WoodPanel: ViewModifier {
    var corner: CGFloat = 9
    func body(content: Content) -> some View {
        content
            .background(
                ZStack {
                    RoundedRectangle(cornerRadius: corner).fill(V.edge)
                    RoundedRectangle(cornerRadius: corner - 2)
                        .fill(LinearGradient(colors: [V.woodLight, V.woodDark], startPoint: .top, endPoint: .bottom))
                        .overlay(RoundedRectangle(cornerRadius: corner - 2).stroke(V.bronze, lineWidth: 1.5))
                        .padding(2)
                    RoundedRectangle(cornerRadius: corner - 5)
                        .stroke(V.bronzeDark.opacity(0.5), style: StrokeStyle(lineWidth: 1, dash: [10, 3]))
                        .padding(6)
                }
            )
            .shadow(color: .black.opacity(0.5), radius: 6, y: 3)
    }
}

extension View {
    func woodPanel(corner: CGFloat = 9) -> some View { modifier(WoodPanel(corner: corner)) }
}

/// Fondo de las hojas (sheets).
struct SheetBackground: View {
    var body: some View {
        LinearGradient(colors: [V.wood, V.woodDark], startPoint: .top, endPoint: .bottom)
            .overlay(alignment: .top) { Rectangle().fill(V.bronze).frame(height: 2) }
            .ignoresSafeArea()
    }
}

/// Botón redondo en forma de escudo.
struct ShieldButton<Icon: View>: View {
    let label: String
    let action: () -> Void
    @ViewBuilder let icon: () -> Icon

    var body: some View {
        Button(action: action) {
            ZStack {
                Circle().fill(V.edge)
                Circle()
                    .fill(RadialGradient(colors: [V.woodLight, V.woodDark], center: UnitPoint(x: 0.4, y: 0.35), startRadius: 0, endRadius: 28))
                    .overlay(Circle().stroke(V.bronze, lineWidth: 2.5))
                    .padding(2)
                Circle().stroke(V.bronzeDark, lineWidth: 1).padding(7)
                icon()
            }
            .frame(width: 50, height: 50)
            .shadow(color: .black.opacity(0.5), radius: 4, y: 2)
        }
        .accessibilityLabel(label)
    }
}

/// Botón principal: brasa encendida.
struct EmberButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(V.cinzelBold(16))
            .tracking(1.2)
            .foregroundStyle(V.onEmber)
            .padding(.horizontal, 22)
            .frame(height: 52)
            .background(
                Capsule().fill(V.edge)
                    .overlay(
                        Capsule()
                            .fill(LinearGradient(colors: [V.ember, V.emberDark], startPoint: .top, endPoint: .bottom))
                            .overlay(Capsule().stroke(V.gold, lineWidth: 1.5))
                            .padding(2)
                    )
            )
            .opacity(configuration.isPressed ? 0.85 : 1)
            .shadow(color: .black.opacity(0.5), radius: 5, y: 3)
    }
}

/// Botón secundario: placa de madera.
struct PlankButtonStyle: ButtonStyle {
    var selected = false
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(V.cinzelBold(13))
            .foregroundStyle(selected ? V.gold : V.parchment)
            .padding(.horizontal, 12)
            .frame(minHeight: 38)
            .background(
                RoundedRectangle(cornerRadius: 8)
                    .fill(selected ? V.gold.opacity(0.2) : Color.black.opacity(0.14))
                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(selected ? V.gold : V.bronzeDark, lineWidth: selected ? 2 : 1))
            )
            .opacity(configuration.isPressed ? 0.8 : 1)
    }
}

/// Ojo de Greydwarf: dibujo original (piedra con iris verde azulado).
struct EyeIcon: View {
    var size: CGFloat = 20
    var body: some View {
        ZStack {
            Circle().fill(Color(red: 42 / 255, green: 30 / 255, blue: 20 / 255))
            Circle().fill(Color(red: 92 / 255, green: 70 / 255, blue: 48 / 255)).padding(size * 0.07)
            Circle().fill(Color(red: 47 / 255, green: 140 / 255, blue: 122 / 255)).padding(size * 0.23)
            Circle().fill(V.eye).padding(size * 0.29)
            Ellipse().fill(Color(red: 16 / 255, green: 34 / 255, blue: 30 / 255))
                .frame(width: size * 0.13, height: size * 0.32)
            Circle().fill(.white.opacity(0.8)).frame(width: size * 0.09).offset(x: -size * 0.1, y: -size * 0.12)
        }
        .frame(width: size, height: size)
        .shadow(color: V.eye.opacity(0.5), radius: size * 0.15)
    }
}

/// Barra de progreso tallada.
struct RuneBar: View {
    var value: Double
    var color: Color = V.ember
    var height: CGFloat = 7
    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                RoundedRectangle(cornerRadius: 2).fill(V.track)
                RoundedRectangle(cornerRadius: 2).fill(color)
                    .frame(width: max(0, min(1, value)) * geo.size.width)
            }
        }
        .frame(height: height)
    }
}

/// Vistas previas de la tienda.
enum Previews {
    private static var cache: [String: UIImage] = [:]
    private static let ci = CIContext(options: [.workingColorSpace: NSNull(), .outputColorSpace: NSNull()])

    static func image(for item: ShopItem) -> UIImage {
        if let i = cache[item.id] { return i }
        let size = CGSize(width: 60, height: 60)
        var img = UIGraphicsImageRenderer(size: size).image { ctx in
            let c = ctx.cgContext
            switch item.cat {
            case .map, .fog:
                sampleMap(c, size)
                if item.cat == .fog {
                    c.setFillColor(ShopCatalog.fogColor(item.id).cgColor)
                    c.fill(CGRect(origin: .zero, size: size))
                    c.setBlendMode(.destinationOut)
                    c.fillEllipse(in: CGRect(x: 26, y: 8, width: 24, height: 24))
                    c.setBlendMode(.normal)
                }
            case .cursor:
                c.setFillColor(UIColor(r: 20, g: 24, b: 31).cgColor)
                c.fill(CGRect(origin: .zero, size: size))
                Art.drawCursor(c, x: 30, y: 30, style: item.id, on: true)
            case .pin:
                c.setFillColor(UIColor(r: 30, g: 22, b: 15).cgColor)
                c.fill(CGRect(origin: .zero, size: size))
                let pin = Art.pin(.food, style: item.id)
                pin.draw(in: CGRect(x: 10, y: 4, width: Art.pinSize.width, height: Art.pinSize.height))
            }
        }
        if item.cat == .map, let ciImg = CIImage(image: img) {
            let out = ShopCatalog.filter(ciImg, matrix: ShopCatalog.mapMatrix(item.id))
            if let cg = ci.createCGImage(out, from: ciImg.extent) {
                img = UIImage(cgImage: cg, scale: img.scale, orientation: .up)
            }
        }
        cache[item.id] = img
        return img
    }

    private static func sampleMap(_ c: CGContext, _ s: CGSize) {
        let w = s.width, h = s.height
        c.setFillColor(UIColor(r: 242, g: 239, b: 233).cgColor); c.fill(CGRect(x: 0, y: 0, width: w, height: h))
        c.setFillColor(UIColor(r: 173, g: 209, b: 158).cgColor); c.fill(CGRect(x: 0, y: 0, width: w * 0.45, height: h * 0.55))
        c.setFillColor(UIColor(r: 170, g: 211, b: 223).cgColor); c.fill(CGRect(x: w * 0.6, y: h * 0.5, width: w * 0.4, height: h * 0.5))
        c.setFillColor(UIColor(r: 248, g: 210, b: 140).cgColor); c.fill(CGRect(x: 0, y: h * 0.68, width: w, height: h * 0.08))
        c.setFillColor(UIColor(r: 217, g: 208, b: 201).cgColor); c.fill(CGRect(x: w * 0.5, y: 0, width: w * 0.06, height: h))
    }
}
