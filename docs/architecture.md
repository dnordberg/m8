# m8 Architecture Overview

This document describes the system architecture for the m8 Android app -- a remote client for controlling and monitoring a Dirtywave M8 Headless tracker running on a Teensy 4.1 microcontroller connected to a Linux VPS.

## Table of Contents

- [Problem Statement](#problem-statement)
- [System Architecture](#system-architecture)
- [Component Details](#component-details)
- [Protocol Reference](#protocol-reference)
- [Network Topology and Port Allocation](#network-topology-and-port-allocation)
- [OpenClaw Coexistence](#openclaw-coexistence)
- [Security Considerations](#security-considerations)
- [Data Flow](#data-flow)
- [Failure Modes and Recovery](#failure-modes-and-recovery)

---

## Problem Statement

The Dirtywave M8 Headless firmware communicates exclusively over USB. There is no native network protocol. The reference web client, [M8WebDisplay](https://github.com/Dirtywave/M8WebDisplay), relies on the WebSerial API, which requires the browser to run on the same physical machine as the USB device.

For remote Android access, we need a bridge architecture that:

1. Exposes USB serial communication over a network transport (WebSocket).
2. Captures USB audio from the Teensy and streams it over the network.
3. Renders the M8 display, plays audio, and sends input from an Android device.
4. Coexists peacefully with the existing OpenClaw system on the same VPS.

---

## System Architecture

```
+--------------------------------------+
|  Teensy 4.1 + M8 Headless Firmware   |
|  (M8_V6_5_2B_HEADLESS.hex)           |
+------------------+-------------------+
                   |
                   |  USB Serial (display protocol + control)
                   |  USB Audio  (24-bit, up to 24 channels)
                   |
+------------------v-------------------+
|            Linux VPS                  |
|                                      |
|  +-------------------------------+   |
|  | WebSocket-to-serial bridge    |   |  Serial Bridge
|  | (port 8765)                   |   |  /dev/ttyACM0 -> WebSocket
|  +-------------------------------+   |
|                                      |
|  +-------------------------------+   |
|  | ALSA/PulseAudio capture       |   |  Audio Bridge
|  | -> Opus/WebRTC stream         |   |  USB audio -> network stream
|  | (port 8766)                   |   |
|  +-------------------------------+   |
|                                      |
|  +-------------------------------+   |
|  | OpenClaw (existing system)    |   |  Do not disturb
|  | (own ports, unchanged)        |   |
|  +-------------------------------+   |
|                                      |
|  +-------------------------------+   |
|  | nginx / caddy reverse proxy   |   |  TLS termination, routing
|  | (port 443)                    |   |
|  +-------------------------------+   |
|                                      |
+------------------+-------------------+
                   |
                   |  WebSocket + Audio Stream
                   |  (via Tailscale or reverse proxy)
                   |
+------------------v-------------------+
|          Android App (m8)            |
|                                      |
|  +-------------------------------+   |
|  | M8 Display Renderer           |   |  Native canvas
|  +-------------------------------+   |
|                                      |
|  +-------------------------------+   |
|  | Audio Player                  |   |  Opus / WebRTC playback
|  +-------------------------------+   |
|                                      |
|  +-------------------------------+   |
|  | Touch / Gamepad Input         |   |  Maps to M8 key commands
|  +-------------------------------+   |
|                                      |
|  +-------------------------------+   |
|  | Connection Manager            |   |  Reconnection, latency,
|  |                               |   |  server discovery
|  +-------------------------------+   |
|                                      |
+--------------------------------------+
```

---

## Component Details

### M8 Headless Firmware (Teensy 4.1)

The Teensy 4.1 microcontroller runs the closed-source M8 Headless firmware. It presents two USB interfaces to the host:

- **USB Serial** -- carries the bidirectional display protocol (screen draw commands from M8, key input commands from host).
- **USB Audio** -- a 24-bit USB audio device with up to 24 channels of output.

The firmware `.hex` file is flashed to the Teensy using the Teensy Loader application. The latest known firmware version is `M8_V6_5_2B_HEADLESS.hex`.

**USB Device Identification:**

| Property       | Value    |
|----------------|----------|
| Vendor ID      | `0x16c0` |
| Product ID     | `0x048a` or `0x048b` |
| Serial device  | `/dev/ttyACM0` (typical) |

### Serial Bridge

The serial bridge converts the USB serial interface (`/dev/ttyACM0`) into a WebSocket endpoint accessible over the network. Two approaches are viable:

1. **ser2net** -- a well-established serial-to-network proxy. Can be configured to expose the serial port as a raw TCP or Telnet socket. A lightweight WebSocket wrapper (e.g., `websocat`) can sit in front of it.

2. **Custom WebSocket-to-serial bridge** -- a purpose-built daemon (Python) that opens the serial port directly and exposes a WebSocket server. This approach offers tighter control over buffering, reconnection, and protocol-level awareness. See `server/bridge.py`.

The bridge must:

- Open `/dev/ttyACM0` at 9600 baud, 8N1, with a 4096-byte buffer.
- Forward bytes bidirectionally between the WebSocket client and the serial port with minimal latency.
- Handle USB device disconnection and reconnection gracefully (the Teensy may be reflashed or power-cycled).
- Bind to `127.0.0.1:8765` to prevent direct external access.

### Audio Bridge

The Teensy presents a USB audio device to the Linux host. The audio bridge captures this audio and streams it to the Android client.

**Capture pipeline:**

1. ALSA or PulseAudio captures audio from the Teensy USB audio device.
2. The raw PCM audio is encoded to Opus (low latency, good compression) or packaged for WebRTC transport.
3. The encoded stream is served over a WebSocket (port 8766) or via a WebRTC peer connection.

**Channel layout (24-bit USB audio, up to 24 channels):**

| Channels | Purpose          |
|----------|------------------|
| 1-2      | Main stereo mix  |
| 3-18     | Individual tracks (8 stereo pairs) |
| 19-20    | Mod FX send      |
| 21-22    | Delay send       |
| 23-24    | Reverb send      |

For the initial implementation, capturing channels 1-2 (main stereo mix) is sufficient. Per-track streaming can be added later for mixing or monitoring use cases.

**Latency target:** Under 50ms end-to-end (capture to playback on Android) is desirable for a responsive music-making experience. Opus in low-delay mode (`OPUS_APPLICATION_RESTRICTED_LOWDELAY`) with small frame sizes (5-10ms) helps achieve this.

### Android Client

The Android app has four primary subsystems:

**M8 Display Renderer** -- Interprets the binary display protocol received over WebSocket and renders the M8 screen using a native Kotlin/Compose canvas renderer that parses the binary protocol directly.

**Audio Player** -- Receives the Opus-encoded audio stream and plays it through the Android audio subsystem. Uses `AudioTrack` in low-latency mode or Oboe (AAudio) for minimal playback latency.

**Touch / Gamepad Input** -- Maps touchscreen gestures and physical gamepad buttons to M8 key commands. The M8 has 8 input keys: UP, DOWN, LEFT, RIGHT, OPTION, EDIT, PLAY, SHIFT. These are sent as single-byte key state commands over the serial WebSocket.

**Connection Manager** -- Handles server discovery (manual entry or Tailscale hostname), WebSocket lifecycle, latency monitoring, and audio stream synchronization.

---

## Protocol Reference

### USB Serial

| Parameter   | Value |
|-------------|-------|
| Baud rate   | 9600  |
| Data bits   | 8     |
| Parity      | None  |
| Stop bits   | 1     |
| Buffer size | 4096 bytes |

Note: While the baud rate is set to 9600 for configuration purposes, the actual USB CDC serial transport operates at full USB speed. The baud rate setting is largely a formality for USB serial devices.

### M8 Display Protocol

The M8 communicates using a binary protocol over serial. Key command bytes:

| Byte   | Command                        | Direction    |
|--------|--------------------------------|--------------|
| `0x43` | Key state (button input)       | Host -> M8   |
| `0x44` | Disconnect                     | Host -> M8   |
| `0x45` | Enable display                 | Host -> M8   |
| `0x52` | Reset display                  | Host -> M8   |
| `0x4B` | MIDI note on/off               | Host -> M8   |

**Key bitmask (sent after 0x43):**

| Bit | Button  |
|-----|---------|
| 0   | UP      |
| 1   | DOWN    |
| 2   | LEFT    |
| 3   | RIGHT   |
| 4   | OPTION  |
| 5   | EDIT    |
| 6   | SHIFT   |
| 7   | PLAY    |

Display data flows from the M8 to the host as a stream of draw commands (rectangles, characters, waveforms) that the client must interpret and render. For full protocol details, refer to the M8WebDisplay source code (`js/display.js`, `js/serial.js`).

### USB Audio

| Property     | Value                    |
|--------------|--------------------------|
| Bit depth    | 24-bit                   |
| Sample rate  | 44100 Hz (typical)       |
| Channels     | Up to 24                 |
| USB class    | USB Audio Class 1 or 2   |

---

## Network Topology and Port Allocation

All M8 services bind to `127.0.0.1` by default. External access is provided exclusively through a reverse proxy with TLS termination, or through a Tailscale tunnel.

| Service                          | Port | Binding       | Protocol  |
|----------------------------------|------|---------------|-----------|
| OpenClaw (existing)              | varies | keep as-is  | --        |
| M8 Serial WebSocket Bridge       | 8765 | `127.0.0.1`  | WebSocket |
| M8 Audio Stream                  | 8766 | `127.0.0.1`  | WebSocket / WebRTC |
| M8 Web Display (optional)        | 8000 | `127.0.0.1`  | HTTP      |
| Reverse Proxy (nginx or caddy)   | 443  | `0.0.0.0` or Tailscale | HTTPS / WSS |

The reverse proxy routes requests to the appropriate backend based on path:

```
wss://m8.example.com/serial   ->  127.0.0.1:8765  (serial bridge)
wss://m8.example.com/audio    ->  127.0.0.1:8766  (audio stream)
https://m8.example.com/        ->  127.0.0.1:8000  (web display, optional)
```

---

## OpenClaw Coexistence

The Linux VPS runs an existing OpenClaw system. All M8 services must coexist without interference:

- M8 services use dedicated, non-conflicting ports (8765, 8766, 8000).
- M8 services bind to `127.0.0.1` only, never to `0.0.0.0` directly.
- M8 services run under a separate systemd unit or user account.
- Reverse proxy configuration for M8 routes is additive -- it must not modify or displace existing OpenClaw proxy rules.
- Resource consumption (CPU, memory, bandwidth) should be monitored to ensure M8 audio encoding does not starve OpenClaw processes.

---

## Security Considerations

### No Public Exposure

The serial bridge and audio stream must never be directly exposed to the public internet. An unauthenticated serial bridge would give anyone full control over the M8 hardware. All services bind to `127.0.0.1` by default.

### Tailscale (Preferred)

The recommended access method is Tailscale, a WireGuard-based mesh VPN:

- Install Tailscale on both the VPS and the Android device.
- Access M8 services via the Tailscale IP or MagicDNS hostname.
- No reverse proxy needed -- the Android app connects directly over the encrypted Tailscale tunnel.
- Tailscale ACLs can restrict which devices are allowed to reach the M8 ports.

### TLS via Reverse Proxy

If Tailscale is not used, a reverse proxy (nginx or caddy) must terminate TLS:

- Use Let's Encrypt certificates (caddy handles this automatically).
- Require client authentication (mutual TLS, API key, or HTTP basic auth at minimum).
- Rate-limit WebSocket connections to prevent abuse.

### Additional Measures

- **Firewall rules** -- Use `ufw` or `iptables` to restrict inbound traffic to only the reverse proxy port (443) and SSH.
- **Principle of least privilege** -- Run M8 bridge services as a dedicated non-root user with access only to `/dev/ttyACM0` and the USB audio device (via `udev` rules or group membership in `dialout` and `audio`).
- **No secrets in the app** -- The Android app should not embed API keys or certificates. Use Tailscale device identity or prompt the user for credentials.

---

## Data Flow

### Display Rendering (M8 -> Android)

```
M8 Firmware
  -> USB Serial (/dev/ttyACM0)
  -> Serial Bridge (localhost:8765)
  -> WebSocket (WSS via proxy or Tailscale)
  -> Android Connection Manager
  -> M8 Display Renderer
  -> Screen
```

### User Input (Android -> M8)

```
Touchscreen / Gamepad
  -> Android Input Handler
  -> Key command byte (0x43 + key bitmask)
  -> WebSocket
  -> Serial Bridge
  -> USB Serial (/dev/ttyACM0)
  -> M8 Firmware
```

### Audio (M8 -> Android)

```
M8 Firmware
  -> USB Audio (24-bit, 24ch)
  -> ALSA/PulseAudio capture
  -> Opus encoder (low-delay mode)
  -> Audio Stream (localhost:8766)
  -> WebSocket or WebRTC (via proxy or Tailscale)
  -> Android Audio Player
  -> Speaker / Headphones
```

---

## Failure Modes and Recovery

| Failure                        | Detection                                      | Recovery                                                  |
|--------------------------------|------------------------------------------------|-----------------------------------------------------------|
| Teensy USB disconnect          | Serial bridge loses `/dev/ttyACM0`             | Bridge watches for device re-appearance; auto-reconnects   |
| Serial bridge crash            | Android WebSocket connection drops              | systemd restarts the bridge; Android reconnects with backoff |
| Audio bridge crash             | Android audio stream stops                      | systemd restarts; Android reconnects audio independently   |
| Network interruption           | WebSocket ping/pong timeout                     | Android reconnects with exponential backoff                |
| M8 firmware hang               | No serial data received for extended period     | User power-cycles Teensy; bridge reconnects automatically  |
| VPS reboot                     | All connections drop                            | systemd starts all services on boot; Android reconnects    |

---

## Future Considerations

- **MIDI forwarding** -- Allow the Android device to send MIDI notes to the M8 via the `0x4B` command.
- **Per-track audio streaming** -- Stream individual track channels (3-24) for remote mixing or recording.
- **Multi-client support** -- Allow multiple viewers to observe the M8 display (read-only) while one client has input control.
- **Recording** -- Capture audio and/or display state on the VPS for later retrieval.
- **Latency optimization** -- Investigate kernel tuning (`CONFIG_PREEMPT_RT`), CPU pinning, and buffer size optimization for sub-20ms audio latency.
