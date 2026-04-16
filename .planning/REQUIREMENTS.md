# Requirements: m8droid — Sound Quality + M8 Academy

**Defined:** 2026-04-16
**Core Value:** A polished way to learn and play the M8 on Android — the sound feels right, and a built-in RPG makes learning the tracker actually fun.
**Branch:** main-rpg

## v1 Requirements

All items below are in scope for this milestone.

### Infrastructure

Foundations that unblock both workstreams. Phase-1 gate for everything else.

- [ ] **INFRA-01**: JNI `.unwrap()` calls in Rust synth replaced with explicit error handling that returns null/error codes to Kotlin instead of aborting the process
- [ ] **INFRA-02**: `SynthEngine` exposed as a public pure-Rust API alongside existing JNI exports so DSP tests can run without the JVM
- [ ] **INFRA-03**: Rust golden-render test harness (`m8-synth/tests/golden.rs`) with at least three seed fixtures using `insta` and `hound`
- [ ] **INFRA-04**: `EmulatorSnapshot` data class and `EmulatorEventRepository` emitting a `SharedFlow<EmulatorSnapshot>` are defined and unit-tested
- [ ] **INFRA-05**: `EmulatorEventRepository.emit()` is wired into `M8ViewModel` 30fps render tick without adding measurable overhead
- [ ] **INFRA-06**: `AcademyProgress` and `AcademyRepository` persist Academy state via a typed DataStore Proto schema (not Preferences + JSON strings)
- [ ] **INFRA-07**: Kotlin test tooling (JUnit5 + Turbine) added to `app/build.gradle.kts`
- [ ] **INFRA-08**: First Academy ViewModel unit test runs green in CI-equivalent `./gradlew test`

### Sound Quality

Improvements to the existing Rust synth. Priority order = perceptual salience.

- [ ] **SND-01**: `ENGINE` global Mutex replaced with `triple-buffer` so the audio thread never blocks on UI writes
- [ ] **SND-02**: UI→audio note events routed through a `crossbeam-channel` command queue instead of direct `M8Song` mutation from the UI thread
- [ ] **SND-03**: JNI byte array for audio chunks is pre-allocated once, eliminating per-chunk GC allocation in the render loop
- [ ] **SND-04**: `AudioTrack` buffer headroom is increased via `setBufferSizeInFrames` (1470–2940 frames) to absorb scheduler jitter on mid-range devices
- [ ] **SND-05**: `tanh` soft saturation applied on the delay feedback path for tape-warmth character
- [ ] **SND-06**: Filter upgraded to a ZDF (zero-delay feedback) SVF with `tanh_cheap` on integrator outputs
- [ ] **SND-07**: PolyBLAMP correction added for band-limited triangle waves
- [ ] **SND-08**: 2× oversampling on nonlinear DSP paths with a half-band FIR decimation filter
- [ ] **SND-09**: Dattorro plate reverb replaces current Schroeder reverb, with denormal flush guards (`abs() < 1e-15 → 0.0`)
- [ ] **SND-10**: Per-voice `cutoff_target` one-pole smoothing eliminates zipper noise on live parameter changes
- [ ] **SND-11**: Mutable Instruments Plaits macrosynth port covering the 8–10 most-used Braids/Plaits modes
- [ ] **SND-12**: `AudioTrack.getUnderrunCount() == 0` over a 2-minute stress playback on a declared mid-range Android baseline device

### Academy — Core

Engine, state, top-nav integration, UX shell. All items must work for chapter content to be worth writing.

- [ ] **ACAD-01**: `AppMode` enum (`{M8, DAW, ACADEMY}`) replaces the existing `dawMode: Boolean` in `M8App` and is wired through `MainActivity`
- [ ] **ACAD-02**: Top-navigation bar shows a game icon next to settings, help, and download that enters Academy mode when tapped
- [ ] **ACAD-03**: Entering Academy does not reset emulator state, stop the audio thread, or interrupt current playback
- [ ] **ACAD-04**: While Academy mode is active, emulator key handlers are paused (not removed) and restored on return to M8 mode
- [ ] **ACAD-05**: `QuestCondition` sealed interface evaluated only against `EmulatorSnapshot` — never against live `M8Song` state
- [ ] **ACAD-06**: `QuestEngine` is pure Kotlin with zero imports from `com.m8droid.emulator` or `com.m8droid.audio` and is covered by unit tests
- [ ] **ACAD-07**: `AcademyViewModel` implements the state machine `idle → quest_active → quest_complete → narrative`
- [ ] **ACAD-08**: Academy progress (chapter, quest index, XP, completed quest IDs) survives process death and app restarts
- [ ] **ACAD-09**: Academy progress and active-quest context survive `onPause` / `onResume` mid-quest
- [ ] **ACAD-10**: New-user onboarding reaches the first playable quest in ≤30 seconds and ≤3 screens without requiring an account
- [ ] **ACAD-11**: Chapter map is visible from day one with completion indicators for all chapters
- [ ] **ACAD-12**: Inline glossary tooltip system explains M8 vocabulary at point of use
- [ ] **ACAD-13**: Quest completion triggers a haptic + XP tick animation celebration
- [ ] **ACAD-14**: Failed quest attempts show a condition-diff ("Swing is 42% — need > 50%") rather than binary pass/fail
- [ ] **ACAD-15**: Skip/hint option becomes available after a fixed number of failed attempts on a quest
- [ ] **ACAD-16**: `QuestOverlay` is visible on the M8 mode screen during an active quest, showing active conditions and completion indicator
- [ ] **ACAD-17**: Contextual command reference panel filtered to the current chapter is accessible during quests
- [ ] **ACAD-18**: Post-quest playback lets the user hear the phrase/pattern they produced
- [ ] **ACAD-19**: Dialogue supports tap-to-skip with skip state persisted per-exchange
- [ ] **ACAD-20**: User can exit Academy mid-quest without losing quest progress or emulator state

### Academy — Content

Full six-chapter arc with mini-games. Scope matches the "gamify the whole experience" intent.

- [ ] **CONT-01**: Chapter 1 — Drums — playable end-to-end with at least four quests
- [ ] **CONT-02**: Chapter 1 character dialogue authored and shippable
- [ ] **CONT-03**: Quest-writing guidelines document (outcome-framed quests, layered conditions, partial-credit model) committed to the repo
- [ ] **CONT-04**: Chapter 2 — Synths — quests authored, dialogue scripted, playable end-to-end
- [ ] **CONT-05**: Chapter 3 — Sampling — quests authored, dialogue scripted, playable end-to-end
- [ ] **CONT-06**: Chapter 4 — FX — quests authored, dialogue scripted, playable end-to-end
- [ ] **CONT-07**: Chapter 5 — Song Structure — quests authored, dialogue scripted, playable end-to-end
- [ ] **CONT-08**: Chapter 6 — Final Jam — capstone quests authored, dialogue scripted, playable end-to-end
- [ ] **CONT-09**: Pattern-match step sequencer mini-game (Canvas grid + pointer input)
- [ ] **CONT-10**: FX command recall flashcard mini-game (`AnimatedContent` card flip)
- [ ] **CONT-11**: Sample slicer mini-game (Canvas waveform + draggable markers)
- [ ] **CONT-12**: BPM tap-tempo mini-game (button + coroutine timer)

## v2 Requirements

Not in this milestone, but acknowledged as likely future work.

### Sound Quality

- **SND-V2-01**: Shimmer reverb mode
- **SND-V2-02**: Per-shape Macrosynth A/B calibration against real M8 hardware
- **SND-V2-03**: Additional filter modes (Moog ladder, comb)

### Academy

- **ACAD-V2-01**: Adaptive hint threshold based on player struggle
- **ACAD-V2-02**: Non-linear bonus challenges
- **ACAD-V2-03**: "Free explore" interstitials between chapters
- **ACAD-V2-04**: Cosmetic unlocks (character color variants, sticker pack)

### Testing

- **TEST-V2-01**: `kotlinx.fuzz` fuzz targets for `M8sParser` and `M8iParser`
- **TEST-V2-02**: Integration tests covering remote mode (`server/bridge.py`) end-to-end

## Out of Scope

Explicit exclusions to prevent scope creep.

| Feature | Reason |
|---------|--------|
| Teensy ARM emulation of real M8 headless firmware | Existing Rust synth works; porting Teensy firmware is months of work with no clear win over tuning existing DSP |
| Community track upload / share backend | User clarified existing `browse/` download path is sufficient; no user-uploaded content in this milestone |
| Accounts, social, likes, comments, follow | No user-to-user interaction in this milestone |
| New tutorial from scratch | Existing `tutorial/` stays as the classic opt-in path; Academy is the richer optional path |
| Song export / save back to `.m8s` | Real gap (CONCERNS.md) but outside this milestone |
| TLS / auth for Python bridge | Security hardening deferred; not blocking this milestone |
| Korge, LibGDX, Compose-Multiplatform game engine | Compose-native is sufficient; adding a game engine is unjustified complexity |
| Room database | DataStore Proto is sufficient for Academy progress shape |
| fundsp / dasp DSP graph libraries | Hot path is already allocation-free; graph libs add overhead with no benefit |
| Replacing the existing Python server/ stack | Works as-is; out of milestone scope |

## Traceability

Populated by roadmap creation. All v1 requirements will map to exactly one phase.

| Requirement | Phase | Status |
|-------------|-------|--------|
| INFRA-01 | — | Pending |
| INFRA-02 | — | Pending |
| INFRA-03 | — | Pending |
| INFRA-04 | — | Pending |
| INFRA-05 | — | Pending |
| INFRA-06 | — | Pending |
| INFRA-07 | — | Pending |
| INFRA-08 | — | Pending |
| SND-01 | — | Pending |
| SND-02 | — | Pending |
| SND-03 | — | Pending |
| SND-04 | — | Pending |
| SND-05 | — | Pending |
| SND-06 | — | Pending |
| SND-07 | — | Pending |
| SND-08 | — | Pending |
| SND-09 | — | Pending |
| SND-10 | — | Pending |
| SND-11 | — | Pending |
| SND-12 | — | Pending |
| ACAD-01 | — | Pending |
| ACAD-02 | — | Pending |
| ACAD-03 | — | Pending |
| ACAD-04 | — | Pending |
| ACAD-05 | — | Pending |
| ACAD-06 | — | Pending |
| ACAD-07 | — | Pending |
| ACAD-08 | — | Pending |
| ACAD-09 | — | Pending |
| ACAD-10 | — | Pending |
| ACAD-11 | — | Pending |
| ACAD-12 | — | Pending |
| ACAD-13 | — | Pending |
| ACAD-14 | — | Pending |
| ACAD-15 | — | Pending |
| ACAD-16 | — | Pending |
| ACAD-17 | — | Pending |
| ACAD-18 | — | Pending |
| ACAD-19 | — | Pending |
| ACAD-20 | — | Pending |
| CONT-01 | — | Pending |
| CONT-02 | — | Pending |
| CONT-03 | — | Pending |
| CONT-04 | — | Pending |
| CONT-05 | — | Pending |
| CONT-06 | — | Pending |
| CONT-07 | — | Pending |
| CONT-08 | — | Pending |
| CONT-09 | — | Pending |
| CONT-10 | — | Pending |
| CONT-11 | — | Pending |
| CONT-12 | — | Pending |

**Coverage:**
- v1 requirements: 52 total
- Mapped to phases: 0 (roadmap not yet created)
- Unmapped: 52 ⚠️

---
*Requirements defined: 2026-04-16*
*Last updated: 2026-04-16 after initial definition*
