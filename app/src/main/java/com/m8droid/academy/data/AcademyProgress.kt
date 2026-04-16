package com.m8droid.academy.data

data class AcademyProgress(
    val currentChapter: Int = 0,
    val currentQuestIndex: Int = 0,
    val xp: Int = 0,
    val completedQuestIds: Set<String> = emptySet(),
)
