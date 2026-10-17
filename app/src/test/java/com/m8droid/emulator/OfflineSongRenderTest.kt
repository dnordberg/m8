package com.m8droid.emulator

import com.m8droid.audio.SampleCache
import com.m8droid.audio.WavDecoder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/** Bounded offline render of the shipped factory song: the same trigger/voice path the phone uses. */
class OfflineSongRenderTest {
    @TempDir lateinit var sdRoot: File

    private fun assets() = listOf(File("src/main/assets"), File("app/src/main/assets")).first { it.isDirectory }

    /** Same order as M8ViewModel.configureTrackInstrument: voice, zones, then sample. */
    private fun configure(synth: M8Synth, cache: SampleCache, track: Int, inst: M8Instrument) {
        synth.configureVoice(track, inst)
        synth.loadSampleZones(track, inst.sampler.zones.mapNotNull { zone ->
            cache.load(zone.path)?.let { zone.rootMidi to it }
        })
        if (inst.type == InstrumentType.SAMPLER) synth.loadSample(track, cache.load(inst.sampler.samplePath))
        else synth.loadSample(track, null)
    }

    private class Rendered(val left: FloatArray, val right: FloatArray, val sectionFrames: IntArray, val noteRows: Int) {
        fun rms(from: Int, to: Int): Double {
            var sum = 0.0
            for (i in from until to) sum += left[i] * left[i] + right[i] * right[i]
            return sqrt(sum / (2.0 * (to - from)))
        }
        fun peak(from: Int, to: Int): Double {
            var p = 0.0
            for (i in from until to) p = maxOf(p, abs(left[i].toDouble()), abs(right[i].toDouble()))
            return p
        }
        val frames get() = left.size
    }

    private fun render(): Rendered {
        val bank = SoundBank.install(sdRoot) { File(assets(), it).readBytes() }
        val cache = SampleCache(sdRoot)
        val song = M8Song().apply { loadDemoSong() }
        val instruments = MeridianDemo.instruments()
        val synth = M8Synth()
        synth.mixerSettings = song.mixer
        val emulator = M8Emulator().also { it.song.loadDemoSong() }
        val rowDuration = (M8Synth.SAMPLE_RATE * 60.0 / (song.tempo * 4.0)).toInt()
        val left = ArrayList<Float>(rowDuration * 48 * 16)
        val right = ArrayList<Float>(rowDuration * 48 * 16)
        val sectionFrames = IntArray(MeridianDemo.sectionNames.size)
        var noteRows = 0
        MeridianDemo.sectionBars.forEachIndexed { section, bars ->
            for (bar in 0 until bars) for (step in 0 until 16) {
                val rowData = emulator.resolveRowDataAt(section, bar, step)
                if (rowData.any { it[0] in 1..127 }) noteRows++
                for (track in 0..7) configure(synth, cache, track, instruments[rowData[track][1].coerceIn(0, instruments.size - 1)])
                synth.triggerRow(rowData)
                val swing = synth.getSwingDelaySamples((step + 1) % 16, song.tempo)
                var frames = 0
                while (frames < rowDuration + swing) {
                    val pcm = synth.generateChunk()
                    var i = 0
                    while (i < pcm.size && frames < rowDuration + swing) {
                        left.add((((pcm[i + 1].toInt() and 0xFF) shl 8) or (pcm[i].toInt() and 0xFF)).toShort() / 32768f)
                        right.add((((pcm[i + 3].toInt() and 0xFF) shl 8) or (pcm[i + 2].toInt() and 0xFF)).toShort() / 32768f)
                        i += 4; frames++
                    }
                }
                sectionFrames[section] = left.size
            }
        }
        // Post-song: the runtime stops with allNotesOff; the voice tail must clear and never stick.
        synth.allNotesOff()
        repeat(40) {
            val pcm = synth.generateChunk()
            var i = 0
            while (i < pcm.size) {
                left.add((((pcm[i + 1].toInt() and 0xFF) shl 8) or (pcm[i].toInt() and 0xFF)).toShort() / 32768f)
                right.add((((pcm[i + 3].toInt() and 0xFF) shl 8) or (pcm[i + 2].toInt() and 0xFF)).toShort() / 32768f)
                i += 4
            }
        }
        return Rendered(left.toFloatArray(), right.toFloatArray(), sectionFrames, noteRows)
    }

    private fun writeWav(rendered: Rendered, file: File) {
        file.parentFile?.mkdirs()
        val data = ByteArray(rendered.frames * 2)
        for (i in 0 until rendered.frames) {
            val mono = (((rendered.left[i] + rendered.right[i]) * 0.5) * 32767f).toInt().coerceIn(-32768, 32767)
            data[i * 2] = (mono and 0xFF).toByte()
            data[i * 2 + 1] = (mono shr 8).toByte()
        }
        val bytes = wavBytes(rendered.frames, data)
        assertEquals(rendered.frames, WavDecoder.decode(bytes).frameCount)
        file.writeBytes(bytes)
    }

    private fun wavBytes(frames: Int, data: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        fun ascii(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
        fun le32(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF); out.write((v shr 16) and 0xFF); out.write((v shr 24) and 0xFF) }
        fun le16(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }
        ascii("RIFF"); le32(36 + data.size); ascii("WAVEfmt "); le32(16); le16(1); le16(1)
        le32(M8Synth.SAMPLE_RATE); le32(M8Synth.SAMPLE_RATE * 2); le16(2); le16(16)
        ascii("data"); le32(data.size); out.write(data)
        return out.toByteArray()
    }

    @Test fun `factory song renders a bounded measurable arrangement without clipping or stuck notes`() {
        val rendered = render()
        val songFrames = rendered.sectionFrames.last()
        val rowDuration = (M8Synth.SAMPLE_RATE * 60.0 / (108 * 4.0)).toInt()
        val swing = (M8Synth.SAMPLE_RATE * 60.0 / (108 * 4.0) * 0.15).toInt()
        assertEquals(384 * rowDuration + 384 * (rowDuration + swing), songFrames, "bounded song length with the runtime swing")
        assertTrue(rendered.noteRows > 200, "arrangement must stay active: ${rendered.noteRows} note rows")

        val peak = rendered.peak(0, songFrames)
        val rms = rendered.rms(0, songFrames)
        val headroomDb = 20 * log10(peak)
        assertTrue(peak <= 1.0, "no hard clipping: peak $peak")
        assertTrue(headroomDb in -12.0..-0.5, "audible but headroom-safe peak: $headroomDb dBFS")
        assertTrue(20 * log10(rms) > -34.0, "song must not be near-silent: rms $rms")

        var silent = 0; var longestSilence = 0; var clippedRuns = 0; var run = 0
        for (i in 0 until songFrames) {
            val loud = maxOf(abs(rendered.left[i]), abs(rendered.right[i]))
            if (loud < 0.0005) { silent++; longestSilence = maxOf(longestSilence, ++run) } else run = 0
            if (loud >= 0.999) clippedRuns++ else clippedRuns = 0
            assertTrue(clippedRuns < 60, "no sustained clipping at frame $i")
        }
        assertTrue(longestSilence < M8Synth.SAMPLE_RATE / 2, "no dead gap longer than 0.5s: $longestSilence frames")
        assertTrue(silent.toDouble() / songFrames < 0.35, "song must be mostly sounding: $silent silent frames")

        // Sparse intro, dense full sections, quieter breakdown, widened final.
        var prev = 0
        val levels = rendered.sectionFrames.map { frame ->
            rendered.rms(prev, frame).also { prev = frame }
        }
        assertTrue(levels[0] < levels[2], "intro quieter than verse: $levels")
        assertTrue(levels[4] < levels[6], "breakdown quieter than final: $levels")
        assertTrue(levels[3] > levels[1], "theme fuller than build: $levels")
        assertTrue(levels.distinct().size >= 5, "sections must not be one repeated loop: $levels")

        // Tail after allNotesOff must decay to digital silence with no stuck voices.
        val tail = rendered.peak(songFrames, rendered.frames)
        assertTrue(tail < 0.05, "post-stop tail must clear: $tail")
        assertTrue(rendered.left.copyOfRange(rendered.frames - 1000, rendered.frames).all { it == 0f },
            "no stuck notes after stop")
        assertTrue((0 until songFrames).none { abs(rendered.left[it]) > 0.999 && it > 0 }, "left channel bounded")

        val out = File(System.getenv("M8_RENDER_DIR") ?: "build/reports/m8-render").apply { mkdirs() }
        writeWav(rendered, File(out, "meridian-light.wav"))
        File(out, "meridian-light.txt").writeText(
            "frames=$songFrames sections=${rendered.sectionFrames.joinToString(",")} " +
            "sectionRms=${levels.joinToString(",") { "%.4f".format(it) }} " +
            "peak=${"%.4f".format(peak)} rms=${"%.4f".format(rms)} noteRows=${rendered.noteRows}")
        assertTrue(File(out, "meridian-light.wav").length() > 44 + songFrames, "wav preview written")
    }
}
