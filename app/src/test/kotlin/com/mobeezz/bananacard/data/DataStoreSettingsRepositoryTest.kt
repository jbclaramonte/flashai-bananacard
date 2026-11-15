package com.mobeezz.bananacard.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.mobeezz.bananacard.data.settings.DataStoreSettingsRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreSettingsRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun createRepo(scope: CoroutineScope): DataStoreSettingsRepository {
        val store = PreferenceDataStoreFactory.create(scope = scope) {
            File(tmp.root, "settings.preferences_pb")
        }
        return DataStoreSettingsRepository(store)
    }

    @Test
    fun defaultsAreFiveAndZero() = runTest {
        val repo = createRepo(backgroundScope)
        assertEquals(5, repo.newCardsPerDay.first())
        assertEquals(0, repo.bestStreak.first())
    }

    @Test
    fun newCardsPerDayPersists() = runTest {
        val repo = createRepo(backgroundScope)
        repo.setNewCardsPerDay(10)
        assertEquals(10, repo.newCardsPerDay.first())
    }

    @Test
    fun invalidCapIsRejected() = runTest {
        val repo = createRepo(backgroundScope)
        try {
            repo.setNewCardsPerDay(7)
            fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
        assertEquals(5, repo.newCardsPerDay.first())
    }

    @Test
    fun bestStreakPersists() = runTest {
        val repo = createRepo(backgroundScope)
        repo.setBestStreak(12)
        assertEquals(12, repo.bestStreak.first())
    }
}
