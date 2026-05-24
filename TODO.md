# M8 Android Implementation TODO

Snapshot tag before this branch: `pre-note-phrase-work-2026-05-21`

Working branch: `feat/note-phrase-m8s-sampler-synth`

## Goal

Make M8droid feel like a real Android-native M8-style instrument: direct note input and phrase editing first, then real song loading, sample playback, stronger synth modes, saving, and MIDI. Keep this Android-first. No iOS port work.

## Stable Release Focus — next milestone

Freeze new feature expansion. The next goal is a stable, testable Android build that Daniel can use seriously without losing work.

Release candidate criteria:

- [x] Build and unit tests pass: `./gradlew testDebugUnitTest assembleDebug`.
- [x] Project save/load/export/share path exists for `.m8droid` files.
- [ ] Install latest APK on a real Android device and run a smoke session.
- [ ] Verify Academy fresh-song flow end-to-end on device: start Academy, complete Basics → Synths → Sampling quests, no blank chapter/fallthrough.
- [ ] Verify core tracker workflow on device: SONG → CHAIN → PHRASE → TABLE editing, preview row, playback, runtime FX audibly affect output.
- [ ] Verify file/project safety on device: new/open/save/autosave/duplicate/rename/delete/share, with dirty-confirm behavior and no accidental data loss.
- [ ] Verify external import path: shared/opened `.m8droid` project can be imported or clearly rejected with a useful message.
- [ ] Fix only crash/data-loss/usability blockers found during smoke testing.
- [ ] Cut a stable git tag and APK artifact once smoke passes.

## Post-Stable Direction — reliable, successful, still M8

After the first stable Android beta, keep the product focused on being a dependable M8-style instrument rather than a feature grab bag.

Principles:

- Reliability first: no lost work, no crashy file flows, predictable audio startup, recoverable project state.
- M8 semantics first: SONG/CHAIN/PHRASE/TABLE behavior should stay close to real M8 where it matters.
- Android-native comfort: touch affordances, share/import, autosave, backup, and guided onboarding should feel natural on a phone.
- Musical success: users should quickly make a loop, save it, reopen it, share it, and improve it.
- Add depth in thin vertical slices: each slice must include tests, APK, smoke checklist, and commit/push.

Post-stable roadmap:

1. Reliability hardening
   - [ ] Add project import from shared/opened `.m8droid` files.
   - [ ] Add crash-safe save writes: temp file + checksum + atomic replace where possible.
   - [ ] Add startup recovery UI when autosave/manual save conflict or last project fails to load.
   - [ ] Add missing sample/project warnings instead of silent failure.
   - [ ] Add a simple diagnostics/export-log action for bug reports.

2. Make-first-session successful
   - [ ] Add a short “make your first loop” guided path independent of the broader Academy.
   - [ ] Add tiny starter templates: beat, bassline, melody, sampler kit.
   - [ ] Add one-tap duplicate/extend pattern flow so users can build beyond a single phrase.
   - [ ] Add clearer success states: saved, exported, shared, project restored.

3. M8 tracker depth
   - [ ] Finish higher-value runtime FX parity before chasing obscure commands.
   - [ ] Improve table/parameter-lock behavior for the commands users actually hear immediately.
   - [ ] Add groove timing at scheduler so imported songs feel less flat.
   - [ ] Add modulation block parsing/application for downloaded/imported instruments.

4. Sound quality and instruments
   - [ ] Improve sampler slice mode and loop crossfade.
   - [ ] Improve MacroSynth/WAVSYNTH mappings with documented reference patches.
   - [ ] Add per-instrument mixer chain basics: drive, filter/EQ-ish shaping, sends.
   - [ ] Keep golden-ish audio tests for every sound engine change.

5. Android-native sharing and backup
   - [ ] Project import/export round-trip via Android share sheet.
   - [ ] Backup/export all projects as a zip.
   - [ ] Optional SD-card/Drive-friendly sync folder once local reliability is proven.
   - [ ] Export audio loop/WAV after project reliability is stable.

6. MIDI and external gear
   - [ ] USB MIDI note input to selected instrument.
   - [ ] MIDI clock sync.
   - [ ] MIDI OUT per track: channel, note, velocity, CC/program sends.
   - [ ] Settings UI for device/channel mapping.

## Priority 0 — Guardrails

- [x] Previous Android roadmap branch merged into `main`.
- [x] Tag current branch before starting this next slice.
- [x] Create isolated feature branch.
- [x] Remove iOS port/scaffold from this branch; it is explicitly out of scope.
- [x] Keep `./gradlew testDebugUnitTest assembleDebug` passing after every meaningful slice.
- [x] Commit and push every completed slice so Daniel can pick it up from another machine.

## Priority 1 — Note input + phrase editing

This is the biggest demo-to-instrument step. Mirror real M8 firmware behavior; do not invent a new tracker interaction model unless it is only a phone-friendly overlay on top of the same semantics.

- [x] Audit current SONG/CHAIN/PHRASE/TABLE edit handlers and cursor row/column mapping.
- [x] Add regression tests for SONG screen hex chain entry.
- [x] Add regression tests for CHAIN screen phrase and transpose entry.
- [x] Add regression tests for PHRASE screen note/instrument/volume/FX entry.
- [x] Implement touch-driven cell selection for SONG/CHAIN/PHRASE screens.
- [x] Implement touch-driven hex entry for SONG cells.
- [x] Implement touch-driven hex entry for CHAIN cells.
- [x] Implement note entry in PHRASE rows using current octave/key/scale.
- [x] Implement instrument, volume, and FX column editing in PHRASE rows.
- [x] Add phone-friendly quick action overlay for tracker cells where it maps cleanly to real M8 edit semantics: insert, clear, duplicate next row, and transpose +/- on SONG/CHAIN/PHRASE with status feedback.
- [x] Add long-press cell edit affordance.
- [x] Add sticky Shift/Edit/Option modes.
- [x] Add mini piano / note picker overlay for phrase note entry.
- [x] Add haptic feedback for edit confirmation and navigation.
- [x] Verify edited phrases trigger audible synth playback correctly. Row resolution lives on `M8Emulator.resolveRowDataAt` so touch edits, the audio scheduler, and previews share one path; regression tests in `M8EmulatorEditTest` guard the edit→synth bridge for notes, instrument index, chain transpose, and NOTE_OFF/EMPTY sentinels.
- [x] Add preview actions for song parts: picker note entry now auditions the written note via `previewNote` (mirrors EDIT-mode key press on real M8); EDIT+PLAY on SONG/CHAIN/PHRASE fires `previewRowAtCursor` as a one-shot synth trigger of the cursor row without engaging the sequencer or toggling playback.

## Priority 2 — `.m8s` song loading

`M8sParser` already exists and has tests. Wire it into the emulator/app so real M8 songs can be loaded and played.

- [x] HIGH PRIORITY: File hub for song loading: File New clears the song, Open Device launches Android's file picker for `.m8s`/audio-ish files, recent songs/projects are shown at the top of the File dialog, and startup tries to restore the last loaded song/project instead of always staying on the demo.
- [x] HIGH PRIORITY: remote song discovery/download, not just instruments. Added a dedicated Songs source that surfaces downloadable `.m8s` starter/song files ahead of instrument/sample sources; remote song downloads save into virtual SD `Songs/` and load through dirty-confirm using the existing one-click `.m8s` import flow.

- [x] Add app-level load path from downloaded/local `.m8s` files into emulator state.
- [x] Call `M8sParser.parse()` + `M8sParser.applyTo()` from the browser/load workflow.
- [x] Preserve current app state or ask/confirm before replacing active song: dirty-state signature now gates `.m8s` loads behind Save + Replace / Discard / Cancel.
- [x] Display load success/failure and partial-import warnings.
- [x] Parse instrument pool — 128 slots at `0x13A3E`, 215 bytes each, reusing `M8iParser.parseBodyAt`. Emulator instrument array expanded from 8 to 128 (named demo presets at 0–7, empty placeholders at 8–127). BrowseDialog picker stays capped at 8.
- [x] Parse mixer settings.
- [x] Parse global FX settings: chorus, delay, reverb. Caveat: V4 delay/reverb HP/LP cutoff and chorus width locations are not documented by upstream; existing defaults are preserved.
- [x] Parse scale definitions and song key. Caveat: enable maps and names are imported; microtuning cent offsets remain a parity gap until the note/synth path can represent them.
- [x] Add V4.0/V4.1 offset tests against real-world fixture files. Covers V4EMPTY, CMDMAPPING_4_0, and upstream V4-1EMPTY fixtures; confirmed V4-1 fixture reports 4.2.0 bytes while sharing V4 offsets.
- [x] Add integration test proving loaded song grid/chain/phrase data reaches `M8Emulator.song`. `M8Emulator.loadParsedSong()` is now the tested import seam used by `M8ViewModel.replaceSong()`.

## Priority 3 — Sample playback

WAV decode and first-pass sampler voice already exist. Finish the path so sampler instruments use actual files and drums sound like drums.

- [x] Pure-Kotlin PCM WAV decoder foundation.
- [x] Generated WAV decoder tests for mono/stereo PCM + unsupported format rejection.
- [x] Sample metadata model: file path, root note, loop points, one-shot/loop mode.
- [x] First-pass SAMPLER voice path in `M8Synth` using decoded WAV data.
- [x] Wire SAMPLER instruments to sample files in the virtual SD/download store.
- [x] Add sample cache to avoid decoding during audio callback.
- [x] Add pitch/transpose playback.
- [x] Add loop-point handling.
- [x] Add one-shot vs loop mode behavior.
- [x] Add interpolation to reduce aliasing on transposed samples.
- [x] Add tests using generated tiny WAV/sample fixtures.
- [x] Add sample preview in browser.


## Priority 4 — Macrosynth / wavsynth modes

Make the synth sound more like M8, not just a generic synth. The current Kotlin synth has a basic mapping; extend it toward real M8 oscillator/macro behavior. If/when the Rust engine is present in this repo, mirror the same model there too.

- [ ] Inventory existing Kotlin synth modes and any Rust crate/engine files in repo.
- [ ] Build an authenticity plan for synth parity: parameter mapping docs, reference patches, rendered-audio fixtures, and real-M8/headless-firmware comparison workflow where possible.
- [ ] Define a single parameter mapping table from `M8Instrument` to audible engine params.
- [ ] Replace fallback preset mapping with fuller M8 parameter interpretation.
- [ ] Expand WAVSYNTH oscillator shapes and wavetable behavior.
- [x] Add first-pass MacroSynth modes where possible: CSAW, morph/saw-square/sine-triangle, square/saw sub, triple saw/square, and noise/drum-like models.
- [ ] Improve FM algorithm/operator mapping beyond current simple type mapping.
- [x] Add golden-ish synth tests for oscillator mode changes, envelope changes, filter changes, and stereo pan.
- [ ] Add comparison fixtures or documented expected behavior against real M8 where available.

## Priority 4B — M8 parity gaps to track explicitly

These are the “sounds/behaves like a real M8” gaps. Do not chase hardware-only items unless they map cleanly to Android.

- [x] MACROSYNTH / Mutable Instruments-style models: first-pass CSAW, morph/saw-square/sine-triangle, square/saw sub, triple saw/square, and noise/drum-like mappings are present. Remaining: deeper Braids parity/formant/chord engines.
- [x] HYPERSYNTH: first-pass 8-detuned-oscillator supersaw behavior with chord intervals, swarm/detune, shift, and sub-osc controls.
- [ ] Runtime per-step FX command engine: first playback slice wires `VOL`, `AMP`, `PAN`, `SDL`, and `KIL` into row/synth runtime behavior; `KIL`, `RET`, `DEL`, `HOP`, and `SNG` now run from the playback flow. `TBL`/`TIC` table automation now affects runtime transpose/volume/pan/delay send and the startup demo exposes it audibly. `PSL` slides now progress toward targets, `PBN` bend accumulation resets on new notes, and `RET` applies timing plus volume-ramp retriggers with a demo flourish. Remaining: fuller arp/table/parameter-lock command parity for `O`, `X`, `Y`, etc.
- [ ] Modulation block: parse and apply 2 envelopes + 2 LFOs with assignable destinations. `DECISIONS.md` says this was explicitly skipped in both `.m8i` and `.m8s`; without it, downloaded instruments are static snapshots.
- [ ] MIDI OUT: support each track driving external synths on its own channel + CCs. This is a bigger M8 hardware-user use case than MIDI input.
- [ ] Per-instrument mixer chain: add 3-band EQ, limiter, drive, sample-rate reduction, and FX sends. Current implementation mostly has master delay/chorus/reverb, not real per-voice processing.
- [ ] Groove timing at scheduler: groove pool is parsed, but playback clock still uses a flat grid. Apply per-track groove/swing patterns to actual note timing.
- [ ] Custom scales / microtuning: parse user scales with per-note cent offsets and enforce them at note-on.
- [x] Sampler fidelity: pitched playback, loop points, one-shot vs loop behavior, and interpolation now exist. Remaining: slice mode and loop crossfade.
- [ ] Sampling in: evaluate Android recording path for creating new samples from mic/USB/line-style sources where possible. This is a hardware feature on M8; on phone it should be treated as an Android-native equivalent, not a perfect hardware clone.

## Hardware-specific items not worth chasing

- [ ] Do not chase volume knob, touch wheel, 5-pin DIN, true USB host MIDI hardware behavior, line-in hardware recording, or SGTL5000 analog character. These are physical-device traits, not app roadmap items.

## Priority 5 — Save songs

Once note/phrase editing exists, saving becomes mandatory and relatively cheap.

- [x] Add stable project snapshot model for song + instruments + engine settings (app-native `.m8droid` v1, binary snapshot with SHA-256 signatures for dirty checks).
- [x] Serialize current emulator state to local app storage (`filesDir/m8sd/Projects/<song>.m8droid`) from the new header `S` action and Save + Replace flow.
- [x] Add app-native project load/restore UI from the SD/Projects folder. The LOAD dialog now has a PROJECTS tab backed by `M8ProjectLibrary`, with dirty-confirm reuse and live restore into the emulator/instrument pool.
- [x] Add autosave after meaningful edits. The ViewModel now debounces dirty project edits and writes one `AUTOSAVED <song>.m8droid` snapshot after a quiet window; manual saves/project loads cancel pending autosaves.
- [x] Add project list operations: recent projects are surfaced in File hub and PROJECTS supports duplicate, rename, and delete with safe app-local `.m8droid` paths.
- [x] Add export/share for project files.
- [ ] Add import from shared file intent or SD browser.
- [x] Add tests for round-trip save/load of phrases, chains, tables, instruments, tempo, transpose, mixer, and global FX settings. `.m8droid` snapshot v2 preserves mixer/chorus/delay/reverb while still reading v1 snapshots.

## Priority 6 — MIDI input + MIDI OUT

`MidiEngine.kt` exists; bind it to live note-on dispatch and selected instrument behavior. Also add MIDI OUT: each track should be able to drive external synths on its own channel + CCs.

- [ ] Audit current `MidiEngine.kt` and Android MIDI permissions/device handling.
- [ ] Finish USB MIDI note input path.
- [ ] Route MIDI note-on/note-off to selected instrument.
- [ ] Respect current octave/scale where appropriate.
- [ ] Map external controller buttons to M8 navigation/actions.
- [ ] Add MIDI clock sync.
- [ ] Add MIDI OUT per track: channel, note, velocity, program/CC sends, and clock behavior.
- [ ] Add settings screen for MIDI device selection.
- [ ] Add Bluetooth MIDI only if Android APIs cooperate cleanly.

## Later Android polish

- [ ] Offline renderer for current pattern/song section.
- [ ] WAV writer and “bounce loop” action.
- [ ] Android share sheet for exported WAV.
- [ ] Local SD delete/rename operations.
- [ ] Favorites/tags/search/pack grouping in content library.
- [ ] Missing samples/instruments warnings for loaded songs.
- [x] Academy Synths chapter unlock/start bug: Chapter 2 now has playable Synths quests and chapter-specific intro/outro dialogue, so tapping SYNTHS after finishing basics starts real quests instead of falling through an empty chapter.
- [x] Academy quests: first beat plus real tracker loop basics — place a chain, link a phrase, enter a note, visit phrase/chain/table/song flow before Synths.
- [x] Tutorial panel positioning: bottom-sheet tutorial overlay with compact/half/expanded states, drag handle, and scrollable content.
- [x] Academy fresh start: entering Academy starts a controlled blank tutorial project (with dirty-song confirmation) instead of inheriting the demo/current file.
- [x] Academy quests: bass patch and deeper synth/sampler practice. Synths now continues into bass context, filter, and envelope-feel quests; Sampling now has playable File hub → sampler slot → phrase trigger → loop check quests.
- [ ] WebSocket remote screen/controller polish.
- [ ] SD card backup/sync helper for real hardware companion use.

## Explicitly out of scope

- [ ] iOS port — removed from this plan; do not implement.

## Recommended implementation order

Daniel's current testing priority after the save/download foundation: tutorial first, then tracker depth. Export/import and broader persistence hardening can wait until the tutorial and core tracker feel good enough to test seriously.

1. Tutorial / Academy UX and content: draggable/bottom-sheet panel, more playable quests, and a smoother guided flow.
2. Tracker depth: chain/phrase/table workflow, parameter locks/runtime FX, modulation, sampler/synth behavior that makes the instrument feel less shallow.
3. File view comfort pass: compact sections, better touch ergonomics, clearer collapsed recents/downloads/projects.
4. Save/export confidence: project share/import and broader persistence hardening.
5. MIDI input + MIDI OUT.
