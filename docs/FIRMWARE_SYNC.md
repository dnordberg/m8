# Firmware Sync Playbook

This document is the process for keeping M8droid in sync with new Dirtywave M8
firmware releases. Run through it top-to-bottom every time a new firmware version
ships.

## Context (read this first)

M8droid does **not** vendor or fork any Dirtywave source. `M8Emulator.kt` is an
independent Kotlin reimplementation of enough M8 behavior to be useful offline.
That means "applying your changes to the new firmware" is not a `git merge` — it's
a behavioral spec problem:

1. Read what changed upstream.
2. Decide what of that to port into our Kotlin code.
3. Re-verify that every entry in `FIRMWARE_DEVIATIONS.md` still makes sense in
   light of the new upstream behavior.
4. Bump the "last checked against" version in `FIRMWARE_DEVIATIONS.md`.

Nothing here is automated. The whole point of this document is to make the manual
process repeatable and hard to forget.

## Upstream sources to watch

Primary:
- **M8 Headless Firmware** — https://github.com/Dirtywave/M8HeadlessFirmware
  (releases page is the trigger for this playbook)
- **Dirtywave release notes** — https://dirtywave.com/ (changelog posts)

Reference clients (useful when upstream commit messages are terse):
- **m8c** — https://github.com/laamaa/m8c (native desktop client, C)
- **M8WebDisplay** — https://github.com/Dirtywave/M8WebDisplay (browser client, JS)

When firmware changes break a reference client, those clients usually get updated
within days. Diffing *their* commits against the last-known-good version is often
faster than decoding the firmware diff directly.

## The playbook

### Step 1 — Record the starting point

Before touching anything:

```bash
cd /Users/danielnordberg/code/m8
git checkout -b firmware-sync-<version>   # e.g. firmware-sync-v7.0
```

Open `docs/FIRMWARE_DEVIATIONS.md` and note the current "last checked against"
version. This is your baseline.

### Step 2 — Read the upstream changelog

Read, in this order:
1. The Dirtywave release post / changelog (human summary).
2. The `M8HeadlessFirmware` GitHub release notes.
3. Any commits in `m8c` tagged with the same version bump.

Capture anything that could affect M8droid into a scratch list. Look specifically
for:

- **New screens** (new `SCREEN_*` entries needed)
- **Changes to the view navigator grid** (affects `screenGrid` in `M8Emulator.kt`)
- **New or changed key bitmask bits** (affects `M8Commands.kt`)
- **New SLIP draw commands** (affects `M8Protocol.kt` + `M8DisplayBuffer.kt`)
- **Changes to PROJECT / CONFIG fields**
- **Instrument parameter changes** (affects `M8Instrument.kt`, synth mapping)
- **Song / chain / phrase format changes** (affects `M8Song.kt`, parsers)
- **Protocol version bumps** (SLIP framing or command IDs)

### Step 3 — Diff the protocol constants

Fast mechanical check: compare our protocol constants against upstream.

```bash
# Inspect our current constants
```

Files to cross-check:
- `app/src/main/java/com/m8droid/protocol/M8Commands.kt` — key bitmasks, command IDs,
  draw-op byte values.
- `app/src/main/java/com/m8droid/protocol/M8Protocol.kt` — SLIP decoding.
- `app/src/main/java/com/m8droid/protocol/M8DisplayBuffer.kt` — draw-op handlers.

If a new draw op was added upstream, M8droid must at minimum decode it without
crashing when talking to real hardware in remote mode. Rendering it is a second step.

### Step 4 — Walk `FIRMWARE_DEVIATIONS.md` top to bottom

For every entry:

1. **Read the "Why" line.** Does the reason still apply given what upstream just
   changed?
2. **Three possible outcomes:**
   - **Still valid** — update the "Last checked against" line in the header only.
   - **Resolved by upstream** — mark the entry `resolved` (don't delete), note the
     firmware version and the commit that brings us into line, and do that commit.
   - **Needs updating** — upstream changed in a way that makes our deviation wrong.
     Update the deviation entry, change the code, and verify.
3. **Never silently delete an entry.** History is the point — a future contributor
   looking at odd code should be able to trace why it exists.

### Step 5 — Port upstream changes into the emulator

For each upstream change from Step 2 that isn't already covered by a deviation:

1. Implement it in the relevant Kotlin file (emulator, protocol, synth, parsers).
2. If your implementation is simpler than, extends, or otherwise differs from
   upstream, **add a new deviation entry in `FIRMWARE_DEVIATIONS.md`**. Don't wait.
3. Build: `./gradlew :app:compileDebugKotlin`
4. If you have a real M8 and the new firmware flashed, spot-check the behavior in
   remote mode (Settings → connect to server → compare screens side by side).

### Step 6 — Update version metadata

1. Bump `docs/FIRMWARE_DEVIATIONS.md` header: `Last checked against M8 firmware version: vX.Y`
2. Add a line to `CHANGES.md` under this sync: "Synced against M8 firmware vX.Y. See
   `docs/FIRMWARE_DEVIATIONS.md` for current deviation list."
3. Tag the commit: `git tag firmware-sync-vX.Y`

### Step 7 — Open the sync PR

Commit the changes on the `firmware-sync-<version>` branch. PR body should include:

- Upstream version synced against and link to the release notes.
- One-line summary per upstream change that was ported.
- One-line summary per deviation that was updated or resolved.
- Anything you chose *not* to port and why (e.g. "new ENVELOPE screen — skipped,
  filed as deviation #7, requires new synth wiring").

## What to do when you don't have real M8 hardware

Most of M8droid's maintainers may not own a Teensy. Without hardware you can still:

- Read firmware release notes and code.
- Read `m8c` and `M8WebDisplay` commits for the same version to see how those
  clients interpreted the change.
- Port changes as best you can and mark the new deviations `believed` rather than
  `confirmed`.
- Ask someone with hardware to spot-check in a follow-up PR.

What you **cannot** do without hardware is confirm frame-exact visual parity or
audio behavior. Don't pretend otherwise in deviation entries — `believed` is
honest, `confirmed` is not.

## When a sync is urgent

If a firmware release breaks remote mode (M8droid talking to real hardware), the
fast path is:

1. Skip Step 4 (deviation walk) and go straight to Steps 3 + 5.
2. Get the protocol layer decoding the new frames without crashing.
3. Ship a minimal fix.
4. Come back and do the full walk as a follow-up PR.

The deviation walk is important but it's a code-hygiene task, not an incident
response.
