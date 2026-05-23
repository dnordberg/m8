package com.m8droid.ui.academy

import com.m8droid.academy.data.EmulatorSnapshot
import com.m8droid.academy.quest.QuestEngine
import com.m8droid.emulator.M8Emulator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AcademyContentTest {
    @Test
    fun `drums chapter teaches first beat chains phrases and tables before synths`() {
        val drumQuests = getChapterQuests(0)
        val titles = drumQuests.map { it.title }

        assertTrue(titles.contains("First Beat"))
        assertTrue(titles.contains("Place a Chain"))
        assertTrue(titles.contains("Link a Phrase"))
        assertTrue(titles.contains("Enter a Note"))
        assertTrue(titles.contains("Phrase Steps"))
        assertTrue(titles.contains("Chain a Pattern"))
        assertTrue(titles.contains("Table Motion"))
        assertTrue(titles.indexOf("Chain a Pattern") > titles.indexOf("Phrase Steps"))
    }

    @Test
    fun `phrase steps quest follows cursor row not playback row`() {
        val quest = getChapterQuests(0).first { it.title == "Phrase Steps" }
        val engine = QuestEngine()

        val result = engine.evaluate(
            quest,
            EmulatorSnapshot(screen = M8Emulator.SCREEN_PHRASE, cursorY = 4, phraseRow = 0),
        )

        assertTrue(result.complete)
    }

    @Test
    fun `synths chapter has playable quests after drums are complete`() {
        val drumsComplete = getChapterQuests(0).map { it.id }.toSet()

        val synthQuests = getChapterQuests(1)

        assertEquals(4, synthQuests.size)
        assertTrue(synthQuests.all { it.id.startsWith("ch2_") })
        assertEquals(0, findNextQuest(1, drumsComplete))
    }
}
