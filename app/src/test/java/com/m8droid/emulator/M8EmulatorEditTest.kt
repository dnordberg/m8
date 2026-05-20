package com.m8droid.emulator

import com.m8droid.protocol.M8Commands
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class M8EmulatorEditTest {
    private fun M8Emulator.tap(key: Int) {
        handleKeyState(key)
        handleKeyState(0)
    }

    @Test
    fun `instrument edit changes highlighted shape row`() {
        val emulator = M8Emulator().apply {
            screen = M8Emulator.SCREEN_INSTRUMENT
            selectedInstrument = 0
            cursorX = 0
            cursorY = 3 // first rendered type-specific row: SHAPE
        }
        val inst = emulator.instruments[0]
        val originalShape = inst.wavSynth.shape
        val originalWarp = inst.wavSynth.warp

        emulator.tap(M8Commands.KEY_EDIT)
        emulator.tap(M8Commands.KEY_UP)

        assertNotEquals(originalShape, inst.wavSynth.shape)
        assertEquals(originalWarp, inst.wavSynth.warp)
    }

    @Test
    fun `instrument edit maps rendered shared rows to shared params`() {
        val emulator = M8Emulator().apply {
            screen = M8Emulator.SCREEN_INSTRUMENT
            selectedInstrument = 0
            cursorX = 0
            cursorY = 10 // rendered shared row after type rows + separator: CUTOFF
        }
        val inst = emulator.instruments[0]
        val originalCutoff = inst.filter.cutoff
        val originalResonance = inst.filter.resonance

        emulator.tap(M8Commands.KEY_EDIT)
        emulator.tap(M8Commands.KEY_UP)

        assertEquals(originalCutoff + 1, inst.filter.cutoff)
        assertEquals(originalResonance, inst.filter.resonance)
    }
}
