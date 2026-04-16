package com.m8droid.academy

import com.m8droid.academy.data.AcademyProgress
import com.m8droid.academy.data.EmulatorEventRepository
import com.m8droid.academy.data.EmulatorSnapshot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AcademyViewModelTest {

    private lateinit var events: EmulatorEventRepository
    private lateinit var vm: AcademyViewModel

    @BeforeEach
    fun setup() {
        events = EmulatorEventRepository()
        vm = AcademyViewModel(events)
    }

    @Test
    fun `initial state is IDLE`() {
        assertEquals(AcademyState.IDLE, vm.state.value)
    }

    @Test
    fun `state transitions idle to quest_active to quest_complete to narrative to idle`() {
        assertEquals(AcademyState.IDLE, vm.state.value)

        vm.startQuest()
        assertEquals(AcademyState.QUEST_ACTIVE, vm.state.value)

        vm.completeQuest("ch1_q1")
        assertEquals(AcademyState.QUEST_COMPLETE, vm.state.value)

        vm.advanceToNarrative()
        assertEquals(AcademyState.NARRATIVE, vm.state.value)

        vm.returnToIdle()
        assertEquals(AcademyState.IDLE, vm.state.value)
    }

    @Test
    fun `completeQuest adds XP and records quest ID`() {
        vm.startQuest()
        vm.completeQuest("ch1_q1")

        val progress = vm.progress.value
        assertEquals(100, progress.xp)
        assertTrue(progress.completedQuestIds.contains("ch1_q1"))
    }

    @Test
    fun `completing multiple quests accumulates XP`() {
        vm.startQuest()
        vm.completeQuest("ch1_q1")
        vm.returnToIdle()
        vm.startQuest()
        vm.completeQuest("ch1_q2")

        val progress = vm.progress.value
        assertEquals(200, progress.xp)
        assertEquals(setOf("ch1_q1", "ch1_q2"), progress.completedQuestIds)
    }

    @Test
    fun `restoreProgress recovers saved state`() {
        val saved = AcademyProgress(
            currentChapter = 2,
            currentQuestIndex = 3,
            xp = 450,
            completedQuestIds = setOf("ch1_q1", "ch1_q2", "ch2_q1"),
        )
        vm.restoreProgress(saved)

        assertEquals(saved, vm.progress.value)
    }

    @Test
    fun `emulator snapshot emits without error`() = runTest {
        events.emit(EmulatorSnapshot(playing = true, bpm = 140))
        // No assertion needed — verify no crash on emit
    }
}
