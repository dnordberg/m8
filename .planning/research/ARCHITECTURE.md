# Architecture Patterns

**Domain:** Layering Academy (RPG/quest/mini-game) + DSP improvements into existing Android Kotlin/Compose/Rust app
**Researched:** 2026-04-15
**Confidence:** HIGH — all findings derive directly from reading the live codebase (M8ViewModel, MainActivity, M8Tutorial, lib.rs, M8Emulator, M8DisplayBuffer, ARCHITECTURE.md, CONCERNS.md)

---

## Existing Architecture Snapshot

```
User input (touch / keyboard / gamepad)
    │
    ▼
MainActivity ──keyState──► M8ViewModel (AndroidViewModel, ~547 lines)
    │                            │
    │                            ├─► emulator: M8Emulator (~1200 lines)
    │                            │      ├── M8Song (mutable, no lock)
    │                            │      ├── M8FxEngine
    │                            │      └── renderFrame() → byte[]
    │                            │
    │                            ├─► audio thread (URGENT_AUDIO priority, raw Thread)
    │                            │      ├── reads emulator.playing / song.tempo @Volatile
    │                            │      ├── triggerCurrentRow() → NativeSynth.triggerRow() (JNI)
    │                            │      └── NativeSynth.generateChunk() → M8AudioPlayer
    │                            │
    │                            └─► emulatorRenderJob (coroutine, ~30fps, Dispatchers.Main)
    │                                   └── renderFrame() → M8DisplayBuffer → Compose
    │
    ▼
M8App (Compose) — local var `dawMode: Boolean`, local var tutorial state
    ├── M8Screen / M8BestLayout / M8FullDeviceLayout  (m8 mode)
    ├── DawLayout                                       (daw mode)
    └── TutorialOverlay / HotkeyOverlay / dialogs      (overlays)
```

Key threading facts drawn from source:

| Thread / Coroutine | Priority | What it touches |
|---|---|---|
| Main (UI) | normal | Compose state, displayTick, keyState flows |
| M8SynthThread (raw Thread) | URGENT_AUDIO + MAX_PRIORITY | emulator.playing, song.tempo, song.songGrid, song.chains, song.phrases (no lock), NativeSynth JNI |
| emulatorRenderJob (coroutine) | Dispatchers.Default (launched in viewModelScope) | NativeSynth.getMasterLevels, emulator.renderFrame, connectionManager.protocol |

Concurrency fragility confirmed in source:
- `songRow / chainRow / phraseRow` are `@Volatile` — safe for single-writer / single-reader visibility but not atomic compound updates
- `M8Song` fields (songGrid, chains, phrases, tempo) are plain mutable arrays accessed from both the audio thread and UI — no lock, no copy-on-write
- `M8DisplayBuffer` uses per-method `synchronized` blocks, so multi-step draw sequences are not atomic

---

## Recommended Architecture: Academy Integration

### Principle: Academy as a Peer Mode, Not a Parasite

The existing pattern is `dawMode: Boolean` local state in `M8App`. Academy follows the same pattern — a top-level mode variable in `M8App` that swaps the visible UI surface. The emulator keeps running (audio thread stays alive) regardless of mode. Academy reads from the emulator; it never writes to it.

```
M8App (Compose)
    ├── mode: AppMode  { M8, DAW, ACADEMY }   ← extend existing dawMode bool
    │
    ├── [M8 mode]    M8Screen + controls (existing)
    ├── [DAW mode]   DawLayout (existing)
    └── [ACADEMY mode]
            ├── AcademyShell (Compose, fullscreen)
            │      ├── visual-novel narrative layer (characters, dialogue, XP bar)
            │      ├── quest panel (current quest, completion indicator)
            │      ├── mini-game surface (Canvas or Compose, isolated coroutine)
            │      └── "Return to M8" button → pops back to M8 mode with quest active
            │
            └── [Quest active, M8 mode visible]
                   ── QuestOverlay (thin transparent Compose layer on top of M8Screen)
                         └── reads QuestViewModel state only; never touches emulator
```

### Component Map

| Component | Package | Responsibility | Communicates With |
|---|---|---|---|
| `M8ViewModel` | `com.m8droid` | Existing orchestrator — audio + sequencer | Unchanged |
| `AcademyViewModel` | `com.m8droid.academy` | Quest state machine, XP, chapter progression, mini-game lifecycle | Reads `EmulatorEventRepository`; writes to `AcademyRepository` |
| `EmulatorEventRepository` | `com.m8droid.academy.data` | Publishes `SharedFlow<EmulatorEvent>` of emulator state snapshots | Fed by `M8ViewModel` via a thin observer hook; consumed by `AcademyViewModel` |
| `AcademyRepository` | `com.m8droid.academy.data` | Persists XP, chapter, quest index, mini-game scores | Backed by DataStore (simple) or Room (if relational queries needed) |
| `QuestEngine` | `com.m8droid.academy.quest` | Evaluates `QuestCondition` predicates against `EmulatorSnapshot` | Pure Kotlin, no Android deps, testable in plain JVM |
| `MiniGameEngine` | `com.m8droid.academy.minigame` | Runs mini-game logic on a coroutine; exposes `StateFlow<MiniGameState>` | Isolated; reads no emulator state |
| `AcademyShell` | `com.m8droid.ui.academy` | Top-level Academy Compose surface | Observes `AcademyViewModel` |
| `QuestOverlay` | `com.m8droid.ui.academy` | Thin overlay on M8 mode showing active quest progress | Observes `AcademyViewModel` |

---

## Data Flow: Quest Observation (the critical design question)

**Goal:** detect "user edited a phrase step", "user set swing > 50 in the phrase screen", "a step triggered" — without coupling Academy code into every emulator screen.

**Chosen pattern: Snapshot diff published via SharedFlow from M8ViewModel**

```
Audio thread / render coroutine
    │
    │  (existing, unchanged)
    ▼
M8ViewModel.onEachFrame() — already ticks at ~30fps via displayTick
    │
    │  NEW: after renderFrame(), snapshot observable fields into EmulatorSnapshot
    ▼
EmulatorSnapshot(
    screen: Int,
    cursorX: Int, cursorY: Int,
    editMode: Boolean,
    playing: Boolean,
    phraseStep: PhraseStep?,      // step under cursor if on phrase screen
    swingValue: Int?,             // swing of current phrase row if visible
    phraseStepCount: Int,         // number of non-empty steps
    songRow: Int, chainRow: Int, phraseRow: Int
)
    │
    ▼
EmulatorEventRepository.emit(snapshot)   ← SharedFlow(replay=1)
    │
    ▼
AcademyViewModel.collectLatest { snapshot →
    questEngine.evaluate(activeQuest, snapshot)
}
    │
    └── QuestCondition.isSatisfied(snapshot) → Boolean
```

**Why not an event bus or side-channel on M8Emulator?**

- Event bus (global singleton): couples emitter to bus infrastructure; ordering / backpressure guarantees are poor; hard to test quest predicates in isolation.
- Side-channel on `M8Emulator`: M8Emulator is already 1200 lines; adding Academy hooks couples Academy logic into the core emulator, violating the constraint "do not disturb live emulator state."
- Repository listener / snapshot diff: the 30fps `displayTick` coroutine already re-reads all emulator state to drive the UI. Piggy-backing a snapshot emit onto that tick costs ~zero overhead (struct field copies, no allocation in the hot path), produces a stable `SharedFlow`, and keeps Academy code in its own package with zero coupling into `M8Emulator.kt` or any UI screen.

**Implementation detail:** `EmulatorEventRepository` is constructed in `M8ViewModel` (or passed in) and exposed as a read-only `SharedFlow`. `M8ViewModel.startLocalEmulator` calls `emit()` at the end of each render tick. `AcademyViewModel` receives the repository via constructor injection (no DI framework needed — manual wiring in `MainActivity` or via a `ViewModelFactory`).

---

## Data Flow: Academy Persistence

**Use DataStore for progression state. Room only if quest history queries are needed.**

Rationale:
- Academy state (current chapter, quest index, XP total, mini-game high scores) fits in a flat proto/preferences store — no relational queries needed for MVP.
- DataStore is already declared in `app/build.gradle.kts` (`androidx.datastore:datastore-preferences 1.1.1`). Adding Room requires a new dependency and schema migration path.
- If "history of completed quests" is needed for display (e.g., "chapter 1 complete, chapter 2 in progress"), a simple `Set<String>` of completed quest IDs in DataStore suffices.
- Escalate to Room only when: (a) quest analytics across sessions, (b) multiple save slots.

```kotlin
// AcademyRepository — DataStore-backed
data class AcademyProgress(
    val currentChapter: Int,        // 0-5
    val currentQuestIndex: Int,
    val xpTotal: Int,
    val completedQuestIds: Set<String>,
    val miniGameScores: Map<String, Int>
)
```

---

## Data Flow: Mini-Game Isolation

**Mini-games run in a coroutine scoped to AcademyViewModel. They never touch the audio thread.**

```
AcademyViewModel.viewModelScope
    │
    └── launch(Dispatchers.Default) {
            MiniGameEngine.run(gameId, config)
                ├── game loop: delay(16ms) → update state → emit to StateFlow
                └── input: receives events via Channel<MiniGameInput> from UI
        }
```

Rules enforced by structure:
1. `MiniGameEngine` has no reference to `M8ViewModel`, `M8Emulator`, `NativeSynth`, or any audio class. Verified at compile time by package boundaries.
2. Mini-game coroutines are cancelled when `AcademyViewModel` is cleared — no leaks.
3. Mini-game UI is a separate Compose subtree inside `AcademyShell`, not overlaid on `M8Screen`, so it cannot inadvertently forward touches to the emulator.
4. No `Thread.sleep`, no blocking calls in mini-game logic — coroutine delay only.

---

## AcademyViewModel: Separate, Not Shared

**Verdict: Separate ViewModel sharing read-only access to `EmulatorEventRepository`.**

| Option | Assessment |
|---|---|
| Extend `M8ViewModel` | Adds ~500+ lines of Academy logic to an already 547-line file; blurs the single-responsibility of audio orchestration; makes unit testing quest logic impossible without spinning up an audio thread. Rejected. |
| Replace with a shared "AppViewModel" | Massive refactor of existing working code; high risk to audio thread invariants. Rejected. |
| Separate `AcademyViewModel` with shared `EmulatorEventRepository` | Academy gets its own lifecycle, its own `SavedStateHandle`-compatible state, testable quest predicates. The bridge (`EmulatorEventRepository`) is a thin read-only `SharedFlow`. Chosen. |

`AcademyViewModel` is a standard `AndroidViewModel`. It is instantiated via `viewModels()` in `MainActivity` alongside the existing `M8ViewModel`, or lazily via `activityViewModels()` from the Academy Compose subtree.

---

## DSP Work: Safe Refactor Path for Rust Synth

### The Core Problem

`m8-synth/src/lib.rs` has zero tests. The global `ENGINE: Mutex<Option<SynthEngine>>` is the only instance; JNI functions lock it on every call. Audio quality improvements (oscillator fidelity, SVF tuning, reverb character) risk silent regressions because there is no automated verification.

### Step 1: Establish a Golden-Render Test Harness Before Any DSP Changes

**Where tests belong:** `m8-synth/tests/golden.rs` (Rust integration tests, run via `cargo test`)

```
m8-synth/
├── Cargo.toml
├── src/lib.rs
└── tests/
    ├── golden.rs          ← render N notes, compare PCM to stored .raw snapshots
    ├── fixtures/
    │   ├── c4_saw_100ms.raw
    │   ├── a3_fm_200ms.raw
    │   └── ...
    └── helpers.rs         ← extract pure-Rust render path from JNI wrappers
```

**Critical design constraint:** The JNI export functions (`Java_com_m8droid_audio_NativeSynth_*`) cannot be called from Rust tests because there is no JVM. The fix is to expose the DSP core as a pure-Rust public API alongside the JNI surface:

```rust
// NEW: pure-Rust test surface (no JNI)
pub struct SynthEngine { ... }  // make pub
impl SynthEngine {
    pub fn new() -> Self { ... }
    pub fn trigger_row(&mut self, notes: &[i32], vols: &[i32]) { ... }
    pub fn generate_chunk(&mut self) -> &[u8] { ... }
}

// JNI exports remain, now delegate to the pub methods
#[unsafe(no_mangle)]
pub extern "system" fn Java_..._generateChunk(...) -> jbyteArray {
    // lock ENGINE, call eng.generate_chunk(), copy to JNI array
}
```

Use `insta` crate for snapshot testing of the rendered PCM — it serializes expected output and fails if the output changes, forcing an explicit "accept" step for any DSP change.

### Step 2: Replace `.unwrap()` at the JNI Boundary Before Changing DSP Logic

Every `.unwrap()` on a JNI call in the current code is a potential process abort. Before iterating on DSP parameters, harden the JNI surface:

```rust
// Current (fragile):
let output = env.new_byte_array(CHUNK_BYTES as i32).unwrap();
env.set_byte_array_region(&output, 0, signed).unwrap();

// Target (safe):
let output = match env.new_byte_array(CHUNK_BYTES as i32) {
    Ok(arr) => arr,
    Err(_) => return std::ptr::null_mut(),  // return null; Kotlin null-checks
};
```

This is a prerequisite for DSP work, not part of DSP work itself.

### Step 3: Extract DSP Parameters into a Configuration Struct

Currently, DSP tuning is in the `PRESETS` const array and hardcoded constants scattered through `Voice::generate()`. Improving SVF character, oscillator fidelity, or reverb tail requires touching the same `generate()` function that golden tests will lock in. The refactor:

```rust
// Extract tunable globals into a named config (stays in lib.rs, no new file needed)
struct DspConfig {
    master_gain: f64,           // currently hardcoded 0.35
    delay_return: f64,          // currently hardcoded 0.3
    reverb_return: f64,         // currently hardcoded 0.25
    limiter_threshold: f64,     // currently hardcoded 0.85
    limiter_knee: f64,          // currently hardcoded 6.0
}
```

This lets A/B comparisons run in tests: construct two `SynthEngine` instances with different `DspConfig`, render the same phrase, compare outputs.

### Step 4: DSP Improvements (order matters for audio-thread safety)

**Safe changes (no thread model risk):**
- Tune `PRESETS` values (cutoff, reso, ADSR times) — pure data change, no code path change
- Adjust `master_gain`, `delay_return`, `reverb_return` constants
- Improve SVF coefficient formula for better high-frequency stability

**Changes requiring audio-thread care:**
- Adding new oscillator waveforms or filter modes: the `match p.wave` block runs on the audio thread; any new branch must be branchless-friendly and must not allocate
- Adding per-voice chorus / new effect bus: new `Vec<f64>` buffers must be allocated in `SynthEngine::new()`, never in `generate_chunk()`
- Changing `CHUNK` size: requires coordinating with `M8Synth.CHUNK_SAMPLES` on the Kotlin side and `M8AudioPlayer` buffer sizing

**Pattern for audio-safe parameter changes:** Since `ENGINE` is behind a `Mutex`, parameter updates (e.g., from a JNI `setDspConfig` call triggered by a UI knob) will briefly contend with `generateChunk`. This is acceptable for infrequent UI-driven updates. Do not introduce lock-free atomics for DSP parameters without a specific measured dropout problem to solve.

---

## Component Boundaries (build-enforced)

```
com.m8droid
├── M8ViewModel.kt                   (existing — audio + sequencer orchestration)
├── MainActivity.kt                  (existing — extend AppMode enum, wire AcademyViewModel)
│
├── emulator/                        (existing — M8Emulator, M8Song, M8FxEngine, parsers)
│   └── [no changes for Academy]
│
├── academy/                         (NEW package)
│   ├── AcademyViewModel.kt          (AndroidViewModel)
│   ├── data/
│   │   ├── EmulatorEventRepository.kt   (SharedFlow<EmulatorSnapshot>)
│   │   ├── EmulatorSnapshot.kt          (data class, all observable emulator state)
│   │   ├── AcademyRepository.kt         (DataStore-backed progress)
│   │   └── AcademyProgress.kt           (data class)
│   ├── quest/
│   │   ├── QuestEngine.kt               (pure Kotlin, no Android imports)
│   │   ├── QuestCondition.kt            (sealed interface / function type)
│   │   ├── Quest.kt                     (data class: id, description, condition)
│   │   └── QuestCatalog.kt              (all quest definitions)
│   └── minigame/
│       ├── MiniGameEngine.kt            (interface)
│       ├── MiniGameState.kt             (sealed class)
│       └── games/
│           ├── PatternMatchGame.kt
│           ├── FxCommandGame.kt
│           └── SampleSliceGame.kt
│
└── ui/
    ├── academy/
    │   ├── AcademyShell.kt              (top-level Compose for Academy mode)
    │   ├── QuestOverlay.kt              (overlay on M8 mode during active quest)
    │   ├── NarrativeView.kt             (visual novel characters + dialogue)
    │   ├── XpBar.kt                     (progression UI)
    │   └── minigame/
    │       └── [game-specific Compose composables]
    └── [existing screens unchanged]
```

**Package boundary enforcement:** `QuestEngine` must not import from `com.m8droid.emulator` or `com.m8droid.audio`. It receives only `EmulatorSnapshot` (a plain data class with no emulator references). This is checkable in code review and can be enforced via a lint rule or Detekt module dependency rule.

---

## Recommended Build Order

This order minimises blocking dependencies across both workstreams.

### Phase 1: Infrastructure (unblocks everything else)

1. **DSP: JNI `.unwrap()` hardening** — zero risk, unblocks safe DSP iteration
2. **DSP: Pure-Rust test surface** — expose `SynthEngine` as pub, add `tests/` directory, add `insta` to Cargo.toml
3. **DSP: Golden-render test seeds** — record baseline PCM for 4–6 representative phrases; commit as fixtures
4. **Academy: `EmulatorSnapshot` data class + `EmulatorEventRepository` SharedFlow** — pure Kotlin, no UI, no DB
5. **Academy: Hook emit into `M8ViewModel` render tick** — one-liner addition at end of `emulatorRenderJob` loop
6. **Academy: `AcademyProgress` + `AcademyRepository` (DataStore)** — persistence layer, no quest logic yet

Deliverable of Phase 1: tests pass, snapshot emits are observable, progress persists. No UI visible.

### Phase 2: Core Logic (parallel streams now possible)

**Stream A — DSP quality:**
7. SVF coefficient improvements (now protected by golden tests)
8. Oscillator waveform tuning / additional waveform modes
9. Reverb / delay character improvements
10. New JNI `setPreset` or `setDspConfig` call if runtime tuning from Kotlin is needed

**Stream B — Academy quest engine:**
7. `QuestEngine` + `QuestCondition` — pure Kotlin unit tests, no Android
8. `QuestCatalog` — define Chapter 1 quests (drums: 4-step phrase, swing > 50, etc.)
9. `AcademyViewModel` — state machine (idle → quest_active → quest_complete → narrative)

### Phase 3: UI Shell

10. `AppMode` enum replaces `dawMode: Boolean` in `M8App`
11. Academy icon in top-nav bar
12. `AcademyShell` — chapter select, narrative view, quest briefing
13. `QuestOverlay` — thin overlay on M8 mode showing active quest + completion indicator
14. XP feedback, unlock animations

### Phase 4: Mini-Games + Full Chapter Content

15. Mini-game framework (`MiniGameEngine` interface, coroutine harness)
16. Individual mini-game implementations (pattern match, FX recall, sample slice)
17. Chapter 2–6 quest content (synths, sampling, FX, song structure, final jam)
18. Visual-novel narrative content (characters, dialogue scripts)

---

## Anti-Patterns to Avoid

### Anti-Pattern 1: Reading M8Song from the Academy Layer

**What:** `AcademyViewModel` or `QuestEngine` directly accesses `M8ViewModel.songData` (which returns `emulator.song`, the live mutable `M8Song`).
**Why bad:** `M8Song` fields are mutated without locks on the audio thread. Academy code reading those fields from a coroutine creates a data race. There is no safe way to read `M8Song` from outside the audio thread without a lock or copy.
**Instead:** `EmulatorSnapshot` contains only the fields needed for quest evaluation, copied in the render coroutine (which already runs after the audio thread's `triggerCurrentRow` has settled for the frame).

### Anti-Pattern 2: Blocking Calls or Sleep in Mini-Game Coroutines

**What:** `Thread.sleep(16)` or any blocking IO inside a mini-game coroutine.
**Why bad:** Coroutine dispatcher threads are shared; blocking one stalls other coroutines including the `emulatorRenderJob`.
**Instead:** `delay(16)` — suspends, does not block the thread.

### Anti-Pattern 3: Allocating in `generate_chunk()` on the Audio Thread

**What:** Creating a `Vec<f64>` or any heap allocation inside `SynthEngine::generate_chunk()` to support a new effect.
**Why bad:** Rust's allocator can block on mutex or syscall; on Android this risks audio dropout.
**Instead:** All buffers are pre-allocated in `SynthEngine::new()`. If a new effect bus is added (e.g., chorus), its delay buffer is sized and zeroed at construction.

### Anti-Pattern 4: Letting Academy Navigate the M8 Emulator

**What:** Academy code calls `M8ViewModel.setScreen()` or `emulator.handleKeyState()` to navigate to the phrase screen to verify a quest.
**Why bad:** Steals user control; changes emulator state without user action; creates a feedback loop where quest verification itself satisfies quest conditions.
**Instead:** Quest conditions are purely observational: `snapshot.screen == SCREEN_PHRASE && snapshot.swingValue > 50`. The user navigates; the system observes.

### Anti-Pattern 5: Persisting Academy State in M8ViewModel

**What:** Adding `questIndex`, `xpTotal`, etc. as fields in `M8ViewModel` for convenience.
**Why bad:** Entangles Academy lifecycle with audio lifecycle; `M8ViewModel.onCleared()` already stops the audio thread — Academy progress must outlive audio lifecycle events (e.g., user puts app in background).
**Instead:** `AcademyRepository` (DataStore) is the source of truth; `AcademyViewModel` owns it.

---

## Scalability Considerations

| Concern | Current Scale | Academy Addition | Risk |
|---|---|---|---|
| `emulatorRenderJob` tick cost | Renders frame + copies level data | Add: copy ~10 fields into `EmulatorSnapshot`, emit to SharedFlow | Negligible — struct copy, no allocation |
| `EmulatorEventRepository` backpressure | N/A | `SharedFlow(replay=1, extraBufferCapacity=0)` — if Academy is slow, snapshot is dropped (latest wins) | Acceptable; quest checking is idempotent |
| DataStore write frequency | Settings only (infrequent) | XP/progress writes on quest completion (infrequent) | No concern |
| Mini-game coroutine count | 0 | 1 per active mini-game, cancelled on exit | No concern |
| Rust synth Mutex contention | `generateChunk` holds lock ~0.016ms per chunk | Any new JNI param-update call contends | Low — param updates are UI-driven, infrequent |

---

## Sources

All findings are HIGH confidence — derived directly from source code read in this session:

- `/app/src/main/java/com/m8droid/M8ViewModel.kt` (547 lines, read in full)
- `/app/src/main/java/com/m8droid/MainActivity.kt` (294 lines, read in full)
- `/app/src/main/java/com/m8droid/tutorial/M8Tutorial.kt` (407 lines, read in full)
- `/m8-synth/src/lib.rs` (615 lines, read in full)
- `.planning/codebase/ARCHITECTURE.md`
- `.planning/codebase/CONCERNS.md`
- `.planning/codebase/STRUCTURE.md`
- `.planning/codebase/TESTING.md`
- `.planning/codebase/STACK.md`
- `.planning/PROJECT.md`
