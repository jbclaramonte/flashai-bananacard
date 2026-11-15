package com.mobeezz.bananacard.domain.planning

import com.mobeezz.bananacard.domain.model.Deck
import com.mobeezz.bananacard.domain.model.Flashcard
import java.time.Duration
import java.time.Instant

data class DeckPlan(val dueCards: List<Flashcard>, val newCards: List<Flashcard>) {
    val plannedDue: Int get() = dueCards.size
    val plannedNew: Int get() = newCards.size
}

enum class DeckSection { TO_REVIEW, NEW_CARDS, NONE, PAUSED }

object StudyPlanner {
    /** Cards due within this window ahead of now count as due. */
    val DUE_TOLERANCE: Duration = Duration.ofMinutes(1)
    val ALLOWED_CAPS = listOf(0, 5, 10)
    const val DEFAULT_CAP = 5

    fun isDue(card: Flashcard, now: Instant): Boolean {
        val schedule = card.schedule ?: return false
        return !schedule.due.isAfter(now.plus(DUE_TOLERANCE))
    }

    /** [cards] must be in insertion order; new cards keep that order. */
    fun planDeck(
        deck: Deck,
        cards: List<Flashcard>,
        newCap: Int,
        newStartedToday: Int,
        now: Instant,
    ): DeckPlan {
        if (deck.isPaused) return DeckPlan(emptyList(), emptyList())
        val due = cards.filter { isDue(it, now) }.sortedBy { it.schedule!!.due }
        val remaining = maxOf(0, newCap - newStartedToday)
        val new = cards.filter { it.isNew }.take(remaining)
        return DeckPlan(due, new)
    }

    fun sectionOf(deck: Deck, plan: DeckPlan): DeckSection = when {
        deck.isPaused -> DeckSection.PAUSED
        plan.plannedDue > 0 -> DeckSection.TO_REVIEW
        plan.plannedNew > 0 -> DeckSection.NEW_CARDS
        else -> DeckSection.NONE
    }
}
