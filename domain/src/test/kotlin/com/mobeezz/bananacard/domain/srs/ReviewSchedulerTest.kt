package com.mobeezz.bananacard.domain.srs

import com.mobeezz.bananacard.domain.fsrs.CardState
import com.mobeezz.bananacard.domain.fsrs.Rating
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
}
