# M8 Headless Android App

Native Android app for remotely controlling a Dirtywave M8 Headless tracker. Connects over WebSocket to a Linux server running the serial bridge and audio stream services.

## Technology Stack

| Component         | Technology                      |
|-------------------|---------------------------------|
| Language          | Kotlin 2.0.21                   |
| UI Framework      | Jetpack Compose (BOM 2024.12.01)|
| Design System     | Material 3                      |
| WebSocket         | OkHttp 4.12                     |
| Persistence       | DataStore Preferences           |
| Navigation        | Navigation Compose 2.8.5        |
| Audio Playback    | AudioTrack (low-latency mode)   |
| Audio Decoding    | MediaCodec (Opus)               |
| Min SDK           | 26 (Android 8.0)                |
| Target/Compile SDK| 36                              |
| AGP               | 8.7.3                           |
| Build             | Gradle Kotlin DSL               |

## Project Structure

```
app/src/main/java/com/m8/
├── MainActivity.kt              Activity shell, keyboard input dispatch, theme
├── M8ViewModel.kt               AndroidViewModel, connection lifecycle, display refresh
│
├── audio/
│   ├── M8AudioClient.kt         WebSocket audio client (port 8766), reconnection
│   ├── M8AudioPlayer.kt         AudioTrack playback, 48kHz stereo, low-latency
│   └── OpusDecoder.kt           MediaCodec Opus decoder wrapper
│
├── data/
│   └── ServerConfig.kt          DataStore persistence for server settings
│
├── input/
│   └── KeyMapper.kt             Keyboard + gamepad -> M8 key bitmask mapping
│
├── network/
│   ├── ConnectionManager.kt     Coordinator: display + audio connections
│   └── M8WebSocketClient.kt     OkHttp WebSocket client with reconnection
│
├── protocol/
│   ├── M8Commands.kt            Command bytes, key bitmask constants, builders
│   ├── M8DisplayBuffer.kt       320x240 ARGB bitmap, sprite font rendering
│   └── M8Protocol.kt            SLIP decoder + M8 display command parser
│
└── ui/
    ├── ConnectionStatus.kt      Connection state indicator composable
    ├── M8Controls.kt            Touch D-pad + action buttons with haptic feedback
    ├── M8Screen.kt              Compose Canvas renderer for M8 display
    └── SettingsScreen.kt        Server configuration form
```

## Architecture

### Display Pipeline

1. **M8WebSocketClient** receives raw bytes from the serial bridge (port 8765)
2. **M8Protocol** decodes SLIP framing (0xC0 delimiter, 0xDB escape sequences)
3. **M8Protocol** parses SLIP frames into draw commands (DRAW_RECT, DRAW_CHAR, DRAW_WAVEFORM, SYSTEM_INFO)
4. **M8DisplayBuffer** applies commands to a 320x240 ARGB Bitmap using a sprite font
5. **M8Screen** renders the bitmap to a Compose Canvas, scaled to fill the screen

### Audio Pipeline

1. **M8AudioClient** connects to the audio stream (port 8766) via OkHttp WebSocket
2. Incoming binary data is routed through **OpusDecoder** (MediaCodec) or treated as raw PCM
3. Decoded PCM is written to **M8AudioPlayer** (AudioTrack at 48kHz stereo, ~40ms buffer)
4. AudioTrack runs in `PERFORMANCE_MODE_LOW_LATENCY`

### Input Pipeline

1. Touch events from **M8Controls** (on-screen D-pad + buttons with haptic feedback)
2. Keyboard events from **MainActivity** (dispatched through `onKeyDown`/`onKeyUp`)
3. Gamepad button events (BUTTON_A, BUTTON_B, L1, START, D-pad)
4. All mapped via **KeyMapper** to M8 key bitmask values
5. Sent through **ConnectionManager.sendKeyState()** as `[0x43, bitmask]`

### Connection Management

**ConnectionManager** coordinates both connections:

- Creates **M8WebSocketClient** for display data (port 8765)
- Creates **M8AudioClient** for audio stream (port 8766)
- Handles connection lifecycle: connect, disconnect, enable display
- Routes control messages from the bridge (serial connected/disconnected)
- Exposes connection state and audio state as Kotlin StateFlows

**M8WebSocketClient** features:
- OkHttp WebSocket with 20-second ping interval
- Connection states: DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING
- Exponential backoff reconnection: 2s, 4s, 8s, 16s, 30s (max)
- Binary messages routed to protocol parser; text messages routed as control messages

## Key Mappings

| M8 Button | Keyboard (Primary) | Keyboard (WASD) | Gamepad        |
|-----------|---------------------|------------------|----------------|
| UP        | Arrow Up / D-pad Up | W                | D-pad Up       |
| DOWN      | Arrow Down / D-pad Down | S            | D-pad Down     |
| LEFT      | Arrow Left / D-pad Left | A            | D-pad Left     |
| RIGHT     | Arrow Right / D-pad Right | D          | D-pad Right    |
| OPTION    | Z                   |                  | A button       |
| EDIT      | X                   |                  | B button       |
| SHIFT     | Left Shift          |                  | L1 shoulder    |
| PLAY      | Space               |                  | Start / R1     |

## M8 Protocol Implementation

### SLIP Framing

The M8 serial protocol uses SLIP framing. M8Protocol.kt processes a raw byte stream:

- `0xC0` marks frame boundaries
- `0xDB 0xDC` decodes to `0xC0` (escaped END)
- `0xDB 0xDD` decodes to `0xDB` (escaped ESC)
- Frame buffer: 8192 bytes

### Display Commands (M8 -> Client)

| Command | Byte   | Size    | Fields |
|---------|--------|---------|--------|
| DRAW_RECT | `0xFE` | 12B | x(u16) y(u16) w(u16) h(u16) r g b |
| DRAW_CHAR | `0xFD` | 12B | x(u16) y(u16) char fg_r fg_g fg_b bg_r bg_g bg_b |
| DRAW_WAVEFORM | `0xFC` | 8+B | x(u16) y(u16) r g b wavedata[N] |
| SYSTEM_INFO | `0xFF` | 6+B | fw_major fw_minor fw_patch ... |

### Input Commands (Client -> M8)

| Command | Bytes | Description |
|---------|-------|-------------|
| KEY_STATE | `0x43` + bitmask | Current button state |
| DISCONNECT | `0x44` | Graceful disconnect |
| ENABLE_DISPLAY | `0x45` | Request display data |
| RESET_DISPLAY | `0x52` | Reset display state |

### Connection Sequence

```
1. WebSocket connect to ws://host:8765
2. Receive JSON: {"event": "serial_connected", "device": "..."}
3. Send: [0x45, 0x52] (enable + reset display)
4. Receive SLIP-framed display commands, render to bitmap
5. Send: [0x43, bitmask] on user input
6. On disconnect: send [0x44]
```

## Audio Details

**M8AudioPlayer** configuration:
- Sample rate: 48000 Hz
- Channels: stereo (CHANNEL_OUT_STEREO)
- Format: 16-bit PCM (ENCODING_PCM_16BIT)
- Buffer: ~40ms (`48000 * 4 * 0.040 = 7680 bytes`)
- Mode: PERFORMANCE_MODE_LOW_LATENCY

**OpusDecoder** uses Android MediaCodec:
- Creates CSD (Codec Specific Data) buffers per RFC 7845
- CSD-0: Opus identification header (OpusHead, 48kHz, stereo)
- CSD-1: Pre-skip (0 ns)
- CSD-2: Seek pre-roll (80ms)
- Decodes individual Opus frames to PCM

**M8AudioClient** audio routing:
- Checks for format header byte: `0x01` = raw PCM, `0x02` = Opus
- Falls back to treating all data as raw PCM if no header detected
- Computes RMS audio level for UI meters

## Server Configuration

**ServerConfig** persists settings via DataStore Preferences:

| Setting      | Key             | Default        |
|--------------|-----------------|----------------|
| Host         | `server_host`   | `100.64.0.1`  |
| Port         | `server_port`   | `8765`         |
| Auto-connect | `auto_connect`  | `true`         |

The audio port is derived as `DEFAULT_AUDIO_PORT = 8766` (hardcoded in ConnectionManager).

## Android Permissions

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.VIBRATE" />
```

## Build Configuration

```kotlin
// app/build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.m8"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.m8"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // ... see app/build.gradle.kts for full list
}
```
