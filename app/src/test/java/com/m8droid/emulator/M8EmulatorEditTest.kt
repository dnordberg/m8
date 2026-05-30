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

    private fun parseSongFixture(path: String): M8sParser.ParsedSong {
        val bytes = javaClass.classLoader!!.getResourceAsStream(path)!!
            .use { it.readBytes() }
        return M8sParser.parse(bytes)
    }

    @Test
    fun `loadParsedSong installs parsed grid chain phrase and instrument data into live emulator`() {
        val parsed = parseSongFixture("m8songs/CMDMAPPING_4_0.m8s")
        val emulator = M8Emulator().apply {
            song.songGrid[0][0] = 0x77
            song.chains[0].rows[0].phrase = 0x66
            song.phrases[0].steps[0].note = 0x55
            currentPhrasePerTrack[0] = 0x66
        }

        val installed = emulator.loadParsedSong(parsed)

        assertEquals(128, installed)
        assertEquals("CMDMAPPING", emulator.song.name)
        assertEquals(133, emulator.song.tempo)
        assertEquals(0x00, emulator.song.songGrid[0][0])
        assertEquals(0xA0, emulator.song.songGrid[0][2])
        assertEquals(0x00, emulator.song.chains[0].rows[0].phrase)
        assertEquals(0, emulator.song.chains[0].rows[0].transpose)
        assertEquals(36, emulator.song.phrases[0].steps[0].note)
        assertEquals(0, emulator.song.phrases[0].steps[0].instrument)
        assertEquals(100, emulator.song.phrases[0].steps[0].volume)
        assertEquals(0x80, emulator.song.phrases[0].steps[0].fx1Cmd)
        assertEquals(0x81, emulator.song.phrases[0].steps[0].fx2Cmd)
        assertEquals(0, emulator.songRow)
        assertEquals(0, emulator.chainRow)
        assertEquals(0, emulator.playRow)
        assertEquals(0, emulator.currentPhrasePerTrack[0])
        assertEquals(M8Song.EMPTY, emulator.currentPhrasePerTrack[1])
        assertEquals(InstrumentType.WAVSYNTH, emulator.instruments[0].type)

        val rowData = emulator.resolveRowDataAt(songRow = 0, chainRow = 0, phraseRow = 0)
        assertEquals(36, rowData[0][0])
        assertEquals(0, rowData[0][1])
        assertEquals(100, rowData[0][2])
        assertEquals(0x80, rowData[0][3])
        assertEquals(0x81, rowData[0][4])
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
    fun `config song name row exposes text editor seam`() {
        val emulator = M8Emulator().apply {
            screen = M8Emulator.SCREEN_CONFIG
            cursorY = 0
        }

        assertEquals(true, emulator.canEditSongNameFromScreen())
        assertEquals(true, emulator.setSongNameFromEditor("  TELEGRAM FIX  "))
        assertEquals("TELEGRAM FIX", emulator.song.name)
        assertEquals(false, emulator.setSongNameFromEditor("TELEGRAM FIX"))

        emulator.cursorY = 1
        assertEquals(false, emulator.canEditSongNameFromScreen())
        assertEquals(false, emulator.setSongNameFromEditor("SHOULD NOT APPLY"))
        assertEquals("TELEGRAM FIX", emulator.song.name)
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

    @Test
    fun `long press display selects phrase note cell and enters edit mode`() {
        val emulator = M8Emulator().apply {
            screen = M8Emulator.SCREEN_PHRASE
            editMode = false
        }

        assertEquals(true, emulator.handleDisplayLongPress(20 + 3 * 28 + 4, 38 + 9 * 12))

        assertEquals(true, emulator.editMode)
        assertEquals(3, emulator.cursorX)
        assertEquals(9, emulator.cursorY)
        assertEquals(0, emulator.phraseEditColumn)
    }

    @Test
    fun `long press ignores non-cell display areas`() {
        val emulator = M8Emulator().apply {
            screen = M8Emulator.SCREEN_SONG
            editMode = false
            cursorX = 4
            cursorY = 7
        }

        assertEquals(false, emulator.handleDisplayLongPress(10, 20))

        assertEquals(false, emulator.editMode)
        assertEquals(4, emulator.cursorX)
        assertEquals(7, emulator.cursorY)
    }

    @Test
    fun `song accepts touch hex digit entry into selected chain cell`() {
        val emulator = M8Emulator().apply {
            screen = M8Emulator.SCREEN_SONG
            song.songGrid[5][2] = M8Song.EMPTY
            song.songGrid[5][3] = 0x44
        }

        emulator.handleDisplayTap(28 + 2 * 26 + 4, 60 + 5 * 10 + 1)
        assertEquals(true, emulator.enterHexDigit(0x0A))
        assertEquals(0x0A, emulator.song.songGrid[5][2])
        assertEquals(0x44, emulator.song.songGrid[5][3])

        assertEquals(true, emulator.enterHexDigit(0x05))
        assertEquals(0xA5, emulator.song.songGrid[5][2])
        assertEquals(0xA5, emulator.selectedChain)
    }

    @Test
    fun `phrase note picker writes selected note cell only`() {
        val emulator = M8Emulator().apply {
            screen = M8Emulator.SCREEN_PHRASE
            song.songGrid[0][2] = 0
            song.chains[0].rows[0].phrase = 4
            resetPlayheadAndResolve()
            cursorX = 2
            cursorY = 6
            phraseEditColumn = 0
            octave = 4
        }
        val selectedTrackStep = emulator.song.phrases[4].steps[6]
        val neighboringTrackStep = emulator.song.phrases[0].steps[6]
        selectedTrackStep.note = M8Song.EMPTY
        selectedTrackStep.instrument = 0x03
        neighboringTrackStep.note = 0x31

        assertEquals(true, emulator.canEnterNoteFromPicker())
        assertEquals(true, emulator.enterNoteFromPicker(7))

        assertEquals(67, selectedTrackStep.note)
        assertEquals(0x03, selectedTrackStep.instrument)
        assertEquals(0x31, neighboringTrackStep.note)
    }

    @Test
    fun `phrase note picker is unavailable outside note cells`() {
        val emulator = M8Emulator().apply {
            screen = M8Emulator.SCREEN_PHRASE
            song.songGrid[0][0] = 0
            song.chains[0].rows[0].phrase = 0
            resetPlayheadAndResolve()
            cursorX = 0
            cursorY = 0
            phraseEditColumn = 1
        }

        assertEquals(false, emulator.canEnterNoteFromPicker())
        assertEquals(false, emulator.enterNoteFromPicker(0))
    }

    @Test
    fun `chain accepts touch hex digit entry into selected phrase cell only`() {
        val emulator = M8Emulator().apply {
            screen = M8Emulator.SCREEN_CHAIN
            selectedChain = 2
            song.chains[2].rows[4].phrase = M8Song.EMPTY
            song.chains[2].rows[4].transpose = 7
        }

        emulator.handleDisplayTap(28, 43 + 4 * 13)
        assertEquals(0, emulator.cursorX)
        assertEquals(true, emulator.enterHexDigit(0x0C))
        assertEquals(0x0C, emulator.song.chains[2].rows[4].phrase)
        assertEquals(7, emulator.song.chains[2].rows[4].transpose)

        assertEquals(true, emulator.enterHexDigit(0x03))
        assertEquals(0xC3, emulator.song.chains[2].rows[4].phrase)
        assertEquals(0xC3, emulator.selectedPhrase)

        emulator.handleDisplayTap(70, 43 + 4 * 13)
        assertEquals(1, emulator.cursorX)
        assertEquals(false, emulator.enterHexDigit(0x09), "transpose column is signed decimal, not a hex cell")
        assertEquals(7, emulator.song.chains[2].rows[4].transpose)
    }

    // Audibility: the scheduler reads from the same M8Song the touch handlers mutate,
    // via emulator.resolveRowDataAt. These tests guard that bridge so an edit can never
    // silently fail to reach the synth.

    @Test
    fun `phrase note picker edit reaches resolved synth row data`() {
        val emulator = M8Emulator().apply {
            screen = M8Emulator.SCREEN_PHRASE
            song.songGrid.forEach { row -> java.util.Arrays.fill(row, M8Song.EMPTY) }
            // Track 2 plays chain 0 → phrase 4; cursor on row 6 of phrase 4.
            song.songGrid[0][2] = 0
            song.chains[0].rows[0].phrase = 4
            song.chains[0].rows[0].transpose = 0
            resetPlayheadAndResolve()
            cursorX = 2
            cursorY = 6
            phraseEditColumn = 0
            octave = 4
        }
        // Picker semitone 7 at octave 4 → MIDI 67.
        assertEquals(true, emulator.enterNoteFromPicker(7))

        val rowData = emulator.resolveRowDataAt(0, 0, 6)
        assertEquals(67, rowData[2][0], "edited note must reach synth row data on the same track")
        assertEquals(0, rowData[0][0], "neighboring track must remain silent")
    }

    @Test
    fun `phrase hex edit on instrument column reaches resolved synth row data`() {
        val emulator = M8Emulator().apply {
            screen = M8Emulator.SCREEN_PHRASE
            song.songGrid[0][3] = 0
            song.chains[0].rows[0].phrase = 2
            resetPlayheadAndResolve()
            cursorX = 3
            cursorY = 4
            phraseEditColumn = 0
            octave = 4
        }
        // Pre-seed a real note so the row produces an audible trigger.
        emulator.song.phrases[2].steps[4].note = 60

        // EDIT+UP on the instrument column changes the instrument index for track 3 row 4.
        emulator.cursorY = 4
        emulator.tap(M8Commands.KEY_RIGHT) // select instrument column
        val before = emulator.song.phrases[2].steps[4].instrument
        emulator.tap(M8Commands.KEY_EDIT)
        emulator.tap(M8Commands.KEY_UP)
        val after = emulator.song.phrases[2].steps[4].instrument
        assertNotEquals(before, after, "instrument edit must mutate the step")

        val rowData = emulator.resolveRowDataAt(0, 0, 4)
        assertEquals(after, rowData[3][1], "edited instrument index must reach resolved synth row data")
        assertEquals(60, rowData[3][0], "note must still resolve audibly")
    }

    @Test
    fun `chain transpose edit is applied to resolved note`() {
        val emulator = M8Emulator().apply {
            screen = M8Emulator.SCREEN_CHAIN
            selectedChain = 1
            song.songGrid[0][0] = 1
            song.chains[1].rows[0].phrase = 5
            song.chains[1].rows[0].transpose = 0
            song.phrases[5].steps[0].note = 60
            resetPlayheadAndResolve()
        }
        val baseline = emulator.resolveRowDataAt(0, 0, 0)
        assertEquals(60, baseline[0][0])

        // Move cursor to transpose column, EDIT+UP once.
        emulator.cursorY = 0
        emulator.cursorX = 1
        emulator.tap(M8Commands.KEY_EDIT)
        emulator.tap(M8Commands.KEY_UP)

        val shifted = emulator.resolveRowDataAt(0, 0, 0)
        assertEquals(61, shifted[0][0], "chain transpose must apply at resolution time, not edit time")
    }

    @Test
    fun `note off and empty notes translate to synth sentinels in resolved row data`() {
        val emulator = M8Emulator().apply {
            song.songGrid[0][0] = 0
            song.chains[0].rows[0].phrase = 0
            resetPlayheadAndResolve()
        }
        emulator.song.phrases[0].steps[0].note = M8Song.NOTE_OFF
        emulator.song.phrases[0].steps[1].note = M8Song.EMPTY
        emulator.song.phrases[0].steps[2].note = 64

        val r0 = emulator.resolveRowDataAt(0, 0, 0)
        val r1 = emulator.resolveRowDataAt(0, 0, 1)
        val r2 = emulator.resolveRowDataAt(0, 0, 2)
        assertEquals(M8Emulator.SYNTH_NOTE_OFF, r0[0][0], "NOTE_OFF must surface as synth-side 0xFF")
        assertEquals(0, r1[0][0], "EMPTY must surface as 0 (continue)")
        assertEquals(64, r2[0][0])
    }

    @Test
    fun `picker returns resolved midi note so callers can audition without recomputing octave math`() {
        val emulator = M8Emulator().apply {
            screen = M8Emulator.SCREEN_PHRASE
            song.songGrid[0][1] = 0
            song.chains[0].rows[0].phrase = 0
            resetPlayheadAndResolve()
            cursorX = 1
            cursorY = 2
            phraseEditColumn = 0
            octave = 5
        }
        // C at octave 5 → 60 + (5-4)*12 + 0 = 72.
        assertEquals(72, emulator.enterNoteFromPickerWithResult(0))
        assertEquals(72, emulator.song.phrases[0].steps[2].note)

        // Out-of-range semitone returns -1 and does not mutate.
        emulator.song.phrases[0].steps[2].note = 50
        assertEquals(-1, emulator.enterNoteFromPickerWithResult(99))
        assertEquals(50, emulator.song.phrases[0].steps[2].note)

        // Wrong column (instrument) returns -1 even with a valid semitone.
        emulator.phraseEditColumn = 1
        assertEquals(-1, emulator.enterNoteFromPickerWithResult(3))
    }

    @Test
    fun `song hex entry on empty track resolves to silent row data`() {
        val emulator = M8Emulator().apply {
            screen = M8Emulator.SCREEN_SONG
            // Leave songGrid[0][4] as M8Song.EMPTY by default to confirm silent track.
            song.songGrid[0][2] = 0
            song.chains[0].rows[0].phrase = 0
            song.phrases[0].steps[0].note = 60
            resetPlayheadAndResolve()
        }
        val rowData = emulator.resolveRowDataAt(0, 0, 0)
        assertEquals(60, rowData[2][0], "active track resolves the seeded note")
        assertEquals(0, rowData[4][0], "track with no chain resolves to a silent row")
        assertEquals(0, rowData[4][2], "and zero volume so the synth treats it as continue")
    }

    @Test
    fun `quick insert clear duplicate transpose and status support phone tracker editing`() {
        val emulator = M8Emulator().apply {
            song.songGrid.forEach { row -> java.util.Arrays.fill(row, M8Song.EMPTY) }
            song.chains.forEach { chain -> chain.rows.forEach { row -> row.phrase = M8Song.EMPTY; row.transpose = 0 } }
            song.phrases.forEach { phrase -> phrase.steps.forEach { step -> step.note = M8Song.EMPTY; step.instrument = M8Song.EMPTY; step.volume = M8Song.EMPTY } }
            resetPlayheadAndResolve()
            screen = M8Emulator.SCREEN_SONG
            cursorX = 0
            cursorY = 0
        }

        assertEquals("SONG 00:T0 --", emulator.trackerEditStatus())
        assertEquals("SONG 00:T0 CHAIN 00", emulator.quickInsertAtSelection())
        assertEquals(0, emulator.song.songGrid[0][0])
        assertEquals("SONG 00:T0 CHAIN 00", emulator.trackerEditStatus())
        assertEquals("SONG ROW 00 DUPED TO 01", emulator.duplicateSelection())
        assertEquals(0, emulator.song.songGrid[1][0])

        emulator.screen = M8Emulator.SCREEN_CHAIN
        emulator.cursorX = 0
        emulator.cursorY = 0
        assertEquals("CHAIN 00:00 -- +00", emulator.trackerEditStatus())
        assertEquals("CHAIN 00:00 PHRASE 00", emulator.quickInsertAtSelection())
        assertEquals(0, emulator.song.chains[0].rows[0].phrase)
        assertEquals("CHAIN 00:00 PHRASE 00 +00", emulator.trackerEditStatus())
        assertEquals("CHAIN 00:00 TSP +01", emulator.transposeSelection(1))
        assertEquals(1, emulator.song.chains[0].rows[0].transpose)
        assertEquals("CHAIN ROW 00 DUPED TO 01", emulator.duplicateSelection())
        assertEquals(0, emulator.song.chains[0].rows[1].phrase)
        assertEquals(1, emulator.song.chains[0].rows[1].transpose)

        emulator.screen = M8Emulator.SCREEN_PHRASE
        emulator.cursorX = 0
        emulator.cursorY = 0
        emulator.phraseEditColumn = 0
        assertEquals("PHRASE 00:00 ---", emulator.trackerEditStatus())
        assertEquals("PHRASE 00:00 NOTE C-4", emulator.quickInsertAtSelection())
        assertEquals(60, emulator.song.phrases[0].steps[0].note)
        assertEquals("PHRASE 00:00 C-4", emulator.trackerEditStatus())
        assertEquals("PHRASE 00:00 NOTE C#4", emulator.transposeSelection(1))
        assertEquals(61, emulator.song.phrases[0].steps[0].note)
        emulator.song.phrases[0].steps[0].fx3Cmd = 7
        emulator.song.phrases[0].steps[0].fx3Val = 64
        assertEquals("PHRASE STEP 00 DUPED TO 01", emulator.duplicateSelection())
        assertEquals(61, emulator.song.phrases[0].steps[1].note)
        assertEquals(7, emulator.song.phrases[0].steps[1].fx3Cmd)
        assertEquals(64, emulator.song.phrases[0].steps[1].fx3Val)
        assertEquals("PHRASE 00:00 CLEARED", emulator.clearSelection())
        assertEquals(M8Song.EMPTY, emulator.song.phrases[0].steps[0].note)
        assertEquals(0, emulator.song.phrases[0].steps[0].fx3Cmd)
        assertEquals(0, emulator.song.phrases[0].steps[0].fx3Val)
    }

    @Test
    fun `phrase fx status exposes command labels and value hints`() {
        val emulator = M8Emulator().apply {
            screen = M8Emulator.SCREEN_PHRASE
            song.songGrid[0][1] = 0
            song.chains[0].rows[0].phrase = 2
            resetPlayheadAndResolve()
            cursorX = 1
            cursorY = 0
        }
        val step = emulator.song.phrases[2].steps[0]
        step.note = 60
        step.fx1Cmd = M8FxEngine.FX_TBL
        step.fx1Val = 0x00
        step.fx2Cmd = M8FxEngine.FX_TIC
        step.fx2Val = 0x01
        step.fx3Cmd = M8FxEngine.FX_PAN
        step.fx3Val = 0x40

        emulator.phraseEditColumn = 3
        assertEquals("PHRASE 02:00 FX1 TBL 00 table automation", emulator.trackerEditStatus())
        emulator.phraseEditColumn = 5
        assertEquals("PHRASE 02:00 FX2 TIC 01 table speed", emulator.trackerEditStatus())
        emulator.phraseEditColumn = 7
        assertEquals("PHRASE 02:00 FX3 PAN 40 pan left", emulator.trackerEditStatus())
    }

    @Test
    fun `fresh tracker loop resolves song chain phrase edits before note entry`() {
        val emulator = M8Emulator().apply {
            song.songGrid.forEach { row -> java.util.Arrays.fill(row, M8Song.EMPTY) }
            song.chains.forEach { chain -> chain.rows.forEach { row -> row.phrase = M8Song.EMPTY; row.transpose = 0 } }
            song.phrases.forEach { phrase -> phrase.steps.forEach { step -> step.note = M8Song.EMPTY; step.instrument = M8Song.EMPTY; step.volume = M8Song.EMPTY } }
            resetPlayheadAndResolve()
            screen = M8Emulator.SCREEN_SONG
            cursorX = 0
            cursorY = 0
        }

        assertEquals(M8Song.EMPTY, emulator.currentPhrasePerTrack[0])

        assertEquals(true, emulator.enterHexDigit(0))
        assertEquals(true, emulator.enterHexDigit(0))
        assertEquals(0, emulator.selectedChain)
        assertEquals(M8Song.EMPTY, emulator.currentPhrasePerTrack[0])

        emulator.screen = M8Emulator.SCREEN_CHAIN
        emulator.cursorX = 0
        emulator.cursorY = 0
        assertEquals(true, emulator.enterHexDigit(0))
        assertEquals(true, emulator.enterHexDigit(0))
        assertEquals(0, emulator.selectedPhrase)
        assertEquals(0, emulator.currentPhrasePerTrack[0])

        emulator.screen = M8Emulator.SCREEN_PHRASE
        emulator.cursorX = 0
        emulator.cursorY = 0
        emulator.phraseEditColumn = 0
        assertEquals(true, emulator.canEnterNoteFromPicker())
        assertEquals(60, emulator.enterNoteFromPickerWithResult(0))
        assertEquals(60, emulator.song.phrases[0].steps[0].note)
    }
}
