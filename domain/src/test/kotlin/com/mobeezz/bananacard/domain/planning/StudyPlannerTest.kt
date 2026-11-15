package com.mobeezz.bananacard.domain.planning

import com.mobeezz.bananacard.domain.fsrs.CardState
import com.mobeezz.bananacard.domain.model.CardSchedule
import com.mobeezz.bananacard.domain.model.Deck
import com.mobeezz.bananacard.domain.model.Flashcard
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyPlannerTest {
    private val now = Instant.parse("2026-01-10T12:00:00Z")

    private fun deck(paused: Boolean = false) = Deck(
        id = "d1", name = "Deck", icon = "📚", iconColor = 0xFFE5F0FF, type = null,
        sourceLanguage = null, targetLanguage = null, description = null,
        isPaused = paused, createdAt = now, updatedAt = now,
    )

    private fun newCard(id: String) = Flashcard(
        id = id, deckId = "d1", question = "q$id", answer = "a$id",
        schedule = null, createdAt = now, updatedAt = now,
    )

    private fun reviewCard(id: String, due: Instant, state: CardState = CardState.REVIEW) = Flashcard(
        id = id, deckId = "d1", question = "q$id", answer = "a$id",
        schedule = CardSchedule(state, null, 3.0, 5.0, due, now.minus(Duration.ofDays(1))),
        createdAt = now, updatedAt = now,
    )

    @Test
    fun pausedDeckPlansNothing() {
        val cards = listOf(reviewCard("r", now.minusSeconds(60)), newCard("n"))
        val plan = StudyPlanner.planDeck(deck(paused = true), cards, 5, 0, now)
        assertEquals(DeckPlan(emptyList(), emptyList()), plan)
        assertEquals(DeckSection.PAUSED, StudyPlanner.sectionOf(deck(paused = true), plan))
    }

    @Test
    fun dueCardsSortedByDueAscending() {
        val cards = listOf(
            reviewCard("c", now.minusSeconds(10)),
            reviewCard("a", now.minusSeconds(300)),
            reviewCard("b", now.minusSeconds(100)),
        )
        val plan = StudyPlanner.planDeck(deck(), cards, 5, 0, now)
        assertEquals(listOf("a", "b", "c"), plan.dueCards.map { it.id })
    }

    @Test
    fun cardDueWithinOneMinuteIsDue() {
        assertTrue(StudyPlanner.isDue(reviewCard("x", now.plusSeconds(59)), now))
        assertFalse(StudyPlanner.isDue(reviewCard("y", now.plusSeconds(61)), now))
        assertFalse(StudyPlanner.isDue(newCard("z"), now))
    }

    @Test
    fun newCardsKeepInputOrderAndRespectRemainingCap() {
        val cards = (1..10).map { newCard("n$it") }
        val plan = StudyPlanner.planDeck(deck(), cards, 5, 2, now)
        assertEquals(listOf("n1", "n2", "n3"), plan.newCards.map { it.id })
    }

    @Test
    fun capLoweredBelowStartedGivesZeroNew() {
        val cards = (1..4).map { newCard("n$it") }
        assertEquals(0, StudyPlanner.planDeck(deck(), cards, 5, 7, now).plannedNew)
    }

    @Test
    fun capZeroGivesNoNew() {
        val cards = (1..4).map { newCard("n$it") }
        assertEquals(0, StudyPlanner.planDeck(deck(), cards, 0, 0, now).plannedNew)
    }

    @Test
    fun sectionToReviewWinsOverNew() {
        val cards = listOf(reviewCard("r", now.minusSeconds(60)), newCard("n"))
        val plan = StudyPlanner.planDeck(deck(), cards, 5, 0, now)
        assertEquals(1, plan.plannedDue)
        assertEquals(1, plan.plannedNew)
        assertEquals(DeckSection.TO_REVIEW, StudyPlanner.sectionOf(deck(), plan))
    }

    @Test
    fun startedDeckWithOnlyNewLeftIsNewCardsSection() {
        val cards = listOf(reviewCard("r", now.plusSeconds(86_400)), newCard("n"))
        val plan = StudyPlanner.planDeck(deck(), cards, 5, 1, now)
        assertEquals(DeckSection.NEW_CARDS, StudyPlanner.sectionOf(deck(), plan))
    }

    @Test
    fun nothingLeftIsNone() {
        val cards = listOf(reviewCard("r", now.plusSeconds(86_400)), newCard("n"))
        val plan = StudyPlanner.planDeck(deck(), cards, 5, 5, now)
        assertEquals(DeckSection.NONE, StudyPlanner.sectionOf(deck(), plan))
    }
}
