# M8 Tracker iOS

Native iOS port scaffold for M8droid.

## Current status

This is a SwiftUI/XcodeGen skeleton that proves the iOS app can be generated and built independently from the Android app. It includes:

- SwiftUI app entry point
- M8-style display placeholder
- Touch controls matching the M8 navigation/action layout
- iOS project generation via XcodeGen
- GitHub Actions workflow for simulator builds on macOS

It does **not** yet include the full Android feature set. The next phases are the ones described in `.planning/IOS_PORT_PLAN.md`:

1. Move portable Kotlin logic into a shared KMP module.
2. Add iOS C FFI for the Rust synth engine.
3. Wire SwiftUI views to shared emulator/protocol/synth logic.
4. Add native iOS audio, MIDI, persistence, and remote WebSocket support.

## Generate the Xcode project

Install XcodeGen on macOS:

```bash
brew install xcodegen
```

Generate the project:

```bash
cd ios
xcodegen generate
```

Open/build:

```bash
open M8Tracker.xcodeproj
```

Or from CLI:

```bash
xcodebuild \
  -project M8Tracker.xcodeproj \
  -scheme M8Tracker \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  build
```

## Why SwiftUI, not Flutter

The Android app is native Kotlin + Jetpack Compose, not Flutter. A Flutter port would require a larger rewrite and would not reuse the current Android UI directly. SwiftUI is the intended iOS layer, with shared business/emulator logic moving into KMP over time.
