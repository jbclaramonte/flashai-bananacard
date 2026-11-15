package com.mobeezz.bananacard.domain.review

import com.mobeezz.bananacard.domain.fsrs.Rating
import com.mobeezz.bananacard.domain.model.Flashcard
import com.mobeezz.bananacard.domain.repository.ReviewRepository
import com.mobeezz.bananacard.domain.srs.ReviewScheduler
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class ReviewRecorder(
    private val scheduler: ReviewScheduler,
    private val reviews: ReviewRepository,
    private val clock: Clock,
    private val zone: () -> ZoneId,
) {
    /** Applies FSRS, persists card + daily tracking in one transaction, returns the updated card. */
    suspend fun record(card: Flashcard, rating: Rating): Flashcard {
        val now = clock.instant()
        val date = LocalDate.ofInstant(now, zone())
        val updated = scheduler.review(card, rating, now)
        reviews.saveReview(updated, date, card.isNew)
        return updated
    }
}
