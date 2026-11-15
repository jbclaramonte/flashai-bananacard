package com.mobeezz.bananacard.domain.srs

import com.mobeezz.bananacard.domain.fsrs.CardState
import com.mobeezz.bananacard.domain.fsrs.Rating
import com.mobeezz.bananacard.domain.model.CardSchedule
import com.mobeezz.bananacard.domain.model.Flashcard
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ReviewSchedulerTest {
    private val now = Instant.parse("2026-01-01T10:00:00Z")
    private val scheduler = ReviewScheduler.default(Random(42))

    private fun newCard() = Flashcard(
        id = "c1", deckId = "d1", question = "hola", answer = "hello",
        schedule = null, createdAt = now, updatedAt = now,
    )

    private fun reviewCard(): Flashcard {
        var card = newCard()
        var t = now
        repeat(3) {
            card = scheduler.review(card, Rating.GOOD, t)
            t = card.schedule!!.due
        }
        return card
    }

    @Test
    fun newCardRatedGoodGetsSchedule() {
        val card = newCard()
        assertTrue(card.isNew)
        val reviewed = scheduler.review(card, Rating.GOOD, now)
        val s = reviewed.schedule
        assertNotNull(s)
        s!!
        assertEquals(CardState.LEARNING, s.state)
        assertEquals(now, s.lastReview)
        assertEquals(now, reviewed.updatedAt)
    }

    @Test
    fun previewHasFourRatingsAndIsDeterministic() {
        val card = reviewCard()
        assertEquals(CardState.REVIEW, card.schedule!!.state)
        val at = card.schedule.due
        val a = scheduler.preview(card, at)
        val b = scheduler.preview(card, at)
        assertEquals(a, b)
        assertEquals(Rating.entries.toSet(), a.keys)
        val again = a.getValue(Rating.AGAIN)
        val hard = a.getValue(Rating.HARD)
        val good = a.getValue(Rating.GOOD)
        val easy = a.getValue(Rating.EASY)
        assertTrue("again<hard", again < hard)
        assertTrue("hard<=good", hard <= good)
        assertTrue("good<easy", good < easy)
        assertTrue(again >= Duration.ZERO)
    }

    @Test
    fun previewOfNewCardWorks() {
        val a = scheduler.preview(newCard(), now)
        assertEquals(4, a.size)
    }

    @Test
    fun reviewKeepsQuestionAndAnswer() {
        val reviewed = scheduler.review(newCard(), Rating.EASY, now)
        assertEquals("hola", reviewed.question)
        assertEquals("hello", reviewed.answer)
        assertEquals("c1", reviewed.id)
        assertEquals("d1", reviewed.deckId)
        assertEquals(now, reviewed.createdAt)
        assertNull(newCard().schedule)
    }

    // Production configuration (spec 7.3): retention 0.9, steps 1m/10m, relearning 10m, max 36500d, fuzz on.

    private fun longIntervalCard() = newCard().copy(
        schedule = CardSchedule(
            state = CardState.REVIEW, step = null, stability = 1_000_000.0, difficulty = 5.0,
            due = now, lastReview = now.minus(Duration.ofDays(1000)),
        ),
    )

    @Test
    fun defaultNewCardPreviewUsesLearningSteps() {
        val p = ReviewScheduler.default().preview(newCard(), now)
        assertEquals(Duration.ofMinutes(1), p.getValue(Rating.AGAIN))
        assertEquals(Duration.ofSeconds(330), p.getValue(Rating.HARD)) // midpoint of 1m and 10m
        assertEquals(Duration.ofMinutes(10), p.getValue(Rating.GOOD))
    }

    @Test
    fun defaultReviewCardAgainPreviewsRelearningStep() {
        val card = reviewCard()
        assertEquals(CardState.REVIEW, card.schedule!!.state)
        val p = ReviewScheduler.default().preview(card, card.schedule.due)
        assertEquals(Duration.ofMinutes(10), p.getValue(Rating.AGAIN))

        val lapsed = ReviewScheduler.default(Random(1)).review(card, Rating.AGAIN, card.schedule.due)
        assertEquals(CardState.RELEARNING, lapsed.schedule!!.state)
        assertEquals(Duration.ofMinutes(10), Duration.between(card.schedule.due, lapsed.schedule.due))
    }

    @Test
    fun defaultDesiredRetentionAndWeightsGiveExpectedFirstInterval() {
        // At 0.9 retention the interval equals the stability, here the initial Easy stability w[3] = 16.1507.
        val p = ReviewScheduler.default().preview(newCard(), now)
        assertEquals(Duration.ofDays(16), p.getValue(Rating.EASY))
    }

    @Test
    fun defaultCapsIntervalAtThirtySixFiveHundredDays() {
        val max = Duration.ofDays(36500)
        val preview = ReviewScheduler.default().preview(longIntervalCard(), now)
        assertEquals(max, preview.getValue(Rating.EASY))
        assertEquals(max, preview.getValue(Rating.GOOD))

        repeat(20) { seed ->
            val due = ReviewScheduler.default(Random(seed)).review(longIntervalCard(), Rating.GOOD, now).schedule!!.due
            assertTrue("seed $seed exceeds the cap", Duration.between(now, due) <= max)
        }
    }

    @Test
    fun defaultFuzzVariesLongIntervalsAndIsSeedDeterministic() {
        fun dueFor(seed: Int) =
            ReviewScheduler.default(Random(seed)).review(longReviewCard(), Rating.GOOD, now).schedule!!.due

        assertEquals(dueFor(1), dueFor(1))
        assertTrue("different seeds should fuzz differently", dueFor(1) != dueFor(2))
        assertTrue("fuzz must be applied: real review differs from the unfuzzed preview for some seed",
            (1..20).any { seed ->
                val preview = ReviewScheduler.default().preview(longReviewCard(), now).getValue(Rating.GOOD)
                Duration.between(now, dueFor(seed)) != preview
            })
    }

    private fun longReviewCard() = newCard().copy(
        schedule = CardSchedule(
            state = CardState.REVIEW, step = null, stability = 1000.0, difficulty = 5.0,
            due = now, lastReview = now.minus(Duration.ofDays(1000)),
        ),
    )
}
