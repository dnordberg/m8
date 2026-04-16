package com.m8droid.academy

import com.m8droid.academy.data.AcademyProgress
import com.m8droid.academy.data.EmulatorEventRepository
import com.m8droid.academy.data.EmulatorSnapshot
import com.m8droid.academy.quest.Quest
import com.m8droid.academy.quest.QuestCondition
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
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
    }

    @Test
    fun `startQuest with Quest object sets active quest`() {
        val quest = Quest(
            id = "ch1_q1", chapter = 1, title = "First Steps",
            briefing = "Start playing",
            conditions = listOf(QuestCondition.IsPlaying(true)),
        )
        vm.startQuest(quest)

        assertEquals(AcademyState.QUEST_ACTIVE, vm.state.value)
        assertEquals(quest, vm.activeQuest.value)
    }

    @Test
    fun `returnToIdle clears active quest and evaluation`() {
        val quest = Quest(
            id = "ch1_q1", chapter = 1, title = "Test",
            briefing = "Test", conditions = listOf(QuestCondition.IsPlaying(true)),
        )
        vm.startQuest(quest)
        vm.returnToIdle()

        assertEquals(AcademyState.IDLE, vm.state.value)
        assertNull(vm.activeQuest.value)
        assertNull(vm.lastEvaluation.value)
    }

    @Test
    fun `quest auto-completes when snapshot meets all conditions`() = runTest {
        val scope = TestScope()
        val localEvents = EmulatorEventRepository()
        val localVm = AcademyViewModel(localEvents, scope = scope)

        val quest = Quest(
            id = "ch1_q1", chapter = 1, title = "Start Playing",
            briefing = "Hit play", conditions = listOf(QuestCondition.IsPlaying(true)),
            xpReward = 50,
        )
        localVm.startQuest(quest)
        scope.advanceUntilIdle()

        localEvents.emit(EmulatorSnapshot(playing = true))
        scope.advanceUntilIdle()

        assertEquals(AcademyState.QUEST_COMPLETE, localVm.state.value)
        assertEquals(50, localVm.progress.value.xp)
        assertTrue(localVm.progress.value.completedQuestIds.contains("ch1_q1"))
    }

    @Test
    fun `quest does not auto-complete when conditions not met`() = runTest {
        val scope = TestScope()
        val localEvents = EmulatorEventRepository()
        val localVm = AcademyViewModel(localEvents, scope = scope)

        val quest = Quest(
            id = "ch1_q1", chapter = 1, title = "Play Fast",
            briefing = "Play at 150+ BPM",
            conditions = listOf(
                QuestCondition.IsPlaying(true),
                QuestCondition.BpmInRange(150, 300),
            ),
        )
        localVm.startQuest(quest)
        scope.advanceUntilIdle()

        localEvents.emit(EmulatorSnapshot(playing = true, bpm = 120))
        scope.advanceUntilIdle()

        assertEquals(AcademyState.QUEST_ACTIVE, localVm.state.value)
        assertNotNull(localVm.lastEvaluation.value)
        assertFalse(localVm.lastEvaluation.value!!.complete)
    }
}
