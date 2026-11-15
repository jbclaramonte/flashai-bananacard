package com.mobeezz.bananacard.domain.srs

import com.mobeezz.bananacard.domain.fsrs.FsrsCard
import com.mobeezz.bananacard.domain.fsrs.FsrsScheduler
import com.mobeezz.bananacard.domain.fsrs.Rating
import com.mobeezz.bananacard.domain.model.CardSchedule
import com.mobeezz.bananacard.domain.model.Flashcard
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

/**
 * Applies FSRS to [Flashcard]s. [fsrs] is used for real reviews (fuzzed);
 * [previewFsrs] has fuzzing disabled so button labels are deterministic.
 */
class ReviewScheduler(
    private val fsrs: FsrsScheduler,
    private val previewFsrs: FsrsScheduler,
) {
    fun review(card: Flashcard, rating: Rating, now: Instant): Flashcard {
        val next = fsrs.reviewCard(card.toFsrs(now), rating, now)
        return card.copy(schedule = next.toSchedule(), updatedAt = now)
    }

    fun preview(card: Flashcard, now: Instant): Map<Rating, Duration> {
        val fsrsCard = card.toFsrs(now)
        return Rating.entries.associateWith { rating ->
            Duration.between(now, previewFsrs.reviewCard(fsrsCard, rating, now).due)
        }
    }

    private fun Flashcard.toFsrs(now: Instant): FsrsCard =
        schedule?.let {
            FsrsCard(it.state, it.step, it.stability, it.difficulty, it.due, it.lastReview)
        } ?: FsrsCard.new(now)

    private fun FsrsCard.toSchedule(): CardSchedule = CardSchedule(
        state = state,
        step = step,
        stability = requireNotNull(stability) { "stability must be set after a review" },
        difficulty = requireNotNull(difficulty) { "difficulty must be set after a review" },
        due = due,
        lastReview = lastReview,
    )

    companion object {
        fun default(random: Random = Random.Default): ReviewScheduler {
            fun build(fuzzing: Boolean) = FsrsScheduler(
                desiredRetention = 0.9,
                learningSteps = listOf(Duration.ofMinutes(1), Duration.ofMinutes(10)),
                relearningSteps = listOf(Duration.ofMinutes(10)),
                maximumInterval = 36500,
                enableFuzzing = fuzzing,
                random = random,
            )
            return ReviewScheduler(build(true), build(false))
        }
    }
}
