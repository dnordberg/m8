package com.m8droid.emulator

import com.m8droid.protocol.M8Commands
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Virtual M8 tracker emulator — generates SLIP-encoded draw commands
 * identical to what a real Teensy M8 headless device sends.
 *
 * Renders all 8 M8 tracker screens with editing support,
 * backed by the M8Song/M8Instrument data model.
 */
class M8Emulator {

    // --- Protocol constants ---
    companion object {
        const val WIDTH = 320
        const val HEIGHT = 240
        const val RIGHT_PANEL_X = 248
        const val FONT_W = 8
        const val FONT_H = 10

        const val SLIP_END: Byte = 0xC0.toByte()
        const val SLIP_ESC: Byte = 0xDB.toByte()
        const val SLIP_ESC_END: Byte = 0xDC.toByte()
        const val SLIP_ESC_ESC: Byte = 0xDD.toByte()

        const val DRAW_RECT: Byte = 0xFE.toByte()
        const val DRAW_CHAR: Byte = 0xFD.toByte()
        const val DRAW_WAVEFORM: Byte = 0xFC.toByte()
        const val SYSTEM_INFO: Byte = 0xFF.toByte()

        const val FPS = 30

        // Screen indices
        const val SCREEN_SONG = 0
        const val SCREEN_CHAIN = 1
        const val SCREEN_PHRASE = 2
        const val SCREEN_INSTRUMENT = 3
        const val SCREEN_TABLE = 4
        const val SCREEN_MIXER = 5
        const val SCREEN_FX = 6
        const val SCREEN_CONFIG = 7
        const val SCREEN_PROJECT = 8

        // Screens shown in the top tab header / OPT+EDIT cycle. PROJECT is
        // reachable only via Shift+Up and is intentionally kept out of the tab row.
        val SCREEN_NAMES = arrayOf("SONG", "CHAIN", "PHRASE", "INSTR", "TABLE", "MIXER", "FX", "CONFIG")

        // Synth-side note-off sentinel mirrored here so the emulator can produce
        // row data without taking a hard dependency on M8Synth in tests.
        const val SYNTH_NOTE_OFF = 0xFF
    }

    // --- Data model ---
    val song = M8Song()
    val instruments = M8Instrument.createDefaults()

    // --- Tracker state ---
    var screen = SCREEN_SONG
    var cursorX = 0   // track/column (meaning varies per screen)
    var cursorY = 0   // row
    var playing = true
    var playRow = 0   // current play row within phrase
    var bpm: Int
        get() = song.tempo
        set(value) { song.tempo = value }
    var octave = 4
    var frameCount = 0
    var waveformPhase = 0.0

    // Song position tracking
    var songRow = 0           // current row in song grid
    var chainRow = 0          // current row within chain
    var songPosition = 0      // legacy compat: increments on advancePattern

    /** Legacy compatibility: returns songPosition as pattern index */
    val currentPattern: Int get() = songPosition

    // Per-track current phrase indices (resolved from song grid -> chain -> phrase)
    val currentPhrasePerTrack = IntArray(8) { 0 }

    // Selected items for sub-screens
    var selectedChain = 0
    var selectedPhrase = 0
    var selectedInstrument = 0
    var selectedTable = 0
    var phraseEditColumn = 0 // 0=note,1=inst,2=vol,3/4=FX1 cmd/val,5/6=FX2,7/8=FX3

    // Editing
    var editMode = false
    private var pendingHexEntryTarget: HexEntryTarget? = null
    private var pendingHexHighNibble: Int? = null

    private data class HexEntryTarget(
        val screen: Int,
        val selectedChain: Int,
        val cursorX: Int,
        val cursorY: Int,
    )

    // External waveform data from synth (set by ViewModel)
    var liveWaveformData: ByteArray? = null
    var liveTrackLevels: DoubleArray? = null
    var liveMasterLevelL = 0.0
    var liveMasterLevelR = 0.0

    // MIDI activity indicator — ViewModel toggles this based on a decay timer.
    @Volatile var midiActive: Boolean = false

    // --- Colors (matching real M8 default theme) ---
    private val cBg = intArrayOf(0, 0, 0)
    private val cText = intArrayOf(100, 160, 220)
    private val cTextBright = intArrayOf(140, 200, 255)
    private val cTextDim = intArrayOf(50, 80, 120)
    private val cCursor = intArrayOf(180, 230, 255)
    private val cCursorBg = intArrayOf(40, 70, 130)
    private val cHeader = intArrayOf(100, 160, 220)
    private val cHeaderHi = intArrayOf(180, 220, 255)
    private val cPlayArrow = intArrayOf(0, 220, 0)
    private val cWaveform = intArrayOf(80, 180, 220)
    private val cNotePanel = intArrayOf(120, 180, 255)
    private val cNotePanelDim = intArrayOf(60, 90, 140)
    private val cKeyWhite = intArrayOf(60, 80, 140)
    private val cKeyBlack = intArrayOf(20, 30, 60)
    private val cKeySep = intArrayOf(30, 40, 80)

    private var lastKeys = 0
    private var shiftChordActive = false

    init {
        song.loadDemoSong()
        resolveCurrentPhrases()
    }

    /**
     * Map taps on the 320×240 rendered M8 display to the exact rendered field.
     * Every editable screen owns its coordinate mapping here so Compose never
     * has to guess which emulator value a phone touch represents.
     *
     * @return true when the tap selected an editable field.
     */
    fun handleDisplayTap(m8X: Int, m8Y: Int): Boolean {
        val selected = when (screen) {
            SCREEN_SONG -> selectSongCell(m8X, m8Y)
            SCREEN_CHAIN -> selectChainCell(m8X, m8Y)
            SCREEN_PHRASE -> selectPhraseCell(m8X, m8Y)
            SCREEN_INSTRUMENT -> selectInstrumentField(m8Y)
            SCREEN_TABLE -> selectTableCell(m8X, m8Y)
            SCREEN_MIXER -> selectMixerField(m8X, m8Y)
            SCREEN_FX -> selectFxField(m8Y)
            SCREEN_CONFIG -> selectConfigField(m8Y)
            else -> false
        }
        if (selected) {
            resetPendingDirectEntry()
            // Phone editing is direct: tapping a field selects it and opens the
            // contextual editor. Hardware/gamepad users retain EDIT-key semantics.
            editMode = true
        }
        return selected
    }

    fun handleDisplayLongPress(m8X: Int, m8Y: Int): Boolean {
        if (!handleDisplayTap(m8X, m8Y)) return false
        editMode = true
        return true
    }

    private fun selectSongCell(m8X: Int, m8Y: Int): Boolean {
        val trackStartX = 28
        val trackSpacing = 26
        val rowStartY = 60
        if (m8X !in trackStartX until (trackStartX + trackSpacing * 8) || m8Y < rowStartY) return false
        val viewOffset = max(0, cursorY - 8)
        cursorX = ((m8X - trackStartX) / trackSpacing).coerceIn(0, 7)
        cursorY = (viewOffset + ((m8Y - rowStartY) / FONT_H)).coerceIn(0, 255)
        val chainIdx = song.songGrid[cursorY][cursorX]
        if (chainIdx != M8Song.EMPTY) selectedChain = chainIdx
        return true
    }

    private fun selectChainCell(m8X: Int, m8Y: Int): Boolean {
        val rowStartY = 16 + 14 + (FONT_H + 3)
        val rowH = FONT_H + 3
        if (m8X !in 20..95 || m8Y !in rowStartY until (rowStartY + rowH * 16)) return false
        cursorY = ((m8Y - rowStartY) / rowH).coerceIn(0, 15)
        cursorX = if (m8X >= 58) 1 else 0
        val chainRow = song.chains[selectedChain.coerceIn(0, 254)].rows[cursorY]
        if (chainRow.phrase != M8Song.EMPTY) selectedPhrase = chainRow.phrase
        return true
    }

    private fun selectPhraseCell(m8X: Int, m8Y: Int): Boolean {
        val trackStartX = 20
        val trackW = 28
        val dataStartY = 38
        val rowH = FONT_H + 2
        if (m8X !in trackStartX until (trackStartX + trackW * 8) ||
            m8Y !in dataStartY until (dataStartY + rowH * 16)
        ) return false
        val relX = m8X - trackStartX
        cursorX = (relX / trackW).coerceIn(0, 7)
        cursorY = ((m8Y - dataStartY) / rowH).coerceIn(0, 15)
        // The compact 8-track renderer exposes NOTE plus the final instrument
        // character. VOL/FX fields remain directly selectable from the phone
        // field strip while edit mode is active.
        phraseEditColumn = if (relX % trackW >= 24) 1 else 0
        return true
    }

    private fun selectInstrumentField(m8Y: Int): Boolean {
        val rowStartY = 16 + 14
        val rowH = FONT_H + 2
        if (m8Y !in rowStartY until HEIGHT) return false
        val row = (m8Y - rowStartY) / rowH
        val inst = instruments[selectedInstrument.coerceIn(0, instruments.lastIndex)]
        val typeStart = 3
        val sharedStart = typeStart + inst.getTypeParams().size + 1
        val isRendered = row == 0 || row == 1 ||
            row in typeStart until (typeStart + inst.getTypeParams().size) ||
            row in sharedStart until (sharedStart + inst.getSharedParams().size)
        if (!isRendered) return false
        cursorY = row
        cursorX = 0
        return true
    }

    private fun selectTableCell(m8X: Int, m8Y: Int): Boolean {
        val rowH = FONT_H + 3
        val rowStartY = 16 + 14 + rowH
        if (m8Y !in rowStartY until (rowStartY + rowH * 16)) return false
        cursorY = ((m8Y - rowStartY) / rowH).coerceIn(0, 15)
        cursorX = when (m8X) {
            in 24..55 -> 0
            in 56..83 -> 1
            in 84..107 -> 2
            in 108..127 -> 3
            in 128..151 -> 4
            in 152..171 -> 5
            in 172..195 -> 6
            in 196..223 -> 7
            else -> return false
        }
        return true
    }

    private fun selectMixerField(m8X: Int, m8Y: Int): Boolean {
        val firstTrackX = 6
        val trackW = 29
        val meterTop = 20 + 16
        val meterBottom = meterTop + 14 + 80 + 14
        val masterY = 20 + 160
        if (m8X in 4..200 && m8Y in masterY until (masterY + 36)) {
            cursorY = 8
            cursorX = 0
            return true
        }
        if (m8X !in firstTrackX until (firstTrackX + trackW * 8) || m8Y !in meterTop..meterBottom) return false
        cursorY = ((m8X - firstTrackX) / trackW).coerceIn(0, 7)
        cursorX = 0 // the visible value under each meter is track volume
        return true
    }

    private fun selectFxField(m8Y: Int): Boolean {
        val renderedRows = linkedMapOf(
            54 to 0, 66 to 1, 78 to 2, 90 to 3,
            126 to 6, 138 to 7, 150 to 8, 162 to 9,
            174 to 10, 186 to 11, 198 to 12,
        )
        val row = renderedRows.entries.firstOrNull { (y, _) -> m8Y in y until (y + FONT_H + 2) }?.value
            ?: return false
        cursorY = row
        cursorX = 0
        return true
    }

    private fun selectConfigField(m8Y: Int): Boolean {
        val rowStartY = 16 + 20
        val rowH = FONT_H + 4
        if (m8Y !in rowStartY until (rowStartY + rowH * 7)) return false
        cursorY = ((m8Y - rowStartY) / rowH).coerceIn(0, 6)
        cursorX = 0
        return true
    }

    private fun resetPendingDirectEntry() {
        pendingHexEntryTarget = null
        pendingHexHighNibble = null
    }

    /**
     * Enter one hex digit into the currently selected rendered hex cell. This is
     * the emulator-core seam used by phone touch controls: the first digit writes
     * 0x0N for immediate visual feedback, the second digit completes 0xNN.
     */
    fun canEnterHexDigit(): Boolean = currentHexEntryTarget() != null

    fun canEditSongNameFromScreen(): Boolean = screen == SCREEN_CONFIG && cursorY == 0

    fun canEditTextFieldFromScreen(): Boolean =
        canEditSongNameFromScreen() || (screen == SCREEN_INSTRUMENT && cursorY == 1)

    fun editableTextFieldLabel(): String = when {
        canEditSongNameFromScreen() -> "SONG NAME"
        screen == SCREEN_INSTRUMENT && cursorY == 1 -> "INSTRUMENT NAME"
        else -> "TEXT"
    }

    fun currentEditableText(): String = when {
        canEditSongNameFromScreen() -> song.name
        screen == SCREEN_INSTRUMENT && cursorY == 1 ->
            instruments[selectedInstrument.coerceIn(0, instruments.lastIndex)].name
        else -> ""
    }

    fun setSongNameFromEditor(name: String): Boolean {
        if (!canEditSongNameFromScreen()) return false
        return setEditableTextFromEditor(name)
    }

    fun setEditableTextFromEditor(value: String): Boolean {
        if (!canEditTextFieldFromScreen()) return false
        val cleaned = value.trim().ifBlank {
            if (canEditSongNameFromScreen()) "NEW SONG" else "INSTRUMENT"
        }.take(64)
        return when {
            canEditSongNameFromScreen() -> {
                if (song.name == cleaned) false else {
                    song.name = cleaned
                    true
                }
            }
            screen == SCREEN_INSTRUMENT && cursorY == 1 -> {
                val instrument = instruments[selectedInstrument.coerceIn(0, instruments.lastIndex)]
                if (instrument.name == cleaned) false else {
                    instrument.name = cleaned
                    true
                }
            }
            else -> false
        }
    }

    fun selectPhraseEditColumn(column: Int): Boolean {
        if (screen != SCREEN_PHRASE || column !in 0..8) return false
        phraseEditColumn = column
        resetPendingDirectEntry()
        return true
    }

    fun selectMixerEditParameter(parameter: Int): Boolean {
        if (screen != SCREEN_MIXER || cursorY !in 0..7 || parameter !in 0..4) return false
        cursorX = parameter
        resetPendingDirectEntry()
        return true
    }

    fun canAdjustSelectedValue(): Boolean = when (screen) {
        SCREEN_SONG -> cursorX in 0..7 && cursorY in 0..255
        SCREEN_CHAIN -> cursorX in 0..1 && cursorY in 0..15
        SCREEN_PHRASE -> currentPhraseStep() != null && phraseEditColumn in 0..8
        SCREEN_TABLE -> cursorX in 0..7 && cursorY in 0..15
        SCREEN_MIXER -> (cursorY in 0..7 && cursorX in 0..4) || cursorY in 8..9
        SCREEN_INSTRUMENT -> {
            val inst = instruments[selectedInstrument.coerceIn(0, instruments.lastIndex)]
            val typeStart = 3
            val sharedStart = typeStart + inst.getTypeParams().size + 1
            cursorY == 0 ||
                cursorY in typeStart until (typeStart + inst.getTypeParams().size) ||
                cursorY in sharedStart until (sharedStart + inst.getSharedParams().size)
        }
        SCREEN_FX -> cursorY in setOf(0, 1, 2, 3, 6, 7, 8, 9, 10, 11, 12, 15, 16, 17, 18, 19, 20, 21)
        SCREEN_CONFIG -> cursorY in 1..6
        else -> false
    }

    /**
     * Adjust the selected field without forcing phone users to synthesize an
     * EDIT+arrow chord. [direction] is -1 or +1; [coarse] uses SHIFT semantics.
     * Edit mode remains active so repeated taps continue editing the same field.
     */
    fun adjustSelectedValue(direction: Int, coarse: Boolean = false): Boolean {
        if (direction !in setOf(-1, 1) || !canAdjustSelectedValue()) return false
        editMode = true
        val arrow = if (direction > 0) M8Commands.KEY_UP else M8Commands.KEY_DOWN
        val keys = arrow or if (coarse) M8Commands.KEY_SHIFT else 0
        handleKeyState(keys)
        handleKeyState(0)
        editMode = true
        return true
    }

    fun enterHexDigit(digit: Int): Boolean {
        if (digit !in 0..0x0F) return false
        val target = currentHexEntryTarget() ?: return false
        val high = if (pendingHexEntryTarget == target) pendingHexHighNibble else null
        val value = if (high == null) digit else ((high shl 4) or digit).coerceAtMost(0xFE)

        when (screen) {
            SCREEN_SONG -> {
                song.songGrid[cursorY.coerceIn(0, 255)][cursorX.coerceIn(0, 7)] = value
                selectedChain = value
                resolveCurrentPhrases()
            }
            SCREEN_CHAIN -> {
                val row = song.chains[selectedChain.coerceIn(0, 254)].rows[cursorY.coerceIn(0, 15)]
                row.phrase = value
                selectedPhrase = value
                resolveCurrentPhrases()
            }
            else -> return false
        }

        pendingHexEntryTarget = if (high == null) target else null
        pendingHexHighNibble = if (high == null) digit else null
        return true
    }

    private fun currentHexEntryTarget(): HexEntryTarget? = when (screen) {
        SCREEN_SONG -> HexEntryTarget(screen, -1, cursorX.coerceIn(0, 7), cursorY.coerceIn(0, 255))
        SCREEN_CHAIN -> if (cursorX == 0) {
            HexEntryTarget(screen, selectedChain.coerceIn(0, 254), cursorX, cursorY.coerceIn(0, 15))
        } else {
            null
        }
        else -> null
    }

    fun canEnterNoteFromPicker(): Boolean = currentPhraseNoteStep() != null

    fun enterNoteFromPicker(semitone: Int): Boolean = enterNoteFromPickerWithResult(semitone) >= 0

    /**
     * Write a picker semitone into the current phrase note cell and return the
     * resulting MIDI note (0–127), or -1 if no editable note cell is selected.
     * Callers that want to audition the note can read the return value instead
     * of recomputing octave/semitone math.
     */
    fun enterNoteFromPickerWithResult(semitone: Int): Int {
        if (semitone !in 0..11) return -1
        val step = currentPhraseNoteStep() ?: return -1
        val midi = (60 + (octave - 4) * 12 + semitone).coerceIn(1, 127)
        step.note = midi
        return midi
    }

    fun quickInsertAtSelection(): String = when (screen) {
        SCREEN_SONG -> {
            val row = cursorY.coerceIn(0, 255)
            val track = cursorX.coerceIn(0, 7)
            song.songGrid[row][track] = 0
            selectedChain = 0
            resolveCurrentPhrases()
            "SONG ${M8Song.hex2(row)}:T$track CHAIN 00"
        }
        SCREEN_CHAIN -> {
            val rowIndex = cursorY.coerceIn(0, 15)
            val chainIndex = selectedChain.coerceIn(0, 254)
            val row = song.chains[chainIndex].rows[rowIndex]
            row.phrase = 0
            selectedPhrase = 0
            resolveCurrentPhrases()
            "CHAIN ${M8Song.hex2(chainIndex)}:${M8Song.hex2(rowIndex)} PHRASE 00"
        }
        SCREEN_PHRASE -> {
            val step = currentPhraseStep() ?: return "NO PHRASE STEP"
            step.note = if (step.note == M8Song.EMPTY || step.note == M8Song.NOTE_OFF) 60 else step.note
            "PHRASE ${M8Song.hex2(currentPhraseIndex())}:${M8Song.hex2(cursorY.coerceIn(0, 15))} NOTE ${trackerNoteName(step.note)}"
        }
        else -> "NO TRACKER CELL"
    }

    fun clearSelection(): String = when (screen) {
        SCREEN_SONG -> {
            val row = cursorY.coerceIn(0, 255)
            val track = cursorX.coerceIn(0, 7)
            song.songGrid[row][track] = M8Song.EMPTY
            resolveCurrentPhrases()
            "SONG ${M8Song.hex2(row)}:T$track CLEARED"
        }
        SCREEN_CHAIN -> {
            val rowIndex = cursorY.coerceIn(0, 15)
            val row = song.chains[selectedChain.coerceIn(0, 254)].rows[rowIndex]
            row.phrase = M8Song.EMPTY
            row.transpose = 0
            resolveCurrentPhrases()
            "CHAIN ${M8Song.hex2(selectedChain.coerceIn(0, 254))}:${M8Song.hex2(rowIndex)} CLEARED"
        }
        SCREEN_PHRASE -> {
            val step = currentPhraseStep() ?: return "NO PHRASE STEP"
            step.note = M8Song.EMPTY
            step.instrument = M8Song.EMPTY
            step.volume = M8Song.EMPTY
            step.fx1Cmd = 0
            step.fx1Val = 0
            step.fx2Cmd = 0
            step.fx2Val = 0
            step.fx3Cmd = 0
            step.fx3Val = 0
            "PHRASE ${M8Song.hex2(currentPhraseIndex())}:${M8Song.hex2(cursorY.coerceIn(0, 15))} CLEARED"
        }
        else -> "NO TRACKER CELL"
    }

    fun duplicateSelection(): String {
        return when (screen) {
        SCREEN_SONG -> {
            val row = cursorY.coerceIn(0, 255)
            val dst = (row + 1).coerceAtMost(255)
            song.songGrid[dst] = song.songGrid[row].copyOf()
            resolveCurrentPhrases()
            "SONG ROW ${M8Song.hex2(row)} DUPED TO ${M8Song.hex2(dst)}"
        }
        SCREEN_CHAIN -> {
            val rowIndex = cursorY.coerceIn(0, 15)
            val dst = (rowIndex + 1).coerceAtMost(15)
            val chain = song.chains[selectedChain.coerceIn(0, 254)]
            chain.rows[dst].phrase = chain.rows[rowIndex].phrase
            chain.rows[dst].transpose = chain.rows[rowIndex].transpose
            resolveCurrentPhrases()
            "CHAIN ROW ${M8Song.hex2(rowIndex)} DUPED TO ${M8Song.hex2(dst)}"
        }
        SCREEN_PHRASE -> {
            val phraseIndex = currentPhraseIndex()
            if (phraseIndex == M8Song.EMPTY || phraseIndex > 254) return "NO PHRASE STEP"
            val row = cursorY.coerceIn(0, 15)
            val dst = (row + 1).coerceAtMost(15)
            val phrase = song.phrases[phraseIndex]
            copyPhraseStep(phrase.steps[row], phrase.steps[dst])
            "PHRASE STEP ${M8Song.hex2(row)} DUPED TO ${M8Song.hex2(dst)}"
        }
        else -> "NO TRACKER CELL"
        }
    }

    fun transposeSelection(delta: Int): String {
        return when (screen) {
        SCREEN_CHAIN -> {
            val rowIndex = cursorY.coerceIn(0, 15)
            val chainIndex = selectedChain.coerceIn(0, 254)
            val row = song.chains[chainIndex].rows[rowIndex]
            row.transpose = (row.transpose + delta).coerceIn(-128, 127)
            "CHAIN ${M8Song.hex2(chainIndex)}:${M8Song.hex2(rowIndex)} TSP ${signed3(row.transpose)}"
        }
        SCREEN_PHRASE -> {
            val step = currentPhraseStep() ?: return "NO PHRASE STEP"
            if (step.note == M8Song.EMPTY || step.note == M8Song.NOTE_OFF) return "NO NOTE"
            step.note = (step.note + delta).coerceIn(1, 127)
            "PHRASE ${M8Song.hex2(currentPhraseIndex())}:${M8Song.hex2(cursorY.coerceIn(0, 15))} NOTE ${trackerNoteName(step.note)}"
        }
        else -> "NO TRANSPOSE TARGET"
        }
    }

    fun trackerEditStatus(): String = when (screen) {
        SCREEN_SONG -> {
            val row = cursorY.coerceIn(0, 255)
            val track = cursorX.coerceIn(0, 7)
            val chain = song.songGrid[row][track]
            val value = if (chain == M8Song.EMPTY) "--" else "CHAIN ${M8Song.hex2(chain)}"
            "SONG ${M8Song.hex2(row)}:T$track $value"
        }
        SCREEN_CHAIN -> {
            val rowIndex = cursorY.coerceIn(0, 15)
            val chainIndex = selectedChain.coerceIn(0, 254)
            val row = song.chains[chainIndex].rows[rowIndex]
            val phrase = if (row.phrase == M8Song.EMPTY) "--" else "PHRASE ${M8Song.hex2(row.phrase)}"
            "CHAIN ${M8Song.hex2(chainIndex)}:${M8Song.hex2(rowIndex)} $phrase ${signed3(row.transpose)}"
        }
        SCREEN_PHRASE -> {
            val phraseIndex = currentPhraseIndex()
            val step = currentPhraseStep()
            val row = M8Song.hex2(cursorY.coerceIn(0, 15))
            val column = phraseEditColumn.coerceIn(0, 8)
            if (step == null) {
                "PHRASE ${M8Song.hex2(phraseIndex)}:$row ---"
            } else if (column in 3..8) {
                val slot = when (column) {
                    3, 4 -> 1
                    5, 6 -> 2
                    else -> 3
                }
                val (cmd, value) = phraseFxPair(step, slot)
                val name = M8FxEngine.fxName(cmd)
                val help = fxValueHint(cmd, value)
                "PHRASE ${M8Song.hex2(phraseIndex)}:$row FX$slot $name ${M8Song.hex2(value)} $help"
            } else {
                val note = trackerNoteName(step.note)
                "PHRASE ${M8Song.hex2(phraseIndex)}:$row $note"
            }
        }
        else -> "NO TRACKER CELL"
    }

    private fun currentPhraseNoteStep(): PhraseStep? {
        if (screen != SCREEN_PHRASE || phraseEditColumn != 0) return null
        val track = cursorX.coerceIn(0, 7)
        val phraseIdx = currentPhrasePerTrack.getOrElse(track) { M8Song.EMPTY }
        if (phraseIdx == M8Song.EMPTY || phraseIdx > 254) return null
        return song.phrases[phraseIdx].steps[cursorY.coerceIn(0, 15)]
    }

    private fun currentPhraseIndex(): Int {
        val track = cursorX.coerceIn(0, 7)
        return currentPhrasePerTrack.getOrElse(track) { M8Song.EMPTY }
    }

    private fun currentPhraseStep(): PhraseStep? {
        val phraseIdx = currentPhraseIndex()
        if (phraseIdx == M8Song.EMPTY || phraseIdx > 254) return null
        return song.phrases[phraseIdx].steps[cursorY.coerceIn(0, 15)]
    }

    private fun copyPhraseStep(source: PhraseStep, target: PhraseStep) {
        target.note = source.note
        target.instrument = source.instrument
        target.volume = source.volume
        target.fx1Cmd = source.fx1Cmd
        target.fx1Val = source.fx1Val
        target.fx2Cmd = source.fx2Cmd
        target.fx2Val = source.fx2Val
        target.fx3Cmd = source.fx3Cmd
        target.fx3Val = source.fx3Val
    }

    private fun signed3(value: Int): String = String.format("%+03d", value)

    private fun phraseFxPair(step: PhraseStep, slot: Int): Pair<Int, Int> = when (slot) {
        1 -> step.fx1Cmd to step.fx1Val
        2 -> step.fx2Cmd to step.fx2Val
        else -> step.fx3Cmd to step.fx3Val
    }

    private fun fxValueHint(cmd: Int, value: Int): String = when (cmd) {
        M8FxEngine.FX_TBL -> "table automation"
        M8FxEngine.FX_TIC -> "table speed"
        M8FxEngine.FX_PAN -> if (value < 0x70) "pan left" else if (value > 0x90) "pan right" else "pan center"
        M8FxEngine.FX_AMP -> "amp level"
        M8FxEngine.FX_VOL -> "volume level"
        M8FxEngine.FX_SDL -> "delay send"
        M8FxEngine.FX_RET -> "retrigger ramp"
        M8FxEngine.FX_PSL -> "pitch slide"
        M8FxEngine.FX_PBN -> if (value < 0x80) "bend down" else if (value > 0x80) "bend up" else "bend center"
        M8FxEngine.FX_KIL -> "kill tick"
        M8FxEngine.FX_DEL -> "delay note"
        M8FxEngine.FX_ARP -> "arp semitones"
        M8FxEngine.FX_PVB, M8FxEngine.FX_PVX -> "vibrato"
        M8FxEngine.FX_CUT -> "filter cutoff"
        M8FxEngine.FX_RES -> "resonance"
        M8FxEngine.FX_NONE -> "no command"
        else -> "value ${M8Song.hex2(value)}"
    }

    private fun trackerNoteName(note: Int): String {
        if (note == M8Song.EMPTY) return "---"
        if (note == M8Song.NOTE_OFF) return "OFF"
        val names = arrayOf("C-", "C#", "D-", "D#", "E-", "F-", "F#", "G-", "G#", "A-", "A#", "B-")
        val semitone = note % 12
        val octave = (note / 12) - 1
        return "${names[semitone]}$octave"
    }

    /**
     * Rewind the sequencer to row 0 and resolve phrase references from
     * the current song grid. Call after replacing song data in place.
     */
    fun resetPlayheadAndResolve() {
        songRow = 0
        chainRow = 0
        playRow = 0
        songPosition = 0
        resolveCurrentPhrases()
    }

    // --- Resolve which phrases are active per track based on song position ---

    private fun resolveCurrentPhrases() {
        for (t in 0 until 8) {
            val chainIdx = song.songGrid[songRow][t]
            if (chainIdx == M8Song.EMPTY) {
                currentPhrasePerTrack[t] = M8Song.EMPTY
            } else {
                val chain = song.chains[chainIdx.coerceIn(0, 254)]
                val row = chain.rows[chainRow.coerceIn(0, 15)]
                currentPhrasePerTrack[t] = row.phrase
            }
        }
    }

    /**
     * Install a parsed .m8s song into this live emulator instance.
     *
     * The emulator owns a stable [song] object used by UI/rendering/audio paths,
     * so imports mutate that object in place, install the parsed instrument pool,
     * and rewind phrase resolution to row 0. Keeping this seam here lets tests
     * verify parser output reaches the same model the sequencer reads.
     *
     * Returns the number of instrument slots copied into [instruments].
     */
    fun loadParsedSong(parsed: M8sParser.ParsedSong): Int {
        val wasPlaying = playing
        playing = false
        M8sParser.applyTo(parsed, song)
        val installed = M8sParser.applyInstruments(parsed.instruments, instruments)
        resetPlayheadAndResolve()
        if (wasPlaying) {
            playing = true
            playRow = 0
        }
        return installed
    }

    // --- Public interface for synth ---

    /**
     * Resolve the current sequencer position (song row → chain row → phrase row)
     * into per-track row data in the format the synth consumes:
     * `intArrayOf(note, instrument, volume, fx1Cmd, fx2Cmd)`.
     *
     * Applies chain transpose and the EMPTY/NOTE_OFF storage-to-synth translation
     * (EMPTY note → 0 "continue", NOTE_OFF → 0xFF, otherwise transposed MIDI note).
     * Falls back to the per-track instrument index when [PhraseStep.instrument] is
     * EMPTY and to 0xCC velocity when [PhraseStep.volume] is EMPTY.
     *
     * Does NOT apply per-step FX side effects — those live in the FX engine and the
     * ViewModel's audio-thread loop. This is the pure read of the model so that
     * touch edits, scheduler triggers, and previews share one resolution path.
     */
    fun resolveRowDataAt(songRow: Int, chainRow: Int, phraseRow: Int): Array<IntArray> {
        val sRow = songRow.coerceIn(0, 255)
        val cRow = chainRow.coerceIn(0, 15)
        val pRow = phraseRow.coerceIn(0, 15)
        return Array(8) { track ->
            val chainIdx = song.songGrid[sRow][track]
            if (chainIdx == M8Song.EMPTY || chainIdx > 254) {
                intArrayOf(0, track, 0, 0, 0)
            } else {
                val chainEntry = song.chains[chainIdx].rows[cRow]
                val phraseIdx = chainEntry.phrase
                if (phraseIdx == M8Song.EMPTY || phraseIdx > 254) {
                    intArrayOf(0, track, 0, 0, 0)
                } else {
                    val step = song.phrases[phraseIdx].steps[pRow]
                    val note = when (step.note) {
                        M8Song.EMPTY -> 0
                        M8Song.NOTE_OFF -> SYNTH_NOTE_OFF
                        else -> (step.note + chainEntry.transpose).coerceIn(1, 127)
                    }
                    val instrument = if (step.instrument == M8Song.EMPTY) track else step.instrument
                    val volume = if (step.volume == M8Song.EMPTY) 0xCC else step.volume
                    intArrayOf(note, instrument, volume, step.fx1Cmd, step.fx2Cmd)
                }
            }
        }
    }

    /**
     * Get the active phrase data for the current play position.
     * Returns Array<Array<IntArray>> where each outer element is a row (unused here -
     * we return current playRow only in the format the synth expects).
     * Format: array[row][track] = intArrayOf(note, instrument, volume, fx, fx2)
     */
    fun getActivePhrase(): Array<Array<IntArray>> {
        return Array(16) { row ->
            Array(8) { track ->
                val phraseIdx = currentPhrasePerTrack[track]
                if (phraseIdx == M8Song.EMPTY || phraseIdx > 254) {
                    intArrayOf(0, track, 0, 0, 0)
                } else {
                    val step = song.phrases[phraseIdx].steps[row]
                    val note = if (step.note == M8Song.EMPTY) 0 else step.note
                    val inst = if (step.instrument == M8Song.EMPTY) track else step.instrument
                    val vol = if (step.volume == M8Song.EMPTY) 0xCC else step.volume
                    val fx = step.fx1Cmd
                    val fx2 = step.fx2Cmd
                    intArrayOf(note, inst, vol, fx, fx2)
                }
            }
        }
    }

    /** Advance to next pattern/chain row in the song */
    fun advancePattern() {
        chainRow++
        // Check if we've exhausted the current chain for track 0 (or any active track)
        var exhausted = true
        for (t in 0 until 8) {
            val chainIdx = song.songGrid[songRow][t]
            if (chainIdx != M8Song.EMPTY && chainIdx <= 254) {
                val chain = song.chains[chainIdx]
                if (chainRow < 16 && chain.rows[chainRow].phrase != M8Song.EMPTY) {
                    exhausted = false
                    break
                }
            }
        }
        if (exhausted) {
            chainRow = 0
            songRow++
            // Find next non-empty song row (or wrap)
            var tries = 0
            while (tries < 256) {
                if (songRow >= 256) songRow = 0
                var hasContent = false
                for (t in 0 until 8) {
                    if (song.songGrid[songRow][t] != M8Song.EMPTY) {
                        hasContent = true
                        break
                    }
                }
                if (hasContent) break
                songRow++
                tries++
            }
        }
        resolveCurrentPhrases()
        songPosition++
    }
    // --- Clipboard for copy/paste ---
    private var clipboardNote = M8Song.EMPTY
    private var clipboardInst = M8Song.EMPTY
    private var clipboardVol = M8Song.EMPTY
    private var clipboardFx1Cmd = 0; private var clipboardFx1Val = 0
    private var clipboardFx2Cmd = 0; private var clipboardFx2Val = 0
    private var clipboardFx3Cmd = 0; private var clipboardFx3Val = 0
    private var clipboardChainPhrase = M8Song.EMPTY
    private var clipboardChainTranspose = 0
    private var clipboardSongChain = M8Song.EMPTY
    // Selection range for OPT+arrow
    private var selectionActive = false
    private var selStartY = 0
    private var selEndY = 0

    // --- Key handling ---

    fun handleKeyState(keys: Int) {
        val prevKeys = lastKeys
        val pressed = keys and prevKeys.inv()
        val released = prevKeys and keys.inv()
        val held = keys
        lastKeys = keys

        val shiftHeld = held and M8Commands.KEY_SHIFT != 0
        val optHeld = held and M8Commands.KEY_OPTION != 0
        val editHeld = held and M8Commands.KEY_EDIT != 0
        val arrowPressed = pressed and (M8Commands.KEY_UP or M8Commands.KEY_DOWN or
            M8Commands.KEY_LEFT or M8Commands.KEY_RIGHT) != 0

        // ======== SHIFT+Arrow: screen navigation ========
        if (shiftHeld && arrowPressed && !editMode) {
            shiftChordActive = true
            navigateScreenGrid(pressed)
            return
        }

        // ======== OPT+EDIT combos (cut/paste) ========
        if (optHeld && pressed and M8Commands.KEY_EDIT != 0) {
            shiftChordActive = true
            when (screen) {
                SCREEN_PHRASE -> {
                    val phraseIdx = currentPhrasePerTrack.getOrElse(cursorX) { M8Song.EMPTY }
                    if (phraseIdx != M8Song.EMPTY && phraseIdx <= 254) {
                        val step = song.phrases[phraseIdx].steps[cursorY]
                        clipboardNote = step.note; clipboardInst = step.instrument
                        clipboardVol = step.volume
                        clipboardFx1Cmd = step.fx1Cmd; clipboardFx1Val = step.fx1Val
                        clipboardFx2Cmd = step.fx2Cmd; clipboardFx2Val = step.fx2Val
                        clipboardFx3Cmd = step.fx3Cmd; clipboardFx3Val = step.fx3Val
                        step.note = M8Song.EMPTY; step.instrument = M8Song.EMPTY
                        step.volume = M8Song.EMPTY; step.fx1Cmd = 0; step.fx1Val = 0
                        step.fx2Cmd = 0; step.fx2Val = 0; step.fx3Cmd = 0; step.fx3Val = 0
                    }
                }
                SCREEN_CHAIN -> {
                    val chain = song.chains[selectedChain.coerceIn(0, 254)]
                    val row = chain.rows[cursorY]
                    clipboardChainPhrase = row.phrase; clipboardChainTranspose = row.transpose
                    row.phrase = M8Song.EMPTY; row.transpose = 0
                }
                SCREEN_SONG -> {
                    clipboardSongChain = song.songGrid[cursorY][cursorX]
                    song.songGrid[cursorY][cursorX] = M8Song.EMPTY
                }
            }
            return
        }

        // ======== OPT+arrow: selection ========
        if (optHeld && arrowPressed) {
            shiftChordActive = true
            if (!selectionActive) {
                selectionActive = true
                selStartY = cursorY
                selEndY = cursorY
            }
            if (pressed and M8Commands.KEY_DOWN != 0) selEndY = min(15, selEndY + 1)
            if (pressed and M8Commands.KEY_UP != 0) selEndY = max(0, selEndY - 1)
            cursorY = selEndY
            return
        }

        // ======== EDIT key behavior ========
        if (pressed and M8Commands.KEY_EDIT != 0) {
            if (optHeld) {
                // Handled above
            } else if (selectionActive) {
                // EDIT while selection active = copy selection
                selectionActive = false
                when (screen) {
                    SCREEN_PHRASE -> {
                        val phraseIdx = currentPhrasePerTrack.getOrElse(cursorX) { M8Song.EMPTY }
                        if (phraseIdx != M8Song.EMPTY && phraseIdx <= 254) {
                            val step = song.phrases[phraseIdx].steps[cursorY]
                            clipboardNote = step.note; clipboardInst = step.instrument
                            clipboardVol = step.volume
                            clipboardFx1Cmd = step.fx1Cmd; clipboardFx1Val = step.fx1Val
                            clipboardFx2Cmd = step.fx2Cmd; clipboardFx2Val = step.fx2Val
                            clipboardFx3Cmd = step.fx3Cmd; clipboardFx3Val = step.fx3Val
                        }
                    }
                    SCREEN_CHAIN -> {
                        val chain = song.chains[selectedChain.coerceIn(0, 254)]
                        val row = chain.rows[cursorY]
                        clipboardChainPhrase = row.phrase; clipboardChainTranspose = row.transpose
                    }
                    SCREEN_SONG -> {
                        clipboardSongChain = song.songGrid[cursorY][cursorX]
                    }
                }
            } else if (editMode) {
                editMode = false
            } else {
                editMode = true
            }
        }

        // ======== OPT pressed alone: paste ========
        if (pressed and M8Commands.KEY_OPTION != 0 && !shiftHeld && !editHeld && !arrowPressed) {
            when (screen) {
                SCREEN_PHRASE -> {
                    val phraseIdx = currentPhrasePerTrack.getOrElse(cursorX) { M8Song.EMPTY }
                    if (phraseIdx != M8Song.EMPTY && phraseIdx <= 254) {
                        val step = song.phrases[phraseIdx].steps[cursorY]
                        if (clipboardNote != M8Song.EMPTY || clipboardFx1Cmd != 0) {
                            step.note = clipboardNote; step.instrument = clipboardInst
                            step.volume = clipboardVol
                            step.fx1Cmd = clipboardFx1Cmd; step.fx1Val = clipboardFx1Val
                            step.fx2Cmd = clipboardFx2Cmd; step.fx2Val = clipboardFx2Val
                            step.fx3Cmd = clipboardFx3Cmd; step.fx3Val = clipboardFx3Val
                        }
                    }
                }
                SCREEN_CHAIN -> {
                    val chain = song.chains[selectedChain.coerceIn(0, 254)]
                    val row = chain.rows[cursorY]
                    row.phrase = clipboardChainPhrase; row.transpose = clipboardChainTranspose
                }
                SCREEN_SONG -> {
                    song.songGrid[cursorY][cursorX] = clipboardSongChain
                }
            }
        }

        // ======== Edit mode: value changes with arrows ========
        if (editMode && arrowPressed) {
            handleEditModeArrows(pressed, shiftHeld)
            return
        }

        // ======== Normal cursor movement ========
        if (pressed and M8Commands.KEY_UP != 0) {
            cursorY = max(0, cursorY - 1)
            normalizeCursorForScreen()
        }
        if (pressed and M8Commands.KEY_DOWN != 0) {
            val maxY = when (screen) {
                SCREEN_FX -> 21
                SCREEN_CONFIG -> 6
                SCREEN_MIXER -> 9
                else -> 15
            }
            cursorY = min(maxY, cursorY + 1)
            normalizeCursorForScreen()
        }
        if (pressed and M8Commands.KEY_LEFT != 0) {
            if (screen == SCREEN_PHRASE) {
                if (phraseEditColumn > 0) {
                    phraseEditColumn--
                } else {
                    cursorX = max(0, cursorX - 1)
                }
            } else {
                cursorX = max(0, cursorX - 1)
            }
        }
        if (pressed and M8Commands.KEY_RIGHT != 0) {
            if (screen == SCREEN_PHRASE) {
                if (phraseEditColumn < 8) {
                    phraseEditColumn++
                } else {
                    cursorX = min(7, cursorX + 1)
                    phraseEditColumn = 0
                }
            } else {
                val maxX = when (screen) {
                    SCREEN_SONG -> 7
                    SCREEN_CHAIN -> 1
                    SCREEN_TABLE -> 7
                    SCREEN_MIXER -> 4
                    SCREEN_INSTRUMENT -> 1
                    SCREEN_FX -> 1
                    SCREEN_CONFIG -> 1
                    SCREEN_PROJECT -> 1
                    else -> 7
                }
                cursorX = min(maxX, cursorX + 1)
            }
        }

        // ======== Play/Stop ========
        if (pressed and M8Commands.KEY_PLAY != 0) {
            if (shiftHeld) {
                playing = true; playRow = cursorY
            } else {
                playing = !playing
                if (playing) playRow = 0
            }
        }

        // ======== Shift tap = octave bump ========
        if (pressed and M8Commands.KEY_SHIFT != 0) shiftChordActive = false
        if (released and M8Commands.KEY_SHIFT != 0 && !shiftChordActive && !editMode) {
            octave = (octave % 8) + 1
        }

        // ======== Update selected items ========
        if (screen == SCREEN_SONG) {
            val chainIdx = song.songGrid[cursorY][cursorX]
            if (chainIdx != M8Song.EMPTY) selectedChain = chainIdx
        }
        if (screen == SCREEN_CHAIN) {
            val row = song.chains[selectedChain.coerceIn(0, 254)].rows[cursorY]
            if (row.phrase != M8Song.EMPTY) selectedPhrase = row.phrase
        }
        if (arrowPressed && !optHeld) selectionActive = false
    }

    /** Handle arrow keys in edit mode — increment/decrement values */
    private fun handleEditModeArrows(pressed: Int, shiftHeld: Boolean) {
        when (screen) {
            SCREEN_PHRASE -> {
                val track = cursorX.coerceIn(0, 7)
                val phraseIdx = currentPhrasePerTrack.getOrElse(track) { M8Song.EMPTY }
                if (phraseIdx == M8Song.EMPTY || phraseIdx > 254) return
                val step = song.phrases[phraseIdx].steps[cursorY]
                when (phraseEditColumn) {
                    0 -> { // Note column
                        val delta = when {
                            pressed and M8Commands.KEY_UP != 0 -> if (shiftHeld) 12 else 1
                            pressed and M8Commands.KEY_DOWN != 0 -> if (shiftHeld) -12 else -1
                            pressed and M8Commands.KEY_RIGHT != 0 -> 12
                            pressed and M8Commands.KEY_LEFT != 0 -> -12
                            else -> 0
                        }
                        if (step.note == M8Song.EMPTY) {
                            step.note = 60 + (octave - 4) * 12
                        } else if (step.note != M8Song.NOTE_OFF) {
                            step.note = (step.note + delta).coerceIn(1, 127)
                        }
                    }
                    1 -> step.instrument = editHex(pressed, shiftHeld, step.instrument).coerceIn(0, 127)
                    2 -> step.volume = editHex(pressed, shiftHeld, step.volume).coerceIn(0, 0xFF)
                    3 -> step.fx1Cmd = editHex(pressed, shiftHeld, step.fx1Cmd).coerceIn(0, 0xFF)
                    4 -> step.fx1Val = editHex(pressed, shiftHeld, step.fx1Val).coerceIn(0, 0xFF)
                    5 -> step.fx2Cmd = editHex(pressed, shiftHeld, step.fx2Cmd).coerceIn(0, 0xFF)
                    6 -> step.fx2Val = editHex(pressed, shiftHeld, step.fx2Val).coerceIn(0, 0xFF)
                    7 -> step.fx3Cmd = editHex(pressed, shiftHeld, step.fx3Cmd).coerceIn(0, 0xFF)
                    8 -> step.fx3Val = editHex(pressed, shiftHeld, step.fx3Val).coerceIn(0, 0xFF)
                }
            }
            SCREEN_CHAIN -> {
                val chain = song.chains[selectedChain.coerceIn(0, 254)]
                val row = chain.rows[cursorY]
                when (cursorX) {
                    0 -> row.phrase = editHex(pressed, shiftHeld, row.phrase).coerceIn(0, 0xFE)
                    1 -> {
                        val delta = when {
                            pressed and M8Commands.KEY_UP != 0 -> 1
                            pressed and M8Commands.KEY_DOWN != 0 -> -1
                            pressed and M8Commands.KEY_RIGHT != 0 -> 12
                            pressed and M8Commands.KEY_LEFT != 0 -> -12
                            else -> 0
                        }
                        row.transpose = (row.transpose + delta).coerceIn(-128, 127)
                    }
                }
            }
            SCREEN_SONG -> {
                val cur = song.songGrid[cursorY][cursorX]
                song.songGrid[cursorY][cursorX] = editHex(pressed, shiftHeld, cur).coerceIn(0, 0xFE)
            }
            SCREEN_TABLE -> {
                val table = song.tables[selectedTable.coerceIn(0, 255)]
                val row = table.rows[cursorY]
                when (cursorX) {
                    0 -> {
                        val delta = when {
                            pressed and M8Commands.KEY_UP != 0 -> 1
                            pressed and M8Commands.KEY_DOWN != 0 -> -1
                            else -> 0
                        }
                        row.transpose = (row.transpose + delta).coerceIn(-128, 127)
                    }
                    1 -> row.volume = editHex(pressed, shiftHeld, row.volume).coerceIn(0, 0xFF)
                    2 -> row.fx1Cmd = editHex(pressed, shiftHeld, row.fx1Cmd).coerceIn(0, 0xFF)
                    3 -> row.fx1Val = editHex(pressed, shiftHeld, row.fx1Val).coerceIn(0, 0xFF)
                    4 -> row.fx2Cmd = editHex(pressed, shiftHeld, row.fx2Cmd).coerceIn(0, 0xFF)
                    5 -> row.fx2Val = editHex(pressed, shiftHeld, row.fx2Val).coerceIn(0, 0xFF)
                    6 -> row.fx3Cmd = editHex(pressed, shiftHeld, row.fx3Cmd).coerceIn(0, 0xFF)
                    7 -> row.fx3Val = editHex(pressed, shiftHeld, row.fx3Val).coerceIn(0, 0xFF)
                }
            }
            SCREEN_MIXER -> {
                val mx = song.mixer
                when {
                    cursorY in 0..7 -> {
                        val t = cursorY
                        when (cursorX) {
                            0 -> mx.trackVolumes[t] = editHex(pressed, shiftHeld, mx.trackVolumes[t]).coerceIn(0, 0xFF)
                            1 -> mx.trackPans[t] = editHex(pressed, shiftHeld, mx.trackPans[t]).coerceIn(0, 0xFF)
                            2 -> mx.trackChorusSend[t] = editHex(pressed, shiftHeld, mx.trackChorusSend[t]).coerceIn(0, 0xFF)
                            3 -> mx.trackDelaySend[t] = editHex(pressed, shiftHeld, mx.trackDelaySend[t]).coerceIn(0, 0xFF)
                            4 -> mx.trackReverbSend[t] = editHex(pressed, shiftHeld, mx.trackReverbSend[t]).coerceIn(0, 0xFF)
                        }
                    }
                    cursorY == 8 -> mx.masterVolume = editHex(pressed, shiftHeld, mx.masterVolume).coerceIn(0, 0xFF)
                    cursorY == 9 -> mx.djFilter = editHex(pressed, shiftHeld, mx.djFilter).coerceIn(0, 0xFF)
                }
            }
            SCREEN_INSTRUMENT -> {
                val inst = instruments[selectedInstrument.coerceIn(0, instruments.lastIndex)]
                editInstrumentRow(pressed, shiftHeld, inst, cursorY)
            }
            SCREEN_FX -> editFxRow(pressed, shiftHeld, cursorY)
            SCREEN_CONFIG -> editConfigRow(pressed, shiftHeld, cursorY)
        }
    }

    private fun normalizeCursorForScreen() {
        if (screen == SCREEN_FX) {
            // FX render maps highlighted params to cursor rows:
            // chorus 0..3, delay 6..12, reverb 15..21. Rows 4/5 and 13/14 are
            // visual section gaps/headers, not editable fields.
            if (cursorY in 4..5) cursorY = if (lastKeys and M8Commands.KEY_UP != 0) 3 else 6
            if (cursorY in 13..14) cursorY = if (lastKeys and M8Commands.KEY_UP != 0) 12 else 15
        }
        if (screen == SCREEN_CONFIG) cursorY = cursorY.coerceIn(0, 6)
    }

    private fun editFxRow(pressed: Int, shiftHeld: Boolean, renderedRow: Int) {
        when (renderedRow) {
            0 -> song.chorus.modDepth = editHex(pressed, shiftHeld, song.chorus.modDepth).coerceIn(0, 0xFF)
            1 -> song.chorus.modFreq = editHex(pressed, shiftHeld, song.chorus.modFreq).coerceIn(0, 0xFF)
            2 -> song.chorus.width = editHex(pressed, shiftHeld, song.chorus.width).coerceIn(0, 0xFF)
            3 -> song.chorus.reverbSend = editHex(pressed, shiftHeld, song.chorus.reverbSend).coerceIn(0, 0xFF)
            6 -> song.delay.filterHP = editHex(pressed, shiftHeld, song.delay.filterHP).coerceIn(0, 0xFF)
            7 -> song.delay.filterLP = editHex(pressed, shiftHeld, song.delay.filterLP).coerceIn(0, 0xFF)
            8 -> song.delay.timeL = editHex(pressed, shiftHeld, song.delay.timeL).coerceIn(0, 0xFF)
            9 -> song.delay.timeR = editHex(pressed, shiftHeld, song.delay.timeR).coerceIn(0, 0xFF)
            10 -> song.delay.feedback = editHex(pressed, shiftHeld, song.delay.feedback).coerceIn(0, 0xFF)
            11 -> song.delay.width = editHex(pressed, shiftHeld, song.delay.width).coerceIn(0, 0xFF)
            12 -> song.delay.reverbSend = editHex(pressed, shiftHeld, song.delay.reverbSend).coerceIn(0, 0xFF)
            15 -> song.reverb.filterHP = editHex(pressed, shiftHeld, song.reverb.filterHP).coerceIn(0, 0xFF)
            16 -> song.reverb.filterLP = editHex(pressed, shiftHeld, song.reverb.filterLP).coerceIn(0, 0xFF)
            17 -> song.reverb.size = editHex(pressed, shiftHeld, song.reverb.size).coerceIn(0, 0xFF)
            18 -> song.reverb.damping = editHex(pressed, shiftHeld, song.reverb.damping).coerceIn(0, 0xFF)
            19 -> song.reverb.modDepth = editHex(pressed, shiftHeld, song.reverb.modDepth).coerceIn(0, 0xFF)
            20 -> song.reverb.modFreq = editHex(pressed, shiftHeld, song.reverb.modFreq).coerceIn(0, 0xFF)
            21 -> song.reverb.width = editHex(pressed, shiftHeld, song.reverb.width).coerceIn(0, 0xFF)
        }
    }

    private fun editConfigRow(pressed: Int, shiftHeld: Boolean, renderedRow: Int) {
        when (renderedRow) {
            1 -> song.tempo = editHex(pressed, shiftHeld, song.tempo).coerceIn(20, 300)
            2 -> {
                val delta = signedDelta(pressed, shiftHeld)
                song.transpose = (song.transpose + delta).coerceIn(-64, 64)
            }
            3 -> song.activeScale = (song.activeScale + signedDelta(pressed, shiftHeld)).coerceIn(0, song.scales.lastIndex)
            4 -> song.quantize = editHex(pressed, shiftHeld, song.quantize).coerceIn(0, 0xFF)
            5 -> octave = (octave + signedDelta(pressed, shiftHeld)).coerceIn(1, 8)
            6 -> {
                val scale = song.scales[song.activeScale]
                scale.key = (scale.key + signedDelta(pressed, shiftHeld)).floorMod(12)
            }
        }
    }

    private fun signedDelta(pressed: Int, shiftHeld: Boolean): Int = when {
        pressed and M8Commands.KEY_UP != 0 -> if (shiftHeld) 16 else 1
        pressed and M8Commands.KEY_DOWN != 0 -> if (shiftHeld) -16 else -1
        pressed and M8Commands.KEY_RIGHT != 0 -> 1
        pressed and M8Commands.KEY_LEFT != 0 -> -1
        else -> 0
    }

    private fun Int.floorMod(mod: Int): Int = ((this % mod) + mod) % mod

    /** Edit a hex byte value: UP/DOWN = ±1 (±16 with shift), LEFT/RIGHT = ±16 */
    private fun editHex(pressed: Int, shiftHeld: Boolean, cur: Int): Int {
        val v = if (cur == M8Song.EMPTY) 0 else cur
        val small = if (shiftHeld) 16 else 1
        val delta = when {
            pressed and M8Commands.KEY_UP != 0 -> small
            pressed and M8Commands.KEY_DOWN != 0 -> -small
            pressed and M8Commands.KEY_RIGHT != 0 -> 0x10
            pressed and M8Commands.KEY_LEFT != 0 -> -0x10
            else -> 0
        }
        return v + delta
    }

    /** Edit the parameter displayed on the highlighted instrument row. */
    private fun editInstrumentRow(pressed: Int, shiftHeld: Boolean, inst: M8Instrument, renderedRow: Int) {
        val delta = when {
            pressed and M8Commands.KEY_UP != 0 -> if (shiftHeld) 16 else 1
            pressed and M8Commands.KEY_DOWN != 0 -> if (shiftHeld) -16 else -1
            pressed and M8Commands.KEY_RIGHT != 0 -> 16
            pressed and M8Commands.KEY_LEFT != 0 -> -16
            else -> 0
        }
        if (delta == 0) return

        if (renderedRow == 0) {
            inst.type = InstrumentType.entries[(inst.type.ordinal + delta).coerceIn(0, InstrumentType.entries.lastIndex)]
            return
        }

        // renderInstrument() draws TYPE at row 0, NAME at row 1, then a blank separator.
        // Type-specific params start on rendered row 3. Shared params start after another
        // separator, so the rendered row must be translated back to the compact param index
        // used by editInstrumentParam(). Without this mapping, the highlighted SHAPE row
        // actually edited WARP (or did nothing), which made instrument edit feel broken.
        val typeParams = inst.getTypeParams()
        val sharedParams = inst.getSharedParams()
        val typeStartRow = 3
        val sharedStartRow = typeStartRow + typeParams.size + 1
        val compactRow = when (renderedRow) {
            in typeStartRow until (typeStartRow + typeParams.size) -> renderedRow - typeStartRow
            in sharedStartRow until (sharedStartRow + sharedParams.size) -> typeParams.size + (renderedRow - sharedStartRow)
            else -> return
        }
        editInstrumentParam(pressed, shiftHeld, inst, compactRow, typeParams.size)
    }

    /** Edit instrument parameter by row index */
    private fun editInstrumentParam(pressed: Int, shiftHeld: Boolean, inst: M8Instrument, row: Int, typeParamCount: Int) {
        val delta = when {
            pressed and M8Commands.KEY_UP != 0 -> if (shiftHeld) 16 else 1
            pressed and M8Commands.KEY_DOWN != 0 -> if (shiftHeld) -16 else -1
            pressed and M8Commands.KEY_RIGHT != 0 -> 16
            pressed and M8Commands.KEY_LEFT != 0 -> -16
            else -> 0
        }
        if (delta == 0) return

        if (row < typeParamCount) {
            when (inst.type) {
                InstrumentType.WAVSYNTH -> when (row) {
                    0 -> inst.wavSynth.shape = WavShape.fromIndex((inst.wavSynth.shape.ordinal + delta).coerceIn(0, WavShape.entries.size - 1))
                    1 -> inst.wavSynth.size = (inst.wavSynth.size + delta).coerceIn(0, 0xFF)
                    2 -> inst.wavSynth.mult = (inst.wavSynth.mult + delta).coerceIn(0, 0xFF)
                    3 -> inst.wavSynth.warp = (inst.wavSynth.warp + delta).coerceIn(0, 0xFF)
                    4 -> inst.wavSynth.mirror = (inst.wavSynth.mirror + delta).coerceIn(0, 0xFF)
                }
                InstrumentType.FM_SYNTH -> when (row) {
                    0 -> inst.fmSynth.algorithm = FmAlgorithm.fromIndex((inst.fmSynth.algorithm.ordinal + delta).coerceIn(0, FmAlgorithm.entries.size - 1))
                    1 -> inst.fmSynth.op1Shape = FmOperatorShape.fromIndex((inst.fmSynth.op1Shape.ordinal + delta).coerceIn(0, FmOperatorShape.entries.size - 1))
                    2 -> inst.fmSynth.op1Ratio = (inst.fmSynth.op1Ratio + delta).coerceIn(0, 0xFF)
                    3 -> inst.fmSynth.op1Level = (inst.fmSynth.op1Level + delta).coerceIn(0, 0xFF)
                    4 -> inst.fmSynth.op1Feedback = (inst.fmSynth.op1Feedback + delta).coerceIn(0, 0xFF)
                    5 -> inst.fmSynth.op2Shape = FmOperatorShape.fromIndex((inst.fmSynth.op2Shape.ordinal + delta).coerceIn(0, FmOperatorShape.entries.size - 1))
                    6 -> inst.fmSynth.op2Ratio = (inst.fmSynth.op2Ratio + delta).coerceIn(0, 0xFF)
                    7 -> inst.fmSynth.op2Level = (inst.fmSynth.op2Level + delta).coerceIn(0, 0xFF)
                    8 -> inst.fmSynth.op2Feedback = (inst.fmSynth.op2Feedback + delta).coerceIn(0, 0xFF)
                    9 -> inst.fmSynth.op3Shape = FmOperatorShape.fromIndex((inst.fmSynth.op3Shape.ordinal + delta).coerceIn(0, FmOperatorShape.entries.size - 1))
                    10 -> inst.fmSynth.op3Ratio = (inst.fmSynth.op3Ratio + delta).coerceIn(0, 0xFF)
                    11 -> inst.fmSynth.op3Level = (inst.fmSynth.op3Level + delta).coerceIn(0, 0xFF)
                    12 -> inst.fmSynth.op3Feedback = (inst.fmSynth.op3Feedback + delta).coerceIn(0, 0xFF)
                    13 -> inst.fmSynth.op4Shape = FmOperatorShape.fromIndex((inst.fmSynth.op4Shape.ordinal + delta).coerceIn(0, FmOperatorShape.entries.size - 1))
                    14 -> inst.fmSynth.op4Ratio = (inst.fmSynth.op4Ratio + delta).coerceIn(0, 0xFF)
                    15 -> inst.fmSynth.op4Level = (inst.fmSynth.op4Level + delta).coerceIn(0, 0xFF)
                    16 -> inst.fmSynth.op4Feedback = (inst.fmSynth.op4Feedback + delta).coerceIn(0, 0xFF)
                }
                InstrumentType.MACROSYNTH -> when (row) {
                    0 -> inst.macroSynth.model = (inst.macroSynth.model + delta).coerceIn(0, MacroSynthParams.MODEL_NAMES.lastIndex)
                    1 -> inst.macroSynth.timbre = (inst.macroSynth.timbre + delta).coerceIn(0, 0xFF)
                    2 -> inst.macroSynth.color = (inst.macroSynth.color + delta).coerceIn(0, 0xFF)
                    3 -> inst.macroSynth.degrade = (inst.macroSynth.degrade + delta).coerceIn(0, 0xFF)
                    4 -> inst.macroSynth.redux = (inst.macroSynth.redux + delta).coerceIn(0, 0xFF)
                }
                InstrumentType.HYPERSYNTH -> when (row) {
                    0 -> inst.hyperSynth.chordBank = (inst.hyperSynth.chordBank + delta).coerceIn(0, HyperSynthParams.CHORD_BANK_NAMES.lastIndex)
                    1 -> inst.hyperSynth.chord = (inst.hyperSynth.chord + delta).coerceIn(0, 0xFF)
                    2 -> inst.hyperSynth.shift = (inst.hyperSynth.shift + delta).coerceIn(0, 0xFF)
                    3 -> inst.hyperSynth.swarm = (inst.hyperSynth.swarm + delta).coerceIn(0, 0xFF)
                    4 -> inst.hyperSynth.width = (inst.hyperSynth.width + delta).coerceIn(0, 0xFF)
                    5 -> inst.hyperSynth.subOsc = (inst.hyperSynth.subOsc + delta).coerceIn(0, 0xFF)
                }
                InstrumentType.SAMPLER -> when (row) {
                    // SAMPLE path/name is not hex-editable from the keypad yet; load picker will own it.
                    1 -> inst.sampler.playMode = (inst.sampler.playMode + signedDelta(pressed, shiftHeld)).coerceIn(0, SamplerParams.PLAY_MODE_NAMES.lastIndex)
                    2 -> inst.sampler.start = (inst.sampler.start + delta).coerceIn(0, 0xFF)
                    3 -> inst.sampler.loopStart = (inst.sampler.loopStart + delta).coerceIn(0, 0xFF)
                    4 -> inst.sampler.length = (inst.sampler.length + delta).coerceIn(0, 0xFF)
                    5 -> inst.sampler.degrade = (inst.sampler.degrade + delta).coerceIn(0, 0xFF)
                    6 -> inst.sampler.detune = (inst.sampler.detune + delta).coerceIn(0, 0xFF)
                }
                InstrumentType.MIDI_OUT -> {}
            }
        } else {
            val sharedRow = row - typeParamCount
            when (sharedRow) {
                0 -> inst.filter.type = FilterType.fromIndex((inst.filter.type.ordinal + delta).coerceIn(0, FilterType.entries.size - 1))
                1 -> inst.filter.cutoff = (inst.filter.cutoff + delta).coerceIn(0, 0xFF)
                2 -> inst.filter.resonance = (inst.filter.resonance + delta).coerceIn(0, 0xFF)
                3 -> inst.amp.amp = (inst.amp.amp + delta).coerceIn(0, 0xFF)
                4 -> inst.amp.limiter = LimiterType.fromIndex((inst.amp.limiter.ordinal + delta).coerceIn(0, LimiterType.entries.size - 1))
                5 -> inst.amp.pan = (inst.amp.pan + delta).coerceIn(0, 0xFF)
                6 -> inst.amp.dry = (inst.amp.dry + delta).coerceIn(0, 0xFF)
                7 -> inst.amp.chorusSend = (inst.amp.chorusSend + delta).coerceIn(0, 0xFF)
                8 -> inst.amp.delaySend = (inst.amp.delaySend + delta).coerceIn(0, 0xFF)
                9 -> inst.amp.reverbSend = (inst.amp.reverbSend + delta).coerceIn(0, 0xFF)
            }
        }
    }


    // Shift+Arrow navigates the on-screen P / SCPIT / M cluster.
    private val scpitOrder = listOf(
        SCREEN_SONG, SCREEN_CHAIN, SCREEN_PHRASE, SCREEN_INSTRUMENT, SCREEN_TABLE
    )
    private val mRowOrder = listOf(SCREEN_MIXER, SCREEN_FX, SCREEN_CONFIG)
    private var lastScpitScreen = SCREEN_SONG

    private fun navigateScreenGrid(pressed: Int) {
        val prev = screen
        val next = when {
            pressed and M8Commands.KEY_UP != 0 -> when {
                prev in scpitOrder -> SCREEN_PROJECT
                prev in mRowOrder -> lastScpitScreen
                else -> prev
            }
            pressed and M8Commands.KEY_DOWN != 0 -> when {
                prev in scpitOrder -> SCREEN_MIXER
                prev == SCREEN_PROJECT -> lastScpitScreen
                else -> prev
            }
            pressed and M8Commands.KEY_LEFT != 0 -> when {
                prev in scpitOrder -> {
                    val i = scpitOrder.indexOf(prev)
                    if (i == 0) mRowOrder.last() else scpitOrder[i - 1]
                }
                prev in mRowOrder -> {
                    val i = mRowOrder.indexOf(prev)
                    if (i == 0) scpitOrder.last() else mRowOrder[i - 1]
                }
                else -> prev
            }
            pressed and M8Commands.KEY_RIGHT != 0 -> when {
                prev in scpitOrder -> {
                    val i = scpitOrder.indexOf(prev)
                    if (i == scpitOrder.size - 1) mRowOrder.first() else scpitOrder[i + 1]
                }
                prev in mRowOrder -> {
                    val i = mRowOrder.indexOf(prev)
                    if (i == mRowOrder.size - 1) scpitOrder.first() else mRowOrder[i + 1]
                }
                else -> prev
            }
            else -> prev
        }
        if (next in scpitOrder) lastScpitScreen = next
        screen = next
        editMode = false
    }

    // --- Frame rendering ---

    fun renderFrame(): ByteArray {
        val cmds = mutableListOf<ByteArray>()

        // Clear screen
        cmds.add(drawRect(0, 0, WIDTH, HEIGHT, cBg))

        when (screen) {
            SCREEN_SONG -> cmds.addAll(renderSong())
            SCREEN_CHAIN -> {
                cmds.addAll(renderScreenHeader())
                cmds.addAll(renderChain())
            }
            SCREEN_PHRASE -> {
                cmds.addAll(renderScreenHeader())
                cmds.addAll(renderPhrase())
            }
            SCREEN_INSTRUMENT -> {
                cmds.addAll(renderScreenHeader())
                cmds.addAll(renderInstrument())
            }
            SCREEN_TABLE -> {
                cmds.addAll(renderScreenHeader())
                cmds.addAll(renderTable())
            }
            SCREEN_MIXER -> {
                cmds.addAll(renderScreenHeader())
                cmds.addAll(renderMixer())
            }
            SCREEN_FX -> {
                cmds.addAll(renderScreenHeader())
                cmds.addAll(renderFx())
            }
            SCREEN_CONFIG -> {
                cmds.addAll(renderScreenHeader())
                cmds.addAll(renderConfig())
            }
            SCREEN_PROJECT -> cmds.addAll(renderProject())
        }

        // Clear the sidebar column so screen content that strays into it
        // can't bleed through, then draw the global status panel over top.
        // Leave the top header strip (y < 13) alone so the screen tab row
        // (SONG..CONFIG) stays visible.
        cmds.add(drawRect(RIGHT_PANEL_X - 4, 13, WIDTH - (RIGHT_PANEL_X - 4), HEIGHT - 13, cBg))
        cmds.addAll(renderRightPanel())

        frameCount++
        waveformPhase += 0.15

        val out = mutableListOf<Byte>()
        for (cmd in cmds) {
            out.addAll(slipEncode(cmd).toList())
        }
        return out.toByteArray()
    }

    // ===== SONG screen =====

    private fun renderSong(): List<ByteArray> {
        val cmds = mutableListOf<ByteArray>()

        val rpX = RIGHT_PANEL_X

        // Waveform at top
        cmds.add(drawWaveform(4, 4, cWaveform, makeWaveformData(230)))

        // Title
        cmds.addAll(drawText("SONG", 4, 32, cText, cBg))

        // Column headers
        val headerY = 48
        val trackStartX = 28
        val trackSpacing = 26
        for (t in 0 until 8) {
            val tx = trackStartX + t * trackSpacing + 4
            val color = if (t == cursorX) cHeaderHi else cHeader
            cmds.addAll(drawText("${t + 1}", tx, headerY, color, cBg))
        }

        // Grid: 16 visible rows × 8 tracks from song.songGrid
        val rowStartY = 60
        val rowH = FONT_H
        val viewOffset = max(0, cursorY - 8) // scroll if cursor beyond visible

        for (visRow in 0 until 16) {
            val row = viewOffset + visRow
            if (row >= 256) break
            val y = rowStartY + visRow * rowH
            val isPlayRow = playing && row == songRow
            val isCursorRow = row == cursorY

            val rowNumColor = if (isPlayRow) cPlayArrow else cTextDim
            cmds.addAll(drawText(M8Song.hex2(row), 4, y, rowNumColor, cBg))

            if (isPlayRow) {
                cmds.addAll(drawText(">", 20, y, cPlayArrow, cBg))
            }

            for (t in 0 until 8) {
                val tx = trackStartX + t * trackSpacing
                val value = song.songGrid[row][t]
                val valueStr = if (value == M8Song.EMPTY) "--" else M8Song.hex2(value)

                val isCursorCell = isCursorRow && t == cursorX
                val cellBg = if (isCursorCell) cCursorBg else cBg
                val cellFg = when {
                    isCursorCell -> cCursor
                    value == M8Song.EMPTY -> cTextDim
                    else -> cTextBright
                }
                cmds.addAll(drawText(valueStr, tx, y, cellFg, cellBg))

                if (isPlayRow && t < 7) {
                    cmds.addAll(drawText(">", tx + 16, y, cPlayArrow, cBg))
                }
            }
        }

        // Footer — octave on the left
        cmds.addAll(drawText("O$octave", 4, HEIGHT - 10, cTextDim, cBg))

        return cmds
    }

    /**
     * Right-side status panel (tempo, per-track notes, piano, SCPIT indicators).
     * Rendered on every screen so global state is always visible, matching the
     * real M8 firmware.
     */
    // ===== PROJECT screen =====

    private fun renderProject(): List<ByteArray> {
        val cmds = mutableListOf<ByteArray>()
        val xLabel = 8
        val xValue = 96
        val yStart = 16
        val lineH = FONT_H + 4

        cmds.addAll(drawText("PROJECT", xLabel, yStart, cHeader, cBg))

        val rows = listOf(
            "NAME"        to "UNTITLED",
            "TRANSPOSE"   to "00",
            "TEMPO"       to song.tempo.toString(),
            "QUANTIZE"    to "OFF",
            "MIDI CHANNEL" to "01",
            "KEY"         to "C",
        )
        for ((i, pair) in rows.withIndex()) {
            val y = yStart + 20 + i * lineH
            cmds.addAll(drawText(pair.first, xLabel, y, cTextDim, cBg))
            cmds.addAll(drawText(pair.second, xValue, y, cText, cBg))
        }

        return cmds
    }

    private fun renderRightPanel(): List<ByteArray> {
        val cmds = mutableListOf<ByteArray>()
        val rpX = RIGHT_PANEL_X

        // VU meter (top right)
        val vuX = rpX; val vuY = 14; val vuW = 64; val vuH = 5
        val masterLevel = if (playing) {
            ((liveMasterLevelL + liveMasterLevelR) / 2 * 200).toInt().coerceIn(0, 100)
        } else 30
        cmds.add(drawRect(vuX, vuY, vuW, vuH, intArrayOf(15, 15, 15)))
        val fillW = vuW * masterLevel / 100
        if (fillW > 0) {
            val greenEnd = vuW * 50 / 100
            val yellowEnd = vuW * 80 / 100
            val gw = min(fillW, greenEnd)
            cmds.add(drawRect(vuX, vuY, gw, vuH, intArrayOf(0, 180, 0)))
            if (fillW > greenEnd) {
                val yw = min(fillW, yellowEnd) - greenEnd
                cmds.add(drawRect(vuX + greenEnd, vuY, yw, vuH, intArrayOf(200, 200, 0)))
            }
            if (fillW > yellowEnd) {
                val rw = fillW - yellowEnd
                cmds.add(drawRect(vuX + yellowEnd, vuY, rw, vuH, intArrayOf(200, 0, 0)))
            }
        }

        // Progress bar
        cmds.add(drawRect(vuX, vuY + 8, vuW, 3, intArrayOf(15, 15, 15)))
        val progW = if (playing) vuW * playRow / 15 else 0
        if (progW > 0) cmds.add(drawRect(vuX, vuY + 8, progW, 3, intArrayOf(50, 70, 180)))

        // Tempo
        val playIndicator = if (playing) ">" else " "
        cmds.addAll(drawText("T", rpX, 46, cText, cBg))
        cmds.addAll(drawText(playIndicator, rpX + 8, 46, cPlayArrow, cBg))
        cmds.addAll(drawText("${song.tempo}", rpX + 16, 46, cText, cBg))

        // Per-track notes
        val activePhrase = getActivePhrase()
        val noteStartY = 62
        val noteLineH = 14
        for (t in 0 until 8) {
            val y = noteStartY + t * noteLineH
            val trackNote = if (playing) activePhrase[playRow][t][0] else activePhrase[0][t][0]
            val noteStr = if (trackNote > 0) M8Song.noteName(trackNote) else "---"
            val noteColor = if (trackNote > 0) cNotePanel else cNotePanelDim
            cmds.addAll(drawText("${t + 1}", rpX, y, cNotePanelDim, cBg))
            cmds.addAll(drawText(noteStr, rpX + 16, y, noteColor, cBg))
        }

        // Piano keyboard
        val kbY = noteStartY + 8 * noteLineH + 6
        val kbX = rpX + 4
        val kbW = 56; val kbH = 18; val whiteKeyW = 8
        cmds.add(drawRect(kbX, kbY, kbW, kbH, cKeyWhite))
        for (i in 1 until 7) {
            cmds.add(drawRect(kbX + i * whiteKeyW, kbY, 1, kbH, cKeySep))
        }
        val blackOffsets = intArrayOf(1, 2, 4, 5, 6)
        for (offset in blackOffsets) {
            cmds.add(drawRect(kbX + offset * whiteKeyW - 3, kbY, 5, 11, cKeyBlack))
        }

        // Mode indicator (EDIT / OPT / SHIFT held)
        val modeY = kbY + kbH + 4
        val optHeld = lastKeys and M8Commands.KEY_OPTION != 0
        val shiftHeld = lastKeys and M8Commands.KEY_SHIFT != 0
        if (editMode) {
            cmds.add(drawRect(rpX, modeY, 64, 10, intArrayOf(180, 60, 30)))
            cmds.addAll(drawText("EDIT", rpX + 16, modeY, intArrayOf(255, 255, 255), intArrayOf(180, 60, 30)))
        } else if (optHeld) {
            cmds.add(drawRect(rpX, modeY, 64, 10, intArrayOf(40, 120, 60)))
            cmds.addAll(drawText("OPT", rpX + 20, modeY, intArrayOf(255, 255, 255), intArrayOf(40, 120, 60)))
        } else if (shiftHeld) {
            cmds.add(drawRect(rpX, modeY, 64, 10, intArrayOf(60, 60, 160)))
            cmds.addAll(drawText("SHIFT", rpX + 12, modeY, intArrayOf(255, 255, 255), intArrayOf(60, 60, 160)))
        }

        // Bottom-right status cluster — navigation grid + MIDI
        //   P  SCPIT        ← Project + top row
        //     MFC      M    ← bottom row + MIDI
        val statusY = HEIGHT - 24

        cmds.addAll(drawText("P", rpX, statusY, if (screen == SCREEN_PROJECT) cTextBright else cTextDim, cBg))

        // SCPIT letters — top row of screen grid
        val scpitLetters = charArrayOf('S', 'C', 'P', 'I', 'T')
        val scpitScreens = intArrayOf(SCREEN_SONG, SCREEN_CHAIN, SCREEN_PHRASE, SCREEN_INSTRUMENT, SCREEN_TABLE)
        for (idx in scpitLetters.indices) {
            val lx = rpX + 12 + idx * 8
            val active = screen == scpitScreens[idx]
            cmds.addAll(drawText(scpitLetters[idx].toString(), lx, statusY, if (active) cTextBright else cTextDim, cBg))
        }

        // MFC letters — bottom row of screen grid (Mixer / FX / Config)
        val mfcLetters = charArrayOf('M', 'F', 'C')
        val mfcScreens = intArrayOf(SCREEN_MIXER, SCREEN_FX, SCREEN_CONFIG)
        val mfcY = statusY + FONT_H + 2
        for (idx in mfcLetters.indices) {
            val lx = rpX + 12 + idx * 8
            val active = screen == mfcScreens[idx]
            cmds.addAll(drawText(mfcLetters[idx].toString(), lx, mfcY, if (active) cTextBright else cTextDim, cBg))
        }

        // MIDI indicator
        cmds.addAll(drawText("M", rpX + 52, mfcY, if (midiActive) cTextBright else cTextDim, cBg))

        return cmds
    }

    // ===== CHAIN screen =====

    private fun renderChain(): List<ByteArray> {
        val cmds = mutableListOf<ByteArray>()
        val yStart = 16
        val chainIdx = selectedChain.coerceIn(0, 254)
        val chain = song.chains[chainIdx]

        cmds.addAll(drawText("CHAIN ${M8Song.hex2(chainIdx)}", 4, yStart, cHeader, cBg))

        // Column headers
        val colPhrase = 28
        val colTranspose = 68
        cmds.addAll(drawText("PHR", colPhrase, yStart + 14, cHeader, cBg))
        cmds.addAll(drawText("TSP", colTranspose, yStart + 14, cHeader, cBg))

        val rowH = FONT_H + 3
        for (row in 0 until 16) {
            val y = yStart + 14 + (row + 1) * rowH
            val isCursorRow = row == cursorY
            val chainRow = chain.rows[row]

            val rowColor = cTextDim
            cmds.addAll(drawText(M8Song.hex2(row), 4, y, rowColor, cBg))

            val phraseStr = if (chainRow.phrase == M8Song.EMPTY) "--" else M8Song.hex2(chainRow.phrase)
            val isCursorPhrase = isCursorRow && cursorX == 0
            val phBg = if (isCursorPhrase) cCursorBg else cBg
            val phFg = if (isCursorPhrase) cCursor else if (chainRow.phrase == M8Song.EMPTY) cTextDim else cTextBright
            cmds.addAll(drawText(phraseStr, colPhrase, y, phFg, phBg))

            val tspStr = if (chainRow.phrase == M8Song.EMPTY) "--" else String.format("%+03d", chainRow.transpose)
            val isCursorTsp = isCursorRow && cursorX == 1
            val tspBg = if (isCursorTsp) cCursorBg else cBg
            val tspFg = if (isCursorTsp) cCursor else if (chainRow.phrase == M8Song.EMPTY) cTextDim else cText
            cmds.addAll(drawText(tspStr, colTranspose, y, tspFg, tspBg))
        }

        return cmds
    }

    // ===== PHRASE screen (all 8 tracks simultaneously) =====

    private fun renderPhrase(): List<ByteArray> {
        val cmds = mutableListOf<ByteArray>()
        val yStart = 14

        // Header: which phrase (from cursor track)
        val phraseIdx = currentPhrasePerTrack.getOrElse(cursorX.coerceIn(0, 7)) { selectedPhrase }
            .let { if (it == M8Song.EMPTY) selectedPhrase else it }
        val fieldName = phraseColumnName(phraseEditColumn)
        cmds.addAll(drawText("PHRASE ${M8Song.hex2(phraseIdx)} T${cursorX.coerceIn(0, 7) + 1} $fieldName", 4, yStart, cText, cBg))

        if (editMode) {
            cmds.addAll(drawText("EDIT", 80, yStart, intArrayOf(255, 100, 100), cBg))
        }

        // Track headers - each track column shows note/inst/vol compactly
        // Layout: rowNum(16px) + 8 tracks × (note3 + inst2 + vol2 = ~7 chars = 56px) ... too wide
        // Real M8: shows note+inst+vol per track more compactly
        // We'll show: row# | N I V | N I V | ... for 8 tracks
        // Each track column = 36px (note:24 + inst:8 + gap:4)

        val trackW = 28
        val trackStartX = 20
        val colHeaderY = yStart + 12

        for (t in 0 until 8) {
            val tx = trackStartX + t * trackW
            val color = if (t == cursorX) cHeaderHi else cHeader
            cmds.addAll(drawText("${t + 1}", tx + 12, colHeaderY, color, cBg))
        }

        val rowH = FONT_H + 2
        val dataStartY = colHeaderY + 12

        for (row in 0 until 16) {
            val y = dataStartY + row * rowH
            val isPlayRow = playing && row == playRow
            val isCursorRow = row == cursorY

            // Row number
            val rowColor = if (isPlayRow) cPlayArrow else cTextDim
            cmds.addAll(drawText(M8Song.hex2(row), 2, y, rowColor, cBg))

            if (isPlayRow) {
                cmds.addAll(drawText(">", 16, y, cPlayArrow, cBg))
            }

            // Per-track data
            for (t in 0 until 8) {
                val tx = trackStartX + t * trackW
                val pIdx = currentPhrasePerTrack.getOrElse(t) { M8Song.EMPTY }
                val isCursorTrack = t == cursorX && isCursorRow

                if (pIdx == M8Song.EMPTY || pIdx > 254) {
                    // Empty track
                    val fg = if (isCursorTrack) cCursor else cTextDim
                    val bg = if (isCursorTrack) cCursorBg else cBg
                    cmds.addAll(drawText("---", tx, y, fg, bg))
                } else {
                    val step = song.phrases[pIdx].steps[row]
                    val noteStr = M8Song.noteName(step.note)
                    val isHighlightTrack = t == cursorX

                    // Note
                    val noteFg = when {
                        isCursorTrack -> cCursor
                        step.note == M8Song.EMPTY -> cTextDim
                        isHighlightTrack -> cTextBright
                        else -> cText
                    }
                    val noteBg = if (isCursorTrack) cCursorBg else cBg
                    cmds.addAll(drawText(noteStr, tx, y, noteFg, noteBg))

                    // Instrument (compact, 1 char)
                    if (step.instrument != M8Song.EMPTY) {
                        val instFg = if (isHighlightTrack) cNotePanel else cNotePanelDim
                        cmds.addAll(drawText(M8Song.hex2(step.instrument).takeLast(1), tx + 26, y, instFg, cBg))
                    }
                }
            }
        }

        // Waveform at bottom
        cmds.addAll(renderWaveformBottom())
        return cmds
    }

    private fun phraseColumnName(column: Int): String = when (column.coerceIn(0, 8)) {
        0 -> "NOTE"
        1 -> "INST"
        2 -> "VOL"
        3 -> "FX1"
        4 -> "FX1V"
        5 -> "FX2"
        6 -> "FX2V"
        7 -> "FX3"
        else -> "FX3V"
    }

    // ===== INSTRUMENT screen =====

    private fun renderInstrument(): List<ByteArray> {
        val cmds = mutableListOf<ByteArray>()
        val yStart = 16
        val instIdx = selectedInstrument.coerceIn(0, instruments.size - 1)
        val inst = instruments[instIdx]

        cmds.addAll(drawText("INSTRUMENT ${M8Song.hex2(instIdx)}", 4, yStart, cHeader, cBg))
        cmds.addAll(drawText(inst.name, WIDTH - inst.name.length * FONT_W - 8, yStart, cTextBright, cBg))

        val lineH = FONT_H + 2
        var lineIdx = 0
        val dataX = 80

        // Type
        val typeY = yStart + 14 + lineIdx * lineH
        val typeCursor = cursorY == lineIdx
        cmds.addAll(drawText("TYPE", 8, typeY, cText, if (typeCursor) cCursorBg else cBg))
        cmds.addAll(drawText(inst.type.label, dataX, typeY, if (typeCursor) cTextBright else cText, if (typeCursor) cCursorBg else cBg))
        lineIdx++

        // Name
        val nameY = yStart + 14 + lineIdx * lineH
        val nameCursor = cursorY == lineIdx
        cmds.addAll(drawText("NAME", 8, nameY, cText, if (nameCursor) cCursorBg else cBg))
        cmds.addAll(drawText(inst.name, dataX, nameY, if (nameCursor) cTextBright else cText, if (nameCursor) cCursorBg else cBg))
        lineIdx++

        // Separator
        lineIdx++

        // Type-specific params
        val typeParams = inst.getTypeParams()
        for ((label, value) in typeParams) {
            val y = yStart + 14 + lineIdx * lineH
            val isCursor = cursorY == lineIdx
            val bg = if (isCursor) cCursorBg else cBg
            cmds.addAll(drawText(label.padEnd(10), 8, y, cText, bg))
            cmds.addAll(drawText(value, dataX, y, if (isCursor) cTextBright else cTextDim, bg))
            lineIdx++
        }

        // Separator
        lineIdx++

        // Shared params
        val sharedParams = inst.getSharedParams()
        for ((label, value) in sharedParams) {
            val y = yStart + 14 + lineIdx * lineH
            if (y >= HEIGHT - 4) break
            val isCursor = cursorY == lineIdx
            val bg = if (isCursor) cCursorBg else cBg
            cmds.addAll(drawText(label.padEnd(10), 8, y, cText, bg))
            cmds.addAll(drawText(value, dataX, y, if (isCursor) cTextBright else cTextDim, bg))
            lineIdx++
        }

        return cmds
    }

    // ===== TABLE screen =====

    private fun renderTable(): List<ByteArray> {
        val cmds = mutableListOf<ByteArray>()
        val yStart = 16
        val tableIdx = selectedTable.coerceIn(0, 255)
        val table = song.tables[tableIdx]

        cmds.addAll(drawText("TABLE ${M8Song.hex2(tableIdx)}", 4, yStart, cHeader, cBg))

        // Column headers
        val colTsp = 28; val colVol = 56; val colFx1 = 84; val colFx2 = 128; val colFx3 = 172
        val headerY = yStart + 14
        cmds.addAll(drawText("TSP", colTsp, headerY, cHeader, cBg))
        cmds.addAll(drawText("VOL", colVol, headerY, cHeader, cBg))
        cmds.addAll(drawText("FX1", colFx1, headerY, cHeader, cBg))
        cmds.addAll(drawText("FX2", colFx2, headerY, cHeader, cBg))
        cmds.addAll(drawText("FX3", colFx3, headerY, cHeader, cBg))

        val rowH = FONT_H + 3
        for (row in 0 until 16) {
            val y = headerY + (row + 1) * rowH
            val isCursorRow = row == cursorY
            val tr = table.rows[row]

            cmds.addAll(drawText(M8Song.hex2(row), 4, y, cTextDim, cBg))

            // Transpose
            val tspStr = String.format("%+03d", tr.transpose)
            val tspCur = isCursorRow && cursorX == 0
            cmds.addAll(drawText(tspStr, colTsp, y, if (tspCur) cCursor else cText, if (tspCur) cCursorBg else cBg))

            // Volume
            val volStr = if (tr.volume == M8Song.EMPTY) "--" else M8Song.hex2(tr.volume)
            val volCur = isCursorRow && cursorX == 1
            cmds.addAll(drawText(volStr, colVol, y, if (volCur) cCursor else cText, if (volCur) cCursorBg else cBg))

            // FX1
            val fx1Name = M8FxEngine.fxName(tr.fx1Cmd)
            val fx1Val = M8Song.hex2(tr.fx1Val)
            val fx1Cur = isCursorRow && cursorX in 2..3
            cmds.addAll(drawText("$fx1Name$fx1Val", colFx1, y, if (fx1Cur) cCursor else cText, if (fx1Cur) cCursorBg else cBg))

            // FX2
            val fx2Name = M8FxEngine.fxName(tr.fx2Cmd)
            val fx2Val = M8Song.hex2(tr.fx2Val)
            val fx2Cur = isCursorRow && cursorX in 4..5
            cmds.addAll(drawText("$fx2Name$fx2Val", colFx2, y, if (fx2Cur) cCursor else cText, if (fx2Cur) cCursorBg else cBg))

            // FX3
            val fx3Name = M8FxEngine.fxName(tr.fx3Cmd)
            val fx3Val = M8Song.hex2(tr.fx3Val)
            val fx3Cur = isCursorRow && cursorX in 6..7
            cmds.addAll(drawText("$fx3Name$fx3Val", colFx3, y, if (fx3Cur) cCursor else cText, if (fx3Cur) cCursorBg else cBg))
        }

        return cmds
    }

    // ===== MIXER screen =====

    private fun renderMixer(): List<ByteArray> {
        val cmds = mutableListOf<ByteArray>()
        val yStart = 20
        cmds.addAll(drawText("MIXER", 136, yStart, cHeader, cBg))

        val levels = liveTrackLevels

        for (ch in 0 until 8) {
            val x = 6 + ch * 29
            val y = yStart + 16

            // Track label with volume from mixer
            val volHex = M8Song.hex2(song.mixer.trackVolumes[ch])
            cmds.addAll(drawText("${ch + 1}", x + 4, y, cText, cBg))

            val barH = 80
            val barY = y + 14

            val level = if (levels != null && playing) {
                (levels.getOrElse(ch) { 0.0 } * 200).toInt().coerceIn(0, 100)
            } else {
                (song.mixer.trackVolumes[ch] * 100 / 255)
            }

            cmds.add(drawRect(x, barY, 28, barH, intArrayOf(15, 15, 15)))

            val barFill = barH * level / 100
            val color = when {
                level > 85 -> intArrayOf(255, 50, 50)
                level > 60 -> intArrayOf(255, 200, 0)
                else -> intArrayOf(0, 180 * level / 100 + 40, 0)
            }
            if (barFill > 0) {
                cmds.add(drawRect(x, barY + barH - barFill, 28, barFill, color))
            }

            // Mixer editing uses cursorY for the selected track and cursorX for
            // that track's parameter (volume/pan/sends), matching handleEditModeArrows.
            val isCursor = cursorY == ch && cursorX == 0
            val bg = if (isCursor) cCursorBg else cBg
            cmds.addAll(drawText(volHex, x + 4, barY + barH + 4, cText, bg))

            // Send levels below volume
            val sendY = barY + barH + 16
            val cho = song.mixer.trackChorusSend[ch]
            val del = song.mixer.trackDelaySend[ch]
            val rev = song.mixer.trackReverbSend[ch]
            if (cho > 0 || del > 0 || rev > 0) {
                cmds.addAll(drawText("C${M8Song.hex2(cho).takeLast(1)}", x, sendY, cTextDim, cBg))
                cmds.addAll(drawText("D${M8Song.hex2(del).takeLast(1)}", x, sendY + 10, cTextDim, cBg))
                cmds.addAll(drawText("R${M8Song.hex2(rev).takeLast(1)}", x, sendY + 20, cTextDim, cBg))
            }
        }

        // Master volume
        val masterY = yStart + 160
        cmds.addAll(drawText("MASTER ${M8Song.hex2(song.mixer.masterVolume)}", 4, masterY, cHeader, cBg))
        val mBarW = 140
        val mLevelL = if (playing) (liveMasterLevelL * 200).toInt().coerceIn(0, 100) else 0
        val mLevelR = if (playing) (liveMasterLevelR * 200).toInt().coerceIn(0, 100) else 0

        cmds.addAll(drawText("L", 4, masterY + 14, cText, cBg))
        cmds.add(drawRect(16, masterY + 14, mBarW, 8, intArrayOf(15, 15, 15)))
        val lFill = mBarW * mLevelL / 100
        if (lFill > 0) cmds.add(drawRect(16, masterY + 14, lFill, 8, intArrayOf(0, 200, 100)))

        cmds.addAll(drawText("R", 4, masterY + 24, cText, cBg))
        cmds.add(drawRect(16, masterY + 24, mBarW, 8, intArrayOf(15, 15, 15)))
        val rFill = mBarW * mLevelR / 100
        if (rFill > 0) cmds.add(drawRect(16, masterY + 24, rFill, 8, intArrayOf(0, 200, 100)))

        return cmds
    }

    // ===== FX screen =====

    private fun renderFx(): List<ByteArray> {
        val cmds = mutableListOf<ByteArray>()
        val yStart = 16
        val lineH = FONT_H + 2
        val dataX = 100
        var line = 0

        cmds.addAll(drawText("EFFECTS", 4, yStart, cHeader, cBg))
        line++

        // --- Chorus ---
        val choY = yStart + 14 + line * lineH
        cmds.addAll(drawText("CHORUS", 4, choY, cHeaderHi, cBg))
        line++

        val chorusParams = listOf(
            "MOD DEPTH" to M8Song.hex2(song.chorus.modDepth),
            "MOD FREQ" to M8Song.hex2(song.chorus.modFreq),
            "WIDTH" to M8Song.hex2(song.chorus.width),
            "REV SEND" to M8Song.hex2(song.chorus.reverbSend),
        )
        for ((label, value) in chorusParams) {
            val y = yStart + 14 + line * lineH
            val isCursor = cursorY == line - 2
            val bg = if (isCursor) cCursorBg else cBg
            cmds.addAll(drawText(label.padEnd(12), 8, y, cText, bg))
            cmds.addAll(drawText(value, dataX, y, if (isCursor) cTextBright else cTextDim, bg))
            line++
        }

        // --- Delay ---
        line++
        val delY = yStart + 14 + line * lineH
        cmds.addAll(drawText("DELAY", 4, delY, cHeaderHi, cBg))
        line++

        val delayParams = listOf(
            "FILTER HP" to M8Song.hex2(song.delay.filterHP),
            "FILTER LP" to M8Song.hex2(song.delay.filterLP),
            "TIME L" to M8Song.hex2(song.delay.timeL),
            "TIME R" to M8Song.hex2(song.delay.timeR),
            "FEEDBACK" to M8Song.hex2(song.delay.feedback),
            "WIDTH" to M8Song.hex2(song.delay.width),
            "REV SEND" to M8Song.hex2(song.delay.reverbSend),
        )
        for ((label, value) in delayParams) {
            val y = yStart + 14 + line * lineH
            if (y >= HEIGHT - 4) break
            val isCursor = cursorY == line - 2
            val bg = if (isCursor) cCursorBg else cBg
            cmds.addAll(drawText(label.padEnd(12), 8, y, cText, bg))
            cmds.addAll(drawText(value, dataX, y, if (isCursor) cTextBright else cTextDim, bg))
            line++
        }

        // --- Reverb ---
        line++
        val revY = yStart + 14 + line * lineH
        if (revY < HEIGHT - 14) {
            cmds.addAll(drawText("REVERB", 4, revY, cHeaderHi, cBg))
            line++

            val reverbParams = listOf(
                "FILTER HP" to M8Song.hex2(song.reverb.filterHP),
                "FILTER LP" to M8Song.hex2(song.reverb.filterLP),
                "SIZE" to M8Song.hex2(song.reverb.size),
                "DAMPING" to M8Song.hex2(song.reverb.damping),
                "MOD DEPTH" to M8Song.hex2(song.reverb.modDepth),
                "MOD FREQ" to M8Song.hex2(song.reverb.modFreq),
                "WIDTH" to M8Song.hex2(song.reverb.width),
            )
            for ((label, value) in reverbParams) {
                val y = yStart + 14 + line * lineH
                if (y >= HEIGHT - 4) break
                val isCursor = cursorY == line - 2
                val bg = if (isCursor) cCursorBg else cBg
                cmds.addAll(drawText(label.padEnd(12), 8, y, cText, bg))
                cmds.addAll(drawText(value, dataX, y, if (isCursor) cTextBright else cTextDim, bg))
                line++
            }
        }

        return cmds
    }

    // ===== CONFIG screen =====

    private fun renderConfig(): List<ByteArray> {
        val cmds = mutableListOf<ByteArray>()
        val yStart = 16
        val lineH = FONT_H + 4
        val dataX = 100

        cmds.addAll(drawText("CONFIG", 4, yStart, cHeader, cBg))

        val params = listOf(
            "SONG NAME" to song.name,
            "TEMPO" to "${song.tempo}",
            "TRANSPOSE" to String.format("%+d", song.transpose),
            "SCALE" to song.scales[song.activeScale].name,
            "QUANTIZE" to if (song.quantize == 0) "OFF" else "${song.quantize}",
            "OCTAVE" to "$octave",
            "KEY" to M8Song.NOTE_NAMES[song.scales[song.activeScale].key],
        )

        for ((i, pair) in params.withIndex()) {
            val y = yStart + 20 + i * lineH
            val isCursor = cursorY == i
            val bg = if (isCursor) cCursorBg else cBg
            cmds.addAll(drawText(pair.first.padEnd(12), 8, y, cText, bg))
            cmds.addAll(drawText(pair.second, dataX, y, if (isCursor) cTextBright else cTextDim, bg))
        }

        // Song grid info
        val infoY = yStart + 20 + params.size * lineH + 10
        cmds.addAll(drawText("SONG ROW  ${M8Song.hex2(songRow)}", 8, infoY, cTextDim, cBg))
        cmds.addAll(drawText("CHAIN ROW ${M8Song.hex2(chainRow)}", 8, infoY + lineH, cTextDim, cBg))
        cmds.addAll(drawText("PLAY ROW  ${M8Song.hex2(playRow)}", 8, infoY + lineH * 2, cTextDim, cBg))
        cmds.addAll(drawText(if (playing) "PLAYING" else "STOPPED", 8, infoY + lineH * 3, if (playing) cPlayArrow else cTextDim, cBg))

        return cmds
    }

    // --- Screen tab header (for non-SONG screens) ---

    private fun renderScreenHeader(): List<ByteArray> {
        val cmds = mutableListOf<ByteArray>()
        val headerBg = intArrayOf(10, 15, 30)
        cmds.add(drawRect(0, 0, WIDTH, 12, headerBg))

        for (i in SCREEN_NAMES.indices) {
            val x = i * 40
            if (i == screen) {
                cmds.add(drawRect(x, 0, 39, 12, cCursorBg))
                cmds.addAll(drawText(SCREEN_NAMES[i], x + 2, 1, cCursor, cCursorBg))
            } else {
                val short = SCREEN_NAMES[i].take(3)
                cmds.addAll(drawText(short, x + 6, 1, cTextDim, headerBg))
            }
        }
        return cmds
    }

    // --- Shared rendering helpers ---

    private fun makeWaveformData(width: Int): ByteArray {
        val liveData = liveWaveformData
        return if (liveData != null && playing) {
            ByteArray(width) { i ->
                val srcIdx = (i * liveData.size / width).coerceIn(0, liveData.size - 1)
                liveData[srcIdx]
            }
        } else {
            ByteArray(width) { i ->
                val t = waveformPhase + i * 0.05
                var v = sin(t) * 0.5 + sin(t * 2.3) * 0.25 + sin(t * 0.7 + 1.0) * 0.25
                v *= if (playing) 0.8 + 0.2 * sin(waveformPhase * 0.3) else 0.3
                max(0, min(255, (128 + v * 80).toInt())).toByte()
            }
        }
    }

    private fun renderWaveformBottom(): List<ByteArray> {
        val cmds = mutableListOf<ByteArray>()
        val waveY = HEIGHT - 36
        cmds.add(drawWaveform(20, waveY, cWaveform, makeWaveformData(280)))
        return cmds
    }

    // --- Protocol command builders ---

    private fun drawRect(x: Int, y: Int, w: Int, h: Int, color: IntArray): ByteArray {
        return byteArrayOf(
            DRAW_RECT,
            (x and 0xFF).toByte(), (x shr 8 and 0xFF).toByte(),
            (y and 0xFF).toByte(), (y shr 8 and 0xFF).toByte(),
            (w and 0xFF).toByte(), (w shr 8 and 0xFF).toByte(),
            (h and 0xFF).toByte(), (h shr 8 and 0xFF).toByte(),
            color[0].toByte(), color[1].toByte(), color[2].toByte(),
        )
    }

    private fun drawChar(x: Int, y: Int, ch: Int, fg: IntArray, bg: IntArray): ByteArray {
        return byteArrayOf(
            DRAW_CHAR,
            (x and 0xFF).toByte(), (x shr 8 and 0xFF).toByte(),
            (y and 0xFF).toByte(), (y shr 8 and 0xFF).toByte(),
            ch.toByte(),
            fg[0].toByte(), fg[1].toByte(), fg[2].toByte(),
            bg[0].toByte(), bg[1].toByte(), bg[2].toByte(),
        )
    }

    private fun drawWaveform(x: Int, y: Int, color: IntArray, data: ByteArray): ByteArray {
        val header = byteArrayOf(
            DRAW_WAVEFORM,
            (x and 0xFF).toByte(), (x shr 8 and 0xFF).toByte(),
            (y and 0xFF).toByte(), (y shr 8 and 0xFF).toByte(),
            color[0].toByte(), color[1].toByte(), color[2].toByte(),
        )
        return header + data
    }

    private fun drawText(text: String, x: Int, y: Int, fg: IntArray, bg: IntArray): List<ByteArray> {
        return text.mapIndexed { i, ch ->
            drawChar(x + i * FONT_W, y, ch.code, fg, bg)
        }
    }

    private fun slipEncode(data: ByteArray): ByteArray {
        val out = mutableListOf<Byte>()
        out.add(SLIP_END)
        for (b in data) {
            when (b) {
                SLIP_END -> { out.add(SLIP_ESC); out.add(SLIP_ESC_END) }
                SLIP_ESC -> { out.add(SLIP_ESC); out.add(SLIP_ESC_ESC) }
                else -> out.add(b)
            }
        }
        out.add(SLIP_END)
        return out.toByteArray()
    }
}