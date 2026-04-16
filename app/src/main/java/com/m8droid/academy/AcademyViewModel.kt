package com.m8droid.academy

import com.m8droid.academy.data.AcademyProgress
import com.m8droid.academy.data.EmulatorEventRepository
import com.m8droid.academy.data.EmulatorSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AcademyState {
    IDLE,
    QUEST_ACTIVE,
    QUEST_COMPLETE,
    NARRATIVE,
}

class AcademyViewModel(
    private val emulatorEvents: EmulatorEventRepository,
) {
    private val _state = MutableStateFlow(AcademyState.IDLE)
    val state: StateFlow<AcademyState> = _state.asStateFlow()

    private val _progress = MutableStateFlow(AcademyProgress())
    val progress: StateFlow<AcademyProgress> = _progress.asStateFlow()

    fun startQuest() {
        _state.value = AcademyState.QUEST_ACTIVE
    }

    fun completeQuest(questId: String) {
        val current = _progress.value
        _progress.value = current.copy(
            xp = current.xp + 100,
            completedQuestIds = current.completedQuestIds + questId,
        )
        _state.value = AcademyState.QUEST_COMPLETE
    }

    fun advanceToNarrative() {
        _state.value = AcademyState.NARRATIVE
    }

    fun returnToIdle() {
        _state.value = AcademyState.IDLE
    }

    fun restoreProgress(progress: AcademyProgress) {
        _progress.value = progress
    }
}
