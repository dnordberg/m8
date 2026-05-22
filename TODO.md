# M8 Android Implementation TODO

Snapshot tag before this branch: `pre-note-phrase-work-2026-05-21`

Working branch: `feat/note-phrase-m8s-sampler-synth`

## Goal

Make M8droid feel like a real Android-native M8-style instrument: direct note input and phrase editing first, then real song loading, sample playback, stronger synth modes, saving, and MIDI. Keep this Android-first. No iOS port work.

## Priority 0 — Guardrails

- [x] Previous Android roadmap branch merged into `main`.
- [x] Tag current branch before starting this next slice.
- [x] Create isolated feature branch.
- [x] Remove iOS port/scaffold from this branch; it is explicitly out of scope.
- [ ] Keep `./gradlew testDebugUnitTest assembleDebug` passing after every meaningful slice.
- [ ] Commit and push every completed slice so Daniel can pick it up from another machine.

## Priority 1 — Note input + phrase editing

This is the biggest demo-to-instrument step. Mirror real M8 firmware behavior; do not invent a new tracker interaction model unless it is only a phone-friendly overlay on top of the same semantics.

- [x] Audit current SONG/CHAIN/PHRASE/TABLE edit handlers and cursor row/column mapping.
- [x] Add regression tests for SONG screen hex chain entry.
- [x] Add regression tests for CHAIN screen phrase and transpose entry.
- [x] Add regression tests for PHRASE screen note/instrument/volume/FX entry.
- [x] Implement touch-driven cell selection for SONG/CHAIN/PHRASE screens.
- [ ] Implement touch-driven hex entry for SONG cells.
- [ ] Implement touch-driven hex entry for CHAIN cells.
- [x] Implement note entry in PHRASE rows using current octave/key/scale.
- [x] Implement instrument, volume, and FX column editing in PHRASE rows.
- [ ] Add phone-friendly value input overlay only where it maps cleanly to real M8 edit semantics.
- [ ] Add long-press cell edit affordance.
- [ ] Add sticky Shift/Edit/Option modes.
- [ ] Add mini piano / note picker overlay for phrase note entry.
- [ ] Add haptic feedback for edit confirmation and navigation.
- [ ] Verify edited phrases trigger audible synth playback correctly.

## Priority 2 — `.m8s` song loading

`M8sParser` already exists and has tests. Wire it into the emulator/app so real M8 songs can be loaded and played.

- [x] Add app-level load path from downloaded/local `.m8s` files into emulator state.
- [x] Call `M8sParser.parse()` + `M8sParser.applyTo()` from the browser/load workflow.
- [ ] Preserve current app state or ask/confirm before replacing active song.
- [x] Display load success/failure and partial-import warnings.
- [ ] Parse instrument pool.
- [ ] Parse mixer settings.
- [ ] Parse global FX settings: chorus, delay, reverb.
- [ ] Parse scale definitions and active scale.
- [ ] Add V4.0/V4.1 offset tests against real-world fixture files.
- [ ] Add integration test proving loaded song grid/chain/phrase data reaches `M8Emulator.song`.

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
- [ ] Runtime per-step FX command engine: first playback slice wires `VOL`, `AMP`, `PAN`, `SDL`, and `KIL` into row/synth runtime behavior. Remaining: fuller arp/retrig/slide/hop/table/parameter-lock command parity for `T`, `R`, `O`, `H`, `X`, `Y`, etc.
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

- [ ] Add stable project snapshot model for song + instruments + engine settings.
- [ ] Serialize current emulator state to local app storage.
- [ ] Add autosave after meaningful edits.
- [ ] Add project list: recent projects, duplicate, rename, delete.
- [ ] Add export/share for project files.
- [ ] Add import from shared file intent or SD browser.
- [ ] Add tests for round-trip save/load of phrases, chains, tables, instruments, tempo, transpose, mixer.

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
- [ ] Academy quests: first beat, bass patch, chains/phrases/tables explainer.
- [ ] WebSocket remote screen/controller polish.
- [ ] SD card backup/sync helper for real hardware companion use.

## Explicitly out of scope

- [ ] iOS port — removed from this plan; do not implement.

## Recommended implementation order

1. Note input + phrase editing.
2. `.m8s` song loading.
3. Sample playback wired to virtual SD.
4. Macrosynth / wavsynth mode depth.
5. Save songs.
6. MIDI input + MIDI OUT.
7. Later Android polish.
