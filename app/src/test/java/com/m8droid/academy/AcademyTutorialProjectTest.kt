package com.m8droid.academy

import com.m8droid.emulator.M8Song
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AcademyTutorialProjectTest {
    @Test
    fun `fresh tutorial project starts from empty song not demo arrangement`() {
        val restored = AcademyTutorialProject.freshSession()
        val song = restored.song

        assertEquals("ACADEMY 01", song.name)
        assertEquals(96, song.tempo)
        assertNotEquals("NIGHTCIRCUIT", song.name)
        assertTrue(song.songGrid.all { row -> row.all { it == M8Song.EMPTY } }, "tutorial must not inherit demo chains")
        assertTrue(song.chains.all { it.isEmpty() }, "tutorial must teach chain placement from blank chains")
        assertTrue(song.phrases.all { it.isEmpty() }, "tutorial must teach note entry from blank phrases")
    }

    @Test
    fun `fresh tutorial project keeps default playable instruments available`() {
        val restored = AcademyTutorialProject.freshSession()

        assertEquals(128, restored.instruments.size)
        assertTrue(restored.instruments.take(8).all { it.name.isNotBlank() })
    }
}
