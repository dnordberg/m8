package com.m8droid.ui.academy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AcademyContentTest {
    @Test
    fun `synths chapter has playable quests after drums are complete`() {
        val drumsComplete = setOf("ch1_q1", "ch1_q2", "ch1_q3", "ch1_q4")

        val synthQuests = getChapterQuests(1)

        assertEquals(4, synthQuests.size)
        assertTrue(synthQuests.all { it.id.startsWith("ch2_") })
        assertEquals(0, findNextQuest(1, drumsComplete))
    }
}
