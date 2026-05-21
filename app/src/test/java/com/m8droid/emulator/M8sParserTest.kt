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
