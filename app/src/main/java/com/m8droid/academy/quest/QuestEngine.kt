package com.m8droid.academy.quest

import com.m8droid.academy.data.EmulatorSnapshot

data class QuestEvaluation(
    val complete: Boolean,
    val conditionResults: List<ConditionResult>,
)

data class ConditionResult(
    val condition: QuestCondition,
    val met: Boolean,
)

class QuestEngine {

    fun evaluate(quest: Quest, snapshot: EmulatorSnapshot): QuestEvaluation {
        val results = quest.conditions.map { condition ->
            ConditionResult(
                condition = condition,
                met = condition.evaluate(snapshot),
            )
        }
        return QuestEvaluation(
            complete = results.all { it.met },
            conditionResults = results,
        )
    }
}
