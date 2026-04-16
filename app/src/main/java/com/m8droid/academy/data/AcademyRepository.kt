package com.m8droid.academy.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.academyDataStore by preferencesDataStore(name = "academy_progress")

class AcademyRepository(private val context: Context) {

    companion object {
        private val KEY_CHAPTER = intPreferencesKey("current_chapter")
        private val KEY_QUEST_INDEX = intPreferencesKey("current_quest_index")
        private val KEY_XP = intPreferencesKey("xp")
        private val KEY_COMPLETED = stringSetPreferencesKey("completed_quest_ids")
    }

    val progress: Flow<AcademyProgress> = context.academyDataStore.data.map { prefs ->
        AcademyProgress(
            currentChapter = prefs[KEY_CHAPTER] ?: 0,
            currentQuestIndex = prefs[KEY_QUEST_INDEX] ?: 0,
            xp = prefs[KEY_XP] ?: 0,
            completedQuestIds = prefs[KEY_COMPLETED] ?: emptySet(),
        )
    }

    suspend fun save(progress: AcademyProgress) {
        context.academyDataStore.edit { prefs ->
            prefs[KEY_CHAPTER] = progress.currentChapter
            prefs[KEY_QUEST_INDEX] = progress.currentQuestIndex
            prefs[KEY_XP] = progress.xp
            prefs[KEY_COMPLETED] = progress.completedQuestIds
        }
    }
}
