package com.mobeezz.bananacard.domain.model

import com.mobeezz.bananacard.domain.fsrs.CardState
import java.time.Instant

data class CardSchedule(
    val state: CardState,
    val step: Int?,
    val stability: Double,
    val difficulty: Double,
    val due: Instant,
    val lastReview: Instant?,
)

data class Flashcard(
    val id: String,
    val deckId: String,
    val question: String,
    val answer: String,
    /** Null means a new, never-reviewed card. */
    val schedule: CardSchedule?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val isNew: Boolean get() = schedule == null
}
