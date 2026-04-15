# Concerns

Technical debt, known issues, and fragile areas identified during codebase mapping.

## Critical

### 1. Parser buffer vulnerabilities
**Where:** `app/src/main/java/com/m8droid/emulator/M8sParser.kt`, `M8iParser.kt`
**Issue:** Binary `.m8s` / `.m8i` parsing lacks comprehensive bounds checking.
**Risk:** `IndexOutOfBoundsException` / crash on malformed or truncated files.
**Fix direction:** Guard every read with length checks; return typed parse errors; add fuzz tests.

### 2. Protocol frame overflow
**Where:** `app/.../protocol/M8Protocol.kt`
**Issue:** Fixed 8 KB decode buffer silently truncates oversized SLIP frames.
**Risk:** Display corruption, silent data loss, hard-to-diagnose UI glitches.
**Fix direction:** Dynamic buffer or explicit error on overflow; log truncation at `WARN`.

### 3. Rust JNI panics
**Where:** `m8-synth/src/lib.rs`
**Issue:** Extensive `.unwrap()` calls on JNI args and allocation results.
**Risk:** Panics cross the FFI boundary and abort the Android process on allocation failure or unexpected input.
**Fix direction:** Replace `.unwrap()` with `Result` + JNI exception throws; catch panics at FFI boundary.

### 4. No WebSocket encryption / auth
**Where:** `server/bridge.py`, `server/m8_emulator.py`, `app/.../network/M8WebSocketClient.kt`
**Issue:** Remote mode uses plain `ws://` with no TLS and no authentication.
**Risk:** Traffic is snoopable; any client on the network can connect and control the bridge.
**Fix direction:** TLS (`wss://`), shared-secret or token auth, bind to loopback by default.

### 5. Subprocess audio encoding fragility
**Where:** `server/audio_stream.py`
**Issue:** Spawns `opus-tools` subprocess without heartbeat or watchdog.
**Risk:** Silent audio dropout if the subprocess dies; no recovery.
**Fix direction:** Heartbeat monitor + auto-restart; surface failure to client.

### 6. Display buffer race conditions
**Where:** `app/.../protocol/M8DisplayBuffer.kt`
**Issue:** Per-method `synchronized` blocks do not provide atomicity across multi-frame operations.
**Risk:** Partial frames, tearing, intermittent corruption under load.
**Fix direction:** Batch commands inside a single lock; or switch to a lock-free double buffer.

## High

### 7. No test infrastructure
See `TESTING.md`. The codebase has zero automated tests covering a 1200-line emulator, 615-line DSP engine, and a binary protocol decoder.

### 8. No song export / save
**Issue:** User edits to the in-emulator song cannot be persisted or exported back to `.m8s`.
**Risk:** Feature gap — user work is lost on exit. Blocks real authoring use.

### 9. Missing display bounds validation
**Where:** `M8DisplayBuffer.kt` draw commands
**Issue:** Coordinate ranges are not validated before writing into the pixel buffer.
**Risk:** Out-of-bounds writes on malformed draw commands.

### 10. Long-running bridge.py memory growth
**Where:** `server/bridge.py`
**Issue:** No evident cleanup of stale WebSocket references; potential accumulation over a long session.
**Risk:** Slow memory leak on long sessions.

## Performance

- **Python synth in audio loop**: `M8Synth.kt` is a fallback but `server/m8_emulator.py` synthesis is Python-side — consider delegating to Rust for parity with native path.
- **No dirty-region tracking**: full 320×240 buffer repaints every frame even when most pixels are unchanged.
- **Per-sample frequency lookup**: frequency table recomputed per sample rather than cached per trig.
- **WebSocket broadcast blocking**: slow clients can stall the audio/state broadcast loop.

## Test Gaps (highest-value)

1. Parser fuzzing with corrupted `.m8s` / `.m8i` files
2. Protocol frame boundary and overflow cases
3. Network reconnection / stress tests for `M8WebSocketClient`
4. Audio thread race conditions around `M8Song` mutation
5. Low-memory scenarios in the Rust synth (allocation panic path)
6. End-to-end: emulator → synth → AudioTrack integration

## Summary

The biggest structural risk is **the combination of binary parsing + concurrent audio rendering + zero tests**. Of the individual issues, the highest leverage fixes are (1) parser bounds checking, (3) removing `.unwrap()` from the JNI surface, and (7) seeding a test suite — these unblock safe future changes to the emulator and DSP layers.
