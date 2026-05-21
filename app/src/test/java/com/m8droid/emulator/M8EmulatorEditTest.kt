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
    fun `fx edit changes exactly the highlighted rendered row`() {
        fun assertFxRow(row: Int, readTarget: (M8Emulator) -> Int, readNext: ((M8Emulator) -> Int)? = null) {
            val emulator = M8Emulator().apply {
                screen = M8Emulator.SCREEN_FX
                cursorY = row
                song.chorus.modDepth = 0x40
                song.chorus.modFreq = 0x40
                song.chorus.width = 0x40
                song.chorus.reverbSend = 0x40
                song.delay.filterHP = 0x40
                song.delay.filterLP = 0x40
                song.delay.timeL = 0x40
                song.delay.timeR = 0x40
                song.delay.feedback = 0x40
                song.delay.width = 0x40
                song.delay.reverbSend = 0x40
                song.reverb.filterHP = 0x40
                song.reverb.filterLP = 0x40
                song.reverb.size = 0x40
                song.reverb.damping = 0x40
                song.reverb.modDepth = 0x40
                song.reverb.modFreq = 0x40
                song.reverb.width = 0x40
            }
            val originalTarget = readTarget(emulator)
            val originalNext = readNext?.invoke(emulator)

            emulator.tap(M8Commands.KEY_EDIT)
            emulator.tap(M8Commands.KEY_UP)

            assertEquals(originalTarget + 1, readTarget(emulator), "cursorY=$row should edit highlighted row")
            if (readNext != null) {
                assertEquals(originalNext, readNext.invoke(emulator), "cursorY=$row must not edit row underneath")
            }
        }

        assertFxRow(0, { it.song.chorus.modDepth }, { it.song.chorus.modFreq })
        assertFxRow(1, { it.song.chorus.modFreq }, { it.song.chorus.width })
        assertFxRow(2, { it.song.chorus.width }, { it.song.chorus.reverbSend })
        assertFxRow(3, { it.song.chorus.reverbSend }, { it.song.delay.filterHP })

        assertFxRow(6, { it.song.delay.filterHP }, { it.song.delay.filterLP })
        assertFxRow(7, { it.song.delay.filterLP }, { it.song.delay.timeL })
        assertFxRow(8, { it.song.delay.timeL }, { it.song.delay.timeR })
        assertFxRow(9, { it.song.delay.timeR }, { it.song.delay.feedback })
        assertFxRow(10, { it.song.delay.feedback }, { it.song.delay.width })
        assertFxRow(11, { it.song.delay.width }, { it.song.delay.reverbSend })
        assertFxRow(12, { it.song.delay.reverbSend }, { it.song.reverb.filterHP })

        assertFxRow(15, { it.song.reverb.filterHP }, { it.song.reverb.filterLP })
        assertFxRow(16, { it.song.reverb.filterLP }, { it.song.reverb.size })
        assertFxRow(17, { it.song.reverb.size }, { it.song.reverb.damping })
        assertFxRow(18, { it.song.reverb.damping }, { it.song.reverb.modDepth })
        assertFxRow(19, { it.song.reverb.modDepth }, { it.song.reverb.modFreq })
        assertFxRow(20, { it.song.reverb.modFreq }, { it.song.reverb.width })
        assertFxRow(21, { it.song.reverb.width })
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

    @Test
    fun `phrase edit changes note on selected track instead of treating track as field column`() {
        val emulator = M8Emulator().apply {
            screen = M8Emulator.SCREEN_PHRASE
            song.songGrid[0][3] = 0
            song.chains[0].rows[0].phrase = 3
            resetPlayheadAndResolve()
            cursorX = 3 // selected track
            cursorY = 0 // selected row
            octave = 4
        }
        val selectedTrackStep = emulator.song.phrases[3].steps[0]
        val neighboringTrackStep = emulator.song.phrases[0].steps[0]
        selectedTrackStep.note = M8Song.EMPTY
        selectedTrackStep.fx1Cmd = 0x22
        neighboringTrackStep.note = 0x31

        emulator.tap(M8Commands.KEY_EDIT)
        emulator.tap(M8Commands.KEY_UP)

        assertEquals(60, selectedTrackStep.note)
        assertEquals(0x22, selectedTrackStep.fx1Cmd, "track index 3 must not be misused as FX1 command column")
        assertEquals(0x31, neighboringTrackStep.note, "editing track 3 must not mutate track 0")
    }

    @Test
    fun `phrase right arrow selects field columns before moving to next track`() {
        val emulator = M8Emulator().apply {
            screen = M8Emulator.SCREEN_PHRASE
            cursorX = 0
            cursorY = 0
            phraseEditColumn = 0
            song.songGrid[0][0] = 0
            song.chains[0].rows[0].phrase = 0
            resetPlayheadAndResolve()
        }
        val step = emulator.song.phrases[0].steps[0]
        step.instrument = 0x03
        step.volume = 0x40

        emulator.tap(M8Commands.KEY_RIGHT)
        assertEquals(0, emulator.cursorX, "first right arrow should stay on selected track")
        assertEquals(1, emulator.phraseEditColumn, "first right arrow should select instrument column")

        emulator.tap(M8Commands.KEY_EDIT)
        emulator.tap(M8Commands.KEY_UP)
        assertEquals(0x04, step.instrument)
        assertEquals(0x40, step.volume)
    }

    @Test
    fun `song edit changes selected track chain cell only`() {
        val emulator = M8Emulator().apply {
            screen = M8Emulator.SCREEN_SONG
            cursorX = 4
            cursorY = 6
            song.songGrid[6][4] = M8Song.EMPTY
            song.songGrid[6][5] = 0x22
        }

        emulator.tap(M8Commands.KEY_EDIT)
        emulator.tap(M8Commands.KEY_UP)

        assertEquals(0x01, emulator.song.songGrid[6][4])
        assertEquals(0x22, emulator.song.songGrid[6][5])
    }

    @Test
    fun `chain edit changes phrase and transpose columns separately`() {
        val emulator = M8Emulator().apply {
            screen = M8Emulator.SCREEN_CHAIN
            selectedChain = 2
            cursorY = 3
            song.chains[2].rows[3].phrase = M8Song.EMPTY
            song.chains[2].rows[3].transpose = 0
        }
        val row = emulator.song.chains[2].rows[3]

        emulator.cursorX = 0
        emulator.tap(M8Commands.KEY_EDIT)
        emulator.tap(M8Commands.KEY_UP)
        assertEquals(0x01, row.phrase)
        assertEquals(0, row.transpose)

        emulator.cursorX = 1
        emulator.tap(M8Commands.KEY_UP)
        assertEquals(0x01, row.phrase)
        assertEquals(1, row.transpose)
    }

    @Test
    fun `display taps select song chain and phrase cells`() {
        val emulator = M8Emulator().apply { screen = M8Emulator.SCREEN_SONG }
        emulator.handleDisplayTap(28 + 2 * 26 + 4, 60 + 5 * 10 + 1)
        assertEquals(2, emulator.cursorX)
        assertEquals(5, emulator.cursorY)

        emulator.screen = M8Emulator.SCREEN_CHAIN
        emulator.handleDisplayTap(70, 43 + 4 * 13)
        assertEquals(1, emulator.cursorX)
        assertEquals(4, emulator.cursorY)

        emulator.screen = M8Emulator.SCREEN_PHRASE
        emulator.handleDisplayTap(20 + 2 * 28 + 26, 38 + 7 * 12)
        assertEquals(2, emulator.cursorX)
        assertEquals(7, emulator.cursorY)
        assertEquals(1, emulator.phraseEditColumn)
    }
}
