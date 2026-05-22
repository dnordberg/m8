package com.m8droid.emulator

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parser for Dirtywave M8 song files (.m8s), version 4.x.
 *
 * Based on the binary layout documented in the m8-files Rust crate
 * (AlexCharlton/m8-files). This parser currently imports the
 * playback-critical subset: header, tempo/name/transpose/quantize,
 * song grid, phrases, chains, grooves, tables, instrument pool, and
 * mixer settings. Later passes should add global FX settings, scales,
 * EQ, and MIDI mappings; until then [ParsedSong.warnings] surfaces the
 * partial-import caveats.
 */
object M8sParser {

    // Absolute byte offsets (V4 layout — identical for 4.0 and 4.1 on all
    // data we care about). Taken from m8-files V4_OFFSETS.
    private const val OFFSET_GROOVE = 0xEE
    private const val OFFSET_SONG_GRID = 0x2EE
    private const val OFFSET_PHRASES = 0xAEE
    private const val OFFSET_CHAINS = 0x9A5E
    private const val OFFSET_TABLES = 0xBA3E
    private const val OFFSET_INSTRUMENTS = 0x13A3E

    // Mixer block lives sequentially in the header region: after the
    // 14-byte version + 128-byte directory + transpose/tempo/quantize/name
    // (32 bytes) + MidiSettings (27 bytes) + key (1 byte) + 18-byte pad,
    // i.e. at file offset 0xCE. 32 bytes wide; the groove pool starts
    // exactly at 0xCE + 32 = 0xEE.
    private const val OFFSET_MIXER = 0xCE
    private const val MIXER_BYTES = 32

    // Global effects block (chorus / delay / reverb). m8-files V4_OFFSETS
    // places it at 0x1A5C1 — that's 3 bytes after the instrument pool ends
    // (0x13A3E + 128*215 = 0x1A5BE), matching the `reader.read_bytes(3)`
    // skip in songs.rs::from_reader.
    private const val OFFSET_FX_SETTINGS = 0x1A5C1
    private const val FX_SETTINGS_BYTES = 17

    // Header field positions (relative to file start, after the 14-byte
    // version block). directory(128) starts at 14.
    private const val POS_TRANSPOSE = 14 + 128        // 142
    private const val POS_TEMPO = POS_TRANSPOSE + 1   // 143 (f32 LE)
    private const val POS_QUANTIZE = POS_TEMPO + 4    // 147
    private const val POS_NAME = POS_QUANTIZE + 1     // 148 (12 bytes)
    // Global song key is read by m8-files after MidiSettings and before the
    // 18-byte padding that precedes the mixer block.
    private const val POS_KEY = 187
    private const val NAME_LEN = 12

    // Scale definitions. m8-files V4_OFFSETS.scale = 0x1AA7E. Each scale is
    // read as: u16 enable bitmap, 12×2 bytes of semitone/cents offsets, then
    // a 16-byte name (42 bytes total; upstream's SIZE constant is stale, but
    // Scale::from_reader consumes this layout). Our current model carries the
    // bitmap + name; cent offsets remain a documented parity gap until the
    // synth/note path supports them.
    private const val OFFSET_SCALES = 0x1AA7E
    private const val N_SCALES = 16
    private const val SCALE_BYTES = 42

    private const val SONG_GRID_BYTES = 2048          // 256 rows × 8 tracks
    private const val N_PHRASES = 255
    private const val PHRASE_BYTES = 16 * 9           // 144, 16 steps × 9
    private const val N_CHAINS = 255
    private const val CHAIN_BYTES = 16 * 2            // 32, 16 rows × 2
    private const val N_TABLES = 256
    private const val TABLE_BYTES = 16 * 8            // 128, 16 rows × 8
    private const val N_GROOVES = 32
    private const val GROOVE_BYTES = 16
    private const val N_INSTRUMENTS = 128
    private const val INSTRUMENT_BYTES = M8iParser.BODY_SIZE   // 215 — same body as .m8i, no per-slot header

    private val DEFERRED_IMPORT_WARNINGS = listOf(
        // Per m8-files: the V4 layout shifted delay/reverb HP-LP filter cutoff
        // positions and the exact new offsets aren't yet known. Until they
        // are, those four fields keep the destination song's existing values.
        "Delay and reverb HP/LP filter cutoff are not imported on V4.x; using current defaults.",
        "Scale microtuning cent offsets are not imported yet; scale enable maps and names are imported.",
    )

    // Minimum file size: cover the tables block. The instrument pool sits
    // right after; older or truncated files without a full pool still load
    // with the playback-core fields and warnings indicating which slots
    // were missing.
    private const val MIN_SIZE = OFFSET_TABLES + N_TABLES * TABLE_BYTES
    private const val FULL_POOL_END = OFFSET_INSTRUMENTS + N_INSTRUMENTS * INSTRUMENT_BYTES

    data class Header(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val name: String,
        val tempo: Int,
        val transpose: Int,
        val quantize: Int,
        val key: Int,
    )

    /**
     * Per-track and global mixer levels parsed from the .m8s mixer block.
     * Mirrors only the fields our [MixerSettings] model carries; values not
     * present in our model (master_limit, analog/usb input mixer, dj_peak,
     * dj_filter_type) are skipped over while preserving sequential offsets.
     *
     * Note: per-track pan and per-track FX sends are NOT in the .m8s mixer
     * block — on real M8 they live on each instrument (`amp.pan`,
     * `amp.chorusSend`, etc.). They are therefore not represented here.
     */
    data class ParsedMixer(
        val masterVolume: Int,
        val trackVolumes: IntArray,   // length 8
        val chorusVolume: Int,
        val delayVolume: Int,
        val reverbVolume: Int,
        val djFilter: Int,
    )

    /**
     * Global chorus/delay/reverb settings parsed from the .m8s effects block.
     * Mirrors the fields m8-files reads for V4 — `delay_hp`, `delay_lp`,
     * `reverb_hp`, `reverb_lp` and `chorus_width` are intentionally absent
     * (their V4 storage location is not documented upstream).
     */
    data class ParsedFx(
        val chorusModDepth: Int,
        val chorusModFreq: Int,
        val chorusReverbSend: Int,
        val delayTimeL: Int,
        val delayTimeR: Int,
        val delayFeedback: Int,
        val delayWidth: Int,
        val delayReverbSend: Int,
        val reverbSize: Int,
        val reverbDamping: Int,
        val reverbModDepth: Int,
        val reverbModFreq: Int,
        val reverbWidth: Int,
    )

    data class ParsedScale(
        val name: String,
        val intervals: BooleanArray,
    )

    class ParsedSong(
        val header: Header,
        val songGrid: Array<IntArray>,
        val phrases: Array<Phrase>,
        val chains: Array<Chain>,
        val tables: Array<Table>,
        val grooves: Array<Groove>,
        /**
         * Up to 128 instruments parsed from the song's instrument pool (offset
         * 0x13A3E). Slots not present in the file (e.g. truncated files) and
         * slots whose kind byte is 0xFF (empty) come through as [M8Instrument]
         * placeholders with name "---" so callers don't need null-checks.
         * Always 128 entries.
         */
        val instruments: Array<M8Instrument>,
        val mixer: ParsedMixer,
        val fx: ParsedFx,
        val scales: Array<ParsedScale>,
        val warnings: List<String> = DEFERRED_IMPORT_WARNINGS,
    )

    fun parse(bytes: ByteArray): ParsedSong {
        if (bytes.size < MIN_SIZE) {
            throw IllegalArgumentException(
                "File too small for an M8 song: ${bytes.size} < $MIN_SIZE",
            )
        }
        val magic = String(bytes, 0, 9, Charsets.US_ASCII)
        if (magic != "M8VERSION") {
            throw IllegalArgumentException("Not an M8 file (bad magic: $magic)")
        }

        val header = parseHeader(bytes)
        if (header.major != 4) {
            throw IllegalArgumentException(
                "Unsupported M8 song version ${header.major}.${header.minor} — only 4.x is supported",
            )
        }

        val songGrid = parseSongGrid(bytes)
        val grooves = Array(N_GROOVES) { i -> parseGroove(bytes, OFFSET_GROOVE + i * GROOVE_BYTES) }
        val phrases = Array(N_PHRASES) { i -> parsePhrase(bytes, OFFSET_PHRASES + i * PHRASE_BYTES) }
        val chains = Array(N_CHAINS) { i -> parseChain(bytes, OFFSET_CHAINS + i * CHAIN_BYTES) }
        val tables = Array(N_TABLES) { i -> parseTable(bytes, OFFSET_TABLES + i * TABLE_BYTES) }
        val (instruments, instrumentWarnings) = parseInstrumentPool(bytes, header)
        val mixer = parseMixer(bytes)
        val fx = parseFx(bytes)
        val scales = parseScales(bytes)

        val warnings = DEFERRED_IMPORT_WARNINGS + instrumentWarnings
        return ParsedSong(header, songGrid, phrases, chains, tables, grooves, instruments, mixer, fx, scales, warnings)
    }

    /**
     * Parse the 32-byte mixer block at file offset 0xCE. Layout (sequential,
     * from m8-files settings::MixerSettings::from_reader):
     *
     *   1 master_volume
     *   1 master_limit               (not in our model — skipped)
     *   8 track_volume[0..7]
     *   1 chorus_volume
     *   1 delay_volume
     *   1 reverb_volume
     *   2 analog_input_volume(L, R)  (not in our model — skipped)
     *   1 usb_input_volume           (not in our model — skipped)
     *   2 analog_input_chorus(L, R)  (not in our model — skipped)
     *   2 analog_input_delay(L, R)   (not in our model — skipped)
     *   2 analog_input_reverb(L, R)  (not in our model — skipped)
     *   1 usb_input_chorus           (not in our model — skipped)
     *   1 usb_input_delay            (not in our model — skipped)
     *   1 usb_input_reverb           (not in our model — skipped)
     *   1 dj_filter
     *   1 dj_peak                    (not in our model — skipped)
     *   1 dj_filter_type             (not in our model — skipped)
     *   4 trailing discard
     *  ---
     *  32 bytes total
     */
    private fun parseMixer(bytes: ByteArray): ParsedMixer {
        var p = OFFSET_MIXER
        val masterVolume = bytes[p++].toInt() and 0xFF
        p++ // master_limit
        val trackVolumes = IntArray(8) { bytes[p++].toInt() and 0xFF }
        val chorusVolume = bytes[p++].toInt() and 0xFF
        val delayVolume = bytes[p++].toInt() and 0xFF
        val reverbVolume = bytes[p++].toInt() and 0xFF
        p += 12 // analog/usb input mixer block
        val djFilter = bytes[p].toInt() and 0xFF
        return ParsedMixer(
            masterVolume = masterVolume,
            trackVolumes = trackVolumes,
            chorusVolume = chorusVolume,
            delayVolume = delayVolume,
            reverbVolume = reverbVolume,
            djFilter = djFilter,
        )
    }

    /**
     * Parse the 17-byte global effects block at file offset 0x1A5C1. Layout
     * mirrors m8-files settings::EffectsSettings::from_reader for V4 — the
     * delay/reverb HP/LP cutoff bytes that exist on pre-V4 files are gone on
     * V4 (m8-files defaults them to 0; their new home isn't documented):
     *
     *   1 chorus_mod_depth
     *   1 chorus_mod_freq
     *   1 chorus_reverb_send
     *   3 unused
     *   1 delay_time_l
     *   1 delay_time_r
     *   1 delay_feedback
     *   1 delay_width
     *   1 delay_reverb_send
     *   1 unused
     *   1 reverb_size
     *   1 reverb_damping
     *   1 reverb_mod_depth
     *   1 reverb_mod_freq
     *   1 reverb_width
     *  ---
     *  17 bytes total (file offsets 0x1A5C1..0x1A5D1 inclusive)
     */
    private fun parseFx(bytes: ByteArray): ParsedFx {
        if (bytes.size < OFFSET_FX_SETTINGS + FX_SETTINGS_BYTES) {
            // Truncated files (older or partial) — fall back to neutral
            // defaults so callers don't crash; warnings still flag the
            // partial import via DEFERRED_IMPORT_WARNINGS.
            return ParsedFx(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        }
        var p = OFFSET_FX_SETTINGS
        val chorusModDepth = bytes[p++].toInt() and 0xFF
        val chorusModFreq = bytes[p++].toInt() and 0xFF
        val chorusReverbSend = bytes[p++].toInt() and 0xFF
        p += 3 // unused
        val delayTimeL = bytes[p++].toInt() and 0xFF
        val delayTimeR = bytes[p++].toInt() and 0xFF
        val delayFeedback = bytes[p++].toInt() and 0xFF
        val delayWidth = bytes[p++].toInt() and 0xFF
        val delayReverbSend = bytes[p++].toInt() and 0xFF
        p++ // unused
        val reverbSize = bytes[p++].toInt() and 0xFF
        val reverbDamping = bytes[p++].toInt() and 0xFF
        val reverbModDepth = bytes[p++].toInt() and 0xFF
        val reverbModFreq = bytes[p++].toInt() and 0xFF
        val reverbWidth = bytes[p].toInt() and 0xFF
        return ParsedFx(
            chorusModDepth, chorusModFreq, chorusReverbSend,
            delayTimeL, delayTimeR, delayFeedback, delayWidth, delayReverbSend,
            reverbSize, reverbDamping, reverbModDepth, reverbModFreq, reverbWidth,
        )
    }

    private fun parseScales(bytes: ByteArray): Array<ParsedScale> {
        if (bytes.size < OFFSET_SCALES + N_SCALES * SCALE_BYTES) {
            return Array(N_SCALES) { i -> defaultParsedScale(i) }
        }
        return Array(N_SCALES) { i -> parseScale(bytes, OFFSET_SCALES + i * SCALE_BYTES, i) }
    }

    private fun parseScale(bytes: ByteArray, offset: Int, index: Int): ParsedScale {
        val map = ByteBuffer.wrap(bytes, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
        val intervals = BooleanArray(12) { note -> ((map shr note) and 0x1) == 1 }
        val nameOffset = offset + 2 + 12 * 2
        val nameBytes = bytes.copyOfRange(nameOffset, nameOffset + 16)
        val name = decodeFixedAscii(nameBytes).ifBlank { defaultParsedScale(index).name }
        return ParsedScale(name, intervals)
    }

    private fun decodeFixedAscii(bytes: ByteArray): String {
        val end = bytes.indexOfFirst { it == 0.toByte() || it == 0xFF.toByte() }
            .let { if (it == -1) bytes.size else it }
        return String(bytes, 0, end, Charsets.US_ASCII).trim()
    }

    private fun defaultParsedScale(index: Int): ParsedScale {
        val scale = M8Song().scales[index.coerceIn(0, 15)]
        return ParsedScale(scale.name, scale.intervals.copyOf())
    }

    /**
     * Parse the 128-entry instrument pool. Each slot is a 215-byte body
     * identical in layout to the .m8i instrument body — the song header
     * carries the version, individual slots do not. Truncated files (no pool
     * at all, or a partial pool) yield placeholder instruments for the missing
     * slots plus a warning so the caller can surface it.
     */
    private fun parseInstrumentPool(
        bytes: ByteArray,
        header: Header,
    ): Pair<Array<M8Instrument>, List<String>> {
        val emptyInst = { M8Instrument("---", InstrumentType.WAVSYNTH) }
        if (bytes.size < OFFSET_INSTRUMENTS + INSTRUMENT_BYTES) {
            return Array(N_INSTRUMENTS) { emptyInst() } to listOf(
                "Instrument pool not present in this file; emulator defaults remain active.",
            )
        }

        val iHeader = M8iParser.Header(header.major, header.minor, header.patch)
        var failed = 0
        var loaded = 0
        val available = ((bytes.size - OFFSET_INSTRUMENTS) / INSTRUMENT_BYTES).coerceAtMost(N_INSTRUMENTS)
        val pool = Array(N_INSTRUMENTS) { i ->
            if (i >= available) return@Array emptyInst()
            val offset = OFFSET_INSTRUMENTS + i * INSTRUMENT_BYTES
            try {
                val inst = M8iParser.parseBodyAt(bytes, offset, iHeader)
                loaded++
                inst
            } catch (t: Throwable) {
                failed++
                emptyInst()
            }
        }

        val warnings = mutableListOf<String>()
        if (available < N_INSTRUMENTS) {
            warnings += "Instrument pool truncated: only $available of $N_INSTRUMENTS slots present in file."
        }
        if (failed > 0) {
            warnings += "$failed instrument slot(s) failed to parse; placeholders used."
        }
        return pool to warnings
    }

    private fun parseHeader(bytes: ByteArray): Header {
        // Version packing: byte 10 = lsb (minor<<4 | patch), byte 11 = msb (major)
        val lsb = bytes[10].toInt() and 0xFF
        val msb = bytes[11].toInt() and 0xFF
        val major = msb and 0x0F
        val minor = (lsb shr 4) and 0x0F
        val patch = lsb and 0x0F

        val transpose = bytes[POS_TRANSPOSE].toInt() and 0xFF
        val tempoF = ByteBuffer.wrap(bytes, POS_TEMPO, 4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .float
        val tempo = tempoF.toInt().coerceIn(40, 300)
        val quantize = bytes[POS_QUANTIZE].toInt() and 0xFF
        val key = bytes[POS_KEY].toInt() and 0xFF

        val nameBytes = bytes.copyOfRange(POS_NAME, POS_NAME + NAME_LEN)
        val end = nameBytes.indexOfFirst { it == 0.toByte() }
            .let { if (it == -1) nameBytes.size else it }
        val name = String(nameBytes, 0, end, Charsets.US_ASCII).trim()

        return Header(major, minor, patch, name, tempo, transpose, quantize, key)
    }

    private fun parseSongGrid(bytes: ByteArray): Array<IntArray> {
        val out = Array(256) { IntArray(8) }
        var p = OFFSET_SONG_GRID
        for (row in 0 until 256) {
            val r = out[row]
            for (t in 0 until 8) {
                r[t] = bytes[p++].toInt() and 0xFF
            }
        }
        return out
    }

    private fun parseGroove(bytes: ByteArray, offset: Int): Groove {
        val groove = Groove()
        var p = offset
        for (i in 0 until 16) {
            val ticks = bytes[p++].toInt() and 0xFF
            groove.ticks[i] = if (ticks == 0) 6 else ticks
        }
        return groove
    }

    private fun parsePhrase(bytes: ByteArray, offset: Int): Phrase {
        val ph = Phrase()
        var p = offset
        for (s in 0 until 16) {
            val step = ph.steps[s]
            step.note = bytes[p++].toInt() and 0xFF
            step.volume = bytes[p++].toInt() and 0xFF
            step.instrument = bytes[p++].toInt() and 0xFF
            step.fx1Cmd = bytes[p++].toInt() and 0xFF
            step.fx1Val = bytes[p++].toInt() and 0xFF
            step.fx2Cmd = bytes[p++].toInt() and 0xFF
            step.fx2Val = bytes[p++].toInt() and 0xFF
            step.fx3Cmd = bytes[p++].toInt() and 0xFF
            step.fx3Val = bytes[p++].toInt() and 0xFF
        }
        return ph
    }

    private fun parseChain(bytes: ByteArray, offset: Int): Chain {
        val ch = Chain()
        var p = offset
        for (r in 0 until 16) {
            val row = ch.rows[r]
            row.phrase = bytes[p++].toInt() and 0xFF
            // Transpose is signed on the M8 UI (displayed as %+03d).
            row.transpose = bytes[p++].toInt()
        }
        return ch
    }

    private fun parseTable(bytes: ByteArray, offset: Int): Table {
        val t = Table()
        var p = offset
        for (r in 0 until 16) {
            val row = t.rows[r]
            row.transpose = bytes[p++].toInt()
            row.volume = bytes[p++].toInt() and 0xFF
            row.fx1Cmd = bytes[p++].toInt() and 0xFF
            row.fx1Val = bytes[p++].toInt() and 0xFF
            row.fx2Cmd = bytes[p++].toInt() and 0xFF
            row.fx2Val = bytes[p++].toInt() and 0xFF
            row.fx3Cmd = bytes[p++].toInt() and 0xFF
            row.fx3Val = bytes[p++].toInt() and 0xFF
        }
        return t
    }

    /**
     * Copy the parsed instrument pool into [destination] in place, capped at
     * `min(parsed.size, destination.size)`. The emulator holds the instrument
     * array as a val; the ViewModel can't reassign it, so we copy slot by slot.
     * Returns the number of slots copied.
     */
    fun applyInstruments(parsed: Array<M8Instrument>, destination: Array<M8Instrument>): Int {
        val n = minOf(parsed.size, destination.size)
        for (i in 0 until n) destination[i] = parsed[i]
        return n
    }

    /**
     * Mutate [song] in place so it reflects [parsed]. The emulator holds
     * a single M8Song instance (val), so we can't reassign — we copy field
     * by field into the existing object.
     */
    fun applyTo(parsed: ParsedSong, song: M8Song) {
        song.name = parsed.header.name.ifBlank { "LOADED SONG" }
        song.tempo = parsed.header.tempo
        song.transpose = parsed.header.transpose
        song.quantize = parsed.header.quantize

        for (row in 0 until 256) {
            val src = parsed.songGrid[row]
            val dst = song.songGrid[row]
            for (t in 0 until 8) dst[t] = src[t]
        }
        for (i in 0 until N_PHRASES) {
            val src = parsed.phrases[i].steps
            val dst = song.phrases[i].steps
            for (s in 0 until 16) {
                dst[s].note = src[s].note
                dst[s].volume = src[s].volume
                dst[s].instrument = src[s].instrument
                dst[s].fx1Cmd = src[s].fx1Cmd
                dst[s].fx1Val = src[s].fx1Val
                dst[s].fx2Cmd = src[s].fx2Cmd
                dst[s].fx2Val = src[s].fx2Val
                dst[s].fx3Cmd = src[s].fx3Cmd
                dst[s].fx3Val = src[s].fx3Val
            }
        }
        for (i in 0 until N_CHAINS) {
            val src = parsed.chains[i].rows
            val dst = song.chains[i].rows
            for (r in 0 until 16) {
                dst[r].phrase = src[r].phrase
                dst[r].transpose = src[r].transpose
            }
        }
        for (i in 0 until N_GROOVES) {
            val src = parsed.grooves[i].ticks
            val dst = song.grooves[i].ticks
            for (s in 0 until 16) dst[s] = src[s]
        }
        for (i in 0 until N_TABLES) {
            val src = parsed.tables[i].rows
            val dst = song.tables[i].rows
            for (r in 0 until 16) {
                dst[r].transpose = src[r].transpose
                dst[r].volume = src[r].volume
                dst[r].fx1Cmd = src[r].fx1Cmd
                dst[r].fx1Val = src[r].fx1Val
                dst[r].fx2Cmd = src[r].fx2Cmd
                dst[r].fx2Val = src[r].fx2Val
                dst[r].fx3Cmd = src[r].fx3Cmd
                dst[r].fx3Val = src[r].fx3Val
            }
        }
        applyMixer(parsed.mixer, song.mixer)
        applyFx(parsed.fx, song)
        applyScales(parsed.scales, parsed.header.key, song)
    }

    /**
     * Copy parsed mixer levels into [dst] and reset per-track pan / FX sends
     * to neutral. On real M8, pan and sends are per-instrument (carried on
     * `AmpParams`), not per-track on the mixer — without this reset, leftover
     * values from the previous song (e.g. demo song's spread pans) would bleed
     * into a freshly loaded song's audio path.
     */
    private fun applyMixer(src: ParsedMixer, dst: MixerSettings) {
        dst.masterVolume = src.masterVolume
        dst.chorusVolume = src.chorusVolume
        dst.delayVolume = src.delayVolume
        dst.reverbVolume = src.reverbVolume
        dst.djFilter = src.djFilter
        for (t in 0 until 8) {
            dst.trackVolumes[t] = src.trackVolumes[t]
            dst.trackPans[t] = 0x80
            dst.trackChorusSend[t] = 0x00
            dst.trackDelaySend[t] = 0x00
            dst.trackReverbSend[t] = 0x00
        }
    }

    /**
     * Apply parsed global effects into [song]. Fields the V4 layout doesn't
     * carry (chorus.width, delay/reverb HP/LP cutoff) are intentionally not
     * touched — see DEFERRED_IMPORT_WARNINGS for the HP/LP gap.
     */
    private fun applyFx(src: ParsedFx, song: M8Song) {
        song.chorus.modDepth = src.chorusModDepth
        song.chorus.modFreq = src.chorusModFreq
        song.chorus.reverbSend = src.chorusReverbSend

        song.delay.timeL = src.delayTimeL
        song.delay.timeR = src.delayTimeR
        song.delay.feedback = src.delayFeedback
        song.delay.width = src.delayWidth
        song.delay.reverbSend = src.delayReverbSend

        song.reverb.size = src.reverbSize
        song.reverb.damping = src.reverbDamping
        song.reverb.modDepth = src.reverbModDepth
        song.reverb.modFreq = src.reverbModFreq
        song.reverb.width = src.reverbWidth
    }

    private fun applyScales(src: Array<ParsedScale>, songKey: Int, song: M8Song) {
        val key = songKey.coerceIn(0, 11)
        val count = minOf(src.size, song.scales.size)
        for (i in 0 until count) {
            val parsed = src[i]
            val dst = song.scales[i]
            dst.name = parsed.name
            dst.key = key
            for (n in 0 until 12) dst.intervals[n] = parsed.intervals[n]
        }
    }
}
