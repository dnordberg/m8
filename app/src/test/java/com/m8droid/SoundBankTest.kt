package com.m8droid

import com.m8droid.emulator.*
import com.m8droid.audio.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SoundBankTest {
    @TempDir lateinit var temp: File
    private fun assets() = listOf(File("src/main/assets"), File("app/src/main/assets")).first { it.isDirectory }
    private fun bank() = SoundBank.install(temp) { File(assets(), it).readBytes() }
    private fun row(note: Int) = Array(8) { if(it == 0) intArrayOf(note, 0, 220) else intArrayOf(255, 255, 255) }

    @Test fun `bundled bank installs offline and repairs copies with playable PCM`() {
        val bank = bank()
        assertTrue(bank.samples.isNotEmpty())
        val cache = SampleCache(temp)
        bank.samples.forEach { sample ->
            val decoded = cache.load(sample.path)!!
            assertTrue(decoded.samples.any { kotlin.math.abs(it) > 0.01 }, sample.id)
            val synth = M8Synth()
            synth.configureVoice(0, bank.instrument(sample.id))
            synth.loadSample(0, decoded)
            synth.triggerRow(row(sample.rootMidi))
            assertTrue((0..8).any { synth.generateChunk().any { it != 0.toByte() } }, sample.id)
        }
        val target = File(temp, bank.samples.first().path.removePrefix("/"))
        target.writeText("interrupted")
        bank()
        assertNotNull(SampleCache(temp).load(bank.samples.first().path))
    }

    @Test fun `assignment isolates chosen high slot and all preset parameters survive reopen`() {
        val bank = bank()
        val pool = M8Instrument.createDefaults()
        val untouched = pool[0]
        bank.sounds.forEach { sound ->
            bank.assign(sound.id, 92, pool)
            assertSame(untouched, pool[0])
            val encoded = M8ProjectSnapshot.encode(M8Song(), pool)
            val restored = M8ProjectSnapshot.decode(encoded)
            assertArrayEquals(encoded, M8ProjectSnapshot.encode(restored.song, restored.instruments), sound.id)
            assertEquals(pool[92].sampler.zones, restored.instruments[92].sampler.zones)
        }
    }

    @Test fun `sample playback uses actual root and nearest zone rather than fixed middle C`() {
        val synth = M8Synth()
        val pcm = WavDecoder.DecodedWav(M8Synth.SAMPLE_RATE, 1, FloatArray(200000) { if(it % 100 < 50) 0.5f else -0.5f })
        val inst = M8Instrument(type = InstrumentType.SAMPLER).apply {
            sampler.rootMidi = 36
            modulation.env1 = Envelope(attack = 0, sustain = 255)
        }
        synth.configureVoice(0, inst); synth.loadSample(0, pcm)
        synth.triggerRow(row(36)); synth.generateChunk()
        val rootPosition = synth.getSamplePosition(0)
        synth.triggerRow(row(48)); synth.generateChunk()
        assertEquals(rootPosition * 2, synth.getSamplePosition(0), 0.01)
        synth.loadSampleZones(0, listOf(36 to pcm, 48 to pcm))
        synth.triggerRow(row(48)); synth.generateChunk()
        assertEquals(rootPosition, synth.getSamplePosition(0), 0.01)
        synth.triggerRow(row(47)); synth.generateChunk()
        assertEquals(rootPosition * Math.pow(2.0, -1.0 / 12), synth.getSamplePosition(0), 0.01)
    }

    @Test fun `original presets all generate distinct nonzero PCM`() {
        val bank = SoundBank(emptyList())
        val outputs = SoundBank.presets.map { preset ->
            val synth = M8Synth(); synth.configureVoice(0, bank.instrument(preset.id)); synth.triggerRow(row(48))
            val bytes = (0..12).flatMap { synth.generateChunk().toList() }.toByteArray()
            assertTrue(bytes.any { it != 0.toByte() }, preset.id)
            bytes.contentHashCode()
        }
        assertEquals(outputs.size, outputs.distinct().size)
    }


    @Test fun `manifest rejects traversal and reads arbitrary bass roots`() {
        val json = """{"version":1,"samples":[{"id":"acoustic_bass_41","name":"Bass","category":"bass","file":"b.wav","rootMidi":41,"loop":false,"source":"test","license":"CC0"}]}"""
        val b = SoundBank.parse(json)
        assertEquals(41, b.instrument("acoustic_bass").sampler.zones.single().rootMidi)
        assertThrows(IllegalArgumentException::class.java) { SoundBank.parse(json.replace("b.wav", "../b.wav")) }
    }

    @Test fun `original UI has no bank controls and runtime still loads samples`() {
        val root = listOf(File("src/main/java/com/m8droid"), File("app/src/main/java/com/m8droid")).first { it.isDirectory }
        val ui = File(root, "ui/BrowseDialog.kt").readText()
        assertFalse(ui.contains("SOUNDS / BEATS"))
        assertFalse(ui.contains("SoundBankPane"))
        assertFalse(File(root, "ui/SoundBankPane.kt").exists())
        val vm = File(root, "M8ViewModel.kt").readText()
        assertTrue(vm.contains("synth.loadSampleZones(track, inst.sampler.zones"))
        assertTrue(vm.contains("SoundBank.install(sdRoot)"))
        assertFalse(vm.contains("loadBankBeat"))
    }
}
