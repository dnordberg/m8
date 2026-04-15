---
title: "M8droid — Status Snapshot"
date: "2026-04-15"
geometry: margin=1in
---

# Where I am

**M8droid** is a standalone Android M8-style tracker — the only M8
client that runs fully on-device with no Teensy, USB, server, or
network dependency.

**Working today:**

- Embedded M8 emulator rendering to a pixel-perfect 320×240 Compose
  Canvas via SLIP-encoded draw commands, matching real hardware.
- Polyphonic synth: PolyBLEP band-limited waveforms, 2-op FM,
  per-voice ADSR, PWM+LFO, resonant SVF filter per voice, S&H noise.
- Effects chain: ping-pong delay, dual modulated chorus, Schroeder
  plate reverb, DC removal, tanh soft clipping.
- Tracker: 8 tracks, BPM-synced sample-accurate playback, swing,
  multiple patterns, song arrangement, per-track metering, live
  waveform visualization.
- Reliable keyboard input via Activity-level `dispatchKeyEvent`;
  down-arrow emulates M8's combined DOWN+SHIFT key.
- **Content loading pipeline (new):** Load button → Browse dialog
  with GitHub / Patchstorage / Archive.org / SD tabs → download to a
  virtual M8 SD card (`filesDir/m8sd/` mirroring real firmware folder
  layout) → JSON index → SD tab shows what's stored → `.m8i`
  instruments parse and load live into any of 8 emulator slots.
- Optional remote mode still works against a real Teensy + M8
  Headless via WebSocket server.

**End-to-end path that works now:** open app → Load → GitHub or
Patchstorage → pick instrument → DOWNLOAD → SD tab → pick slot →
LOAD → audible on next note.

# Where I want to be

A phone-native M8 that is useful for actual music-making, not just
demoing. Concretely:

- **Compose, not just watch.** Note input from the UI so songs can be
  built on-device.
- **Full song import.** `.m8s` loader covering grooves, matrix,
  phrases, chains, tables, instruments, effects, midi, scales, eq,
  with V4.0 / V4.1 offset branching.
- **Sample playback in the audio engine** so downloaded `.wav`s and
  sample-based instruments actually make sound.
- **Save / load user songs** — persist work between sessions.
- **Full `.m8i` fidelity** — parse the modulation block (envelopes +
  LFOs) so downloaded instruments sound identical to the source.
- **SD management** — delete, rename, browse without accumulation.
- **MIDI input.**

Stretch: V2/V3 firmware file compatibility; more content sources.

# What I need to do (next)

Rough order, biggest impact first.

1. **`.m8s` song parser.** Biggest unlock — turns the download
   pipeline from "instrument grabber" into "song library." Dedicated
   session; version-branch V4.0 vs V4.1.
2. **Sample playback in the audio engine.** Required before the
   Samples/ folder is useful at all. Touches the DSP core.
3. **UI note input.** Navigation + entry for patterns / phrases /
   chains so you can compose. Biggest product change.
4. **Song save/load (user work, not just imports).** Serialize the
   emulator state. Needed before note input is worth anything.
5. **`.m8i` modulation block parser.** Finish the instrument parser.
   Needs research against real files for the Mod union layout.
6. **SD management UI** — delete, rename, free-space indicator.
7. **MIDI input** (lower priority; needs USB-host plumbing).
8. **Older firmware version support** in `.m8i` — wait until it
   actually comes up.

# Decisions made recently without much input

See **DECISIONS.md** (in repo root) for the full list with
trade-offs. Highlights worth a second look:

- Down arrow sends DOWN+SHIFT together — you can't isolate DOWN from
  a keyboard anymore.
- Virtual SD layout locks us into M8 firmware folder naming.
- `.m8i` parser silently falls back to WavSynth for unknown kinds
  and skips the modulation block entirely.
- 8 instrument slots in the UI is an emulator simplification, not
  real M8 capacity.
- JSON index (not SQLite) for the virtual SD — simple, not
  transactional.
- Collision-safe rename on download is silent.
- Song / Sample / Pack tabs show "not yet implemented" as dead UI
  surface rather than being hidden.
