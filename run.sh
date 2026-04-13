#!/usr/bin/env bash
# Run M8droid on Android emulator: clean state, build, install, launch.
# Usage: ./run.sh

set -euo pipefail

SDK="$HOME/Library/Android/sdk"
ADB="$SDK/platform-tools/adb"
EMU="$SDK/emulator/emulator"
AVD="Medium_Phone_API_36.0"
PKG="com.m8droid"
ACTIVITY="$PKG/.MainActivity"
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

echo "=== M8droid Run ==="

# Reuse running emulator if present, otherwise cold boot
if $ADB devices 2>/dev/null | grep -q "emulator.*device"; then
    echo "Reusing running emulator."
else
    echo "Starting emulator (cold boot, no saved state)..."
    $EMU -avd "$AVD" -no-snapshot-load -gpu auto -read-only &
    EMU_PID=$!

    echo "Waiting for emulator..."
    for i in $(seq 1 90); do
        if $ADB devices 2>/dev/null | grep -q "emulator.*device"; then
            break
        fi
        sleep 2
    done

    echo "Waiting for boot..."
    $ADB wait-for-device
    while [ "$($ADB shell getprop sys.boot_completed 2>/dev/null | tr -d '\r\n')" != "1" ]; do
        sleep 2
    done
    echo "Emulator booted."
fi

# Stop app if running (install -r will replace it in place)
$ADB shell am force-stop "$PKG" 2>/dev/null || true

# Build
echo "Building..."
./gradlew assembleDebug -q

# Install
echo "Installing..."
$ADB install -r app/build/outputs/apk/debug/app-debug.apk

# Launch
echo "Launching..."
$ADB shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null

echo ""
echo "=== M8droid running on emulator ==="
echo "Logs: adb logcat --pid=\$(adb shell pidof $PKG)"
echo "Stop: adb -s emulator-5554 emu kill"
