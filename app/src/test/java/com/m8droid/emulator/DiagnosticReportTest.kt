package com.m8droid.emulator

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiagnosticReportTest {
    @Test
    fun reportIncludesProjectStateAndWarnings() {
        val song = M8Song().apply {
            name = "Smoke Test"
            tempo = 123
        }
        val warnings = ProjectHealth.Warnings(
            missingSamples = listOf(
                ProjectHealth.MissingSample(2, "KICK", "/Samples/kick.wav"),
            ),
        )

        val report = DiagnosticReport.render(
            song = song,
            instruments = M8Instrument.createDefaults(),
            isDirty = true,
            status = "RESTORE FAILED: Missing Project",
            warnings = warnings,
            recent = listOf("PROJECT: Demo @ /m8sd/Projects/Demo.m8droid"),
        )

        assertTrue(report.contains("M8Droid Diagnostics"))
        assertTrue(report.contains("Song: Smoke Test"))
        assertTrue(report.contains("Tempo: 123"))
        assertTrue(report.contains("Dirty: true"))
        assertTrue(report.contains("RESTORE FAILED"))
        assertTrue(report.contains("Missing samples"))
        assertTrue(report.contains("KICK"))
        assertTrue(report.contains("/Samples/kick.wav"))
        assertTrue(report.contains("PROJECT: Demo"))
    }
}
