# m8

Android app for remotely controlling a [Dirtywave M8](https://dirtywave.com/) tracker running in headless mode on a Linux server.

## What This Is

M8 Headless runs on a Teensy 4.1 microcontroller connected to a Linux server via USB. The M8 has no native network protocol -- it communicates entirely over USB serial (display/control) and USB audio.

This project provides:

- **WebSocket-to-serial bridge** (`server/bridge.py`) -- exposes the M8 serial protocol over the network
- **Android app** -- renders the M8 display, plays audio, and sends input from your phone
- **Integration guide** -- for running alongside an existing OpenClaw system

## Architecture

```
[Teensy 4.1 + M8 Headless]
        | USB
        v
[Linux VPS]
  ├── bridge.py (serial -> WebSocket, port 8765)
  ├── audio stream (Opus, port 8766)
  └── OpenClaw (existing, untouched)
        | WebSocket + Audio (via Tailscale)
        v
[Android App]
  ├── M8 Display Renderer
  ├── Audio Player
  └── Touch / Gamepad Input
```

## Quick Start

### Server

```bash
# 1. Connect Teensy 4.1 (with M8 Headless firmware) to server via USB

# 2. Set up the bridge
sudo mkdir -p /opt/m8 && sudo chown $USER:$USER /opt/m8
python3 -m venv /opt/m8/bridge-env
source /opt/m8/bridge-env/bin/activate
pip install websockets pyserial pyserial-asyncio

# 3. Run the bridge
python3 server/bridge.py
```

### Android

1. Install the M8 app
2. Configure server address (Tailscale IP or domain)
3. Connect and play

## Documentation

- [Architecture](docs/architecture.md) -- system design, protocol details, port allocation
- [Server Setup](docs/server-setup.md) -- quick and full integration guides, systemd, tmux
- [Android App](docs/android-app.md) -- app design, protocol, tech stack, project structure
- [Serial Bridge](docs/serial-bridge.md) -- bridge design, configuration, security
- [Troubleshooting](docs/troubleshooting.md) -- hardware, network, audio, OpenClaw issues

## Requirements

### Server

- Linux VPS with USB port (or USB passthrough for VMs)
- Teensy 4.1 with M8 Headless firmware ([latest](https://github.com/Dirtywave/M8HeadlessFirmware))
- Python 3.8+
- Network access (Tailscale recommended)

### Android

- Android 8.0+ (API 26)
- Network connectivity to server

## Sources

- [M8 Headless Firmware](https://github.com/Dirtywave/M8HeadlessFirmware) -- closed-source .hex files for Teensy 4.1
- [M8 Web Display](https://github.com/Dirtywave/M8WebDisplay) -- reference browser client (WebSerial-based)
- [m8c](https://github.com/laamaa/m8c) -- native Linux/Mac/Windows display client
- [M8 Docs](https://github.com/Dirtywave/M8Docs) -- official setup documentation

## License

TBD
