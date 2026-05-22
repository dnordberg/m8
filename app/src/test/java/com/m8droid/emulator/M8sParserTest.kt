package com.m8droid.emulator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class M8sParserTest {
    @Test
    fun `parses groove pool from v4 song file`() {
        val bytes = minimalV4Song()
        val grooveOffset = 0xEE
        bytes[grooveOffset + 0] = 3
        bytes[grooveOffset + 1] = 9
        bytes[grooveOffset + 16] = 4
        bytes[grooveOffset + 17] = 8

        val parsed = M8sParser.parse(bytes)

        assertEquals(3, parsed.grooves[0].ticks[0])
        assertEquals(9, parsed.grooves[0].ticks[1])
        assertEquals(4, parsed.grooves[1].ticks[0])
        assertEquals(8, parsed.grooves[1].ticks[1])
    }

    @Test
    fun `applies parsed grooves into mutable song`() {
        val bytes = minimalV4Song()
        bytes[0xEE + 0] = 5
        bytes[0xEE + 1] = 7
        val parsed = M8sParser.parse(bytes)
        val song = M8Song()

        M8sParser.applyTo(parsed, song)

        assertEquals(5, song.grooves[0].ticks[0])
        assertEquals(7, song.grooves[0].ticks[1])
    }

    @Test
    fun `parsed song exposes partial import warnings for unsupported pools`() {
        val parsed = M8sParser.parse(minimalV4Song())

        assertTrue(parsed.warnings.any { it.contains("instrument", ignoreCase = true) })
        assertTrue(parsed.warnings.any { it.contains("HP/LP", ignoreCase = false) })
        assertTrue(parsed.warnings.any { it.contains("scale", ignoreCase = true) })
        assertTrue(
            parsed.warnings.none { it.contains("Mixer", ignoreCase = false) },
            "mixer should no longer appear in deferred warnings",
        )
        assertTrue(
            parsed.warnings.none { w ->
                w.startsWith("Global FX settings (chorus/delay/reverb) are not imported")
            },
            "chorus/delay/reverb levels are now imported — broad FX warning should be gone",
        )
    }

    @Test
    fun `parses mixer levels from V4 song mixer block`() {
        val bytes = minimalV4Song()
        // Mixer block is at file offset 0xCE.
        val mx = 0xCE
        bytes[mx + 0] = 0xC0.toByte()              // master_volume
        bytes[mx + 1] = 0x42                        // master_limit (skipped)
        for (t in 0 until 8) {
            bytes[mx + 2 + t] = (0x10 + t * 0x10).toByte() // track_volume[t]
        }
        bytes[mx + 10] = 0x55                       // chorus_volume
        bytes[mx + 11] = 0x66                       // delay_volume
        bytes[mx + 12] = 0x77                       // reverb_volume
        // 13 bytes of analog/usb input mixer (offsets 13..25 within block) — skipped.
        bytes[mx + 26] = 0xA5.toByte()              // dj_filter

        val parsed = M8sParser.parse(bytes)

        assertEquals(0xC0, parsed.mixer.masterVolume)
        for (t in 0 until 8) {
            assertEquals(0x10 + t * 0x10, parsed.mixer.trackVolumes[t])
        }
        assertEquals(0x55, parsed.mixer.chorusVolume)
        assertEquals(0x66, parsed.mixer.delayVolume)
        assertEquals(0x77, parsed.mixer.reverbVolume)
        assertEquals(0xA5, parsed.mixer.djFilter)
    }

    @Test
    fun `parses global FX block from V4 song file`() {
        val bytes = v4SongWithFxBlock()
        val fx = 0x1A5C1
        bytes[fx + 0] = 0x11   // chorus_mod_depth
        bytes[fx + 1] = 0x22   // chorus_mod_freq
        bytes[fx + 2] = 0x33   // chorus_reverb_send
        // 3 unused (offsets 3..5)
        bytes[fx + 6] = 0x44   // delay_time_l
        bytes[fx + 7] = 0x55   // delay_time_r
        bytes[fx + 8] = 0x66   // delay_feedback
        bytes[fx + 9] = 0x77   // delay_width
        bytes[fx + 10] = 0x18  // delay_reverb_send
        // 1 unused (offset 11)
        bytes[fx + 12] = 0xC0.toByte() // reverb_size
        bytes[fx + 13] = 0x70          // reverb_damping
        bytes[fx + 14] = 0x20          // reverb_mod_depth
        bytes[fx + 15] = 0x30          // reverb_mod_freq
        bytes[fx + 16] = 0xAA.toByte() // reverb_width — final byte of the 17-byte block

        val parsed = M8sParser.parse(bytes)

        assertEquals(0x11, parsed.fx.chorusModDepth)
        assertEquals(0x22, parsed.fx.chorusModFreq)
        assertEquals(0x33, parsed.fx.chorusReverbSend)
        assertEquals(0x44, parsed.fx.delayTimeL)
        assertEquals(0x55, parsed.fx.delayTimeR)
        assertEquals(0x66, parsed.fx.delayFeedback)
        assertEquals(0x77, parsed.fx.delayWidth)
        assertEquals(0x18, parsed.fx.delayReverbSend)
        assertEquals(0xC0, parsed.fx.reverbSize)
        assertEquals(0x70, parsed.fx.reverbDamping)
        assertEquals(0x20, parsed.fx.reverbModDepth)
        assertEquals(0x30, parsed.fx.reverbModFreq)
        assertEquals(0xAA, parsed.fx.reverbWidth)
    }

    @Test
    fun `applyTo writes parsed FX into song chorus delay and reverb`() {
        val bytes = v4SongWithFxBlock()
        val fx = 0x1A5C1
        bytes[fx + 0] = 0x10           // chorus_mod_depth
        bytes[fx + 1] = 0x20           // chorus_mod_freq
        bytes[fx + 2] = 0x30           // chorus_reverb_send
        bytes[fx + 6] = 0x38           // delay_time_l
        bytes[fx + 7] = 0x40           // delay_time_r
        bytes[fx + 8] = 0x70           // delay_feedback
        bytes[fx + 9] = 0xFF.toByte()  // delay_width
        bytes[fx + 10] = 0x18          // delay_reverb_send
        bytes[fx + 12] = 0xD0.toByte() // reverb_size
        bytes[fx + 13] = 0x80.toByte() // reverb_damping
        bytes[fx + 14] = 0x18          // reverb_mod_depth
        bytes[fx + 15] = 0x20          // reverb_mod_freq

        val parsed = M8sParser.parse(bytes)
        val song = M8Song()
        // Capture pre-apply HP/LP defaults; they should be preserved.
        val delayHpBefore = song.delay.filterHP
        val delayLpBefore = song.delay.filterLP
        val reverbHpBefore = song.reverb.filterHP
        val reverbLpBefore = song.reverb.filterLP

        M8sParser.applyTo(parsed, song)

        assertEquals(0x10, song.chorus.modDepth)
        assertEquals(0x20, song.chorus.modFreq)
        assertEquals(0x30, song.chorus.reverbSend)

        assertEquals(0x38, song.delay.timeL)
        assertEquals(0x40, song.delay.timeR)
        assertEquals(0x70, song.delay.feedback)
        assertEquals(0xFF, song.delay.width)
        assertEquals(0x18, song.delay.reverbSend)

        assertEquals(0xD0, song.reverb.size)
        assertEquals(0x80, song.reverb.damping)
        assertEquals(0x18, song.reverb.modDepth)
        assertEquals(0x20, song.reverb.modFreq)

        // HP/LP cutoff fields aren't carried in the V4 layout that m8-files
        // knows — leave whatever was on the destination song.
        assertEquals(delayHpBefore, song.delay.filterHP)
        assertEquals(delayLpBefore, song.delay.filterLP)
        assertEquals(reverbHpBefore, song.reverb.filterHP)
        assertEquals(reverbLpBefore, song.reverb.filterLP)
    }

    @Test
    fun `applyTo writes parsed mixer levels and resets stale per-track pan and sends`() {
        val bytes = minimalV4Song()
        val mx = 0xCE
        bytes[mx + 0] = 0xB0.toByte()
        bytes[mx + 2] = 0x40   // track 0 volume
        bytes[mx + 9] = 0x90.toByte()   // track 7 volume
        bytes[mx + 10] = 0x88.toByte()  // chorus
        bytes[mx + 26] = 0x80.toByte()  // dj_filter (centred)
        val parsed = M8sParser.parse(bytes)

        // Seed the destination with non-default pans/sends to verify reset.
        val song = M8Song()
        song.mixer.trackPans[0] = 0x10
        song.mixer.trackChorusSend[3] = 0x77
        song.mixer.trackDelaySend[5] = 0x66
        song.mixer.trackReverbSend[7] = 0x55

        M8sParser.applyTo(parsed, song)

        assertEquals(0xB0, song.mixer.masterVolume)
        assertEquals(0x40, song.mixer.trackVolumes[0])
        assertEquals(0x90, song.mixer.trackVolumes[7])
        assertEquals(0x88, song.mixer.chorusVolume)
        assertEquals(0x80, song.mixer.djFilter)
        // Per-track pan and FX sends should have been reset to neutral
        // because real M8 stores those on the instrument, not the mixer.
        for (t in 0 until 8) {
            assertEquals(0x80, song.mixer.trackPans[t], "trackPans[$t] not neutral")
            assertEquals(0x00, song.mixer.trackChorusSend[t], "chorusSend[$t] not cleared")
            assertEquals(0x00, song.mixer.trackDelaySend[t], "delaySend[$t] not cleared")
            assertEquals(0x00, song.mixer.trackReverbSend[t], "reverbSend[$t] not cleared")
        }
    }

    @Test
    fun `instrument pool fills 128 placeholders when file truncates before pool`() {
        val parsed = M8sParser.parse(minimalV4Song())
        assertEquals(128, parsed.instruments.size)
        assertTrue(
            parsed.instruments.all { it.name == "---" },
            "missing-pool slots should be empty placeholder instruments",
        )
    }

    @Test
    fun `instrument pool parses real V4 song fixture`() {
        val bytes = javaClass.classLoader!!.getResourceAsStream("m8songs/CMDMAPPING_4_0.m8s")!!
            .use { it.readBytes() }

        val parsed = M8sParser.parse(bytes)

        assertEquals(128, parsed.instruments.size)
        // CMDMAPPING_4_0 has a single WavSynth in slot 0 and empty slots elsewhere.
        assertEquals(InstrumentType.WAVSYNTH, parsed.instruments[0].type)
        assertEquals("---", parsed.instruments[1].name, "slot 1 should be empty in this fixture")
        assertTrue(
            parsed.warnings.none { it.contains("Instrument pool", ignoreCase = true) },
            "complete pool should not emit truncation warnings",
        )
    }

    @Test
    fun `applyInstruments copies parsed pool into destination`() {
        val parsed = M8sParser.parse(minimalV4Song())
        val destination = M8Instrument.createDefaults()
        val first = parsed.instruments[0]

        val copied = M8sParser.applyInstruments(parsed.instruments, destination)

        assertEquals(destination.size, copied)
        // The destination slot 0 now references the parsed slot 0 instance.
        assertTrue(destination[0] === first, "instrument copy should share the parsed slot reference")
    }

    @Test
    fun `M8Instrument createDefaults exposes the full 128-slot M8 pool`() {
        val defaults = M8Instrument.createDefaults()
        assertEquals(M8Instrument.SLOT_COUNT, defaults.size)
        assertEquals(128, defaults.size)
        // First 8 are the named Android demo presets.
        assertEquals("LEAD", defaults[0].name)
        assertEquals("FX", defaults[7].name)
        // Remaining slots are empty placeholders so out-of-range references don't fall back.
        assertEquals("---", defaults[8].name)
        assertEquals("---", defaults[127].name)
    }

    private fun minimalV4Song(): ByteArray {
        val bytes = ByteArray(0xBA3E + 256 * 128)
        "M8VERSION".toByteArray(Charsets.US_ASCII).copyInto(bytes, 0)
        bytes[10] = 0x10 // minor 1, patch 0
        bytes[11] = 0x04 // major 4
        ByteBuffer.wrap(bytes, 143, 4).order(ByteOrder.LITTLE_ENDIAN).putFloat(120f)
        "TEST".toByteArray(Charsets.US_ASCII).copyInto(bytes, 148)
        bytes.fill(0xFF.toByte(), 0x2EE, 0x2EE + 2048)
        bytes.fill(0xFF.toByte(), 0xAEE, 0xAEE + 255 * 144)
        bytes.fill(0xFF.toByte(), 0x9A5E, 0x9A5E + 255 * 32)
        return bytes
    }

    /**
     * Larger fixture extending [minimalV4Song] to cover the instrument pool
     * (so FX/scale offsets past 0x13A3E are addressable). The instrument
     * pool itself is left zero-filled; individual tests poke specific bytes.
     */
    private fun v4SongWithFxBlock(): ByteArray {
        val instrumentEnd = 0x13A3E + 128 * M8iParser.BODY_SIZE
        val fxEnd = 0x1A5C1 + 17
        val size = maxOf(instrumentEnd, fxEnd)
        val base = minimalV4Song()
        return base.copyOf(size)
    }
}
