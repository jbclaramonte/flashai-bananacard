package com.mobeezz.bananacard.domain.streak

import com.mobeezz.bananacard.domain.fakes.FakeActivityRepository
import com.mobeezz.bananacard.domain.fakes.FakeSettingsRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class StreakServiceTest {
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 5, 10)
    private val clock = Clock.fixed(Instant.parse("2026-05-10T12:00:00Z"), zone)
    private val activity = FakeActivityRepository()
    private val settings = FakeSettingsRepository()
    private val service = StreakService(activity, settings, clock) { zone }

    private fun activeLast(n: Int) {
        activity.setActiveDays((0 until n).map { today.minusDays(it.toLong()) }.toSet())
    }

    @Test fun bestIsRaisedWhenCurrentExceedsIt() = runTest {
        settings.setBestStreak(2)
        settings.bestStreakWrites = 0
        activeLast(3)

        assertEquals(StreakSnapshot(3, 3), service.refresh())
        assertEquals(3, settings.bestStreak.first())
        assertEquals(1, settings.bestStreakWrites)
    }

    @Test fun bestIsKeptWhenCurrentIsLower() = runTest {
        settings.setBestStreak(10)
        settings.bestStreakWrites = 0
        activeLast(1)

        assertEquals(StreakSnapshot(1, 10), service.refresh())
        assertEquals(10, settings.bestStreak.first())
        assertEquals(0, settings.bestStreakWrites)
    }

    @Test fun refreshUsesZoneProviderForToday() = runTest {
        var current = ZoneId.of("UTC")
        val svc = StreakService(activity, settings, Clock.fixed(Instant.parse("2026-05-10T23:30:00Z"), ZoneId.of("UTC"))) { current }
        activity.setActiveDays(setOf(LocalDate.of(2026, 5, 11)))
        assertEquals(0, svc.refresh().current)
        current = ZoneId.of("Asia/Tokyo") // 2026-05-11 08:30 local
        assertEquals(1, svc.refresh().current)
    }
}
