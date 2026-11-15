package com.mobeezz.bananacard.domain.streak

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class StreakCalculatorTest {
    private val today = LocalDate.of(2026, 5, 10)

    @Test fun noActivityIsZero() {
        assertEquals(0, currentStreak(emptySet(), today))
    }

    @Test fun todayOnlyIsOne() {
        assertEquals(1, currentStreak(setOf(today), today))
    }

    @Test fun yesterdayOnlyIsOne() {
        assertEquals(1, currentStreak(setOf(today.minusDays(1)), today))
    }

    @Test fun gapBreaksStreak() {
        val days = setOf(today.minusDays(3), today.minusDays(2), today)
        assertEquals(1, currentStreak(days, today))
    }

    @Test fun fiveConsecutiveEndingYesterdayIsFive() {
        val days = (1L..5L).map { today.minusDays(it) }.toSet()
        assertEquals(5, currentStreak(days, today))
    }

    @Test fun olderActivityOnlyIsZero() {
        assertEquals(0, currentStreak(setOf(today.minusDays(2)), today))
    }

    @Test fun dstChangeDoesNotBreakStreak() {
        val zone = ZoneId.of("Europe/Paris")
        val days = setOf(LocalDate.of(2026, 3, 28), LocalDate.of(2026, 3, 29), LocalDate.of(2026, 3, 30))
        // 2026-03-29 is a 23-hour day in Paris; day arithmetic must be calendar-based.
        assertEquals(23, java.time.Duration.between(
            ZonedDateTime.of(2026, 3, 29, 0, 0, 0, 0, zone),
            ZonedDateTime.of(2026, 3, 30, 0, 0, 0, 0, zone),
        ).toHours())
        assertEquals(3, currentStreak(days, LocalDate.of(2026, 3, 30)))
    }
}
