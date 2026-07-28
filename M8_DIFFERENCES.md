# M8droid Differences, Limits, and Community Feedback Notes

This document is the honest compatibility note for M8droid. It explains what the app does today, where it differs from a real Dirtywave M8, and what should be improved before broader M8 community feedback.

M8droid is an unofficial Android-native M8-style tracker/emulator. It is useful for experimenting, learning, sketching, loading many `.m8s` songs, saving Android-native projects, and testing phone-first tracker workflows. It is not Dirtywave firmware running on Android, and it should not be marketed as a sound-identical replacement for real M8 hardware.

## Release-readiness verdict

M8droid is suitable for a small controlled community feedback beta after a clean APK build and real-device smoke test. Signed GitHub beta artifacts now provide a repeatable tester distribution path, but the remaining real-device smoke gate still applies; the stable/latest channel must remain unpublished until it passes.

It is not yet ready for a broad claim like “M8 on Android” without heavy qualifiers.

Use this positioning:

> M8droid is an experimental Android M8-style tracker/emulator. It loads many M8 v4 `.m8s` files and saves app-native `.m8droid` projects. The synth/audio is Android-native and approximate; it is not Dirtywave firmware and is not sound-identical to real M8 hardware yet. Feedback is wanted on Android device compatibility, file loading, touch workflow, missing sample handling, diagnostics, and overall usability.

Avoid these claims for now:

- “M8 on Android.”
- “Sounds like real M8.”
- “Works with Teensy/M8 hardware.”
- “Round-trips `.m8s` files.”
- “Can replace real M8 hardware.”

## What is stopping a broader release?

Nothing fundamental blocks a private beta, but these issues block a confident public M8-community launch:

1. Sound parity is approximate. The app uses local Android synth/emulator code, not Dirtywave’s firmware/DSP.
2. Hardware bridge mode is not productized. WebSocket scaffolding exists, but the app currently starts in local emulator mode and does not expose a proven real-M8/Teensy serial bridge mode.
3. Save format is app-native `.m8droid`, not official Dirtywave `.m8s` export.
4. Imported `.m8s` songs may load structurally but sound different because of missing samples, parser gaps, incomplete instrument/FX parity, and Android-native synthesis.
5. Real-device smoke testing is still the practical release gate.
6. Public messaging must be careful because experienced M8 users will judge sound, workflow, and file compatibility harshly if expectations are inflated.

## Song loading support

M8droid can load songs with scope limits.

Supported today:

- Load `.m8s` v4.x files through Android file/document flows and File Hub paths.
- Import core song data into local emulator state.
- Parse playback-critical sections, including:
  - song grid
  - chains
  - phrases
  - tables
  - grooves
  - mixer subset
  - global FX subset
  - scale names and enable-map subset
  - 128 instrument slots
- Load app-native `.m8droid` projects.
- Restore a recent song/project on startup, with recovery UI when loading fails.
- Surface partial-import and missing-sample warnings.

Current limits:

- `.m8s` import is one-way into the app/emulator state.
- `.m8s` support is v4.x-focused.
- Delay/reverb HP/LP cutoff fields are not fully imported.
- Scale microtuning cent offsets are not fully imported.
- Missing samples will make imported songs sound wrong or incomplete.
- Some M8 commands, modulation behavior, timing nuances, and routing details are still parity gaps.

## Song saving support

M8droid can save app-native projects.

Supported today:

- Save current project as `.m8droid` through the local project library.
- Autosave after meaningful edits.
- Track dirty state and confirm before replacing unsaved work.
- List, load, rename, duplicate, delete, share, import, and export `.m8droid` projects.
- Preserve song data, instruments, effects, mixer settings, and related emulator state through app-native snapshots.

Not supported yet:

- Export official Dirtywave `.m8s` files.
- Round-trip `.m8s -> M8droid -> .m8s` back to real M8 hardware.
- Promise compatibility with real M8 project saves.

The safest tester wording is: M8droid loads many `.m8s` files, but saves its own `.m8droid` project format.

## Sound differences from real M8

Songs may be structurally recognizable, but they should not be expected to sound identical to a real Dirtywave M8.

What can be similar:

- Song, chain, phrase, and table sequencing.
- Notes, tempo, row timing, and basic phrase playback.
- Some runtime FX behaviors implemented in the emulator/FX engine.
- Basic instrument-derived timbre mapping.
- Sampler playback when referenced WAV/sample files exist in expected paths.
- MIDI note output to external synths where Android MIDI works.

Why sound differs:

- The app does not run Dirtywave firmware locally.
- The app does not embed the real Dirtywave synth/DSP engine.
- Dirtywave instrument engines are approximated, not cloned.
- Some imported `.m8s` settings are not represented yet.
- Missing samples change songs dramatically.
- Real M8 behavior around tables, modulation, FX timing, mixer routing, instrument engines, sample slicing, envelopes, filters, groove, and project data is only partially modeled.
- Native synth integration may be a bottleneck if it receives only simple row note/volume data while richer instrument configuration lives in the Kotlin synth path.

Best honest summary:

> M8droid loads many M8 v4 songs and plays an Android-native approximation. It is useful for sketching, learning, browsing, and feedback, but it is not sound-identical to real M8 hardware.

## Teensy, firmware, and hardware constraints

The main constraint is not just that a “Teensy synth” is unavailable. The real issue is that M8droid does not run Dirtywave’s actual firmware/audio engine.

M8droid currently has:

- local display/tracker emulation
- local Android synth paths
- `.m8s` parsing
- app-native project save/load
- Android MIDI bridge support
- WebSocket client scaffolding

M8droid does not currently have:

- official M8 firmware running locally
- exact Dirtywave synth DSP
- a proven selectable real-M8/Teensy hardware bridge runtime
- official `.m8s` writer/exporter
- verified real-M8 project round-trip

## Hardware and external gear modes

There are two different hardware scenarios.

### External MIDI synths

Basic external MIDI behavior is a reasonable current or near-current use case because Android MIDI support exists in the app.

Expected scope:

- Send basic note events from sequencer rows.
- Potentially support CC/program/clock work later.
- External synths will sound like the external synth, not like a real M8.

### Real Dirtywave M8 or Teensy serial bridge

This is not release-ready as a public claim.

Current observations:

- `M8WebSocketClient` exists.
- The app starts in local emulator mode.
- `ConnectionManager`/runtime flow currently behaves primarily as local emulator display/protocol plumbing.
- Settings/server UI exists, but that is not the same as a proven selectable hardware bridge mode.
- Key input currently mutates/sends state to the local emulator by default.

Future hardware bridge mode should be separate from local emulator mode:

- Local Emulator: Android-native display, state, synth, project files.
- Hardware Bridge: phone acts as display/controller/companion for real M8/Teensy hardware, with hardware as source of truth.

Do not merge those concepts casually; state drift and tester confusion would be likely.

## Improvements before first community feedback

Before asking outside testers, finish the practical release hygiene first:

1. Clean or intentionally commit the current working tree.
2. Build a named APK from a known branch/commit.
3. Run `./gradlew testDebugUnitTest assembleDebug`.
4. Run `git diff --check`.
5. Install the APK on a real Android device.
6. Smoke test first launch, audio startup, demo playback, `.m8s` open, `.m8droid` save/load/share/import, recent project restore, dirty-confirm behavior, missing-sample warnings, and diagnostics export.
7. Add or verify in-app beta/limitations copy so testers see the same caveats without needing this document.
8. Deliver the APK privately before posting publicly.

## High-value parity roadmap

The most valuable improvements are not more broad features. They are targeted gaps that affect trust and tester feedback quality.

### 1. First-session and feedback polish

- Add a clear beta/about/known-limitations screen.
- Make diagnostics export one-tap from Help.
- Make success states obvious: saved, loaded, imported, restored, exported.
- Show missing-sample warnings prominently.
- Guide users toward “try demo,” “open song,” “make first loop,” and “export diagnostics.”

### 2. Sound and emulator parity

- Ensure the active synth path receives full instrument/sample/FX configuration, not only note and volume rows.
- Add stable audio fixture tests for simple imported songs.
- Improve sample path matching, missing-sample resolution, slice mode, loop behavior, and loop crossfade.
- Close known parser gaps for delay/reverb HP/LP and scale cent offsets.
- Improve high-impact M8 command behavior before obscure edge cases.
- Apply groove timing in the scheduler so imported songs feel less flat.

### 3. Official format round-trip

If serious M8 users are a target, eventually implement official `.m8s` export.

Start small:

- Write `.m8s` only for the subset M8droid fully represents.
- Add round-trip tests through the parser.
- Verify generated files with real M8 or a reference parser when possible.
- Keep `.m8droid` as the internal richer/project-safe format.

### 4. Hardware bridge mode

If real M8/Teensy support matters, make it a separate runtime mode:

- Add a runtime setting: Local Emulator / Hardware Bridge.
- Wire WebSocket/serial display frames into the display path.
- Send key state to hardware in hardware mode.
- Treat hardware as source of truth in hardware mode.
- Show connection and fallback states clearly.
- Test with actual hardware before claiming support.

## Suggested first community feedback audience

Start with 5–20 technically sympathetic testers:

- M8 owners who understand tracker workflows.
- Android users comfortable installing APKs.
- People willing to report device model, Android version, logs, sample paths, and diagnostics.
- People who understand “approximate sound” and will not expect a full M8 replacement yet.

Avoid a broad Reddit/Discord blast until the first smoke results are clean.

## Bottom line

- Private/community beta: yes, after clean build and real-device smoke test.
- Broad M8 community launch: not yet.
- Claiming faithful M8 sound: no.
- Claiming official `.m8s` save/round-trip: no.
- Claiming real M8/Teensy hardware bridge support: no, not until hardware bridge mode is wired and tested.

The right next move is honest packaging, diagnostics, smoke testing, and small-group feedback. The biggest risk is not that the app is useless; it is that inflated claims will create the wrong expectations.
