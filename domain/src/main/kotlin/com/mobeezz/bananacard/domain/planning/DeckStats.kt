package com.mobeezz.bananacard.domain.planning

import com.mobeezz.bananacard.domain.fsrs.CardState
import com.mobeezz.bananacard.domain.model.Flashcard
import java.time.Instant

data class DeckStats(
    val total: Int,
    val progress: Double,
    val cardsToReview: Int,
    val learningCount: Int,
)

fun deckStats(cards: List<Flashcard>, now: Instant): DeckStats {
    val total = cards.size
    val reviewCount = cards.count { it.schedule?.state == CardState.REVIEW }
    val dueCount = cards.count { StudyPlanner.isDue(it, now) }
    val newCount = cards.count { it.isNew }
    val learning = cards.count {
        val state = it.schedule?.state
        state == CardState.LEARNING || state == CardState.RELEARNING
    }
    return DeckStats(
        total = total,
        progress = if (total == 0) 0.0 else reviewCount.toDouble() / total,
        cardsToReview = dueCount + newCount,
        learningCount = learning,
    )
}
