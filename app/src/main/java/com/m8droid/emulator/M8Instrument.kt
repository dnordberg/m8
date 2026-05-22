package com.m8droid.emulator

/**
 * M8 Instrument definitions matching the real Dirtywave M8 tracker.
 * Each instrument type has type-specific synth parameters plus shared filter/amp/mixer params.
 */

// ===== Enums matching M8 values =====

enum class InstrumentType(val label: String) {
    WAVSYNTH("WAVSYNTH"),
    MACROSYNTH("MACROSYNTH"),
    SAMPLER("SAMPLER"),
    MIDI_OUT("MIDI OUT"),
    FM_SYNTH("FMSYNTH"),
    HYPERSYNTH("HYPERSYNTH"),
}

enum class FilterType(val label: String) {
    OFF("OFF"),
    LP("LP"),
    HP("HP"),
    BP("BP"),
    BS("BS"),          // Bandstop/Notch
    LP_HP("LP>HP"),    // Series LP then HP
    ;
    companion object {
        fun fromIndex(i: Int) = entries.getOrElse(i) { OFF }
    }
}

enum class LimiterType(val label: String) {
    CLIP("CLIP"),
    SIN("SIN"),
    FOLD("FOLD"),
    WRAP("WRAP"),
    POST("POST"),
    POST_AD("POST:AD"),
    ;
    companion object {
        fun fromIndex(i: Int) = entries.getOrElse(i) { CLIP }
    }
}

enum class WavShape(val label: String) {
    PULSE_12("PULSE 12%"),
    PULSE_25("PULSE 25%"),
    PULSE_50("PULSE 50%"),
    PULSE_75("PULSE 75%"),
    SAW("SAW"),
    TRIANGLE("TRIANGLE"),
    SINE("SINE"),
    NOISE_PITCH("NOISE PIT"),
    NOISE("NOISE"),
    OVERFLOW("OVERFLOW"),
    ;
    companion object {
        fun fromIndex(i: Int) = entries.getOrElse(i) { PULSE_50 }
    }
}

enum class EnvelopeType(val label: String) {
    ADSR("ADSR"),
    AHD("AHD"),
    DRUM("DRUM"),
    TRIGGER("TRIGGER"),
    TRACKING("TRACKING"),
}

enum class LfoShape(val label: String) {
    TRIANGLE("TRI"),
    SINE("SIN"),
    RAMP_DOWN("RAMPDN"),
    RAMP_UP("RAMPUP"),
    SQUARE("SQR"),
    RANDOM("RANDOM"),
    DRUNK("DRUNK"),
    ;
    companion object {
        fun fromIndex(i: Int) = entries.getOrElse(i) { TRIANGLE }
    }
}

/** FM Synth algorithm (operator routing) - M8 has 12 */
enum class FmAlgorithm(val label: String) {
    ALG_A("A>B>C>D"),
    ALG_B("[A+B]>C>D"),
    ALG_C("[A>B+C]>D"),
    ALG_D("[A>B]+[C>D]"),
    ALG_E("A>[B+C+D]"),
    ALG_F("A>[B+C]>D"),
    ALG_G("[A+B+C]>D"),
    ALG_H("A>B>[C+D]"),
    ALG_I("[A>B>C]+D"),
    ALG_J("[A>B]+C+D"),
    ALG_K("A+B+[C>D]"),
    ALG_L("A+B+C+D"),
    ;
    companion object {
        fun fromIndex(i: Int) = entries.getOrElse(i) { ALG_A }
    }
}

enum class FmOperatorShape(val label: String) {
    SIN("SIN"), SAW("SAW"), SQR("SQR"), PUL("PUL"),
    IMP("IMP"), NOI("NOI"), TRI("TRI"),
    ;
    companion object {
        fun fromIndex(i: Int) = entries.getOrElse(i) { SIN }
    }
}

// ===== Shared parameter blocks =====

/** Shared filter parameters (all synth instruments) */
data class FilterParams(
    var type: FilterType = FilterType.LP,
    var cutoff: Int = 0xFF,      // 0-255
    var resonance: Int = 0x00,   // 0-255
)

/** Shared amp/output parameters */
data class AmpParams(
    var amp: Int = 0x80,         // 0-255
    var limiter: LimiterType = LimiterType.CLIP,
    var pan: Int = 0x80,         // 0=L, 0x80=C, 0xFF=R
    var dry: Int = 0xC0,         // Dry level
    var chorusSend: Int = 0x00,
    var delaySend: Int = 0x00,
    var reverbSend: Int = 0x00,
)

/** Envelope (shared modulation) */
data class Envelope(
    var type: EnvelopeType = EnvelopeType.ADSR,
    var attack: Int = 0x00,      // 0-255
    var hold: Int = 0x00,
    var decay: Int = 0x40,
    var sustain: Int = 0xC0,     // (ADSR only)
    var release: Int = 0x40,     // (ADSR only)
    var dest: Int = 0,           // Modulation destination parameter index
    var amount: Int = 0x80,      // Modulation amount (0x80 = center/none)
)

/** LFO modulation source */
data class Lfo(
    var shape: LfoShape = LfoShape.TRIANGLE,
    var speed: Int = 0x40,
    var amount: Int = 0x80,      // 0x80 = center/none
    var dest: Int = 0,           // Destination parameter index
    var retrigger: Boolean = true,
)

/** Modulation page for an instrument */
data class ModulationParams(
    var env1: Envelope = Envelope(),
    var env2: Envelope = Envelope(),
    var lfo1: Lfo = Lfo(),
    var lfo2: Lfo = Lfo(),
)

// ===== Type-specific synth parameters =====

/** WAVSYNTH parameters */
data class WavSynthParams(
    var shape: WavShape = WavShape.PULSE_50,
    var size: Int = 0x80,        // Waveform buffer length
    var mult: Int = 0x01,        // Repeats/hard-sync multiplier
    var warp: Int = 0x00,        // Push shape to one side
    var mirror: Int = 0x00,      // Waveform reflection (0=OFF)
)

/** MACROSYNTH parameters (Mutable Instruments Braids models) */
data class MacroSynthParams(
    var model: Int = 0,          // 0-43: Braids model index
    var timbre: Int = 0x80,      // Model-specific param 1
    var color: Int = 0x80,       // Model-specific param 2
    var degrade: Int = 0x00,     // Sample rate reduction
    var redux: Int = 0x00,       // Bit rate reduction
) {
    companion object {
        val MODEL_NAMES = arrayOf(
            "CSAW", "MORPH", "SAW SQUARE", "SINE TRIANGLE", "BUZZ",
            "SQUARE SUB", "SAW SUB", "SQUARE SYNC", "SAW SYNC",
            "TRIPLE SAW", "TRIPLE SQUARE", "TRIPLE TRIANGLE", "TRIPLE SIN",
            "TRIPLE RING", "SAW SWARM", "SAW COMB", "TOY",
            "DIGITAL FILT LP", "DIGITAL FILT PK", "DIGITAL FILT BP", "DIGITAL FILT HP",
            "VOSIM", "VOWEL", "VOWEL FOF", "HARMONICS",
            "FM", "FEEDBACK FM", "CHAOTIC FM",
            "PLUCKED", "BOWED", "BLOWN", "FLUTED",
            "STRUCK BELL", "STRUCK DRUM",
            "KICK", "SNARE", "CYMBAL", "CLAP",
            "WAVETABLE", "WAVEMAP", "WAVELINE", "WAVETBLx4",
            "FILTERED NOISE", "TWIN PEAKS",
        )
        fun modelName(index: Int) = MODEL_NAMES.getOrElse(index) { "MODEL $index" }
    }
}

/** FM SYNTH parameters (4-operator) */
data class FmSynthParams(
    var algorithm: FmAlgorithm = FmAlgorithm.ALG_A,
    // Operator parameters: [ratio, level, feedback, shape] × 4
    var op1Shape: FmOperatorShape = FmOperatorShape.SIN,
    var op1Ratio: Int = 0x01,    // Frequency ratio (×1)
    var op1Level: Int = 0xFF,
    var op1Feedback: Int = 0x00,
    var op2Shape: FmOperatorShape = FmOperatorShape.SIN,
    var op2Ratio: Int = 0x02,    // ×2
    var op2Level: Int = 0xC0,
    var op2Feedback: Int = 0x00,
    var op3Shape: FmOperatorShape = FmOperatorShape.SIN,
    var op3Ratio: Int = 0x03,    // ×3
    var op3Level: Int = 0x80,
    var op3Feedback: Int = 0x00,
    var op4Shape: FmOperatorShape = FmOperatorShape.SIN,
    var op4Ratio: Int = 0x04,    // ×4
    var op4Level: Int = 0x60,
    var op4Feedback: Int = 0x00,
    // Modulation routing
    var mod1: Int = 0x00,
    var mod2: Int = 0x00,
    var mod3: Int = 0x00,
    var mod4: Int = 0x00,
)

/** HYPERSYNTH parameters (6-osc chord synth) */
data class HyperSynthParams(
    var chordBank: Int = 0,      // 0-15 chord bank
    var chord: Int = 0,          // Chord within bank
    var shift: Int = 0x00,       // Crossfade between intervals
    var swarm: Int = 0x20,       // Detune amount
    var width: Int = 0xFF,       // Stereo spread
    var subOsc: Int = 0x00,      // Sub oscillator level
) {
    companion object {
        val CHORD_BANK_NAMES = arrayOf(
            "MAJOR", "MINOR", "SUS2", "SUS4",
            "7TH", "MAJ7", "MIN7", "DIM",
            "AUG", "POWER", "5TH+OCT", "STACKED",
            "QUARTAL", "MODAL", "CUSTOM 1", "CUSTOM 2",
        )
    }
}

/** SAMPLER parameters */
data class SamplerParams(
    var samplePath: String = "",     // File path to WAV
    var playMode: Int = 0,           // 0=FWD, 1=REV, 2=FWDLOOP, 3=REVLOOP, 4=FWD_PP, 5=REV_PP, 6=OSC
    var sliceMode: Int = 0,
    var start: Int = 0x00,           // Sample start point
    var loopStart: Int = 0x00,       // Loop start
    var length: Int = 0xFF,          // Sample length
    var degrade: Int = 0x00,
    var detune: Int = 0x80,          // 0x80 = center (no detune)
) {
    companion object {
        val PLAY_MODE_NAMES = arrayOf("FWD", "REV", "FWDLOOP", "REVLOOP", "FWD PP", "REV PP", "OSC")
    }
}

// ===== Complete Instrument =====

/**
 * A complete M8 instrument with type-specific synthesis parameters,
 * shared filter/amp/mixer, and modulation routing.
 */
class M8Instrument(
    var name: String = "INIT",
    var type: InstrumentType = InstrumentType.WAVSYNTH,
    var transpose: Boolean = true,
    var table: Int = 0xFF,       // 0xFF = no table assigned
) {
    // Type-specific parameters
    var wavSynth = WavSynthParams()
    var macroSynth = MacroSynthParams()
    var fmSynth = FmSynthParams()
    var hyperSynth = HyperSynthParams()
    var sampler = SamplerParams()

    // Shared parameters
    var filter = FilterParams()
    var amp = AmpParams()
    var modulation = ModulationParams()

    /** Get the type-specific parameter names and values for display */
    fun getTypeParams(): List<Pair<String, String>> = when (type) {
        InstrumentType.WAVSYNTH -> listOf(
            "SHAPE" to wavSynth.shape.label,
            "SIZE" to M8Song.hex2(wavSynth.size),
            "MULT" to M8Song.hex2(wavSynth.mult),
            "WARP" to M8Song.hex2(wavSynth.warp),
            "MIRROR" to if (wavSynth.mirror > 0) "ON" else "OFF",
        )
        InstrumentType.MACROSYNTH -> listOf(
            "MODEL" to MacroSynthParams.modelName(macroSynth.model),
            "TIMBRE" to M8Song.hex2(macroSynth.timbre),
            "COLOR" to M8Song.hex2(macroSynth.color),
            "DEGRADE" to M8Song.hex2(macroSynth.degrade),
            "REDUX" to M8Song.hex2(macroSynth.redux),
        )
        InstrumentType.FM_SYNTH -> listOf(
            "ALGO" to fmSynth.algorithm.label,
            "OP1 SHAPE" to fmSynth.op1Shape.label,
            "OP1 RATIO" to M8Song.hex2(fmSynth.op1Ratio),
            "OP1 LEVEL" to M8Song.hex2(fmSynth.op1Level),
            "OP1 FB" to M8Song.hex2(fmSynth.op1Feedback),
            "OP2 SHAPE" to fmSynth.op2Shape.label,
            "OP2 RATIO" to M8Song.hex2(fmSynth.op2Ratio),
            "OP2 LEVEL" to M8Song.hex2(fmSynth.op2Level),
            "OP2 FB" to M8Song.hex2(fmSynth.op2Feedback),
            "OP3 SHAPE" to fmSynth.op3Shape.label,
            "OP3 RATIO" to M8Song.hex2(fmSynth.op3Ratio),
            "OP3 LEVEL" to M8Song.hex2(fmSynth.op3Level),
            "OP3 FB" to M8Song.hex2(fmSynth.op3Feedback),
            "OP4 SHAPE" to fmSynth.op4Shape.label,
            "OP4 RATIO" to M8Song.hex2(fmSynth.op4Ratio),
            "OP4 LEVEL" to M8Song.hex2(fmSynth.op4Level),
            "OP4 FB" to M8Song.hex2(fmSynth.op4Feedback),
        )
        InstrumentType.HYPERSYNTH -> listOf(
            "CHORD BNK" to HyperSynthParams.CHORD_BANK_NAMES.getOrElse(hyperSynth.chordBank) { "---" },
            "CHORD" to M8Song.hex2(hyperSynth.chord),
            "SHIFT" to M8Song.hex2(hyperSynth.shift),
            "SWARM" to M8Song.hex2(hyperSynth.swarm),
            "WIDTH" to M8Song.hex2(hyperSynth.width),
            "SUB OSC" to M8Song.hex2(hyperSynth.subOsc),
        )
        InstrumentType.SAMPLER -> listOf(
            "SAMPLE" to sampler.samplePath.substringAfterLast('/').ifEmpty { "---" },
            "PLAY" to SamplerParams.PLAY_MODE_NAMES.getOrElse(sampler.playMode) { "FWD" },
            "START" to M8Song.hex2(sampler.start),
            "LOOP ST" to M8Song.hex2(sampler.loopStart),
            "LENGTH" to M8Song.hex2(sampler.length),
            "DEGRADE" to M8Song.hex2(sampler.degrade),
            "DETUNE" to M8Song.hex2(sampler.detune),
        )
        InstrumentType.MIDI_OUT -> listOf(
            "PORT" to "01",
            "CHANNEL" to "01",
            "BANK SEL" to "--",
            "PROGRAM" to "--",
        )
    }

    /** Get shared filter/amp params for display */
    fun getSharedParams(): List<Pair<String, String>> = listOf(
        "FILTER" to filter.type.label,
        "CUTOFF" to M8Song.hex2(filter.cutoff),
        "RES" to M8Song.hex2(filter.resonance),
        "AMP" to M8Song.hex2(amp.amp),
        "LIM" to amp.limiter.label,
        "PAN" to M8Song.hex2(amp.pan),
        "DRY" to M8Song.hex2(amp.dry),
        "CHO" to M8Song.hex2(amp.chorusSend),
        "DEL" to M8Song.hex2(amp.delaySend),
        "REV" to M8Song.hex2(amp.reverbSend),
    )

    companion object {
        /**
         * Real M8 hardware holds 128 instrument slots; the .m8s instrument pool
         * sits in a 128-entry block. Expose that capacity here so phrase steps
         * that reference instrument > 7 (common in real-world fixtures) resolve
         * to a real M8Instrument instead of falling back to the previous
         * track configuration. The first 8 slots get the named Android demo
         * presets; the remaining 120 slots are empty WavSynth placeholders.
         */
        const val SLOT_COUNT = 128

        /** Create the full 128-slot instrument pool with named defaults at 0..7. */
        fun createDefaults(): Array<M8Instrument> {
            val named = createNamedDefaults()
            return Array(SLOT_COUNT) { i ->
                named.getOrNull(i) ?: M8Instrument("---", InstrumentType.WAVSYNTH)
            }
        }

        /** Just the named Android demo presets, in display order. */
        private fun createNamedDefaults(): Array<M8Instrument> = arrayOf(
            // 0: Lead - Pulse wave
            M8Instrument("LEAD", InstrumentType.WAVSYNTH).apply {
                wavSynth = WavSynthParams(WavShape.PULSE_50, 0x80, 0x01, 0x00, 0x00)
                filter = FilterParams(FilterType.LP, 0xB0, 0x30)
                amp = AmpParams(0x80, LimiterType.CLIP, 0x80, 0xC0, 0x00, 0x00, 0x30)
                modulation.env1 = Envelope(attack = 0x02, decay = 0x1A, sustain = 0xB0, release = 0x4C)
                modulation.lfo1 = Lfo(LfoShape.SINE, 0x38, 0x90, dest = 2) // PWM mod
            },
            // 1: Bass - Saw
            M8Instrument("BASS", InstrumentType.WAVSYNTH).apply {
                wavSynth = WavSynthParams(WavShape.SAW, 0x80, 0x01, 0x00, 0x00)
                filter = FilterParams(FilterType.LP, 0x58, 0x48)
                amp = AmpParams(0x90, LimiterType.CLIP, 0x80, 0xC0, 0x00, 0x00, 0x00)
                modulation.env1 = Envelope(attack = 0x01, decay = 0x26, sustain = 0x99, release = 0x26)
                modulation.env2 = Envelope(attack = 0x00, decay = 0x33, amount = 0xA0, dest = 1) // Filter env
            },
            // 2: Pad - Triangle
            M8Instrument("PAD", InstrumentType.WAVSYNTH).apply {
                wavSynth = WavSynthParams(WavShape.TRIANGLE, 0x80, 0x01, 0x00, 0x00)
                filter = FilterParams(FilterType.LP, 0xD9, 0x10)
                amp = AmpParams(0x70, LimiterType.CLIP, 0x80, 0xC0, 0x50, 0x00, 0x50)
                modulation.env1 = Envelope(attack = 0x4C, decay = 0x66, sustain = 0xCC, release = 0xFF)
            },
            // 3: Hat - Noise
            M8Instrument("HAT", InstrumentType.WAVSYNTH).apply {
                wavSynth = WavSynthParams(WavShape.NOISE, 0x80, 0x01, 0x00, 0x00)
                filter = FilterParams(FilterType.HP, 0x99, 0x20)
                amp = AmpParams(0x80, LimiterType.CLIP, 0x80, 0xC0, 0x00, 0x00, 0x00)
                modulation.env1 = Envelope(attack = 0x00, decay = 0x0D, sustain = 0x00, release = 0x05)
            },
            // 4: FM Bell
            M8Instrument("FM BELL", InstrumentType.FM_SYNTH).apply {
                fmSynth = FmSynthParams(
                    algorithm = FmAlgorithm.ALG_A,
                    op1Ratio = 0x01, op1Level = 0xFF,
                    op2Ratio = 0x03, op2Level = 0xC0,
                    op3Ratio = 0x05, op3Level = 0x80,
                    op4Ratio = 0x07, op4Level = 0x60,
                )
                filter = FilterParams(FilterType.LP, 0xE6, 0x00)
                amp = AmpParams(0x70, LimiterType.SIN, 0x80, 0xC0, 0x00, 0x00, 0x40)
                modulation.env1 = Envelope(attack = 0x00, decay = 0xCC, sustain = 0x33, release = 0x80)
            },
            // 5: Pluck
            M8Instrument("PLUCK", InstrumentType.WAVSYNTH).apply {
                wavSynth = WavSynthParams(WavShape.PULSE_50, 0x80, 0x01, 0x00, 0x00)
                filter = FilterParams(FilterType.LP, 0x8C, 0x38)
                amp = AmpParams(0x80, LimiterType.CLIP, 0x80, 0xC0, 0x00, 0x30, 0x00)
                modulation.env1 = Envelope(attack = 0x00, decay = 0x33, sustain = 0x1A, release = 0x26)
                modulation.env2 = Envelope(attack = 0x00, decay = 0x26, amount = 0xB0, dest = 1) // Filter
            },
            // 6: Sub
            M8Instrument("SUB", InstrumentType.WAVSYNTH).apply {
                wavSynth = WavSynthParams(WavShape.SINE, 0x80, 0x01, 0x00, 0x00)
                filter = FilterParams(FilterType.LP, 0xF2, 0x00)
                amp = AmpParams(0x90, LimiterType.CLIP, 0x80, 0xC0, 0x00, 0x00, 0x20)
                modulation.env1 = Envelope(attack = 0x02, decay = 0x0D, sustain = 0xE6, release = 0x33)
            },
            // 7: FX
            M8Instrument("FX", InstrumentType.FM_SYNTH).apply {
                fmSynth = FmSynthParams(
                    algorithm = FmAlgorithm.ALG_C,
                    op1Ratio = 0x07, op1Level = 0xFF, op1Feedback = 0x20,
                    op2Ratio = 0x05, op2Level = 0xC0,
                    op3Ratio = 0x03, op3Level = 0x80,
                    op4Ratio = 0x01, op4Level = 0x60,
                )
                filter = FilterParams(FilterType.BP, 0x80, 0x60)
                amp = AmpParams(0x60, LimiterType.FOLD, 0x80, 0xC0, 0x00, 0x00, 0x20)
                modulation.env1 = Envelope(attack = 0x00, decay = 0x4C, sustain = 0x00, release = 0x1A)
            },
        )
    }
}
