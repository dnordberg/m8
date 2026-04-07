# WebSocket-to-Serial Bridge

The M8 Headless firmware running on a Teensy 4.1 communicates exclusively over USB serial. The bridge exposes this serial port as a WebSocket endpoint, allowing remote clients (Android app, browser, scripts) to send and receive M8 protocol bytes over TCP.

## Architecture

```
 Android / Browser                   Linux Server
+------------------+              +-----------------------------+
|                  |   WebSocket  |  bridge.py                  |
|  M8 Client App  | <==========> |  ws://host:8765             |
|                  |   (TCP)      |    |                        |
+------------------+              |    v                        |
                                  |  /dev/ttyACM0  (USB serial) |
                                  |    |                        |
                                  |    v                        |
                                  |  Teensy 4.1 (M8 Headless)  |
                                  +-----------------------------+
```

The bridge performs **bidirectional byte forwarding**:

- **Serial to WebSocket** -- Every chunk of bytes read from the Teensy (up to 4096 bytes) is broadcast to all connected WebSocket clients.
- **WebSocket to Serial** -- Every message received from any WebSocket client is written to the serial port via async drain.

The bridge is protocol-agnostic. It treats data as an opaque byte stream and does not interpret SLIP framing or M8 commands.

## Implementation Details

`server/bridge.py` is a single-file Python asyncio application built on:

- `websockets` (>=12.0) -- WebSocket server with ping/pong keep-alive
- `pyserial` (>=3.5) -- Serial port enumeration and USB vendor ID detection
- `pyserial-asyncio` (>=0.6) -- Async serial I/O via `open_serial_connection()`

The bridge is implemented as the `M8Bridge` class with these responsibilities:

1. **Teensy detection** -- scans serial ports for USB vendor ID `0x16C0` (PJRC)
2. **Async serial** -- opens a `StreamReader`/`StreamWriter` pair via `serial_asyncio`
3. **WebSocket server** -- serves on configurable host/port with 20s ping interval
4. **Multi-client broadcast** -- maintains a set of connected clients; serial data goes to all
5. **Control messages** -- sends JSON status events to clients
6. **Reconnection** -- retries serial connection every 2 seconds on disconnect
7. **Graceful shutdown** -- handles SIGINT/SIGTERM, closes all connections

## Running the Bridge

### Prerequisites

```bash
pip install -r server/requirements.txt
```

Or manually:

```bash
pip install websockets pyserial pyserial-asyncio
```

### Basic Usage

```bash
python3 server/bridge.py
```

With no arguments, the bridge auto-detects a connected Teensy by scanning serial ports for vendor ID `0x16C0`. Falls back to the first `/dev/ttyACM*` device if vendor ID metadata is unavailable. Binds to `127.0.0.1:8765` by default.

### Command-Line Options

| Flag         | Default       | Description |
|--------------|---------------|-------------|
| `--serial`   | auto-detect   | Path to serial device (e.g. `/dev/ttyACM0`) |
| `--baud`     | `9600`        | Baud rate (formality for USB CDC serial) |
| `--host`     | `127.0.0.1`   | WebSocket bind address |
| `--port`     | `8765`        | WebSocket port |
| `--verbose` / `-v` | off     | Enable debug logging |

Example with all options:

```bash
python3 server/bridge.py \
  --serial /dev/ttyACM0 \
  --baud 9600 \
  --host 0.0.0.0 \
  --port 8765 \
  --verbose
```

### Auto-Detection

When `--serial` is not provided:

1. Bridge scans all serial ports via `serial.tools.list_ports.comports()`
2. Checks each port's `vid` property against `0x16C0` (PJRC/Teensy)
3. If no vendor ID match, falls back to the first `/dev/ttyACM*` device
4. If no device found, retries every 2 seconds until one appears

This allows the bridge to start at boot before the Teensy is plugged in.

## Multiple Clients

The bridge supports multiple simultaneous WebSocket connections. Serial output is broadcast to every connected client. Input from any client is written to the serial port.

In practice, only one client should send input commands at a time to avoid interleaving protocol messages.

## Control Messages

The bridge sends JSON text messages to clients on state changes:

**Serial connected:**
```json
{"event": "serial_connected", "device": "/dev/ttyACM0"}
```

**Serial disconnected:**
```json
{"event": "serial_disconnected"}
```

On initial WebSocket connection, if the serial port is already open, the client receives a `serial_connected` message immediately.

## Serial Disconnection and Reconnection

If the Teensy is unplugged or the serial device disappears:

1. Serial read returns empty or raises an OSError
2. Bridge closes the serial connection
3. Broadcasts `{"event": "serial_disconnected"}` to all WebSocket clients
4. Enters reconnection loop (retries every 2 seconds)
5. On successful reconnection, broadcasts `{"event": "serial_connected", "device": "..."}`

WebSocket connections remain open during serial disconnection. Clients should handle control messages and display appropriate status.

## Security

The bridge binds to `127.0.0.1` by default -- only reachable from localhost.

To expose for Android access:

1. **Tailscale** (preferred) -- bind to `0.0.0.0` or the Tailscale IP; traffic is encrypted via WireGuard
2. **Reverse proxy** -- place behind nginx/caddy with TLS termination

**Do not bind to `0.0.0.0` on a public network without protection.** The bridge provides raw serial access to the Teensy hardware.

Example nginx WebSocket proxy:

```nginx
location /ws {
    proxy_pass http://127.0.0.1:8765;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_read_timeout 86400;
}
```

## Error Handling

| Scenario | Bridge Behavior |
|----------|----------------|
| Teensy not connected at startup | Retry auto-detection every 2s |
| Teensy unplugged during operation | Notify clients, close serial, reconnect loop |
| Serial read/write error | Close port, reconnect loop |
| WebSocket client disconnects | Remove from client set, continue |
| All WebSocket clients disconnect | Continue running, wait for new clients |
| SIGINT / SIGTERM | Close all WebSocket connections, close serial, exit |

## Latency

- **Serial**: USB CDC serial operates at full USB speed regardless of configured baud rate. Sub-millisecond per chunk.
- **WebSocket**: Under 5ms round trip over LAN/Tailscale. TLS adds overhead on initial handshake only.
- **Display**: M8 sends screen updates at ~30fps. At typical LAN latencies, the experience is indistinguishable from a local connection.
