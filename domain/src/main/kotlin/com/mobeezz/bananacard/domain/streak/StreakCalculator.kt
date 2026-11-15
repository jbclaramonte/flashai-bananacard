package com.mobeezz.bananacard.domain.streak

import java.time.LocalDate

/**
 * Consecutive active days going backwards, starting from [today] if active,
 * otherwise from yesterday (the streak is still alive until the day ends).
 * Calendar-day arithmetic only, so DST shifts cannot break a streak.
 */
fun currentStreak(activeDays: Set<LocalDate>, today: LocalDate): Int {
    var day = if (today in activeDays) today else today.minusDays(1)
    var count = 0
    while (day in activeDays) {
        count++
        day = day.minusDays(1)
    }
    return count
}
