package com.m8droid.emulator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class M8DemoSongTest {
    @Test
    fun `startup demo is a four track techno sketch grouped A through D`() {
        val song = M8Song().apply { loadDemoSong() }

        assertEquals("NEON GRID", song.name)
        assertEquals(128, song.tempo)
        assertEquals(listOf(0x00, 0x02, 0x04, 0x06), song.songGrid[0].take(4))
        assertTrue(song.songGrid[0].drop(4).all { it == M8Song.EMPTY }, "startup demo should stay focused on A-D tracks")

        val drumPhrase = song.chains[0x00].rows[0].phrase
        val bassPhrase = song.chains[0x02].rows[0].phrase
        val chordPhrase = song.chains[0x04].rows[0].phrase
        val melodyPhrase = song.chains[0x06].rows[0].phrase

        assertTrue(song.phrases[drumPhrase].steps.any { it.instrument == 3 }, "track A should use the hat/noise drum instrument")
        assertTrue(song.phrases[bassPhrase].steps.any { it.instrument == 1 }, "track B should use bass")
        assertTrue(song.phrases[chordPhrase].steps.any { it.instrument == 2 }, "track C should use pad/piano-style chords")
        assertTrue(song.phrases[melodyPhrase].steps.any { it.instrument == 5 }, "track D should use a plucky melody instrument")
    }

    @Test
    fun `startup demo bass showcases audible table automation`() {
        val song = M8Song().apply { loadDemoSong() }
        val bassPhrase = song.chains[0x02].rows[0].phrase
        val openingStep = song.phrases[bassPhrase].steps[0]

        assertEquals(M8FxEngine.FX_TBL, openingStep.fx1Cmd, "bass should immediately enable a table so FX are phone-testable")
        assertEquals(0x00, openingStep.fx1Val)
        assertEquals(M8FxEngine.FX_TIC, openingStep.fx2Cmd, "bass table should use explicit tick-rate control")
        assertEquals(0x01, openingStep.fx2Val)

        val table = song.tables[0x00]
        assertTrue(table.rows.any { it.transpose == 12 }, "table should include an obvious octave jump")
        assertTrue(table.rows.any { it.volume != M8Song.EMPTY && it.volume < 0x70 }, "table should include audible volume gating")
        assertTrue(table.rows.any { it.fx1Cmd == M8FxEngine.FX_PAN }, "table should move pan so automation is visible/audible")
        assertTrue(table.rows.any { it.fx2Cmd == M8FxEngine.FX_SDL }, "table should bump delay send for a tail")
    }

    @Test
    fun `old demo remains available under explicit old demo loader`() {
        val oldDemo = M8Song().apply { loadOldDemoSong() }
        val startupDemo = M8Song().apply { loadDemoSong() }

        assertEquals("NIGHTCIRCUIT", oldDemo.name)
        assertEquals(118, oldDemo.tempo)
        assertEquals(0x07, oldDemo.songGrid[0][2])
        assertFalse(oldDemo.name == startupDemo.name)
    }

    @Test
    fun `emulator boots into new demo`() {
        val emulator = M8Emulator()

        assertEquals("NEON GRID", emulator.song.name)
        assertEquals(128, emulator.song.tempo)
    }

    @Test
    fun `demo loaders reset custom scale definitions`() {
        val song = M8Song().apply {
            scales[1].name = "CUSTOM"
            scales[1].key = 7
            scales[1].intervals.fill(false)
        }

        song.loadDemoSong()
        assertEquals("MAJOR", song.scales[1].name)
        assertEquals(0, song.scales[1].key)
        assertTrue(song.scales[1].intervals.contentEquals(booleanArrayOf(true, false, true, false, true, true, false, true, false, true, false, true)))

        song.scales[2].name = "BROKEN"
        song.scales[2].key = 5
        song.scales[2].intervals.fill(true)
        song.loadOldDemoSong()
        assertEquals("MINOR", song.scales[2].name)
        assertEquals(0, song.scales[2].key)
        assertTrue(song.scales[2].intervals.contentEquals(booleanArrayOf(true, false, true, true, false, true, false, true, true, false, true, false)))
    }

    @Test
    fun `built in projects seed new and old demo files for loading later`() {
        val dir = Files.createTempDirectory("m8-demo-projects").toFile()
        try {
            val seeded = BuiltInDemoProjects.ensureSeeded(dir)
            val projects = M8ProjectLibrary.list(dir)

            assertEquals(setOf("New Demo", "Old Demo"), seeded.map { it.nameWithoutExtension }.toSet())
            assertEquals(setOf("New Demo.m8droid", "Old Demo.m8droid"), projects.map { it.fileName }.toSet())
            assertEquals("NEON GRID", M8ProjectLibrary.load(File(dir, "New Demo.m8droid")).song.name)
            assertEquals("NIGHTCIRCUIT", M8ProjectLibrary.load(File(dir, "Old Demo.m8droid")).song.name)
        } finally {
            dir.deleteRecursively()
        }
    }
}
