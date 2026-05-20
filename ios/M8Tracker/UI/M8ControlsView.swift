import SwiftUI

enum M8Key: String, CaseIterable, Identifiable {
    case up, down, left, right, option, edit, play, shift

    var id: String { rawValue }

    var label: String {
        switch self {
        case .up: "▲"
        case .down: "▼"
        case .left: "◀"
        case .right: "▶"
        case .option: "OPT"
        case .edit: "EDIT"
        case .play: "PLAY"
        case .shift: "SHIFT"
        }
    }
}

struct M8ControlsView: View {
    @Binding var selectedKey: M8Key?

    var body: some View {
        HStack(alignment: .center, spacing: 24) {
            dPad
            Spacer(minLength: 8)
            actionPad
        }
        .padding(18)
        .background(M8Colors.panel)
        .clipShape(RoundedRectangle(cornerRadius: 22))
        .overlay {
            RoundedRectangle(cornerRadius: 22)
                .stroke(M8Colors.panelHighlight, lineWidth: 1)
        }
    }

    private var dPad: some View {
        Grid(horizontalSpacing: 8, verticalSpacing: 8) {
            GridRow {
                Color.clear.frame(width: 56, height: 56)
                keyButton(.up)
                Color.clear.frame(width: 56, height: 56)
            }
            GridRow {
                keyButton(.left)
                keyButton(.down)
                keyButton(.right)
            }
        }
    }

    private var actionPad: some View {
        Grid(horizontalSpacing: 10, verticalSpacing: 10) {
            GridRow {
                keyButton(.option)
                keyButton(.edit)
            }
            GridRow {
                keyButton(.shift)
                keyButton(.play)
            }
        }
    }

    private func keyButton(_ key: M8Key) -> some View {
        Button {
            selectedKey = key
        } label: {
            Text(key.label)
                .font(.system(.headline, design: .monospaced, weight: .bold))
                .foregroundStyle(selectedKey == key ? M8Colors.background : M8Colors.text)
                .frame(width: 56, height: 56)
                .background(selectedKey == key ? M8Colors.accent : M8Colors.panelHighlight)
                .clipShape(RoundedRectangle(cornerRadius: 14))
        }
        .buttonStyle(.plain)
        .simultaneousGesture(
            DragGesture(minimumDistance: 0)
                .onEnded { _ in
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.12) {
                        if selectedKey == key {
                            selectedKey = nil
                        }
                    }
                }
        )
        .accessibilityLabel(key.rawValue)
    }
}

#Preview {
    M8ControlsView(selectedKey: .constant(.play))
        .padding()
        .background(M8Colors.background)
}
