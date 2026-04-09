# m8

A standalone M8 tracker for Android — no hardware required.

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

- **Remote mode** — can also connect to a real M8 Headless over WebSocket if you do have the hardware

### What it doesn't do (yet)

- Note input from the UI (you can watch the demo song, not compose yet)
- Sample playback / wavetable import
- Save/load songs
- MIDI input
- Load .m8s files

## Quick Start

### Just the app (no hardware needed)

1. Build: `./gradlew assembleDebug`
2. Install the APK on your Android device
3. Open — the emulator starts automatically in local mode
4. Press PLAY to hear the demo song
5. Use the d-pad and buttons to navigate screens

### Remote mode (with M8 hardware)

If you have a Teensy 4.1 running M8 Headless connected to a Linux server:

```bash
# Install and start the server
cd server
pip install websockets pyserial pyserial-asyncio
python3 m8_emulator.py --host 0.0.0.0
```

In the app, go to Settings and enter your server's IP. The app switches to remote mode and connects over WebSocket.

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

REMOTE MODE:
[Teensy 4.1 + M8 Headless]
        | USB
[Linux Server]
  └── m8_emulator.py (WebSocket)
        | network
[Android App]
  ├── Display + Audio via WebSocket
  └── Touch input sent back
```

## Project Structure

```
app/src/main/java/com/m8/
├── MainActivity.kt          Activity, keyboard input
├── M8ViewModel.kt           Orchestrates emulator + synth + audio
├── emulator/
│   ├── M8Emulator.kt        Virtual tracker (patterns, navigation, display)
│   └── M8Synth.kt           DSP engine (oscillators, filters, effects)
├── audio/
│   ├── M8AudioPlayer.kt     AudioTrack output (44.1kHz stereo)
│   ├── M8AudioClient.kt     WebSocket audio client (remote mode)
│   └── OpusDecoder.kt       MediaCodec Opus decoder (remote mode)
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

## Sources & Inspiration

- [Dirtywave M8](https://dirtywave.com/) — the original hardware tracker
- [M8 Headless Firmware](https://github.com/Dirtywave/M8HeadlessFirmware)
- [m8c](https://github.com/laamaa/m8c) — native desktop M8 client
- [M8 Web Display](https://github.com/Dirtywave/M8WebDisplay) — browser client

## License

MIT — see [LICENSE](LICENSE)

This project is not affiliated with or endorsed by Dirtywave. M8 is a trademark of Dirtywave.
