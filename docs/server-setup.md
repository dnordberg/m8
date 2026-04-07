# Server Setup Guide

Set up Dirtywave M8 Headless on a Linux VPS alongside an existing OpenClaw system. The M8 will be accessed remotely from an Android app via a WebSocket-to-serial bridge.

---

## Quickest Integration

Minimal steps to get M8 running. Assumes Teensy 4.1 is USB-connected to the VPS.

```bash
# 1. Check for port conflicts
ss -tulnp | grep LISTEN

# 2. Check if Teensy is detected
lsusb | grep "16c0"
ls /dev/ttyACM*

# 3. Add user to dialout group (if not already)
sudo usermod -a -G dialout $USER
# Re-login required after this

# 4. Create working directory
sudo mkdir -p /opt/m8
sudo chown $USER:$USER /opt/m8
cd /opt/m8

# 5. Clone firmware repo (for reference / flashing)
git clone https://github.com/Dirtywave/M8HeadlessFirmware.git /opt/m8/firmware

# 6. Set up the serial bridge
sudo apt install -y python3-pip python3-venv
python3 -m venv /opt/m8/bridge-env
source /opt/m8/bridge-env/bin/activate
pip install websockets pyserial pyserial-asyncio

# 7. Copy bridge.py to /opt/m8/server/ (from this repo's server/ directory)
mkdir -p /opt/m8/server
# cp server/bridge.py /opt/m8/server/bridge.py

# 8. Start the bridge
python3 /opt/m8/server/bridge.py --serial /dev/ttyACM0

# 9. Test from another terminal
# pip install websocket-client
# python3 -c "
# import websocket
# ws = websocket.create_connection('ws://127.0.0.1:8765')
# ws.send(bytes([0x45, 0x52]))  # enable + reset display
# print(ws.recv())
# ws.close()
# "
```

That's it for basic serial bridge access. The Android app can now connect to `ws://127.0.0.1:8765` (via Tailscale or reverse proxy).

---

## Full Reliable Integration

### Flash Firmware (if not already done)

```bash
# Install Teensy Loader CLI
# Option A: from package manager (if available)
sudo apt install -y teensy-loader-cli

# Option B: build from source
# git clone https://github.com/PaulStoffregen/teensy_loader_cli.git /opt/m8/teensy-loader
# cd /opt/m8/teensy-loader && make && sudo cp teensy_loader_cli /usr/local/bin/

# Flash the latest firmware
teensy_loader_cli --mcu=TEENSY41 -w /opt/m8/firmware/Releases/M8_V6_5_2B_HEADLESS.hex
```

### Install m8c (optional, for local testing)

```bash
# Dependencies
sudo apt install -y git gcc pkg-config make libserialport-dev libsdl3-dev

# Build
git clone https://github.com/laamaa/m8c.git /opt/m8/m8c
cd /opt/m8/m8c && make && sudo make install

# Test
m8c --list
m8c --dev /dev/ttyACM0
```

### Serial-to-WebSocket Bridge

See [serial-bridge.md](serial-bridge.md) for full details. Quick setup:

```bash
# Set up Python environment
python3 -m venv /opt/m8/bridge-env
source /opt/m8/bridge-env/bin/activate
pip install websockets pyserial pyserial-asyncio

# Run the bridge (auto-detects Teensy)
python3 /opt/m8/server/bridge.py
# Or with explicit device:
python3 /opt/m8/server/bridge.py --serial /dev/ttyACM0 --port 8765
```

### Audio Streaming

```bash
# Install audio dependencies
sudo apt install -y pulseaudio pulseaudio-utils ffmpeg

# List audio devices to find M8
pactl list sources short | grep -i m8
# Or: arecord -l | grep -i teensy

# Stream audio via ffmpeg (example -- adjust source name)
ffmpeg \
  -f pulse -i alsa_input.usb-PJRC_Teensy_Audio-00.analog-stereo \
  -c:a libopus -b:a 128k -vbr on \
  -application restricted_lowdelay \
  -frame_duration 10 \
  -f webm pipe:1 | \
  python3 /opt/m8/server/audio_stream.py --port 8766
```

Note: The audio streaming server (`audio_stream.py`) is a planned component. For initial testing, you can use Icecast or a simple HTTP chunked transfer approach.

### M8 Web Display (optional, for browser access)

```bash
# Install Node.js if not present
sudo apt install -y nodejs npm

# Clone and build
git clone https://github.com/Dirtywave/M8WebDisplay.git /opt/m8/webdisplay
cd /opt/m8/webdisplay
npm ci
make deploy

# Static files are now in /opt/m8/webdisplay/deploy/
# Serve with any web server (nginx, caddy, python http.server)
```

Note: M8WebDisplay uses WebSerial API and requires the browser to be on the same machine as the USB device. It won't work for remote access without modification.

---

## Run Commands

### Using tmux

```bash
# Create tmux session for M8 services
tmux new-session -d -s m8

# Window 0: Serial bridge
tmux send-keys -t m8:0 'source /opt/m8/bridge-env/bin/activate && python3 /opt/m8/server/bridge.py' Enter

# Window 1: Audio stream (when implemented)
tmux new-window -t m8:1 -n audio
tmux send-keys -t m8:1 'ffmpeg -f pulse -i alsa_input.usb-PJRC_Teensy_Audio-00.analog-stereo -c:a libopus -b:a 128k -f ogg - | nc -l -p 8766' Enter

# Attach to session
tmux attach -t m8

# Detach: Ctrl+B, then D
# List windows: Ctrl+B, then W
```

### Quick one-liner

```bash
# Just the bridge (foreground)
cd /opt/m8 && source bridge-env/bin/activate && python3 server/bridge.py

# Bridge in background with logging
cd /opt/m8 && source bridge-env/bin/activate && nohup python3 server/bridge.py >> /opt/m8/bridge.log 2>&1 &
```

---

## Access Instructions

### Via Tailscale (Recommended)

```bash
# Install Tailscale on the VPS (if not already installed)
curl -fsSL https://tailscale.com/install.sh | sh
sudo tailscale up

# Check Tailscale IP
tailscale ip -4
# Example output: 100.64.0.5

# On your Android device:
# 1. Install Tailscale from Play Store
# 2. Log in with same account
# 3. In the M8 app, set server to: 100.64.0.5:8765
#    Or use MagicDNS: your-vps-name.tailnet-name.ts.net:8765
```

With Tailscale, no reverse proxy or TLS configuration is needed -- all traffic is encrypted end-to-end via WireGuard.

### Via Reverse Proxy (nginx)

```nginx
# /etc/nginx/sites-available/m8
server {
    listen 443 ssl;
    server_name m8.yourdomain.com;

    ssl_certificate /etc/letsencrypt/live/m8.yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/m8.yourdomain.com/privkey.pem;

    # WebSocket serial bridge
    location /ws {
        proxy_pass http://127.0.0.1:8765;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_read_timeout 86400;
    }

    # Audio stream
    location /audio {
        proxy_pass http://127.0.0.1:8766;
        proxy_buffering off;
        proxy_cache off;
    }

    # Web display (optional)
    location / {
        root /opt/m8/webdisplay/deploy;
        try_files $uri $uri/ =404;
    }
}
```

```bash
# Enable the site
sudo ln -sf /etc/nginx/sites-available/m8 /etc/nginx/sites-enabled/m8
sudo nginx -t && sudo systemctl reload nginx

# Get TLS cert (if not already set up)
sudo certbot --nginx -d m8.yourdomain.com
```

### Via Existing OpenClaw Gateway

If OpenClaw already has a reverse proxy, add the M8 locations to the existing configuration rather than creating a new server block:

```nginx
# Add to existing OpenClaw server block
location /m8/ws {
    proxy_pass http://127.0.0.1:8765;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_read_timeout 86400;
}

location /m8/audio {
    proxy_pass http://127.0.0.1:8766;
    proxy_buffering off;
}
```

---

## Process Management

### systemd Service (Recommended for production)

```ini
# /etc/systemd/system/m8-bridge.service
[Unit]
Description=M8 Serial WebSocket Bridge
After=network.target
Wants=network-online.target

[Service]
Type=simple
User=m8
Group=dialout
WorkingDirectory=/opt/m8
ExecStart=/opt/m8/bridge-env/bin/python3 /opt/m8/server/bridge.py
Restart=always
RestartSec=5
StandardOutput=journal
StandardError=journal

# Security hardening
NoNewPrivileges=yes
ProtectSystem=strict
ReadWritePaths=/opt/m8
PrivateTmp=yes

[Install]
WantedBy=multi-user.target
```

```bash
# Create m8 user (if not using your own)
sudo useradd -r -s /bin/false -G dialout,audio m8
sudo chown -R m8:m8 /opt/m8

# Enable and start
sudo systemctl daemon-reload
sudo systemctl enable m8-bridge
sudo systemctl start m8-bridge

# Check status
sudo systemctl status m8-bridge
sudo journalctl -u m8-bridge -f
```

### tmux (Simpler alternative)

```bash
# Start
tmux new-session -d -s m8 'source /opt/m8/bridge-env/bin/activate && python3 /opt/m8/server/bridge.py'

# Check
tmux list-sessions | grep m8

# Attach
tmux attach -t m8

# Stop
tmux kill-session -t m8
```

---

## Troubleshooting

See [troubleshooting.md](troubleshooting.md) for comprehensive coverage. Quick checks:

```bash
# Teensy detected?
lsusb | grep "16c0"

# Serial device available?
ls -la /dev/ttyACM*

# User in dialout group?
groups | grep dialout

# Port conflicts?
ss -tulnp | grep -E '8765|8766|8000'

# Bridge running?
pgrep -f bridge.py

# OpenClaw still healthy?
# (use OpenClaw's own health check)
```

---

## Verification Checklist

Run these commands after setup to confirm everything works:

```bash
# 1. Teensy detected
lsusb | grep "16c0"
# Expected: Bus XXX Device XXX: ID 16c0:048a ...

# 2. Serial device available
ls -la /dev/ttyACM*
# Expected: crw-rw---- 1 root dialout ... /dev/ttyACM0

# 3. User in dialout group
groups | grep dialout
# Expected: ... dialout ...

# 4. No port conflicts with OpenClaw
ss -tulnp | grep -E '8765|8766|8000'
# Expected: empty (before starting M8 services)

# 5. Bridge starts and connects
source /opt/m8/bridge-env/bin/activate
python3 /opt/m8/server/bridge.py &
sleep 2
# Expected: log messages showing serial connection

# 6. WebSocket responds
python3 -c "
import asyncio, websockets
async def test():
    async with websockets.connect('ws://127.0.0.1:8765') as ws:
        msg = await asyncio.wait_for(ws.recv(), timeout=5)
        print('Received:', msg[:50])
asyncio.run(test())
"
# Expected: received data or JSON status message

# 7. Audio device visible (if streaming audio)
pactl list sources short | grep -i m8
# Expected: M8 audio source listed

# 8. OpenClaw still healthy
ss -tulnp | grep LISTEN
# Expected: OpenClaw ports still listening, M8 ports added

# 9. Tailscale connected (if using)
tailscale status
# Expected: shows your VPS and Android device

# 10. All M8 services running
pgrep -af 'bridge.py|m8'
# Or if using systemd:
systemctl status m8-bridge
```
