package com.mobeezz.bananacard.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.mobeezz.bananacard.domain.planning.StudyPlanner
import com.mobeezz.bananacard.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val newCardsPerDay: Flow<Int> =
        dataStore.data.map { it[NEW_CARDS_PER_DAY] ?: StudyPlanner.DEFAULT_CAP }

    override suspend fun setNewCardsPerDay(value: Int) {
        require(value in StudyPlanner.ALLOWED_CAPS) {
            "newCardsPerDay must be one of ${StudyPlanner.ALLOWED_CAPS}, was $value"
        }
        dataStore.edit { it[NEW_CARDS_PER_DAY] = value }
    }

    override val bestStreak: Flow<Int> =
        dataStore.data.map { it[BEST_STREAK] ?: 0 }

    override suspend fun setBestStreak(value: Int) {
        dataStore.edit { it[BEST_STREAK] = value }
    }

    companion object {
        const val DATASTORE_NAME = "settings"
        private val NEW_CARDS_PER_DAY = intPreferencesKey("new_cards_per_day")
        private val BEST_STREAK = intPreferencesKey("best_streak")
    }
}
