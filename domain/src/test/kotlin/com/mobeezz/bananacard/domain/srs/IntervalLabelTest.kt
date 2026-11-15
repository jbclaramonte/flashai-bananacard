package com.mobeezz.bananacard.domain.srs

import java.time.Duration
import org.junit.Test
import org.junit.Assert.assertEquals

class IntervalLabelTest {
    private fun label(d: Duration) = intervalLabel(d)

    @Test fun underOneMinute() = assertEquals(IntervalLabel(0, IntervalUnit.LESS_THAN_MINUTE), label(Duration.ofSeconds(30)))
    @Test fun minutes() = assertEquals(IntervalLabel(59, IntervalUnit.MINUTES), label(Duration.ofMinutes(59)))
    @Test fun sixtyMinutesIsOneHour() = assertEquals(IntervalLabel(1, IntervalUnit.HOURS), label(Duration.ofMinutes(60)))
    @Test fun hours() = assertEquals(IntervalLabel(23, IntervalUnit.HOURS), label(Duration.ofHours(23)))
    @Test fun twentyFourHoursIsOneDay() = assertEquals(IntervalLabel(1, IntervalUnit.DAYS), label(Duration.ofHours(24)))
    @Test fun days() = assertEquals(IntervalLabel(29, IntervalUnit.DAYS), label(Duration.ofDays(29)))
    @Test fun thirtyDaysIsOneMonth() = assertEquals(IntervalLabel(1, IntervalUnit.MONTHS), label(Duration.ofDays(30)))
    @Test fun fortyFiveDaysRoundsHalfUpToTwoMonths() = assertEquals(IntervalLabel(2, IntervalUnit.MONTHS), label(Duration.ofDays(45)))
    @Test fun months() = assertEquals(IntervalLabel(12, IntervalUnit.MONTHS), label(Duration.ofDays(364)))
    @Test fun yearBoundary() = assertEquals(IntervalLabel(1, IntervalUnit.YEARS), label(Duration.ofDays(365)))
    @Test fun yearsRoundHalfUp() = assertEquals(IntervalLabel(2, IntervalUnit.YEARS), label(Duration.ofDays(548)))
}
