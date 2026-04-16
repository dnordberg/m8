# Firmware Deviations

This file tracks every place `M8Emulator.kt` intentionally differs from, simplifies,
or extends the real Dirtywave M8 Headless Firmware. It is the single source of truth
for "what's ours vs. what's theirs" when porting a new M8 firmware release.

**Last checked against M8 firmware version:** *(unknown — not yet verified against hardware)*

**How to use this file:**
- When you make a deliberate behavior change in the emulator, add an entry here.
- When Dirtywave ships new firmware, walk this file top to bottom and decide whether
  each deviation still applies, needs updating, or has been superseded by upstream.
- See `FIRMWARE_SYNC.md` for the full update playbook.

**Status legend:**
- `confirmed` — verified against real M8 hardware or Dirtywave source
- `believed` — inferred from docs/reference clients; needs verification
- `stub` — placeholder implementation, known incomplete

---

## 1. Shift+Arrow view navigator uses a 4-row grid with PROJECT on top

- **Status:** believed
- **Files:** `app/src/main/java/com/m8droid/emulator/M8Emulator.kt:319-348`
- **What:** Shift+Arrow navigates a 4×3 grid:
  ```
  row 0: PROJECT PROJECT PROJECT   ← Shift+Up from any main-row screen lands here
  row 1: SONG    CHAIN    PHRASE
  row 2: INSTR   TABLE    MIXER
  row 3: FX      CONFIG   -
  ```
- **Why we deviate:** Real M8's view navigator is a 3×3 grid with different slot
  contents (Song/Chain/Phrase, Instr/Table/Envelope, Groove/Scale/Project). We don't
  have ENVELOPE, GROOVE, or SCALE screens yet, so our grid is filled with the screens
  we *do* have. PROJECT was shoehorned as a wide top row so Shift+Up always reaches it.
- **Port action on new firmware:** If Dirtywave adds/removes screens or changes grid
  layout, update `screenGrid` in `M8Emulator.kt` to match. Once ENVELOPE/GROOVE/SCALE
  exist in our emulator, collapse the PROJECT row and move PROJECT into its real slot.

## 2. OPT = previous screen, EDIT = next screen (linear cycle)

- **Status:** believed to deviate
- **Files:** `app/src/main/java/com/m8droid/emulator/M8Emulator.kt:281-295`
- **What:** Pressing OPTION alone cycles to the previous screen in `SCREEN_NAMES`
  order; pressing EDIT alone cycles to the next one.
- **Why we deviate:** Added early as a convenience before Shift+Arrow navigation
  existed. On real M8, OPTION and EDIT do not change screens — they modify the
  current selection or toggle edit mode.
- **Port action:** Probably remove entirely now that Shift+Arrow works. Preserved for
  now because existing workflows / tutorials may rely on it. Revisit after the view
  grid is complete.

## 3. Shift+EDIT toggles edit mode on PHRASE screen only

- **Status:** believed to deviate
- **Files:** `app/src/main/java/com/m8droid/emulator/M8Emulator.kt:288-294`
- **What:** On the PHRASE screen, Shift+EDIT toggles a custom `editMode` flag that
  repurposes arrow keys as semitone/octave nudgers for the cursor note.
- **Why we deviate:** Real M8 uses a different input model for note editing (tap a
  cell, hold EDIT, etc.). Our `editMode` is a simplified stand-in.
- **Port action:** When implementing full phrase editing, replace this with whatever
  the real M8 input flow is. Check the m8c reference client for the canonical behavior.

## 4. Shift-release (without chord) bumps octave

- **Status:** believed to deviate
- **Files:** `app/src/main/java/com/m8droid/emulator/M8Emulator.kt:297-303`
- **What:** Releasing SHIFT alone (when it wasn't used as a modifier for arrows or
  edit) increments `octave` modulo 8.
- **Why we deviate:** The real M8 changes octave via a specific combo (believed to be
  Shift+Left/Right on the INSTRUMENT screen, or the dedicated octave control). Using
  shift-tap as the edge trigger lets us demo octave changes without implementing the
  full contextual controls.
- **Port action:** Replace with the canonical octave control once the relevant screen
  inputs are implemented. Delete the `shiftChordActive` flag at the same time.

## 5. Cursor X-bounds are hardcoded per screen

- **Status:** stub
- **Files:** `app/src/main/java/com/m8droid/emulator/M8Emulator.kt:260-271`
- **What:** The `maxX` for cursor movement is a hand-written `when` per screen:
  SONG/PHRASE=7, CHAIN=1, TABLE=4, MIXER=7, INSTRUMENT/FX/CONFIG/PROJECT=0.
- **Why we deviate:** Real M8 PHRASE has 9 editable columns (NOTE / INSTR / VOL /
  FX1CMD / FX1VAL / FX2CMD / FX2VAL / FX3CMD / FX3VAL), CHAIN has 2, and so on. Our
  numbers are best-guess placeholders that match what we currently *render*, not what
  the real firmware supports.
- **Port action:** When each screen gets full rendering and editing, audit `maxX`
  against the real column count and update this file.

## 6. PROJECT screen contents are a stub

- **Status:** stub
- **Files:** `app/src/main/java/com/m8droid/emulator/M8Emulator.kt:492-516`
- **What:** PROJECT shows only NAME / TRANSPOSE / TEMPO / QUANTIZE / MIDI CHANNEL /
  KEY, all non-editable.
- **Why we deviate:** Real M8 PROJECT has many more fields (sample rate, MIDI routing,
  CC mappings, finetune, swing, etc.) and everything is editable.
- **Port action:** Expand fields when implementing project-level settings. Cross-check
  against the current firmware's PROJECT screen at that time.

## 7. Missing screens: GROOVE, ENVELOPE, SCALE

- **Status:** stub (missing feature)
- **Files:** `app/src/main/java/com/m8droid/emulator/M8Emulator.kt:38-50`
- **What:** `SCREEN_NAMES` has 8 entries. Real M8 has additional screens: GROOVE,
  ENVELOPE (per-instrument, reachable from INSTRUMENT), SCALE, and LIVE/PLAY mode.
- **Why we deviate:** Not yet implemented.
- **Port action:** When adding any missing screen, add a `SCREEN_*` constant, extend
  `SCREEN_NAMES`, add render + input handlers, and rework the `screenGrid` (see #1).

## 8. Right-panel status cluster uses a custom SCPIT row

- **Status:** believed to deviate
- **Files:** `app/src/main/java/com/m8droid/emulator/M8Emulator.kt:581-594`
- **What:** Bottom-right panel shows a vertical `P / SCPIT / M` cluster where each
  glyph highlights when the corresponding screen is active. The "P" on top represents
  PROJECT.
- **Why we deviate:** Real M8 has a similar indicator region but uses different
  glyphs and layout. Ours is a pragmatic approximation for visual feedback.
- **Port action:** Cross-check glyph set, positions, and highlight meaning when doing
  a visual diff against a real M8 screenshot.

## 9. `SCREEN_NAMES` tab row rendered at the top of every non-PROJECT screen

- **Status:** believed to deviate
- **Files:** `app/src/main/java/com/m8droid/emulator/M8Emulator.kt:1047-1062` (`renderScreenHeader`)
- **What:** We draw a horizontal tab row "SONG CHAIN PHRASE ..." with the current
  screen highlighted. This is rendered on every screen except PROJECT.
- **Why we deviate:** Real M8 does not show a persistent tab row at the top — it uses
  the right-panel indicator only. Our tab row was added to make keyboard-driven
  navigation discoverable.
- **Port action:** Consider removing once the view navigator overlay (the one the
  user sees when holding Shift) is implemented as an on-demand transient popup.

## 10. Cursor Y hard-clamped to 15

- **Status:** believed correct (16 rows is M8 standard)
- **Files:** `app/src/main/java/com/m8droid/emulator/M8Emulator.kt:239, 246`
- **What:** `cursorY` is clamped to `0..15` regardless of screen.
- **Why we deviate:** Minor — real M8 SONG view has 256 rows with scroll. We don't
  yet implement vertical scrolling, so all screens visually max out at 16 rows.
- **Port action:** When implementing SONG scroll, replace the hard clamp with a
  screen-aware max row and a scroll offset. File a deviation entry for the scroll
  behavior at that time.

---

## How to add a new entry

When you make a deliberate change that deviates from or extends firmware behavior:

1. Add a numbered section here with: status, files + line numbers, what, why, and a
   port action describing what to do on the next firmware update.
2. Prefer "why" over "what" — the code already says what. The why is what you'll
   forget in six months.
3. If you're *fixing* a deviation (bringing us closer to firmware parity), don't
   delete the entry — mark it `resolved` with the commit hash and keep it for history.
