# DECISIONS.md

Decisions made recently without much explicit input from you. Flagged
so you can override any that don't match your intent.

## Input handling

- **`dispatchKeyEvent` over `onKeyDown`/`onKeyUp`.** Moved hardware-key
  capture up to the Activity level so focused Compose views can't
  swallow events. Trade-off: bypasses normal Compose focus model.
- **Down arrow sends `KEY_DOWN` + `KEY_SHIFT` simultaneously.** Mirrors
  the real M8 Model 01 where DOWN and SHIFT are the same physical key.
  Side effect: you can no longer send a pure DOWN from a keyboard; use
  the dedicated shift keys if you need isolated behavior.
- **Repeat-count guarding on held keys.** Screen-switch hotkeys no
  longer spam when held. Chosen value hardcoded.

## Content loading architecture

- **Virtual M8 SD layout under `filesDir/m8sd/`.** Folder names
  (`Songs/`, `Instruments/`, `Samples/`, `Themes/`, `Scales/`,
  `Packs/`) match real M8 firmware convention rather than any Android
  idiom. Locks us into that naming.
- **`ContentSource` interface, three live sources.** GitHub,
  Patchstorage, Archive.org. New sources drop in without touching the
  dialog. Curated GitHub repo list is hardcoded
  (`laamaa/m8i`, `trash80/M8HeadlessFirmware`).
- **One `RemoteItem` per Patchstorage attachment** (rather than one per
  patch). Lets users grab individual files but inflates list length.
- **JSON index at `m8sd/index.json`** for per-entry metadata. Chosen
  over SQLite / DataStore — simpler, human-readable, but no
  transactional safety.
- **Collision-safe rename on download** rather than overwrite or
  prompt. Silent; you won't see name clashes.
- **Thin `HttpClient` coroutine wrapper over OkHttp**, GET-only,
  hardcoded User-Agent, IO dispatcher. Not a general client.

## `.m8s` song parser — one-click load

Implemented in `M8sParser.kt`. Reference: `AlexCharlton/m8-files`
(V4_OFFSETS). Test fixtures checked in under
`app/src/test/resources/m8songs/` (`V4EMPTY.m8s`, `CMDMAPPING_4_0.m8s`
from that same repo's `examples/songs/`).

**Parsed (playback-critical):** header (version, tempo, transpose,
quantize, name), song grid (256×8), phrases (255×16 steps, 9 bytes
each: note/vel/inst/3×FX), chains (255×16 rows), tables (256×16
rows).

**Not parsed (deferred):** instrument pool at `0x13A3E`, mixer
settings, effects/EQ, grooves, scales, MIDI mappings, directory.
Consequence: a loaded song plays against the emulator's *existing*
default instrument slots — phrase steps that reference instrument N
still produce sound, but not the timbre the author intended.
Following up means porting `M8iParser` per-subtype body parsing to
the `.m8s` instrument block. Scope cut because the user explicitly
OK'd "few hundred lines," and a full instrument-pool parser roughly
doubles that.

**Version support:** V4.x only (`major == 4`). 4.0 and 4.1 share
offsets for everything we read. Older versions rejected.

**Offset strategy:** absolute offsets over sequential reads. Lets us
skip intermediate sections (MidiSettings, MixerSettings, Grooves)
without knowing their exact layout. Fragile if Dirtywave ever ships a
V5 that relocates things — acceptable because the Rust reference does
the same.

**Signed transpose bytes:** `ChainRow.transpose` and
`TableRow.transpose` are signed on the M8 UI (`%+03d` display) but
stored as `u8` on disk. Parser reads them with plain `.toInt()` so
`0x80` → `-128`. All other byte fields are `and 0xFF`-masked.

**UX:** on the remote tab, `[DOWNLOAD]` becomes `[SAVE + LOAD]` for
songs — downloads, saves to SD, parses, and applies in one click. On
the SD tab, the song detail pane gains a `[LOAD SONG]` action
(previously a "coming next phase" stub).

**No formal unit test.** The project has no `testImplementation`
dependencies or test source sets. Setting up JUnit for one parser
test was more yak-shaving than warranted. Parser byte offsets and
struct layouts were validated against the real fixture files with
Python binary inspection before writing the Kotlin. Fixtures are
checked in, ready for a future test phase.

## `.m8i` parser scope cuts

- **V4.x only.** V2/V3 files will throw or produce garbage. No version
  detection branching yet.
- **Modulation block (2 envelopes + 2 LFOs) not parsed.** Instruments
  load with correct static tone but lose envelope/LFO automation from
  the original file. Falls back to Kotlin defaults. Flagged in code.
- **Unknown instrument kinds fall back to WavSynth defaults** rather
  than erroring. Hides real format drift behind "it still makes
  sound."
- **Empty slots (`0xFF`) return a placeholder** silently.

## Emulator integration

- **8 instrument slots** in the UI slot picker. Matches the emulator's
  simplified slot count, not real M8 hardware's capacity.
- **`replaceInstrument` reconfigures the synth voice immediately** on
  load, audible on next note-on. No undo.
- **Song / Sample / Pack show "not yet implemented"** in the detail
  pane rather than a disabled button. Honest but dead UI surface.

## Deferred / not done (explicitly)

- `.m8s` instrument pool parsing (grid + notes load, but timbre
  comes from the emulator's default instruments, not the song's)
- Sample playback in the audio engine (samples download but are
  silent)
- SD delete/rename — downloads accumulate indefinitely
- Older firmware version support in `.m8i` parser
