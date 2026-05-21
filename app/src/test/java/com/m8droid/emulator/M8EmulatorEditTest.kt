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

    @Test
    fun `config edit changes highlighted tempo scale octave and key rows`() {
        val emulator = M8Emulator().apply {
            screen = M8Emulator.SCREEN_CONFIG
            cursorY = 1 // TEMPO
        }
        val originalTempo = emulator.song.tempo
        emulator.tap(M8Commands.KEY_EDIT)
        emulator.tap(M8Commands.KEY_UP)
        assertEquals(originalTempo + 1, emulator.song.tempo)

        emulator.cursorY = 3 // SCALE
        val originalScale = emulator.song.activeScale
        emulator.tap(M8Commands.KEY_UP)
        assertEquals(originalScale + 1, emulator.song.activeScale)

        emulator.cursorY = 5 // OCTAVE
        val originalOctave = emulator.octave
        emulator.tap(M8Commands.KEY_UP)
        assertEquals(originalOctave + 1, emulator.octave)

        emulator.cursorY = 6 // KEY
        val originalKey = emulator.song.scales[emulator.song.activeScale].key
        emulator.tap(M8Commands.KEY_DOWN)
        assertEquals((originalKey + 11) % 12, emulator.song.scales[emulator.song.activeScale].key)
    }

    @Test
    fun `fx edit uses same rendered cursor rows as highlighted values`() {
        val emulator = M8Emulator().apply {
            screen = M8Emulator.SCREEN_FX
            cursorY = 6 // DELAY / FILTER HP as rendered
        }
        val originalHp = emulator.song.delay.filterHP
        val originalLp = emulator.song.delay.filterLP

        emulator.tap(M8Commands.KEY_EDIT)
        emulator.tap(M8Commands.KEY_UP)

        assertEquals(originalHp + 1, emulator.song.delay.filterHP)
        assertEquals(originalLp, emulator.song.delay.filterLP)
    }

    @Test
    fun `sampler instrument edit changes highlighted play and loop rows`() {
        val emulator = M8Emulator().apply {
            screen = M8Emulator.SCREEN_INSTRUMENT
            selectedInstrument = 0
            instruments[0].type = InstrumentType.SAMPLER
            cursorY = 4 // PLAY row: TYPE, NAME, separator, SAMPLE, PLAY
        }
        val inst = emulator.instruments[0]
        val originalPlayMode = inst.sampler.playMode
        val originalLoopStart = inst.sampler.loopStart

        emulator.tap(M8Commands.KEY_EDIT)
        emulator.tap(M8Commands.KEY_UP)
        assertEquals(originalPlayMode + 1, inst.sampler.playMode)

        emulator.cursorY = 6 // LOOP ST row
        emulator.tap(M8Commands.KEY_UP)
        assertEquals(originalLoopStart + 1, inst.sampler.loopStart)
    }
}
