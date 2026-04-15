# Technology Stack

**Analysis Date:** 2026-04-15

## Languages

**Primary:**
- Kotlin 2.0.21 - Android app UI and logic
- Python 3.x - Server bridges and emulator
- Rust 2021 edition - Native audio DSP for Android

**Secondary:**
- Java 17 - Android compilation target (Kotlin compiles to JVM bytecode)
- XML - Android manifest, build configuration

## Runtime

**Environment:**
- Android Runtime (ART) - Executes compiled bytecode on Android 12+ (targetSdk 36)
- Python 3.x runtime - Executes server scripts (bridge.py, audio_stream.py, m8_emulator.py)
- JVM - Kotlin/Java runtime on Android

**Package Manager:**
- Gradle 8.12 - Android build system and dependency management
- Python pip - Python package management (requirements.txt)
- Cargo - Rust package manager

## Frameworks

**Core:**
- Jetpack Compose 2024.12.01 - Modern declarative UI toolkit for Android
- Androidx Activity 1.9.3 - Activity lifecycle management
- Androidx Navigation 2.8.5 - Fragment/screen navigation
- Androidx Lifecycle 2.8.7 - Lifecycle-aware components
- Androidx DataStore 1.1.1 - Preference storage (replaces SharedPreferences)

**Networking:**
- OkHttp 4.12.0 - HTTP client with WebSocket support
- websockets (Python) 12.0+ - WebSocket server library for Python

**Audio:**
- Opus codec (via opus-tools) - Audio compression for streaming

**DSP/Audio Engine:**
- JNI (Java Native Interface) 0.21 - Bridge between Kotlin and Rust

**Build/Dev:**
- Android Gradle Plugin 8.7.3 - Android build plugin
- Kotlin Plugin 2.0.21 - Kotlin language support
- Kotlin Compose Plugin 2.0.21 - Compose compiler

## Key Dependencies

**Critical:**
- okhttp3:okhttp (4.12.0) - WebSocket communication with server bridges
- androidx.compose:compose-bom (2024.12.01) - All Compose UI components
- jni (0.21.1 Rust) - Android NDK bindings for native audio synthesis

**Infrastructure:**
- kotlinx-coroutines-android (1.9.0) - Async/await and structured concurrency
- androidx.datastore:datastore-preferences (1.1.1) - Persistent configuration storage
- android.material (1.12.0) - Material Design components

**Python Server:**
- pyserial (3.5+) - Serial port communication with Teensy 4.1
- pyserial-asyncio (0.6+) - Async serial operations
- websockets (12.0+) - WebSocket server for client connections
- numpy (optional) - Signal processing in emulator

## Configuration

**Environment:**
- Android SDK 36 (compileSdk)
- Android MinSdk 26 (Android 8.0)
- JVM target 17
- Kotlin/Compose target JVM 17

**Build:**
- `build.gradle.kts` - Root build configuration
- `app/build.gradle.kts` - App module configuration (Compose, dependencies)
- `settings.gradle.kts` - Project structure (includes :app module)
- `m8-synth/Cargo.toml` - Rust synth library (builds to .so native library)
- `server/requirements.txt` - Python dependencies

## Platform Requirements

**Development:**
- Android Studio or IntelliJ IDEA with Kotlin support
- Android SDK 36
- NDK (required for Rust compilation to ARM native library)
- Gradle 8.12
- Python 3.7+ (for server scripts)
- ALSA utils (`apt-get install alsa-utils opus-tools`) for audio capture on Teensy

**Production:**
- Android 8.0+ (minSdk 26)
- Python 3.7+ runtime on Teensy + host machine (bridge.py, audio_stream.py)
- OR: Python 3.7+ emulator mode on development machine (m8_emulator.py)
- Serial connection to Teensy 4.1 (via USB, vendor ID 0x16C0 PJRC)
- Network connectivity for WebSocket (localhost or LAN)

## Native Compilation

**Rust → ARM:**
- m8-synth/Cargo.toml builds to cdylib (shared object library)
- Profile: release with LTO, optimized (opt-level 3)
- Output: libm8_synth.so for Android ARM64
- Loaded via JNI from `com.m8droid.audio.NativeSynth`

---

*Stack analysis: 2026-04-15*
