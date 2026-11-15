package com.mobeezz.bananacard.domain.planning

import com.mobeezz.bananacard.domain.fsrs.CardState
import com.mobeezz.bananacard.domain.model.CardSchedule
import com.mobeezz.bananacard.domain.model.Flashcard
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class DeckStatsTest {
    private val now = Instant.parse("2026-01-10T12:00:00Z")

    private fun newCard(id: String) = Flashcard(
        id = id, deckId = "d1", question = "q", answer = "a",
        schedule = null, createdAt = now, updatedAt = now,
    )

    private fun reviewCard(id: String, due: Instant, state: CardState) = Flashcard(
        id = id, deckId = "d1", question = "q", answer = "a",
        schedule = CardSchedule(state, null, 3.0, 5.0, due, now.minus(Duration.ofDays(1))),
        createdAt = now, updatedAt = now,
    )

    @Test
    fun emptyDeckHasZeroProgress() {
        assertEquals(DeckStats(0, 0.0, 0, 0), deckStats(emptyList(), now))
    }

    @Test
    fun learningCountExcludesNewCards() {
        val cards = listOf(
            newCard("n"),
            reviewCard("l", now.plusSeconds(600), CardState.LEARNING),
            reviewCard("rl", now.plusSeconds(600), CardState.RELEARNING),
            reviewCard("r", now.plusSeconds(600), CardState.REVIEW),
        )
        assertEquals(2, deckStats(cards, now).learningCount)
    }

    @Test
    fun progressCountsReviewStateOnly() {
        val future = now.plus(Duration.ofDays(3))
        val cards = listOf(
            reviewCard("a", future, CardState.REVIEW),
            reviewCard("b", future, CardState.REVIEW),
            reviewCard("c", future, CardState.LEARNING),
            newCard("d"),
        )
        val stats = deckStats(cards, now)
        assertEquals(0.5, stats.progress, 0.0)
        assertEquals(4, stats.total)
        assertEquals(1, stats.cardsToReview)
    }
}
