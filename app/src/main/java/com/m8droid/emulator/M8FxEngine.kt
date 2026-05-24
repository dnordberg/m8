package com.m8droid.emulator

import kotlin.math.roundToInt

/**
 * Processes M8 tracker FX commands that appear in phrase and table steps.
 * Each command is a 3-letter code + 1-byte value (0x00-0xFF).
 */
class M8FxEngine {

    // ===== FX Command IDs =====
    companion object {
        // Note/pitch commands
        const val FX_NONE = 0x00
        const val FX_ARP = 0x01    // Arpeggio: XY = semitones (0, X, Y cycle each tick)
        const val FX_PSL = 0x02    // Portamento/slide: speed
        const val FX_PBN = 0x03    // Pitch bend: amount per tick (80=center)
        const val FX_PVB = 0x04    // Vibrato: XY = speed/depth
        const val FX_PVX = 0x05    // Extreme vibrato
        const val FX_FIN = 0x06    // Fine tune: 80=center

        // Volume/amp commands
        const val FX_VOL = 0x10    // Volume: set to value
        const val FX_KIL = 0x11    // Kill note after N ticks
        const val FX_RET = 0x12    // Retrigger: XY = speed/volume ramp
        const val FX_REP = 0x13    // Repeat/ramp
        const val FX_DEL = 0x14    // Delay note by N ticks

        // Filter commands
        const val FX_CUT = 0x20    // Filter cutoff: set to value
        const val FX_RES = 0x21    // Resonance: set to value
        const val FX_AMP = 0x22    // Amp: set to value
        const val FX_PAN = 0x23    // Pan: set to value (80=center)

        // Mixer/send commands
        const val FX_DRY = 0x30    // Dry level
        const val FX_SCH = 0x31    // Chorus send
        const val FX_SDL = 0x32    // Delay send
        const val FX_SRV = 0x33    // Reverb send

        // Sequencer flow commands
        const val FX_HOP = 0x40    // Hop to row
        const val FX_CHA = 0x41    // Chance: probability (0=never, FF=always)
        const val FX_NTH = 0x42    // Every Nth trigger: XY = N/offset
        const val FX_RND = 0x43    // Randomize note ± value
        const val FX_RNL = 0x44    // Randomize left (note - value to note)
        const val FX_SNG = 0x45    // Song hop

        // Table commands
        const val FX_TBL = 0x50    // Set table
        const val FX_THO = 0x51    // Table hop (jump to row in table)
        const val FX_TIC = 0x52    // Table tick rate

        // Tempo/groove
        const val FX_TPO = 0x60    // Set tempo
        const val FX_GRV = 0x61    // Set groove
        const val FX_SCA = 0x62    // Set scale for track
        const val FX_SCG = 0x63    // Set global scale
        const val FX_TSP = 0x64    // Global transpose

        // Effects
        const val FX_XCM = 0x70    // Chorus mod depth
        const val FX_XCF = 0x71    // Chorus mod freq
        const val FX_XCW = 0x72    // Chorus width
        const val FX_XDT = 0x73    // Delay time
        const val FX_XDF = 0x74    // Delay feedback
        const val FX_XDW = 0x75    // Delay width
        const val FX_XRS = 0x76    // Reverb size
        const val FX_XRD = 0x77    // Reverb damping
        const val FX_XRW = 0x78    // Reverb width
        const val FX_DJF = 0x79    // DJ filter (00=LP, 80=off, FF=HP)

        // Volume tracks
        const val FX_VMV = 0x80    // Master volume
        const val FX_VCH = 0x81    // Chorus volume
        const val FX_VDE = 0x82    // Delay volume
        const val FX_VRE = 0x83    // Reverb volume

        val FX_NAMES = mapOf(
            FX_NONE to "---",
            FX_ARP to "ARP", FX_PSL to "PSL", FX_PBN to "PBN", FX_PVB to "PVB",
            FX_PVX to "PVX", FX_FIN to "FIN",
            FX_VOL to "VOL", FX_KIL to "KIL", FX_RET to "RET", FX_REP to "REP", FX_DEL to "DEL",
            FX_CUT to "CUT", FX_RES to "RES", FX_AMP to "AMP", FX_PAN to "PAN",
            FX_DRY to "DRY", FX_SCH to "SCH", FX_SDL to "SDL", FX_SRV to "SRV",
            FX_HOP to "HOP", FX_CHA to "CHA", FX_NTH to "NTH", FX_RND to "RND",
            FX_RNL to "RNL", FX_SNG to "SNG",
            FX_TBL to "TBL", FX_THO to "THO", FX_TIC to "TIC",
            FX_TPO to "TPO", FX_GRV to "GRV", FX_SCA to "SCA", FX_SCG to "SCG", FX_TSP to "TSP",
            FX_XCM to "XCM", FX_XCF to "XCF", FX_XCW to "XCW",
            FX_XDT to "XDT", FX_XDF to "XDF", FX_XDW to "XDW",
            FX_XRS to "XRS", FX_XRD to "XRD", FX_XRW to "XRW", FX_DJF to "DJF",
            FX_VMV to "VMV", FX_VCH to "VCH", FX_VDE to "VDE", FX_VRE to "VRE",
        )

        /** Reverse lookup: name to ID */
        val FX_IDS = FX_NAMES.entries.associate { (k, v) -> v to k }

        fun fxName(cmd: Int): String = FX_NAMES[cmd] ?: "???"
    }

    // ===== Per-track state for tick-based FX =====

    data class TrackFxState(
        // Arpeggio
        var arpNote1: Int = 0,
        var arpNote2: Int = 0,
        var arpTick: Int = 0,
        var arpActive: Boolean = false,

        // Portamento
        var portaTarget: Double = 0.0,
        var portaSpeed: Double = 0.0,
        var portaActive: Boolean = false,
        var currentFreq: Double = 0.0,

        // Vibrato
        var vibratoSpeed: Double = 0.0,
        var vibratoDepth: Double = 0.0,
        var vibratoPhase: Double = 0.0,
        var vibratoActive: Boolean = false,

        // Pitch bend
        var pitchBend: Double = 0.0,
        var pitchBendSemitones: Double = 0.0,
        var pitchBendActive: Boolean = false,

        // Retrigger
        var retrigSpeed: Int = 0,
        var retrigVolRamp: Double = 0.0,
        var retrigTick: Int = 0,
        var retrigBaseVolume: Int = 0x7F,
        var retrigCount: Int = 0,
        var retrigActive: Boolean = false,

        // Kill
        var killTick: Int = -1,

        // Delay (note delay, not effect)
        var delayTicks: Int = 0,
        var delayedNote: Int = -1,
        var delayedVol: Int = -1,
        var delayedInst: Int = -1,

        // Table
        var tableIndex: Int = -1,
        var tableRow: Int = 0,
        var tableTick: Int = 0,
        var tableTickRate: Int = 1,
        var tableNoteOffset: Int = 0,

        // Chance
        var lastChance: Boolean = true,
        var nthCounter: Int = 0,
    ) {
        fun reset() {
            arpActive = false
            portaActive = false
            currentFreq = 0.0
            vibratoActive = false
            pitchBend = 0.0
            pitchBendSemitones = 0.0
            pitchBendActive = false
            retrigActive = false
            retrigTick = 0
            retrigCount = 0
            killTick = -1
            delayTicks = 0
            delayedNote = -1
            tableIndex = -1
            tableRow = 0
            tableTick = 0
            tableTickRate = 1
            tableNoteOffset = 0
        }
    }

    val trackStates = Array(8) { TrackFxState() }

    // Results of processing a step's FX
    data class FxResult(
        var skipNote: Boolean = false,      // Don't trigger the note (e.g., CHA failed)
        var hopToRow: Int = -1,             // HOP target row (-1 = no hop)
        var songHopToRow: Int = -1,         // SNG target song row (-1 = no song hop)
        var tempoChange: Int = -1,          // New tempo (-1 = no change)
        var transposeChange: Int = Int.MIN_VALUE,
        var noteOffset: Int = 0,            // Random note offset
        var volumeOverride: Int = -1,       // Runtime VOL command override (-1 = no change)
        var ampOverride: Int = -1,          // Runtime AMP command override (-1 = no change)
        var panOverride: Int = -1,          // Runtime PAN command override (-1 = no change)
        var delaySendOverride: Int = -1,    // Runtime SDL command override (-1 = no change)
    )

    /**
     * Process FX commands for a phrase step at the moment it's triggered.
     * Returns an FxResult indicating any flow changes.
     */
    fun processStepFx(
        track: Int,
        step: PhraseStep,
        currentTick: Int,
        baseNote: Int,
    ): FxResult {
        val result = FxResult()
        val state = trackStates[track]

        // Reset tick-based effects on new note
        if (step.note != 0xFF && step.note != 0) {
            state.arpActive = false
            state.retrigActive = false
            state.killTick = -1
            state.vibratoPhase = 0.0
            state.pitchBend = 0.0
            state.pitchBendSemitones = 0.0
            state.pitchBendActive = false
            if (step.fx1Cmd != FX_PSL && step.fx2Cmd != FX_PSL && step.fx3Cmd != FX_PSL) {
                state.portaActive = false
                state.currentFreq = 0.0
            }
        }

        // Process each FX slot
        val fxSlots = arrayOf(
            step.fx1Cmd to step.fx1Val,
            step.fx2Cmd to step.fx2Val,
            step.fx3Cmd to step.fx3Val,
        )

        for ((cmd, value) in fxSlots) {
            if (cmd == FX_NONE) continue

            when (cmd) {
                FX_ARP -> {
                    state.arpNote1 = (value shr 4) and 0x0F
                    state.arpNote2 = value and 0x0F
                    state.arpTick = 0
                    state.arpActive = true
                }
                FX_PSL -> {
                    if (baseNote > 0) {
                        state.portaTarget = M8Synth.noteToFreq(baseNote)
                        state.portaSpeed = (value.coerceAtLeast(1) / 255.0 * 0.18).coerceAtLeast(0.002)
                        state.portaActive = true
                    }
                }
                FX_PBN -> {
                    val bend = (value - 0x80) / 128.0  // -1.0 to +1.0
                    state.pitchBend = bend * 0.01       // Per-sample bend rate
                    state.pitchBendActive = true
                }
                FX_PVB -> {
                    state.vibratoSpeed = ((value shr 4) and 0x0F) * 0.5 + 0.5
                    state.vibratoDepth = (value and 0x0F) / 15.0 * 0.02
                    state.vibratoActive = true
                }
                FX_PVX -> {
                    state.vibratoSpeed = ((value shr 4) and 0x0F) * 0.5 + 0.5
                    state.vibratoDepth = (value and 0x0F) / 15.0 * 0.1 // 5x depth of PVB
                    state.vibratoActive = true
                }
                FX_VOL -> {
                    result.volumeOverride = value.coerceIn(0, 0xFF)
                }
                FX_KIL -> {
                    state.killTick = value
                }
                FX_RET -> {
                    state.retrigSpeed = ((value shr 4) and 0x0F).coerceAtLeast(1)
                    val volParam = value and 0x0F
                    state.retrigVolRamp = (volParam - 8) * 2.25
                    state.retrigTick = 0
                    state.retrigCount = 0
                    state.retrigBaseVolume = step.volume.takeIf { it != M8Song.EMPTY } ?: 0x7F
                    state.retrigActive = true
                }
                FX_DEL -> {
                    state.delayTicks = value
                    state.delayedNote = step.note
                    state.delayedVol = step.volume
                    state.delayedInst = step.instrument
                    result.skipNote = true
                }
                FX_HOP -> {
                    result.hopToRow = value and 0x0F
                }
                FX_SNG -> {
                    result.songHopToRow = value.coerceIn(0, 255)
                }
                FX_CHA -> {
                    val chance = value / 255.0
                    state.lastChance = Math.random() < chance
                    if (!state.lastChance) {
                        result.skipNote = true
                    }
                }
                FX_NTH -> {
                    val n = ((value shr 4) and 0x0F).coerceAtLeast(1)
                    val offset = value and 0x0F
                    state.nthCounter++
                    if ((state.nthCounter + offset) % n != 0) {
                        result.skipNote = true
                    }
                }
                FX_RND -> {
                    val range = value.coerceAtLeast(1)
                    result.noteOffset = (Math.random() * range * 2 - range).roundToInt()
                }
                FX_RNL -> {
                    result.noteOffset = -(Math.random() * value).roundToInt()
                }
                FX_TPO -> {
                    if (value in 40..300) {
                        result.tempoChange = value
                    }
                }
                FX_TSP -> {
                    result.transposeChange = value - 0x80  // Signed: 80=0, 81=+1, 7F=-1
                }
                FX_TBL -> {
                    state.tableIndex = value.coerceIn(0, 255)
                    state.tableRow = 0
                    state.tableTick = 0
                    state.tableNoteOffset = 0
                }
                FX_THO -> {
                    state.tableRow = value and 0x0F
                }
                FX_TIC -> {
                    state.tableTickRate = value.coerceAtLeast(1)
                }
                FX_AMP -> {
                    result.ampOverride = value.coerceIn(0, 0xFF)
                }
                FX_PAN -> {
                    result.panOverride = value.coerceIn(0, 0xFF)
                }
                FX_SDL -> {
                    result.delaySendOverride = value.coerceIn(0, 0xFF)
                }
            }
        }

        return result
    }

    /**
     * Compute one active table tick for a track. M8 tables are per-tick
     * automation lanes selected by TBL, optionally rate-limited by TIC.
     */
    data class TableTickResult(
        val noteOffset: Int = 0,
        val volumeOverride: Int = -1,
        val ampOverride: Int = -1,
        val panOverride: Int = -1,
        val delaySendOverride: Int = -1,
    )

    fun processTableTick(track: Int, tables: Array<Table>): TableTickResult {
        val state = trackStates[track]
        val tableIndex = state.tableIndex
        if (tableIndex !in tables.indices) {
            state.tableNoteOffset = 0
            return TableTickResult()
        }

        val row = tables[tableIndex].rows[state.tableRow.coerceIn(0, 15)]
        state.tableNoteOffset = row.transpose

        var ampOverride = -1
        var panOverride = -1
        var delaySendOverride = -1
        val fxSlots = arrayOf(
            row.fx1Cmd to row.fx1Val,
            row.fx2Cmd to row.fx2Val,
            row.fx3Cmd to row.fx3Val,
        )
        for ((cmd, value) in fxSlots) {
            when (cmd) {
                FX_AMP -> ampOverride = value.coerceIn(0, 0xFF)
                FX_PAN -> panOverride = value.coerceIn(0, 0xFF)
                FX_SDL -> delaySendOverride = value.coerceIn(0, 0xFF)
                FX_THO -> {
                    state.tableRow = value and 0x0F
                    state.tableTick = 0
                }
                FX_TIC -> state.tableTickRate = value.coerceAtLeast(1)
            }
        }

        state.tableTick++
        if (state.tableTick >= state.tableTickRate) {
            state.tableTick = 0
            state.tableRow = (state.tableRow + 1) and 0x0F
        }

        return TableTickResult(
            noteOffset = row.transpose,
            volumeOverride = row.volume.takeIf { it != M8Song.EMPTY } ?: -1,
            ampOverride = ampOverride,
            panOverride = panOverride,
            delaySendOverride = delaySendOverride,
        )
    }

    /**
     * Compute per-sample frequency modification from active tick FX.
     * Call this for every audio sample.
     */
    fun getFreqModifier(track: Int, baseFreq: Double, sampleIndex: Int): Double {
        val state = trackStates[track]
        var freq = baseFreq

        // Table transpose
        if (state.tableNoteOffset != 0) {
            freq *= Math.pow(2.0, state.tableNoteOffset / 12.0)
        }

        // Arpeggio
        if (state.arpActive) {
            val arpPhase = (sampleIndex / (M8Synth.SAMPLE_RATE / 30)) % 3  // Cycle at ~30Hz
            val semitones = when (arpPhase) {
                1 -> state.arpNote1
                2 -> state.arpNote2
                else -> 0
            }
            freq *= Math.pow(2.0, semitones / 12.0)
        }

        // Vibrato
        if (state.vibratoActive) {
            state.vibratoPhase += state.vibratoSpeed / M8Synth.SAMPLE_RATE
            val mod = kotlin.math.sin(state.vibratoPhase * 2.0 * Math.PI) * state.vibratoDepth
            freq *= (1.0 + mod)
        }

        // Pitch bend
        if (state.pitchBendActive) {
            state.pitchBendSemitones += state.pitchBend
            freq *= Math.pow(2.0, state.pitchBendSemitones / 12.0)
        }

        // Portamento
        if (state.portaActive && state.portaTarget > 0.0) {
            if (state.currentFreq <= 0.0) state.currentFreq = baseFreq
            val diff = state.portaTarget - state.currentFreq
            state.currentFreq += diff * state.portaSpeed
            freq = state.currentFreq
            if (kotlin.math.abs(diff) < 0.1) {
                state.currentFreq = state.portaTarget
                state.portaActive = false
            }
        }

        return freq
    }

    /**
     * Process tick-based effects (retrigger, kill).
     * Called once per sequencer tick.
     * Returns true if the note should be re-triggered.
     */
    data class TickResult(
        val retrigger: Boolean = false,
        val releaseNote: Boolean = false,
        val delayedNote: Int = -1,
        val delayedInstrument: Int = -1,
        val delayedVolume: Int = -1,
        val retriggerVolumeOverride: Int = -1,
    )

    fun processTick(track: Int, tick: Int): TickResult {
        val state = trackStates[track]
        var retrigger = false
        var releaseNote = false
        var delayedNote = -1
        var delayedInstrument = -1
        var delayedVolume = -1
        var retriggerVolumeOverride = -1

        // Kill
        if (state.killTick >= 0 && tick >= state.killTick) {
            state.killTick = -1
            releaseNote = true
        }

        // Retrigger
        if (state.retrigActive) {
            state.retrigTick++
            if (state.retrigTick >= state.retrigSpeed) {
                state.retrigTick = 0
                state.retrigCount++
                retrigger = true
                retriggerVolumeOverride = (state.retrigBaseVolume + state.retrigVolRamp * state.retrigCount)
                    .roundToInt()
                    .coerceIn(0, 0xFF)
            }
        }

        // Note delay
        if (state.delayTicks > 0) {
            state.delayTicks--
            if (state.delayTicks == 0) {
                delayedNote = state.delayedNote
                delayedInstrument = state.delayedInst
                delayedVolume = state.delayedVol
                state.delayedNote = -1
                state.delayedInst = -1
                state.delayedVol = -1
            }
        }

        return TickResult(
            retrigger = retrigger,
            releaseNote = releaseNote,
            delayedNote = delayedNote,
            delayedInstrument = delayedInstrument,
            delayedVolume = delayedVolume,
            retriggerVolumeOverride = retriggerVolumeOverride,
        )
    }

    /** Reset all track states */
    fun reset() {
        for (state in trackStates) {
            state.reset()
        }
    }
}
