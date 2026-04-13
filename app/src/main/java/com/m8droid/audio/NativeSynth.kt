package com.m8droid.audio

/**
 * JNI bridge to the Rust m8-synth native library.
 * All DSP runs in native ARM code — zero GC, deterministic timing.
 */
object NativeSynth {
    init {
        System.loadLibrary("m8_synth")
    }

    /** Initialize the native synth engine. Call once on startup. */
    external fun init()

    /** Destroy the native synth engine. Call on shutdown. */
    external fun destroy()

    /**
     * Trigger notes for all 8 tracks.
     * @param notes ByteArray(8) — MIDI note per track (0=continue, 0xFF=note-off, 1-127=note)
     * @param vols ByteArray(8) — volume per track (0-255)
     */
    external fun triggerRow(notes: ByteArray, vols: ByteArray)

    /**
     * Generate one chunk of 16-bit stereo PCM audio (735 samples = 2940 bytes).
     * This is the hot path — runs entirely in native Rust.
     */
    external fun generateChunk(): ByteArray

    /** Release all voices and clear effects. */
    external fun allNotesOff()

    /** Get master L/R levels for visualization. Returns DoubleArray(2). */
    external fun getMasterLevels(): DoubleArray

    /** Get per-track levels for visualization. Returns DoubleArray(8). */
    external fun getTrackLevels(): DoubleArray
}
