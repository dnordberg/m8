# M8 Headless Android App

Android app for remotely controlling a Dirtywave M8 Headless tracker running on a Linux VPS. The M8 Headless runs on a Teensy 4.1 connected to the VPS via USB. A WebSocket-to-serial bridge and audio stream run on the server.

The app connects to the M8 Headless over the network using WebSocket for display and control, and an audio stream for sound. It replaces what M8WebDisplay does in the browser but runs natively on Android without requiring WebSerial.

## Architecture

```
Android App
+--> Display Layer
|    +-- M8 Screen Renderer (Canvas/OpenGL)
|    +-- Protocol Parser (binary serial protocol)
|    +-- Display Commands Handler
|
+--> Input Layer
|    +-- Touch Controls (on-screen M8 buttons)
|    +-- Gamepad Support (physical controller)
|    +-- Keyboard Support (external keyboard)
|    +-- Key-to-M8 Command Mapper
|
+--> Audio Layer
|    +-- Opus/WebM Decoder
|    +-- Audio Player (low-latency)
|    +-- Volume/Channel Control
|
+--> Network Layer
|    +-- WebSocket Client (serial bridge)
|    +-- Audio Stream Client
|    +-- Connection Manager (auto-reconnect)
|    +-- Tailscale/VPN Detection
|
+--> Settings
     +-- Server Configuration (host, ports)
     +-- Input Mapping
     +-- Audio Settings
     +-- Display Preferences
```

## M8 Serial Protocol

The app implements the M8 display protocol based on the M8WebDisplay source. The protocol is binary and not officially documented by Dirtywave. The reference implementation lives in M8WebDisplay's `js/display.js` and `js/serial.js`.

### Commands FROM M8 (display data)

The M8 sends binary frames containing screen draw commands. The display is 320x240 pixels. Commands include:

- **Rectangle draws** -- position, size, color
- **Character draws** -- position, character, foreground and background colors
- **Waveform data** -- oscilloscope-style waveform display
- **Screen clear/refresh** -- full or partial screen updates

### Commands TO M8 (input)

| Command | Byte(s) | Description |
|---------|---------|-------------|
| Key state | `0x43` + bitmask byte | Send current button state |
| Disconnect | `0x44` | Graceful disconnect |
| Enable + reset | `0x45 0x52` | Enable display and reset state |
| MIDI note | `0x4B` + note data | Send MIDI note information |

**Key bitmask layout:**

| Bit | Button |
|-----|--------|
| 0 | UP |
| 1 | DOWN |
| 2 | LEFT |
| 3 | RIGHT |
| 4 | OPTION |
| 5 | EDIT |
| 6 | SHIFT |
| 7 | PLAY |

Multiple buttons can be held simultaneously by setting multiple bits in a single bitmask byte.

### Connection Sequence

1. Open WebSocket to bridge at `ws://server:8765`
2. Send enable and reset command: `[0x45, 0x52]`
3. Begin receiving display frames
4. Send key commands on user input
5. On disconnect send `[0x44]`

## Technology Stack Options

### Option A -- Kotlin + Jetpack Compose (Recommended)

Native Android with the best performance characteristics.

- OkHttp for WebSocket
- Jetpack Compose Canvas for display rendering
- Oboe for low-latency audio
- Material 3 for settings UI

This option provides the lowest input and audio latency, direct access to Android hardware APIs, and the most control over rendering.

### Option B -- PWA / WebView Wrapper

Wrap a modified M8WebDisplay in a WebView and replace WebSerial calls with WebSocket bridge calls.

- Simpler implementation
- Less performant than native
- Audio latency may be higher
- Limited access to gamepad APIs

### Option C -- Flutter / React Native

Cross-platform with moderate performance.

- Potential to target iOS as well
- Good WebSocket support
- Additional abstraction layer adds latency

## Key Implementation Details

### Display Rendering

The M8 screen is 320x240 pixels with a specific color palette. The recommended approach:

- Maintain a bitmap buffer matching the M8 resolution (320x240)
- Parse incoming binary frames from the WebSocket
- Apply draw commands (rectangles, characters, waveforms) to the buffer
- Blit the buffer to a Canvas or SurfaceView, scaled to fill the device screen
- Target 60fps render loop, though the M8 typically sends frames at around 30fps

### Input Handling

Map on-screen touch buttons to the M8 key bitmask. The bitmask format supports simultaneous button presses natively.

**Default key mapping (matches m8c conventions):**

| M8 Button | Keyboard | Gamepad |
|-----------|----------|---------|
| UP | Arrow Up | D-pad Up |
| DOWN | Arrow Down | D-pad Down |
| LEFT | Arrow Left | D-pad Left |
| RIGHT | Arrow Right | D-pad Right |
| OPTION | Z | A button |
| EDIT | X | B button |
| SHIFT | Left Shift | L shoulder |
| PLAY | Space | Start |

Input sources:

- **Touch** -- on-screen button overlay with haptic feedback
- **Gamepad** -- physical controller via Android input API, map D-pad and face buttons
- **Keyboard** -- external keyboard with configurable mapping

### Audio

The server streams Opus-encoded audio over HTTP or WebSocket. On the Android side:

- Use Oboe (preferred) or AudioTrack for low-latency playback
- Buffer size tuning is critical for the latency vs. glitch tradeoff
- Smaller buffers reduce latency but increase the chance of audio dropouts
- Consider adaptive bitrate based on connection quality
- Decode Opus frames and feed PCM samples to the audio output

### Connection Management

- Auto-detect Tailscale network availability
- Store multiple server profiles for different M8 instances
- Auto-reconnect with exponential backoff on disconnection
- Show a connection status indicator in the UI
- Handle network transitions between WiFi and cellular gracefully

## Android Permissions

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.VIBRATE" />
```

## Project Structure (Kotlin)

```
app/src/main/java/com/m8/
|-- MainActivity.kt
|-- ui/
|   |-- M8Screen.kt            Compose Canvas M8 display
|   |-- M8Controls.kt          Touch button overlay
|   |-- SettingsScreen.kt      Configuration UI
|   |-- ConnectionStatus.kt    Status indicator
|-- protocol/
|   |-- M8Protocol.kt          Binary protocol parser
|   |-- M8Commands.kt          Command constants and builders
|   |-- M8DisplayBuffer.kt     Pixel buffer management
|-- network/
|   |-- M8WebSocketClient.kt   WebSocket connection
|   |-- M8AudioClient.kt       Audio stream receiver
|   |-- ConnectionManager.kt   Reconnect logic
|-- audio/
|   |-- M8AudioPlayer.kt       Low-latency playback
|   |-- OpusDecoder.kt         Audio decoding
|-- input/
|   |-- KeyMapper.kt           Configurable key mapping
|   |-- GamepadHandler.kt      Physical controller
|   |-- TouchHandler.kt        On-screen buttons
|-- data/
    |-- ServerConfig.kt        Server profiles
    |-- Preferences.kt         App settings
```

## Build Setup

```kotlin
// app/build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.m8"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
        targetSdk = 35
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.compose.ui:ui:1.7.0")
    implementation("androidx.compose.material3:material3:1.3.0")
    implementation("com.google.oboe:oboe:1.9.0")
}
```

## Protocol Reference

The exact binary display protocol format needs to be reverse-engineered from M8WebDisplay's JavaScript source, specifically `js/display.js` and `js/serial.js`. The protocol is not officially documented by Dirtywave. The m8c C client is another useful reference for understanding the protocol details.
