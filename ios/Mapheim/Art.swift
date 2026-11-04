import UIKit

/// Dibujos del mapa: marcador del jugador, marcos de pines y piedras rúnicas.
enum Art {
    static let edge = UIColor(r: 13, g: 9, b: 5)
    static let bronze = UIColor(r: 184, g: 135, b: 74)
    static let parchment = UIColor(r: 234, g: 220, b: 192)
    static let gold = UIColor(r: 232, g: 176, b: 74)
    static let rune = UIColor(r: 127, g: 224, b: 200)

    private static var cache: [String: UIImage] = [:]

    private static func cached(_ key: String, _ size: CGSize, _ draw: (CGContext) -> Void) -> UIImage {
        if let img = cache[key] { return img }
        let img = UIGraphicsImageRenderer(size: size).image { draw($0.cgContext) }
        cache[key] = img
        return img
    }

    // MARK: Jugador

    static let cursorSize = CGSize(width: 72, height: 72)

    static func cursor(_ style: String, active: Bool) -> UIImage {
        cached("cur_\(style)_\(active)", cursorSize) { c in
            drawCursor(c, x: 36, y: 36, style: style, on: active)
        }
    }

    static func drawCursor(_ c: CGContext, x: CGFloat, y: CGFloat, style: String, on: Bool) {
        switch style {
        case "cur_shield": shield(c, x, y, on)
        case "cur_rune": runeStone(c, x, y, on)
        case "cur_wisp": wisp(c, x, y, on)
        case "cur_ember": ember(c, x, y, on)
        default: torch(c, x, y, on)
        }
    }

    private static func circle(_ c: CGContext, _ x: CGFloat, _ y: CGFloat, _ r: CGFloat, _ color: UIColor) {
        c.setFillColor(color.cgColor)
        c.fillEllipse(in: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
    }

    private static func ring(_ c: CGContext, _ x: CGFloat, _ y: CGFloat, _ r: CGFloat, _ color: UIColor, _ w: CGFloat) {
        c.setStrokeColor(color.cgColor)
        c.setLineWidth(w)
        c.strokeEllipse(in: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
    }

    private static func glow(_ c: CGContext, _ x: CGFloat, _ y: CGFloat, _ color: UIColor, _ big: CGFloat) {
        circle(c, x, y, big, color.withAlphaComponent(0.14))
        circle(c, x, y, big * 0.6, color.withAlphaComponent(0.28))
    }

    private static func torch(_ c: CGContext, _ x: CGFloat, _ y: CGFloat, _ on: Bool) {
        let base = on ? UIColor(r: 224, g: 120, b: 47) : UIColor(r: 168, g: 151, b: 124)
        if on { glow(c, x, y, base, 28) }
        circle(c, x, y, 8, base)
        ring(c, x, y, 8, edge, 2.5)
        if on { circle(c, x, y, 3.5, UIColor(r: 255, g: 226, b: 160)) }
    }

    private static func shield(_ c: CGContext, _ x: CGFloat, _ y: CGFloat, _ on: Bool) {
        if on { glow(c, x, y, UIColor(r: 224, g: 120, b: 47), 26) }
        let r: CGFloat = 12
        circle(c, x, y, r + 2, edge)
        circle(c, x, y, r, UIColor(r: 110, g: 70, b: 38))
        c.setStrokeColor(UIColor(r: 70, g: 44, b: 24).cgColor)
        c.setLineWidth(1.2)
        for i in -1...1 {
            let dx = CGFloat(i) * r * 0.45
            c.move(to: CGPoint(x: x + dx, y: y - r * 0.92))
            c.addLine(to: CGPoint(x: x + dx, y: y + r * 0.92))
        }
        c.strokePath()
        ring(c, x, y, r, bronze, 2.5)
        circle(c, x, y, 3.8, UIColor(r: 200, g: 160, b: 90))
        ring(c, x, y, 3.8, edge, 1)
    }

    private static func runeStone(_ c: CGContext, _ x: CGFloat, _ y: CGFloat, _ on: Bool) {
        let rc = on ? rune : UIColor(r: 150, g: 160, b: 150)
        if on { glow(c, x, y, rc, 26) }
        let w: CGFloat = 10, h: CGFloat = 13
        let p = UIBezierPath()
        p.move(to: CGPoint(x: x - w * 0.8, y: y + h))
        p.addLine(to: CGPoint(x: x - w, y: y - h * 0.4))
        p.addQuadCurve(to: CGPoint(x: x + w, y: y - h * 0.4), controlPoint: CGPoint(x: x, y: y - h * 1.35))
        p.addLine(to: CGPoint(x: x + w * 0.8, y: y + h))
        p.close()
        UIColor(r: 96, g: 98, b: 96).setFill(); p.fill()
        edge.setStroke(); p.lineWidth = 2; p.stroke()
        c.setStrokeColor(rc.cgColor)
        c.setLineWidth(2.2)
        c.setLineCap(.round)
        c.move(to: CGPoint(x: x, y: y + h * 0.65)); c.addLine(to: CGPoint(x: x, y: y - h * 0.65))
        c.move(to: CGPoint(x: x, y: y - h * 0.05)); c.addLine(to: CGPoint(x: x - w * 0.55, y: y - h * 0.6))
        c.move(to: CGPoint(x: x, y: y - h * 0.05)); c.addLine(to: CGPoint(x: x + w * 0.55, y: y - h * 0.6))
        c.strokePath()
    }

    private static func wisp(_ c: CGContext, _ x: CGFloat, _ y: CGFloat, _ on: Bool) {
        let r: CGFloat = on ? 30 : 18
        let colors = [UIColor(r: 235, g: 250, b: 255).cgColor,
                      UIColor(r: 120, g: 200, b: 255, a: 0.67).cgColor,
                      UIColor(r: 80, g: 140, b: 255, a: 0).cgColor] as CFArray
        if let g = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors, locations: [0, 0.3, 1]) {
            c.drawRadialGradient(g, startCenter: CGPoint(x: x, y: y), startRadius: 0,
                                 endCenter: CGPoint(x: x, y: y), endRadius: r, options: [])
        }
        circle(c, x, y, 4.5, .white)
    }

    private static func ember(_ c: CGContext, _ x: CGFloat, _ y: CGFloat, _ on: Bool) {
        let hot = on ? UIColor(r: 255, g: 110, b: 30) : UIColor(r: 150, g: 90, b: 60)
        if on { glow(c, x, y, UIColor(r: 255, g: 80, b: 20), 30) }
        c.setFillColor(hot.cgColor)
        for i in 0..<8 {
            let a = (Double(i) * 45 + 22.5) * .pi / 180
            c.move(to: CGPoint(x: x + CGFloat(cos(a - 0.28) * 8), y: y + CGFloat(sin(a - 0.28) * 8)))
            c.addLine(to: CGPoint(x: x + CGFloat(cos(a) * 16), y: y + CGFloat(sin(a) * 16)))
            c.addLine(to: CGPoint(x: x + CGFloat(cos(a + 0.28) * 8), y: y + CGFloat(sin(a + 0.28) * 8)))
            c.closePath()
        }
        c.fillPath()
        circle(c, x, y, 9.5, edge)
        circle(c, x, y, 8, UIColor(r: 200, g: 60, b: 20))
        circle(c, x, y, 4, UIColor(r: 255, g: 200, b: 90))
    }

    // MARK: Pines

    /// La punta del pin queda en el borde inferior de la imagen.
    static let pinSize = CGSize(width: 40, height: 52)

    static func pin(_ type: PinType, style: String) -> UIImage {
        cached("pin_\(type.rawValue)_\(style)", pinSize) { c in
            drawPin(c, x: 20, cy: 20, tipY: 51, r: 14, fill: type.color, style: style)
            if let sym = UIImage(systemName: type.symbol,
                                 withConfiguration: UIImage.SymbolConfiguration(pointSize: 12, weight: .bold))?
                .withTintColor(parchment, renderingMode: .alwaysOriginal) {
                let s = sym.size
                sym.draw(in: CGRect(x: 20 - s.width / 2, y: 20 - s.height / 2, width: s.width, height: s.height))
            }
        }
    }

    static func drawPin(_ c: CGContext, x: CGFloat, cy: CGFloat, tipY: CGFloat, r: CGFloat, fill: UIColor, style: String) {
        c.setFillColor(edge.cgColor)
        c.move(to: CGPoint(x: x - r * 0.55, y: cy + r * 0.6))
        c.addLine(to: CGPoint(x: x, y: tipY))
        c.addLine(to: CGPoint(x: x + r * 0.55, y: cy + r * 0.6))
        c.closePath()
        c.fillPath()

        if style == "pin_runestone" {
            diamond(c, x, cy, r + 2.5); c.setFillColor(edge.cgColor); c.fillPath()
            diamond(c, x, cy, r); c.setFillColor(fill.cgColor); c.fillPath()
            diamond(c, x, cy, r); c.setStrokeColor(rune.cgColor); c.setLineWidth(1.8); c.strokePath()
            return
        }
        let ringColor: UIColor
        switch style {
        case "pin_iron": ringColor = UIColor(r: 160, g: 166, b: 172)
        case "pin_gold": ringColor = gold
        default: ringColor = bronze
        }
        circle(c, x, cy, r + 2, edge)
        circle(c, x, cy, r, fill)
        ring(c, x, cy, r, ringColor, style == "pin_gold" ? 3.2 : 2)
        if style == "pin_shieldrim" {
            ring(c, x, cy, r - 3.5, UIColor(r: 110, g: 78, b: 42), 1.2)
            for i in 0..<4 {
                let a = (Double(i) * 90 + 45) * .pi / 180
                circle(c, x + CGFloat(cos(a)) * r, cy + CGFloat(sin(a)) * r, 2.2, UIColor(r: 200, g: 160, b: 90))
            }
        }
    }

    private static func diamond(_ c: CGContext, _ x: CGFloat, _ y: CGFloat, _ r: CGFloat) {
        c.move(to: CGPoint(x: x, y: y - r * 1.2))
        c.addLine(to: CGPoint(x: x + r, y: y))
        c.addLine(to: CGPoint(x: x, y: y + r * 1.2))
        c.addLine(to: CGPoint(x: x - r, y: y))
        c.closePath()
    }

    // MARK: Lugares

    static func poiColor(_ cat: Cat) -> UIColor {
        switch cat {
        case .park: return UIColor(r: 141, g: 170, b: 94)
        case .forest: return UIColor(r: 79, g: 122, b: 69)
        case .sight: return gold
        case .landmark: return UIColor(r: 166, g: 61, b: 47)
        }
    }

    static func poi(_ cat: Cat, famous: Bool, found: Bool) -> UIImage {
        let size = CGSize(width: 30, height: 36)
        return cached("poi_\(cat.rawValue)_\(famous)_\(found)", size) { c in
            let x: CGFloat = 15, y: CGFloat = 18
            let r: CGFloat = famous ? 8 : 5.5
            if found {
                diamond(c, x, y, r + 2.5); c.setFillColor(edge.cgColor); c.fillPath()
                diamond(c, x, y, r); c.setFillColor(poiColor(cat).cgColor); c.fillPath()
                diamond(c, x, y, r); c.setStrokeColor(parchment.cgColor); c.setLineWidth(1.5); c.strokePath()
            } else {
                diamond(c, x, y, r + 4); c.setFillColor(edge.cgColor); c.fillPath()
                diamond(c, x, y, r + 4); c.setStrokeColor(gold.withAlphaComponent(0.85).cgColor); c.setLineWidth(2); c.strokePath()
                let attrs: [NSAttributedString.Key: Any] = [
                    .font: UIFont(name: "Cinzel-Bold", size: 12) ?? UIFont.boldSystemFont(ofSize: 12),
                    .foregroundColor: gold
                ]
                let q = NSAttributedString(string: "?", attributes: attrs)
                let s = q.size()
                q.draw(at: CGPoint(x: x - s.width / 2, y: y - s.height / 2))
            }
        }
    }
}
