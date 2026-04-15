# Conventions

Derived from reading 25+ Kotlin, Python, and Rust source files across UI, emulator, audio, protocol, and DSP layers.

## Kotlin (Android app)

- **Files / classes**: `PascalCase` (`M8Emulator.kt`, `M8ViewModel`)
- **Methods / properties**: `camelCase`
- **Constants**: `UPPER_CASE` in `companion object` blocks
- **Packages**: lowercase, dotted (`com.m8droid.emulator`)
- **Data classes**: default values on most fields; `@dataclass`-style construction
- **Null safety**: leans on `?.` / `?:` / `!!` judiciously; `!!` used where invariants are guaranteed
- **Concurrency**: `@Volatile` on fields shared across audio/UI threads; `synchronized` blocks for `M8DisplayBuffer`; Kotlin coroutines on the UI side
- **Comments**: KDoc on public classes/methods; inline comments sparse
- **Imports**: standard library → AndroidX/third-party → project-local, blank-line separated
- **Logging**: `android.util.Log.d/i/w/e` with class-name tag

## Python (server/)

- **Style**: PEP 8, `snake_case` everywhere
- **Type hints**: yes, with `from __future__ import annotations` at module top
- **Dataclasses**: `@dataclass` with type-annotated fields and defaults
- **Errors**: specific exception types; `try/except` around I/O and subprocess boundaries
- **Logging**: stdlib `logging` with module-level loggers and structured format strings
- **Imports**: standard → third-party → project, blank-line separated
- **Constants**: module-level `UPPER_CASE`

## Rust (m8-synth)

- **Items**: `snake_case` functions/variables, `PascalCase` types, `UPPER_CASE` consts
- **Hot paths**: `#[inline(always)]` on per-sample DSP functions
- **No logging**: DSP code avoids any I/O or logging in the audio render loop
- **Error handling**: relies on safe type design; `.unwrap()` used on JNI boundary (see CONCERNS.md — fragile)
- **Derives**: `#[derive(Clone, Copy, Default, Debug)]` on small DSP structs
- **Modules**: single-file crate (`lib.rs`) at the moment — no submodule tree

## Error Handling — by layer

| Layer | Strategy |
|-------|----------|
| Kotlin UI / ViewModel | try/catch + null-safe chains, user-visible toasts |
| Kotlin emulator | try/catch around parsers; surfaces parse errors as nullable returns |
| Kotlin protocol | bounded buffer; silently truncates oversized frames (fragile) |
| Python server | typed exceptions, logged at `ERROR` level, subprocess restart loop |
| Rust DSP | `.unwrap()` on JNI args; no recovery path |

## Comments / Docs

- **Kotlin**: KDoc on public classes and non-trivial methods. No enforcement.
- **Python**: module docstrings common; function docstrings inconsistent.
- **Rust**: minimal — a few `///` comments on public fns.

## Anti-patterns observed

- `.unwrap()` on JNI args in Rust — panics cross FFI into Android runtime
- Mutable `M8Song` shared between audio and UI threads without explicit lock
- Silent truncation of oversized protocol frames
- No `require`/`check` guards on binary parser inputs
