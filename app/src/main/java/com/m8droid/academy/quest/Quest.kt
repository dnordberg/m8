package com.m8droid.academy.quest

data class Quest(
    val id: String,
    val chapter: Int,
    val title: String,
    val briefing: String,
    val conditions: List<QuestCondition>,
    val xpReward: Int = 100,
    val hintText: String = "",
)
