# m8

Android app for remotely controlling a [Dirtywave M8](https://dirtywave.com/) tracker running in headless mode on a Linux server.

## What This Is

M8 Headless runs on a Teensy 4.1 microcontroller connected to a Linux server via USB. The M8 has no native network protocol -- it communicates entirely over USB serial (display/control) and USB audio.

This project provides:

- **Serial bridge** (`server/bridge.py`) -- exposes M8 serial protocol over WebSocket (port 8765)
- **Audio stream** (`server/audio_stream.py`) -- captures Teensy USB audio, encodes to Opus, streams over WebSocket (port 8766)
- **Android app** -- renders the M8 display, plays audio, and sends input from your phone

## Architecture

```
[Teensy 4.1 + M8 Headless]
        | USB (serial + audio)
        v
[Linux Server]
  ├── bridge.py        (serial <-> WebSocket, port 8765)
  └── audio_stream.py  (arecord -> opusenc -> WebSocket, port 8766)
        | WebSocket (via Tailscale)
        v
[Android App]
  ├── M8 Display (320x240, Compose Canvas)
  ├── Audio Player (AudioTrack, 48kHz)
  └── Touch / Keyboard / Gamepad Input
```

## Quick Start

### Server

```bash
# 1. Connect Teensy 4.1 (with M8 Headless firmware) to server via USB

# 2. Install dependencies
sudo apt install -y python3-pip python3-venv alsa-utils opus-tools
python3 -m venv /opt/m8/bridge-env
source /opt/m8/bridge-env/bin/activate
pip install -r server/requirements.txt

# 3. Start the serial bridge (auto-detects Teensy)
python3 server/bridge.py --host 0.0.0.0

# 4. Start the audio stream (in another terminal)
source /opt/m8/bridge-env/bin/activate
python3 server/audio_stream.py --host 0.0.0.0
```

### Android

1. Build and install the M8 app (`./gradlew installDebug`)
2. Open Settings, enter your server's IP address (Tailscale IP, e.g. `100.64.0.1`)
3. Connect -- the M8 display renders on screen, audio plays through your device
4. Use on-screen controls, keyboard, or gamepad to interact with the M8

## Documentation

- [Architecture](docs/architecture.md) -- system design, protocol reference, data flow diagrams
- [Server Setup](docs/server-setup.md) -- installation, systemd services, Tailscale, nginx
- [Serial Bridge](docs/serial-bridge.md) -- bridge design, configuration, auto-detection, security
- [Android App](docs/android-app.md) -- app architecture, project structure, protocol implementation
- [Troubleshooting](docs/troubleshooting.md) -- hardware, network, audio, known issues

## Requirements

### Server

- Linux with USB port (or USB passthrough for VMs)
- Teensy 4.1 with M8 Headless firmware
- Python 3.8+
- System packages: `alsa-utils`, `opus-tools`
- Python packages: `websockets`, `pyserial`, `pyserial-asyncio`
- Network access (Tailscale recommended)

### Android

- Android 8.0+ (API 26)
- Network connectivity to server

## Project Structure

```
server/
├── bridge.py           Serial-to-WebSocket bridge (port 8765)
├── audio_stream.py     Audio capture and streaming (port 8766)
└── requirements.txt    Python dependencies

app/src/main/java/com/m8/
├── MainActivity.kt     Activity, keyboard input, theme
├── M8ViewModel.kt      Connection lifecycle, display refresh
├── audio/              Audio client, player, Opus decoder
├── data/               Server settings persistence
├── input/              Key mapping (keyboard + gamepad)
├── network/            WebSocket clients, connection manager
├── protocol/           SLIP decoder, M8 commands, display buffer
└── ui/                 Compose screens, controls, settings
```

## Protocol Summary

The M8 uses a binary protocol over SLIP-framed serial:

- **Display** (M8 -> client): DRAW_RECT (`0xFE`), DRAW_CHAR (`0xFD`), DRAW_WAVEFORM (`0xFC`), SYSTEM_INFO (`0xFF`)
- **Input** (client -> M8): KEY_STATE (`0x43` + bitmask), DISCONNECT (`0x44`), ENABLE_DISPLAY (`0x45`), RESET_DISPLAY (`0x52`)
- **Key bitmask**: UP(0), DOWN(1), LEFT(2), RIGHT(3), OPTION(4), EDIT(5), SHIFT(6), PLAY(7)

## Known Issues

- **Audio format mismatch**: Server outputs OGG/Opus container; client expects raw Opus frames
- **Sample rate mismatch**: Server captures at 44100 Hz; client plays at 48000 Hz
- **Control message field name**: Server sends `"event"` field; client checks `"type"` field

See [troubleshooting.md](docs/troubleshooting.md) for details and workarounds.

## Sources

- [M8 Headless Firmware](https://github.com/Dirtywave/M8HeadlessFirmware) -- firmware .hex files for Teensy 4.1
- [M8 Web Display](https://github.com/Dirtywave/M8WebDisplay) -- reference browser client (WebSerial)
- [m8c](https://github.com/laamaa/m8c) -- native Linux/Mac/Windows display client
- [M8 Docs](https://github.com/Dirtywave/M8Docs) -- official setup documentation

## License

TBD
