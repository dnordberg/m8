package com.m8droid.academy.quest

import com.m8droid.academy.data.EmulatorSnapshot

sealed interface QuestCondition {

    fun evaluate(snapshot: EmulatorSnapshot): Boolean

    fun describe(): String

    data class IsPlaying(val expected: Boolean = true) : QuestCondition {
        override fun evaluate(snapshot: EmulatorSnapshot) = snapshot.playing == expected
        override fun describe() = if (expected) "Playback is running" else "Playback is stopped"
    }

    data class OnScreen(val screen: Int) : QuestCondition {
        override fun evaluate(snapshot: EmulatorSnapshot) = snapshot.screen == screen
        override fun describe() = "Navigate to screen $screen"
    }

    data class BpmInRange(val min: Int, val max: Int) : QuestCondition {
        override fun evaluate(snapshot: EmulatorSnapshot) = snapshot.bpm in min..max
        override fun describe() = "Set tempo between $min and $max BPM"
    }

    data class PhraseRowAt(val row: Int) : QuestCondition {
        override fun evaluate(snapshot: EmulatorSnapshot) = snapshot.phraseRow == row
        override fun describe() = "Phrase row is at step $row"
    }

    data class CursorYAtLeast(val row: Int) : QuestCondition {
        override fun evaluate(snapshot: EmulatorSnapshot) = snapshot.cursorY >= row
        override fun describe() = "Move cursor to row $row or below"
    }

    data class EditModeActive(val expected: Boolean = true) : QuestCondition {
        override fun evaluate(snapshot: EmulatorSnapshot) = snapshot.editMode == expected
        override fun describe() = if (expected) "Enter edit mode" else "Exit edit mode"
    }

    data class AllOf(val conditions: List<QuestCondition>) : QuestCondition {
        override fun evaluate(snapshot: EmulatorSnapshot) = conditions.all { it.evaluate(snapshot) }
        override fun describe() = conditions.joinToString("; ") { it.describe() }
    }
}
