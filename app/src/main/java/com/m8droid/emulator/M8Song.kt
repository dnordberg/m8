package com.m8droid.emulator

/**
 * Complete M8 tracker song data model.
 * Matches the real Dirtywave M8 data structure.
 */

/** A single step in a phrase */
data class PhraseStep(
    var note: Int = 0xFF,        // 0xFF = empty, 0x00 = note off, 1-127 = MIDI note
    var instrument: Int = 0xFF,  // 0xFF = empty, 0-127 = instrument index
    var volume: Int = 0xFF,      // 0xFF = empty, 0x00-0x7F = volume
    var fx1Cmd: Int = 0,         // FX command 1 (3-letter code as enum)
    var fx1Val: Int = 0,         // FX value 1 (0x00-0xFF)
    var fx2Cmd: Int = 0,         // FX command 2
    var fx2Val: Int = 0,         // FX value 2
    var fx3Cmd: Int = 0,         // FX command 3
    var fx3Val: Int = 0,         // FX value 3
)

/** A phrase is 16 steps of note data */
class Phrase {
    val steps = Array(16) { PhraseStep() }

    fun isEmpty(): Boolean = steps.all { it.note == 0xFF }
}

/** A single row in a chain */
data class ChainRow(
    var phrase: Int = 0xFF,      // 0xFF = empty, 0-254 = phrase index
    var transpose: Int = 0,      // -128 to +127 semitones
)

/** A chain is up to 16 rows of phrase references */
class Chain {
    val rows = Array(16) { ChainRow() }

    fun isEmpty(): Boolean = rows.all { it.phrase == 0xFF }
}

/** A single row in a table (per-tick modulation sequencer) */
data class TableRow(
    var transpose: Int = 0,      // Semitone offset
    var volume: Int = 0xFF,      // 0xFF = no change
    var fx1Cmd: Int = 0,
    var fx1Val: Int = 0,
    var fx2Cmd: Int = 0,
    var fx2Val: Int = 0,
    var fx3Cmd: Int = 0,
    var fx3Val: Int = 0,
)

/** A table is 16 rows of per-tick modulation */
class Table {
    val rows = Array(16) { TableRow() }
}

/** Groove: per-step tick counts (default 6 ticks per step = straight time) */
class Groove {
    val ticks = IntArray(16) { 6 }  // Default: 6 ticks per step (straight)
}

/** Scale: 12 semitone enable flags + key offset */
data class Scale(
    var name: String = "CHROMATIC",
    var key: Int = 0,            // Root note offset (0=C)
    val intervals: BooleanArray = BooleanArray(12) { true },  // Which semitones are enabled
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Scale) return false
        return name == other.name && key == other.key && intervals.contentEquals(other.intervals)
    }
    override fun hashCode(): Int = name.hashCode() * 31 + key + intervals.contentHashCode()
}

/** Effects settings (global send effects) */
data class ChorusSettings(
    var modDepth: Int = 0x40,
    var modFreq: Int = 0x40,
    var width: Int = 0xFF,
    var reverbSend: Int = 0x00,
)

data class DelaySettings(
    var filterHP: Int = 0x40,
    var filterLP: Int = 0x80,
    var timeL: Int = 0x30,       // Left delay time
    var timeR: Int = 0x30,       // Right delay time
    var feedback: Int = 0x80,
    var width: Int = 0xFF,
    var reverbSend: Int = 0x00,
)

data class ReverbSettings(
    var filterHP: Int = 0x20,
    var filterLP: Int = 0xE0,
    var size: Int = 0xE0,
    var damping: Int = 0x80,
    var modDepth: Int = 0x10,
    var modFreq: Int = 0x30,
    var width: Int = 0xFF,
)

/** Mixer settings */
class MixerSettings {
    val trackVolumes = IntArray(8) { 0xE0 }      // Per-track volume (0-255)
    val trackPans = IntArray(8) { 0x80 }          // Per-track pan (0=L, 0x80=C, 0xFF=R)
    val trackChorusSend = IntArray(8) { 0x00 }    // Per-track chorus send
    val trackDelaySend = IntArray(8) { 0x00 }     // Per-track delay send
    val trackReverbSend = IntArray(8) { 0x00 }    // Per-track reverb send
    var masterVolume: Int = 0xE0
    var djFilter: Int = 0x80                       // 0x00=full LP, 0x80=off, 0xFF=full HP
    var chorusVolume: Int = 0xE0
    var delayVolume: Int = 0xE0
    var reverbVolume: Int = 0xE0
}

/**
 * Complete M8 Song containing all data.
 */
class M8Song {
    // Song grid: 8 tracks × 256 rows of chain references (0xFF = empty)
    val songGrid = Array(256) { IntArray(8) { 0xFF } }

    // Phrase pool: 255 phrases
    val phrases = Array(255) { Phrase() }

    // Chain pool: 255 chains
    val chains = Array(255) { Chain() }

    // Instrument pool: 128 instruments (created separately)
    val instrumentIndices = IntArray(128) { 0xFF }  // Maps to instrument type

    // Table pool: 256 tables
    val tables = Array(256) { Table() }

    // Groove pool: 32 grooves
    val grooves = Array(32) { Groove() }

    // Scale definitions: 16 user scales
    val scales = Array(16) { i ->
        when (i) {
            0 -> Scale("CHROMATIC")
            1 -> Scale("MAJOR", intervals = booleanArrayOf(true,false,true,false,true,true,false,true,false,true,false,true))
            2 -> Scale("MINOR", intervals = booleanArrayOf(true,false,true,true,false,true,false,true,true,false,true,false))
            3 -> Scale("PENTATONIC", intervals = booleanArrayOf(true,false,true,false,true,false,false,true,false,true,false,false))
            4 -> Scale("BLUES", intervals = booleanArrayOf(true,false,false,true,false,true,true,true,false,false,true,false))
            5 -> Scale("DORIAN", intervals = booleanArrayOf(true,false,true,true,false,true,false,true,false,true,true,false))
            6 -> Scale("PHRYGIAN", intervals = booleanArrayOf(true,true,false,true,false,true,false,true,true,false,true,false))
            7 -> Scale("MIXOLYDIAN", intervals = booleanArrayOf(true,false,true,false,true,true,false,true,false,true,true,false))
            else -> Scale("CHROMATIC")
        }
    }

    // Global settings
    var tempo: Int = 120
    var transpose: Int = 0
    var activeScale: Int = 0
    var quantize: Int = 0        // Live quantize steps
    var name: String = "NEW SONG"

    // Effects
    var chorus = ChorusSettings()
    var delay = DelaySettings()
    var reverb = ReverbSettings()
    var mixer = MixerSettings()

    companion object {
        const val EMPTY = 0xFF
        const val NOTE_OFF = 0x00

        val NOTE_NAMES = arrayOf("C-", "C#", "D-", "D#", "E-", "F-", "F#", "G-", "G#", "A-", "A#", "B-")

        fun noteName(note: Int): String {
            if (note == EMPTY) return "---"
            if (note == NOTE_OFF) return "OFF"
            val n = (note - 1) % 12
            val oct = (note - 1) / 12
            return "${NOTE_NAMES[n]}$oct"
        }

        fun hex2(v: Int): String = String.format("%02X", v and 0xFF)
    }

    private fun resetContents() {
        for (row in songGrid) java.util.Arrays.fill(row, EMPTY)
        for (chain in chains) chain.rows.forEach { row ->
            row.phrase = EMPTY
            row.transpose = 0
        }
        for (phrase in phrases) phrase.steps.forEach { step ->
            step.note = EMPTY
            step.instrument = EMPTY
            step.volume = EMPTY
            step.fx1Cmd = 0
            step.fx1Val = 0
            step.fx2Cmd = 0
            step.fx2Val = 0
            step.fx3Cmd = 0
            step.fx3Val = 0
        }
        for (table in tables) table.rows.forEach { row ->
            row.transpose = 0
            row.volume = EMPTY
            row.fx1Cmd = 0
            row.fx1Val = 0
            row.fx2Cmd = 0
            row.fx2Val = 0
            row.fx3Cmd = 0
            row.fx3Val = 0
        }
        for (groove in grooves) java.util.Arrays.fill(groove.ticks, 6)
        java.util.Arrays.fill(instrumentIndices, EMPTY)
        resetScales()
        chorus = ChorusSettings()
        delay = DelaySettings()
        reverb = ReverbSettings()
        mixer = MixerSettings()
        transpose = 0
        activeScale = 0
        quantize = 0
    }

    private fun resetScales() {
        val defaults = arrayOf(
            "CHROMATIC" to BooleanArray(12) { true },
            "MAJOR" to booleanArrayOf(true, false, true, false, true, true, false, true, false, true, false, true),
            "MINOR" to booleanArrayOf(true, false, true, true, false, true, false, true, true, false, true, false),
            "PENTATONIC" to booleanArrayOf(true, false, true, false, true, false, false, true, false, true, false, false),
            "BLUES" to booleanArrayOf(true, false, false, true, false, true, true, true, false, false, true, false),
            "DORIAN" to booleanArrayOf(true, false, true, true, false, true, false, true, false, true, true, false),
            "PHRYGIAN" to booleanArrayOf(true, true, false, true, false, true, false, true, true, false, true, false),
            "MIXOLYDIAN" to booleanArrayOf(true, false, true, false, true, true, false, true, false, true, true, false),
        )
        for (i in scales.indices) {
            val (name, intervals) = defaults.getOrElse(i) { "CHROMATIC" to BooleanArray(12) { true } }
            scales[i].name = name
            scales[i].key = 0
            for (j in scales[i].intervals.indices) scales[i].intervals[j] = intervals[j]
        }
    }

    /**
     * Startup demo: a compact M8-style techno sketch laid out as four groups:
     * A drums, B bassline, C piano/pad chords, D melody. Old full demo remains
     * available through [loadOldDemoSong] and seeded as "Old Demo" in Projects.
     */
    fun loadDemoSong() {
        resetContents()
        name = "NEON GRID"
        tempo = 128
        activeScale = 2 // minor

        val EE = EMPTY
        val OFF = NOTE_OFF
        val C2 = 24; val C3 = 36; val Eb3 = 39; val G3 = 43; val Bb3 = 46
        val C4 = 48; val Eb4 = 51; val G4 = 55; val Bb4 = 58
        val C5 = 60; val D5 = 62; val Eb5 = 63; val F5 = 65; val G5 = 67; val Bb5 = 70

        fun PhraseStep.set(n: Int, inst: Int, vol: Int,
                           f1c: Int = 0, f1v: Int = 0,
                           f2c: Int = 0, f2v: Int = 0) {
            note = n; instrument = inst; volume = vol
            fx1Cmd = f1c; fx1Val = f1v; fx2Cmd = f2c; fx2Val = f2v
        }

        // A — drum beat: four-on-the-floor noise kick/hat groove on instrument 3.
        phrases[0x00].apply {
            steps[ 0].set(C2,  3, 0x78, M8FxEngine.FX_CUT, 0x18)
            steps[ 2].set(G4,  3, 0x38, M8FxEngine.FX_CUT, 0x70)
            steps[ 4].set(C2,  3, 0x74, M8FxEngine.FX_CUT, 0x18)
            steps[ 6].set(G4,  3, 0x46, M8FxEngine.FX_CUT, 0x70)
            steps[ 8].set(C2,  3, 0x78, M8FxEngine.FX_CUT, 0x18)
            steps[10].set(G4,  3, 0x38, M8FxEngine.FX_CUT, 0x70)
            steps[12].set(C2,  3, 0x76, M8FxEngine.FX_CUT, 0x18)
            steps[14].set(G4,  3, 0x50, M8FxEngine.FX_CUT, 0x70)
        }
        phrases[0x01].apply {
            steps[ 0].set(C2, 3, 0x78, M8FxEngine.FX_CUT, 0x18)
            steps[ 2].set(G4, 3, 0x3C, M8FxEngine.FX_CUT, 0x70)
            steps[ 3].set(G4, 3, 0x28, M8FxEngine.FX_CUT, 0x70)
            steps[ 4].set(C2, 3, 0x74, M8FxEngine.FX_CUT, 0x18)
            steps[ 6].set(G4, 3, 0x4A, M8FxEngine.FX_CUT, 0x70)
            steps[ 8].set(C2, 3, 0x78, M8FxEngine.FX_CUT, 0x18)
            steps[10].set(G4, 3, 0x3C, M8FxEngine.FX_CUT, 0x70)
            steps[12].set(C2, 3, 0x78, M8FxEngine.FX_CUT, 0x18)
            steps[13].set(G4, 3, 0x30, M8FxEngine.FX_CUT, 0x70)
            steps[14].set(G4, 3, 0x52, M8FxEngine.FX_CUT, 0x70)
            steps[15].set(G4, 3, 0x34, M8FxEngine.FX_CUT, 0x70)
        }

        // B — bassline: driving C minor saw pattern, with table automation on the first hit.
        phrases[0x02].apply {
            steps[ 0].set(C3,  1, 0x70, M8FxEngine.FX_TBL, 0x00, M8FxEngine.FX_TIC, 0x01)
            steps[ 1].set(OFF, 1, EE)
            steps[ 3].set(C3,  1, 0x58)
            steps[ 4].set(OFF, 1, EE)
            steps[ 6].set(Bb3, 1, 0x60)
            steps[ 7].set(OFF, 1, EE)
            steps[ 8].set(C3,  1, 0x70)
            steps[10].set(Eb3, 1, 0x58)
            steps[11].set(OFF, 1, EE)
            steps[12].set(G3,  1, 0x62)
            steps[14].set(Bb3, 1, 0x58)
            steps[15].set(OFF, 1, EE)
        }
        phrases[0x03].apply {
            steps[ 0].set(C3,  1, 0x70)
            steps[ 2].set(C4,  1, 0x52, M8FxEngine.FX_PSL, 0x90)
            steps[ 3].set(OFF, 1, EE)
            steps[ 4].set(Eb3, 1, 0x64)
            steps[ 6].set(G3,  1, 0x58)
            steps[ 7].set(OFF, 1, EE)
            steps[ 8].set(Bb3, 1, 0x68)
            steps[10].set(G3,  1, 0x58)
            steps[12].set(Eb3, 1, 0x62)
            steps[14].set(C3,  1, 0x70, M8FxEngine.FX_RET, 0x24)
            steps[15].set(OFF, 1, EE)
        }

        // C — piano-style chords: C minor / Bb / Eb / G movement on pad voice.
        phrases[0x04].apply {
            steps[ 0].set(C4,  2, 0x62, M8FxEngine.FX_ARP, 0x37)
            steps[ 4].set(Bb3, 2, 0x5A, M8FxEngine.FX_ARP, 0x37)
            steps[ 8].set(Eb4, 2, 0x60, M8FxEngine.FX_ARP, 0x47)
            steps[12].set(G3,  2, 0x58, M8FxEngine.FX_ARP, 0x37)
        }
        phrases[0x05].apply {
            steps[ 0].set(C4,  2, 0x58, M8FxEngine.FX_ARP, 0x37)
            steps[ 6].set(Eb4, 2, 0x54, M8FxEngine.FX_ARP, 0x47)
            steps[ 8].set(Bb3, 2, 0x58, M8FxEngine.FX_ARP, 0x37)
            steps[12].set(G4,  2, 0x5A, M8FxEngine.FX_ARP, 0x35)
        }

        // D — melody: bright pluck hook answering the bass.
        phrases[0x06].apply {
            steps[ 0].set(C5,  5, 0x60)
            steps[ 2].set(Eb5, 5, 0x54)
            steps[ 4].set(G5,  5, 0x62)
            steps[ 7].set(F5,  5, 0x4C)
            steps[ 8].set(Eb5, 5, 0x58)
            steps[10].set(D5,  5, 0x50)
            steps[12].set(C5,  5, 0x64, M8FxEngine.FX_PVB, 0x24)
            steps[15].set(OFF, 5, EE)
        }
        phrases[0x07].apply {
            steps[ 0].set(G5,  5, 0x62)
            steps[ 1].set(Bb5, 5, 0x54)
            steps[ 3].set(G5,  5, 0x50)
            steps[ 4].set(Eb5, 5, 0x5A)
            steps[ 6].set(F5,  5, 0x50)
            steps[ 8].set(G5,  5, 0x64, M8FxEngine.FX_PVB, 0x24, M8FxEngine.FX_PBN, 0x90)
            steps[10].set(Eb5, 5, 0x52)
            steps[12].set(C5,  5, 0x62)
            steps[14].set(OFF, 5, EE)
        }

        chains[0x00].apply { rows[0] = ChainRow(0x00, 0); rows[1] = ChainRow(0x01, 0) }
        chains[0x01].apply { rows[0] = ChainRow(0x00, 0); rows[1] = ChainRow(0x00, 0) }
        chains[0x02].apply { rows[0] = ChainRow(0x02, 0); rows[1] = ChainRow(0x03, 0) }
        chains[0x03].apply { rows[0] = ChainRow(0x02, 0); rows[1] = ChainRow(0x02, 0) }
        chains[0x04].apply { rows[0] = ChainRow(0x04, 0); rows[1] = ChainRow(0x05, 0) }
        chains[0x05].apply { rows[0] = ChainRow(0x04, 0); rows[1] = ChainRow(0x04, 0) }
        chains[0x06].apply { rows[0] = ChainRow(0x06, 0); rows[1] = ChainRow(0x07, 0) }
        chains[0x07].apply { rows[0] = ChainRow(0x06, 0); rows[1] = ChainRow(0x06, 0) }

        // Table 00 — bass motion: octave pops, volume gates, pan wiggle, and delay throws.
        tables[0x00].apply {
            rows[0] = TableRow(transpose = 0,  volume = 0x70, fx1Cmd = M8FxEngine.FX_PAN, fx1Val = 0x80)
            rows[1] = TableRow(transpose = 12, volume = 0x54, fx1Cmd = M8FxEngine.FX_PAN, fx1Val = 0x40)
            rows[2] = TableRow(transpose = 0,  volume = 0x64, fx1Cmd = M8FxEngine.FX_PAN, fx1Val = 0xC0)
            rows[3] = TableRow(transpose = 7,  volume = 0x48, fx1Cmd = M8FxEngine.FX_PAN, fx1Val = 0x80, fx2Cmd = M8FxEngine.FX_SDL, fx2Val = 0x48)
            rows[4] = TableRow(transpose = 0,  volume = 0x70, fx1Cmd = M8FxEngine.FX_PAN, fx1Val = 0x80)
            rows[5] = TableRow(transpose = -12, volume = 0x40, fx1Cmd = M8FxEngine.FX_PAN, fx1Val = 0x60)
            rows[6] = TableRow(transpose = 0,  volume = 0x60, fx1Cmd = M8FxEngine.FX_PAN, fx1Val = 0xA0)
            rows[7] = TableRow(transpose = 12, volume = 0x50, fx1Cmd = M8FxEngine.FX_PAN, fx1Val = 0x80, fx2Cmd = M8FxEngine.FX_SDL, fx2Val = 0x58)
        }

        // 8-row mini-arrangement. Columns A-D map to drums, bass, chords, melody.
        songGrid[0] = intArrayOf(0x00, 0x02, 0x04, 0x06, EE, EE, EE, EE)
        songGrid[1] = intArrayOf(0x00, 0x02, 0x04, 0x07, EE, EE, EE, EE)
        songGrid[2] = intArrayOf(0x00, 0x03, 0x04, 0x06, EE, EE, EE, EE)
        songGrid[3] = intArrayOf(0x00, 0x02, 0x05, 0x07, EE, EE, EE, EE)
        songGrid[4] = intArrayOf(0x01, 0x02, 0x04, 0x06, EE, EE, EE, EE)
        songGrid[5] = intArrayOf(0x01, 0x03, 0x05, 0x07, EE, EE, EE, EE)
        songGrid[6] = intArrayOf(0x00, 0x02, 0x04, 0x06, EE, EE, EE, EE)
        songGrid[7] = intArrayOf(0x01, 0x02, 0x04, 0x07, EE, EE, EE, EE)

        mixer.apply {
            trackVolumes[0] = 0xD0 // A drums
            trackVolumes[1] = 0xE4 // B bass
            trackVolumes[2] = 0xA8 // C chords
            trackVolumes[3] = 0xC8 // D melody
            trackPans[0] = 0x80
            trackPans[1] = 0x80
            trackPans[2] = 0xA0
            trackPans[3] = 0x60
            trackDelaySend[3] = 0x30
            trackReverbSend[2] = 0x40
            trackReverbSend[3] = 0x28
            masterVolume = 0xE0
        }
        delay = DelaySettings(timeL = 0x30, timeR = 0x38, feedback = 0x64, reverbSend = 0x18)
        reverb = ReverbSettings(size = 0xB8, damping = 0x80, width = 0xFF)
    }

    /**
     * Create a demo song: dark electronic track in C minor at 118 BPM.
     * 8-section arrangement showcasing all 8 instrument tracks with
     * dynamic builds, breakdowns, and full-energy drops.
     */
    fun loadOldDemoSong() {
        resetContents()
        name = "NIGHTCIRCUIT"
        tempo = 118

        // =====================================================================
        // C minor note constants for readability
        // =====================================================================
        val C3 = 36; val Eb3 = 39; val F3 = 41; val G3 = 43; val Ab3 = 44; val Bb3 = 46
        val C4 = 48; val Eb4 = 51; val F4 = 53; val G4 = 55; val Ab4 = 56; val Bb4 = 58
        val C5 = 60; val D5 = 62; val Eb5 = 63; val F5 = 65; val G5 = 67; val Ab5 = 68
        val Bb5 = 70; val C6 = 72; val Eb6 = 75; val G6 = 79
        val OFF = NOTE_OFF
        val EE = EMPTY   // shorthand for empty (0xFF)

        // Helper to set a phrase step concisely
        fun PhraseStep.set(n: Int, inst: Int, vol: Int,
                           f1c: Int = 0, f1v: Int = 0,
                           f2c: Int = 0, f2v: Int = 0) {
            note = n; instrument = inst; volume = vol
            fx1Cmd = f1c; fx1Val = f1v; fx2Cmd = f2c; fx2Val = f2v
        }

        // =====================================================================
        // PHRASES — Track 0: Lead (WAVSYNTH pulse, inst 0)
        // =====================================================================

        // Phrase 0x00: Main lead melody A — singable Cm theme
        phrases[0x00].apply {
            //       C5   .  Eb5  .   G5  .  Bb5  Ab5  G5   .  Eb5  .   F5  .   Eb5 OFF
            steps[ 0].set(C5,  0, 0x60, M8FxEngine.FX_PVB, 0x24)
            steps[ 1].set(EE,  EE, EE)
            steps[ 2].set(Eb5, 0, 0x58)
            steps[ 3].set(EE,  EE, EE)
            steps[ 4].set(G5,  0, 0x65)
            steps[ 5].set(EE,  EE, EE)
            steps[ 6].set(Bb5, 0, 0x60)
            steps[ 7].set(Ab5, 0, 0x50)
            steps[ 8].set(G5,  0, 0x68, M8FxEngine.FX_PVB, 0x34)
            steps[ 9].set(EE,  EE, EE)
            steps[10].set(Eb5, 0, 0x55)
            steps[11].set(EE,  EE, EE)
            steps[12].set(F5,  0, 0x5A)
            steps[13].set(EE,  EE, EE)
            steps[14].set(Eb5, 0, 0x48)
            steps[15].set(OFF, 0, EE)
        }

        // Phrase 0x01: Lead melody B — answering phrase, descending
        phrases[0x01].apply {
            steps[ 0].set(G5,  0, 0x60, M8FxEngine.FX_PVB, 0x24)
            steps[ 1].set(Ab5, 0, 0x55)
            steps[ 2].set(G5,  0, 0x5C)
            steps[ 3].set(EE,  EE, EE)
            steps[ 4].set(F5,  0, 0x60)
            steps[ 5].set(Eb5, 0, 0x55)
            steps[ 6].set(EE,  EE, EE)
            steps[ 7].set(D5,  0, 0x50)
            steps[ 8].set(C5,  0, 0x68, M8FxEngine.FX_PVB, 0x34)
            steps[ 9].set(EE,  EE, EE)
            steps[10].set(EE,  EE, EE)
            steps[11].set(EE,  EE, EE)
            steps[12].set(Eb5, 0, 0x48)
            steps[13].set(C5,  0, 0x40)
            steps[14].set(OFF, 0, EE)
            steps[15].set(EE,  EE, EE)
        }

        // Phrase 0x02: Lead melody C — variation for drop 2, higher energy
        phrases[0x02].apply {
            steps[ 0].set(C6,  0, 0x68, M8FxEngine.FX_PVB, 0x24)
            steps[ 1].set(EE,  EE, EE)
            steps[ 2].set(Bb5, 0, 0x60)
            steps[ 3].set(G5,  0, 0x55)
            steps[ 4].set(Ab5, 0, 0x65)
            steps[ 5].set(EE,  EE, EE)
            steps[ 6].set(G5,  0, 0x5C)
            steps[ 7].set(F5,  0, 0x50)
            steps[ 8].set(Eb5, 0, 0x68, M8FxEngine.FX_PVB, 0x34)
            steps[ 9].set(F5,  0, 0x55)
            steps[10].set(G5,  0, 0x60)
            steps[11].set(EE,  EE, EE)
            steps[12].set(Ab5, 0, 0x65)
            steps[13].set(G5,  0, 0x50)
            steps[14].set(F5,  0, 0x48)
            steps[15].set(OFF, 0, EE)
        }

        // Phrase 0x03: Lead melody D — answering phrase for drop 2
        phrases[0x03].apply {
            steps[ 0].set(Eb5, 0, 0x60)
            steps[ 1].set(EE,  EE, EE)
            steps[ 2].set(F5,  0, 0x58)
            steps[ 3].set(G5,  0, 0x55)
            steps[ 4].set(Ab5, 0, 0x65, M8FxEngine.FX_PVB, 0x44)
            steps[ 5].set(EE,  EE, EE)
            steps[ 6].set(EE,  EE, EE)
            steps[ 7].set(EE,  EE, EE)
            steps[ 8].set(G5,  0, 0x60)
            steps[ 9].set(F5,  0, 0x50)
            steps[10].set(Eb5, 0, 0x58)
            steps[11].set(D5,  0, 0x48)
            steps[12].set(C5,  0, 0x65, M8FxEngine.FX_PVB, 0x34)
            steps[13].set(EE,  EE, EE)
            steps[14].set(EE,  EE, EE)
            steps[15].set(OFF, 0, EE)
        }

        // =====================================================================
        // PHRASES — Track 1: Bass (WAVSYNTH saw, inst 1)
        // =====================================================================

        // Phrase 0x04: Bass A — syncopated Cm groove with octave jumps
        phrases[0x04].apply {
            steps[ 0].set(C3,  1, 0x70)
            steps[ 1].set(OFF, 1, EE)
            steps[ 2].set(EE,  EE, EE)
            steps[ 3].set(C4,  1, 0x50)
            steps[ 4].set(OFF, 1, EE)
            steps[ 5].set(C3,  1, 0x68)
            steps[ 6].set(OFF, 1, EE)
            steps[ 7].set(EE,  EE, EE)
            steps[ 8].set(Ab3, 1, 0x65)
            steps[ 9].set(OFF, 1, EE)
            steps[10].set(EE,  EE, EE)
            steps[11].set(Ab3, 1, 0x50)
            steps[12].set(Bb3, 1, 0x6A)
            steps[13].set(OFF, 1, EE)
            steps[14].set(Bb3, 1, 0x48)
            steps[15].set(OFF, 1, EE)
        }

        // Phrase 0x05: Bass B — Eb to G movement
        phrases[0x05].apply {
            steps[ 0].set(Eb3, 1, 0x70)
            steps[ 1].set(OFF, 1, EE)
            steps[ 2].set(EE,  EE, EE)
            steps[ 3].set(Eb4, 1, 0x50)
            steps[ 4].set(OFF, 1, EE)
            steps[ 5].set(Eb3, 1, 0x68)
            steps[ 6].set(OFF, 1, EE)
            steps[ 7].set(EE,  EE, EE)
            steps[ 8].set(G3,  1, 0x65)
            steps[ 9].set(OFF, 1, EE)
            steps[10].set(EE,  EE, EE)
            steps[11].set(G3,  1, 0x50)
            steps[12].set(F3,  1, 0x6A)
            steps[13].set(OFF, 1, EE)
            steps[14].set(G3,  1, 0x48)
            steps[15].set(OFF, 1, EE)
        }

        // Phrase 0x06: Bass build — retrigger escalation for build sections
        phrases[0x06].apply {
            steps[ 0].set(C3,  1, 0x60)
            steps[ 1].set(OFF, 1, EE)
            steps[ 2].set(C3,  1, 0x60)
            steps[ 3].set(OFF, 1, EE)
            steps[ 4].set(C3,  1, 0x62)
            steps[ 5].set(C3,  1, 0x62)
            steps[ 6].set(OFF, 1, EE)
            steps[ 7].set(C3,  1, 0x64)
            steps[ 8].set(C3,  1, 0x66, M8FxEngine.FX_RET, 0x34)
            steps[ 9].set(C3,  1, 0x68)
            steps[10].set(C3,  1, 0x6A, M8FxEngine.FX_RET, 0x24)
            steps[11].set(C3,  1, 0x6C)
            steps[12].set(C3,  1, 0x6E, M8FxEngine.FX_RET, 0x14)
            steps[13].set(C3,  1, 0x6E)
            steps[14].set(C3,  1, 0x70, M8FxEngine.FX_RET, 0x14)
            steps[15].set(C3,  1, 0x70)
        }

        // =====================================================================
        // PHRASES — Track 2: Pad (WAVSYNTH triangle, inst 2)
        // =====================================================================

        // Phrase 0x07: Pad Cm chord — sustained, slow filter sweep
        phrases[0x07].apply {
            steps[ 0].set(C4,  2, 0x50, M8FxEngine.FX_CUT, 0x40)
            steps[ 4].set(EE,  EE, EE, M8FxEngine.FX_CUT, 0x50)
            steps[ 8].set(EE,  EE, EE, M8FxEngine.FX_CUT, 0x60)
            steps[12].set(EE,  EE, EE, M8FxEngine.FX_CUT, 0x50)
        }

        // Phrase 0x08: Pad Ab chord (Ab-C-Eb) — transpose up 8 from Cm voicing
        phrases[0x08].apply {
            steps[ 0].set(Ab4, 2, 0x50, M8FxEngine.FX_CUT, 0x45)
            steps[ 4].set(EE,  EE, EE, M8FxEngine.FX_CUT, 0x55)
            steps[ 8].set(EE,  EE, EE, M8FxEngine.FX_CUT, 0x65)
            steps[12].set(EE,  EE, EE, M8FxEngine.FX_CUT, 0x55)
        }

        // Phrase 0x09: Pad Bb (Bb-D-F) — for variation
        phrases[0x09].apply {
            steps[ 0].set(Bb4, 2, 0x48, M8FxEngine.FX_CUT, 0x48)
            steps[ 4].set(EE,  EE, EE, M8FxEngine.FX_CUT, 0x58)
            steps[ 8].set(EE,  EE, EE, M8FxEngine.FX_CUT, 0x68)
            steps[12].set(EE,  EE, EE, M8FxEngine.FX_CUT, 0x58)
        }

        // Phrase 0x0A: Pad Eb (Eb-G-Bb) — for outro fade
        phrases[0x0A].apply {
            steps[ 0].set(Eb4, 2, 0x45, M8FxEngine.FX_CUT, 0x60)
            steps[ 8].set(EE,  EE, EE, M8FxEngine.FX_CUT, 0x40)
            steps[14].set(EE,  EE, EE, M8FxEngine.FX_CUT, 0x20)
        }

        // =====================================================================
        // PHRASES — Track 3: Hi-hat (noise, inst 3)
        // =====================================================================

        // Phrase 0x0B: Hi-hat main groove — accents on offbeats, ghost notes
        phrases[0x0B].apply {
            steps[ 0].set(80, 3, 0x60)  // Downbeat accent
            steps[ 1].set(80, 3, 0x30)  // Ghost note
            steps[ 2].set(80, 3, 0x55)  // Offbeat
            steps[ 3].set(80, 3, 0x32)  // Ghost
            steps[ 4].set(80, 3, 0x68)  // Strong accent
            steps[ 5].set(80, 3, 0x30)  // Ghost
            steps[ 6].set(80, 3, 0x50)  // Offbeat
            steps[ 7].set(80, 3, 0x38)  // Ghost
            steps[ 8].set(80, 3, 0x60)  // Downbeat
            steps[ 9].set(80, 3, 0x30)  // Ghost
            steps[10].set(80, 3, 0x55)  // Offbeat
            steps[11].set(80, 3, 0x35)  // Ghost
            steps[12].set(80, 3, 0x65)  // Accent
            steps[13].set(80, 3, 0x38)  // Ghost
            steps[14].set(80, 3, 0x50)  // Offbeat
            steps[15].set(80, 3, 0x30)  // Ghost
        }

        // Phrase 0x0C: Hi-hat intro — sparse, just offbeats
        phrases[0x0C].apply {
            steps[ 2].set(80, 3, 0x38)
            steps[ 6].set(80, 3, 0x40)
            steps[10].set(80, 3, 0x38)
            steps[14].set(80, 3, 0x40)
        }

        // Phrase 0x0D: Hi-hat build — retrigger acceleration
        phrases[0x0D].apply {
            steps[ 0].set(80, 3, 0x50)
            steps[ 2].set(80, 3, 0x50)
            steps[ 4].set(80, 3, 0x55)
            steps[ 5].set(80, 3, 0x55)
            steps[ 6].set(80, 3, 0x58)
            steps[ 7].set(80, 3, 0x58)
            steps[ 8].set(80, 3, 0x5A, M8FxEngine.FX_RET, 0x34)
            steps[10].set(80, 3, 0x5C, M8FxEngine.FX_RET, 0x34)
            steps[12].set(80, 3, 0x60, M8FxEngine.FX_RET, 0x24)
            steps[14].set(80, 3, 0x65, M8FxEngine.FX_RET, 0x14)
        }

        // =====================================================================
        // PHRASES — Track 4: FM Bell (FM synth, inst 4)
        // =====================================================================

        // Phrase 0x0E: Bell accents — sparse melodic hits
        phrases[0x0E].apply {
            steps[ 0].set(C6,  4, 0x50)
            steps[ 7].set(G5,  4, 0x40)
            steps[12].set(Eb5, 4, 0x48)
        }

        // Phrase 0x0F: Bell variation — different accents
        phrases[0x0F].apply {
            steps[ 4].set(Ab5, 4, 0x45)
            steps[ 8].set(Bb5, 4, 0x50)
            steps[14].set(G6,  4, 0x3A)
        }

        // Phrase 0x10: Bell breakdown — ethereal, spaced
        phrases[0x10].apply {
            steps[ 0].set(C6,  4, 0x48, M8FxEngine.FX_CUT, 0x70)
            steps[ 8].set(G6,  4, 0x38, M8FxEngine.FX_CUT, 0x80)
        }

        // =====================================================================
        // PHRASES — Track 5: Pluck (pulse, inst 5)
        // =====================================================================

        // Phrase 0x11: Pluck arp Cm — outlines C minor triad
        phrases[0x11].apply {
            steps[ 0].set(C5,  5, 0x55, M8FxEngine.FX_ARP, 0x37)
            steps[ 2].set(Eb5, 5, 0x48)
            steps[ 4].set(G5,  5, 0x50)
            steps[ 6].set(C6,  5, 0x45)
            steps[ 8].set(G5,  5, 0x50, M8FxEngine.FX_ARP, 0x37)
            steps[10].set(Eb5, 5, 0x48)
            steps[12].set(C5,  5, 0x55)
            steps[14].set(G4,  5, 0x42)
        }

        // Phrase 0x12: Pluck arp Ab — outlines Ab major triad
        phrases[0x12].apply {
            steps[ 0].set(Ab4, 5, 0x55, M8FxEngine.FX_ARP, 0x37)
            steps[ 2].set(C5,  5, 0x48)
            steps[ 4].set(Eb5, 5, 0x50)
            steps[ 6].set(Ab5, 5, 0x45)
            steps[ 8].set(Eb5, 5, 0x50, M8FxEngine.FX_ARP, 0x37)
            steps[10].set(C5,  5, 0x48)
            steps[12].set(Ab4, 5, 0x55)
            steps[14].set(Eb4, 5, 0x42)
        }

        // Phrase 0x13: Pluck delay — sparse notes into delay for breakdown
        phrases[0x13].apply {
            steps[ 0].set(C5,  5, 0x50)
            steps[ 4].set(G5,  5, 0x40)
            steps[12].set(Eb5, 5, 0x45)
        }

        // Phrase 0x14: Pluck build — rising energy
        phrases[0x14].apply {
            steps[ 0].set(C5,  5, 0x48)
            steps[ 2].set(Eb5, 5, 0x48)
            steps[ 4].set(G5,  5, 0x4C)
            steps[ 6].set(C6,  5, 0x4C)
            steps[ 8].set(C5,  5, 0x50, M8FxEngine.FX_ARP, 0x37)
            steps[ 9].set(Eb5, 5, 0x50)
            steps[10].set(G5,  5, 0x54)
            steps[11].set(C6,  5, 0x54)
            steps[12].set(C5,  5, 0x58, M8FxEngine.FX_RET, 0x24)
            steps[13].set(Eb5, 5, 0x58)
            steps[14].set(G5,  5, 0x5C)
            steps[15].set(C6,  5, 0x5C)
        }

        // =====================================================================
        // PHRASES — Track 6: Sub (sine, inst 6)
        // =====================================================================

        // Phrase 0x15: Sub bass — downbeats only, Cm root motion
        phrases[0x15].apply {
            steps[ 0].set(C3,  6, 0x70)
            steps[ 4].set(OFF, 6, EE)
            steps[ 8].set(Ab3, 6, 0x68)
            steps[12].set(OFF, 6, EE)
        }

        // Phrase 0x16: Sub bass — Eb-Bb root motion
        phrases[0x16].apply {
            steps[ 0].set(Eb3, 6, 0x70)
            steps[ 4].set(OFF, 6, EE)
            steps[ 8].set(Bb3, 6, 0x68)
            steps[12].set(OFF, 6, EE)
        }

        // Phrase 0x17: Sub bass — sustained C for drops
        phrases[0x17].apply {
            steps[ 0].set(C3,  6, 0x70)
            steps[ 8].set(OFF, 6, EE)
        }

        // =====================================================================
        // PHRASES — Track 7: FX (FM, inst 7)
        // =====================================================================

        // Phrase 0x18: FX riser — ascending pitch sweep for builds
        phrases[0x18].apply {
            steps[ 0].set(C4,  7, 0x30, M8FxEngine.FX_PBN, 0x90)
            steps[ 4].set(EE,  EE, EE, M8FxEngine.FX_PBN, 0x98)
            steps[ 8].set(EE,  EE, EE, M8FxEngine.FX_PBN, 0xA0)
            steps[12].set(EE,  EE, EE, M8FxEngine.FX_PBN, 0xA8)
        }

        // Phrase 0x19: FX riser part 2 — crescendo to drop
        phrases[0x19].apply {
            steps[ 0].set(EE,  EE, EE, M8FxEngine.FX_PBN, 0xB0, M8FxEngine.FX_VOL, 0x40)
            steps[ 4].set(EE,  EE, EE, M8FxEngine.FX_PBN, 0xB8, M8FxEngine.FX_VOL, 0x50)
            steps[ 8].set(EE,  EE, EE, M8FxEngine.FX_PBN, 0xC0, M8FxEngine.FX_VOL, 0x60)
            steps[12].set(EE,  EE, EE, M8FxEngine.FX_PBN, 0xC8, M8FxEngine.FX_VOL, 0x70)
            steps[15].set(OFF, 7, EE)
        }

        // Phrase 0x1A: FX impact — downlifter crash on drop
        phrases[0x1A].apply {
            steps[ 0].set(C6,  7, 0x70, M8FxEngine.FX_PBN, 0x60)
            steps[ 4].set(EE,  EE, EE, M8FxEngine.FX_PBN, 0x50)
            steps[ 8].set(EE,  EE, EE, M8FxEngine.FX_PBN, 0x40, M8FxEngine.FX_VOL, 0x40)
            steps[12].set(OFF, 7, EE)
        }

        // Phrase 0x1B: FX transition — quick sweep for section changes
        phrases[0x1B].apply {
            steps[ 0].set(G5,  7, 0x50, M8FxEngine.FX_PBN, 0x70)
            steps[ 4].set(OFF, 7, EE)
            steps[12].set(C5,  7, 0x40, M8FxEngine.FX_PBN, 0x90)
            steps[15].set(OFF, 7, EE)
        }

        // =====================================================================
        // CHAINS — Pairs of phrases per section for 32-row blocks
        // =====================================================================

        // Chain 0x00: Lead — drop 1 (melody A + B)
        chains[0x00].apply {
            rows[0] = ChainRow(0x00, 0)
            rows[1] = ChainRow(0x01, 0)
        }

        // Chain 0x01: Lead — drop 1 variation (melody B + A)
        chains[0x01].apply {
            rows[0] = ChainRow(0x01, 0)
            rows[1] = ChainRow(0x00, 0)
        }

        // Chain 0x02: Lead — drop 2 (melody C + D)
        chains[0x02].apply {
            rows[0] = ChainRow(0x02, 0)
            rows[1] = ChainRow(0x03, 0)
        }

        // Chain 0x03: Bass — main groove (A + B)
        chains[0x03].apply {
            rows[0] = ChainRow(0x04, 0)
            rows[1] = ChainRow(0x05, 0)
        }

        // Chain 0x04: Bass — build (groove A + build retrigger)
        chains[0x04].apply {
            rows[0] = ChainRow(0x04, 0)
            rows[1] = ChainRow(0x06, 0)
        }

        // Chain 0x05: Pad — Cm then Ab
        chains[0x05].apply {
            rows[0] = ChainRow(0x07, 0)
            rows[1] = ChainRow(0x08, 0)
        }

        // Chain 0x06: Pad — Bb then Eb (variation)
        chains[0x06].apply {
            rows[0] = ChainRow(0x09, 0)
            rows[1] = ChainRow(0x0A, 0)
        }

        // Chain 0x07: Pad — Cm sustained (intro/outro)
        chains[0x07].apply {
            rows[0] = ChainRow(0x07, 0)
            rows[1] = ChainRow(0x07, 0)
        }

        // Chain 0x08: Hi-hat — full groove x2
        chains[0x08].apply {
            rows[0] = ChainRow(0x0B, 0)
            rows[1] = ChainRow(0x0B, 0)
        }

        // Chain 0x09: Hi-hat — intro sparse x2
        chains[0x09].apply {
            rows[0] = ChainRow(0x0C, 0)
            rows[1] = ChainRow(0x0C, 0)
        }

        // Chain 0x0A: Hi-hat — build (sparse then retrigger)
        chains[0x0A].apply {
            rows[0] = ChainRow(0x0C, 0)
            rows[1] = ChainRow(0x0D, 0)
        }

        // Chain 0x0B: Bell — accents pair
        chains[0x0B].apply {
            rows[0] = ChainRow(0x0E, 0)
            rows[1] = ChainRow(0x0F, 0)
        }

        // Chain 0x0C: Bell — breakdown
        chains[0x0C].apply {
            rows[0] = ChainRow(0x10, 0)
            rows[1] = ChainRow(0x10, 0)
        }

        // Chain 0x0D: Pluck — Cm arp pair
        chains[0x0D].apply {
            rows[0] = ChainRow(0x11, 0)
            rows[1] = ChainRow(0x12, 0)
        }

        // Chain 0x0E: Pluck — delay plucks for breakdown
        chains[0x0E].apply {
            rows[0] = ChainRow(0x13, 0)
            rows[1] = ChainRow(0x13, 0)
        }

        // Chain 0x0F: Pluck — build arp
        chains[0x0F].apply {
            rows[0] = ChainRow(0x12, 0)
            rows[1] = ChainRow(0x14, 0)
        }

        // Chain 0x10: Sub — Cm-Ab pair
        chains[0x10].apply {
            rows[0] = ChainRow(0x15, 0)
            rows[1] = ChainRow(0x16, 0)
        }

        // Chain 0x11: Sub — sustained C for drops
        chains[0x11].apply {
            rows[0] = ChainRow(0x17, 0)
            rows[1] = ChainRow(0x15, 0)
        }

        // Chain 0x12: FX — riser (part 1 + part 2)
        chains[0x12].apply {
            rows[0] = ChainRow(0x18, 0)
            rows[1] = ChainRow(0x19, 0)
        }

        // Chain 0x13: FX — impact + transition
        chains[0x13].apply {
            rows[0] = ChainRow(0x1A, 0)
            rows[1] = ChainRow(0x1B, 0)
        }

        // Chain 0x14: Pad — outro fade (Eb then Cm dying)
        chains[0x14].apply {
            rows[0] = ChainRow(0x0A, 0)
            rows[1] = ChainRow(0x07, 0)
        }

        // Chain 0x15: Lead — outro (melody B fading)
        chains[0x15].apply {
            rows[0] = ChainRow(0x01, 0)
            rows[1] = ChainRow(0x03, 0)
        }

        // =====================================================================
        // SONG ARRANGEMENT — 8 rows x 8 tracks
        // Tracks: 0=Lead, 1=Bass, 2=Pad, 3=HiHat, 4=Bell, 5=Pluck, 6=Sub, 7=FX
        // =====================================================================

        // Row 0: Intro — sparse pad + subtle hi-hats, mood setting
        songGrid[0] = intArrayOf(EE, EE, 0x07, 0x09, EE, EE, EE, EE)

        // Row 1: Build — bass enters, pluck arps begin, energy rising
        songGrid[1] = intArrayOf(EE, 0x03, 0x05, 0x0A, EE, 0x0D, EE, 0x12)

        // Row 2: Drop 1 — full arrangement, lead melody enters
        songGrid[2] = intArrayOf(0x00, 0x03, 0x05, 0x08, 0x0B, 0x0D, 0x10, 0x13)

        // Row 3: Drop 1 variation — melodic variation, same energy
        songGrid[3] = intArrayOf(0x01, 0x03, 0x06, 0x08, 0x0B, 0x0D, 0x10, EE)

        // Row 4: Breakdown — stripped back, ethereal pad + bells + delay plucks
        songGrid[4] = intArrayOf(EE, EE, 0x05, EE, 0x0C, 0x0E, EE, 0x13)

        // Row 5: Build 2 — drums return, bass builds, FX riser
        songGrid[5] = intArrayOf(EE, 0x04, 0x05, 0x0A, EE, 0x0F, 0x11, 0x12)

        // Row 6: Drop 2 — full arrangement, alternative lead, more intense
        songGrid[6] = intArrayOf(0x02, 0x03, 0x05, 0x08, 0x0B, 0x0D, 0x10, 0x13)

        // Row 7: Outro — gradual fadeout, pad holds, elements drop out
        songGrid[7] = intArrayOf(0x15, EE, 0x14, 0x09, 0x0C, EE, EE, EE)

        // =====================================================================
        // GROOVE — slight swing for humanized feel
        // =====================================================================
        grooves[0].apply {
            ticks[ 0] = 6; ticks[ 1] = 6; ticks[ 2] = 7; ticks[ 3] = 5
            ticks[ 4] = 6; ticks[ 5] = 6; ticks[ 6] = 7; ticks[ 7] = 5
            ticks[ 8] = 6; ticks[ 9] = 6; ticks[10] = 7; ticks[11] = 5
            ticks[12] = 6; ticks[13] = 6; ticks[14] = 7; ticks[15] = 5
        }

        // =====================================================================
        // EFFECTS — global send effects tuned for dark electronic
        // =====================================================================
        chorus = ChorusSettings(
            modDepth = 0x30,
            modFreq = 0x28,
            width = 0xFF,
            reverbSend = 0x20
        )

        delay = DelaySettings(
            filterHP = 0x50,
            filterLP = 0x90,
            timeL = 0x38,
            timeR = 0x40,    // Slightly offset L/R for stereo width
            feedback = 0x70,
            width = 0xFF,
            reverbSend = 0x18
        )

        reverb = ReverbSettings(
            filterHP = 0x28,
            filterLP = 0xC0,
            size = 0xD0,
            damping = 0x70,
            modDepth = 0x18,
            modFreq = 0x20,
            width = 0xFF
        )

        // =====================================================================
        // MIXER — stereo spread with send effect routing
        // =====================================================================
        mixer.apply {
            // Track volumes
            trackVolumes[0] = 0xD0  // Lead
            trackVolumes[1] = 0xE0  // Bass
            trackVolumes[2] = 0x90  // Pad
            trackVolumes[3] = 0xB0  // Hi-hat
            trackVolumes[4] = 0xA0  // Bell
            trackVolumes[5] = 0xC0  // Pluck
            trackVolumes[6] = 0xE0  // Sub
            trackVolumes[7] = 0x80  // FX

            // Stereo panning spread
            trackPans[0] = 0x60  // Lead slightly left
            trackPans[1] = 0x80  // Bass center
            trackPans[2] = 0xA0  // Pad slightly right
            trackPans[3] = 0x50  // Hi-hat left of center
            trackPans[4] = 0xB0  // Bell right
            trackPans[5] = 0x30  // Pluck left
            trackPans[6] = 0x80  // Sub center
            trackPans[7] = 0x80  // FX center

            // Chorus sends — pad and pluck for width
            trackChorusSend[0] = 0x00
            trackChorusSend[1] = 0x00
            trackChorusSend[2] = 0x60  // Pad heavy chorus
            trackChorusSend[3] = 0x00
            trackChorusSend[4] = 0x20  // Bell light chorus
            trackChorusSend[5] = 0x30  // Pluck moderate chorus
            trackChorusSend[6] = 0x00
            trackChorusSend[7] = 0x00

            // Delay sends — lead, bell, pluck for space
            trackDelaySend[0] = 0x20  // Lead light delay
            trackDelaySend[1] = 0x00
            trackDelaySend[2] = 0x00
            trackDelaySend[3] = 0x00
            trackDelaySend[4] = 0x30  // Bell moderate delay
            trackDelaySend[5] = 0x50  // Pluck heavy delay
            trackDelaySend[6] = 0x00
            trackDelaySend[7] = 0x10  // FX light delay

            // Reverb sends — atmosphere
            trackReverbSend[0] = 0x30  // Lead moderate reverb
            trackReverbSend[1] = 0x10  // Bass minimal reverb
            trackReverbSend[2] = 0x70  // Pad heavy reverb
            trackReverbSend[3] = 0x10  // Hi-hat minimal reverb
            trackReverbSend[4] = 0x50  // Bell heavy reverb
            trackReverbSend[5] = 0x20  // Pluck light reverb
            trackReverbSend[6] = 0x10  // Sub minimal reverb
            trackReverbSend[7] = 0x30  // FX moderate reverb

            masterVolume = 0xE0
            chorusVolume = 0xD0
            delayVolume = 0xD0
            reverbVolume = 0xE0
        }
    }
}
