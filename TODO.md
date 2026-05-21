# M8 Android Roadmap TODO

Snapshot tag before this branch: `pre-android-roadmap-2026-05-21`

Working branch: `feat/android-m8-library-roadmap`

## Goal

Turn the Android app from an M8-inspired demo/browser into a useful phone-native tracker workstation: load M8 content, compose on-device, persist work, play samples, export audio, support MIDI, and teach the workflow.

## Priority 0 — Guardrails

- [x] Tag current branch before major work.
- [x] Create isolated feature branch.
- [ ] Keep Android debug builds passing after every meaningful slice.
- [ ] Commit and push every completed slice so Daniel can pick it up from another machine.

## Priority 1 — Save/load local user projects

- [ ] Add a stable project snapshot model for song + instruments + engine settings.
- [ ] Serialize current emulator state to local app storage.
- [ ] Add autosave after meaningful edits.
- [ ] Add project list: recent projects, duplicate, rename, delete.
- [ ] Add export/share for project files.
- [ ] Add import from shared file intent or SD browser.
- [ ] Add tests for round-trip save/load of phrases, chains, tables, instruments, tempo, transpose, mixer.

## Priority 2 — Sample playback

- [x] Add pure-Kotlin WAV decoder foundation for PCM WAV files.
- [x] Add generated WAV decoder tests for mono/stereo PCM + unsupported format rejection.
- [x] Add sample metadata model: file path, root note, loop points, one-shot/loop mode.
- [x] Add first-pass SAMPLER voice path in `M8Synth` using decoded WAV data.
- [ ] Wire SAMPLER instruments to sample files in the virtual SD.
- [ ] Add pitch/transpose playback.
- [ ] Add loop-point handling.
- [ ] Add sample cache to avoid decoding during audio callback.
- [ ] Add one-click sample preview in the browser.
- [ ] Add tests using generated tiny WAV fixtures.

## Priority 3 — Fuller `.m8s` import fidelity

Current `.m8s` parser covers header, song grid, phrases, chains, tables, and grooves.

- [x] Parse groove pool.
- [x] Apply parsed grooves into `M8Song`.
- [ ] Parse instrument pool.
- [ ] Parse mixer settings.
- [ ] Parse global FX settings: chorus, delay, reverb.
- [ ] Parse scale definitions and active scale.
- [ ] Parse EQ where useful.
- [ ] Parse MIDI mappings where useful.
- [ ] Add V4.0/V4.1 offset tests against real-world fixture files.
- [ ] Surface partial-import warnings instead of silently defaulting important data.

## Priority 4 — Phone-native composition UX

- [ ] Add long-press cell edit affordance.
- [ ] Add sticky modifier modes for Shift/Edit/Option.
- [ ] Add note picker / mini piano overlay.
- [ ] Add value wheel or keypad overlay for hex fields.
- [ ] Add pattern/phrase/chain quick-jump.
- [ ] Add haptic feedback for M8 buttons and edits.
- [ ] Add portrait sketch mode and landscape tracker mode.
- [ ] Add undo/redo stack for edits.

## Priority 5 — Audio export

- [ ] Add offline renderer for current pattern/song section.
- [ ] Add WAV writer.
- [ ] Add “bounce loop” action.
- [ ] Add Android share sheet for exported WAV.
- [ ] Later: per-track stems.

## Priority 6 — MIDI input

- [ ] Finish USB MIDI note input path.
- [ ] Add Bluetooth MIDI if Android APIs cooperate.
- [ ] Route MIDI notes to selected instrument.
- [ ] Map external controller buttons to M8 navigation/actions.
- [ ] Add MIDI clock sync.
- [ ] Add settings screen for MIDI device selection.

## Priority 7 — Content library / pack manager

- [ ] Add local SD delete/rename operations.
- [ ] Add favorites and tags.
- [ ] Add search across downloaded + remote content.
- [ ] Add preview before download/load.
- [ ] Add pack grouping.
- [ ] Show missing samples/instruments for loaded songs.
- [ ] Cache remote indexes for offline browsing.

## Priority 8 — Academy mode expansion

- [ ] Add interactive “make your first beat” quest.
- [ ] Add “bass patch from scratch” quest.
- [ ] Add chains/phrases/tables explainer quests.
- [ ] Add state-based quest checks against emulator content.
- [ ] Add progress badges.

## Priority 9 — Real hardware companion mode

- [ ] Polish WebSocket remote screen/controller mode.
- [ ] Add SD card backup/sync helper.
- [ ] Add M8 content transfer workflow.
- [ ] Add real-hardware patch/song browser.
- [ ] Add MIDI/router companion mode.

## Priority 10 — Synth/emulator layer quality

- [x] Wire `M8Synth.applyInstrument` / `configureVoice` into real per-track voice presets instead of no-op stubs.
- [x] Map WAVSYNTH shape, FM type, filter cutoff/resonance, amp, pan, delay send, and envelope into render behavior.
- [x] Add synth regression tests proving instrument shape changes audio and pan affects stereo balance.
- [x] Add sampler voice path using decoded WAV data.
- [ ] Replace fallback preset mapping with fuller M8 parameter interpretation.
- [ ] Wire sampler path to virtual SD file lookup.
- [ ] Add offline render path shared by export/bounce.
- [ ] Add underrun/latency instrumentation.

## Recommended implementation order

1. Save/load user projects.
2. Sample playback.
3. Fuller `.m8s` import: instruments + mixer/FX.
4. Phone-native composition UX.
5. Export WAV/share.
6. MIDI input.
7. Content library polish.
8. Academy/tutorial expansion.
9. Hardware companion mode.

## Notes

- Keep this Android-first. iOS/KMP work is separate.
- Avoid perfect-clone traps. Target: M8 workflow adapted for phone-native music-making.
- Preserve valuables in code: emulator, parser, synth engine, Browse/SD pipeline, Academy system.
