package com.mobeezz.bananacard.domain.streak

import com.mobeezz.bananacard.domain.repository.ActivityRepository
import com.mobeezz.bananacard.domain.repository.SettingsRepository
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first

data class StreakSnapshot(val current: Int, val best: Int)

class StreakService(
    private val activity: ActivityRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val zone: () -> ZoneId,
) {
    /** Computes current from activity, persists best = max(stored, current), returns both. */
    suspend fun refresh(): StreakSnapshot {
        val today = LocalDate.ofInstant(clock.instant(), zone())
        val days = activity.activeDaysSince(LocalDate.EPOCH)
        val current = currentStreak(days, today)
        val stored = settings.bestStreak.first()
        val best = maxOf(stored, current)
        if (best != stored) settings.setBestStreak(best)
        return StreakSnapshot(current, best)
    }
}
