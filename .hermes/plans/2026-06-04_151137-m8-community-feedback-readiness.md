# m8 Community Feedback Readiness Plan

Date: 2026-06-04 15:11 UTC

## Goal

Answer the practical release question for the Android m8 app:

- What is stopping us?
- Can we load and save songs?
- Do loaded songs sound close to the original M8?
- Are there constraints because the Dirtywave/Teensy synth/firmware is not available?
- Can the app work connected to real M8 / Teensy-based hardware?
- What should we improve in emulator, audio, and release polish before public community feedback?

This is planning only. No implementation is included here.

## Current repo context observed

Branch state at inspection time:

- `main` tracks `origin/main`.
- There are uncommitted modal style consistency changes from the prior UI task:
  - `app/src/main/java/com/m8droid/ui/ModalStyle.kt`
  - `app/src/main/java/com/m8droid/ui/HelpButton.kt`
  - `app/src/main/java/com/m8droid/ui/SettingsScreen.kt`
  - `app/src/main/java/com/m8droid/ui/BrowseDialog.kt`
  - `app/src/main/java/com/m8droid/browse/FileHubTabs.kt`
  - related tests
- Last committed work includes file hub loading/config editing and sampler/synth improvements.

Important implementation facts from code inspection:

- App starts in **local emulator mode** via `M8ViewModel.startLocalEmulator()`.
- Display is rendered locally through `M8Emulator.renderFrame()` and parsed through `ConnectionManager.protocol.processBytes()`.
- `ConnectionManager` currently only owns display/protocol state; it does not currently manage a live serial/WebSocket connection to hardware.
- `M8WebSocketClient` exists but is not wired into `M8ViewModel`/`ConnectionManager` as an active selectable hardware mode.
- `MidiEngine` is wired and starts from `MainActivity`; it handles Android USB-OTG/Bluetooth MIDI note/CC I/O, not the Dirtywave M8 serial/control protocol.
- Internal app projects are saved as `.m8droid`, not as Dirtywave `.m8s` export files.
- Dirtywave `.m8s` v4.x files can be imported via `M8sParser`, including song grid, phrases, chains, tables, grooves, mixer subset, FX subset, scales subset, and 128-slot instrument pool.
- `M8ProjectSnapshot` persists local project state with song/instruments/effects/mixer and has import/export tests.
- `NativeSynth` JNI currently exposes only basic row trigger/generate/levels calls, while the Kotlin synth has richer instrument/sample configuration. This is a likely sound-parity bottleneck if native mode is active.

## Direct answers

### 1. What is stopping us?

Nothing fundamental stops a **private beta / community feedback** release, but several things stop us from honestly calling it a faithful Android M8 replacement:

1. **Sound parity is not guaranteed.** The app uses a local emulator/synth approximation, not Dirtywave firmware/DSP.
2. **Hardware M8/Teensy serial mode is not actually productized.** There is WebSocket client scaffolding, but the app currently boots and operates as a local emulator.
3. **Save format is app-native `.m8droid`.** We can save local work, but not round-trip back to official `.m8s` yet.
4. **Real-device smoke testing is still the gate.** Unit tests/build pass historically, but community feedback needs confidence on actual phones: audio startup, save/load/import/export, touch workflow, share/open flows, and no data-loss.
5. **Public expectations need careful wording.** M8 community users will judge sound and workflow harshly if this is presented as “an M8 on Android.” It should be framed as an experimental Android M8-style tracker/emulator/companion app.

### 2. Can we load songs?

Yes, with scope limits.

Supported:

- Load `.m8s` v4.x via Android document picker or File Hub download path.
- Parse playback-critical sections:
  - header/name/tempo/transpose/quantize/key
  - song grid
  - phrases
  - chains
  - tables
  - grooves
  - mixer subset
  - global FX subset
  - scale names/enable maps subset
  - 128 instrument slots
- Load app-native `.m8droid` projects.
- Restore recent song/project on startup with visible recovery dialog on failure.
- Import external `.m8droid` bytes into managed project storage.

Current limitations:

- `.m8s` import is one-way into the emulator/app state.
- `M8sParser` is v4.x only.
- Parser warnings explicitly say delay/reverb HP/LP cutoff and scale microtuning cent offsets are not fully imported.
- Missing samples are surfaced as project warnings, but the song will not sound identical if sample files are not present under the expected virtual SD paths.

### 3. Can we save songs?

Yes for app-native projects; not yet as official Dirtywave `.m8s`.

Supported:

- Save current project as `.m8droid` through `M8ProjectLibrary.saveProject()`.
- Autosave after meaningful edits.
- Dirty-state tracking and confirm-before-replace behavior.
- List/load/rename/duplicate/delete/share `.m8droid` projects.
- Import/export app-native projects with managed-root containment and tests.

Not supported yet:

- Export/save to official `.m8s` format.
- Full `.m8s` round-trip that can be loaded back into real M8 hardware.

This should be stated clearly to testers.

### 4. Do songs sound similar to the original M8?

Partly, but not reliably enough to market as faithful.

What should sound structurally similar:

- Basic song/chain/phrase sequencing.
- Notes, row timing, tempo, simple phrase playback.
- Some runtime FX behaviors already implemented through `M8FxEngine`.
- Basic instrument-derived timbre mapping when using the Kotlin synth path.
- Sampler playback if referenced WAV files are present in the virtual SD/sample cache.
- MIDI note output can mirror sequencer rows to connected MIDI devices.

Why it will differ from a real M8:

- The real M8 firmware/DSP/Teensy synth internals are not embedded here.
- Dirtywave instrument engines are approximated, not cloned.
- Native JNI synth API currently only accepts row notes/volumes and generates audio; it does not expose rich per-instrument configuration in the code inspected.
- Kotlin `M8Synth` has richer params, samplers, MacroSynth/HyperSynth approximations, filters, envelopes, FX, etc., but these are still approximations.
- Missing samples will change songs dramatically.
- Some `.m8s` settings are explicitly not imported yet.
- The real M8 has nuanced behavior around tables, modulation, FX timing, mixer routing, instrument engines, sample slicing, envelopes, filters, groove, rendering, and project data that we only partially model.

Best honest positioning:

> “It loads many M8 v4 songs and plays an Android-native approximation. It is useful for sketching, learning, browsing, and feedback, but it is not yet sound-identical to real M8 hardware.”

### 5. Are constraints related to the Teensy synth not being available?

Yes.

The main constraint is not simply “Teensy synth unavailable”; it is that the app does not run Dirtywave’s actual firmware/audio engine. The app has:

- local display/tracker emulator
- local Kotlin and native synth paths
- M8 file parsing
- Android MIDI bridge

It does **not** currently have:

- official M8 firmware running locally
- exact Dirtywave synth DSP
- exact M8 serial/control mode wired as the primary runtime
- full official `.m8s` writer/exporter

So sound and behavior can be good enough for an experimental beta, but not exact.

### 6. Will it work connected to a Teensy synth / real M8?

There are two different meanings:

#### A. Connected to MIDI synth hardware

Likely yes for basic MIDI behavior, because `MidiEngine` handles Android MIDI devices and `M8ViewModel` broadcasts sequencer rows as MIDI notes. That can drive external synths over USB-OTG/Bluetooth MIDI.

Limitations:

- Primarily note on/off and CC scaffolding.
- Not a full M8 firmware/control protocol.
- External synths will sound like the external synth, not M8.

#### B. Connected to a real Dirtywave M8 / Teensy serial bridge as controller/display

Not release-ready yet.

Reasons:

- `M8WebSocketClient` exists, but inspection found no active integration from `M8ViewModel`/`ConnectionManager` into a selectable “hardware bridge” mode.
- `ConnectionManager` currently comments and behaves like local emulator display plumbing.
- Settings have host/port/restart UI, but `startLocalEmulator()` is what actually runs.
- The app currently sends local key state to `M8Emulator.handleKeyState()`, not to real hardware by default.

Plan outcome: treat hardware bridge as a future mode, not a current release claim.

## Release readiness verdict

### My recommendation

You are ready for a **small controlled community feedback release**, but not a broad “M8 replacement” release.

Release it if the announcement is honest and constrained:

- “Experimental Android M8-style tracker/emulator.”
- “Loads many M8 v4 `.m8s` songs, saves app-native `.m8droid` projects.”
- “Sound is approximate and under active development.”
- “Not affiliated with Dirtywave.”
- “Real M8 hardware bridge is not the focus of this build.”
- “Looking for Android device feedback, file loading feedback, UI/touch workflow feedback, and missing-sample/song compatibility reports.”

Do **not** release it with claims like:

- “M8 on Android” without qualifiers.
- “Sounds like real M8.”
- “Works with Teensy/M8 hardware” unless and until hardware bridge mode is proven.
- “Round-trips `.m8s` files.”

### Suggested audience

Start with 5–20 technically sympathetic testers:

- M8 owners who understand tracker workflows.
- Android users comfortable installing APKs.
- People willing to send diagnostics and sample paths.
- Avoid launching to a huge subreddit/Discord blast before first smoke results.

## What to improve before first community feedback

These are release-blocking or near-blocking for credibility.

### Phase 0 — stabilize current branch before talking publicly

1. Commit or revert the current uncommitted modal-style changes.
2. Build a named APK from a clean branch.
3. Confirm artifact delivery works as a Telegram file card and not a visible `MEDIA:` path.
4. Run:
   - `./gradlew testDebugUnitTest assembleDebug`
   - `git diff --check`
5. Real-device smoke test on Daniel’s Android phone.

Likely files:

- `app/src/main/java/com/m8droid/ui/ModalStyle.kt`
- `app/src/main/java/com/m8droid/ui/HelpButton.kt`
- `app/src/main/java/com/m8droid/ui/SettingsScreen.kt`
- `app/src/main/java/com/m8droid/ui/BrowseDialog.kt`
- `app/src/main/java/com/m8droid/browse/FileHubTabs.kt`

### Phase 1 — define release messaging inside the app

Add a visible beta/about/diagnostics note:

- App is experimental.
- `.m8s` load support is partial and v4.x-focused.
- Saves are `.m8droid`, not official `.m8s` export.
- Sound is approximate.
- Missing sample warnings matter.
- Diagnostics export exists and should be used in bug reports.

Likely files:

- `app/src/main/java/com/m8droid/ui/HelpButton.kt`
- `app/src/main/java/com/m8droid/emulator/DiagnosticReport.kt`
- maybe `app/src/main/java/com/m8droid/ui/AppHeaderBar.kt` or equivalent header/about entry

Tests:

- Add a small string/content test if there is an existing test seam.
- Otherwise verify through APK smoke.

### Phase 2 — first-session/community feedback polish

Improve the first 3 minutes:

1. On first launch, show “Try Demo / Open Song / Academy / Help”.
2. Keep File Hub simple and consistent.
3. Make save/load states very explicit:
   - `SAVED <file>`
   - `LOADED <song>`
   - `IMPORTED <project>`
   - `MISSING SAMPLES — EXPORT DIAGNOSTICS`
4. Add a “Known limitations” help card.
5. Make diagnostics export one-tap from Help.

Likely files:

- `app/src/main/java/com/m8droid/ui/HelpButton.kt`
- `app/src/main/java/com/m8droid/ui/BrowseDialog.kt`
- `app/src/main/java/com/m8droid/MainActivity.kt`
- `app/src/main/java/com/m8droid/M8ViewModel.kt`

Tests:

- Existing UI layout state tests where available.
- `testDebugUnitTest assembleDebug`.

### Phase 3 — sound/emulator parity improvements

Highest ROI improvements:

1. **Make native synth accept instrument configuration**, not only note/volume rows.
   - Add JNI calls for per-track instrument type/params/sample assignment or disable native path until parity improves.
   - Today, Kotlin `M8Synth.configureVoice()` has richer mapping, but `NativeSynth` interface does not show corresponding config calls.
2. **Create audio golden tests** for simple `.m8s` fixtures:
   - basic pulse/saw/sine phrase
   - sampler one-shot
   - table arpeggio/vibrato/retrigger
   - mixer volume/pan/send
3. **Add song compatibility report after import**:
   - supported sections
   - missing samples
   - unsupported version/settings
   - warnings surfaced before playback expectations are set
4. **Improve sampler handling**:
   - sample path matching against virtual SD
   - missing sample resolver UI
   - loop/crossfade/slice behavior
5. **Close known parser gaps**:
   - delay/reverb HP/LP V4 offsets
   - scale cent offsets
   - any remaining global FX/mixer fields
6. **M8 command/timing parity**:
   - more table commands
   - groove nuances
   - hop/break/retrigger edge cases
   - phrase/chain/song navigation edge cases

Likely files:

- `app/src/main/java/com/m8droid/audio/NativeSynth.kt`
- native Rust/JNI source for `m8_synth` if present in repo
- `app/src/main/java/com/m8droid/emulator/M8Synth.kt`
- `app/src/main/java/com/m8droid/emulator/M8FxEngine.kt`
- `app/src/main/java/com/m8droid/emulator/M8sParser.kt`
- `app/src/main/java/com/m8droid/audio/SampleCache.kt`
- `app/src/test/java/com/m8droid/emulator/M8SynthInstrumentTest.kt`
- `app/src/test/java/com/m8droid/emulator/M8FxEngineTest.kt`
- parser tests for `.m8s` fixtures

Validation:

- Unit tests for parser and FX.
- Generated PCM comparison for stable fixtures.
- Manual A/B listening against real M8/rendered examples where possible.

### Phase 4 — official format round-trip

If the goal is acceptance by serious M8 users, eventually support exporting official `.m8s` or be explicit that this app does not.

Steps:

1. Define `.m8s` writer scope.
2. Start with files the app imported without unsupported fields.
3. Encode v4 `.m8s` sections from current `M8Song` + instruments.
4. Add round-trip tests:
   - `.m8s -> app -> .m8s -> parser`
   - app-generated `.m8s` loads in reference parser
   - if possible, loads on real M8
5. Keep `.m8droid` as richer internal project format if needed.

Likely files:

- new `app/src/main/java/com/m8droid/emulator/M8sWriter.kt`
- `M8sParser.kt`
- `M8ProjectLibrary.kt`
- File Hub/share UI

### Phase 5 — hardware bridge mode, if we want it

This should be a separate product mode:

- **Local Emulator**: current app, Android-native audio/display/state.
- **Hardware Bridge**: app connects to M8/Teensy serial bridge; phone acts as display/controller/companion.

Implementation plan:

1. Wire `M8WebSocketClient` into `ConnectionManager` or a new `HardwareConnectionManager`.
2. Add settings toggle: `Runtime mode = Local Emulator / Hardware Bridge`.
3. In hardware mode:
   - render display from hardware SLIP frames
   - send key state through `M8Commands.keyState(...)`
   - do not mutate local `M8Song` as source of truth
   - show connection state clearly
4. Add fallback/disconnect behavior.
5. Test with an actual bridge and real M8/Teensy device.

Likely files:

- `app/src/main/java/com/m8droid/network/ConnectionManager.kt`
- `app/src/main/java/com/m8droid/network/M8WebSocketClient.kt`
- `app/src/main/java/com/m8droid/protocol/M8Commands.kt`
- `app/src/main/java/com/m8droid/M8ViewModel.kt`
- `app/src/main/java/com/m8droid/data/ServerSettings.kt`
- `app/src/main/java/com/m8droid/ui/SettingsScreen.kt`

Risks:

- User expectations change completely in hardware mode.
- Local emulator and hardware state can drift if not separated cleanly.
- Need real hardware testing; cannot verify only with unit tests.

## Suggested feedback-release checklist

Before sending to community testers:

1. Clean working tree or intentionally committed beta branch.
2. Build named APK.
3. Real-device smoke test:
   - fresh install
   - first launch
   - audio starts
   - demo plays
   - File Hub opens
   - open `.m8s`
   - save `.m8droid`
   - close/reopen restores recent
   - share/export `.m8droid`
   - import shared `.m8droid`
   - diagnostics export
   - missing-sample warning path
4. Write short release notes:
   - What it is
   - What it is not
   - How to test
   - Known limitations
   - What feedback is wanted
5. Deliver APK as native Telegram file card first to Daniel.
6. Only then post/share externally after explicit approval.

## Suggested community post wording

Use a humble, accurate version like:

> I’m testing an experimental Android M8-style tracker/emulator. It can load many M8 v4 `.m8s` files and save app-native `.m8droid` projects. The synth/audio is Android-native and approximate — this is not Dirtywave firmware and not sound-identical to real M8 hardware yet. I’m looking for feedback on Android device compatibility, file loading, touch workflow, missing sample handling, and general usability. If you test it, diagnostics export is included so compatibility bugs are easier to report.

Avoid overstating sound/hardware compatibility.

## Bottom line

Release readiness:

- **Private/community feedback beta:** yes, after a clean build and real-device smoke test.
- **Broad M8 community launch:** not yet.
- **Claiming faithful M8 sound:** no.
- **Claiming official `.m8s` save/round-trip:** no.
- **Claiming real M8/Teensy hardware bridge support:** no, not until `M8WebSocketClient` is wired/tested as a runtime mode.

My opinion: do a controlled beta now, but frame it as experimental and feedback-seeking. The biggest immediate win is not more features; it is honest packaging, smoke testing, diagnostics, and preventing users from assuming exact M8 sound or official project round-trip.
