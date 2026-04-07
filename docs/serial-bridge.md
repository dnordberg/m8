# WebSocket-to-Serial Bridge

The M8 Headless firmware running on a Teensy 4.1 communicates exclusively over USB serial. The Web Serial API only works when the browser runs on the same machine the Teensy is physically plugged into, and Android devices cannot host a Teensy over USB-OTG without additional drivers.

The bridge solves this by exposing the serial port as a WebSocket endpoint on the local network, allowing any client -- browser, Android app, or automation script -- to send and receive M8 protocol bytes over TCP.

## Architecture

```
 Android / Browser                   Linux VPS
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

- **Serial to WebSocket** -- Every byte received from the Teensy is broadcast to all connected WebSocket clients.
- **WebSocket to Serial** -- Every message received from any WebSocket client is written to the serial port.

There is no protocol interpretation inside the bridge. It treats the data as an opaque byte stream, which keeps the implementation simple and avoids coupling to a specific firmware version.

## Running the Bridge

### Prerequisites

```bash
pip install -r server/requirements.txt
```

Dependencies: `websockets`, `pyserial`, `pyserial-asyncio`.

### Basic Usage

```bash
python3 server/bridge.py
```

With no arguments the bridge will auto-detect a connected Teensy by scanning `/dev/ttyACM*` devices and checking for the Teensy USB vendor ID (`0x16C0`). It binds to `127.0.0.1:8765` by default.

### Command-Line Options

| Flag       | Default       | Description |
|------------|---------------|-------------|
| `--serial` | auto-detect   | Path to serial device (e.g. `/dev/ttyACM0`) |
| `--baud`   | `9600`        | Baud rate for the serial connection |
| `--host`   | `127.0.0.1`  | Address to bind the WebSocket server to |
| `--port`   | `8765`        | Port for the WebSocket server |
| `-v`       | off           | Enable debug logging |

Example with all options:

```bash
python3 server/bridge.py \
  --serial /dev/ttyACM0 \
  --baud 9600 \
  --host 127.0.0.1 \
  --port 8765
```

### Auto-Detection

When `--serial` is not provided, the bridge scans serial ports and uses `pyserial` to query the USB vendor ID. The Teensy 4.1 reports vendor ID `0x16C0` (PJRC). The first matching device is used. If no device is found the bridge enters a retry loop, checking every two seconds until one appears. This allows the bridge to start at boot before the Teensy is plugged in.

## Multiple Clients

The bridge supports multiple simultaneous WebSocket connections. Serial output is broadcast to every connected client. Input from any client is written to the serial port. In practice only one client should send commands at a time to avoid interleaving protocol messages.

## Serial Disconnection and Reconnection

If the Teensy is unplugged or the serial device disappears, the bridge:

1. Logs the disconnection event.
2. Notifies all connected WebSocket clients with a JSON control message:
   `{"event": "serial_disconnected"}`.
3. Enters a reconnection loop, attempting to reopen the serial port every two seconds.
4. On successful reconnection, notifies clients with:
   `{"event": "serial_connected", "device": "/dev/ttyACM0"}`.

WebSocket clients (the Android app) should handle these events and display appropriate status to the user.

## Security Considerations

The bridge binds to `127.0.0.1` by default, which means it is only reachable from the local machine. This is the safe default.

To expose the bridge to the network (for Android access), use one of:

1. **Tailscale** (preferred) -- no configuration needed, encrypted tunnel
2. **Reverse proxy with TLS** -- place behind nginx/caddy

**Do not bind to `0.0.0.0` without protection.** The bridge provides raw serial access to the Teensy. An unauthorized client could send arbitrary commands to the device.

Example nginx snippet for TLS termination:

```nginx
location /ws {
    proxy_pass http://127.0.0.1:8765;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $host;
    proxy_read_timeout 86400;
}
```

## Audio Streaming

Serial communication carries only display and control data. Audio from the M8 is a separate concern.

The Teensy 4.1 presents a USB audio device alongside the serial port. To stream audio to remote clients:

1. **Capture USB audio** using ALSA or PulseAudio on the host.
2. **Encode as Opus** using `ffmpeg` for low-latency, compressed audio.
3. **Stream via WebSocket or HTTP** to the client.

Example using ffmpeg:

```bash
ffmpeg \
  -f pulse -i alsa_input.usb-PJRC_Teensy_Audio-00.analog-stereo \
  -c:a libopus -b:a 128k -vbr on \
  -application restricted_lowdelay \
  -frame_duration 10 \
  -f ogg pipe:1
```

Audio streaming is intentionally kept separate from the serial bridge to allow independent configuration and keep the bridge code simple.

## Latency Considerations

- **Serial latency** is negligible. USB CDC serial at 9600 baud on a Teensy 4.1 adds sub-millisecond delay per byte. The actual USB transport is faster than the configured baud rate.
- **WebSocket latency** over LAN/Tailscale is typically under 5ms round trip. TLS adds a small overhead on initial handshake only.
- **Display rendering** latency depends on the client. The M8 protocol sends screen updates at roughly 30fps. At typical LAN latencies the experience is indistinguishable from a local USB connection.
- **Audio latency** depends on the encoding pipeline. Opus with 10ms frame size and a small jitter buffer can achieve end-to-end latency under 100ms on LAN, which is acceptable for monitoring. For tighter latency, reduce frame size to 5ms and minimize buffering.

## Error Handling Summary

| Scenario | Bridge Behavior |
|----------|----------------|
| Teensy not connected at startup | Retry auto-detection every 2s |
| Teensy unplugged during operation | Notify clients, enter reconnect loop |
| WebSocket client disconnects | Remove from client set, continue |
| All WebSocket clients disconnect | Continue running, wait for new clients |
| Serial read/write error | Close port, enter reconnect loop |
| SIGINT / SIGTERM | Graceful shutdown of all connections |
