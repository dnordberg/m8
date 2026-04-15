# Changes

A high-level summary of recent work on m8droid. Ordered roughly from
earliest to most recent within the session.

---

## Keyboard input

### Reliable hardware-key capture

Hardware keyboard input on Android Compose apps is fragile because any
focused Compose view can swallow `KeyEvent`s before the Activity sees
them. Previously the app overrode `onKeyDown`/`onKeyUp`, which meant
keys sometimes did nothing depending on which part of the UI had focus.

`MainActivity` now overrides `dispatchKeyEvent` instead. That hook runs
at the Activity level before Compose can intercept, so arrow keys, WASD,
Z/X, Space, and Shift always reach `KeyMapper`. Repeat-count guarding
was added so held keys don't spam screen-switch hotkeys.

### Down-arrow as shift+down combo

On the original M8 (Model 01), the physical DOWN button and SHIFT
button are the same key. To mirror that behavior with a keyboard,
pressing the down arrow (or `S` in WASD mode) now sends both
`KEY_DOWN` and `KEY_SHIFT` simultaneously — so combos like "shift +
down" are a single keystroke, matching the muscle memory of M8
hardware users.

The dedicated Left/Right Shift keys still send `KEY_SHIFT` alone, so
you can still do shift+up, shift+left, shift+right the normal way.

---

## Content loading — Phase 1: download infrastructure

The goal was to let users browse and download M8 content (songs,
instruments, samples, themes) from free online sources directly in
the app, without leaving the emulator or juggling SD-card swaps.

### Load button

A third circular button (`\u2B07` / down-arrow glyph) now sits in the
top-right row next to Settings and Help. It matches the existing M8
neon-green visual language exactly — 36dp, circular, 1dp green
border, monospace glyph. Hidden while other overlays are up.

### Browse dialog

Opening the Load button brings up a full-screen M8-styled dialog with
four tabs: **GitHub**, **Patchstorage**, **Archive.org**, and **SD**.
The dialog has a list view on the left and a detail pane on the right.
Selecting an item shows all available metadata (title, author,
description, license, size, download count, tags, date) and a
DOWNLOAD button.

### Content sources

Three remote sources are live, each implementing a common
`ContentSource` interface so new sources can be added without touching
the dialog:

- **GitHub** — walks curated community repos (`laamaa/m8i`,
  `trash80/M8HeadlessFirmware`) via the git-tree API. Auto-classifies
  files by extension (`.m8s`, `.m8i`, `.m8t`, `.m8n`, `.wav`). No
  auth required, no rate-limit issues at current curation size.

- **Patchstorage** — queries the public REST API filtered to the
  Dirtywave M8 platform (platform ID resolved dynamically on first
  call). Pulls title, author, tags, description, license, download
  counts, and per-patch file attachments. One `RemoteItem` is emitted
  per attached file so users can pick exactly which file to grab.

- **Archive.org** — enumerates files inside known archive items
  (currently `ChipmusicResources`, the M8 Community SD-card Starter
  Pack) via the archive.org metadata API. Surfaces `.m8s`, `.m8i`,
  `.m8t`, `.wav`, and `.zip`/`.7z` bundles.

### HTTP client

A thin coroutine wrapper (`HttpClient`) was added over the existing
OkHttp dependency. Suspend-based, GET-only, hardcoded User-Agent,
with connect / read / call timeouts. All network work is pinned to
the IO dispatcher.

### Error / loading / empty states

The dialog handles every state explicitly: loading spinner, error
screen with retry, empty state, and downloading state. Errors bubble
from sources into the UI with a red indicator.

---

## Content loading — virtual M8 SD card

The download store was restructured to mirror how real M8 hardware
organizes its microSD card. Files now land in `filesDir/m8sd/` under
subdirectories matching the real M8 firmware's folder convention:

```
m8sd/
  Songs/        .m8s
  Instruments/  .m8i
  Samples/      .wav
  Themes/       .m8t
  Scales/       .m8n
  Packs/        .zip / .7z
```

Each download is routed to the correct folder automatically based on
file kind. Collision-safe naming ensures two files with the same name
from different sources don't overwrite each other. A JSON index at
`m8sd/index.json` tracks metadata per entry (source, author, license,
original URL, virtual SD path, download timestamp).

The detail pane now shows **SAVED TO SD → /Instruments/foo.m8i**
after a download, using the virtual M8 path rather than the raw
Android filesystem path.

### SD tab

The browse dialog gained a fourth tab, **SD**, which lists content
already present on the virtual SD card — grouped by folder, in the
same layout the real M8 screen would show. This is both a "what have
I downloaded?" view and the entry point for loading content into the
live emulator.

---

## Content loading — Phase 2: .m8i instrument parser

Downloading content is only half of "load media"; the other half is
actually reading the files into the running emulator. The first file
type to support is `.m8i` (instruments) because it's the smallest,
the most common content on Patchstorage and GitHub repos, and it
exercises the whole download → parse → apply pipeline end-to-end
without requiring any changes to the audio engine.

### Binary parser for V4.x instrument files

A new `M8iParser` object reads the standard M8 file format:

- **14-byte header**: 10-byte ASCII version preamble + packed
  major/minor/patch nibbles + padding. Version is extracted and
  passed to the body parser so V4.0 vs V4.1 differences (transpose
  byte encoding) can be handled correctly.
- **215-byte instrument body**: shared fields (kind, name, transpose,
  table tick, volume, pitch, fine tune) followed by a per-kind
  parameter block followed by 10 bytes of filter/amp/mixer.
- **Per-kind dispatching**: WavSynth, MacroSynth, Sampler, MIDI Out,
  FM Synth, and HyperSynth all have distinct parameter-block
  layouts; each has its own parse function with exact byte offsets
  ported from the Rust `m8-files` reference implementation.
- **Filter/amp/mixer block** (shared across synth kinds): filter
  type/cutoff/resonance, amp, limiter, pan, dry, chorus send, delay
  send, reverb send — all mapped into the existing `M8Instrument`
  data classes.
- **Graceful degradation**: empty slots (kind `0xFF`) return a
  placeholder, unknown kinds fall back to WavSynth defaults, ASCII
  name reading strips nulls and non-printable bytes.

**Known limitation** (called out in code): modulation block (2
envelopes + 2 LFOs) is not parsed yet. Instruments load with correct
static tone but lose any envelope/LFO automation baked into the
original file. This is a deliberate scope cut — the Mod union layout
needs further research against real test files, and falling back to
Kotlin defaults still produces an audible, musically useful
instrument.

### replaceInstrument action

`M8ViewModel` gained `replaceInstrument(slot, newInst)`, which
overwrites a slot in `emulator.instruments` and immediately
reconfigures the corresponding synth voice so the change is audible
on the next note-on. Also exposes `instrumentSlotCount` so the UI
can render the right number of slot buttons.

### Load-into-slot UI

The SD tab detail pane now recognizes instrument entries and shows:

- full metadata (title, virtual SD path, type, author, size, source,
  license)
- an inline **slot picker** — one small numbered button per
  instrument slot (currently 8, matching the emulator's simplified
  slot count)
- a **[LOAD]** button that reads the file from disk, parses it via
  `M8iParser`, and calls `replaceInstrument` with the chosen slot
- a status line with green "LOADED 'NAME' -> SLOT N" on success or
  red "ERROR: ..." on parse failure

Other content kinds (Song / Sample / Pack) show an honest "not yet
implemented" note in the detail pane rather than a dead button.

---

## What's now possible end-to-end

Open the app → tap the Load button (top-right) → GitHub or
Patchstorage tab → browse → tap an instrument → DOWNLOAD → it lands
on the virtual SD card at `/Instruments/foo.m8i` → SD tab → tap the
entry → pick a slot → LOAD → the instrument is live in the running
emulator, audible on the next note.

## What's still deferred

- **Song loader** — `.m8s` files require a larger parser covering
  grooves, song matrix, phrases, chains, tables, instruments block,
  effects, midi, scales, and eq. Version-branching between V4.0 and
  V4.1 offsets. A dedicated session of work.
- **Sample playback** — `.wav` samples can already be downloaded and
  organized, but the native synth engine only generates waveforms;
  sample-based instruments load but won't make sound until sample
  playback is implemented in the audio engine.
- **Modulation block** in `.m8i` files — envelopes and LFOs. See
  parser note above.
- **Older firmware versions** — parser is V4-only. V2/V3 files will
  either throw or produce garbage; detection + branching is a
  follow-up when it comes up.
- **SD management** — no delete/rename yet, downloads accumulate.
