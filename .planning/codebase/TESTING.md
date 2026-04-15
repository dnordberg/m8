# Testing

## Current State: None

**This codebase has no test infrastructure.** This is the single most notable quality finding.

| Area | Status |
|------|--------|
| Android unit tests | `app/src/test/` is empty |
| Android instrumented tests | `app/src/androidTest/` not present |
| JUnit / Mockito / Robolectric deps | Not declared in `app/build.gradle.kts` |
| Python tests | No `pytest`/`unittest` files in `server/` |
| Python test config | No `pytest.ini`, `pyproject.toml` test section, or `tox.ini` |
| Rust tests | No `#[cfg(test)]` modules in `m8-synth/src/lib.rs` |
| Rust integration tests | No `tests/` directory in `m8-synth/` |
| CI / CD | No `.github/workflows/`, Jenkinsfile, or equivalent |
| Coverage | Not measured |

## Implications

- No automated regression detection for an engine that has 1200+ line `M8Emulator.kt`, 1579-line `m8_emulator.py`, 615-line Rust DSP, and a binary SLIP protocol decoder.
- Parsers (`M8sParser`, `M8iParser`) have no fuzzing or malformed-input coverage — bug-risk surface flagged in CONCERNS.md.
- Rust↔Kotlin JNI boundary has no contract tests.
- Sequencer timing, FX engine, and synth DSP are verified only by manual listening.

## Recommended Test Seeds (for when testing is added)

| Priority | Target | Framework |
|----------|--------|-----------|
| P0 | `M8sParser` / `M8iParser` — fuzz with malformed binaries | JUnit + `kotlinx.fuzz` or property tests |
| P0 | `M8Protocol` SLIP decoder — frame boundaries, oversized frames | JUnit |
| P0 | `M8FxEngine` command dispatch — per-command expected output | JUnit |
| P1 | `m8-synth` DSP — golden-sample rendering tests | Rust `#[cfg(test)]` with `insta` snapshots |
| P1 | `M8ViewModel` sequencer timing — fake audio clock | JUnit + coroutines-test |
| P1 | `server/m8_emulator.py` — integration tests against recorded WebSocket sessions | `pytest` |
| P2 | Remote reconnect / heartbeat behavior | `pytest` + mock WebSocket |

## Mocking / Fixtures

- No fixtures directory exists.
- Recommend adding `app/src/test/resources/` for `.m8s` and `.m8i` samples once parser tests are introduced.
