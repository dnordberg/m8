# M8droid

A standalone M8-style tracker for Android — no hardware required.

> **Disclaimer:** M8droid is an unofficial, community-built Android client inspired by the [Dirtywave M8](https://dirtywave.com/) headless firmware. It is **not affiliated with, authorized, or endorsed by Dirtywave**. "M8" is a trademark of Dirtywave; "M8droid" is used only to describe compatibility. All original hardware, firmware, and design credit belongs to Dirtywave / Trash80.
>
> For the current honest compatibility/release note, read [M8_DIFFERENCES.md](M8_DIFFERENCES.md). It covers `.m8s` loading, `.m8droid` saving, sound differences, hardware/Teensy limits, and community-feedback positioning.

## What Makes This Different

Every other M8 client requires a physical Teensy 4.1 with M8 Headless firmware connected via USB. This doesn't.

**This is the only M8-style tracker that runs entirely on your phone.**

| Feature | m8c / M8WebDisplay | This project |
|---|---|---|
| Requires Teensy hardware | Yes | No |
| Requires USB connection | Yes | No |
| Requires server | Yes | No |
| Runs on Android | No | Yes |
| Built-in sound engine | No (passes audio from hardware) | Yes (full polyphonic synth) |
| Works offline | No | Yes |

### What's inside

- **Embedded emulator** — generates the same SLIP-encoded draw commands as real M8 hardware, rendered on a pixel-perfect 320×240 Compose Canvas. No network, no USB, no server.

- **Polyphonic synthesizer** with:
  - Band-limited waveforms (PolyBLEP anti-aliased saw, pulse, triangle)
  - 2-operator FM synthesis (bells, metallic tones)
  - Full ADSR envelopes per voice
  - Pulse width modulation with LFO
  - Resonant state-variable filter per voice (LP/HP/BP) with envelope and LFO modulation
  - Sample-and-hold noise with LFSR

- **Effects chain:**
  - Stereo ping-pong delay with damped feedback
  - Dual modulated delay line chorus
  - Schroeder plate reverb (8 comb filters + 4 allpass diffusers)
  - DC offset removal and tanh soft clipping

- **Tracker features:**
  - 8 tracks with distinct instrument presets (lead, bass, pad, hi-hat, FM bell, pluck, sub, SFX)
  - BPM-synced playback (sample-accurate timing, not frame-rate)
  - Swing/groove control
  - Multiple patterns with song arrangement
  - Note-off support (proper ADSR release)
  - Per-track level metering and master stereo VU
  - Live waveform visualization from actual audio output

- **Hardware bridge scaffolding** — WebSocket/client code exists for future real-M8/Teensy bridge work, but local emulator mode is the current release focus.

## What it doesn't do yet

For the detailed compatibility and community-feedback note, see [M8_DIFFERENCES.md](M8_DIFFERENCES.md).

Current important limits:

- It does not run Dirtywave's official firmware or exact synth/DSP engine.
- Imported `.m8s` songs play through an Android-native approximation, so they should not be expected to sound identical to real M8 hardware.
- It saves app-native `.m8droid` projects, not official Dirtywave `.m8s` files.
- `.m8s` export/round-trip back to real M8 hardware is not implemented yet.
- Real M8/Teensy hardware bridge mode is not a proven public release feature yet.

## Download

M8droid has separate signed Android release channels:

- **Beta:** [Download `M8droid-beta.apk`](https://github.com/dnordberg/m8/releases/download/beta/M8droid-beta.apk) — the rolling community-test build.
- **Stable/latest:** [Download `M8droid-latest.apk`](https://github.com/dnordberg/m8/releases/latest/download/M8droid-latest.apk) — available after the first device-smoked stable release.
- **All versioned releases and checksums:** [GitHub Releases](https://github.com/dnordberg/m8/releases)

Beta and stable use the same package (`com.m8droid`) and production signing identity, so a stable build can upgrade a beta from the same version line. Every release also includes a versioned APK and SHA-256 checksum. See [RELEASING.md](RELEASING.md) for the automated tag contract.

## Quick Start

### Development (emulator)

```bash
./run.sh
```

This single command cold boots the Android emulator, builds the APK, uninstalls any old version, installs fresh, and launches the app. No saved state — clean every time.

To watch logs while it runs:

```bash
adb logcat --pid=$(adb shell pidof com.m8droid)
```

To stop the emulator:

```bash
adb -s emulator-5554 emu kill
```

### Install on a real device

1. Build: `./gradlew assembleDebug`
2. Install the APK on your Android device
3. Open — the emulator starts automatically in local mode
4. Press PLAY to hear the demo song
5. Use the d-pad and buttons to navigate screens

### Hardware bridge status

Local emulator mode is the current release focus. WebSocket/client scaffolding exists for future real-M8/Teensy bridge work, but real hardware bridge mode should not be claimed as release-ready until it is wired as a selectable runtime mode and tested with actual hardware. See [M8_DIFFERENCES.md](M8_DIFFERENCES.md) for the detailed distinction between local emulator, external MIDI, and real-M8/Teensy bridge behavior.

## Architecture

```
LOCAL MODE (default):
┌─────────────────────────────┐
│        Android App          │
│  ┌───────────┐ ┌─────────┐ │
│  │ M8Emulator│→│M8Protocol│→ Display (320×240 Canvas)
│  │ (tracker) │ │ (SLIP)  │ │
│  └─────┬─────┘ └─────────┘ │
│        │ phrase data        │
│  ┌─────▼─────┐ ┌─────────┐ │
│  │  M8Synth  │→│AudioTrack│→ Speaker
│  │ (DSP)     │ │ (44.1kHz)│ │
│  └───────────┘ └─────────┘ │
│        ▲ waveform + levels  │
│        └──→ Live VU meters  │
└─────────────────────────────┘

FUTURE HARDWARE BRIDGE MODE (not release-ready yet):
[Real M8 / Teensy bridge]
        | USB/serial
[Bridge host]
        | WebSocket/network
[Android App]
  ├── Display frames from hardware
  └── Touch/key input sent back to hardware
```

## Project Structure

```
app/src/main/java/com/m8droid/
├── MainActivity.kt          Activity, keyboard input
├── M8ViewModel.kt           Orchestrates emulator + synth + audio
├── emulator/
│   ├── M8Emulator.kt        Virtual tracker (patterns, navigation, display)
│   └── M8Synth.kt           DSP engine (oscillators, filters, effects)
├── audio/
│   ├── M8AudioPlayer.kt     AudioTrack output (44.1kHz stereo)
│   ├── M8AudioClient.kt     WebSocket audio client scaffolding
│   └── OpusDecoder.kt       MediaCodec Opus decoder scaffolding
├── protocol/
│   ├── M8Protocol.kt        SLIP frame decoder
│   ├── M8Commands.kt        Key constants and command IDs
│   └── M8DisplayBuffer.kt   320×240 bitmap renderer
├── network/
│   ├── ConnectionManager.kt  WebSocket lifecycle
│   └── M8WebSocketClient.kt  Display WebSocket client
├── ui/
│   ├── M8Screen.kt          Compose Canvas (pixel-perfect scaling)
│   ├── M8Controls.kt        Portrait d-pad + buttons
│   ├── SettingsScreen.kt    Server config
│   └── ConnectionStatus.kt  Status indicators
├── input/KeyMapper.kt       Keyboard + gamepad mapping
└── data/ServerConfig.kt     DataStore persistence

server/
└── m8_emulator.py           Server-side emulator with full synth
```

## Requirements

- Android 8.0+ (API 26)
- That's it. No hardware, no server, no network.

## Tags

- `working-april-8-2026` — first working build
- `its-ok-april-9-2026` — stable before audio overhaul
- `audio-engine-v2-april-9-2026` — ADSR, PolyBLEP, FM, SVF filters, effects
- `audio-complete-april-9-2026` — live visualization, song mode, BPM sync, server synth

## Sources, Credits & Attribution

M8droid would not exist without the work of others. Full credit to:

- **[Dirtywave](https://dirtywave.com/) / Trash80** — creator of the original M8 hardware tracker, the firmware, the UI, the protocol, and the entire concept. Everything interesting about this project originates there.
- **[M8 Headless Firmware](https://github.com/Dirtywave/M8HeadlessFirmware)** — the open-sourced firmware whose protocol and display commands this app implements. Licensed by Dirtywave; see that repository for its license terms.
- **[m8c](https://github.com/laamaa/m8c)** by laamaa — the native desktop M8 client; reference implementation for the SLIP protocol and display rendering.
- **[M8 Web Display](https://github.com/Dirtywave/M8WebDisplay)** — browser client, additional protocol reference.

If you enjoy M8droid, please support Dirtywave by buying a real M8 — the hardware is the real thing and the project exists because of their work.

## License

The M8droid Android application code is released under the MIT license — see [LICENSE](LICENSE).

Portions of the protocol, command set, and display behavior are derived from the Dirtywave M8 Headless Firmware and related open-source clients; those components remain under their respective upstream licenses.

## Trademark Notice

"M8" is a trademark of Dirtywave. **M8droid is an unofficial, fan-made Android client and is not affiliated with, authorized, sponsored, or endorsed by Dirtywave in any way.** The name "M8droid" is used solely to indicate compatibility with the M8 protocol. If Dirtywave requests a name change, this project will comply.
