# Rust Native Synth Engine

The M8 audio engine is implemented in Rust, compiled to native ARM/x86_64 via Android NDK. This eliminates JVM overhead (GC pauses, boxing, thread scheduling jitter) that caused audio distortion in the previous Kotlin implementation.

## Architecture

```
┌─────────────────────────────────────────┐
│              Android App                │
│                                         │
│  Kotlin (UI + Sequencer)                │
│    │                                    │
│    │ JNI calls                          │
│    ▼                                    │
│  ┌─────────────────────────────┐       │
│  │  libm8_synth.so (Rust)      │       │
│  │                             │       │
│  │  8 voices × PolyBLEP osc   │       │
│  │  2-pole SVF filter/voice    │       │
│  │  Exponential ADSR           │       │
│  │  Stereo ping-pong delay     │       │
│  │  Schroeder reverb           │       │
│  │  Transparent soft limiter   │       │
│  │                             │       │
│  │  → 16-bit stereo PCM       │       │
│  └──────────────┬──────────────┘       │
│                 │                       │
│                 ▼                       │
│  AudioTrack.write() (direct, blocking) │
│                 │                       │
│                 ▼                       │
│           Android Audio HAL             │
└─────────────────────────────────────────┘
```

## Why Rust

| Factor | Kotlin/JVM | Rust Native |
|--------|-----------|-------------|
| GC pauses | 5-50ms random stalls | None |
| `sin()` cost | JNI call + boxing | Single CPU instruction |
| Memory allocation | Per-chunk ByteBuffer | Zero in audio path |
| Thread scheduling | Coroutine dispatcher | Direct OS thread |
| Predictability | Non-deterministic | Deterministic |

## Building

```bash
# Build Rust library for Android targets
./build-rust.sh

# Then build the Android APK (includes the .so)
./gradlew assembleDebug
```

### Prerequisites

- Rust toolchain with `aarch64-linux-android` and `x86_64-linux-android` targets
- Android NDK (installed via sdkmanager)
- `cargo-ndk` (`cargo install cargo-ndk`)

```bash
rustup target add aarch64-linux-android x86_64-linux-android
cargo install cargo-ndk
```

## JNI Interface

The Kotlin side calls into Rust via `NativeSynth`:

```kotlin
NativeSynth.init()                          // Create engine
NativeSynth.triggerRow(notes, vols)         // Trigger 8 tracks
val pcm: ByteArray = NativeSynth.generateChunk()  // 735 samples stereo 16-bit
NativeSynth.allNotesOff()                   // Release all
NativeSynth.destroy()                       // Cleanup
```

## DSP Details

### Oscillators
- **Saw**: PolyBLEP anti-aliased (polynomial correction at discontinuities)
- **Pulse**: PolyBLEP with variable width
- **Sine**: `f64::sin()` (native FSIN instruction on ARM)
- **Triangle**: Piecewise linear (zero-cost)
- **Noise**: LFSR-based pitched sample-and-hold
- **FM**: 2-operator with envelope-modulated index

### Filter
2-pole Chamberlin SVF with stability clamping:
- `f` clamped to `2*q - 0.01` (prevents blowup at any cutoff)
- State variables clamped to ±4.0 (prevents runaway)
- NaN check with reset (safety net)

### Effects
- **Delay**: Stereo ping-pong (375ms L, 500ms R), damped cross-feedback at 40%
- **Reverb**: 4 comb filters (1116/1188/1277/1356 samples) + 2 allpass diffusers (556/441)

### Output
- Master gain scaling to ~35% of full range
- Transparent soft limiter: linear below 0.85, rational squash above
- 16-bit PCM at 44100Hz stereo

## File Structure

```
m8-synth/
├── Cargo.toml          — Rust crate config (cdylib, release optimized)
├── src/
│   └── lib.rs          — Complete synth engine + JNI exports
app/src/main/
├── java/com/m8/audio/
│   └── NativeSynth.kt  — Kotlin JNI declarations
├── jniLibs/
│   ├── arm64-v8a/
│   │   └── libm8_synth.so  — ARM64 native library (~300KB)
│   └── x86_64/
│       └── libm8_synth.so  — x86_64 for emulator (~330KB)
```

## Performance

At 44100Hz with 8 voices + filter + delay + reverb:
- **Teensy 4.1**: ARM Cortex-M7 @ 600MHz (bare metal C) — this is what we're emulating
- **Android phone**: ARM Cortex-A78 @ 2.8GHz (Rust native) — ~5-10x faster per core
- CPU usage: <5% of one core on a modern phone
