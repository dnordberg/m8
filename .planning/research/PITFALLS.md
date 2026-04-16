# Domain Pitfalls

**Domain:** Android music-tracker emulator + gamified RPG learning mode (m8droid)
**Researched:** 2026-04-15
**Scope:** Both workstreams — DSP/sound-quality and gamification/Academy — plus integration

---

## Critical Pitfalls

Mistakes that cause rewrites, process aborts, or audio that is silently wrong forever.

---

### Pitfall 1: Aliasing Sneaking Back In During "Improvements"

**What goes wrong:** A developer improves oscillator fidelity — better waveform shaping, tighter frequency tracking, new macrosynth mode — and inadvertently breaks the PolyBLEP correction. The result is a waveform that sounds louder or brighter but has high-frequency aliasing components that are inaudible on laptop speakers and invisible without spectrum analysis. The regression ships because there is no automated reference.

**Why it happens:** The current `poly_blep()` in `lib.rs` is a single-function correction applied at phase discontinuities (lines 514–525). Any refactor that changes phase accumulation ordering, normalises frequency differently, or adds oversampling without re-applying the BLEP at the right point will silently drop the correction. The `phase -= self.phase.floor()` approach at line 235 is fragile — it accumulates floating-point error at very high notes and can create sub-sample jitter that shifts discontinuity timing out of the BLEP window.

**Consequences:** Aliasing at harmonics of `f_note` fold around Nyquist and land in-band. On some patches it is subtle; on high-register FM (voice 7, `fm_ratio: 7.0`) it is immediately objectionable. Existing `.m8s` songs render differently.

**Warning signs:**
- Spectrum of a sawtooth at A5 (880 Hz) has asymmetric harmonic falloff
- FM bell patch sounds harsher than before the change
- Any test at note 100+ produces audible grit that wasn't there before

**Prevention:**
- Before touching any oscillator code, capture golden WAV reference renders for at least: saw at A2/A4/A6, pulse at 50% PW, FM bell (preset 4) at C4. Store in `m8-synth/tests/golden/`.
- Add a Rust `#[cfg(test)]` golden-sample test that renders 2048 samples, computes RMS of the top octave (>10 kHz), and asserts it stays below a threshold calibrated to the pre-change render.
- After any BLEP change, run a quick spectrum analysis with Audacity or sox before committing.

**Phase mapping:** Sound-quality phase; must establish golden tests before first DSP commit.

---

### Pitfall 2: Filter Stability / NaN Explosion

**What goes wrong:** The SVF in `lib.rs` is tuned at lines 237–252. It already has `clamp(-4.0, 4.0)` guards and `is_finite()` resets. If someone tunes `f` higher (e.g. raises the `0.48` Nyquist guard to `0.49`), or adds a resonance mode that computes `q` differently, the `f > 2*q - epsilon` stability condition can be violated. One NaN propagates through the 8-voice loop, contaminates the delay buffer, and produces a single loud digital click — or, if the clamping guards haven't been extended to the new code path, a sequence of NaNs that converts the audio output to white noise for the duration of the song.

**Why it happens:** SVF stability requires `f ≤ 2q` per sample. The existing guard `f = (...).min(2.0 * q - 0.01)` at line 242 is correct but silently depends on `q ≥ 0.5`, enforced by `let q = (1.0 - p.reso * 0.95).max(0.5)`. If resonance is redesigned for closer real-M8 character and `q` is allowed below 0.5, the guard becomes `f ≤ something negative`, which `.min()` does not catch.

**Consequences:** One bad frame produces an audible click. Continued NaN propagation mutes all subsequent output until `all_notes_off()` resets the filter state.

**Warning signs:**
- Single loud click on a specific patch/note combination
- Voice goes silent mid-sustain after a parameter sweep
- `is_finite()` guards triggering (add logging there during development)

**Prevention:**
- Any resonance redesign must re-derive the stability bound analytically, not just "raise reso and listen."
- Add `debug_assert!(f <= 2.0 * q)` and `debug_assert!(q > 0.0)` after computing both, so tests catch violations in debug builds.
- Extend golden tests to include a high-resonance patch at cutoff sweep.
- The `is_finite()` resets (lines 249–250) are a safety net, not the primary guard — do not remove them even if you believe the math is correct.

**Phase mapping:** Sound-quality phase. Any filter-character work must start with stability analysis.

---

### Pitfall 3: Oscillator Math Changes Break Existing .m8s Song Renders

**What goes wrong:** A frequency mapping or phase normalisation change makes the emulator produce subtly different pitches or timbres for the same note number. Existing songs that were authored and saved as `.m8s` files now render differently from the real M8 hardware reference. Because there are no golden-render tests, the regression is not caught until a user compares side-by-side.

**Why it happens:** The note-to-frequency formula at line 393 (`440.0 * (2.0_f64).powf((note as f64 - 69.0) / 12.0)`) is standard 12-TET, but the M8 hardware uses its own tuning table in firmware. If the improvement work references the headless firmware source and adopts a slightly different mapping, the renders diverge. Similarly, any change to how `vol` is interpreted (line 394: `vol as f64 / 255.0` vs a curve) changes amplitude across all existing songs.

**Consequences:** Sound designer's tracks no longer sound like they intended. Community `.m8s` files from the M8 tracker scene render wrong.

**Warning signs:**
- A known `.m8s` demo track sounds "off" — pitches feel slightly flat or sharp
- Volume envelopes punch differently from the reference recording

**Prevention:**
- Treat the note-to-freq formula and vol-to-amplitude mapping as a fixed contract. Document them explicitly in `lib.rs` as `// M8 firmware compat: do not change`.
- Before any tuning change, compare against headless firmware output (Dirtywave publishes binaries); treat firmware as ground truth.
- Keep a `tests/regression/` directory of short `.m8s` files + expected frequency content assertions.

**Phase mapping:** Sound-quality phase; define the compatibility contract on day one.

---

### Pitfall 4: Denormal Numbers Causing Performance Cliffs

**What goes wrong:** The reverb comb filters and the noise LP at line 216–217 produce very small floating-point values during silent periods. On ARM processors denormal (subnormal) IEEE 754 values are handled in software, not hardware, causing 10–100× slowdown for the affected samples. The audio thread (which runs at `THREAD_PRIORITY_URGENT_AUDIO`) takes longer than the buffer period, causing a buffer underrun.

**Why it happens:** The current code has a silence gate (lines 406–416) that calls `delay.clear()` and `reverb.clear()` when no voices are active. This correctly flushes buffers between songs. But during a song with sparse notes (long rests between trig rows), voices enter their release stage and decay toward zero — without being fully silenced. The comb filter feedback `* 0.84` at line 329 causes exponential decay; after many iterations the values are `~1e-320`, well into denormal range. ARM Cortex-A (Android mid-range devices) does not have hardware flush-to-zero by default unless the FPSCR register is configured.

**Warning signs:**
- CPU usage spikes on sparse arrangements but is fine on busy ones
- Audio stutters on budget/mid-range Android phones during long reverb tails
- Profiling shows reverb/delay processing time is 10× higher on silent sections

**Prevention:**
- Add a DC-offset flush guard to `Reverb::process()` and `Delay::process()`: after writing to the buffer, if `|value| < 1e-15`, store `0.0` instead. This costs two comparisons per sample and eliminates denormals.
- Alternatively, set flush-to-zero mode via Rust's `std::arch` or by writing to FPSCR at thread startup (must be done on the audio thread itself, not the JVM thread). Mark it clearly as a platform-specific audio thread optimisation.
- Add a microbenchmark in Rust tests that measures `generate_chunk()` time with a fully-decayed reverb tail and asserts it completes in less than 2× the "busy" time.

**Phase mapping:** Sound-quality phase, but also relevant to Academy integration phase (Academy must not stall the audio thread).

---

### Pitfall 5: Zipper Noise on Parameter Changes

**What goes wrong:** When the Academy quest system changes an instrument parameter in real time (e.g. "set cutoff to 80%" as a live demonstration), or when the user modifies parameters while the sequencer plays, the SVF cutoff frequency jumps instantaneously. At 44.1 kHz sample rate this causes a first-order discontinuity that is audible as a click or "zipper" on every parameter change event.

**Why it happens:** `triggerRow()` in `M8ViewModel.kt` fires on the audio thread boundary, but parameter lock changes (FX commands that alter cutoff/reso mid-phrase) are applied per-row, not per-sample. Each row is 735 samples (`CHUNK = 735`). A jump from `cutoff=0.3` to `cutoff=0.8` in one chunk is a 50% step function through the filter coefficient.

**Consequences:** Every live parameter change during Academy demonstrations sounds like a cheap synth. High-reso sweeps click.

**Warning signs:**
- Audible click when dragging a parameter slider during playback
- Academy "change your filter" quest prompts a harsh artifact
- The artifact is gone when playback is stopped — confirming it is a rate-change issue, not a signal clipping issue

**Prevention:**
- Add a per-voice `cutoff_target` and `reso_target`, and implement linear interpolation toward the target over one chunk (735 samples). This is 4 extra multiply-adds per sample per voice — negligible cost.
- Do not add Kotlin-side smoothing (moving average on the UI thread and posting to the audio thread) — that introduces cross-thread latency and makes zipper worse, not better.
- Alternatively, use one-pole LP smoothing: `cutoff_actual += 0.01 * (cutoff_target - cutoff_actual)` per sample.

**Phase mapping:** Sound-quality phase. Address before Academy, since Academy amplifies the problem by changing params live.

---

### Pitfall 6: Buffer Underruns on Mid-Range Android Hardware

**What goes wrong:** The audio render loop in `M8ViewModel.kt` runs on a single thread at `THREAD_PRIORITY_URGENT_AUDIO`. Any blocking operation in that loop — including Kotlin object allocation (GC pressure), the `ENGINE.lock().unwrap()` Mutex in Rust, or an Academy quest-check call that touches SharedPreferences or Room — causes a scheduling gap. The AudioTrack buffer underruns, producing a click or dropout.

**Why it happens:** Three specific risks in the current code:
1. `ENGINE.lock().unwrap()` is called for every `generateChunk()` call (line 574). The Mutex itself is nearly free unless contention exists. Contention would occur if Academy code triggers a parameter change from the UI thread while the audio thread holds the lock — the UI thread could hold an Android lock (e.g. DataStore write) and cause priority inversion.
2. The audio thread calls `Log.e(TAG, ...)` inside the catch block (line 344). `android.util.Log` is not real-time safe on all Android versions — it can block waiting for the logger.
3. Any Academy onboarding that adds a UI coroutine which accidentally posts to the audio thread's dispatcher will cause unbounded delay.

**Warning signs:**
- `AudioTrack.getUnderrunCount()` is non-zero after short playback on a mid-range test device
- Dropouts correlate with Academy UI navigation (mode switching, quest transitions)
- `adb logcat` shows GC events (`art: Explicit concurrent mark sweep GC`) coinciding with clicks

**Prevention:**
- Establish a rule: nothing new goes into the audio render loop body except DSP. All Academy state reads must be lock-free (read a `@Volatile` flag, not a DataStore call).
- Replace `Log.e` inside the audio loop with a ring buffer that the UI thread drains asynchronously.
- Test on a mid-range device (Snapdragon 680 class) with Academy running simultaneously, and profile with Android Studio's Energy Profiler + CPU Profiler.
- Explicitly add `AudioTrack.getUnderrunCount()` assertion to any audio-path integration test.

**Phase mapping:** Must be addressed as part of Academy integration work — the audio thread is the primary risk surface when adding any new feature.

---

### Pitfall 7: Breaking Regressions Because No Golden-Render Tests Exist

**What goes wrong:** This is the meta-pitfall that enables every other DSP pitfall. The codebase has zero automated tests (`app/src/test/` is empty, no `#[cfg(test)]` in `lib.rs`). Every sound-quality "improvement" is verified only by the developer's ears on the device in front of them. A regression can be: (a) an aliasing increase that is inaudible on laptop speakers but obvious on studio monitors, (b) a pitch drift of 2 cents that accumulates across an octave, (c) a denormal-triggered CPU spike that only manifests on a specific Android version.

**Why it happens:** Historical: the project was built without a test harness. The gap in `TESTING.md` is acknowledged. But without a build gate, "I'll add tests later" never happens.

**Consequences:** Every DSP change is a gamble. The Academy work (which changes parameters live) has no way to verify it does not regress playback.

**Prevention:**
- Before Phase 1 DSP work begins, seed the test suite with three Rust golden-render tests using `insta` or manual byte-comparison:
  - `test_saw_a4_spectrum`: render 4096 samples, FFT, assert top harmonic > -6 dBFS and aliasing mirror < -50 dBFS
  - `test_svf_no_nan`: sweep cutoff 0→1 while voice plays; assert every output sample `is_finite()`
  - `test_reverb_denormal`: render 20 silent chunks after note-off, assert no sample has `abs() < f64::MIN_POSITIVE && abs() > 0.0`
- Add one Kotlin JUnit test for `M8Protocol` SLIP decoding (parser concern from CONCERNS.md, not DSP, but unblocks test infrastructure).
- CI gate: the Rust `cargo test` must pass before any PR to `main-rpg` merges.

**Phase mapping:** Phase 0 / pre-work. Block DSP improvements on having at least the golden-render tests in place.

---

## Gamification / Academy Pitfalls

---

### Pitfall 8: Boring Chore Gamification That Demotivates Learners

**What goes wrong:** The quest system frames M8 tasks as assignments with XP rewards rather than as invitations to play. Quests read like homework: "Set the BPM to 120. Complete phrase row 1. Add swing of value 60." Users collect XP but feel no creative ownership. Within a few sessions they stop opening the Academy because it is less enjoyable than just playing the M8 freely.

**Why it happens:** This is the most documented failure mode in gamified learning research (2024 BJED study; Sage/Gamification review 2025). The mistake is designing for extrinsic reward (XP, badges) as the primary hook rather than intrinsic discovery. The M8 is an expressive creative tool; quest design that treats it like a form to fill out inverts the experience.

**Warning signs:**
- Quest text is imperative and prescriptive ("Set X to Y")
- There is only one correct completion state per quest
- Users open Academy, complete the quest, immediately close Academy and go back to free play
- Academy chapter completion rate drops sharply after chapter 1

**Prevention:**
- Design quests with a playful framing first: "Make a hi-hat pattern that feels urgent" is better than "Set note length to 1/16 on steps 1, 3, 5, 7." The detection system checks the functional outcome, not the exact sequence.
- Give partial credit: if a user accomplishes something musically coherent that is not the "expected" path, reward exploration.
- Keep quests short (under 3 minutes of focused effort). Long prescriptive quests are the worst offenders.
- Test each quest with a real M8 user who has never seen it: if they describe it as a chore, redesign it.

**Phase mapping:** Academy design phase. Establish quest-writing guidelines before any quest content is authored.

---

### Pitfall 9: Overly Linear Tutorial That Blocks Experimentation

**What goes wrong:** The Academy chapter structure (Drums → Synths → Sampling → FX → Song Structure → Final Jam) is implemented as a hard gate: you cannot access chapter 3 until you complete chapter 2. A user who already understands drums and synths but wants to learn FX has to sit through chapters they don't need. They abandon the Academy and use the existing `tutorial/` instead — which defeats the purpose of building the Academy.

**Why it happens:** Linear gating is easy to implement and "feels" structured. It mirrors traditional curriculum thinking, which is appropriate for school contexts but wrong for a creative tool where learners have wildly varying prior experience.

**Consequences:** Power users and M8 community members (the exact audience most likely to advocate for the app) find the Academy condescending and skip it entirely.

**Warning signs:**
- Users report in feedback that they "already know" chapter 1–3 content
- Academy engagement drops off after chapter 1 despite completion
- The Academy is described as "for beginners" in user reviews when the intent was broader

**Prevention:**
- Allow chapter selection from the start. Gate specific quest content within a chapter (e.g. quest 3 unlocks after quest 1 in the same chapter), not chapter access itself.
- Add a brief "skip ahead" diagnostic at Academy launch: "How much M8 experience do you have?" that unlocks chapters accordingly.
- Design chapter 1 to be completable in 5 minutes by a total beginner and still entertaining for an intermediate — use difficulty branching within the chapter rather than blocking.

**Phase mapping:** Academy design phase. Must be resolved in information architecture before content is authored.

---

### Pitfall 10: Quest Detection False Negatives ("I Did It, Why No Credit?")

**What goes wrong:** The quest system monitors M8 emulator state (song grid, phrase data, instrument parameters) and fires completion events when conditions are met. A user legitimately completes the task — makes a 4-step drum phrase with swing > 50% — but the detection logic checks `song.swing` at a moment when the emulator hasn't flushed the pending swing value, or checks `phraseRow.stepCount` before the display buffer has rendered the new state. The quest never completes. The user repeats the action, nothing happens, and they conclude the app is broken.

**Why it happens:** `M8Song` is mutable and shared across threads without explicit locks (CONCERNS.md, issue #6; ARCHITECTURE.md). The audio thread writes sequencer state; the quest detection logic (presumably running on the UI thread or a coroutine) reads it. Without memory fences, the UI thread can read stale values. `@Volatile` on individual fields (used for `songRow`, `chainRow`, `phraseRow` in `M8ViewModel`) provides single-field visibility but not compound-state atomicity.

**Consequences:** False negatives destroy trust in the quest system. One instance is tolerable; two in the same session causes abandonment.

**Warning signs:**
- Quest completion is "flaky" — works sometimes, not others — for the same user action
- Completion fires 2–3 seconds after the user action rather than immediately
- Completion never fires on actions that depend on multiple fields being true simultaneously (e.g. swing AND step count AND BPM)

**Prevention:**
- Define a `QuestSnapshot` data class capturing all observable M8 state fields atomically (copy under a lock or use a `@Synchronized` read method on `M8Song`).
- Quest detection must operate on a snapshot, not live field reads. Produce a new snapshot each time the sequencer advances a row (which already happens on the audio thread); post it to a `StateFlow` that the quest system collects.
- Add unit tests for each quest condition function with known-good and known-bad snapshots.
- Add a "manual re-check" button in the Academy UI so a user can tap "check now" if they believe they completed a quest — this is a safety valve, not a replacement for fixing detection.

**Phase mapping:** Academy quest detection implementation phase. Define the snapshot model before writing any quest conditions.

---

### Pitfall 11: Quest Detection False Positives (Trivial Hacks Complete Quests)

**What goes wrong:** A quest asks the user to "make a bass line with at least 4 different notes." The detection checks `distinct note values in phrase > 3`. A user who doesn't understand the quest opens the phrase screen, randomly hits 4 different keys, completes the quest, and learns nothing. Worse, they cannot tell the Academy that their solution doesn't sound like a bass line — and the XP reward reinforces the random behavior.

**Why it happens:** Quest detection based purely on structural properties (count of X, value of Y) is easy to implement and easy to fool. Musical-quality properties (does this sound like a bass line?) are hard or impossible to detect programmatically.

**Consequences:** Users game the system, collect XP without learning, and the Academy fails its pedagogical goal. Community perception: "the Academy is a checkbox exercise."

**Warning signs:**
- Quest completion rate is near 100% for every quest — suspiciously high
- Users describe completing quests "without really doing anything"
- Sessions are very short: users complete a quest cluster in 30 seconds each

**Prevention:**
- Layer detection: structural check (4 distinct notes) PLUS a playback step where the Academy plays back the user's phrase and asks "does this sound like what the quest described? Press YES to confirm." This adds a self-assessment gate that makes trivial completion conscious.
- Avoid quests whose structural completion criterion is a superset of the learning goal. "4 distinct notes in a bass range (C1–C3)" is harder to trivially complete than "4 distinct notes anywhere."
- Cap the trivial-completion path: if a user completes 3 quests in under 30 seconds each, show a "Try really playing it" nudge.

**Phase mapping:** Academy quest design and detection implementation. Write the detection specification alongside the quest content.

---

### Pitfall 12: Academy Pulling Users Away From Actually Using the M8 Freely

**What goes wrong:** The Academy is engaging enough that users spend all their time in it and never develop the habit of opening the M8 instrument editor and improvising freely. The Academy inadvertently teaches "follow instructions" rather than "explore and discover." After completing the Academy, users feel finished rather than inspired to create.

**Why it happens:** Visual novel framing and quest completion mechanics create a "completion" mental model. Users optimise for finishing chapters, not for musical understanding. The Academy becomes the game, and the M8 becomes the game's UI.

**Consequences:** Users who complete the Academy do not become M8 power users — the core product goal fails.

**Warning signs:**
- User sessions during Academy chapters show zero time on free-play screens (Song, Phrase, Instrument) outside quest prompts
- After Academy completion, session length drops significantly
- User reviews say "I finished the Academy" as a terminal statement, not "the Academy taught me and now I love making tracks"

**Prevention:**
- After each quest completion, before granting XP, require the user to do one free-play action: "Now try it your way for 30 seconds. Tap anywhere to continue." No quest detection — just a timer and a prompt.
- "Final Jam" chapter must be entirely open-ended: no quest conditions, just a narrative character saying "show me what you've got."
- Add a "free explore" button visible from within the Academy at all times that drops the user to the M8 Song screen with Academy context suspended (not destroyed).
- Consider making XP rewards partially contingent on time spent in free-play mode, not only quest completion.

**Phase mapping:** Academy design phase. Build the free-play interstitials into the quest format from the beginning — retrofitting them is expensive.

---

### Pitfall 13: Progression Tied to Streaks / Anxiety Instead of Learning

**What goes wrong:** The Academy implements a daily streak: "You've practiced 7 days in a row!" Missing a day resets the streak. Users begin to feel anxious about the streak rather than excited about the M8. A user who misses one day due to travel concludes "I've lost my progress" even though their knowledge hasn't changed. They may not return.

**Why it happens:** Streak mechanics are borrowed from Duolingo and other language apps where daily practice has genuine retention value. For a creative music tool, the equivalent of "daily practice" is making music, not completing Academy quests. Streak mechanics that punish gaps apply loss aversion to a creative hobby — which 2024 gamification research consistently identifies as a demotivator (Sage JCIE 2025, BJED 2024).

**Warning signs:**
- Users open the app just to keep a streak, do the minimum quest, and close
- Qualitative feedback mentions "anxiety about losing progress"
- Streak resets correlate with app uninstalls

**Prevention:**
- Do not implement streaks. Full stop. This project does not need them.
- Use cumulative XP and chapter unlocks — progress never goes backward. A user can return after two weeks and continue exactly where they left off.
- If a time-based mechanic is wanted, make it positive: "You've been on the M8 for 3 weeks" (total days, not consecutive). Celebrate presence, not compulsion.
- Lock-and-key progression (new character appears when chapter is complete) is more appropriate than streak-based gating.

**Phase mapping:** Academy design phase. Decide "no streaks" before any persistence model is designed, so the schema never includes streak counters.

---

### Pitfall 14: UI Patterns That Confuse Academy Mode With Live M8 Playing

**What goes wrong:** The Academy overlay partially obscures the M8 display. A user in an Academy quest sees the drum phrase screen with a quest UI chrome on top. They try to press a note key to input a drum hit — but the key is intercepted by the Academy's "confirm" action. Or they tap the M8 display thinking they are editing freely, but a tap navigates the Academy visual novel forward. The interaction model of the two modes collides.

**Why it happens:** The Academy is designed as a "mode on top of the emulator." The M8 emulator input system (`input/` directory) handles key routing globally. If Academy mode registers the same key handlers without clearing the emulator's handlers, both receive the event. The result is unpredictable: the user's Edit key edits the M8 phrase AND confirms the Academy dialog simultaneously.

**Consequences:** First-time users cannot tell whether they are "in the Academy" or "on the M8." Trust in both systems erodes.

**Warning signs:**
- Test users ask "am I controlling the M8 or the Academy right now?" more than once per session
- A key press during an Academy quest produces an unexpected M8 state change
- The Academy "back" action and the M8 "back" action (OPT button) trigger simultaneously

**Prevention:**
- Define a strict input ownership model: either the Academy owns all input, or the M8 owns all input. No shared ownership. When the Academy is active, the M8 input handlers are suspended (not removed — just paused via a flag that `input/` routing checks before dispatch).
- Use a visually distinct full-screen Academy frame that clearly signals "you are in Academy mode." The M8 display, when visible inside Academy, must be visually framed as a "demo window" (border, label, reduced opacity controls) unless the quest explicitly hands control to the user.
- Add a persistent "EXIT ACADEMY" button in the Academy chrome that is always reachable and immediately returns to the M8's last screen state.
- Test with users who have never seen the app: can they tell within 5 seconds which mode they are in?

**Phase mapping:** Academy UI implementation phase. Resolve the input ownership model before building any Academy screens.

---

### Pitfall 15: Context Loss When Switching Modes During a Quest

**What goes wrong:** A user is midway through a quest ("open the instrument editor and change the waveform"). They get a phone call. When they return, the app has gone through an `onPause`/`onResume` cycle. The Academy quest state is restored, but the M8 emulator state has been reset (audio thread restarted, emulator re-initialised). The M8 is now on the Song screen with an empty session. The quest is asking the user to do something in an instrument editor that no longer has the context the quest set up.

**Why it happens:** The M8 emulator state (`M8Song`, cursor position, current screen) is not persisted across process lifecycle events — only the audio thread and in-memory state exist. The Academy quest progress is stored in DataStore (the natural choice given the existing `data/` infrastructure), but the emulator state that the quest depends on is ephemeral.

**Consequences:** Returning from a phone call or multitasking drops the user into a broken quest state with no recovery path. The user has to restart the chapter.

**Warning signs:**
- Any quest that sets up emulator state as a precondition is fragile across `onPause`
- The "resume Academy" button on the home screen brings back quest text but the M8 is in a different state
- Users report "the quest forgot what I was doing"

**Prevention:**
- Design quests so that the Academy can re-apply their setup state when resuming. Each quest must have a `setupEmulatorState()` function that the Academy calls on resume if it detects a context mismatch.
- Store a minimal "quest context snapshot" in DataStore alongside quest progress: the emulator screen, BPM, and relevant instrument index needed for the current quest. On resume, restore from snapshot before displaying the quest.
- Implement `onSaveInstanceState` / ViewModel `SavedStateHandle` coverage for the Academy's current quest position and the emulator's screen/cursor so the resume experience is seamless.
- Test explicitly: start a quest, lock the phone for 30 seconds, unlock, resume. The quest must be coherent.

**Phase mapping:** Academy persistence and lifecycle phase. This is highest-risk integration work — address it early, not in a follow-up sprint.

---

## Integration Pitfalls

---

### Pitfall 16: Academy Code Leaking Into Every M8 Screen

**What goes wrong:** The quest detection system needs to observe emulator state. The easiest implementation: add an `academyQuestChecker?.onScreenRendered(screen, song, instruments)` call to every screen render path in `M8Emulator.kt`. Six months later, `M8Emulator.kt` (already 1200 lines) has Academy callbacks in 20 places, and the emulator cannot be understood or tested without understanding the Academy.

**Why it happens:** Observer/callback injection is the path of least resistance when retrofitting a monitoring layer onto an existing system. It is easier than building a proper event bus or a state-diffing layer that runs outside the emulator.

**Consequences:** The emulator becomes untestable in isolation. Any Academy change requires understanding emulator internals. The emulator cannot be extracted or reused. Future maintainers cannot distinguish M8 emulation logic from Academy logic.

**Warning signs:**
- Any file in `emulator/` imports anything from `academy/`
- `M8Emulator.kt` has more than 2–3 Academy-related lines
- Removing the Academy requires surgical editing of the emulator

**Prevention:**
- The Academy observes a `StateFlow<QuestSnapshot>` produced by `M8ViewModel`, not by `M8Emulator` directly. `M8ViewModel` already has access to all sequencer state; it can produce a snapshot each row advance without touching the emulator.
- Define a strict dependency rule: `emulator/` → nothing in `academy/`; `academy/` → `emulator/` read-only via ViewModel flows. Enforce with a lint rule or package-private visibility.
- Keep `M8Emulator.kt` oblivious to the Academy's existence.

**Phase mapping:** Academy architecture design phase. Define the dependency rule before writing the first Academy class.

---

### Pitfall 17: Adding Lifecycle Bugs to the Audio Thread

**What goes wrong:** Academy mode switching triggers ViewModel state changes (`_academyActive.value = true`). A Compose LaunchedEffect or coroutine collecting that flow inadvertently calls a method that acquires a lock or posts to the audio thread. The audio thread was already running, holds the Rust `ENGINE` Mutex, and the new call blocks waiting for the same lock. Priority inversion: the UI coroutine (low priority) holds something the audio thread (URGENT_AUDIO) needs.

**Why it happens:** `M8ViewModel` manages both the audio thread and the UI state flows. Adding Academy state flows to the same ViewModel creates shared mutable state that both UI code and audio-path code touch. It is tempting to add an `if (academyActive) { questChecker.check(snapshot) }` inside the audio loop — one line, seems harmless.

**Consequences:** Audio dropouts every time the Academy is opened or closed. Intermittent; hard to reproduce on a developer's high-end device.

**Warning signs:**
- Audio drops when tapping the Academy icon
- Audio drops when completing a quest and the XP animation plays
- The bug disappears in release builds (due to JIT differences) and reappears in debug

**Prevention:**
- The audio render loop in `M8ViewModel` must remain a closed system: it writes sequencer state via `@Volatile` fields and posts PCM to `AudioTrack`. It reads nothing from the Academy. Period.
- Quest snapshot production happens after the audio chunk is written, not during: post the snapshot to a `Channel` at the end of each row advance, and let a separate coroutine drain the channel and call quest detection.
- Create an explicit "audio thread touchpoint audit" checklist: before any PR that touches `M8ViewModel` is merged, the author must list every new operation that the audio thread calls or reads.

**Phase mapping:** Academy integration phase. Enforce the audit process from the first Academy PR.

---

### Pitfall 18: Persistence Layer Choices That Force a Migration Later

**What goes wrong:** Academy quest progress is stored in DataStore Preferences (the existing infrastructure in `data/`). This works for simple key-value data (which chapter, which quest, total XP). But the Academy requires structured data: per-chapter quest completion status, partial quest state, quest context snapshots, narrative branch choices (which character dialogue path the user took). Shoving structured data into Preferences as JSON strings works until a format change requires a migration — at which point there is no migration infrastructure.

**Why it happens:** `data/` already uses DataStore Preferences. It is a 5-minute start. The complexity of the Academy's state is underestimated at implementation time.

**Consequences:** A v1.1 quest format change requires either a migration function (which DataStore Preferences does not support natively) or a "reset all Academy progress" forced on users. Both are bad.

**Prevention:**
- Model Academy progress as structured data from day one. Use DataStore with a Protocol Buffer schema (DataStore Proto, not DataStore Preferences) for Academy state. This gives typed migrations via `Migration` objects.
- Alternatively, use Room (SQLite) for Academy progress. Room is already a transitive dependency (via Jetpack lifecycle). It supports typed schema migrations. For complex relational data (quest state trees), it is the right choice.
- Decide: DataStore Proto for simple progression + Room for quest snapshots. Do not use DataStore Preferences with JSON strings.
- Define the schema in week 1 of Academy development and treat it as a public API — any structural change requires a migration test.

**Phase mapping:** Academy architecture design phase. The schema decision must happen before any Academy data is written to disk.

---

### Pitfall 19: Over-Investing in a Game Engine Before Validating the Concept

**What goes wrong:** The Academy visual novel shell is built using a purpose-built game engine (Unity, Godot, or a Compose-based custom narrative engine). Significant engineering effort (2–3 weeks) goes into the engine, character animation system, dialogue trees, and scene management. After the first playtest, it is discovered that users skip all the visual novel content and just want to do the M8 tasks. The engine investment is stranded.

**Why it happens:** "Visual novel RPG" sounds like it needs an engine. The temptation is to build the infrastructure before validating whether the narrative layer adds value.

**Consequences:** Wasted engineering time on infrastructure that may not be the right form factor. The Academy ships late or ships without its core M8 quest functionality because the engine took all the time.

**Prevention:**
- Build the Academy narrative shell as the simplest possible Compose implementation first: a full-screen card with a character image (static PNG), dialogue text, and a Next button. Hardcode 3 exchanges. Put it in front of test users.
- If users engage with the narrative and want more, invest in a proper dialogue system. If they skip it to get to the quest, cut the narrative investment significantly.
- The quest detection and M8 integration are the hard parts and the core value. The visual novel shell is a UX layer on top — it should be validated before it is engineered.
- Do not introduce a game engine (Unity, Godot) into the Android project. The dependency surface, APK size, and build complexity are not justified. Compose can deliver everything needed for a basic visual novel.

**Phase mapping:** Academy proof-of-concept phase. The first Academy milestone should be: a quest that works end-to-end (setup → M8 task → detection → completion) with placeholder UI, not a polished visual novel with placeholder quest detection.

---

## Phase-Specific Warnings

| Phase Topic | Likely Pitfall | Mitigation |
|-------------|---------------|------------|
| First DSP commit | Aliasing regression (Pitfall 1) | Golden-render tests must exist before the commit |
| Filter character work | NaN explosion / stability (Pitfall 2) | Analytical stability check + `debug_assert!` guards |
| Note tuning changes | .m8s compat break (Pitfall 3) | Treat note-freq formula as immutable contract |
| Sparse arrangements | Denormal CPU spike (Pitfall 4) | Add flush guard to reverb/delay before going to prod |
| Live param changes / Academy | Zipper noise (Pitfall 5) | Add per-voice param smoothing before Academy ships |
| Any new feature in ViewModel | Audio thread lifecycle bug (Pitfall 17) | Touchpoint audit checklist on every PR |
| Academy quest writing | Chore gamification (Pitfall 8) | Quest guidelines reviewed before content is authored |
| Academy architecture | Code leak into emulator (Pitfall 16) | Dependency rule enforced from first class |
| Academy data design | Forced migration later (Pitfall 18) | DataStore Proto or Room, decided in week 1 |
| Academy quest detection | False negatives (Pitfall 10) | Atomic QuestSnapshot model before any quest code |
| Academy lifecycle | Context loss on resume (Pitfall 15) | Explicit resume test in acceptance criteria |
| Academy MVP scope | Engine over-investment (Pitfall 19) | Quest-first, narrative-second ordering |

---

## Sources

- Android audio latency and priority inversion: [Avoid priority inversion | Android Open Source Project](https://source.android.com/docs/core/audio/avoiding_pi), [Design for reduced latency | Android Open Source Project](https://source.android.com/docs/core/audio/latency/design)
- Denormal numbers in DSP: [Floating point denormals | EarLevel Engineering](https://www.earlevel.com/main/2019/04/19/floating-point-denormals/), [Resolving denormal floats once and for all | JUCE Forum](https://forum.juce.com/t/resolving-denormal-floats-once-and-for-all/8241), [Rust denormal issue #123123](https://github.com/rust-lang/rust/issues/123123)
- Rust FFI / panic at JNI boundary: [Item 18: Don't panic — Effective Rust](https://effective-rust.com/panic.html), [FFI — The Rustonomicon](https://doc.rust-lang.org/nomicon/ffi.html), [jni crate docs](https://docs.rs/jni/latest/jni/)
- Gamification motivation research: [Gamification is not Working: Why? — Sage 2025](https://journals.sagepub.com/doi/abs/10.1177/15554120241228125), [Gamification in 2024 | UX Lessons & Design Pitfalls](https://www.himumsaiddad.com/insights/gamification-trends-2024), [Duolingo Case Study 2025](https://www.youngurbanproject.com/duolingo-case-study/)
- Streak psychology: [Streaks and Milestones for Gamification in Mobile Apps | Plotline](https://www.plotline.so/blog/streaks-for-gamification-in-mobile-apps/), [What makes goal-setting apps motivate or backfire | Cornell Chronicle](https://news.cornell.edu/stories/2025/12/what-makes-goal-setting-apps-motivate-or-backfire)
- PolyBLEP oscillator aliasing: [Making Audio Plugins Part 18: PolyBLEP Oscillator](https://www.martin-finke.de/articles/audio-plugins-018-polyblep-oscillator/), [Oscillator antialiasing — KVR Audio](https://www.kvraudio.com/forum/viewtopic.php?t=437116)
- Codebase-specific sources: `.planning/codebase/CONCERNS.md`, `.planning/codebase/ARCHITECTURE.md`, `.planning/codebase/TESTING.md`, `m8-synth/src/lib.rs`, `app/.../M8ViewModel.kt`
