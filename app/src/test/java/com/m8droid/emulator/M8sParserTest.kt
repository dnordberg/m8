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
        assertTrue(parsed.warnings.any { it.contains("mixer", ignoreCase = true) })
        assertTrue(parsed.warnings.any { it.contains("scale", ignoreCase = true) })
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
}
