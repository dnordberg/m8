package com.m8droid.emulator

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProjectHealthTest {
    @Test
    fun warnsAboutSamplerInstrumentsWhoseSamplesAreMissing() {
        val instruments = M8Instrument.createDefaults()
        instruments[3] = M8Instrument("KICK", InstrumentType.SAMPLER).apply {
            sampler.samplePath = "/Samples/Drums/kick.wav"
        }
        instruments[7] = M8Instrument("SNARE", InstrumentType.SAMPLER).apply {
            sampler.samplePath = "Samples/Drums/snare.wav"
        }
        val sdRoot = createTempDir()
        File(sdRoot, "Samples/Drums/snare.wav").apply {
            parentFile!!.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3))
        }

        val warnings = ProjectHealth.checkSamples(instruments, sdRoot)

        assertEquals(1, warnings.missingSamples.size)
        assertEquals(3, warnings.missingSamples.single().instrumentSlot)
        assertEquals("KICK", warnings.missingSamples.single().instrumentName)
        assertEquals("/Samples/Drums/kick.wav", warnings.missingSamples.single().samplePath)
        assertTrue(warnings.userMessage().contains("KICK"))
        assertTrue(warnings.userMessage().contains("kick.wav"))
    }

    @Test
    fun ignoresBlankSamplerPathsAndRejectsTraversalAsMissing() {
        val instruments = M8Instrument.createDefaults()
        instruments[0] = M8Instrument("EMPTY SAMPLER", InstrumentType.SAMPLER).apply {
            sampler.samplePath = ""
        }
        instruments[1] = M8Instrument("BAD PATH", InstrumentType.SAMPLER).apply {
            sampler.samplePath = "../outside.wav"
        }
        val sdRoot = createTempDir()
        File(sdRoot.parentFile, "outside.wav").writeBytes(byteArrayOf(1))

        val warnings = ProjectHealth.checkSamples(instruments, sdRoot)

        assertEquals(1, warnings.missingSamples.size)
        assertEquals("BAD PATH", warnings.missingSamples.single().instrumentName)
        assertEquals("../outside.wav", warnings.missingSamples.single().samplePath)
    }
}
