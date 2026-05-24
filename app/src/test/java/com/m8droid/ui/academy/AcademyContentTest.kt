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
    fun `synths chapter teaches bass patch and deeper shaping after drums are complete`() {
        val drumsComplete = getChapterQuests(0).map { it.id }.toSet()

        val synthQuests = getChapterQuests(1)
        val titles = synthQuests.map { it.title }

        assertEquals(7, synthQuests.size)
        assertTrue(synthQuests.all { it.id.startsWith("ch2_") })
        assertTrue(titles.contains("Bass Patch"))
        assertTrue(titles.contains("Shape the Filter"))
        assertTrue(titles.contains("Envelope Feel"))
        assertTrue(titles.indexOf("Bass Patch") > titles.indexOf("Ready to Shape"))
        assertEquals(0, findNextQuest(1, drumsComplete))
    }

    @Test
    fun `sampling chapter has playable sample practice quests`() {
        val synthsComplete = getChapterQuests(1).map { it.id }.toSet()

        val samplerQuests = getChapterQuests(2)
        val titles = samplerQuests.map { it.title }

        assertEquals(5, samplerQuests.size)
        assertTrue(samplerQuests.all { it.id.startsWith("ch3_") })
        assertTrue(titles.contains("Open the Sample Crate"))
        assertTrue(titles.contains("Load a Sampler Slot"))
        assertTrue(titles.contains("Trigger a Sample"))
        assertTrue(titles.contains("Sampler Loop Check"))
        assertEquals(0, findNextQuest(2, synthsComplete))
    }

    @Test
    fun `fx chapter teaches runtime fx and table motion`() {
        val samplerComplete = getChapterQuests(2).map { it.id }.toSet()

        val fxQuests = getChapterQuests(3)
        val titles = fxQuests.map { it.title }

        assertEquals(6, fxQuests.size)
        assertTrue(fxQuests.all { it.id.startsWith("ch4_") })
        assertTrue(titles.contains("Per-Step FX"))
        assertTrue(titles.contains("Table Automation"))
        assertTrue(titles.contains("Hear the Motion"))
        assertEquals(0, findNextQuest(3, samplerComplete))
    }

    @Test
    fun `fx chapter teaches slide bend and retrigger polish`() {
        val fxQuests = getChapterQuests(3)
        val titles = fxQuests.map { it.title }

        assertEquals(6, fxQuests.size)
        assertTrue(titles.contains("Slide Into Notes"))
        assertTrue(titles.contains("Retrig Fills"))
        assertTrue(fxQuests.any { it.briefing.contains("PSL") && it.briefing.contains("PBN") })
        assertTrue(fxQuests.any { it.briefing.contains("RET") })
    }
}
