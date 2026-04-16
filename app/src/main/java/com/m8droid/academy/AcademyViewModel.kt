package com.m8droid.academy

import com.m8droid.academy.data.AcademyProgress
import com.m8droid.academy.data.AcademyRepository
import com.m8droid.academy.data.EmulatorEventRepository
import com.m8droid.academy.data.EmulatorSnapshot
import com.m8droid.academy.quest.Quest
import com.m8droid.academy.quest.QuestEngine
import com.m8droid.academy.quest.QuestEvaluation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class AcademyState {
    IDLE,
    QUEST_ACTIVE,
    QUEST_COMPLETE,
    NARRATIVE,
}

class AcademyViewModel(
    private val emulatorEvents: EmulatorEventRepository,
    private val repository: AcademyRepository? = null,
    private val scope: CoroutineScope? = null,
) {
    private val questEngine = QuestEngine()

    private val _state = MutableStateFlow(AcademyState.IDLE)
    val state: StateFlow<AcademyState> = _state.asStateFlow()

    private val _progress = MutableStateFlow(AcademyProgress())
    val progress: StateFlow<AcademyProgress> = _progress.asStateFlow()

    private val _activeQuest = MutableStateFlow<Quest?>(null)
    val activeQuest: StateFlow<Quest?> = _activeQuest.asStateFlow()

    private val _lastEvaluation = MutableStateFlow<QuestEvaluation?>(null)
    val lastEvaluation: StateFlow<QuestEvaluation?> = _lastEvaluation.asStateFlow()

    init {
        scope?.launch {
            repository?.progress?.collectLatest { saved ->
                _progress.value = saved
            }
        }
        scope?.launch {
            emulatorEvents.snapshots.collectLatest { snapshot ->
                evaluateActiveQuest(snapshot)
            }
        }
    }

    private fun evaluateActiveQuest(snapshot: EmulatorSnapshot) {
        val quest = _activeQuest.value ?: return
        if (_state.value != AcademyState.QUEST_ACTIVE) return

        val evaluation = questEngine.evaluate(quest, snapshot)
        _lastEvaluation.value = evaluation

        if (evaluation.complete) {
            completeQuest(quest.id, quest.xpReward)
        }
    }

    fun startQuest(quest: Quest) {
        _activeQuest.value = quest
        _lastEvaluation.value = null
        _state.value = AcademyState.QUEST_ACTIVE
    }

    fun startQuest() {
        _state.value = AcademyState.QUEST_ACTIVE
    }

    private fun completeQuest(questId: String, xp: Int) {
        val current = _progress.value
        _progress.value = current.copy(
            xp = current.xp + xp,
            completedQuestIds = current.completedQuestIds + questId,
        )
        _state.value = AcademyState.QUEST_COMPLETE
        _activeQuest.value = null
        persistProgress()
    }

    fun completeQuest(questId: String) {
        completeQuest(questId, 100)
    }

    fun advanceToNarrative() {
        _state.value = AcademyState.NARRATIVE
    }

    fun returnToIdle() {
        _state.value = AcademyState.IDLE
        _activeQuest.value = null
        _lastEvaluation.value = null
    }

    fun restoreProgress(progress: AcademyProgress) {
        _progress.value = progress
    }

    fun persistProgress() {
        scope?.launch {
            repository?.save(_progress.value)
        }
    }
}
