#!/usr/bin/env bash
# Build Rust m8-synth for Android targets and copy .so files into app/src/main/jniLibs
set -euo pipefail

export PATH="$HOME/.cargo/bin:$PATH"
export ANDROID_NDK_HOME="$HOME/Library/Android/sdk/ndk/27.2.12479018"

cd "$(dirname "$0")/m8-synth"

echo "=== Building m8-synth for Android ==="

# Build for arm64 (most Android devices) and x86_64 (emulator)
cargo ndk \
    -t aarch64-linux-android \
    -t x86_64-linux-android \
    -o ../app/src/main/jniLibs \
    build --release 2>&1

echo "=== Done ==="
ls -lh ../app/src/main/jniLibs/*/libm8_synth.so 2>/dev/null || echo "WARNING: .so files not found"
