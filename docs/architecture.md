# m8 Architecture Overview

System architecture for the m8 Android app -- a remote client for controlling and monitoring a Dirtywave M8 Headless tracker running on a Teensy 4.1 connected to a Linux server via USB.

## Table of Contents

- [Problem Statement](#problem-statement)
- [System Architecture](#system-architecture)
- [Component Details](#component-details)
- [Protocol Reference](#protocol-reference)
- [Network Topology](#network-topology)
- [Data Flow](#data-flow)
- [Security Considerations](#security-considerations)
- [Failure Modes and Recovery](#failure-modes-and-recovery)

---

## Problem Statement

The Dirtywave M8 Headless firmware communicates exclusively over USB. There is no native network protocol. The reference web client, M8WebDisplay, relies on the WebSerial API, which requires the browser to run on the same physical machine as the USB device.

For remote Android access, we need a bridge architecture that:

1. Exposes USB serial communication over a network transport (WebSocket).
2. Captures USB audio from the Teensy and streams it over the network.
3. Renders the M8 display, plays audio, and sends input from an Android device.

---

## System Architecture

```
+--------------------------------------+
|  Teensy 4.1 + M8 Headless Firmware   |
+------------------+-------------------+
                   |
                   |  USB Serial (SLIP-framed display protocol + control)
                   |  USB Audio  (16-bit stereo, 44100 Hz)
                   |
+------------------v-------------------+
|            Linux Server              |
|                                      |
|  +-------------------------------+   |
|  | bridge.py                     |   |  Serial Bridge
|  | /dev/ttyACM0 -> WebSocket     |   |  Teensy auto-detection (0x16C0)
|  | port 8765                     |   |  async serial via serial_asyncio
|  +-------------------------------+   |
|                                      |
|  +-------------------------------+   |
|  | audio_stream.py               |   |  Audio Bridge
|  | arecord -> opusenc pipeline   |   |  ALSA capture -> Opus/OGG encoding
|  | port 8766                     |   |  3 auto-detection strategies
|  +-------------------------------+   |
|                                      |
+------------------+-------------------+
                   |
                   |  WebSocket (display) + WebSocket (audio)
                   |  via Tailscale VPN (default: 100.64.0.1)
                   |
+------------------v-------------------+
|          Android App (m8)            |
|                                      |
|  +-------------------------------+   |
|  | M8 Display Renderer           |   |  320x240 bitmap, Compose Canvas
|  | SLIP decoder + M8 protocol    |   |  sprite font rendering
|  +-------------------------------+   |
|                                      |
|  +-------------------------------+   |
|  | Audio Player                  |   |  AudioTrack, 48kHz stereo
|  | OpusDecoder (MediaCodec)      |   |  low-latency playback
|  +-------------------------------+   |
|                                      |
|  +-------------------------------+   |
|  | Touch / Keyboard / Gamepad    |   |  Maps to M8 key bitmask
|  | D-pad + action buttons        |   |  haptic feedback
|  +-------------------------------+   |
|                                      |
|  +-------------------------------+   |
|  | ConnectionManager             |   |  Coordinates display + audio
|  | M8WebSocketClient (OkHttp)    |   |  exponential backoff reconnect
|  +-------------------------------+   |
|                                      |
+--------------------------------------+
```

---

## Component Details

### M8 Headless Firmware (Teensy 4.1)

The Teensy 4.1 microcontroller runs the closed-source M8 Headless firmware. It presents two USB interfaces to the host:

- **USB Serial** -- carries the bidirectional SLIP-framed display protocol (draw commands from M8, key input commands from host).
- **USB Audio** -- a stereo USB audio device.

**USB Device Identification:**

| Property       | Value    |
|----------------|----------|
| Vendor ID      | `0x16C0` (PJRC) |
| Serial device  | `/dev/ttyACM0` (typical) |

### Serial Bridge (`server/bridge.py`)

A Python asyncio service that bridges the USB serial port to a WebSocket endpoint.

**Key features:**

- Teensy auto-detection by scanning serial ports for vendor ID `0x16C0`, with `/dev/ttyACM*` glob fallback
- Async serial I/O via `serial_asyncio` (4096-byte read buffer)
- WebSocket server on port 8765 (via `websockets` library, ping interval 20s)
- Multi-client broadcast: serial output is sent to all connected WebSocket clients
- Input from any WebSocket client is forwarded to the serial port
- JSON control messages: `{"event": "serial_connected", "device": "..."}` and `{"event": "serial_disconnected"}`
- Reconnection loop when Teensy is unplugged (retries every 2 seconds)
- Graceful shutdown on SIGINT/SIGTERM

The bridge is protocol-agnostic -- it forwards raw bytes without interpretation.

### Audio Bridge (`server/audio_stream.py`)

A Python asyncio service that captures USB audio from the Teensy and streams Opus/OGG-encoded audio over WebSocket.

**Key features:**

- Auto-detection of Teensy USB audio device using 3 strategies:
  1. `/proc/asound/cards` -- scan for "Teensy" by name
  2. `/proc/asound/cardN/usbid` -- match USB vendor ID `16c0:`
  3. `arecord -l` -- parse output for Teensy entries
- Capture pipeline: `arecord` (raw S16_LE PCM) piped to `opusenc` (OGG/Opus output)
- Default: 44100 Hz, 2 channels, 128 kbps Opus, 20ms frame size
- WebSocket server on port 8766 with multi-client broadcast
- JSON control messages: `{"event": "audio_connected", ...}` with codec/format metadata
- Supports client commands: `ping`, `status`
- Device reconnection on USB disconnect

**System dependencies:** `alsa-utils` (arecord), `opus-tools` (opusenc)

### Android Client

The Android app (package `com.m8droid`) is built with Kotlin and Jetpack Compose.

**M8DisplayBuffer + M8Protocol** -- Maintains a 320x240 ARGB bitmap. Decodes SLIP-framed binary data from the WebSocket and applies draw commands (rectangles, characters, waveforms) to the bitmap using a sprite font renderer.

**M8AudioPlayer + OpusDecoder** -- Receives audio data over a separate WebSocket. Decodes Opus frames using Android's MediaCodec API. Plays decoded PCM via AudioTrack at 48kHz stereo in low-latency mode (~40ms buffer).

**KeyMapper** -- Maps Android keyboard keys, gamepad buttons, and WASD keys to the M8 key bitmask. Touch controls (M8Controls.kt) provide an on-screen D-pad and action buttons with haptic feedback.

**ConnectionManager** -- Coordinates the display WebSocket and audio WebSocket connections. Handles protocol initialization (enable + reset display), key state transmission, and mute control.

**M8WebSocketClient + M8AudioClient** -- OkHttp-based WebSocket clients with exponential backoff reconnection (2s initial, 30s max, doubling up to 5 times).

---

## Protocol Reference

### SLIP Framing

All M8 serial data uses SLIP (Serial Line Internet Protocol) framing:

| Byte   | Meaning              |
|--------|----------------------|
| `0xC0` | Frame delimiter (END)|
| `0xDB` | Escape character     |
| `0xDC` | Escaped END (0xC0)   |
| `0xDD` | Escaped ESC (0xDB)   |

### Display Commands (M8 -> Host)

| Command Byte | Name          | Payload Size | Format |
|--------------|---------------|-------------|--------|
| `0xFE`       | DRAW_RECT     | 12 bytes    | cmd(1) + x(2) + y(2) + w(2) + h(2) + r(1) + g(1) + b(1) |
| `0xFD`       | DRAW_CHAR     | 12 bytes    | cmd(1) + x(2) + y(2) + char(1) + fg_r(1) + fg_g(1) + fg_b(1) + bg_r(1) + bg_g(1) + bg_b(1) |
| `0xFC`       | DRAW_WAVEFORM | 8+ bytes    | cmd(1) + x(2) + y(2) + r(1) + g(1) + b(1) + wavedata(N) |
| `0xFF`       | SYSTEM_INFO   | 6+ bytes    | cmd(1) + fw_major(1) + fw_minor(1) + fw_patch(1) + ... |
| `0xFB`       | DRAW_JOYPAD   | varies      | Joypad state (currently ignored by client) |

All multi-byte integers are little-endian unsigned 16-bit.

### Input Commands (Host -> M8)

| Command Byte | Name           | Payload |
|--------------|----------------|---------|
| `0x43`       | KEY_STATE      | 1 byte key bitmask |
| `0x44`       | DISCONNECT     | (none) |
| `0x45`       | ENABLE_DISPLAY | (none) |
| `0x52`       | RESET_DISPLAY  | (none) |

**Key bitmask:**

| Bit | Button  | Value  |
|-----|---------|--------|
| 0   | UP      | `0x01` |
| 1   | DOWN    | `0x02` |
| 2   | LEFT    | `0x04` |
| 3   | RIGHT   | `0x08` |
| 4   | OPTION  | `0x10` |
| 5   | EDIT    | `0x20` |
| 6   | SHIFT   | `0x40` |
| 7   | PLAY    | `0x80` |

Multiple buttons are combined by ORing their values into a single bitmask byte.

### Connection Sequence

1. Open WebSocket to bridge at `ws://server:8765`
2. Send enable + reset commands: `[0x45, 0x52]`
3. Begin receiving SLIP-framed display data
4. Send key state commands (`[0x43, bitmask]`) on user input
5. On disconnect, send `[0x44]`

### USB Audio

| Property     | Value              |
|--------------|--------------------|
| Capture rate | 44100 Hz           |
| Bit depth    | 16-bit (S16_LE)    |
| Channels     | 2 (stereo)         |
| Encoding     | Opus in OGG container |
| Transport    | WebSocket (port 8766) |

---

## Network Topology

| Service                    | Port | Default Binding | Protocol  |
|----------------------------|------|-----------------|-----------|
| M8 Serial WebSocket Bridge | 8765 | `127.0.0.1`    | WebSocket |
| M8 Audio Stream            | 8766 | `127.0.0.1`    | WebSocket |

The recommended access method is Tailscale (default host: `100.64.0.1`). When using Tailscale, bind the servers to `0.0.0.0` or the Tailscale IP so the Android client can reach them directly over the encrypted WireGuard tunnel.

---

## Data Flow

### Display Rendering (M8 -> Android)

```
M8 Firmware
  -> USB Serial (/dev/ttyACM0)
  -> bridge.py (SLIP frames, raw forwarding)
  -> WebSocket (port 8765, via Tailscale)
  -> M8WebSocketClient (OkHttp)
  -> M8Protocol (SLIP decode + command parse)
  -> M8DisplayBuffer (320x240 bitmap)
  -> M8Screen (Compose Canvas render)
```

### User Input (Android -> M8)

```
Touch / Keyboard / Gamepad
  -> KeyMapper (key -> bitmask)
  -> ConnectionManager.sendKeyState()
  -> M8WebSocketClient.send([0x43, bitmask])
  -> WebSocket (port 8765)
  -> bridge.py
  -> USB Serial (/dev/ttyACM0)
  -> M8 Firmware
```

### Audio (M8 -> Android)

```
M8 Firmware
  -> USB Audio (stereo, 44100 Hz)
  -> arecord (ALSA capture, raw S16_LE)
  -> opusenc (Opus/OGG encoding, 128 kbps)
  -> audio_stream.py
  -> WebSocket (port 8766, via Tailscale)
  -> M8AudioClient (OkHttp)
  -> OpusDecoder (MediaCodec)
  -> M8AudioPlayer (AudioTrack, 48kHz stereo)
  -> Speaker / Headphones
```

---

## Security Considerations

Both bridge services bind to `127.0.0.1` by default. The serial bridge provides raw access to the Teensy -- never expose it directly to the internet.

**Recommended: Tailscale**
- Install Tailscale on both server and Android device
- Access via Tailscale IP (e.g., `100.64.0.1`)
- All traffic encrypted end-to-end via WireGuard
- No reverse proxy needed

**Alternative: TLS reverse proxy**
- Place nginx or caddy in front of both WebSocket ports
- Use Let's Encrypt for certificates
- Require authentication

**Best practices:**
- Run bridge services as a dedicated non-root user in `dialout` and `audio` groups
- Use `ufw` firewall rules to restrict inbound traffic
- Do not embed secrets in the Android app

---

## Failure Modes and Recovery

| Failure                      | Detection                                    | Recovery                                               |
|------------------------------|----------------------------------------------|--------------------------------------------------------|
| Teensy USB disconnect        | Serial read returns empty / OSError          | Bridge notifies clients, retries every 2s              |
| Serial bridge crash          | Android WebSocket drops                      | systemd restarts; Android reconnects with backoff      |
| Audio pipeline crash         | opusenc process exits                        | audio_stream.py restarts pipeline, retries device      |
| Network interruption         | WebSocket connection failure                 | Android reconnects with exponential backoff (2s-30s)   |
| M8 firmware hang             | No serial data for extended period           | User power-cycles Teensy; bridge reconnects            |
| VPS reboot                   | All connections drop                         | systemd starts services on boot; Android reconnects    |
