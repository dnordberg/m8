import SwiftUI

struct M8DisplayView: View {
    let activeKey: M8Key?

    private let columns = ["SONG", "CHAIN", "PHRASE", "INS", "TABLE", "MIX", "FX", "CONF"]

    var body: some View {
        Canvas { context, size in
            let rect = CGRect(origin: .zero, size: size)
            context.fill(Path(rect), with: .color(M8Colors.screen))

            drawGrid(in: rect, context: &context)
            drawHeader(in: rect, context: &context)
            drawRows(in: rect, context: &context)
            drawFooter(in: rect, context: &context)
        }
        .overlay {
            RoundedRectangle(cornerRadius: 14)
                .stroke(M8Colors.panelHighlight, lineWidth: 2)
        }
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .shadow(color: .black.opacity(0.45), radius: 18, y: 10)
    }

    private func drawGrid(in rect: CGRect, context: inout GraphicsContext) {
        let cellWidth = rect.width / CGFloat(columns.count)
        let cellHeight = rect.height / 12.0

        for col in 0...columns.count {
            let x = CGFloat(col) * cellWidth
            var path = Path()
            path.move(to: CGPoint(x: x, y: 0))
            path.addLine(to: CGPoint(x: x, y: rect.height))
            context.stroke(path, with: .color(M8Colors.grid.opacity(0.55)), lineWidth: 1)
        }

        for row in 0...12 {
            let y = CGFloat(row) * cellHeight
            var path = Path()
            path.move(to: CGPoint(x: 0, y: y))
            path.addLine(to: CGPoint(x: rect.width, y: y))
            context.stroke(path, with: .color(M8Colors.grid.opacity(0.35)), lineWidth: 1)
        }
    }

    private func drawHeader(in rect: CGRect, context: inout GraphicsContext) {
        let cellWidth = rect.width / CGFloat(columns.count)
        for (idx, title) in columns.enumerated() {
            let text = Text(title)
                .font(.system(size: max(9, rect.width / 42), weight: .bold, design: .monospaced))
                .foregroundColor(idx == 0 ? M8Colors.accent : M8Colors.text)
            context.draw(text, at: CGPoint(x: CGFloat(idx) * cellWidth + cellWidth / 2, y: rect.height * 0.08), anchor: .center)
        }
    }

    private func drawRows(in rect: CGRect, context: inout GraphicsContext) {
        let labels = ["00", "01", "02", "03", "04", "05", "06", "07"]
        let notes = ["C-3", "---", "G-3", "---", "A#2", "---", "D-4", "---"]
        let cellWidth = rect.width / CGFloat(columns.count)

        for idx in labels.indices {
            let y = rect.height * (0.21 + CGFloat(idx) * 0.07)
            let rowColor = idx == 0 ? M8Colors.orange : M8Colors.muted
            context.draw(
                Text(labels[idx]).font(.system(size: max(10, rect.width / 36), weight: .regular, design: .monospaced)).foregroundColor(rowColor),
                at: CGPoint(x: cellWidth * 0.5, y: y),
                anchor: .center
            )
            context.draw(
                Text(notes[idx]).font(.system(size: max(10, rect.width / 36), weight: .semibold, design: .monospaced)).foregroundColor(M8Colors.text),
                at: CGPoint(x: cellWidth * 1.5, y: y),
                anchor: .center
            )
        }
    }

    private func drawFooter(in rect: CGRect, context: inout GraphicsContext) {
        let pressed = activeKey?.rawValue.uppercased() ?? "NONE"
        let footer = "M8DROID iOS PORT · KEY: \(pressed)"
        context.draw(
            Text(footer).font(.system(size: max(9, rect.width / 44), weight: .medium, design: .monospaced)).foregroundColor(M8Colors.accent),
            at: CGPoint(x: rect.midX, y: rect.height * 0.92),
            anchor: .center
        )
    }
}

#Preview {
    M8DisplayView(activeKey: .play)
        .frame(width: 360, height: 270)
        .padding()
        .background(M8Colors.background)
}
