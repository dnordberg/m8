# Troubleshooting

Troubleshooting guide for the m8 system: Teensy hardware, server bridges, network, audio, and Android app.

---

## Teensy / Hardware

### Teensy not detected by lsusb

**Symptom:** `lsusb | grep "16c0"` returns nothing.

**Cause:** Teensy not plugged in, bad USB cable, or USB passthrough not configured (VM).

**Fix:**
```bash
# Check all USB devices
lsusb

# Check kernel messages for USB events
dmesg | tail -20

# Try a different USB cable (data cable, not charge-only)
# Try a different USB port

# If running in a VM, ensure USB passthrough is enabled:
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

# Monitor udev events while plugging in Teensy
udevadm monitor --property &

# Check dmesg for details
dmesg | grep -i "acm\|teensy\|16c0"
```

### Permission denied on serial device

**Symptom:** `bridge.py` fails with "Permission denied" or "Failed to open /dev/ttyACM0".

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

### Multiple /dev/ttyACM devices

**Symptom:** Multiple `/dev/ttyACM*` devices, unsure which is M8.

**Fix:**
```bash
# List serial ports with USB metadata
python3 -m serial.tools.list_ports -v

# The M8 Teensy shows vendor ID 16c0 (PJRC)
# bridge.py auto-detection handles this automatically

# Or check each device manually:
for dev in /dev/ttyACM*; do
  echo "$dev:"
  udevadm info -q property "$dev" | grep -E "ID_VENDOR|ID_MODEL"
  echo
done
```

---

## Serial Bridge

### Bridge can't find serial device

**Symptom:** `bridge.py` logs "No M8 Teensy detected -- retrying in 2s" repeatedly.

**Cause:** Teensy not connected, wrong device path, or permissions.

**Fix:**
```bash
# Check device exists
ls -la /dev/ttyACM0

# Check if another process has it open
fuser /dev/ttyACM0
lsof /dev/ttyACM0

# Force a specific device
python3 server/bridge.py --serial /dev/ttyACM0

# Check USB vendor ID detection
python3 -c "
import serial.tools.list_ports
for p in serial.tools.list_ports.comports():
    print(f'{p.device}: vid=0x{p.vid or 0:04x}, pid=0x{p.pid or 0:04x}')
"
```

### WebSocket connection refused

**Symptom:** Android app can't connect, "Connection refused".

**Cause:** Bridge not running, wrong host binding, or firewall.

**Fix:**
```bash
# Check if bridge is running
pgrep -af bridge.py

# Check if port is listening
ss -tulnp | grep 8765

# Common mistake: bridge defaults to 127.0.0.1 (localhost only)
# For remote access, bind to 0.0.0.0:
python3 server/bridge.py --host 0.0.0.0

# Or bind to Tailscale IP:
python3 server/bridge.py --host $(tailscale ip -4)

# Test locally
python3 -c "
import asyncio, websockets
async def test():
    async with websockets.connect('ws://127.0.0.1:8765') as ws:
        print('Connected!')
asyncio.run(test())
"
```

### Bridge crashes on Teensy disconnect

**Symptom:** Bridge process exits when Teensy is unplugged.

**Expected behavior:** Bridge should catch the serial error, notify clients with `{"event": "serial_disconnected"}`, and retry every 2 seconds.

**Fix:**
```bash
# Check logs for traceback
journalctl -u m8-bridge -f
# Or run with verbose logging:
python3 server/bridge.py --verbose

# If the bridge does crash, systemd will auto-restart it
sudo systemctl restart m8-bridge
```

---

## Audio

### No audio device found

**Symptom:** `audio_stream.py` logs "No Teensy audio device detected" repeatedly.

**Cause:** Teensy audio not recognized by ALSA.

**Fix:**
```bash
# Check ALSA devices
arecord -l
# Look for "Teensy" or "PJRC"

# Check /proc/asound
cat /proc/asound/cards

# Check USB audio IDs
for card in /proc/asound/card*/usbid; do
  echo "$card: $(cat $card 2>/dev/null)"
done

# Manually specify device
python3 server/audio_stream.py --device hw:1,0

# Make sure user is in audio group
sudo usermod -a -G audio $USER
```

### Audio crackling / dropouts

**Symptom:** Audio plays but has pops, clicks, or gaps.

**Cause:** Buffer underruns, CPU contention, or network jitter.

**Fix:**
```bash
# Check CPU usage
top -p $(pgrep -f audio_stream.py)

# Give audio process higher priority
sudo nice -n -10 python3 server/audio_stream.py

# Try a higher bitrate or different sample rate
python3 server/audio_stream.py --bitrate 192000

# Add user to audio group for real-time scheduling
sudo usermod -a -G audio $USER
# Add to /etc/security/limits.d/audio.conf:
# @audio - rtprio 95
# @audio - memlock unlimited
```

### Audio format mismatch (KNOWN ISSUE)

**Symptom:** Audio connects but no sound plays, or garbled audio.

**Cause:** The server streams Opus encoded in an OGG container. The Android client's M8AudioClient expects either raw Opus frames (with a `0x02` header byte) or raw PCM data. The OGG container headers are not recognized by the client's format detection logic, so the OGG data is treated as raw PCM, producing garbage audio.

**Current status:** This is a known architecture mismatch. The server's `audio_stream.py` uses `opusenc` which outputs OGG/Opus, but the Android client's `OpusDecoder` (MediaCodec) expects individual Opus frames without OGG framing.

**Workaround options:**
1. Modify `audio_stream.py` to extract raw Opus frames instead of streaming OGG
2. Add an OGG demuxer to the Android client
3. Replace `opusenc` with `ffmpeg` on the server to output raw Opus packets

### Sample rate mismatch (KNOWN ISSUE)

**Symptom:** Audio pitch is slightly off, or playback speed is wrong.

**Cause:** The server captures audio at **44100 Hz** (Teensy default), but the Android `M8AudioPlayer` plays back at **48000 Hz**. This 8.8% mismatch causes audio to play too fast and at a higher pitch.

**Fix options:**
1. Change `M8AudioPlayer.SAMPLE_RATE` from 48000 to 44100 in the Android app
2. Add sample rate conversion on the server (resample to 48000 before encoding)
3. Read the `sample_rate` field from the server's `audio_connected` JSON message and configure AudioTrack dynamically

### opusenc or arecord not found

**Symptom:** `audio_stream.py` exits with "arecord not found" or "opusenc not found".

**Fix:**
```bash
sudo apt install -y alsa-utils opus-tools

# Verify
which arecord opusenc
arecord --version
opusenc --version
```

---

## Network

### Can't connect from Android app

**Symptom:** App shows "Connecting..." indefinitely or "Connection failed".

**Fix (check in order):**
```bash
# 1. Bridge running and listening?
ss -tulnp | grep -E '8765|8766'

# 2. Bridge bound to reachable address?
# If it shows 127.0.0.1, it's localhost-only. Restart with:
python3 server/bridge.py --host 0.0.0.0

# 3. Tailscale connected on both devices?
tailscale status

# 4. Firewall?
sudo ufw status
# If using Tailscale, allow its interface:
sudo ufw allow in on tailscale0

# 5. Can you reach the server?
# From Android Termux or another device:
ping <server-tailscale-ip>
```

### Tailscale not routing

**Symptom:** Tailscale shows connected but M8 services unreachable.

**Fix:**
```bash
# Check both devices are on same tailnet
tailscale status

# Ping test
tailscale ping <server-tailscale-ip>

# MOST COMMON ISSUE: bridge bound to 127.0.0.1
# Fix: bind to 0.0.0.0 or Tailscale IP
python3 server/bridge.py --host 0.0.0.0
python3 server/audio_stream.py --host 0.0.0.0

# Check Tailscale ACLs if configured
```

### Firewall blocking ports

**Symptom:** Works locally but not remotely.

**Fix:**
```bash
# Check UFW
sudo ufw status verbose

# If using Tailscale (recommended), allow all Tailscale traffic:
sudo ufw allow in on tailscale0

# Or allow specific ports:
sudo ufw allow 8765/tcp comment "M8 serial bridge"
sudo ufw allow 8766/tcp comment "M8 audio stream"
```

---

## Android App

### Control message field name mismatch (KNOWN ISSUE)

**Symptom:** App connects to bridge but never shows "M8 Connected" status. Serial connect/disconnect events are ignored.

**Cause:** The server (`bridge.py`) sends JSON control messages with the field name `"event"`:
```json
{"event": "serial_connected", "device": "/dev/ttyACM0"}
```

But the Android client (`ConnectionManager.kt`) checks for the field name `"type"`:
```kotlin
when (json.optString("type")) {
    "serial_connected" -> ...
}
```

Since `optString("type")` returns empty string when the actual field is `"event"`, the control message is never matched.

**Fix:** Either:
1. Change `ConnectionManager.kt` to read `json.optString("event")` instead of `"type"`
2. Change `bridge.py` to send `{"type": "serial_connected"}` instead of `{"event": ...}`

### Display not rendering

**Symptom:** Connected but screen is blank.

**Fix:**
- The app must send enable + reset on connect: `[0x45, 0x52]`
- Check that binary WebSocket messages are being received (enable verbose logging)
- Verify the control message issue above is not preventing display enable

### Audio not playing

**Symptom:** Display works but no sound.

**Fix:**
- Check audio stream service is running: `ss -tulnp | grep 8766`
- Check Android media volume (not ringer volume)
- Check the audio format mismatch and sample rate mismatch issues above
- Look for errors in logcat: `adb logcat | grep -i "m8audio\|opus\|audiotrack"`

### Input lag

**Symptom:** Button presses have noticeable delay.

**Fix:**
- Check network latency: `ping <server>`
- Use Tailscale or LAN instead of internet for lower latency
- Ensure touch events send immediately (KeyMapper dispatches on key down/up without debouncing)

### App crashes on reconnect

**Symptom:** App crashes when server restarts or network drops.

**Fix:**
```bash
# Check logcat for crash stack trace
adb logcat | grep -i "m8\|crash\|fatal"
```

Both M8WebSocketClient and M8AudioClient use exponential backoff reconnection (2s initial, 30s max) and should handle disconnections gracefully.

---

## Quick Diagnostics

Run these commands on the server for a quick health check:

```bash
# Hardware
lsusb | grep "16c0"
ls -la /dev/ttyACM*
arecord -l | grep -i teensy

# Services
pgrep -af "bridge.py\|audio_stream.py"
ss -tulnp | grep -E '8765|8766'

# Network
tailscale status
sudo ufw status

# Logs
journalctl -u m8-bridge --no-pager -n 20
journalctl -u m8-audio --no-pager -n 20
```
