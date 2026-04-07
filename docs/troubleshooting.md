# Troubleshooting

Comprehensive troubleshooting guide for M8 Headless running on a Linux VPS with OpenClaw.

---

## Teensy / Hardware

### Teensy not detected by lsusb

**Symptom:** `lsusb | grep "16c0"` returns nothing.

**Cause:** Teensy not plugged in, bad USB cable, or USB passthrough not configured (if VPS is a VM).

**Fix:**
```bash
# Check all USB devices
lsusb

# Check kernel messages for USB events
dmesg | tail -20

# Try a different USB cable (data cable, not charge-only)
# Try a different USB port

# If running in a VM, ensure USB passthrough is enabled:
# - VirtualBox: Devices > USB > Add Teensy
# - Proxmox: qm set <vmid> -usb0 host=16c0:048a
# - KVM/libvirt: use virt-manager USB redirection
```

### /dev/ttyACM0 not appearing

**Symptom:** `lsusb` shows Teensy but `ls /dev/ttyACM*` returns "No such file or directory".

**Cause:** Missing kernel module `cdc_acm`, or udev rules preventing device creation.

**Fix:**
```bash
# Load the kernel module
sudo modprobe cdc_acm

# Check if it loaded
lsmod | grep cdc_acm

# Check udev for relevant rules
udevadm monitor --property &
# Then plug in the Teensy and watch output

# If module loads but device still missing, check dmesg:
dmesg | grep -i "acm\|teensy\|16c0"
```

### Permission denied on serial device

**Symptom:** `bridge.py` or `m8c` fails with "Permission denied: /dev/ttyACM0".

**Cause:** User not in `dialout` group.

**Fix:**
```bash
# Check current groups
groups

# Add to dialout
sudo usermod -a -G dialout $USER

# IMPORTANT: You must log out and back in, or:
newgrp dialout

# Verify
ls -la /dev/ttyACM0
# Should show: crw-rw---- 1 root dialout ...
```

### Multiple Teensy devices

**Symptom:** Multiple `/dev/ttyACM*` devices, unsure which is M8.

**Fix:**
```bash
# List serial ports with details
python3 -m serial.tools.list_ports -v

# Or check USB device tree
for dev in /dev/ttyACM*; do
  echo "$dev:"
  udevadm info -q property "$dev" | grep -E "ID_VENDOR|ID_MODEL|ID_SERIAL"
  echo
done

# The M8 Teensy will show vendor ID 16c0
```

### SD card issues

**Symptom:** M8 boots but shows filesystem errors or can't save.

**Cause:** SD card not formatted correctly.

**Fix:**
- Cards <= 32GB: format as FAT32
- Cards > 32GB: format as exFAT
- Use a quality SD card (SanDisk, Samsung EVO)
- Format on a computer, not the Teensy

---

## Serial Bridge

### Bridge can't open serial port

**Symptom:** `bridge.py` logs "Failed to open /dev/ttyACM0" repeatedly.

**Cause:** Device doesn't exist, permissions, or another process has it open.

**Fix:**
```bash
# Check device exists
ls -la /dev/ttyACM0

# Check if another process has it open
fuser /dev/ttyACM0
# Or:
lsof /dev/ttyACM0

# Kill conflicting process if needed
# (e.g., if m8c is running, stop it first)

# Check permissions
groups | grep dialout
```

### WebSocket connection refused

**Symptom:** Android app can't connect, "Connection refused" error.

**Cause:** Bridge not running, wrong port, or firewall blocking.

**Fix:**
```bash
# Check if bridge is running
pgrep -af bridge.py

# Check if port is listening
ss -tulnp | grep 8765

# Check firewall
sudo ufw status
sudo iptables -L -n | grep 8765

# If using Tailscale, check it's connected
tailscale status

# Test locally
python3 -c "
import asyncio, websockets
async def test():
    async with websockets.connect('ws://127.0.0.1:8765') as ws:
        print('Connected!')
asyncio.run(test())
"
```

### Data corruption / garbled display

**Symptom:** M8 display renders incorrectly on the Android app.

**Cause:** Baud rate mismatch, or WebSocket binary/text mode confusion.

**Fix:**
```bash
# Ensure bridge uses correct baud rate (9600 is default for M8)
python3 /opt/m8/server/bridge.py --baud 9600

# The Android app must send/receive WebSocket messages as binary, not text
# Check the app's WebSocket configuration
```

### High latency

**Symptom:** Noticeable delay between input and display update.

**Cause:** Network latency, buffering, or CPU contention.

**Fix:**
```bash
# Check network latency to server
ping <server-ip>

# Check CPU usage (audio encoding can be heavy)
top -p $(pgrep -f bridge.py)

# Reduce buffer sizes in bridge.py if needed
# Check if other processes are consuming CPU
htop
```

### Bridge crashes on Teensy disconnect

**Symptom:** Bridge process exits when Teensy is unplugged.

**Cause:** Unhandled serial exception (should not happen with current bridge.py).

**Fix:**
```bash
# Check bridge logs
journalctl -u m8-bridge -f
# Or: cat /opt/m8/bridge.log

# The bridge should handle disconnection gracefully and retry
# If it crashes, check Python traceback and update bridge.py

# As a workaround, systemd will auto-restart:
sudo systemctl restart m8-bridge
```

---

## Audio

### No audio device found

**Symptom:** `pactl list sources short | grep -i m8` returns nothing.

**Cause:** PulseAudio not detecting the Teensy USB audio device.

**Fix:**
```bash
# Check ALSA first
arecord -l
# Look for "Teensy" or "PJRC"

# If ALSA sees it but PulseAudio doesn't:
pulseaudio --kill
pulseaudio --start

# List all PulseAudio sources
pactl list sources short

# The device name is usually like:
# alsa_input.usb-PJRC_Teensy_Audio-00.analog-stereo
```

### Audio crackling / dropouts

**Symptom:** Audio plays but has pops, clicks, or gaps.

**Cause:** Buffer underruns, CPU contention, or network jitter.

**Fix:**
```bash
# Increase buffer size in ffmpeg
# Change -frame_duration from 10 to 20 (ms)

# Check CPU usage
top

# Give audio process higher priority
sudo nice -n -10 ffmpeg ...

# Check for audio thread scheduling issues
# Add to /etc/security/limits.d/audio.conf:
# @audio - rtprio 95
# @audio - memlock unlimited

# Add user to audio group
sudo usermod -a -G audio $USER
```

### PulseAudio vs ALSA conflicts

**Symptom:** Audio works with ALSA tools but not PulseAudio, or vice versa.

**Fix:**
```bash
# Check if PulseAudio is running
ps aux | grep pulseaudio

# If you want to bypass PulseAudio and use ALSA directly:
# Use -f alsa instead of -f pulse in ffmpeg

# If PulseAudio is needed, ensure it's not blocking ALSA:
# Edit /etc/pulse/default.pa and check module-alsa-sink/source settings
```

### Audio latency too high

**Symptom:** Audio is noticeably behind the display.

**Fix:**
```bash
# Reduce Opus frame duration
# In ffmpeg: -frame_duration 5  (minimum for Opus)

# Reduce buffer sizes on both ends:
# Server: smaller ffmpeg output buffer
# Android: smaller AudioTrack/Oboe buffer

# Use OPUS_APPLICATION_RESTRICTED_LOWDELAY mode
# In ffmpeg: -application restricted_lowdelay

# Consider using WebRTC instead of plain WebSocket for audio
# (WebRTC has built-in jitter buffer and adaptive bitrate)
```

---

## Network

### Can't connect from Android app

**Symptom:** App shows "Connection failed" or times out.

**Fix:**
```bash
# 1. Check bridge is running and listening
ss -tulnp | grep 8765

# 2. Check network connectivity
# From Android (via Termux or similar):
ping <server-ip>

# 3. Check Tailscale (if using)
tailscale status
# Both devices should show as connected

# 4. Check firewall on server
sudo ufw status
# If using ufw with Tailscale, ensure tailscale0 interface is allowed:
sudo ufw allow in on tailscale0

# 5. Try connecting from server itself
curl -i -N -H "Connection: Upgrade" -H "Upgrade: websocket" \
  -H "Sec-WebSocket-Version: 13" -H "Sec-WebSocket-Key: dGVzdA==" \
  http://127.0.0.1:8765/
```

### Tailscale not routing

**Symptom:** Tailscale shows connected but can't reach M8 services.

**Fix:**
```bash
# Check Tailscale status on both devices
tailscale status

# Check if the server's Tailscale IP is reachable
tailscale ping <server-tailscale-ip>

# Check if the bridge is listening on the right interface
# If bridge binds to 127.0.0.1, it won't be reachable via Tailscale IP
# Either:
# a) Bind bridge to 0.0.0.0 (if Tailscale is your only network exposure)
python3 /opt/m8/server/bridge.py --host 0.0.0.0
# b) Or bind to Tailscale IP specifically
python3 /opt/m8/server/bridge.py --host $(tailscale ip -4)

# Check Tailscale ACLs if you have them configured
```

### Firewall blocking ports

**Symptom:** Connection works locally but not remotely.

**Fix:**
```bash
# Check UFW
sudo ufw status verbose

# Allow M8 ports (only if NOT using Tailscale as sole access method)
sudo ufw allow 8765/tcp comment "M8 serial bridge"
sudo ufw allow 8766/tcp comment "M8 audio stream"

# Or if using Tailscale, allow all traffic on tailscale0:
sudo ufw allow in on tailscale0
```

### nginx WebSocket proxy not working

**Symptom:** HTTP 502, timeout, or WebSocket upgrade fails through nginx.

**Fix:**
```nginx
# Ensure these headers are set in the location block:
location /ws {
    proxy_pass http://127.0.0.1:8765;
    proxy_http_version 1.1;                    # REQUIRED for WebSocket
    proxy_set_header Upgrade $http_upgrade;     # REQUIRED
    proxy_set_header Connection "upgrade";      # REQUIRED
    proxy_read_timeout 86400;                   # Prevent idle timeout
}
```

```bash
# Test nginx config
sudo nginx -t

# Reload after changes
sudo systemctl reload nginx

# Check nginx error log
sudo tail -f /var/log/nginx/error.log
```

---

## OpenClaw Coexistence

### Port conflicts

**Symptom:** M8 bridge fails to start with "Address already in use".

**Fix:**
```bash
# Find what's using the port
ss -tulnp | grep 8765

# If OpenClaw or another service uses 8765, change the M8 bridge port:
python3 /opt/m8/server/bridge.py --port 8775

# Update Android app and reverse proxy config accordingly
```

### Resource contention

**Symptom:** OpenClaw performance degrades after starting M8 services.

**Fix:**
```bash
# Check resource usage
htop

# Audio encoding (ffmpeg) can be CPU-heavy
# Set CPU affinity to limit M8 to specific cores:
taskset -c 2,3 python3 /opt/m8/server/bridge.py
taskset -c 2,3 ffmpeg ...

# Or use cgroups to limit CPU usage:
# Create /etc/systemd/system/m8-bridge.service.d/limits.conf:
# [Service]
# CPUQuota=50%
# MemoryMax=512M
```

### Service ordering issues

**Symptom:** M8 services start before USB device is ready.

**Fix:**
```bash
# Add udev dependency to systemd service
# Edit /etc/systemd/system/m8-bridge.service:
# [Unit]
# After=network.target dev-ttyACM0.device
# BindsTo=dev-ttyACM0.device

# Or rely on the bridge's built-in auto-detection retry loop
# (it will wait for the device to appear)
```

---

## Android App

### WebSocket connection fails

**Symptom:** App shows "Connecting..." indefinitely.

**Fix:**
- Verify server address and port in app settings
- Check network connectivity (WiFi, Tailscale)
- Try opening `ws://<server>:8765` in a browser WebSocket tester
- Check server logs: `journalctl -u m8-bridge -f`

### Display not rendering

**Symptom:** Connected but screen is blank or garbled.

**Fix:**
- The app must send enable + reset command on connect: `[0x45, 0x52]`
- Check that WebSocket messages are being received (enable app debug logging)
- Verify firmware version matches expected protocol

### Audio not playing

**Symptom:** Display works but no sound.

**Fix:**
- Audio stream is separate from serial -- check audio server is running
- Check Android volume (media volume, not ringer)
- Verify audio stream URL in app settings
- Check server audio capture: `pactl list sources short`

### Input lag

**Symptom:** Button presses have noticeable delay.

**Fix:**
- Check network latency: `ping <server>`
- Reduce display buffer in the app
- Ensure touch events send immediately (no debouncing on M8 key commands)
- Use Tailscale or LAN instead of internet for lower latency

### App crashes on reconnect

**Symptom:** App crashes when server restarts or network drops.

**Fix:**
- Check Android logcat for crash stack trace:
  ```bash
  adb logcat | grep -i "m8\|crash\|fatal"
  ```
- Ensure WebSocket client handles connection close gracefully
- Connection manager should use exponential backoff, not immediate retry flood
