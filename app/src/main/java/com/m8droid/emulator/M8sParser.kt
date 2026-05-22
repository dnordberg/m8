package com.m8droid.emulator

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parser for Dirtywave M8 song files (.m8s), version 4.x.
 *
 * Based on the binary layout documented in the m8-files Rust crate
 * (AlexCharlton/m8-files). This parser currently imports the
 * playback-critical subset: header, tempo/name/transpose/quantize,
 * song grid, phrases, chains, grooves, and tables. Later passes should
 * add instrument pool, mixer/FX settings, scales, EQ, and MIDI mappings;
 * until then [ParsedSong.warnings] surfaces the partial-import caveats.
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

    // Header field positions (relative to file start, after the 14-byte
    // version block). directory(128) starts at 14.
    private const val POS_TRANSPOSE = 14 + 128        // 142
    private const val POS_TEMPO = POS_TRANSPOSE + 1   // 143 (f32 LE)
    private const val POS_QUANTIZE = POS_TEMPO + 4    // 147
    private const val POS_NAME = POS_QUANTIZE + 1     // 148 (12 bytes)
    private const val NAME_LEN = 12

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
        "Mixer and global FX settings are not imported yet; Android defaults remain active.",
        "Scale definitions/custom microtuning are not imported yet.",
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

        val warnings = DEFERRED_IMPORT_WARNINGS + instrumentWarnings
        return ParsedSong(header, songGrid, phrases, chains, tables, grooves, instruments, warnings)
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

        val nameBytes = bytes.copyOfRange(POS_NAME, POS_NAME + NAME_LEN)
        val end = nameBytes.indexOfFirst { it == 0.toByte() }
            .let { if (it == -1) nameBytes.size else it }
        val name = String(nameBytes, 0, end, Charsets.US_ASCII).trim()

        return Header(major, minor, patch, name, tempo, transpose, quantize)
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
    }
}
