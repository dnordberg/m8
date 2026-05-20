import SwiftUI

struct ContentView: View {
    @State private var selectedKey: M8Key?

    var body: some View {
        ZStack {
            M8Colors.background.ignoresSafeArea()

            VStack(spacing: 18) {
                HeaderView()

                M8DisplayView(activeKey: selectedKey)
                    .aspectRatio(4.0 / 3.0, contentMode: .fit)
                    .padding(.horizontal, 20)

                M8ControlsView(selectedKey: $selectedKey)
                    .padding(.horizontal, 20)

                Spacer(minLength: 12)
            }
            .padding(.top, 20)
        }
    }
}

private struct HeaderView: View {
    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text("M8 Tracker")
                    .font(.system(.title2, design: .monospaced, weight: .bold))
                    .foregroundStyle(M8Colors.text)
                Text("iOS build skeleton")
                    .font(.system(.caption, design: .monospaced))
                    .foregroundStyle(M8Colors.muted)
            }

            Spacer()

            Text("LOCAL")
                .font(.system(.caption, design: .monospaced, weight: .semibold))
                .foregroundStyle(M8Colors.background)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(M8Colors.accent)
                .clipShape(Capsule())
        }
        .padding(.horizontal, 20)
    }
}

#Preview {
    ContentView()
        .preferredColorScheme(.dark)
}
