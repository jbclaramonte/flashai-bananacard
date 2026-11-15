package com.mobeezz.bananacard.domain.fakes

import com.mobeezz.bananacard.domain.model.Deck
import com.mobeezz.bananacard.domain.model.DeckDailyStatus
import com.mobeezz.bananacard.domain.model.Flashcard
import com.mobeezz.bananacard.domain.planning.StudyPlanner
import com.mobeezz.bananacard.domain.repository.ActivityRepository
import com.mobeezz.bananacard.domain.repository.DeckRepository
import com.mobeezz.bananacard.domain.repository.FlashcardRepository
import com.mobeezz.bananacard.domain.repository.ReviewRepository
import com.mobeezz.bananacard.domain.repository.SettingsRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeDeckRepository : DeckRepository {
    private val decks = MutableStateFlow<List<Deck>>(emptyList())
    private var linkedCards: FakeFlashcardRepository? = null
    private var linkedActivity: FakeActivityRepository? = null

    /** Optional links so [delete] cascades like the real repository. */
    fun linkCascade(cards: FakeFlashcardRepository, activity: FakeActivityRepository) {
        linkedCards = cards
        linkedActivity = activity
    }

    override fun observeDecks(): Flow<List<Deck>> = decks.map { l -> l.sortedByDescending { it.createdAt } }
    override fun observeDeck(id: String): Flow<Deck?> = decks.map { l -> l.firstOrNull { it.id == id } }
    override suspend fun insert(deck: Deck) = decks.update { it + deck }
    override suspend fun update(deck: Deck) = decks.update { l -> l.map { if (it.id == deck.id) deck else it } }
    override suspend fun delete(id: String) {
        decks.update { l -> l.filterNot { it.id == id } }
        linkedCards?.removeDeck(id)
        linkedActivity?.removeDeck(id)
    }
}

class FakeFlashcardRepository : FlashcardRepository {
    private val cards = MutableStateFlow<List<Flashcard>>(emptyList())

    override fun observeCards(deckId: String): Flow<List<Flashcard>> = cards.map { l -> l.filter { it.deckId == deckId } }
    override fun observeAllCards(): Flow<List<Flashcard>> = cards
    override suspend fun get(id: String): Flashcard? = cards.value.firstOrNull { it.id == id }
    override suspend fun insert(cards: List<Flashcard>) = this.cards.update { it + cards }
    override suspend fun update(card: Flashcard) = cards.update { l -> l.map { if (it.id == card.id) card else it } }
    override suspend fun delete(ids: List<String>) = cards.update { l -> l.filterNot { it.id in ids.toSet() } }

    internal fun removeDeck(deckId: String) = cards.update { l -> l.filterNot { it.deckId == deckId } }
}

class FakeActivityRepository : ActivityRepository {
    private val reviewed = MutableStateFlow<Map<LocalDate, Int>>(emptyMap())
    private val statuses = MutableStateFlow<Map<Pair<String, LocalDate>, DeckDailyStatus>>(emptyMap())

    /** Test helper: marks exactly [days] as active (1 card reviewed each), replacing prior activity. */
    fun setActiveDays(days: Set<LocalDate>) {
        reviewed.value = days.associateWith { 1 }
    }

    private fun active(from: LocalDate): Set<LocalDate> =
        reviewed.value.filter { (d, n) -> n > 0 && !d.isBefore(from) }.keys

    override fun observeActiveDays(from: LocalDate): Flow<Set<LocalDate>> = reviewed.map { active(from) }
    override fun observeCardsReviewed(date: LocalDate): Flow<Int> = reviewed.map { it[date] ?: 0 }
    override fun observeDailyStatuses(date: LocalDate): Flow<Map<String, DeckDailyStatus>> =
        statuses.map { m -> m.values.filter { it.date == date }.associateBy { it.deckId } }

    override suspend fun activeDaysSince(from: LocalDate): Set<LocalDate> = active(from)

    override suspend fun markCompleted(deckId: String, date: LocalDate) =
        updateStatus(deckId, date) { it.copy(completedOnce = true) }

    internal fun recordReview(deckId: String, date: LocalDate, wasNew: Boolean) {
        reviewed.update { it + (date to ((it[date] ?: 0) + 1)) }
        updateStatus(deckId, date) {
            it.copy(reviewedOnce = true, newStartedCount = it.newStartedCount + if (wasNew) 1 else 0)
        }
    }

    internal fun removeDeck(deckId: String) = statuses.update { m -> m.filterKeys { it.first != deckId } }

    private fun updateStatus(deckId: String, date: LocalDate, f: (DeckDailyStatus) -> DeckDailyStatus) {
        statuses.update { m ->
            val cur = m[deckId to date] ?: DeckDailyStatus(deckId, date, false, false, 0)
            m + ((deckId to date) to f(cur))
        }
    }
}

class FakeReviewRepository(
    private val flashcards: FakeFlashcardRepository,
    private val activity: FakeActivityRepository,
) : ReviewRepository {
    override suspend fun saveReview(card: Flashcard, date: LocalDate, wasNew: Boolean) {
        flashcards.update(card)
        activity.recordReview(card.deckId, date, wasNew)
    }
}

class FakeSettingsRepository : SettingsRepository {
    private val cap = MutableStateFlow(StudyPlanner.DEFAULT_CAP)
    private val best = MutableStateFlow(0)

    /** Number of [setBestStreak] calls, for asserting "no write". */
    var bestStreakWrites = 0

    override val newCardsPerDay: Flow<Int> = cap
    override suspend fun setNewCardsPerDay(value: Int) {
        require(value in StudyPlanner.ALLOWED_CAPS) { "newCardsPerDay must be one of ${StudyPlanner.ALLOWED_CAPS}" }
        cap.value = value
    }

    override val bestStreak: Flow<Int> = best
    override suspend fun setBestStreak(value: Int) {
        bestStreakWrites++
        best.value = value
    }
}
