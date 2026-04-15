# External Integrations

**Analysis Date:** 2026-04-15

## APIs & External Services

**M8 Headless Hardware Protocol:**
- Teensy 4.1 running M8 Headless firmware - Custom SLIP-encoded binary protocol
  - SDK/Client: `pyserial`, `pyserial-asyncio` (Python server)
  - Protocol: Binary command-response over serial USB (9600 baud)
  - Commands: `DRAW_RECT`, `DRAW_CHAR`, `DRAW_WAVEFORM`, `SYSTEM_INFO`
  - Vendor ID: 0x16C0 (PJRC Teensy vendor)

## Data Storage

**Databases:**
- None (stateless protocol bridge)

**File Storage:**
- Local filesystem only - Asset files (`app/src/main/assets/m8_font.png`)
- DataStore preferences - App configuration stored locally on device (`androidx.datastore:datastore-preferences`)

**Caching:**
- None configured

## Authentication & Identity

**Auth Provider:**
- None - No external authentication
- Local communication only (WebSocket on localhost or LAN)

## Monitoring & Observability

**Error Tracking:**
- None

**Logs:**
- Python: Standard logging module with console output
- Android: Android Log (android.util.Log)
- No persistent logging infrastructure

## CI/CD & Deployment

**Hosting:**
- Local development/testing only
- WebSocket server runs on development machine or Raspberry Pi alongside Teensy

**CI Pipeline:**
- None configured (build.gradle.kts exists but no CI configuration detected)

## Network Communication

**WebSocket Bridges (Server → Android App):**

**Port 8765 - M8 Serial Bridge:**
- `server/bridge.py` - Bridges serial port (Teensy) to WebSocket clients
- Client: Android app via `M8WebSocketClient` (OkHttp WebSocket)
- Protocol: SLIP-encoded binary frames
- URL format: `ws://[host]:8765` (default: localhost)
- Handles: Key input, display commands, system info

**Port 8766 - M8 Audio Stream Server:**
- `server/audio_stream.py` - Captures USB audio from Teensy and streams to clients
- Client: Android app via `M8AudioClient` (OkHttp WebSocket)
- Protocol: Opus-encoded or raw PCM audio frames with header byte
  - `0x01` = raw PCM
  - `0x02` = Opus encoded
- Bitrate: 128 kbps Opus (configurable)
- Sample rate: 44.1 kHz stereo 16-bit
- Reconnection: Exponential backoff (2s → 30s max)

**Emulator Mode:**
- `server/m8_emulator.py` - Standalone emulator replacing Teensy + bridge
- Simulates M8 tracker UI over WebSocket
- Renders display commands via SLIP protocol
- Supports audio synthesis via optional numpy

## Configuration

**Required env vars:**
- None enforced, all configurable via CLI arguments:
  - `bridge.py`: `--serial`, `--baud`, `--host`, `--port`
  - `audio_stream.py`: `--device`, `--host`, `--port`
  - `m8_emulator.py`: `--host`, `--port`, `--audio-port`

**Secrets location:**
- No secrets - Local development only
- Connection URLs hardcoded in app or passed as preferences

## Webhooks & Callbacks

**Incoming:**
- WebSocket clients connect to server (Android app connects to bridge)

**Outgoing:**
- None - Pull-based model only

## Cross-Device Communication

**Display Protocol:**
- SLIP encoding (Serial Line Internet Protocol)
- Binary command format with 16-bit coordinates, 8-bit colors (RGB)
- Commands: 0xFE (draw rect), 0xFD (draw char), 0xFC (draw waveform), 0xFF (system info)
- Implementation: `com.m8droid.protocol.M8Protocol` parses and renders

**Audio Transport:**
- WebSocket binary frames
- First byte indicates format (PCM vs Opus)
- Decoded to raw PCM by `OpusDecoder` or used directly
- Played via Android AudioTrack via `M8AudioPlayer`

**Local State:**
- Display buffer: `M8DisplayBuffer` maintains 320x240 pixel framebuffer
- Font: 8x10 bitmap font sprite sheet (`m8_font.png`)
- Input: Key states mapped via `KeyMapper`

---

*Integration audit: 2026-04-15*
