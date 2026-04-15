# Architecture

## Pattern

Multi-tier hybrid: **local-first embedded emulator** with an optional **remote mode** that talks to a Python WebSocket bridge.

The Android app can either:
1. Run the entire M8 emulation in-process (emulator + synth on-device), or
2. Connect over WebSocket to `server/bridge.py` (real M8 over serial) or `server/m8_emulator.py` (headless emulator on host).

## Layers

| Layer | Location | Responsibility |
|-------|----------|----------------|
| Presentation | `app/src/main/java/com/m8droid/ui/` | Jetpack Compose UI, screens, input wiring |
| Orchestration | `app/src/main/java/com/m8droid/M8ViewModel.kt` (~547 lines) | Sequencer timing, state, lifecycle, mode switching |
| Emulation | `app/src/main/java/com/m8droid/emulator/` | M8 tracker model: `M8Emulator.kt` (~1200 lines), `M8Song.kt`, `M8Instrument.kt`, `M8FxEngine.kt`, `M8sParser.kt`, `M8iParser.kt` |
| Audio (native) | `m8-synth/src/lib.rs` (~615 lines) + `app/.../audio/NativeSynth.kt` | 8-voice DSP: PolyBLEP oscillators, SVF filter, ADSR, delay, reverb — called via JNI |
| Audio (fallback) | `app/.../audio/M8Synth.kt` | Kotlin DSP fallback when native lib unavailable |
| Audio output | `app/.../audio/M8AudioPlayer.kt`, `M8AudioClient.kt`, `OpusDecoder.kt` | `AudioTrack` output, remote Opus decode |
| Protocol | `app/.../protocol/M8Protocol.kt`, `M8DisplayBuffer.kt`, `M8Commands.kt` | SLIP binary frame decoder, 320×240 raster buffer |
| Network | `app/.../network/M8WebSocketClient.kt` | Remote-mode WebSocket client |
| Server — emulator | `server/m8_emulator.py` (~1579 lines) | Headless Python implementation of M8 tracker |
| Server — bridge | `server/bridge.py` | Serial→WebSocket bridge to a real M8 device |
| Server — audio | `server/audio_stream.py` | Opus audio streaming (spawns `opus-tools` subprocess) |

## Entry Points

- **Android**: `MainActivity.kt` → `M8ViewModel.startLocalEmulator()` or `startRemoteMode()`
- **Audio thread**: spawned from `M8ViewModel`, runs at `THREAD_PRIORITY_URGENT_AUDIO`
- **Python emulator**: `python server/m8_emulator.py` (standalone)
- **Python bridge**: `python server/bridge.py` (requires serial M8)
- **Rust lib**: built via `cargo` into `libm8_synth.so`, loaded via JNI

## Data Flow (local mode)

```
User input (touch/keyboard/gamepad)
    │
    ▼
M8ViewModel (state + sequencer tick)
    │
    ├─► M8Emulator (resolve row → phrase → trig)
    │      │
    │      ▼
    │   M8FxEngine (FX commands, parameter locks)
    │      │
    │      ▼
    │   NativeSynth (JNI) ──► m8-synth/lib.rs
    │                            │
    │                            ▼
    │                       f32 audio chunk
    │                            │
    │                            ▼
    │                       M8AudioPlayer → AudioTrack
    │
    └─► M8DisplayBuffer (30fps coroutine) ──► Compose UI
```

## Data Flow (remote mode)

```
Real M8 hardware ──serial──► bridge.py ──ws://──► M8WebSocketClient
                                                        │
                                                        ▼
                                             M8Protocol (SLIP decoder)
                                                        │
                                                        ▼
                                             M8DisplayBuffer ──► Compose UI

audio_stream.py ──Opus/ws──► M8AudioClient ──► OpusDecoder ──► AudioTrack
```

## State Management

- `M8ViewModel` holds mutable state flows for UI.
- Sequencer position fields (`songRow`, `chainRow`, `phraseRow`) are `@Volatile` for thread safety between audio thread and UI.
- `M8Song` is mutable and shared across threads **without explicit locks** — a fragility noted in CONCERNS.md.
- `M8DisplayBuffer` uses `synchronized` blocks per-method, but multi-frame operations are not atomic.

## Cross-Cutting Concerns

- **Logging**: `android.util.Log` on Android, stdlib `logging` in Python, none in Rust DSP (hot path).
- **Error handling**: Kotlin uses try/catch + null-safety; Python uses typed exceptions; Rust uses `.unwrap()` heavily (brittle — see CONCERNS.md).
- **Thread affinity**: audio thread is pinned to `URGENT_AUDIO` priority; UI render is a 30fps coroutine on `Dispatchers.Main`.
- **No DI framework**; components are constructed directly in `M8ViewModel`.

## Abstractions

- `NativeSynth` / `M8Synth` share a small surface (trigger voice, render chunk) so the Kotlin fallback can swap in when JNI lib is missing.
- `M8Emulator` exposes a "screen" abstraction — all 8 M8 screens are rendered through the same display buffer path.
- Protocol decoding is isolated in `M8Protocol` so local and remote modes converge on the same `M8DisplayBuffer`.
