# Feature Landscape

**Domain:** Android music-tracker emulator with gamified RPG learning mode + DSP sound-quality improvement
**Researched:** 2026-04-15
**Milestone scope:** M8 Academy (gamified learning) + Rust synth sound-quality improvements

---

## Part 1 — M8 Academy: Gamified Learning Features

### Table Stakes

Features that users of any learning app expect as baseline. If these are missing, the Academy feels broken, unpolished, or untrustworthy.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| **Persistent progress save** | Without this, users who close the app mid-chapter have lost their work; trust collapses immediately | Low | Can be a simple JSON/Room record of `(chapterId, questId, xp, unlocks)`. Key: save on every quest completion and on app backgrounding, not just chapter end |
| **Clear "what's next" signal** | Every learning app shows a single unambiguous next action. Users won't dig to find their place | Low | A highlighted "Continue" CTA on Academy home screen pointing to the exact next incomplete quest |
| **Abandon / resume quest at any time** | Users get interrupted mid-quest on mobile constantly. No way to exit without losing state = rage quit | Low–Med | Quest state (which conditions have been met so far) should snapshot; "Resume" restores in-progress conditions |
| **Chapter map / progress overview** | Users want to know where they are in the overall arc before committing to the next unit | Low | A 6-node chapter path (Drums → … → Final Jam) with completion checkmarks and locked-future-node visual |
| **Unambiguous quest instructions** | Tracker newcomers don't know M8 vocabulary ("phrase", "swing", "FX command"). Instructions must define terms inline | Med | Tap-to-expand glossary tooltips on unfamiliar terms within quest text |
| **Completion feedback / celebration** | After a quest succeeds, a satisfying moment (animation, sound, XP counter ticking up) confirms success | Low | Haptic + particle burst + XP number. Missing = users don't know if it worked |
| **Graceful failure feedback** | When the M8 UI watcher detects the task is NOT met, tell the user what's still wrong — not just "not yet" | Med | Diff between current state and goal: "Swing is 42% — you need > 50%" |
| **Skip/hint option for stuck users** | Users stuck for too long will abandon the app, not the quest | Med | After N failed attempts, offer a "Show me" hint or a "Skip this for now" option (skip costs 0 XP, hint costs partial XP) |
| **Short onboarding (≤30 seconds to first quest)** | Standard in all 2026 learning apps; forced long intros cause drop-off | Low | Three-screen intro max: who are the characters, what is a quest, tap to start. No account required |
| **Works without audio playing** | Academy must be launchable without disrupting a song the user was editing | Med | Academy entry must not reset emulator state; quest watcher observes M8 emulator state passively |

### Differentiators

Features that separate a delightful Academy from a generic checklist tutorial. These are what get users talking and returning.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| **Visual-novel character cast with personality** | Characters make the M8 subsystems memorable and human. "Beatrix teaches drums" is more memorable than "Chapter 1: Drums" | Med | One character per chapter. Minimal sprite art. Character voice = consistent writing tone, not actual audio |
| **Quest conditions verified in the real M8 UI** | Learning happens in the actual tool, not a sandbox. The tracker muscle memory transfers. This is the key differentiator from any generic music tutorial | High | See "Quest detection" section below. Condition engine reads live emulator state (M8Emulator/M8Song) and compares to goal specification |
| **Between-chapter mini-games that drill concepts** | Breaks up the VN reading pacing; reinforces knowledge through active recall in a low-stakes game context | High | Examples: pattern-matching "which FX command does this?"; flashcard command recall; sequence-reconstruction puzzle |
| **Contextual M8 command reference panel** | While inside a quest, a slide-in panel shows only the commands relevant to that quest. Reduces need to leave the app to look things up | Med | Subset of M8 cheat-sheet filtered per chapter. Links to full reference optional |
| **XP and unlock system tied to real knowledge milestones** | Unlocking a mini-game or a cosmetic character reaction by completing a quest cluster creates genuine progression feel | Med | XP gates chapter unlock. Cosmetic unlocks (character expressions, UI palette) are LOW cost, HIGH perceived value |
| **Non-linear optional challenges** | After completing a chapter, offer bonus "expert challenges" for power users (e.g., "make the phrase work in 5/4 time") | Med | Clearly marked optional. No XP penalty for skipping |
| **Chapter-end "jam session" context** | After completing chapter content, drop the user into a small pre-built song skeleton they can immediately riff on, using the skills just taught | Med–High | Pre-authored `.m8s` snippet loaded into emulator; Academy overlay recedes; re-enter Academy when done |
| **Character dialogue that references what you just did** | After completing a quest, character says "Nice, you used the Hum command — that's exactly how I build basslines." Feedback feels personal | Med | Quest completion payload includes metadata the VN dialogue system uses for branch selection |
| **Adaptive hint threshold** | If a user nails a quest on first try, don't show the hint button next time. If they fail three times, proactively surface help | Med | Simple attempt counter per quest drives hint visibility logic |
| **Accessible text size and contrast** | Learning apps that fail accessibility feel amateur; WCAG 2.1 AA minimum is a baseline expectation in 2026 | Low | Jetpack Compose has built-in accessibility support; ensure VN text passes contrast checks |

### Anti-Features

Gamification tropes to explicitly avoid. These are well-documented patterns that create short-term engagement metrics at the cost of user trust and long-term retention.

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| **Punitive streak loss** | Losing a multi-day streak because of a single missed day creates anxiety and shame, not motivation. Users rage-quit or feel the app doesn't respect their life. Duolingo's streak mechanic is well-studied as both effective AND harmful | A "consistency band" (e.g., "played 5 of last 7 days") or a simple "last played" display. No punishing reset |
| **Forced chapter-by-chapter gating with no context** | Blocking users from any future content until chapter 1 is complete ignores users who already know drums basics | Show the full chapter map from day one, locked nodes grayed-out. Let users see what's coming |
| **Mandatory long onboarding before first payoff** | Every screen before the first "I did a thing!" moment is churn risk. Pushing a 5-screen character intro before any play is patronizing | Get to first quest in ≤3 taps. Full character backstory is optional lore |
| **XP inflation that feels meaningless** | Awarding 5000 XP for a trivial task and 5050 XP for a hard one makes the numbers feel like fake feedback | Keep XP integers small (1–10 per quest), or use named ranks instead of raw XP if numbers feel hollow |
| **"You failed, start over" with no state preservation** | Mobile users are interrupted. Restarting a multi-step quest because of a phone call is unfair | Checkpoint mid-quest; at minimum, remember which sub-conditions were already met |
| **Rewarding time-on-screen rather than mastery** | Some gamification systems inflate "engagement" by making tasks long rather than meaningful | Gate on skill demonstration, not time spent. Quests have a clear measurable condition (swing > 50%), not a timer |
| **Guilt-trip push notifications** | "You haven't practiced in 3 days 😢" maps to anxiety and notification-disabling, not return visits | Opt-in only; framed as opportunity, not shame ("New challenge unlocked" not "You're falling behind") |
| **Upsell / paywall mid-quest** | For this app there's no monetization model in scope, but if ads or prompts interrupt learning flow they permanently damage trust | Academy must be 100% free-to-complete with no mid-flow interruptions |
| **Overly chatty NPC dialogue with no skip** | Visual-novel dialogue is engaging for narrative moments, annoying when repeated for the 3rd retry of the same quest | Allow tap-to-skip all dialogue; skip state persists (don't re-show skipped intro dialogue) |
| **Binary pass/fail with no partial credit** | A quest with 3 conditions showing "FAILED" because 1 was missed discourages users who got 2 right | Show condition-by-condition status: "Swing ✓ · 4 steps ✓ · Phrase plays ✗" |

---

## Part 2 — Quest Detection Inside the Real M8 UI

### The Core Pattern

M8 Academy quests are verified by reading live emulator state from `M8Emulator` / `M8Song`, not from audio analysis or UI scraping. This is architecturally simpler and more reliable than microphone-based approaches (Yousician model) or pixel-watching (Synthesia model), because m8droid already owns the emulator state.

### How Other Apps Do It

| Approach | Product | How | Relevance to m8droid |
|----------|---------|-----|----------------------|
| **Audio pitch detection** | Yousician | Microphone listens to what instrument plays; pitch algorithm + timing alignment to expected score | Not applicable — M8 emulator generates audio programmatically; mic-based detection adds noise and platform fragility |
| **Falling-note display watcher** | Synthesia | App owns piano display; it renders the "falling notes" and detects key hits via MIDI or keyboard events | Closest analog: Synthesia owns the display loop. m8droid owns the emulator; Academy can observe emulator state directly |
| **Checklist self-report** | Most mobile language/music apps | User taps "I did it" button; no verification | Trivially gameable, feels hollow for a skill-building context |
| **State-inspection checkpoint** | Duolingo (lesson completion) | App owns all lesson content; completion is computed, not detected | Direct analog: Academy owns the quest spec; emulator state is the ground truth |
| **Achievement event hook** | Steam, Google Play Games | App fires `unlockAchievement()` at a point in code determined by developer logic | Same model — m8droid quest engine evaluates conditions after every emulator tick |

### Recommended Quest Detection Architecture for m8droid

Rather than a complex watcher daemon, a quest condition evaluator that polls/subscribes to `M8Song` / `M8Emulator` state on each sequencer tick or user action is the right model:

```
Quest condition spec (JSON/data class):
  - type: SWING_ON_PHRASE / STEP_COUNT / FX_COMMAND_USED / INSTRUMENT_TYPE / etc.
  - target: { track: 0, phrase: any, threshold: 50, comparator: GT }

Condition evaluator:
  - Called on: sequencer step tick, instrument edit save, phrase save
  - Reads: M8Song.currentPhrase, M8Song.tracks[n], M8Emulator.swingForPhrase(p)
  - Returns: ConditionResult(met: Boolean, progress: String)  // e.g., "Swing 42% — need > 50%"

Quest engine:
  - Holds list of conditions; all must be met for quest completion
  - On all-met: fires completion event → Academy overlay animates, XP awarded
```

This approach:
- Requires no new Android permissions
- Works offline
- Does not touch the audio thread (read-only, on UI/emulator tick)
- Produces the "what's still missing" diff for graceful failure feedback

### Condition Types Needed Per Chapter

| Chapter | Example Quest | Condition Type | Complexity |
|---------|--------------|----------------|------------|
| Drums | "Make a 4-step kick phrase with swing > 50%" | STEP_COUNT + SWING_GT + INSTRUMENT_CATEGORY | Med |
| Synths | "Use the Macrosynth CSAW shape on track 2" | INSTRUMENT_TYPE + SYNTH_SHAPE | Low |
| Sampling | "Slice a sample into at least 4 regions" | SAMPLE_SLICE_COUNT_GTE | Med |
| FX | "Add a delay FX command to a note (DEL)" | FX_COMMAND_PRESENT(DEL) | Low |
| Song Structure | "Chain at least 3 unique phrases in a song row" | CHAIN_UNIQUE_PHRASE_COUNT_GTE | Med |
| Final Jam | Composite multi-condition | ALL_ABOVE | High |

---

## Part 3 — What Makes M8/Tracker Tutorials Actually Work

### What Existing M8 Tutorials Get Right

Community feedback from M8 forums (Elektronauts, MOD WIGGLER, Disquiet, Sound On Sound) converges on these points:

- **The manual is unusually good.** Dirtywave's operation manual is well-structured; users who read it seriously unlock the tracker fast. This suggests in-app learning can lean on the same vocabulary without reinventing it.
- **Muscle memory is the real unlock.** Users report that the M8 "clicks" once shortcut combos are in muscle memory — not when they're read, but when they're repeated. Implication: quests should have users perform actions multiple times, not just once.
- **Navigation is the first hurdle.** Newcomers are confused by the grid-of-screens layout (Song → Chain → Phrase → Instrument). The Academy chapter flow already mirrors this (Drums = first phrase-level work), which is the right call.
- **The tracker workflow is alien to DAW users.** Every "obvious" DAW concept (track, region, clip) maps to different M8 vocabulary. Explicit bridging ("In M8 a 'chain' is like a track lane in Ableton") reduces confusion.

### What Existing M8 Tutorials Get Wrong

- **Text-only documentation fails at showing timing.** The manual describes swing but doesn't let you hear 0% vs 60% swing side-by-side. The Academy's audio-live context is a genuine advantage — quest completion should play back the phrase so the user hears the result.
- **Community tutorials assume hardware.** Most YouTube/forum tutorials assume a physical M8 or a Teensy running headless firmware. They skip Android-specific context. The Academy can fill this gap directly.
- **Command recall is under-drilled.** The FX command list (40+ commands: VOL, PAN, CHO, DEL, REV, RET, etc.) is where beginners stall most. A mini-game that drills command → effect association directly addresses this known weak point.
- **No safe sandbox for experimentation.** Community advice is "load someone else's song and explore." The Academy's pre-authored skeletal songs for each chapter are a better structured version of this.
- **Setup friction gates learning.** For headless/m8c users, initial setup complexity causes abandonment before the first note. m8droid's zero-setup local emulation removes this entirely — a fact worth highlighting in Academy onboarding.

### Specific Tracker Learning Patterns That Work

| Pattern | Why It Works | Academy Application |
|---------|--------------|---------------------|
| Immediate audible feedback on every action | Learner hears cause-effect in milliseconds | Quest completion triggers phrase playback |
| Constraint-based exercises | Limitations force creative problem-solving (e.g., "make a groove with only 4 steps") | Quest constraints ARE the learning mechanism |
| Contextual reference visible while working | Cheat sheet on the same screen as the task | Slide-in command panel during quests |
| Spaced repetition of commands | FX commands appear in multiple chapters | Mini-game reinforcement between chapters |
| Teach by showing a finished example | "Here's a phrase with swing, now you make one" | Character demonstrates; user replicates |

---

## Part 4 — Sound Quality: Features Users Notice Most

### What Users Perceive First (Salience Ranking)

Based on M8 community discussions (MOD WIGGLER, KVR, INTERESTING.md community wishes) and DSP literature, perceptual salience descends in this order:

| Rank | Artifact / Feature | Perceived As | Root Cause in Current Synth |
|------|--------------------|--------------|------------------------------|
| 1 | **Oscillator aliasing** | Harsh metallic edge on high notes; "sounds digital, not M8-digital" | PolyBLEP is already present but may need tuning; at high pitches even PolyBLEP leaks |
| 2 | **Filter resonance character** | Self-oscillation ceiling, ladder vs. state-variable "color" | Current SVF is correct topology but resonance curve / saturation at peak may differ from M8 hardware |
| 3 | **Reverb tail naturalness** | Metallic flutter, abrupt tail cutoff, or "swimming pool" character | Current reverb algorithm (likely basic Schroeder/FDN) may not match M8's shimmer reverb character |
| 4 | **Delay feedback saturation** | Tape-like warmth vs. pristine digital echo | Delay feedback path probably has no soft saturation; M8 hardware has slight analog character |
| 5 | **Macrosynth shape accuracy** | Missing or incorrectly-parameterized Braids-derived shapes | 44 Macrosynth shapes require per-shape calibration; any shape that sounds wrong is immediately obvious |
| 6 | **Audio dropout / clicks** | Intermittent ticks and pops during playback | Race conditions in sequencer state (already documented in CONCERNS.md) |
| 7 | **ADSR shape accuracy** | Percussive sounds decaying too fast/slow; pads blooming oddly | Exponential vs linear curves; release tail behavior |

### Specific Improvements That Will Have High Perceived Impact

**Oscillator Aliasing (High Priority)**
- At sample rates of 44.1–48 kHz, PolyBLEP provides good but not perfect alias rejection above ~8 kHz
- 2x oversampling in the oscillator stage (render at 88.2/96 kHz, downsample) is the standard fix and is compute-feasible on modern Android mid-range hardware
- Alternative: minBLEP tables for common waveforms (saw, square, tri) — higher memory, higher accuracy, lower CPU than oversampling
- Confidence: HIGH (well-established DSP literature, KVR community consensus)

**Filter Resonance Tuning (Medium Priority)**
- The SVF topology is correct; the issue is likely the resonance-to-Q mapping and whether there is soft saturation in the feedback path
- M8 hardware filter has a slight saturating character at high resonance; a `tanh()` or polynomial soft-clip on the feedback path adds this character without changing the topology
- This is one of the most noticeable differences between "sounds digital" and "sounds like M8"
- Confidence: MEDIUM (based on community descriptions; exact M8 hardware filter circuit not public)

**Reverb Tail / Shimmer Reverb (Medium Priority)**
- M8 firmware added a "shimmer" reverb mode; if the Rust synth reverb predates this, it may lack the pitch-shifted feedback layer
- Shimmer reverb = standard FDN/plate reverb + pitch-shifting in the feedback path (typically +1 or +2 octaves)
- Adding shimmer as an additional reverb mode addresses a specific named community wish
- Confidence: MEDIUM (shimmer reverb existence confirmed from firmware changelog and community; implementation effort is medium)

**Delay Saturation (Low-Medium Priority)**
- Adding a subtle `tanh()` on delay feedback creates "tape delay" warmth
- Single line of Rust code in the delay feedback path; very high impact-to-effort ratio
- Confidence: HIGH (standard technique)

**Audio Dropout Elimination (High Priority, Different Character)**
- Not a DSP accuracy issue — an architecture issue
- The race condition in sequencer state mutation (CONCERNS.md items 4, 6) can cause glitches audible as clicks
- Lock-free ring buffer between sequencer tick and audio render, or read-only snapshots passed to audio thread, are the fix paths
- Must be addressed before or alongside DSP tuning; tuned DSP that still clicks is worse than slightly-off DSP that is clean
- Confidence: HIGH (directly documented in CONCERNS.md)

---

## Feature Dependencies

```
Quest detection engine
    └─► requires: read-access to M8Emulator/M8Song state (already available)
    └─► requires: condition spec data model (new, simple)
    └─► required-by: all Academy quests

Persistent progress save
    └─► requires: Room database or SharedPreferences schema (new, low effort)
    └─► required-by: resume, chapter map, XP display

Chapter map UI
    └─► requires: persistent progress save
    └─► required-by: chapter unlock flow

Mini-games
    └─► requires: chapter completion events
    └─► independent of: quest detection engine (mini-games are self-contained Activities/screens)

Visual-novel shell
    └─► requires: chapter/quest navigation model
    └─► independent of: M8 emulator state (dialogue is scripted, not reactive)

Academy top-nav entry
    └─► requires: non-destructive emulator state isolation (must not disturb active song)
    └─► required-by: everything Academy

Oscillator oversampling
    └─► requires: Rust synth changes + JNI layer parity
    └─► independent of: Academy

Filter saturation tweak
    └─► requires: Rust synth SVF code review
    └─► independent of: oversampling (can ship separately)

Dropout fix
    └─► requires: concurrency audit of M8ViewModel / M8Song (see CONCERNS.md)
    └─► must ship before: audio quality features are perceived correctly
```

## MVP Recommendation

For the Academy MVP, prioritize in this order:

1. **Quest detection engine** — the core of Academy; without it, quests are checklist theater
2. **Persistent progress save** — mobile-first requirement; must be in v1
3. **Clear next step + chapter map** — navigation and orientation
4. **Abandon/resume quest** — mobile interruption reality
5. **Completion celebration feedback** — motivational loop closure
6. **VN shell with at least one character** — Drums chapter character minimum

Defer to post-MVP:
- Mini-games (high complexity, not on critical path to "Academy teaches M8 skills")
- Non-linear bonus challenges
- Adaptive hint threshold (start with fixed attempt count)
- Second through sixth chapter characters (ship chapter 1 complete, others in follow-on)

For sound quality MVP:
1. **Dropout elimination** (concurrency fix) — must ship first; affects all other quality perception
2. **Delay saturation** (one-line change, high impact-to-effort)
3. **Filter resonance saturation** (medium effort, high perceived difference)
4. **Oscillator oversampling** (medium effort, addresses most-visible aliasing)

Defer to post-MVP:
- Shimmer reverb (medium-high effort, nice but not blocking)
- Macrosynth per-shape calibration (high effort, long tail)

---

## Sources

- [Dirtywave M8 community tips — pauley-unsaturated GitHub](https://github.com/pauley-unsaturated/DirtyWave-M8-Tips) — MEDIUM confidence
- [Sound on Sound M8 Model:02 review](https://www.soundonsound.com/reviews/dirtywave-m8-model02) — MEDIUM confidence
- [Elektronauts M8 Tracker thread](https://www.elektronauts.com/t/m8-tracker-part-2/243500/850) — MEDIUM confidence
- [MOD WIGGLER M8 thread](https://www.modwiggler.com/forum/viewtopic.php?t=260324) — MEDIUM confidence
- [KVR Audio — PolyBLEP oscillator discussion](https://www.kvraudio.com/forum/viewtopic.php?t=437116) — HIGH confidence (DSP practitioner community)
- [KVR Audio — aliasing suppression in filter feedback](https://www.kvraudio.com/forum/viewtopic.php?t=605221) — HIGH confidence
- [Duolingo gamification case study — Trophy](https://trophy.so/blog/duolingo-gamification-case-study) — MEDIUM confidence
- [Gamification dark patterns — DarkPattern.games](https://www.darkpattern.games/) — MEDIUM confidence
- [Gamification for Good: Addressing Dark Patterns in Gamified UX Design](https://www.researchgate.net/publication/339487229_Gamification_for_Good_Addressing_Dark_Patterns_in_Gamified_UX_Design) — HIGH confidence (academic)
- [Yousician — how note detection works](https://support.yousician.com/hc/en-us/articles/201576782-Guitar-sound-recognition-issues) — MEDIUM confidence
- [Disquiet — M8 Headless Cheat Sheet (2025)](https://disquiet.com/2025/08/12/m8-headless-cheat-sheet/) — MEDIUM confidence
- [OpenMPT Tracker Handbook — Beginners](https://resources.openmpt.org/tracker_handbook/page/Beginners.htm) — MEDIUM confidence
- [M8 Firmware changelog — shimmer reverb](https://github.com/Dirtywave/M8Firmware/blob/main/changelog.txt) — HIGH confidence (official)
- m8droid INTERESTING.md, CONCERNS.md, ARCHITECTURE.md — HIGH confidence (first-party codebase)
