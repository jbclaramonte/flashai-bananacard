package com.mobeezz.bananacard.domain.fsrs

import java.time.Instant

/** Learning state of a card. Values match Dart `fsrs` 2.0.1 `State`. */
enum class CardState(val value: Int) { LEARNING(1), REVIEW(2), RELEARNING(3) }

/** Rating given when reviewing a card. Values match Dart `fsrs` 2.0.1 `Rating`. */
enum class Rating(val value: Int) { AGAIN(1), HARD(2), GOOD(3), EASY(4) }

/**
 * FSRS scheduling state of a card, ported from Dart `fsrs` 2.0.1 `Card`
 * (without `cardId`, which scheduling never reads).
 *
 * [step] is non-null only in the LEARNING and RELEARNING states.
 * [stability] and [difficulty] are null until the first review.
 */
data class FsrsCard(
    val state: CardState,
    val step: Int?,
    val stability: Double?,
    val difficulty: Double?,
    val due: Instant,
    val lastReview: Instant?,
) {
    companion object {
        /** A never-reviewed card: LEARNING, step 0, due at [now]. */
        fun new(now: Instant): FsrsCard = FsrsCard(
            state = CardState.LEARNING,
            step = 0,
            stability = null,
            difficulty = null,
            due = now,
            lastReview = null,
        )
    }
}
