package com.mobeezz.bananacard.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.mobeezz.bananacard.data.db.BananaCardDatabase
import com.mobeezz.bananacard.data.repository.RoomActivityRepository
import com.mobeezz.bananacard.data.repository.RoomDeckRepository
import com.mobeezz.bananacard.data.repository.RoomFlashcardRepository
import com.mobeezz.bananacard.data.repository.RoomReviewRepository
import com.mobeezz.bananacard.domain.fsrs.CardState
import com.mobeezz.bananacard.domain.model.CardSchedule
import com.mobeezz.bananacard.domain.model.Deck
import com.mobeezz.bananacard.domain.model.DeckType
import com.mobeezz.bananacard.domain.model.Flashcard
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomRepositoriesTest {
    private lateinit var db: BananaCardDatabase
    private lateinit var decks: RoomDeckRepository
    private lateinit var cards: RoomFlashcardRepository
    private lateinit var activity: RoomActivityRepository
    private lateinit var reviews: RoomReviewRepository

    private val day = LocalDate.of(2026, 3, 10)
    private val t0 = Instant.ofEpochMilli(1_700_000_000_000)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BananaCardDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        decks = RoomDeckRepository(db)
        cards = RoomFlashcardRepository(db)
        activity = RoomActivityRepository(db)
        reviews = RoomReviewRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun deck(
        id: String,
        createdAt: Instant = t0,
        type: DeckType? = DeckType.LANGUAGE,
    ) = Deck(
        id = id, name = "Deck $id", icon = "📚", iconColor = 0xFFFFE5E5,
        type = type, sourceLanguage = "fr", targetLanguage = "es",
        description = "desc", isPaused = false, createdAt = createdAt, updatedAt = createdAt,
    )

    private fun card(id: String, deckId: String, createdAt: Instant = t0, schedule: CardSchedule? = null) =
        Flashcard(
            id = id, deckId = deckId, question = "q$id", answer = "a$id",
            schedule = schedule, createdAt = createdAt, updatedAt = createdAt,
        )

    @Test
    fun deckRoundTripKeepsAllFields() = runTest {
        val full = deck("d1")
        val sparse = Deck(
            id = "d2", name = "n", icon = "🚀", iconColor = 0xFFE6FAF4,
            type = null, sourceLanguage = null, targetLanguage = null, description = null,
            isPaused = true, createdAt = t0, updatedAt = t0.plusMillis(5),
        )
        decks.insert(full)
        decks.insert(sparse)

        decks.observeDeck("d1").test { assertEquals(full, awaitItem()); cancelAndIgnoreRemainingEvents() }
        decks.observeDeck("d2").test { assertEquals(sparse, awaitItem()); cancelAndIgnoreRemainingEvents() }
        decks.observeDeck("missing").test { assertNull(awaitItem()); cancelAndIgnoreRemainingEvents() }

        val general = full.copy(type = DeckType.GENERAL, name = "renamed")
        decks.update(general)
        decks.observeDeck("d1").test { assertEquals(general, awaitItem()); cancelAndIgnoreRemainingEvents() }
    }

    @Test
    fun decksOrderedByCreatedAtDesc() = runTest {
        decks.insert(deck("old", createdAt = t0))
        decks.insert(deck("new", createdAt = t0.plusSeconds(10)))
        decks.insert(deck("mid", createdAt = t0.plusSeconds(5)))

        decks.observeDecks().test {
            assertEquals(listOf("new", "mid", "old"), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun cardsKeepInsertionOrderWithEqualCreatedAt() = runTest {
        decks.insert(deck("d1"))
        // ids deliberately not in alphabetical order
        cards.insert(listOf(card("c", "d1"), card("a", "d1"), card("b", "d1")))

        cards.observeCards("d1").test {
            assertEquals(listOf("c", "a", "b"), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
        cards.observeAllCards().test {
            assertEquals(listOf("c", "a", "b"), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun newCardRoundTripsWithNullSchedule() = runTest {
        decks.insert(deck("d1"))
        val c = card("c1", "d1")
        cards.insert(listOf(c))

        val loaded = cards.get("c1")
        assertEquals(c, loaded)
        assertNull(loaded!!.schedule)
        assertTrue(loaded.isNew)
        assertNull(cards.get("nope"))
    }

    @Test
    fun scheduledCardRoundTrips() = runTest {
        decks.insert(deck("d1"))
        val schedule = CardSchedule(
            state = CardState.RELEARNING, step = 1, stability = 3.25, difficulty = 6.5,
            due = t0.plusSeconds(600), lastReview = t0.plusSeconds(60),
        )
        val review = CardSchedule(
            state = CardState.REVIEW, step = null, stability = 12.0, difficulty = 4.0,
            due = t0.plusSeconds(86400), lastReview = null,
        )
        cards.insert(listOf(card("c1", "d1", schedule = schedule), card("c2", "d1", schedule = review)))

        assertEquals(schedule, cards.get("c1")!!.schedule)
        assertEquals(review, cards.get("c2")!!.schedule)

        cards.delete(listOf("c1", "c2"))
        assertNull(cards.get("c1"))
        assertNull(cards.get("c2"))
    }

    @Test
    fun saveReviewUpdatesCardActivityAndStatus() = runTest {
        decks.insert(deck("d1"))
        cards.insert(listOf(card("n", "d1"), card("o", "d1")))
        val s = CardSchedule(CardState.REVIEW, null, 5.0, 5.0, t0.plusSeconds(100), t0)

        reviews.saveReview(card("n", "d1", schedule = s), day, wasNew = true)
        reviews.saveReview(card("o", "d1", schedule = s), day, wasNew = false)

        assertEquals(s, cards.get("n")!!.schedule)
        assertEquals(s, cards.get("o")!!.schedule)
        activity.observeCardsReviewed(day).test { assertEquals(2, awaitItem()); cancelAndIgnoreRemainingEvents() }
        activity.observeCardsReviewed(day.plusDays(1)).test { assertEquals(0, awaitItem()); cancelAndIgnoreRemainingEvents() }
        activity.observeDailyStatuses(day).test {
            val status = awaitItem().getValue("d1")
            assertTrue(status.reviewedOnce)
            assertFalse(status.completedOnce)
            assertEquals(1, status.newStartedCount)
            assertEquals(day, status.date)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deckUpdateKeepsOriginalCreatedAt() = runTest {
        val original = deck("d1")
        decks.insert(original)

        val edited = original.copy(
            name = "renamed", isPaused = true, description = null,
            createdAt = t0.plusSeconds(999), updatedAt = t0.plusSeconds(50),
        )
        decks.update(edited)

        decks.observeDeck("d1").test {
            val loaded = awaitItem()!!
            assertEquals(t0, loaded.createdAt)
            assertEquals(edited.copy(createdAt = t0), loaded)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun cardEditKeepsScheduleAndCreatedAt() = runTest {
        decks.insert(deck("d1"))
        val schedule = CardSchedule(CardState.REVIEW, null, 8.0, 5.5, t0.plusSeconds(3600), t0)
        cards.insert(listOf(card("c1", "d1", schedule = schedule)))

        // The caller holds a stale or foreign object: new text, but no schedule and another created_at.
        val edit = card("c1", "d1").copy(
            question = "new q", answer = "new a",
            createdAt = t0.plusSeconds(777), updatedAt = t0.plusSeconds(20),
        )
        cards.update(edit)

        val loaded = cards.get("c1")!!
        assertEquals("new q", loaded.question)
        assertEquals("new a", loaded.answer)
        assertEquals(t0.plusSeconds(20), loaded.updatedAt)
        assertEquals(schedule, loaded.schedule)
        assertEquals(t0, loaded.createdAt)
    }

    @Test
    fun saveReviewWithStaleTextDoesNotRevertEdit() = runTest {
        decks.insert(deck("d1"))
        val stale = card("c1", "d1")
        cards.insert(listOf(stale))
        cards.update(stale.copy(question = "edited q", answer = "edited a", updatedAt = t0.plusSeconds(5)))

        val s = CardSchedule(CardState.REVIEW, null, 5.0, 5.0, t0.plusSeconds(100), t0)
        reviews.saveReview(stale.copy(schedule = s, updatedAt = t0.plusSeconds(10)), day, wasNew = true)

        val loaded = cards.get("c1")!!
        assertEquals("edited q", loaded.question)
        assertEquals("edited a", loaded.answer)
        assertEquals(s, loaded.schedule)
        assertEquals(t0.plusSeconds(10), loaded.updatedAt)
        assertEquals(t0, loaded.createdAt)
    }

    @Test
    fun saveReviewOnMissingCardWritesNoActivity() = runTest {
        decks.insert(deck("d1"))

        val s = CardSchedule(CardState.REVIEW, null, 5.0, 5.0, t0.plusSeconds(100), t0)
        try {
            reviews.saveReview(card("ghost", "d1", schedule = s), day, wasNew = true)
            fail("saveReview on a missing card must throw")
        } catch (expected: IllegalStateException) {
            // expected: the whole transaction is rolled back
        }

        activity.observeCardsReviewed(day).test { assertEquals(0, awaitItem()); cancelAndIgnoreRemainingEvents() }
        activity.observeDailyStatuses(day).test { assertTrue(awaitItem().isEmpty()); cancelAndIgnoreRemainingEvents() }
    }

    @Test
    fun markCompletedLatches() = runTest {
        decks.insert(deck("d1"))
        activity.markCompleted("d1", day)
        activity.markCompleted("d1", day)
        activity.observeDailyStatuses(day).test {
            val s = awaitItem().getValue("d1")
            assertTrue(s.completedOnce)
            assertFalse(s.reviewedOnce)
            assertEquals(0, s.newStartedCount)
            cancelAndIgnoreRemainingEvents()
        }

        // a later review must not clear the completed flag
        cards.insert(listOf(card("c1", "d1")))
        reviews.saveReview(card("c1", "d1"), day, wasNew = true)
        activity.observeDailyStatuses(day).test {
            val s = awaitItem().getValue("d1")
            assertTrue(s.completedOnce)
            assertTrue(s.reviewedOnce)
            assertEquals(1, s.newStartedCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deleteDeckCascades() = runTest {
        decks.insert(deck("d1"))
        decks.insert(deck("d2"))
        cards.insert(listOf(card("c1", "d1"), card("c2", "d2")))
        reviews.saveReview(card("c1", "d1"), day, wasNew = true)
        reviews.saveReview(card("c2", "d2"), day, wasNew = true)

        decks.delete("d1")

        cards.observeCards("d1").test { assertEquals(emptyList<Flashcard>(), awaitItem()); cancelAndIgnoreRemainingEvents() }
        cards.observeCards("d2").test { assertEquals(listOf("c2"), awaitItem().map { it.id }); cancelAndIgnoreRemainingEvents() }
        activity.observeDailyStatuses(day).test {
            val m = awaitItem()
            assertFalse(m.containsKey("d1"))
            assertTrue(m.containsKey("d2"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun activeDaysExcludesZeroAndOlderDates() = runTest {
        decks.insert(deck("d1"))
        cards.insert(listOf(card("c1", "d1")))
        val old = day.minusDays(10)
        val recent = day.minusDays(2)
        reviews.saveReview(card("c1", "d1"), old, wasNew = false)
        reviews.saveReview(card("c1", "d1"), recent, wasNew = false)
        reviews.saveReview(card("c1", "d1"), day, wasNew = false)
        // a zero-count row, as could result from a manual correction
        db.dailyActivityDao().setCount(day.plusDays(1).toString(), 0)

        val from = day.minusDays(5)
        assertEquals(setOf(recent, day), activity.activeDaysSince(from))
        activity.observeActiveDays(from).test {
            assertEquals(setOf(recent, day), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
