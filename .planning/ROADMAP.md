# Roadmap — m8droid: Sound Quality + M8 Academy

**Milestone:** Sound Quality + M8 Academy
**Branch:** main-rpg
**Depth:** Comprehensive
**Created:** 2026-04-16
**Requirements:** 52 v1 requirements across 5 phases
**Coverage:** 52/52 (100%)

---

## Overview

This milestone adds two major capabilities to the existing m8droid Android emulator: tighter audio quality in the Rust DSP backend, and a gamified learning mode (M8 Academy) built as a peer navigation mode in Compose. Phase 1 is a hard gate — it establishes the test harness and snapshot architecture that make all downstream work regression-safe and architecturally sound. Sound quality and Academy engine work proceed in parallel after Phase 1 because they share zero runtime dependencies. The Plaits macrosynth port (highest-complexity DSP item) and all content authoring are deferred to the final phase so they cannot block the Academy launch.

---

## Phases

### Phase 1 — Infrastructure

**Goal:** Both workstreams have a safe floor to build on — the Rust DSP surface is guarded by golden-render tests, and the emulator-to-Academy snapshot bridge is wired and unit-tested.

**Dependencies:** None. This is the root phase.

**Requirements:** INFRA-01, INFRA-02, INFRA-03, INFRA-04, INFRA-05, INFRA-06, INFRA-07, INFRA-08

**Success Criteria:**

1. Running `cargo test` in `m8-synth/` executes at least three golden-render tests that fail if any rendered audio sample deviates from the committed fixture — a developer can verify this in under 60 seconds on a fresh clone.
2. A Kotlin unit test covering `EmulatorEventRepository` emitting a `SharedFlow<EmulatorSnapshot>` passes via `./gradlew test` without requiring a connected device.
3. `AcademyProgress` round-trips through DataStore Proto (write → process kill → read) with all fields intact, verified by an instrumented unit test.
4. The JNI boundary in `lib.rs` returns null/error codes to Kotlin for all previously-unwrapping call sites — verified by intentionally passing a null input and confirming no process abort occurs.
5. The first Academy ViewModel unit test (using JUnit5 + Turbine) runs green in `./gradlew test` with no device attached.

---

### Phase 2 — Sound Quality Core

**Goal:** The emulator sounds noticeably closer to real M8 hardware — playback is dropout-free on a mid-range Android device, the delay and filter have analog warmth, aliasing on high notes is reduced, and the reverb tail is smoother.

**Dependencies:** Phase 1 (golden-render tests must exist before any DSP is changed).

**Requirements:** SND-01, SND-02, SND-03, SND-04, SND-05, SND-06, SND-07, SND-08, SND-09, SND-10, SND-12

**Success Criteria:**

1. A 2-minute stress playback on the declared mid-range baseline device reports `AudioTrack.getUnderrunCount() == 0` — verifiable by running the built-in stress test and reading the logged underrun count.
2. A user switching rapidly between M8 screens or adjusting filter cutoff live hears no zipper noise — the parameter transition is smooth and click-free.
3. The delay effect has audible tape-warmth saturation at high feedback levels — A/B compare against the pre-Phase-2 build confirms the character difference.
4. The reverb tail is smooth (Dattorro plate) rather than metallic — a developer can verify by running the golden-render test for the reverb fixture and comparing the waveform spectrogram.
5. All existing golden-render tests still pass after every DSP change — no regressions introduced.

---

### Phase 3 — Academy Engine

**Goal:** The Academy quest engine, state machine, and persistence layer work correctly and are fully unit-tested — the logic is complete even before any Academy UI exists.

**Dependencies:** Phase 1 (EmulatorEventRepository and DataStore schema must exist; JUnit5 + Turbine must be available).

**Note:** Phases 2 and 3 can run in parallel. They share zero runtime dependencies — Phase 2 modifies Rust DSP, Phase 3 is pure Kotlin with no audio or emulator imports.

**Requirements:** ACAD-01, ACAD-02, ACAD-03, ACAD-04, ACAD-05, ACAD-06, ACAD-07, ACAD-08, ACAD-09

**Success Criteria:**

1. `AppMode` enum is wired through `M8App` and `MainActivity` — entering and exiting Academy mode in code does not stop the audio thread or reset emulator state, verified by a unit test asserting audio-thread lifecycle invariants.
2. `QuestEngine` has zero imports from `com.m8droid.emulator` or `com.m8droid.audio` — verified by a compile-time module boundary check and unit tests that run on the plain JVM without Android SDK.
3. The `AcademyViewModel` state machine transitions `idle → quest_active → quest_complete → narrative` and back, verified by Turbine-based unit tests covering each transition including quest failure and retry.
4. Academy progress (chapter, quest index, XP, completed quest IDs) survives a simulated process death — write progress, kill process in test, re-initialize, confirm restored state.
5. Quest context (active quest + partial conditions) survives `onPause` / `onResume` — a developer can background the app mid-quest and return to find the same quest active.

---

### Phase 4 — Academy UI Shell

**Goal:** A user can open M8 Academy from the top navigation, play through a quest in the real M8 UI with an overlay showing live condition status, and receive clear feedback on success or failure — the full interaction loop is usable.

**Dependencies:** Phase 3 (Academy engine and ViewModel must be complete; AppMode refactor must be wired).

**Requirements:** ACAD-10, ACAD-11, ACAD-12, ACAD-13, ACAD-14, ACAD-15, ACAD-16, ACAD-17, ACAD-18, ACAD-19, ACAD-20

**Success Criteria:**

1. A new user can tap the game icon in the top navigation, complete onboarding, and reach the first playable quest in 30 seconds or fewer — measurable with a stopwatch by any tester.
2. The chapter map is visible from the first Academy screen on day one, showing all six chapters with completion indicators — no chapters are hidden or locked from view.
3. During an active quest, a `QuestOverlay` is visible on the M8 mode screen showing the active conditions and a live completion indicator — observable by entering a quest and switching to M8 mode.
4. Completing a quest triggers a haptic vibration and an XP tick animation — the celebration is observable on a physical device within one second of the final condition being met.
5. A failed quest attempt displays a condition-diff (e.g., "Swing is 42% — need > 50%") rather than binary pass/fail — visible in the Academy UI after deliberately failing the swing condition.

---

### Phase 5 — Content, Mini-games, and Macrosynth

**Goal:** All six Academy chapters are playable end-to-end, the four between-chapter mini-games are implemented, the quest-writing guidelines are committed, and the Macrosynth Plaits port is shipped — the milestone is fully complete.

**Dependencies:** Phase 4 (Academy UI Shell must be complete for chapter content to be playable; Phase 2 golden tests must be green before Plaits DSP is merged).

**Requirements:** CONT-01, CONT-02, CONT-03, CONT-04, CONT-05, CONT-06, CONT-07, CONT-08, CONT-09, CONT-10, CONT-11, CONT-12, SND-11

**Success Criteria:**

1. A user can start Chapter 1 (Drums), complete all four quests end-to-end, and progress to Chapter 2 — verified by a tester walking the complete Chapter 1 path on a device.
2. All six chapters (Drums through Final Jam) are playable end-to-end with authored quests and dialogue — a tester can complete each chapter in sequence without hitting unimplemented screens.
3. All four mini-games (pattern match, FX flashcard, sample slicer, BPM tap-tempo) are reachable and interactive between chapters — verified by navigating to each mini-game and completing one round.
4. The Macrosynth Plaits port exposes the 8–10 most-used Braids/Plaits modes as selectable oscillator types in the emulator — a user can select and play any of the new modes and hear audio output without dropout.
5. The quest-writing guidelines document is committed to the repository and was used to frame all CONT-01..CONT-08 quests — verifiable by reviewing the committed doc and confirming the chapter quests follow its structure.

---

## Progress

| Phase | Goal | Requirements | Status |
|-------|------|--------------|--------|
| 1 — Infrastructure | Safe floor for both workstreams | INFRA-01..08 (8) | Not started |
| 2 — Sound Quality Core | Dropout-free, warmer, less aliased audio | SND-01..10, SND-12 (11) | Not started |
| 3 — Academy Engine | Quest engine + state machine + persistence | ACAD-01..09 (9) | Not started |
| 4 — Academy UI Shell | Playable Academy loop with overlay | ACAD-10..20 (11) | Not started |
| 5 — Content + Mini-games + Macrosynth | All chapters + mini-games + Plaits | CONT-01..12, SND-11 (13) | Not started |

**Phases 2 and 3 can execute in parallel after Phase 1 completes.**
**Phase 4 requires Phase 3 complete. Phase 5 requires Phase 4 complete and Phase 2 green.**

---

## Coverage Map

| Requirement | Phase |
|-------------|-------|
| INFRA-01 | 1 |
| INFRA-02 | 1 |
| INFRA-03 | 1 |
| INFRA-04 | 1 |
| INFRA-05 | 1 |
| INFRA-06 | 1 |
| INFRA-07 | 1 |
| INFRA-08 | 1 |
| SND-01 | 2 |
| SND-02 | 2 |
| SND-03 | 2 |
| SND-04 | 2 |
| SND-05 | 2 |
| SND-06 | 2 |
| SND-07 | 2 |
| SND-08 | 2 |
| SND-09 | 2 |
| SND-10 | 2 |
| SND-11 | 5 |
| SND-12 | 2 |
| ACAD-01 | 3 |
| ACAD-02 | 3 |
| ACAD-03 | 3 |
| ACAD-04 | 3 |
| ACAD-05 | 3 |
| ACAD-06 | 3 |
| ACAD-07 | 3 |
| ACAD-08 | 3 |
| ACAD-09 | 3 |
| ACAD-10 | 4 |
| ACAD-11 | 4 |
| ACAD-12 | 4 |
| ACAD-13 | 4 |
| ACAD-14 | 4 |
| ACAD-15 | 4 |
| ACAD-16 | 4 |
| ACAD-17 | 4 |
| ACAD-18 | 4 |
| ACAD-19 | 4 |
| ACAD-20 | 4 |
| CONT-01 | 5 |
| CONT-02 | 5 |
| CONT-03 | 5 |
| CONT-04 | 5 |
| CONT-05 | 5 |
| CONT-06 | 5 |
| CONT-07 | 5 |
| CONT-08 | 5 |
| CONT-09 | 5 |
| CONT-10 | 5 |
| CONT-11 | 5 |
| CONT-12 | 5 |

---

*Roadmap created: 2026-04-16*
*Last updated: 2026-04-16 after initial creation*
