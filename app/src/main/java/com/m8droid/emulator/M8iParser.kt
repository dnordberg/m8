package com.m8droid.emulator

/**
 * Parser for Dirtywave M8 `.m8i` instrument files, V4.x firmware.
 *
 * Format reference: AlexCharlton/m8-files (Rust), src/instruments and
 * src/version.rs. The file layout is:
 *
 *   bytes  0..9   ASCII version preamble (not validated — matches m8-files)
 *   byte  10      packed nibbles: minor = hi, patch = lo
 *   byte  11      major version in low nibble
 *   bytes 12..13  padding
 *   bytes 14..228 215-byte instrument body
 *   [bytes 357..374 optional EQ block on V4.1+ — not parsed]
 *
 * The instrument body starts with shared fields (kind, name, transpose,
 * table_tick, volume, pitch, fine_tune) then a per-kind parameter block,
 * then 10 bytes of filter/amp/mixer. Modulation (2 envelopes + 2 LFOs)
 * follows but is left at defaults by this parser — porting the full
 * Mod union is a follow-up.
 */
object M8iParser {

    class ParseException(message: String) : Exception(message)

    private const val HEADER_SIZE = 14
    const val BODY_SIZE = 215
    private const val MIN_SIZE = HEADER_SIZE + BODY_SIZE

    // Kind enum values on byte 0 of the instrument body.
    private const val KIND_WAVSYNTH = 0x00
    private const val KIND_MACROSYNTH = 0x01
    private const val KIND_SAMPLER = 0x02
    private const val KIND_MIDI_OUT = 0x03
    private const val KIND_FM_SYNTH = 0x04
    private const val KIND_HYPERSYNTH = 0x05
    private const val KIND_NONE = 0xFF

    data class Header(val major: Int, val minor: Int, val patch: Int) {
        override fun toString() = "V$major.$minor.$patch"
    }

    /** Parse a .m8i file into an [M8Instrument]. */
    fun parse(bytes: ByteArray): M8Instrument {
        if (bytes.size < MIN_SIZE) {
            throw ParseException("File too small: ${bytes.size} < $MIN_SIZE bytes")
        }
        val header = parseHeader(bytes)
        if (header.major < 1 || header.major > 4) {
            // Be lenient — still try to parse, just warn via exception on failure.
        }
        val body = bytes.copyOfRange(HEADER_SIZE, HEADER_SIZE + BODY_SIZE)
        return parseBody(body, header)
    }

    /**
     * Parse a bare 215-byte instrument body (no .m8i file header) using the
     * supplied version. Used by M8sParser when reading the instrument pool out
     * of a .m8s song file — the song header carries the version, individual
     * instrument bodies do not. [offset] is the start of the body within
     * [bytes]; the slice is read in place so we don't allocate per slot.
     */
    fun parseBodyAt(bytes: ByteArray, offset: Int, header: Header): M8Instrument {
        if (offset < 0 || offset + BODY_SIZE > bytes.size) {
            throw ParseException("Body slice out of range: $offset..${offset + BODY_SIZE} of ${bytes.size}")
        }
        val body = bytes.copyOfRange(offset, offset + BODY_SIZE)
        return parseBody(body, header)
    }

    fun parseHeader(bytes: ByteArray): Header {
        val lsb = bytes[10].toInt() and 0xFF
        val msb = bytes[11].toInt() and 0xFF
        val major = msb and 0x0F
        val minor = (lsb shr 4) and 0x0F
        val patch = lsb and 0x0F
        return Header(major, minor, patch)
    }

    private fun parseBody(body: ByteArray, header: Header): M8Instrument {
        val kind = body.u8(0)
        if (kind == KIND_NONE) {
            return M8Instrument(name = "---", type = InstrumentType.WAVSYNTH)
        }

        val name = readAsciiString(body, 1, 12)
        val transposeByte = body.u8(13)
        val transpose = when {
            header.major >= 4 && header.minor >= 1 -> transposeByte != 0
            header.major >= 4 -> (transposeByte and 0x01) != 0
            else -> transposeByte != 0
        }
        val tableTick = body.u8(14)
        @Suppress("UNUSED_VARIABLE") val volume = body.u8(15)
        @Suppress("UNUSED_VARIABLE") val pitch = body.u8(16)
        @Suppress("UNUSED_VARIABLE") val fineTune = body.u8(17)

        val instType = kindToType(kind)
        val inst = M8Instrument(
            name = name,
            type = instType,
            transpose = transpose,
            table = tableTick,
        )

        // Each kind has its own param block followed by 10 bytes of
        // filter/amp/mixer. filterOffset is the body-relative position of
        // the first filter byte; the per-kind parser returns it.
        val filterOffset = when (kind) {
            KIND_WAVSYNTH -> parseWavSynth(body, inst)
            KIND_MACROSYNTH -> parseMacroSynth(body, inst)
            KIND_SAMPLER -> parseSampler(body, inst)
            KIND_MIDI_OUT -> parseMidiOut(body, inst)
            KIND_FM_SYNTH -> parseFmSynth(body, inst)
            KIND_HYPERSYNTH -> parseHyperSynth(body, inst)
            else -> -1
        }

        if (kind != KIND_MIDI_OUT && filterOffset > 0 && filterOffset + 10 <= body.size) {
            parseFilterAmpMixer(body, filterOffset, inst)
        }

        return inst
    }

    /** WavSynth: 5 param bytes at 18..22, filter/amp at 23. */
    private fun parseWavSynth(body: ByteArray, inst: M8Instrument): Int {
        inst.wavSynth = WavSynthParams(
            shape = WavShape.fromIndex(body.u8(18)),
            size = body.u8(19),
            mult = body.u8(20),
            warp = body.u8(21),
            mirror = body.u8(22),
        )
        return 23
    }

    /** MacroSynth: 5 param bytes at 18..22, filter/amp at 23. */
    private fun parseMacroSynth(body: ByteArray, inst: M8Instrument): Int {
        inst.macroSynth = MacroSynthParams(
            model = body.u8(18),
            timbre = body.u8(19),
            color = body.u8(20),
            degrade = body.u8(21),
            redux = body.u8(22),
        )
        return 23
    }

    /**
     * Sampler: 6 param bytes at 18..23, filter/amp at 24.
     * Sample path is a fixed 128-byte ASCII string at body offset 0x57 (87).
     */
    private fun parseSampler(body: ByteArray, inst: M8Instrument): Int {
        inst.sampler = SamplerParams(
            playMode = body.u8(18),
            sliceMode = body.u8(19),
            start = body.u8(20),
            loopStart = body.u8(21),
            length = body.u8(22),
            degrade = body.u8(23),
            detune = 0x80,
            samplePath = if (body.size >= 0x57 + 128) {
                readAsciiString(body, 0x57, 128)
            } else "",
        )
        return 24
    }

    /**
     * MIDI Out: different layout — no filter/amp/mixer. We read only port,
     * channel, bank, program to populate sampler-like display. Return -1 to
     * skip filter/amp parsing.
     */
    private fun parseMidiOut(body: ByteArray, inst: M8Instrument): Int {
        // MIDI Out fields are displayed via a hardcoded table in getTypeParams,
        // so no target struct to populate yet. Kept here for future expansion.
        @Suppress("UNUSED_VARIABLE") val port = body.u8(15)
        @Suppress("UNUSED_VARIABLE") val channel = body.u8(16)
        @Suppress("UNUSED_VARIABLE") val bankSelect = body.u8(17)
        @Suppress("UNUSED_VARIABLE") val programChange = body.u8(18)
        return -1
    }

    /**
     * FM Synth: algo (1) + 4 op shapes (4) + 4 ratio/fine pairs (8) +
     * 4 level/feedback pairs (8) + 4 mod_a (4) + 4 mod_b (4) + 4 mod1-4 (4)
     * = 33 bytes, so params at 18..50 and filter/amp at 51.
     *
     * Earlier firmware omitted op shapes; we only read shapes if the file
     * is at least V1.4 (matching m8-files behaviour).
     */
    private fun parseFmSynth(body: ByteArray, inst: M8Instrument): Int {
        var p = 18
        val algo = body.u8(p); p++
        val fm = FmSynthParams(algorithm = FmAlgorithm.fromIndex(algo))

        // Op shapes (4 bytes). In m8-files these are only present on >=1.4;
        // for safety we always read 4 bytes since our parse() is used on V4.x.
        fm.op1Shape = FmOperatorShape.fromIndex(body.u8(p)); p++
        fm.op2Shape = FmOperatorShape.fromIndex(body.u8(p)); p++
        fm.op3Shape = FmOperatorShape.fromIndex(body.u8(p)); p++
        fm.op4Shape = FmOperatorShape.fromIndex(body.u8(p)); p++

        // Ratio / ratio_fine pairs (2 bytes per op × 4 = 8)
        fm.op1Ratio = body.u8(p); p += 2
        fm.op2Ratio = body.u8(p); p += 2
        fm.op3Ratio = body.u8(p); p += 2
        fm.op4Ratio = body.u8(p); p += 2

        // Level / feedback pairs (2 bytes per op × 4 = 8)
        fm.op1Level = body.u8(p); p++
        fm.op1Feedback = body.u8(p); p++
        fm.op2Level = body.u8(p); p++
        fm.op2Feedback = body.u8(p); p++
        fm.op3Level = body.u8(p); p++
        fm.op3Feedback = body.u8(p); p++
        fm.op4Level = body.u8(p); p++
        fm.op4Feedback = body.u8(p); p++

        // mod_a × 4 + mod_b × 4 + mod1..4 → 12 bytes
        p += 8 // skip mod_a / mod_b (per-op modulation slots)
        fm.mod1 = body.u8(p); p++
        fm.mod2 = body.u8(p); p++
        fm.mod3 = body.u8(p); p++
        fm.mod4 = body.u8(p); p++

        inst.fmSynth = fm
        return p
    }

    /** HyperSynth: 7 bytes chord + 5 bytes params = 12; filter/amp at 30. */
    private fun parseHyperSynth(body: ByteArray, inst: M8Instrument): Int {
        val p = 18
        // Skip the 7-byte default_chord, read the 5 param bytes after it.
        inst.hyperSynth = HyperSynthParams(
            chordBank = body.u8(p + 7),
            chord = 0,
            shift = body.u8(p + 8),
            swarm = body.u8(p + 9),
            width = body.u8(p + 10),
            subOsc = body.u8(p + 11),
        )
        return 30
    }

    /** 10 bytes: filter_type, cutoff, res, amp, limit, pan, dry, cho, del, rev. */
    private fun parseFilterAmpMixer(body: ByteArray, off: Int, inst: M8Instrument) {
        inst.filter = FilterParams(
            type = FilterType.fromIndex(body.u8(off)),
            cutoff = body.u8(off + 1),
            resonance = body.u8(off + 2),
        )
        inst.amp = AmpParams(
            amp = body.u8(off + 3),
            limiter = LimiterType.fromIndex(body.u8(off + 4)),
            pan = body.u8(off + 5),
            dry = body.u8(off + 6),
            chorusSend = body.u8(off + 7),
            delaySend = body.u8(off + 8),
            reverbSend = body.u8(off + 9),
        )
    }

    private fun kindToType(kind: Int): InstrumentType = when (kind) {
        KIND_WAVSYNTH -> InstrumentType.WAVSYNTH
        KIND_MACROSYNTH -> InstrumentType.MACROSYNTH
        KIND_SAMPLER -> InstrumentType.SAMPLER
        KIND_MIDI_OUT -> InstrumentType.MIDI_OUT
        KIND_FM_SYNTH -> InstrumentType.FM_SYNTH
        KIND_HYPERSYNTH -> InstrumentType.HYPERSYNTH
        else -> InstrumentType.WAVSYNTH
    }

    private fun readAsciiString(bytes: ByteArray, offset: Int, len: Int): String {
        val end = minOf(offset + len, bytes.size)
        val sb = StringBuilder()
        for (i in offset until end) {
            val b = bytes[i].toInt() and 0xFF
            if (b == 0) break
            if (b in 0x20..0x7E) sb.append(b.toChar())
        }
        return sb.toString().trim()
    }

    private fun ByteArray.u8(i: Int): Int = this[i].toInt() and 0xFF
}
