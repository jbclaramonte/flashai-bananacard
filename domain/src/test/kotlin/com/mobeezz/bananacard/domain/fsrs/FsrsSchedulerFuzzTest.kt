package com.mobeezz.bananacard.domain.fsrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

class FsrsSchedulerFuzzTest {

    private val t0: Instant = Instant.parse("2026-01-05T08:00:00Z")

    /** The `good_chain` reference scenario: GOOD each time, at these offsets from the previous review. */
    private val goodChainOffsets = listOf(
        Duration.ZERO,
        Duration.ofMinutes(10),
        Duration.ofDays(1),
        Duration.ofDays(3),
        Duration.ofDays(8),
    )

    @Test
    fun fuzzedIntervalStaysWithinBounds() {
        val unfuzzed = FsrsScheduler(enableFuzzing = false)
        val fuzzed = FsrsScheduler(enableFuzzing = true, random = Random(42))

        // Replay good_chain up to (not including) step 4.
        var card = FsrsCard.new(t0)
        var at = t0
        for (offset in goodChainOffsets.dropLast(1)) {
            at = at.plus(offset)
            card = unfuzzed.reviewCard(card, Rating.GOOD, at)
        }
        at = at.plus(goodChainOffsets.last())

        val plain = unfuzzed.reviewCard(card, Rating.GOOD, at)
        val fuzzy = fuzzed.reviewCard(card, Rating.GOOD, at)

        assertEquals(CardState.REVIEW, plain.state)
        val intervalDays = Duration.between(at, plain.due).toDays()
        assertTrue("precondition: interval $intervalDays must be fuzzable", intervalDays >= 3)

        // Bounds formula from fsrs.dart:1011-1046.
        var delta = 1.0
        for ((start, end, factor) in listOf(
            Triple(2.5, 7.0, 0.15),
            Triple(7.0, 20.0, 0.1),
            Triple(20.0, Double.POSITIVE_INFINITY, 0.05),
        )) {
            delta += factor * maxOf(minOf(intervalDays.toDouble(), end) - start, 0.0)
        }
        val maxIvl = minOf(Math.round(intervalDays + delta), 36500L)
        val minIvl = minOf(maxOf(2L, Math.round(intervalDays - delta)), maxIvl)

        val fuzzedInterval = Duration.between(at, fuzzy.due)
        assertEquals("fuzzed interval is whole days", 0L, fuzzedInterval.minusDays(fuzzedInterval.toDays()).toMillis())
        val fuzzedDays = fuzzedInterval.toDays()
        assertTrue(
            "fuzzed $fuzzedDays days outside [$minIvl, $maxIvl] around $intervalDays",
            fuzzedDays in minIvl..maxIvl,
        )

        // Fuzzing only moves the due date.
        assertEquals(plain.copy(due = fuzzy.due), fuzzy)
    }

    @Test
    fun intervalsBelowTwoAndHalfDaysAreNotFuzzed() {
        // Default steps: the first review stays in learning (10 min).
        assertFirstReviewUnchanged(learningSteps = DEFAULT_LEARNING_STEPS, rating = Rating.GOOD)

        // No learning steps: the first review goes straight to REVIEW with a 1-day interval.
        for (rating in listOf(Rating.AGAIN, Rating.HARD)) {
            val card = assertFirstReviewUnchanged(learningSteps = emptyList(), rating = rating)
            assertEquals(CardState.REVIEW, card.state)
            assertTrue(Duration.between(t0, card.due) < Duration.ofHours(60))
        }
    }

    private fun assertFirstReviewUnchanged(learningSteps: List<Duration>, rating: Rating): FsrsCard {
        val plain = FsrsScheduler(learningSteps = learningSteps, enableFuzzing = false)
            .reviewCard(FsrsCard.new(t0), rating, t0)
        val fuzzy = FsrsScheduler(learningSteps = learningSteps, enableFuzzing = true, random = Random(42))
            .reviewCard(FsrsCard.new(t0), rating, t0)
        assertEquals("$rating with steps $learningSteps", plain, fuzzy)
        return fuzzy
    }

    private companion object {
        val DEFAULT_LEARNING_STEPS = listOf(Duration.ofMinutes(1), Duration.ofMinutes(10))
    }
}
