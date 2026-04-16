# Technology Stack — m8droid Milestone: Sound Quality + M8 Academy

**Project:** m8droid
**Researched:** 2026-04-15
**Scope:** Additive only — what to bolt onto the existing Kotlin/Rust/Compose/Python stack. Do NOT replace anything.

---

## Existing Stack (Locked — Do Not Re-research)

| Layer | Technology | Version |
|-------|-----------|---------|
| UI | Jetpack Compose (BOM) | 2024.12.01 |
| Navigation | AndroidX Navigation | 2.8.5 |
| State | AndroidX Lifecycle / ViewModel | 2.8.7 |
| Storage | AndroidX DataStore Preferences | 1.1.1 |
| Async | kotlinx-coroutines-android | 1.9.0 |
| Network | OkHttp + WebSocket | 4.12.0 |
| Audio output | Android AudioTrack (Java API) | minSdk 26 |
| DSP engine | Rust 2021 edition → libm8_synth.so via JNI | jni 0.21.1 |
| Build | Gradle 8.12 / AGP 8.7.3 / Kotlin 2.0.21 | — |
| Server | Python 3.x + websockets 12.0+ | — |

---

## Part 1: DSP / Sound Quality

### 1.1 Oscillator Fidelity

**Current state:** PolyBLEP saw and pulse, naive triangle, sine, LFSR noise, FM. Triangle is not band-limited (produces aliasing harmonics). No wavetable support. No macrosynth / M8 Wavsynth equivalent.

**Recommendation: Stay pure Rust, no external DSP crate for hot-path oscillators.**

Rationale: The existing code is already allocation-free and inline in the audio loop. Introducing fundsp (0.23.0) or dasp as a dependency brings graph-allocation overhead and a learning cliff for a hot path that is already well-structured. The specific techniques below can be ported as self-contained functions in under 300 lines total.

**Techniques to add (in priority order):**

| Technique | What it fixes | Complexity | Confidence |
|-----------|--------------|-----------|-----------|
| PolyBLAMP for triangle | Triangle wave has a ramp discontinuity in its derivative, causing harmonics. BLAMP (band-limited rAMP) corrects this the same way BLEP corrects step discontinuities. One additional correction function alongside `poly_blep`. | Low | HIGH — well-documented in DSP literature, drop-in addition |
| 2× oversampling for nonlinear paths | Filter self-oscillation and waveshaping produce foldover aliasing that PolyBLEP cannot suppress. Run voices in double-rate (88.2 kHz internally), then decimate with a half-band FIR before writing to the output buffer. Half-band FIR is efficient because ~50% coefficients are zero. 2× is sufficient for M8's 44.1 kHz output; 4× is only needed for heavy distortion. | Medium | HIGH |
| Band-limited wavetable for Wavsynth emulation | The M8's Wavsynth engine is an 8-bit wavetable with 9 base shapes. Implement as a pre-computed, band-limited wavetable set (one table per octave, computed at startup, stored as `static` once-cell). Lookup by phase with linear interpolation. This replaces the current triangle and adds a true wavetable mode. | Medium | HIGH |
| Macrosynth: port Mutable Instruments Plaits algorithms | The M8 Macrosynth is documented as based on Mutable Instruments Braids (48 synthesis modes). Braids/Plaits source is MIT-licensed C++ at `github.com/pichenettes/eurorack`. Port the subset used by M8 (virtual-analog, PWM, hard sync, formant, FM, and the noise/particle modes). Do NOT port all 48; start with the 8–10 most used. | High | MEDIUM — porting C++ DSP to Rust is mechanical but test coverage is essential |

**Reference material (do NOT blindly copy — use as ground truth for behavior):**
- `github.com/Dirtywave/M8HeadlessFirmware` — precompiled binaries only, but the changelog describes engine changes. Use the Python `m8_emulator.py` in the existing server/ as the behavioral reference for what parameters map to what sound.
- `github.com/pichenettes/eurorack` (Braids and Plaits directories) — MIT-licensed C++ reference for macrosynth algorithms.

**Do NOT use:**
- **fundsp 0.23.0** — rich graph-based DSP library, great for prototyping, but allocates at node-graph construction time and adds ~80 kB of Rust code. Not worth it for targeted improvements to an existing hand-rolled engine.
- **dasp** — sample-type conversion utilities only; no oscillator algorithms. Wrong tool.
- **oboe Rust crate (0.4.2)** — see §1.4 on dropout elimination; summary: not needed yet.

---

### 1.2 Filter Character

**Current state:** 2-pole TPT SVF (transposed direct form II), LP output only, clamped for stability. Stability clamping (`min(2*q - 0.01, f)`) limits resonance before self-oscillation. Resonance range is conservative.

**Recommendation: Replace stability clamp with a proper nonlinear SVF implementation.**

The M8 uses a filter character that is not a clean Butterworth — it has mild saturation at high resonance. The approach:

1. **Zero-delay feedback (ZDF) SVF** — replace the current two-integrator loop with a ZDF formulation (Vadim Zavalishin's "The Art of VA Filter Design", freely available). ZDF gives exact cutoff tracking, stable self-oscillation, and is only 4 multiply-adds more expensive per sample. Implement in Rust as a drop-in replacement for the current `svf_lo`/`svf_bd` state. Confidence: HIGH.

2. **Soft saturation on integrator outputs** — add a `tanh_cheap` (polynomial approximation) on each integrator output. This is the key to "analog warmth" at high resonance. A 3rd-order odd polynomial approximation of tanh (`x - x³/6`) is sufficient and runs without transcendentals. Confidence: HIGH — standard technique, Huovilainen (2004) paper documents this for ladder, same principle applies to SVF.

3. **Ladder filter (Moog-style) as an optional mode** — the M8 does not have a dedicated ladder filter, but adding it as an optional instrument-level filter type increases emulation range. Use the Huovilainen improved model (2006 paper). The `surgefilter-huovilainen` Rust crate (alpha, 0.2.12) wraps this but is pre-production quality; implement from the paper directly (the algorithm is ~30 lines of Rust). Confidence: MEDIUM — useful but not P0.

**Do NOT use:**
- **surgefilter-huovilainen 0.2.12-alpha.0** — alpha crate, non-production, bring the algorithm in directly from the paper.

---

### 1.3 Reverb and Delay Upgrade

**Current state:** Schroeder reverb (4 comb + 2 allpass, mono, hardcoded lengths). Ping-pong delay (correct structure, but hardcoded 375ms/500ms lengths, not tempo-sync'd).

**Recommendation: Replace Schroeder reverb with a Dattorro plate-reverb implementation.**

Rationale: The current Schroeder configuration has metallic coloration that is well-documented as a limitation of the algorithm. Dattorro's 1997 "Effect Design" plate reverb (allpass-loop topology) is the most commonly cited higher-quality free algorithm and is what many tracker/synth reverbs are modeled on. It produces a smoother, less-ringy tail. Implementation is ~80 lines of Rust. The exact delay-line lengths and coefficients are in Dattorro's published paper (JAES 1997) and widely referenced online. Confidence: HIGH.

**Delay improvements (lower priority):**
- Add tempo-sync'd delay time: the delay length should be derivable from the current BPM so the ping-pong sits on-grid.
- The existing damped cross-feedback is fine structurally; no rewrite needed.

**Chorus (new — not in current engine):**
The M8 has a chorus/vibrato FX command. Add a simple dual-voice chorus: two parallel delay lines (15–35 ms range) with sinusoidal LFO modulation on the read pointer (pitch-modulated delay). Standard "ensemble" model. No BBD emulation needed — the M8 is digital and uses a straightforward LFO-modulated delay. Confidence: HIGH — 40 lines of Rust.

**Do NOT use:**
- Any external reverb crate — they either wrap C libraries (cross-compilation complexity) or are insufficiently documented for licensing. Implement from Dattorro's published algorithm directly.

---

### 1.4 Dropout / Glitch Elimination

**Current state:** AudioTrack Java API, audio thread at `THREAD_PRIORITY_URGENT_AUDIO`, buffer of 735 samples (CHUNK = 735 ≈ 16.6 ms at 44.1 kHz). Dropouts from: (a) Mutex lock contention (`ENGINE: Mutex<Option<SynthEngine>>`) blocking the audio callback, (b) GC pressure from JNI array allocation in `generateChunk` each call (`env.new_byte_array`), (c) thread-unsafe `M8Song` mutation from UI thread during render.

**Recommendation: Four targeted fixes, no library changes required.**

| Fix | What | Confidence |
|----|------|-----------|
| Pre-allocate the JNI byte array | `generateChunk` calls `env.new_byte_array(CHUNK_BYTES)` on every call — this GC-allocates inside the audio callback. Pre-allocate a global JNI `jbyteArray` reference during `init()`, reuse it. Requires `GlobalRef`. | HIGH |
| Replace `Mutex` with a lock-free triple-buffer | The `ENGINE: Mutex<Option<SynthEngine>>` is locked by both the audio thread (render) and the UI thread (triggerRow, allNotesOff). Under contention this causes priority inversion. Use a lock-free triple-buffer (one buffer per role: produce / ready / consume). The `triple-buffer` Rust crate (0.3.0) provides this pattern. | HIGH |
| Increase AudioTrack buffer headroom | Current CHUNK = 735 samples. Use `AudioTrack.setBufferSizeInFrames()` to push the effective buffer to 1470–2940 (2×–4× CHUNK) at startup based on `AudioTrack.getMinBufferSize()`. This trades 16–33 ms of additional latency for near-zero dropout risk on congested devices. | HIGH |
| Fix M8Song cross-thread mutation | From CONCERNS.md: `M8Song` is mutated without locks. Wrap UI-thread writes in a command queue (a `crossbeam-channel` or a Kotlin `Channel<SynthCommand>`) that the audio thread drains at the top of each render call. | HIGH |

**Do NOT switch to Oboe (C++ via JNI):** The `oboe` Rust crate (0.4.2) provides Rust bindings for Google's Oboe library and gives AAudio access on API 27+. It would reduce latency on high-end devices. However: (1) the current AudioTrack with `PERFORMANCE_MODE_LOW_LATENCY` (available API 26+, matching minSdk) already gets close to Oboe latency, (2) adding a C++ dependency complicates the NDK build, (3) the dropout problem is not a latency problem — it is a mutex-contention + GC problem that the above fixes address without touching the audio API. Re-evaluate Oboe only if the fixes above don't eliminate dropouts.

**Rust crates to add:**

| Crate | Version | Purpose |
|-------|---------|---------|
| `triple-buffer` | 0.3.x | Lock-free state triple-buffer for audio/UI boundary |
| `crossbeam-channel` | 0.5.x | Command queue for note events from UI thread |

Both are `no_std`-compatible, allocation-free in the steady state, and compile cleanly for ARM64.

---

### 1.5 Testing — DSP Layer

**Current state:** Zero tests (see TESTING.md).

**Recommendation: Add Rust `#[cfg(test)]` golden-render tests using `insta`.**

The `insta` crate (mitsuhiko/insta) provides snapshot testing for Rust. For audio DSP, the companion `insta-fun` crate generates WAV + SVG snapshots from FunDSP audio units. However, since we are not using fundsp, use plain `insta` with a `Vec<f32>` round-trip: render N samples, serialize as hex or as a WAV blob, snapshot. On regression the snapshot diff shows exactly which sample changed.

Workflow:
1. `cargo test` renders a known note through each oscillator + filter combination.
2. First run: `cargo insta review` approves snapshots.
3. Subsequent PRs: any DSP regression surfaces immediately as a snapshot diff.

**Crates to add (Cargo.toml, dev-dependencies only):**

| Crate | Version | Purpose |
|-------|---------|---------|
| `insta` | 1.x | Snapshot assertion |
| `hound` | 3.5.x | WAV encode/decode for golden WAV files |

`hound` is well-maintained, zero-dependency WAV I/O used throughout the Rust audio ecosystem.

---

## Part 2: M8 Academy — In-App RPG

### 2.1 Framework Decision: Compose-Native, No Game Engine

**Recommendation: Build the Academy entirely in Jetpack Compose with a custom state machine. Do not introduce Korge, LibGDX, or any game engine.**

Rationale:

| Option | Verdict | Why |
|--------|---------|-----|
| **Pure Compose + ViewModel state machine** | **USE THIS** | Zero new dependencies. Compose `Canvas`, `AnimatedContent`, `LaunchedEffect`, `drawWithContent` cover all visual-novel and mini-game UI needs. The M8 screen is already a Compose-rendered 320×240 buffer — the Academy is the same pattern. State machine lives in a `AcademyViewModel`. |
| **Korge 6.x** | Avoid | Korge is a full multiplatform game engine. It ships its own rendering loop, its own Compose integration (korge-compose) is experimental/low priority per the project's own GitHub issues. Embedding Korge inside an existing Compose nav graph requires significant wiring and brings ~10 MB of runtime overhead. |
| **LibGDX** | Avoid | Desktop-era Java game framework. Android support exists but the integration with a Compose nav-graph is awkward (requires `SurfaceView` interop). No Kotlin-first API. |
| **Compose Multiplatform** | Not applicable | Project is Android-only. CMP adds complexity with no benefit here. |
| **Unity/Godot via WebView** | Avoid | Absurd overhead for a visual novel. |

**What Compose gives us for free:**

- `AnimatedContent` for scene/dialogue transitions (cross-fade, slide).
- `Canvas` composable for mini-game rendering (pattern-match grid, waveform visualizer, sample slicer) at 60fps via `withFrameNanos` loop in a coroutine.
- `rememberInfiniteTransition` for idle character animations (subtle sprite oscillation).
- `LaunchedEffect` for timed narrative beats (typewriter text reveal).
- Standard `Navigation` graph entry — the Academy is a NavGraph destination alongside the existing M8 screens.

**Confidence: HIGH** — multiple production games (Flappy Bird clones, puzzle games) have been shipped using Compose Canvas on Android. Visual novel complexity is lower than most action games.

---

### 2.2 Visual Novel Shell

**Recommendation: Custom Kotlin implementation using a sealed-class scene/dialogue DSL.**

Define the narrative as a Kotlin data structure:

```
sealed class AcademyNode
data class Dialogue(val character: Character, val text: String, val next: AcademyNode) : AcademyNode()
data class Choice(val prompt: String, val options: List<Pair<String, AcademyNode>>) : AcademyNode()
data class Quest(val id: String, val description: String, val validator: QuestValidator) : AcademyNode()
data class MiniGame(val type: MiniGameType, val next: AcademyNode) : AcademyNode()
object End : AcademyNode()
```

This is a tree of Kotlin objects — no DSL library needed. State is just `currentNode: AcademyNode` in `AcademyViewModel`.

**Do NOT reach for a VN framework** (Ren'Py, Twine-to-Android wrappers, etc.) — they are not Compose-aware and would require WebView embedding.

Character sprites: static `@DrawableRes` PNG assets rendered with `Image`. Parallax depth layers rendered with `Canvas.translate`. No sprite animation library needed for this fidelity.

---

### 2.3 Mini-Games

Mini-games are small Compose screens backed by their own `MiniGameViewModel`. The rendering loop for interactive mini-games:

```kotlin
LaunchedEffect(Unit) {
    while (true) {
        withFrameNanos { frameNanos ->
            viewModel.tick(frameNanos)
        }
    }
}
```

This runs on the main thread at display refresh rate without blocking the audio thread. Confirmed pattern from Android Developers documentation on game loops in Compose.

Mini-game categories and their Compose implementation:

| Mini-game | Implementation |
|-----------|---------------|
| Pattern-match step sequencer | `Canvas` grid, touch detection via `Modifier.pointerInput` |
| FX command recall flashcard | Simple `Card`/`AnimatedContent` flip |
| Sample slicer | `Canvas` waveform + draggable slice markers via `Modifier.draggable` |
| BPM tap-tempo | `Button` + coroutine timer |

No physics engine needed. No collision detection beyond simple rectangle hit-test.

---

### 2.4 Quest System — Integration with Live M8 State

The quest system hooks into `M8Emulator` state, not into the audio engine. Quests are validators that inspect `M8Song` / `M8Phrase` / `M8FxEngine` state:

```kotlin
interface QuestValidator {
    fun validate(song: M8Song): Boolean
}
```

Example: "Make a 4-step drum phrase with swing > 50%" — validator reads `phrase.steps` and `phrase.swing` directly from `M8Song`. The `AcademyViewModel` observes a `StateFlow<M8Song>` emitted by `M8ViewModel` on each sequencer tick.

This requires exposing a `songStateFlow: StateFlow<M8Song>` from `M8ViewModel`. No new library needed — pure Kotlin coroutines.

---

### 2.5 Persistence — RPG Progression

**Recommendation: Use existing AndroidX DataStore Preferences (already a dependency at 1.1.1). Do NOT add Room.**

Rationale: Academy progression data is simple key-value:
- Current chapter index (Int)
- Completed quest IDs (Set<String>)
- XP total (Int)
- Unlocked characters (Set<String>)

DataStore Preferences handles all of this natively with coroutine-based access. Room adds a SQLite schema, DAOs, and database migrations for data that is fundamentally flat. The CONCERNS.md already calls out zero-test infrastructure as the highest risk — adding Room to an untested codebase adds another migration-failure surface with no gain.

**The boundary rule:** If a future milestone adds quest history with timestamps, filtering by date, or relational data (e.g., per-quest attempt records), migrate to Room then. Not now.

---

### 2.6 Testing — Kotlin / Academy Layer

**Recommendation: Seed a minimal JUnit5 test suite alongside Academy development.**

Dependencies to add to `app/build.gradle.kts` (testImplementation scope):

| Library | Version | Purpose |
|---------|---------|---------|
| `junit5` (junit-jupiter) | 5.11.x | Modern JUnit with parameterized tests |
| `kotlinx-coroutines-test` | 1.9.0 (already in project) | TestScope + runTest for ViewModel coroutines |
| `androidx.test.ext:junit` | 1.2.x | Android JUnit integration |
| `kotlinx.fuzz` (JetBrains Research) | 0.1.x | Fuzz testing for M8sParser/M8iParser; requires JDK 8+ |

**kotlinx.fuzz** was announced by JetBrains Research in April 2025. It wraps Jazzer (JVM fuzzer) with a Kotlin-friendly API and Gradle plugin. It is the highest-leverage test to add because the parser concerns are P0 per CONCERNS.md. Confidence: MEDIUM — library is new (0.1.x), API may shift, but the investment is low (one fuzz target per parser).

For Rust DSP tests, use `insta` + `hound` as described in §1.5.

For the `AcademyViewModel` state machine, use `kotlinx-coroutines-test` `runTest` + `Turbine` (a `StateFlow` test library):

| Library | Version | Purpose |
|---------|---------|---------|
| `app.cash.turbine:turbine` | 1.2.x | StateFlow / SharedFlow test assertions |

Turbine is small, well-maintained, and used widely in Kotlin coroutine testing. Confidence: HIGH.

---

## Full Additive Dependency Summary

### Rust (Cargo.toml)

| Crate | Type | Version | Purpose | Priority |
|-------|------|---------|---------|---------|
| `triple-buffer` | dep | 0.3.x | Lock-free audio/UI state boundary | P0 |
| `crossbeam-channel` | dep | 0.5.x | Note event queue, UI→audio | P0 |
| `insta` | dev-dep | 1.x | DSP golden-render snapshot tests | P1 |
| `hound` | dev-dep | 3.5.x | WAV I/O for snapshot baselines | P1 |

### Kotlin (app/build.gradle.kts)

All existing Compose/DataStore/Coroutines dependencies are already present. New additions:

| Library | Scope | Version | Purpose | Priority |
|---------|-------|---------|---------|---------|
| `junit-jupiter` | testImpl | 5.11.x | JUnit5 for Academy VM tests | P0 |
| `app.cash.turbine:turbine` | testImpl | 1.2.x | StateFlow test assertions | P0 |
| `kotlinx.fuzz` + Gradle plugin | testImpl | 0.1.x | Parser fuzz testing | P1 |

### What Is Explicitly NOT Added

| Rejected Dependency | Reason |
|--------------------|--------|
| fundsp | Rich DSP graph library, allocation overhead, not needed for targeted improvements |
| dasp | Sample-type utilities only, no relevant oscillator/filter algorithms |
| oboe Rust crate | Dropout root cause is Mutex contention + JNI GC, not audio API latency |
| surgefilter-huovilainen | Alpha crate; implement Huovilainen algorithm directly from paper |
| Korge | Full game engine, experimental Compose interop, 10 MB overhead |
| LibGDX | Java-first, awkward Compose interop, no benefit for visual novel |
| Room | Relational DB overkill for flat key-value RPG progress; DataStore already present |
| SharedPreferences | DataStore already present and is the modern replacement |
| Any VN framework | Not Compose-aware; would require WebView embedding |

---

## Architecture Notes for Roadmap

**DSP work is self-contained in `m8-synth/src/lib.rs`** — all improvements are additive to the existing Rust module. No Kotlin changes needed for oscillator/filter work except updating JNI surface if new parameters are exposed.

**Academy is a new top-level nav destination** — add `AcademyScreen.kt`, `AcademyViewModel.kt`, `AcademyRepository.kt` (wrapping DataStore). Insert a navigation entry in the existing nav graph. No existing screens are modified.

**The critical path risk:** The macrosynth port from Mutable Instruments C++ is the highest-complexity DSP item and should be time-boxed. If it slips, the other oscillator improvements (PolyBLAMP triangle, ZDF SVF, Dattorro reverb, chorus) deliver substantial sound-quality gains independently.

**Audio thread safety** must be solved before any DSP quality work — writing a better oscillator into a racing data structure will produce new artifacts. Mutex→triple-buffer refactor is the first Rust commit.

---

## Sources

- Dirtywave M8 HeadlessFirmware GitHub: https://github.com/Dirtywave/M8HeadlessFirmware
- Mutable Instruments Eurorack source (Braids/Plaits, MIT): https://github.com/pichenettes/eurorack
- Mutable Instruments Braids open source page: https://pichenettes.github.io/mutable-instruments-documentation/modules/braids/open_source/
- fundsp crate (0.23.0): https://crates.io/crates/fundsp
- triple-buffer crate: https://crates.io/crates/triple-buffer
- crossbeam-channel crate: https://crates.io/crates/crossbeam-channel
- insta snapshot testing: https://insta.rs/
- insta-fun (WAV/SVG snapshots for audio): https://crates.io/crates/insta-fun
- hound WAV crate: https://crates.io/crates/hound
- kotlinx.fuzz announcement (JetBrains Research, April 2025): https://blog.jetbrains.com/research/2025/04/kotlinxfuzz-kotlin-fuzzing/
- Turbine StateFlow test library: https://github.com/cashapp/turbine
- Vadim Zavalishin, "The Art of VA Filter Design" (ZDF SVF): freely available PDF
- Dattorro, "Effect Design, Part 1: Reverberator and Other Filters", JAES 1997
- Huovilainen, "Non-Linear Digital Implementation of the Moog Ladder Filter", DAFx 2004
- ADAA reference (Chowdhury): https://github.com/jatinchowdhury18/ADAA
- Oboe AudioTrack low-latency (AudioTrack.PERFORMANCE_MODE_LOW_LATENCY API 26): https://developer.android.com/games/sdk/oboe/low-latency-audio
- Korge game engine: https://korge.org/
- Jetpack Compose Canvas game loop pattern: https://developer.android.com/games/develop/gameloops
- AndroidX DataStore vs Room: https://developer.android.com/topic/libraries/architecture/datastore

*Confidence summary: DSP techniques HIGH (well-established literature). Macrosynth port MEDIUM (porting effort unquantified). Academy Compose approach HIGH (proven pattern). kotlinx.fuzz MEDIUM (new library, 0.1.x). Oboe rejection HIGH (root cause analysis confirms mutex/GC, not API).*
