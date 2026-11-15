package com.mobeezz.bananacard.domain.review

import app.cash.turbine.test
import com.mobeezz.bananacard.domain.fakes.FakeActivityRepository
import com.mobeezz.bananacard.domain.fakes.FakeFlashcardRepository
import com.mobeezz.bananacard.domain.fakes.FakeReviewRepository
import com.mobeezz.bananacard.domain.fsrs.Rating
import com.mobeezz.bananacard.domain.model.Flashcard
import com.mobeezz.bananacard.domain.srs.ReviewScheduler
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.random.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewRecorderTest {
    private val paris = ZoneId.of("Europe/Paris")
    private val noon = Instant.parse("2026-01-05T12:00:00Z")
    private val today = LocalDate.of(2026, 1, 5)

    private val flashcards = FakeFlashcardRepository()
    private val activity = FakeActivityRepository()
    private val reviews = FakeReviewRepository(flashcards, activity)

    private fun recorderAt(instant: Instant) = ReviewRecorder(
        scheduler = ReviewScheduler.default(Random(0)),
        reviews = reviews,
        clock = Clock.fixed(instant, ZoneOffset.UTC),
        zone = { paris },
    )

    private fun newCard(id: String = "c1") =
        Flashcard(id, "d1", "q", "a", schedule = null, createdAt = noon, updatedAt = noon)

    private suspend fun status(date: LocalDate) = activity.observeDailyStatuses(date).first()["d1"]

    @Test
    fun recordsActivityAndReviewedOnce() = runTest {
        val card = newCard()
        flashcards.insert(listOf(card))

        recorderAt(noon).record(card, Rating.GOOD)

        activity.observeCardsReviewed(today).test {
            assertEquals(1, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(status(today)!!.reviewedOnce)
    }

    @Test
    fun newCardIncrementsNewStarted() = runTest {
        val card = newCard()
        flashcards.insert(listOf(card))
        val recorder = recorderAt(noon)

        val scheduled = recorder.record(card, Rating.GOOD)
        assertNotNull(scheduled.schedule)
        assertEquals(1, status(today)!!.newStartedCount)

        recorder.record(scheduled, Rating.GOOD)
        assertEquals(1, status(today)!!.newStartedCount)
        assertEquals(2, activity.observeCardsReviewed(today).first())
    }

    @Test
    fun reviewCardDoesNotIncrementNewStarted() = runTest {
        val scheduled = ReviewScheduler.default(Random(0)).review(newCard(), Rating.GOOD, noon)
        assertFalse(scheduled.isNew)
        flashcards.insert(listOf(scheduled))

        recorderAt(noon).record(scheduled, Rating.GOOD)

        assertEquals(0, status(today)!!.newStartedCount)
        assertTrue(status(today)!!.reviewedOnce)
    }

    @Test
    fun reviewJustAfterMidnightCountsForNewDay() = runTest {
        val card = newCard()
        flashcards.insert(listOf(card))

        recorderAt(Instant.parse("2026-01-05T22:59:00Z")).record(card, Rating.GOOD)
        assertEquals(1, activity.observeCardsReviewed(LocalDate.of(2026, 1, 5)).first())
        assertEquals(0, activity.observeCardsReviewed(LocalDate.of(2026, 1, 6)).first())

        val updated = flashcards.get("c1")!!
        recorderAt(Instant.parse("2026-01-05T23:01:00Z")).record(updated, Rating.GOOD)
        assertEquals(1, activity.observeCardsReviewed(LocalDate.of(2026, 1, 5)).first())
        assertEquals(1, activity.observeCardsReviewed(LocalDate.of(2026, 1, 6)).first())
    }
}
