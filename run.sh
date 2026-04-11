#!/usr/bin/env bash
# Run M8 on Android emulator: clean state, build, install, launch.
# Usage: ./run.sh

set -euo pipefail

SDK="$HOME/Library/Android/sdk"
ADB="$SDK/platform-tools/adb"
EMU="$SDK/emulator/emulator"
AVD="Medium_Phone_API_36.0"
PKG="com.m8"
ACTIVITY="$PKG/.MainActivity"
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

echo "=== M8 Run ==="

# Kill existing emulator
if $ADB devices 2>/dev/null | grep -q emulator; then
    echo "Killing existing emulator..."
    $ADB -s emulator-5554 emu kill 2>/dev/null || true
    sleep 3
fi

# Launch emulator with no snapshot (cold boot)
echo "Starting emulator (cold boot, no saved state)..."
$EMU -avd "$AVD" -no-snapshot-load -gpu auto &
EMU_PID=$!

# Wait for adb device
echo "Waiting for emulator..."
for i in $(seq 1 90); do
    if $ADB devices 2>/dev/null | grep -q "emulator.*device"; then
        break
    fi
    sleep 2
done

# Wait for boot
echo "Waiting for boot..."
$ADB wait-for-device
while [ "$($ADB shell getprop sys.boot_completed 2>/dev/null | tr -d '\r\n')" != "1" ]; do
    sleep 2
done
echo "Emulator booted."

# Uninstall old app if present
echo "Removing old app..."
$ADB uninstall "$PKG" 2>/dev/null || true

# Build
echo "Building..."
./gradlew assembleDebug -q

# Install
echo "Installing..."
$ADB install -r app/build/outputs/apk/debug/app-debug.apk

# Launch
echo "Launching..."
$ADB shell am start -n "$ACTIVITY"

echo ""
echo "=== M8 running on emulator ==="
echo "Logs: adb logcat --pid=\$(adb shell pidof $PKG)"
echo "Stop: adb -s emulator-5554 emu kill"
