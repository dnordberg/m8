package com.m8.emulator

import com.m8.protocol.M8Commands
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Virtual M8 tracker emulator — generates SLIP-encoded draw commands
 * identical to what a real Teensy M8 headless device sends.
 *
 * Runs entirely in-process. No network, no USB, no server needed.
 */
class M8Emulator {

    // --- Protocol constants ---
    companion object {
        const val WIDTH = 320
        const val HEIGHT = 240
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
        const val SCREEN_PHRASE = 0
        const val SCREEN_SONG = 1
        const val SCREEN_CHAIN = 2
        const val SCREEN_INSTRUMENT = 3
        const val SCREEN_TABLE = 4
        const val SCREEN_MIXER = 5
        const val SCREEN_FX = 6
        const val SCREEN_CONFIG = 7

        val SCREEN_NAMES = arrayOf("SONG", "CHAIN", "PHRASE", "INSTR", "TABLE", "MIXER", "FX", "CONFIG")
        val NOTES = arrayOf("C-", "C#", "D-", "D#", "E-", "F-", "F#", "G-", "G#", "A-", "A#", "B-")
    }

    // --- Tracker state ---
    var screen = SCREEN_PHRASE
    var cursorX = 0
    var cursorY = 0
    var playing = false
    var playRow = 0
    var bpm = 120
    var octave = 4
    var frameCount = 0
    var waveformPhase = 0.0

    // Multiple patterns for song mode
    var currentPattern = 0
    val patternOrder = intArrayOf(0, 0, 1, 0, 1, 2, 0, 1) // Song arrangement
    var songPosition = 0  // Index into patternOrder

    // External waveform data from synth (set by ViewModel)
    var liveWaveformData: ByteArray? = null

    // Phrase data: 16 rows x 8 tracks
    // Track 0: Lead melody (pulse w/ PWM)
    // Track 1: Bass line (saw)
    // Track 2: Pad / chord (triangle)
    // Track 3: Hi-hat / percussion (noise)
    // Track 4: FM bell accents
    // Track 5: Pluck arpeggio
    // Track 6: Sub bass (sine)
    // Track 7: FX hits (FM)
    val phraseData = Array(16) { row ->
        Array(8) { track ->
            // Key of C minor: C=25, Eb=28, G=32, Bb=35
            // Lead melody — expressive phrase
            val leadNotes = intArrayOf(
                60, 0, 63, 0,  67, 0, 70, 63,  // C5 . Eb5 . G5 . Bb5 Eb5
                72, 0, 70, 67,  63, 0, 60, 0   // C6 . Bb5 G5 Eb5 . C5 .
            )
            // Bass — driving eighth-note root pattern
            val bassNotes = intArrayOf(
                36, 0, 36, 0,  36, 0, 36, 48,  // C3 . C3 . C3 . C3 C4
                39, 0, 39, 0,  43, 0, 43, 0    // Eb3 . Eb3 . G3 . G3 .
            )
            // Pad chords (sustained)
            val padNotes = intArrayOf(
                60, 0, 0, 0,  0, 0, 0, 0,      // C5 (hold)
                63, 0, 0, 0,  67, 0, 0, 0      // Eb5 (hold) G5 (hold)
            )
            // Hi-hat pattern
            val hatNotes = intArrayOf(
                80, 0, 80, 0,  80, 0, 80, 0,   // steady eighths
                80, 0, 80, 80, 80, 0, 80, 0    // variation with ghost note
            )
            val hatVols = intArrayOf(
                0xCC, 0, 0x88, 0,  0xCC, 0, 0x88, 0,
                0xCC, 0, 0x88, 0x55, 0xCC, 0, 0x88, 0
            )
            // FM bell — sparse accents on beat
            val bellNotes = intArrayOf(
                72, 0, 0, 0,  0, 0, 0, 0,
                0, 0, 0, 0,   79, 0, 0, 0      // C6 . . . . . . . . . . . G6 . . .
            )
            // Pluck arpeggios
            val pluckNotes = intArrayOf(
                0, 60, 0, 63,  0, 67, 0, 63,
                0, 60, 0, 67,  0, 72, 0, 67
            )
            // Sub bass (octave below bass, only on downbeats)
            val subNotes = intArrayOf(
                24, 0, 0, 0,  0, 0, 0, 0,
                27, 0, 0, 0,  31, 0, 0, 0
            )
            // FX hits
            val fxNotes = intArrayOf(
                0, 0, 0, 0,  0, 0, 0, 96,
                0, 0, 0, 0,  0, 0, 0, 0
            )

            val note = when (track) {
                0 -> leadNotes[row]
                1 -> bassNotes[row]
                2 -> padNotes[row]
                3 -> hatNotes[row]
                4 -> bellNotes[row]
                5 -> pluckNotes[row]
                6 -> subNotes[row]
                7 -> fxNotes[row]
                else -> 0
            }

            val vol = when {
                track == 3 -> hatVols[row] // per-step hat volume
                note > 0 -> 0xCC          // default volume
                else -> 0
            }

            val inst = track // instrument matches track
            val fx = 0
            intArrayOf(note, inst, vol, fx, 0)
        }
    }

    // Pattern 1: variation — different melody, same groove
    val phraseData1 = Array(16) { row ->
        Array(8) { track ->
            val leadNotes = intArrayOf(
                67, 0, 70, 0,  72, 0, 75, 72,
                70, 0, 67, 63,  60, 0, 63, 0
            )
            val bassNotes = intArrayOf(
                43, 0, 43, 0,  43, 0, 43, 36,
                36, 0, 36, 0,  39, 0, 39, 0
            )
            val padNotes = intArrayOf(
                67, 0, 0, 0,  0, 0, 0, 0,
                70, 0, 0, 0,  63, 0, 0, 0
            )
            val hatNotes = intArrayOf(
                80, 80, 80, 0,  80, 0, 80, 80,
                80, 0, 80, 0,  80, 80, 80, 0
            )
            val hatVols = intArrayOf(
                0xCC, 0x66, 0x88, 0,  0xCC, 0, 0x88, 0x55,
                0xCC, 0, 0x88, 0,  0xCC, 0x66, 0x88, 0
            )
            val bellNotes = intArrayOf(
                0, 0, 0, 0,  79, 0, 0, 0,
                0, 0, 84, 0,  0, 0, 0, 0
            )
            val pluckNotes = intArrayOf(
                0, 67, 0, 70,  0, 72, 0, 70,
                0, 67, 0, 72,  0, 75, 0, 72
            )
            val subNotes = intArrayOf(
                31, 0, 0, 0,  0, 0, 0, 0,
                24, 0, 0, 0,  27, 0, 0, 0
            )
            val fxNotes = intArrayOf(
                0, 0, 0, 96,  0, 0, 0, 0,
                0, 0, 0, 0,  0, 0, 96, 0
            )

            val note = when (track) {
                0 -> leadNotes[row]; 1 -> bassNotes[row]; 2 -> padNotes[row]
                3 -> hatNotes[row]; 4 -> bellNotes[row]; 5 -> pluckNotes[row]
                6 -> subNotes[row]; 7 -> fxNotes[row]; else -> 0
            }
            val vol = when {
                track == 3 -> hatVols[row]
                note > 0 -> 0xCC
                else -> 0
            }
            intArrayOf(note, track, vol, 0, 0)
        }
    }

    // Pattern 2: breakdown — sparse, atmospheric
    val phraseData2 = Array(16) { row ->
        Array(8) { track ->
            val leadNotes = intArrayOf(
                72, 0, 0, 0,  0, 0, 70, 0,
                0, 0, 0, 0,  67, 0, 0, 0
            )
            val bassNotes = intArrayOf(
                36, 0, 0, 0,  0, 0, 0, 0,
                39, 0, 0, 0,  0, 0, 0, 0
            )
            val padNotes = intArrayOf(
                60, 0, 0, 0,  0, 0, 0, 0,
                0, 0, 0, 0,  0, 0, 0, 0
            )
            val hatNotes = intArrayOf(
                0, 0, 80, 0,  0, 0, 0, 0,
                0, 0, 80, 0,  0, 0, 80, 0
            )
            val hatVols = intArrayOf(
                0, 0, 0x66, 0,  0, 0, 0, 0,
                0, 0, 0x66, 0,  0, 0, 0x55, 0
            )
            val bellNotes = intArrayOf(
                84, 0, 0, 0,  0, 0, 0, 0,
                0, 0, 0, 0,  79, 0, 0, 0
            )
            val pluckNotes = intArrayOf(0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0)
            val subNotes = intArrayOf(
                24, 0, 0, 0,  0, 0, 0, 0,
                27, 0, 0, 0,  0, 0, 0, 0
            )
            val fxNotes = intArrayOf(0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0)

            val note = when (track) {
                0 -> leadNotes[row]; 1 -> bassNotes[row]; 2 -> padNotes[row]
                3 -> hatNotes[row]; 4 -> bellNotes[row]; 5 -> pluckNotes[row]
                6 -> subNotes[row]; 7 -> fxNotes[row]; else -> 0
            }
            val vol = when {
                track == 3 -> hatVols[row]
                note > 0 -> 0xAA
                else -> 0
            }
            intArrayOf(note, track, vol, 0, 0)
        }
    }

    /** Get the active phrase data based on current pattern */
    fun getActivePhrase(): Array<Array<IntArray>> {
        return when (currentPattern) {
            0 -> phraseData
            1 -> phraseData1
            2 -> phraseData2
            else -> phraseData
        }
    }

    /** Advance to next pattern in song order */
    fun advancePattern() {
        songPosition = (songPosition + 1) % patternOrder.size
        currentPattern = patternOrder[songPosition]
    }

    private var lastKeys = 0

    // --- Colors ---
    private val cBg = intArrayOf(0, 0, 0)
    private val cText = intArrayOf(0, 255, 0)
    private val cTextDim = intArrayOf(0, 128, 0)
    private val cCursor = intArrayOf(255, 255, 255)
    private val cCursorBg = intArrayOf(60, 60, 180)
    private val cHeader = intArrayOf(255, 100, 0)
    private val cValue = intArrayOf(200, 200, 255)
    private val cPlayOn = intArrayOf(255, 50, 50)
    private val cWaveform = intArrayOf(0, 200, 255)

    // --- Key handling ---

    fun handleKeyState(keys: Int) {
        val pressed = keys and lastKeys.inv()
        lastKeys = keys

        if (pressed and M8Commands.KEY_UP != 0) cursorY = max(0, cursorY - 1)
        if (pressed and M8Commands.KEY_DOWN != 0) cursorY = min(15, cursorY + 1)
        if (pressed and M8Commands.KEY_LEFT != 0) cursorX = max(0, cursorX - 1)
        if (pressed and M8Commands.KEY_RIGHT != 0) cursorX = min(7, cursorX + 1)
        if (pressed and M8Commands.KEY_PLAY != 0) {
            playing = !playing
            if (playing) playRow = 0
        }
        if (pressed and M8Commands.KEY_OPTION != 0) screen = (screen + 1) % SCREEN_NAMES.size
        if (pressed and M8Commands.KEY_EDIT != 0) screen = (screen - 1 + SCREEN_NAMES.size) % SCREEN_NAMES.size
        if (pressed and M8Commands.KEY_SHIFT != 0) octave = (octave % 8) + 1
    }

    // --- Frame rendering ---

    /**
     * Render one frame and return the raw SLIP-encoded bytes.
     * Feed this directly into M8Protocol.processBytes().
     */
    fun renderFrame(): ByteArray {
        val cmds = mutableListOf<ByteArray>()

        // Clear screen
        cmds.add(drawRect(0, 0, WIDTH, HEIGHT, cBg))

        // Header
        cmds.addAll(renderHeader())

        // Main content
        when (screen) {
            SCREEN_PHRASE -> cmds.addAll(renderPhrase())
            SCREEN_MIXER -> cmds.addAll(renderMixer())
            SCREEN_INSTRUMENT -> cmds.addAll(renderInstrument())
            else -> cmds.addAll(renderPlaceholder())
        }

        // Waveform
        cmds.addAll(renderWaveform())

        // Footer
        cmds.addAll(renderFooter())

        // Advance animation state (row advance is now handled by audio engine)
        frameCount++
        waveformPhase += 0.15

        // SLIP-encode all commands into one byte array
        val out = mutableListOf<Byte>()
        for (cmd in cmds) {
            out.addAll(slipEncode(cmd).toList())
        }
        return out.toByteArray()
    }

    // --- Renderers ---

    private fun renderHeader(): List<ByteArray> {
        val cmds = mutableListOf<ByteArray>()
        cmds.add(drawRect(0, 0, WIDTH, 12, intArrayOf(20, 20, 40)))

        for (i in SCREEN_NAMES.indices) {
            val x = i * 40
            if (i == screen) {
                cmds.add(drawRect(x, 0, 39, 12, intArrayOf(60, 60, 120)))
                cmds.addAll(drawText(SCREEN_NAMES[i], x + 2, 1, cCursor, intArrayOf(60, 60, 120)))
            } else {
                val short = SCREEN_NAMES[i].take(3)
                cmds.addAll(drawText(short, x + 6, 1, cTextDim, intArrayOf(20, 20, 40)))
            }
        }
        return cmds
    }

    private fun renderPhrase(): List<ByteArray> {
        val cmds = mutableListOf<ByteArray>()
        val yStart = 16
        val rowH = FONT_H + 2

        val headers = arrayOf("ROW", "NT1", "I1", "NT2", "I2", "NT3", "I3", "VOL")
        for (i in headers.indices) {
            cmds.addAll(drawText(headers[i], 4 + i * 38, yStart, cHeader, cBg))
        }

        for (row in 0 until 16) {
            val y = yStart + (row + 1) * rowH
            val isPlayRow = playing && row == playRow
            val rowBg = if (isPlayRow) intArrayOf(40, 0, 0) else cBg
            val rowFg = if (isPlayRow) cPlayOn else cTextDim

            cmds.addAll(drawText(hex2(row), 4, y, rowFg, rowBg))

            val trackData = phraseData[row]
            var col = 0
            for (track in 0 until min(3, trackData.size)) {
                val (note, inst, vol, _, _) = trackData[track]
                val nx = 4 + (col + 1) * 38
                val isCursor = row == cursorY && cursorX == track
                val cellBg = if (isCursor) cCursorBg else rowBg
                val cellFg = if (isCursor) cCursor else cValue

                cmds.addAll(drawText(noteName(note), nx, y, cellFg, cellBg))
                val ix = 4 + (col + 2) * 38
                val iStr = if (note > 0) hex2(inst) else "--"
                cmds.addAll(drawText(iStr, ix, y, if (note == 0) cTextDim else cText, rowBg))
                col += 2
            }

            val note0 = trackData[0][0]
            val vol0 = trackData[0][2]
            val vx = 4 + 7 * 38
            val vStr = if (note0 > 0) hex2(vol0) else "--"
            cmds.addAll(drawText(vStr, vx, y, if (note0 == 0) cTextDim else intArrayOf(200, 100, 255), rowBg))
        }
        return cmds
    }

    // External track levels from synth (set by ViewModel)
    var liveTrackLevels: DoubleArray? = null
    var liveMasterLevelL = 0.0
    var liveMasterLevelR = 0.0

    private fun renderMixer(): List<ByteArray> {
        val cmds = mutableListOf<ByteArray>()
        val yStart = 20
        cmds.addAll(drawText("MIXER", 136, yStart, cHeader, cBg))

        val levels = liveTrackLevels

        for (ch in 0 until 8) {
            val x = 8 + ch * 38
            val y = yStart + 16
            cmds.addAll(drawText("CH${ch + 1}", x, y, cText, cBg))

            val barH = 100
            val barY = y + 14

            // Use live levels if available, else animated fallback
            val level = if (levels != null && playing) {
                (levels.getOrElse(ch) { 0.0 } * 200).toInt().coerceIn(0, 100)
            } else {
                (50 + 50 * sin(waveformPhase + ch * 0.8)).toInt()
            }

            // Bar background
            cmds.add(drawRect(x, barY, 28, barH, intArrayOf(30, 30, 30)))

            // Bar fill with color gradient (green -> yellow -> red)
            val barFill = barH * level / 100
            val color = when {
                level > 85 -> intArrayOf(255, 50, 50)   // Red - hot
                level > 60 -> intArrayOf(255, 200, 0)   // Yellow
                else -> intArrayOf(0, 255 * level / 100, 0) // Green
            }
            if (barFill > 0) {
                cmds.add(drawRect(x, barY + barH - barFill, 28, barFill, color))
            }

            val isCursor = cursorX == ch
            val bg = if (isCursor) cCursorBg else cBg
            cmds.addAll(drawText(hex2(level), x + 4, barY + barH + 4, cValue, bg))
        }

        // Master stereo VU meter at bottom
        val masterY = yStart + 140
        cmds.addAll(drawText("MASTER", 4, masterY, cHeader, cBg))
        val mBarW = 140
        val mLevelL = if (playing) (liveMasterLevelL * 200).toInt().coerceIn(0, 100) else 0
        val mLevelR = if (playing) (liveMasterLevelR * 200).toInt().coerceIn(0, 100) else 0

        // Left channel
        cmds.addAll(drawText("L", 4, masterY + 14, cText, cBg))
        cmds.add(drawRect(16, masterY + 14, mBarW, 8, intArrayOf(30, 30, 30)))
        val lFill = mBarW * mLevelL / 100
        if (lFill > 0) cmds.add(drawRect(16, masterY + 14, lFill, 8, intArrayOf(0, 200, 100)))

        // Right channel
        cmds.addAll(drawText("R", 4, masterY + 24, cText, cBg))
        cmds.add(drawRect(16, masterY + 24, mBarW, 8, intArrayOf(30, 30, 30)))
        val rFill = mBarW * mLevelR / 100
        if (rFill > 0) cmds.add(drawRect(16, masterY + 24, rFill, 8, intArrayOf(0, 200, 100)))

        // Pattern info
        cmds.addAll(drawText("PAT:${hex2(currentPattern)} POS:${hex2(songPosition)}", 180, masterY, cValue, cBg))
        cmds.addAll(drawText("BPM:$bpm  SWG:15%", 180, masterY + 14, cTextDim, cBg))

        return cmds
    }

    private fun renderInstrument(): List<ByteArray> {
        val cmds = mutableListOf<ByteArray>()
        val yStart = 16
        cmds.addAll(drawText("INSTRUMENT ${hex2(cursorX)}", 100, yStart, cHeader, cBg))

        val params = listOf(
            "TYPE" to "WAVSYNTH", "NAME" to "SYNTH ${String.format("%02d", cursorX)}",
            "SHAPE" to "SAW", "SIZE" to "80", "MULT" to "01", "WARP" to "00",
            "MIRROR" to "OFF", "FILTER" to "LP", "CUTOFF" to "FF", "RES" to "40",
            "AMP" to "80", "LIM" to "CLIP", "PAN" to "80", "DRY" to "C0",
            "CHO" to "00", "DEL" to "20", "REV" to "40",
        )

        for ((i, pair) in params.withIndex()) {
            val y = yStart + 14 + i * (FONT_H + 2)
            val isCursor = i == cursorY
            val bg = if (isCursor) cCursorBg else cBg
            cmds.addAll(drawText(pair.first.padEnd(8), 8, y, cText, bg))
            cmds.addAll(drawText(pair.second, 80, y, if (isCursor) cValue else cTextDim, bg))
        }
        return cmds
    }

    private fun renderPlaceholder(): List<ByteArray> {
        val cmds = mutableListOf<ByteArray>()
        val name = SCREEN_NAMES[screen]
        cmds.addAll(drawText("$name VIEW", 120, 60, cHeader, cBg))
        cmds.addAll(drawText("USE OPT/EDIT TO", 88, 100, cTextDim, cBg))
        cmds.addAll(drawText("SWITCH SCREENS", 96, 114, cTextDim, cBg))
        cmds.addAll(drawText("ARROWS TO NAVIGATE", 72, 140, cText, cBg))
        cmds.addAll(drawText("PLAY TO START/STOP", 72, 154, cPlayOn, cBg))
        return cmds
    }

    private fun renderWaveform(): List<ByteArray> {
        val cmds = mutableListOf<ByteArray>()
        val waveY = HEIGHT - 36
        val waveW = 280

        // Use live audio waveform data if available, else decorative sine
        val liveData = liveWaveformData
        val waveData: ByteArray
        if (liveData != null && playing) {
            // Downsample from 320 to 280
            waveData = ByteArray(waveW)
            for (i in 0 until waveW) {
                val srcIdx = (i * liveData.size / waveW).coerceIn(0, liveData.size - 1)
                waveData[i] = liveData[srcIdx]
            }
        } else {
            waveData = ByteArray(waveW)
            for (i in 0 until waveW) {
                val t = waveformPhase + i * 0.05
                var v = sin(t) * 0.5 + sin(t * 2.3) * 0.25 + sin(t * 0.7 + 1.0) * 0.25
                v *= if (playing) 0.8 + 0.2 * sin(waveformPhase * 0.3) else 0.3
                waveData[i] = max(0, min(255, (128 + v * 80).toInt())).toByte()
            }
        }

        cmds.add(drawWaveform(20, waveY, cWaveform, waveData))
        return cmds
    }

    private fun renderFooter(): List<ByteArray> {
        val cmds = mutableListOf<ByteArray>()
        val y = HEIGHT - 12
        cmds.add(drawRect(0, y, WIDTH, 12, intArrayOf(20, 20, 40)))

        val footerBg = intArrayOf(20, 20, 40)
        cmds.addAll(drawText("BPM:$bpm", 4, y + 1, cText, footerBg))

        val playText = if (playing) "PLAY" else "STOP"
        val playColor = if (playing) cPlayOn else cTextDim
        cmds.addAll(drawText(playText, 80, y + 1, playColor, footerBg))

        cmds.addAll(drawText("OCT:$octave", 140, y + 1, cText, footerBg))
        cmds.addAll(drawText("P:${hex2(currentPattern)}", 200, y + 1, cValue, footerBg))
        cmds.addAll(drawText("R:${hex2(playRow)}", 240, y + 1, cValue, footerBg))
        cmds.addAll(drawText("X:$cursorX Y:${hex2(cursorY)}", 280, y + 1, cTextDim, footerBg))
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

    // --- Helpers ---

    private fun noteName(n: Int): String {
        if (n == 0) return "---"
        val note = (n - 1) % 12
        val oct = (n - 1) / 12
        return "${NOTES[note]}$oct"
    }

    private fun hex2(v: Int): String = String.format("%02X", v and 0xFF)

    private operator fun IntArray.component6() = this[5]
}
