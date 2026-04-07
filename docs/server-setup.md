# Server Setup Guide

Set up M8 Headless on a Linux server. The Teensy 4.1 is USB-connected to the server. Two Python services bridge serial and audio to WebSocket for remote access from the Android app.

---

## Quick Start

Minimal steps. Assumes Teensy 4.1 with M8 Headless firmware is connected via USB.

```bash
# 1. Check that Teensy is detected
lsusb | grep "16c0"
ls /dev/ttyACM*

# 2. Add user to required groups
sudo usermod -a -G dialout,audio $USER
# Re-login required after this

# 3. Install system dependencies
sudo apt install -y python3-pip python3-venv alsa-utils opus-tools

# 4. Set up Python environment
python3 -m venv /opt/m8/bridge-env
source /opt/m8/bridge-env/bin/activate
pip install websockets pyserial pyserial-asyncio

# 5. Copy server files (from this repo's server/ directory)
sudo mkdir -p /opt/m8/server
cp server/bridge.py server/audio_stream.py server/requirements.txt /opt/m8/server/

# 6. Start the serial bridge
python3 /opt/m8/server/bridge.py --host 0.0.0.0

# 7. In another terminal, start the audio stream
source /opt/m8/bridge-env/bin/activate
python3 /opt/m8/server/audio_stream.py --host 0.0.0.0
```

The Android app can now connect to:
- Display: `ws://<server-ip>:8765`
- Audio: `ws://<server-ip>:8766`

---

## Server Components

### Serial Bridge (`bridge.py`)

Bridges Teensy USB serial to WebSocket on port 8765.

- Auto-detects Teensy by USB vendor ID `0x16C0`
- Async serial via `serial_asyncio`
- Multi-client WebSocket broadcast
- JSON control messages for serial connect/disconnect events
- See [serial-bridge.md](serial-bridge.md) for full details

```bash
python3 server/bridge.py [--serial /dev/ttyACM0] [--host 0.0.0.0] [--port 8765] [-v]
```

### Audio Stream (`audio_stream.py`)

Captures Teensy USB audio and streams Opus/OGG over WebSocket on port 8766.

- Auto-detects Teensy audio device via 3 strategies (procfs, usbid, arecord)
- Pipeline: `arecord` (raw PCM capture) | `opusenc` (Opus/OGG encoding)
- Multi-client WebSocket broadcast
- Default: 44100 Hz, stereo, 128 kbps Opus

```bash
python3 server/audio_stream.py [--device hw:1,0] [--host 0.0.0.0] [--port 8766] [-v]
```

### Dependencies

**Python packages** (`server/requirements.txt`):
```
websockets>=12.0
pyserial>=3.5
pyserial-asyncio>=0.6
```

**System packages:**
```bash
sudo apt install -y alsa-utils opus-tools
```

- `alsa-utils` provides `arecord` for audio capture
- `opus-tools` provides `opusenc` for Opus encoding

---

## Running with tmux

```bash
# Create tmux session
tmux new-session -d -s m8

# Window 0: Serial bridge
tmux send-keys -t m8:0 \
  'source /opt/m8/bridge-env/bin/activate && python3 /opt/m8/server/bridge.py --host 0.0.0.0' Enter

# Window 1: Audio stream
tmux new-window -t m8:1 -n audio
tmux send-keys -t m8:1 \
  'source /opt/m8/bridge-env/bin/activate && python3 /opt/m8/server/audio_stream.py --host 0.0.0.0' Enter

# Attach
tmux attach -t m8
# Detach: Ctrl+B, then D
```

---

## Running with systemd

### Serial Bridge Service

```ini
# /etc/systemd/system/m8-bridge.service
[Unit]
Description=M8 Serial WebSocket Bridge
After=network.target

[Service]
Type=simple
User=m8
Group=dialout
WorkingDirectory=/opt/m8
ExecStart=/opt/m8/bridge-env/bin/python3 /opt/m8/server/bridge.py --host 0.0.0.0
Restart=always
RestartSec=5
StandardOutput=journal
StandardError=journal
NoNewPrivileges=yes

[Install]
WantedBy=multi-user.target
```

### Audio Stream Service

```ini
# /etc/systemd/system/m8-audio.service
[Unit]
Description=M8 Audio WebSocket Stream
After=network.target sound.target

[Service]
Type=simple
User=m8
Group=audio
WorkingDirectory=/opt/m8
ExecStart=/opt/m8/bridge-env/bin/python3 /opt/m8/server/audio_stream.py --host 0.0.0.0
Restart=always
RestartSec=5
StandardOutput=journal
StandardError=journal
NoNewPrivileges=yes

[Install]
WantedBy=multi-user.target
```

### Enable Services

```bash
# Create m8 user
sudo useradd -r -s /bin/false -G dialout,audio m8
sudo chown -R m8:m8 /opt/m8

# Enable and start
sudo systemctl daemon-reload
sudo systemctl enable m8-bridge m8-audio
sudo systemctl start m8-bridge m8-audio

# Check status
sudo systemctl status m8-bridge m8-audio
sudo journalctl -u m8-bridge -f
sudo journalctl -u m8-audio -f
```

---

## Network Access

### Via Tailscale (Recommended)

```bash
# Install Tailscale
curl -fsSL https://tailscale.com/install.sh | sh
sudo tailscale up

# Check Tailscale IP
tailscale ip -4
# Example: 100.64.0.1

# Bind services to 0.0.0.0 (or Tailscale IP)
python3 server/bridge.py --host 0.0.0.0
python3 server/audio_stream.py --host 0.0.0.0
```

On Android:
1. Install Tailscale from Play Store
2. Log in with same account
3. In the M8 app settings, set host to the server's Tailscale IP (e.g., `100.64.0.1`)

No reverse proxy or TLS needed -- all traffic is encrypted via WireGuard.

### Via Reverse Proxy (nginx)

```nginx
# /etc/nginx/sites-available/m8
server {
    listen 443 ssl;
    server_name m8.yourdomain.com;

    ssl_certificate /etc/letsencrypt/live/m8.yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/m8.yourdomain.com/privkey.pem;

    # Serial bridge WebSocket
    location /ws {
        proxy_pass http://127.0.0.1:8765;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 86400;
    }

    # Audio stream WebSocket
    location /audio {
        proxy_pass http://127.0.0.1:8766;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 86400;
    }
}
```

```bash
sudo ln -sf /etc/nginx/sites-available/m8 /etc/nginx/sites-enabled/m8
sudo nginx -t && sudo systemctl reload nginx
sudo certbot --nginx -d m8.yourdomain.com
```

---

## Verification Checklist

```bash
# 1. Teensy detected
lsusb | grep "16c0"
# Expected: Bus XXX Device XXX: ID 16c0:XXXX ...

# 2. Serial device available
ls -la /dev/ttyACM*
# Expected: crw-rw---- 1 root dialout ... /dev/ttyACM0

# 3. User in required groups
groups | grep -E "dialout|audio"

# 4. Audio device visible
arecord -l | grep -i teensy
# Expected: card N: Teensy [Teensy], device 0: USB Audio [USB Audio]

# 5. System tools available
which arecord opusenc
# Expected: paths to both binaries

# 6. Bridge starts and connects
python3 server/bridge.py --verbose &
sleep 2
# Expected: log messages showing serial connection and WebSocket listening

# 7. WebSocket responds
python3 -c "
import asyncio, websockets
async def test():
    async with websockets.connect('ws://127.0.0.1:8765') as ws:
        msg = await asyncio.wait_for(ws.recv(), timeout=5)
        print('Received:', msg[:80])
asyncio.run(test())
"

# 8. Audio stream starts
python3 server/audio_stream.py --verbose &
sleep 3
# Expected: log messages showing audio capture pipeline started

# 9. Port check
ss -tulnp | grep -E '8765|8766'
# Expected: both ports listening

# 10. Tailscale connected (if using)
tailscale status
```

---

## Port Allocation

| Service              | Port | Protocol  |
|----------------------|------|-----------|
| Serial Bridge        | 8765 | WebSocket |
| Audio Stream         | 8766 | WebSocket |
