# STATE — m8droid: Sound Quality + M8 Academy

*Project memory. Updated at every phase boundary and significant decision.*

---

## Project Reference

**Core value:** A polished way to learn and play the M8 on Android — the sound feels right, and a built-in RPG makes learning the tracker actually fun.
**Branch:** main-rpg
**Milestone:** Sound Quality + M8 Academy
**Roadmap:** `.planning/ROADMAP.md`
**Requirements:** `.planning/REQUIREMENTS.md` (52 v1 requirements)

---

## Current Position

**Current phase:** 1 — Infrastructure
**Current plan:** Not yet planned (`/gsd:plan-phase 1`)
**Status:** Not started

```
Phase 1 [Infrastructure]          ░░░░░░░░░░  0%
Phase 2 [Sound Quality Core]      ░░░░░░░░░░  0%  (parallel with Phase 3)
Phase 3 [Academy Engine]          ░░░░░░░░░░  0%  (parallel with Phase 2)
Phase 4 [Academy UI Shell]        ░░░░░░░░░░  0%
Phase 5 [Content + Mini-games]    ░░░░░░░░░░  0%
─────────────────────────────────────────────────
Milestone                         ░░░░░░░░░░  0%
```

---

## Phase Summary

| Phase | Goal | Reqs | Status |
|-------|------|------|--------|
| 1 | Safe test + snapshot foundation for both workstreams | 8 | Not started |
| 2 | Dropout-free, warmer, less aliased audio | 11 | Not started |
| 3 | Quest engine + state machine + persistence (parallel w/ 2) | 9 | Not started |
| 4 | Playable Academy loop in-app with overlay | 11 | Not started |
| 5 | All chapters + mini-games + Plaits macrosynth | 13 | Not started |

---

## Accumulated Context

### Key Architectural Decisions

- **Academy observes emulator via snapshot only.** `EmulatorEventRepository` emits `SharedFlow<EmulatorSnapshot>` from the M8ViewModel 30fps render tick. `QuestEngine` and `AcademyViewModel` never import from `com.m8droid.emulator` or `com.m8droid.audio`. This is enforced at code-review level.
- **AppMode enum replaces `dawMode: Boolean`.** Three states: `M8`, `DAW`, `ACADEMY`. Wired in `M8App` and `MainActivity`. Audio thread lifecycle is unchanged by mode switch.
- **DataStore Proto for AcademyProgress.** Not Preferences + JSON strings. Schema decision must be locked in Phase 1 (INFRA-06) before any Academy data is written to disk — migration cost is high if changed later.
- **No game engine.** Compose Canvas + AnimatedContent covers all visual-novel and mini-game needs. Explicitly rejected: Korge, LibGDX, Ren'Py.
- **SND-11 (Plaits) deferred to Phase 5.** Highest-complexity DSP item. Can slip without blocking Academy launch. Time-boxed — if not completed in Phase 5, it becomes the only v1 carry-over.
- **Phases 2 and 3 are parallel.** They share zero runtime dependencies: Phase 2 is Rust DSP, Phase 3 is pure Kotlin JVM with no audio imports.

### Open Questions (as of roadmap creation)

- **DataStore Preferences vs. DataStore Proto** — INFRA-06 must resolve this in Phase 1, week 1, before any Academy data is written.
- **Quest-writing guidelines** — CONT-03 must be authored before chapters 2–6 content is drafted. Guidelines should be reviewed in Phase 5, before CONT-04..08 authoring begins.
- **Macrosynth mode enumeration** — the 8–10 "most-used" Braids/Plaits modes are not yet enumerated. Must be scoped before Phase 5 planning.
- **Mid-range baseline device** — no specific test device or underrun-acceptance threshold is defined. Needs to be named before Phase 2 success criteria can be verified (SND-12).
- **Chapter 1 narrative content** — character name (Beatrix?), personality brief, and opening dialogue exchanges needed before Phase 4 UI shell can be validated.

### Risk Register

| Risk | Severity | Mitigation |
|------|----------|------------|
| Plaits port scope unknown | High | Time-box in Phase 5; if not done, carry as only post-v1 item |
| Audio thread regression from DSP changes | High | Phase 1 golden tests gate all DSP merges |
| Academy pulling emulator state directly (breaking isolation) | High | Module boundary enforced: QuestEngine has zero emulator imports |
| DataStore schema migration if started with Preferences | Medium | Resolve in INFRA-06, Phase 1 |
| Quest content quality for chapters 2–6 | Medium | Quest-writing guidelines (CONT-03) reviewed before content authored |
| `kotlinx.fuzz` instability (0.1.x) | Low | Test-only dependency; easily swapped or dropped |

### Decisions Log

| Date | Decision | Rationale |
|------|----------|-----------|
| 2026-04-16 | 5 phases chosen over research's suggested 4 | ACAD engine vs. ACAD UI is a clean delivery boundary; UI shell can start after engine is tested without waiting for it to be pixel-perfect; gives clearer planning unit per `/gsd:plan-phase` invocation |
| 2026-04-16 | SND-11 (Plaits) moved to Phase 5 | Highest-complexity DSP item; must not block Academy launch; golden tests in Phase 2 protect against regression when it eventually merges |
| 2026-04-16 | ACAD-01..04 (AppMode + non-destructive entry) placed in Phase 3, not Phase 4 | AppMode refactor is engine-layer work (wiring through MainActivity); UI visibility of the mode is Phase 4 |

---

## Session Continuity

*Updated at the start of each session.*

**Last session:** 2026-04-16 — Roadmap created by roadmapper agent.
**Next action:** Run `/gsd:plan-phase 1` to decompose Phase 1 Infrastructure into an executable plan.
**Blockers:** None. Phase 1 has no upstream dependencies.

---

## Todos

- [ ] Name the mid-range Android baseline device for SND-12 stress test
- [ ] Enumerate the 8–10 Braids/Plaits modes before Phase 5 planning
- [ ] Confirm DataStore Proto vs. Preferences decision in Phase 1, week 1 (INFRA-06)
- [ ] Draft Chapter 1 character name + personality brief before Phase 4 UI validation

---

*State initialized: 2026-04-16*
*Last updated: 2026-04-16 after roadmap creation*
