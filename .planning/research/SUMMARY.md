# Research Summary — m8droid Milestone: Sound Quality + M8 Academy

**Synthesized:** 2026-04-16
**Branch:** main-rpg
**Sources:** STACK.md · FEATURES.md · ARCHITECTURE.md · PITFALLS.md · PROJECT.md

---

## Executive Summary

m8droid already has a working Android Kotlin/Compose app backed by a Rust DSP engine and a Python remote-mode server. This milestone bolts two independent but entangled workstreams onto that foundation: tightening audio quality in the existing Rust synth, and adding an in-app "M8 Academy" RPG/edutainment mode. Both workstreams share a single blocker — the codebase has zero tests and a thread-safety gap in sequencer state — meaning any sound improvement that ships before that gap is closed risks silent regressions, and any Academy integration that touches live emulator state risks audio dropouts.

The sound-quality work is additive Rust DSP: no external crates on the hot path, targeted algorithm improvements ordered by perceptual salience. The highest-impact items in priority order are: dropout/glitch elimination (concurrency fix first), delay feedback saturation (one-liner, immediately audible), SVF tanh saturation for filter warmth, oscillator oversampling for aliasing at high notes, Dattorro plate reverb replacing the current Schroeder reverb, and finally the Mutable Instruments Plaits macrosynth port. The Academy is built entirely in Jetpack Compose with no game engine, using a sealed-class quest DSL, a separate `AcademyViewModel`, and DataStore Preferences for progression persistence. No Room, no Korge, no Ren'Py.

The critical architectural insight is that Academy must observe emulator state through a snapshot layer (`EmulatorEventRepository` feeding `SharedFlow<EmulatorSnapshot>`) and never touch the audio thread or the mutable `M8Song` directly. This pattern is already defined by how `M8ViewModel` drives the 30fps display buffer — the Academy piggybacks on that tick at near-zero overhead. Everything depends on Phase 1 infrastructure: JNI hardening, golden-render test harness, and the snapshot/repository layer. Without those three things, both workstreams are flying blind.

---

## Key Findings

### From STACK.md

| Technology Decision | Rationale |
|--------------------|-----------|
| Pure Rust DSP, no fundsp/dasp | Hot-path is already allocation-free; external graph libs add overhead with no benefit for targeted improvements |
| `triple-buffer` 0.3.x (Rust) | Lock-free replacement for `ENGINE: Mutex`; eliminates audio-thread priority inversion |
| `crossbeam-channel` 0.5.x (Rust) | Note event command queue from UI thread to audio thread; replaces direct M8Song mutation |
| `insta` + `hound` (Rust dev-deps) | Golden-render snapshot tests; first line of defense against silent DSP regressions |
| Compose-native Academy, no Korge/LibGDX | Zero new dependencies; Compose Canvas + AnimatedContent covers all visual-novel and mini-game needs |
| DataStore Preferences, not Room | Academy progression is flat key-value; DataStore already in the project; Room adds migration complexity for no gain at MVP |
| JUnit5 + Turbine + kotlinx.fuzz (test-only) | JUnit5 for Academy VM tests; Turbine for StateFlow assertions; kotlinx.fuzz for parser fuzzing |

**Version-critical:** Existing BOM (Compose 2024.12.01), AGP 8.7.3, Kotlin 2.0.21, coroutines 1.9.0 — all new dependencies must stay compatible.

**Explicitly rejected:** fundsp, dasp, oboe Rust crate, surgefilter-huovilainen (alpha), Korge, LibGDX, SharedPreferences, any VN framework.

---

### From FEATURES.md

**Sound quality — priority order (perceptual salience + effort/impact):**

| Priority | Improvement | Why |
|----------|------------|-----|
| P0 | Dropout/glitch elimination | Concurrency root cause; tuned DSP that clicks is worse than off-pitch DSP that is clean |
| P1 | Delay feedback saturation (`tanh` on feedback path) | One-liner in Rust; immediately audible "tape warmth"; highest impact-to-effort ratio |
| P2 | SVF `tanh_cheap` on integrator outputs | "Analog warmth" at high resonance; key perceptual difference from hardware M8 |
| P3 | 2× oscillator oversampling (88.2 kHz internal → decimate) | Eliminates aliasing foldover in nonlinear paths; compute-feasible on mid-range Android |
| P4 | PolyBLAMP triangle correction | Triangle ramp discontinuity aliases; drop-in addition alongside `poly_blep` |
| P5 | Dattorro plate reverb (replace Schroeder) | Schroeder has metallic coloration; Dattorro produces smoother tail (~80 lines Rust) |
| P6 | Macrosynth: Mutable Instruments Plaits port | 8–10 most-used Braids modes; MIT-licensed C++ reference; high complexity, time-box |

**Academy — table stakes (must have for v1):**

- Persistent progress (save on every quest completion and on app backgrounding)
- Clear "what's next" signal on Academy home screen
- Abandon/resume quest without losing in-progress state
- Chapter map with completion indicators visible from day one
- Inline glossary tooltips (M8 vocabulary is alien to DAW users)
- Completion celebration (haptic + XP tick animation)
- Graceful failure feedback with condition diff ("Swing is 42% — need > 50%")
- Skip/hint option after N failed attempts
- 30-second onboarding max to first quest
- Non-destructive entry (must not reset emulator state)

**Academy — differentiators (what makes it special):**

- Quest conditions verified in the real M8 UI, not a sandbox — muscle memory transfers
- Visual-novel characters, one per chapter/subsystem (Beatrix teaches drums, etc.)
- Contextual command reference panel (filtered to current chapter) visible during quests
- Post-quest playback of the user's phrase so they hear the result
- Between-chapter mini-games drilling FX command recall, pattern matching, sample slicing
- XP gates chapter unlock; cosmetic unlocks have low cost / high perceived value

**Academy — anti-features (explicitly avoid):**

- Punitive streak loss (use cumulative XP, never reset)
- Hard chapter gating (show full map day one; gate quests within chapters, not chapter access)
- Overly prescriptive quest text ("Set X to Y" instead of "Make something that feels urgent")
- Binary pass/fail with no partial credit
- No skip on dialogue (tap-to-skip persists per-exchange)
- Rewarding time-on-screen rather than mastery demonstration

**Deferred to post-MVP:** Mini-games (chapters 2–6), non-linear bonus challenges, adaptive hint threshold, shimmer reverb, macrosynth per-shape calibration.

---

### From ARCHITECTURE.md

**Core decision: Academy as a peer mode, not a parasite.**

Replace `dawMode: Boolean` in `M8App` with `AppMode` enum: `{ M8, DAW, ACADEMY }`. Academy runs as a top-level nav destination; the audio thread never stops; emulator state is never touched by Academy code.

**Component map (new package `com.m8droid.academy`):**

| Component | Responsibility |
|-----------|---------------|
| `AppMode` enum | Replaces `dawMode: Boolean`; wired in `M8App` + `MainActivity` |
| `AcademyViewModel` | Separate AndroidViewModel; owns quest state machine, XP, chapter progression |
| `EmulatorEventRepository` | `SharedFlow<EmulatorSnapshot>` — the only bridge between emulator and Academy |
| `EmulatorSnapshot` | Plain data class; copied from M8ViewModel render tick (~10 fields) |
| `AcademyRepository` | DataStore-backed; holds `AcademyProgress` (chapter, questIndex, XP, completedIds) |
| `QuestEngine` | Pure Kotlin, no Android imports — testable in plain JVM |
| `QuestCondition` | Sealed interface; evaluated against `EmulatorSnapshot` only |
| `MiniGameEngine` | Coroutine-scoped to AcademyViewModel; zero access to audio/emulator classes |
| `AcademyShell` | Top-level Compose surface for Academy mode |
| `QuestOverlay` | Thin overlay on M8 mode during active quest (read-only, observes AcademyViewModel) |

**Data flow:**

```
Audio thread → triggerCurrentRow() → NativeSynth JNI
                                      ↓
M8ViewModel.emulatorRenderJob (30fps) → renderFrame()
    → copy fields to EmulatorSnapshot
    → EmulatorEventRepository.emit(snapshot)       ← SharedFlow(replay=1)
         ↓
AcademyViewModel.collectLatest { snapshot →
    questEngine.evaluate(activeQuest, snapshot)    ← pure Kotlin
}
```

**DSP safe refactor path (order enforced):**

1. JNI `.unwrap()` hardening (replace with explicit error handling returning null to Kotlin)
2. Expose `SynthEngine` as a public pure-Rust API alongside JNI exports (enables tests without JVM)
3. Seed golden-render tests (`m8-synth/tests/golden.rs` using `insta`)
4. Extract DSP tuning values into `DspConfig` struct for A/B comparison in tests
5. Apply DSP improvements (now regression-protected)

**Dependency rule (enforced in code review):**
- `emulator/` → nothing in `academy/`
- `academy/` → `emulator/` read-only via ViewModel flows only
- `QuestEngine` → no imports from `com.m8droid.emulator` or `com.m8droid.audio`

---

### From PITFALLS.md

**Top 5 pitfalls by severity and cross-stream impact:**

| # | Pitfall | Phase | Prevention |
|---|---------|-------|-----------|
| 1 | No golden tests → silent DSP regressions (P7) | Pre-work | Block all DSP commits behind `cargo test`; seed 3 golden renders before touching oscillator code |
| 2 | Audio thread lifecycle bugs from Academy mode switching (P17) | Academy integration | Audio render loop is a closed system; snapshot post happens after chunk write; PR audit checklist |
| 3 | Quest detection false negatives from unlocked M8Song reads (P10) | Academy quest engine | Evaluate `EmulatorSnapshot` only, never live `M8Song`; snapshot is copied under render-tick timing |
| 4 | NaN explosion / filter stability under resonance redesign (P2) | Sound quality | `debug_assert!(f <= 2.0 * q)` in Rust debug builds; derive stability bound analytically |
| 5 | Context loss on onPause/onResume during active quest (P15) | Academy lifecycle | Each quest has `setupEmulatorState()` for resume; minimal context snapshot stored in DataStore |

**Additional pitfalls addressed in design:**

- Denormal CPU spikes in reverb/delay during sparse arrangements (P4) — add `abs() < 1e-15 → 0.0` flush guard before Dattorro implementation
- Zipper noise on live parameter changes (P5) — per-voice `cutoff_target` with one-pole LP smoothing; required before Academy ships
- Boring chore gamification (P8) — quest text framed as musical outcomes; quest guidelines reviewed before content authored
- Overly linear gating (P9) — full chapter map visible day one; gate quests within chapters, not chapter access
- Input ownership collision (P14) — emulator key handlers paused (not removed) when Academy active
- DataStore Preferences with JSON strings → forced migration (P18) — typed DataStore Proto schema from day one

---

## Implications for Roadmap

Research strongly implies a 4-phase structure. Phase 1 is a hard gate for everything else.

### Phase 1 — Infrastructure (blocks both workstreams)

**Rationale:** Zero tests + unlocked M8Song reads + JNI `.unwrap()` panics are three independent ways to silently corrupt results on any subsequent work. None of the DSP improvements or Academy integration can be validated without this foundation.

**Delivers:** Test-guarded Rust DSP surface, atomic snapshot bridge from emulator to Academy layer, DataStore progress persistence.

**Work items:**
- JNI `.unwrap()` → explicit error handling (return null to Kotlin)
- Expose `SynthEngine` as pub Rust API (no JNI required for tests)
- Seed `m8-synth/tests/golden.rs` with 3 golden renders (`insta` + `hound`)
- `EmulatorSnapshot` data class + `EmulatorEventRepository` SharedFlow
- Hook `EmulatorEventRepository.emit()` into `M8ViewModel` render tick (one line)
- `AcademyProgress` data class + `AcademyRepository` (DataStore)
- Add `triple-buffer` + `crossbeam-channel` to Cargo.toml (dropout root-cause fix starts here)

**Pitfalls addressed:** P7 (no tests), P10 (false negatives from live M8Song reads).

### Phase 2A — Sound Quality Core (parallel with 2B)

**Rationale:** Golden tests exist; DSP changes are now regression-safe. Priority follows perceptual salience: fix dropout first (architectural), then cheapest-highest-impact improvements, then medium-effort filter/oscillator work, then high-complexity macrosynth.

**Delivers:** Dropout-free audio on mid-range hardware; warmer delay and filter character; reduced aliasing on high notes; smoother reverb tail.

**Work items (ordered):**
1. Mutex → triple-buffer refactor; M8Song UI-thread write → `crossbeam-channel` command queue
2. Pre-allocate JNI byte array (eliminate per-chunk GC allocation)
3. AudioTrack buffer headroom increase (1470–2940 frames via `setBufferSizeInFrames`)
4. `tanh` soft saturation on delay feedback path (one-liner)
5. ZDF SVF with `tanh_cheap` on integrator outputs (filter warmth)
6. PolyBLAMP triangle correction
7. 2× oversampling for nonlinear paths (half-band FIR decimation)
8. Dattorro plate reverb replacing Schroeder (+ denormal flush guards)
9. Per-voice `cutoff_target` one-pole smoothing (zipper fix — required before Academy ships)

**Defer:** Macrosynth Plaits port (separate track, time-boxed, can slip without blocking Academy).

**Pitfalls addressed:** P1 (aliasing regression), P2 (NaN/stability), P4 (denormals), P5 (zipper), P6 (buffer underruns).

### Phase 2B — Academy Quest Engine + ViewModel (parallel with 2A)

**Rationale:** `EmulatorEventRepository` is ready from Phase 1. Quest engine is pure Kotlin with no Android deps — fully unit-testable. VM and data layer can be built before any UI exists.

**Delivers:** Working quest state machine; Chapter 1 (Drums) quest catalog; XP/chapter persistence; fully unit-tested quest conditions.

**Work items:**
- `AppMode` enum replacing `dawMode: Boolean` in `M8App` + `MainActivity`
- `QuestCondition` sealed interface (evaluated against `EmulatorSnapshot`)
- `QuestEngine` pure Kotlin (all-conditions-met → fire completion event)
- `QuestCatalog` — Chapter 1 quests (4-step drum phrase, swing > 50%, instrument category)
- `AcademyViewModel` state machine: idle → quest_active → quest_complete → narrative
- Quest-writing guidelines (outcome-framed, not prescriptive) before any content authored
- Schema decision: DataStore Proto vs DataStore Preferences for `AcademyProgress` — week 1

**Pitfalls addressed:** P8 (chore gamification), P10 (false negatives), P11 (false positives), P18 (migration-forcing schema).

### Phase 3 — Academy UI Shell

**Rationale:** Quest engine and VM working. UI is a thin layer over proven logic. Input ownership model must be resolved before the first screen ships.

**Delivers:** Full Academy mode visible in app; Chapter 1 playable end-to-end; QuestOverlay on M8 screen during active quest.

**Work items:**
- Input ownership model: emulator key handlers paused (not removed) when Academy active
- `AcademyShell` Compose: chapter map, narrative view, quest briefing
- `QuestOverlay`: thin Compose layer on M8 mode showing active quest + completion indicator
- Visual-novel shell: static character PNG + `AnimatedContent` dialogue with tap-to-skip
- XP feedback (haptic + particle burst + XP counter animation)
- Graceful failure feedback: condition diff displayed inline
- Skip/hint option (fixed attempt threshold for MVP, not adaptive)
- 30-second onboarding: 3-screen intro max, no account required
- Persist quest context snapshot in DataStore for resume-after-onPause

**Pitfalls addressed:** P9 (linear gating), P12 (Academy pulls away from free play), P13 (streaks), P14 (input collision), P15 (context loss on resume), P19 (engine over-investment).

### Phase 4 — Mini-Games + Full Chapter Content

**Rationale:** Core loop proven. Mini-games are high complexity, not on the critical path. Chapters 2–6 follow the Chapter 1 template.

**Delivers:** Between-chapter mini-games; full 6-chapter arc; all character dialogue scripts.

**Work items:**
- `MiniGameEngine` interface + coroutine harness (scoped to `AcademyViewModel.viewModelScope`)
- Pattern-match step sequencer mini-game (Canvas grid + `Modifier.pointerInput`)
- FX command recall flashcard mini-game (`AnimatedContent` card flip)
- Sample slicer mini-game (Canvas waveform + draggable markers)
- BPM tap-tempo mini-game (Button + coroutine timer)
- Quest catalog for Chapters 2–6 (synths, sampling, FX, song structure, Final Jam)
- Character dialogue scripts for all 6 chapters
- Macrosynth Plaits port (if not completed in Phase 2A — time-boxed)
- Optional: non-linear bonus challenges; "free explore" interstitials in quest flow

---

## Confidence Assessment

| Area | Confidence | Basis |
|------|------------|-------|
| Dropout root cause (Mutex + JNI GC) | HIGH | Direct source analysis of `M8ViewModel.kt` and `lib.rs`; corroborated by CONCERNS.md |
| DSP improvement techniques | HIGH | Zavalishin, Dattorro, Huovilainen literature; KVR community consensus |
| Academy Compose architecture | HIGH | Derived from live codebase; Compose Canvas game loop is documented Android pattern |
| EmulatorSnapshot bridge design | HIGH | Derived from live source of M8ViewModel 30fps render tick; no novel architecture required |
| DataStore sufficiency for MVP progression | HIGH | Flat key-value shape confirmed; Room escalation path defined |
| Macrosynth Plaits port effort | MEDIUM | Algorithm documented and MIT-licensed; Rust port complexity unquantified; time-box recommended |
| Quest content quality (chapters 2–6) | MEDIUM | Content not yet authored; guidelines defined but not user-validated |
| `kotlinx.fuzz` stability | MEDIUM | New library (0.1.x, April 2025); low investment risk |
| Shimmer reverb priority | MEDIUM | Exists in M8 firmware; exact implementation not public; deprioritized vs. Dattorro plate |

**Overall: HIGH** — both workstreams are well-defined with low architectural uncertainty. Primary risk is scope (macrosynth port, chapter 2–6 content), not technical approach.

---

## Gaps to Address During Planning

1. **DataStore Preferences vs. DataStore Proto schema decision** — must be made in week 1 before any Academy data is written to disk.
2. **Quest-writing guidelines** — no content drafted yet.
3. **Macrosynth Plaits port scope** — the 8–10 "most used" Braids/Plaits modes are not yet enumerated.
4. **Mid-range device baseline** — no specific test device or dropout-acceptance threshold defined.
5. **Chapter 1 narrative content** — character name, personality brief, and 3 opening dialogue exchanges needed for Phase 3 validation.

---

## Additive Dependencies Summary

### Rust (Cargo.toml)

| Crate | Type | Version | Purpose | Phase |
|-------|------|---------|---------|-------|
| `triple-buffer` | dep | 0.3.x | Lock-free audio/UI boundary | 1 |
| `crossbeam-channel` | dep | 0.5.x | Note event queue UI→audio | 1 |
| `insta` | dev-dep | 1.x | DSP golden-render snapshot tests | 1 |
| `hound` | dev-dep | 3.5.x | WAV I/O for golden test fixtures | 1 |

### Kotlin (app/build.gradle.kts, test scope only)

| Library | Version | Purpose | Phase |
|---------|---------|---------|-------|
| `junit-jupiter` | 5.11.x | JUnit5 for Academy VM tests | 1 |
| `app.cash.turbine:turbine` | 1.2.x | StateFlow / SharedFlow test assertions | 1 |
| `kotlinx.fuzz` + Gradle plugin | 0.1.x | Parser fuzz testing | 1 |

No new production Kotlin dependencies. No game engine. No database beyond existing DataStore.
