# m8droid

## What This Is

m8droid is an Android app that emulates the Dirtywave M8 tracker locally (with a Rust DSP backend and an optional remote mode that talks to real M8 hardware or a Python server). This milestone adds a **gamified learning mode** — "M8 Academy" — and tightens audio quality so the existing emulator sounds closer to the real M8.

## Core Value

**A polished way to learn and play the M8 on Android — the sound feels right, and a built-in RPG makes learning the tracker actually fun.**

If everything else fails, these two things must work: the emulator sounds good, and the Academy teaches M8 skills through play.

## Requirements

### Validated

<!-- Inferred from existing codebase (.planning/codebase/). These already work and stay. -->

- ✓ Local M8 emulation on Android — `M8Emulator.kt` (~1200 lines) — existing
- ✓ Rust DSP synth via JNI — `m8-synth/src/lib.rs` (~615 lines), 8 voices, PolyBLEP/SVF/ADSR/delay/reverb — existing
- ✓ Kotlin DSP fallback — `M8Synth.kt` — existing
- ✓ 8-screen M8 UI rendered to 320×240 display buffer — `M8DisplayBuffer.kt` + Compose UI — existing
- ✓ `.m8s` / `.m8i` file parsing — `M8sParser.kt` / `M8iParser.kt` — existing
- ✓ Remote mode: WebSocket client connecting to Python bridge or headless emulator — `M8WebSocketClient.kt`, `server/bridge.py`, `server/m8_emulator.py` — existing
- ✓ Opus audio streaming from server — `OpusDecoder.kt`, `server/audio_stream.py` — existing
- ✓ Track browser/downloader from existing sources — `app/.../browse/` — existing
- ✓ Keyboard + gamepad input mapping — `app/.../input/` — existing
- ✓ Interactive tutorial — `app/.../tutorial/` — existing (kept as opt-in classic path)

### Active

**Sound Quality**

- [ ] Oscillator fidelity closer to real M8 (waveforms / aliasing / macrosynth modes)
- [ ] Filter character closer to real M8 (SVF tuning, possibly additional filter modes)
- [ ] Built-in FX (reverb, delay, chorus) closer to real M8 character
- [ ] Eliminate audio dropouts, clicks, and crackles during playback

**M8 Academy — Gamified Learning Mode**

- [ ] New top-navigation entry: game icon alongside settings / help / download
- [ ] Tapping the icon launches M8 Academy in its own mode without disturbing the live emulator state
- [ ] Visual-novel narrative shell with a cast of characters, one per M8 subsystem (drums, synths, sampling, FX, song structure)
- [ ] Chapter structure: Drums → Synths → Sampling → FX → Song Structure → Final Jam
- [ ] Quest system that gives real M8 tasks ("make a 4-step drum phrase with swing > 50%") and detects completion in the real M8 UI
- [ ] Mini-games between chapters that drill specific concepts (pattern matching, FX command recall, sample slicing, etc.)
- [ ] XP / progression / unlock feedback so the player feels they're growing
- [ ] Academy progress persists between sessions
- [ ] Short onboarding so new users can open the Academy and start playing within ~30 seconds

### Out of Scope

- **Replacing the Rust synth with Teensy ARM emulation** — the existing Rust synth works; improving it is far cheaper than porting/emulating the Teensy M8 headless firmware, and we'd rather invest that effort in the Academy
- **Community track upload / sharing backend** — user was overstating earlier; existing download paths in `browse/` are sufficient for this milestone
- **Extending `browse/` download sources** — already works; no new sources needed
- **Accounts / social features / likes / comments** — out of scope, no user-to-user interaction in this milestone
- **New tutorial from scratch** — the existing `tutorial/` stays; the Academy is added as an opt-in richer path rather than replacing it
- **Song export / save back to .m8s** — a real gap noted in CONCERNS.md but not in this milestone's scope
- **TLS / auth for the Python bridge** — security hardening deferred; not blocking this milestone

## Context

- **Brownfield project.** The codebase is already substantial — mixed Kotlin (Android app), Rust (DSP synth compiled to `libm8_synth.so` via JNI), and Python (`server/` for remote mode and audio streaming). Full codebase map lives in `.planning/codebase/` (ARCHITECTURE, STRUCTURE, STACK, INTEGRATIONS, CONVENTIONS, TESTING, CONCERNS).
- **Zero test infrastructure** (see `.planning/codebase/TESTING.md`). Any meaningful change to the synth or Academy wants at least some tests added — parser fuzzing and synth golden-render tests are highest leverage.
- **Audio-thread fragility** — sequencer state is mutated across threads without locks in places; any sound-quality work should be aware of the concurrency model in `M8ViewModel.kt` and `M8DisplayBuffer.kt`.
- **Known M8 community wishes** captured in `INTERESTING.md` at repo root — useful reference for "what makes the sound feel right" and for future scoping beyond this milestone.
- **M8 firmware is open source** (Dirtywave publishes headless firmware binaries) — relevant as ground-truth reference for oscillator / filter / FX behavior even though we're not emulating the Teensy directly.

## Constraints

- **Tech stack**: Android / Kotlin / Compose for UI; Rust for DSP; Python for server-side. Do not introduce a new language or framework without explicit discussion.
- **Non-destructive**: do not break existing local-mode or remote-mode playback while adding Academy or tuning sound.
- **Performance**: audio render must stay glitch-free on mid-range Android hardware; Academy UI must not stall the audio thread.
- **Branch**: all work for this milestone goes on `main-rpg`.
- **No backend services** added in this milestone — everything ships in the app (plus the existing `server/` code path for remote mode).

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Improve Rust synth in place, do not replace with Teensy emulation | Emulator already works; porting Teensy firmware is months of work; sound-quality gains can come from tuning existing DSP | — Pending |
| M8 Academy is a separate top-nav mode, not a replacement for the existing tutorial | Preserves fast path for users who don't want a game; lets Academy be richer without gating first-run users | — Pending |
| Academy style: visual novel + quests hooked into real M8 UI + between-chapter mini-games | Best pedagogical fit per user selection — teaches concepts in context rather than in isolation | — Pending |
| No community upload / backend | User clarified existing download already covers the "load tracks from internet" need | — Pending |
| All milestone work on `main-rpg` branch | User instruction | ✓ Good |

---
*Last updated: 2026-04-16 after initialization*
