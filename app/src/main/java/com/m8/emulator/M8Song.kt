package com.m8.emulator

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

    /**
     * Create a demo song with musical content across multiple phrases and chains.
     */
    fun loadDemoSong() {
        name = "DEMO SONG"
        tempo = 112

        // --- Phrase 0x00: Lead melody (C minor) ---
        phrases[0].apply {
            val notes = intArrayOf(60, 0xFF, 63, 0xFF, 67, 0xFF, 70, 63, 72, 0xFF, 70, 67, 63, 0xFF, 60, 0xFF)
            for (row in 0 until 16) {
                steps[row].note = notes[row]
                if (notes[row] != 0xFF) {
                    steps[row].instrument = 0
                    steps[row].volume = 0x60
                }
            }
        }

        // --- Phrase 0x01: Bass line ---
        phrases[1].apply {
            val notes = intArrayOf(36, 0xFF, 36, 0xFF, 36, 0xFF, 36, 48, 39, 0xFF, 39, 0xFF, 43, 0xFF, 43, 0xFF)
            for (row in 0 until 16) {
                steps[row].note = notes[row]
                if (notes[row] != 0xFF) {
                    steps[row].instrument = 1
                    steps[row].volume = 0x60
                }
            }
        }

        // --- Phrase 0x02: Pad/chord ---
        phrases[2].apply {
            val notes = intArrayOf(60, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 63, 0xFF, 0xFF, 0xFF, 67, 0xFF, 0xFF, 0xFF)
            for (row in 0 until 16) {
                steps[row].note = notes[row]
                if (notes[row] != 0xFF) {
                    steps[row].instrument = 2
                    steps[row].volume = 0x50
                }
            }
        }

        // --- Phrase 0x03: Hi-hat ---
        phrases[3].apply {
            val notes = intArrayOf(80, 0xFF, 80, 0xFF, 80, 0xFF, 80, 0xFF, 80, 0xFF, 80, 80, 80, 0xFF, 80, 0xFF)
            val vols =  intArrayOf(0x60, 0, 0x40, 0, 0x60, 0, 0x40, 0, 0x60, 0, 0x40, 0x28, 0x60, 0, 0x40, 0)
            for (row in 0 until 16) {
                steps[row].note = notes[row]
                if (notes[row] != 0xFF) {
                    steps[row].instrument = 3
                    steps[row].volume = vols[row]
                }
            }
        }

        // --- Phrase 0x04: FM bell accents ---
        phrases[4].apply {
            steps[0].note = 72; steps[0].instrument = 4; steps[0].volume = 0x50
            steps[12].note = 79; steps[12].instrument = 4; steps[12].volume = 0x50
        }

        // --- Phrase 0x05: Pluck arpeggios ---
        phrases[5].apply {
            val notes = intArrayOf(0xFF, 60, 0xFF, 63, 0xFF, 67, 0xFF, 63, 0xFF, 60, 0xFF, 67, 0xFF, 72, 0xFF, 67)
            for (row in 0 until 16) {
                steps[row].note = notes[row]
                if (notes[row] != 0xFF) {
                    steps[row].instrument = 5
                    steps[row].volume = 0x50
                }
            }
        }

        // --- Phrase 0x06: Sub bass ---
        phrases[6].apply {
            val notes = intArrayOf(24, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 27, 0xFF, 0xFF, 0xFF, 31, 0xFF, 0xFF, 0xFF)
            for (row in 0 until 16) {
                steps[row].note = notes[row]
                if (notes[row] != 0xFF) {
                    steps[row].instrument = 6
                    steps[row].volume = 0x60
                }
            }
        }

        // --- Phrase 0x07: FX hits ---
        phrases[7].apply {
            steps[7].note = 96; steps[7].instrument = 7; steps[7].volume = 0x50
        }

        // --- Phrase 0x08: Alt lead melody ---
        phrases[8].apply {
            val notes = intArrayOf(67, 0xFF, 70, 0xFF, 72, 0xFF, 75, 72, 70, 0xFF, 67, 63, 60, 0xFF, 63, 0xFF)
            for (row in 0 until 16) {
                steps[row].note = notes[row]
                if (notes[row] != 0xFF) {
                    steps[row].instrument = 0
                    steps[row].volume = 0x60
                }
            }
        }

        // --- Phrase 0x09: Alt bass ---
        phrases[9].apply {
            val notes = intArrayOf(43, 0xFF, 43, 0xFF, 43, 0xFF, 43, 36, 36, 0xFF, 36, 0xFF, 39, 0xFF, 39, 0xFF)
            for (row in 0 until 16) {
                steps[row].note = notes[row]
                if (notes[row] != 0xFF) {
                    steps[row].instrument = 1
                    steps[row].volume = 0x60
                }
            }
        }

        // --- Phrase 0x0A: Breakdown lead ---
        phrases[10].apply {
            val notes = intArrayOf(72, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 70, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 67, 0xFF, 0xFF, 0xFF)
            for (row in 0 until 16) {
                steps[row].note = notes[row]
                if (notes[row] != 0xFF) {
                    steps[row].instrument = 0
                    steps[row].volume = 0x50
                }
            }
        }

        // --- Chain 0x00: Intro (lead + hats) ---
        chains[0].apply {
            rows[0] = ChainRow(0x00, 0)
            rows[1] = ChainRow(0x08, 0)
        }

        // --- Chain 0x01: Bass chain ---
        chains[1].apply {
            rows[0] = ChainRow(0x01, 0)
            rows[1] = ChainRow(0x09, 0)
        }

        // --- Chain 0x02: Pad chain ---
        chains[2].apply {
            rows[0] = ChainRow(0x02, 0)
            rows[1] = ChainRow(0x02, 5)  // Transposed up 5 semitones
        }

        // --- Chain 0x03: Drums chain ---
        chains[3].apply {
            rows[0] = ChainRow(0x03, 0)
            rows[1] = ChainRow(0x03, 0)
        }

        // --- Chain 0x04: Bells chain ---
        chains[4].apply {
            rows[0] = ChainRow(0x04, 0)
            rows[1] = ChainRow(0x04, 7)
        }

        // --- Chain 0x05: Plucks chain ---
        chains[5].apply {
            rows[0] = ChainRow(0x05, 0)
            rows[1] = ChainRow(0x05, 3)
        }

        // --- Chain 0x06: Sub chain ---
        chains[6].apply {
            rows[0] = ChainRow(0x06, 0)
            rows[1] = ChainRow(0x06, 0)
        }

        // --- Chain 0x07: FX chain ---
        chains[7].apply {
            rows[0] = ChainRow(0x07, 0)
            rows[1] = ChainRow(0x07, 12)
        }

        // --- Chain 0x08: Breakdown ---
        chains[8].apply {
            rows[0] = ChainRow(0x0A, 0)
        }

        // --- Song arrangement (8 tracks × rows) ---
        // Row 0: Intro - just lead
        songGrid[0] = intArrayOf(0x00, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY)
        // Row 1: Add drums
        songGrid[1] = intArrayOf(0x00, EMPTY, EMPTY, 0x03, EMPTY, EMPTY, EMPTY, EMPTY)
        // Row 2: Add bass + pad
        songGrid[2] = intArrayOf(0x00, 0x01, 0x02, 0x03, EMPTY, EMPTY, 0x06, EMPTY)
        // Row 3: Full arrangement
        songGrid[3] = intArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07)
        // Row 4: Full arrangement variation
        songGrid[4] = intArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07)
        // Row 5: Breakdown
        songGrid[5] = intArrayOf(0x08, EMPTY, 0x02, EMPTY, 0x04, EMPTY, 0x06, EMPTY)
        // Row 6: Build back
        songGrid[6] = intArrayOf(0x00, 0x01, EMPTY, 0x03, EMPTY, 0x05, 0x06, EMPTY)
        // Row 7: Full again
        songGrid[7] = intArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07)

        // Mixer defaults
        mixer.apply {
            trackVolumes[0] = 0xD0  // Lead
            trackVolumes[1] = 0xE0  // Bass
            trackVolumes[2] = 0xA0  // Pad (lower)
            trackVolumes[3] = 0xC0  // Hats
            trackVolumes[4] = 0xB0  // Bells
            trackVolumes[5] = 0xC0  // Plucks
            trackVolumes[6] = 0xD0  // Sub
            trackVolumes[7] = 0x90  // FX

            trackReverbSend[0] = 0x30
            trackReverbSend[2] = 0x60
            trackReverbSend[4] = 0x40
            trackChorusSend[2] = 0x50
            trackDelaySend[5] = 0x30
        }
    }
}
