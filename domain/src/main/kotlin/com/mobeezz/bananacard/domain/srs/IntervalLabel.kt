package com.mobeezz.bananacard.domain.srs

import java.time.Duration
import kotlin.math.roundToLong

enum class IntervalUnit { LESS_THAN_MINUTE, MINUTES, HOURS, DAYS, MONTHS, YEARS }

data class IntervalLabel(val value: Long, val unit: IntervalUnit)

/** Buckets an interval for display on rating buttons (same thresholds as the Flutter app). */
fun intervalLabel(interval: Duration): IntervalLabel {
    val minutes = interval.toMinutes()
    val hours = interval.toHours()
    val days = interval.toDays()
    return when {
        minutes < 1 -> IntervalLabel(0, IntervalUnit.LESS_THAN_MINUTE)
        minutes < 60 -> IntervalLabel(minutes, IntervalUnit.MINUTES)
        hours < 24 -> IntervalLabel(hours, IntervalUnit.HOURS)
        days < 30 -> IntervalLabel(days, IntervalUnit.DAYS)
        days < 365 -> IntervalLabel((days / 30.0).roundToLong(), IntervalUnit.MONTHS)
        else -> IntervalLabel((days / 365.0).roundToLong(), IntervalUnit.YEARS)
    }
}
