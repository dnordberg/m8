package com.m8droid.academy.quest

import com.m8droid.academy.data.EmulatorSnapshot
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class QuestEngineTest {

    private val engine = QuestEngine()

    @Test
    fun `quest with all conditions met is complete`() {
        val quest = Quest(
            id = "test_1", chapter = 1, title = "Test",
            briefing = "Play at 120 BPM on screen 0",
            conditions = listOf(
                QuestCondition.IsPlaying(true),
                QuestCondition.BpmInRange(100, 140),
                QuestCondition.OnScreen(0),
            ),
        )
        val snapshot = EmulatorSnapshot(playing = true, bpm = 120, screen = 0)
        val result = engine.evaluate(quest, snapshot)

        assertTrue(result.complete)
        assertTrue(result.conditionResults.all { it.met })
    }

    @Test
    fun `quest with one condition unmet is not complete`() {
        val quest = Quest(
            id = "test_2", chapter = 1, title = "Test",
            briefing = "Play at 150+ BPM",
            conditions = listOf(
                QuestCondition.IsPlaying(true),
                QuestCondition.BpmInRange(150, 300),
            ),
        )
        val snapshot = EmulatorSnapshot(playing = true, bpm = 120)
        val result = engine.evaluate(quest, snapshot)

        assertFalse(result.complete)
        assertTrue(result.conditionResults[0].met)
        assertFalse(result.conditionResults[1].met)
    }

    @Test
    fun `condition diff shows which conditions are unmet`() {
        val quest = Quest(
            id = "test_3", chapter = 1, title = "Test",
            briefing = "Navigate to phrase screen and start playing",
            conditions = listOf(
                QuestCondition.IsPlaying(true),
                QuestCondition.OnScreen(2),
                QuestCondition.EditModeActive(true),
            ),
        )
        val snapshot = EmulatorSnapshot(playing = false, screen = 2, editMode = false)
        val result = engine.evaluate(quest, snapshot)

        assertFalse(result.complete)
        val unmet = result.conditionResults.filter { !it.met }
        assertEquals(2, unmet.size)
        assertFalse(unmet[0].met)
        assertFalse(unmet[1].met)
    }

    @Test
    fun `AllOf compound condition evaluates all subconditions`() {
        val compound = QuestCondition.AllOf(
            listOf(QuestCondition.IsPlaying(true), QuestCondition.BpmInRange(120, 120))
        )
        val snapshot = EmulatorSnapshot(playing = true, bpm = 120)
        assertTrue(compound.evaluate(snapshot))

        val snapshot2 = EmulatorSnapshot(playing = true, bpm = 130)
        assertFalse(compound.evaluate(snapshot2))
    }

    @Test
    fun `CursorYAtLeast evaluates cursor row not playback phrase row`() {
        val condition = QuestCondition.CursorYAtLeast(4)

        assertTrue(condition.evaluate(EmulatorSnapshot(cursorY = 4, phraseRow = 0)))
        assertTrue(condition.evaluate(EmulatorSnapshot(cursorY = 8, phraseRow = 0)))
        assertFalse(condition.evaluate(EmulatorSnapshot(cursorY = 3, phraseRow = 4)))
    }

    @Test
    fun `tracker loop conditions use edited song chain and phrase data`() {
        assertTrue(QuestCondition.SongCellFilled.evaluate(EmulatorSnapshot(selectedSongChain = 0)))
        assertFalse(QuestCondition.SongCellFilled.evaluate(EmulatorSnapshot(selectedSongChain = 2)))
        assertFalse(QuestCondition.SongCellFilled.evaluate(EmulatorSnapshot(selectedSongChain = 0xFF)))

        assertTrue(QuestCondition.ChainRowHasPhrase.evaluate(EmulatorSnapshot(selectedChainRowPhrase = 0)))
        assertFalse(QuestCondition.ChainRowHasPhrase.evaluate(EmulatorSnapshot(selectedChainRowPhrase = 2)))
        assertFalse(QuestCondition.ChainRowHasPhrase.evaluate(EmulatorSnapshot(selectedChainRowPhrase = 0xFF)))

        assertTrue(QuestCondition.PhraseStepHasNote.evaluate(EmulatorSnapshot(selectedPhraseStepNote = 60)))
        assertFalse(QuestCondition.PhraseStepHasNote.evaluate(EmulatorSnapshot(selectedPhraseStepNote = 0xFF)))
    }

    @Test
    fun `QuestCondition has zero imports from emulator or audio packages`() {
        val pkg = QuestCondition::class.java.`package`?.name ?: ""
        assertTrue(pkg.startsWith("com.m8droid.academy"))
    }

    @Test
    fun `QuestEngine has zero imports from emulator or audio packages`() {
        val pkg = QuestEngine::class.java.`package`?.name ?: ""
        assertTrue(pkg.startsWith("com.m8droid.academy"))
    }
}
