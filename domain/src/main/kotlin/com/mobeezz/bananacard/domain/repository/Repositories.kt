package com.mobeezz.bananacard.domain.repository

import com.mobeezz.bananacard.domain.model.Deck
import com.mobeezz.bananacard.domain.model.DeckDailyStatus
import com.mobeezz.bananacard.domain.model.Flashcard
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

interface DeckRepository {
    /** Ordered by createdAt DESC. */
    fun observeDecks(): Flow<List<Deck>>
    fun observeDeck(id: String): Flow<Deck?>
    suspend fun insert(deck: Deck)
    suspend fun update(deck: Deck)
    /** Cascades to the deck's cards and daily status. */
    suspend fun delete(id: String)
}

interface FlashcardRepository {
    /** Insertion order. */
    fun observeCards(deckId: String): Flow<List<Flashcard>>
    /** Insertion order. */
    fun observeAllCards(): Flow<List<Flashcard>>
    suspend fun get(id: String): Flashcard?
    /** One transaction. */
    suspend fun insert(cards: List<Flashcard>)
    suspend fun update(card: Flashcard)
    /** One transaction. */
    suspend fun delete(ids: List<String>)
}

interface ActivityRepository {
    /** Days since [from] with cards_reviewed > 0. */
    fun observeActiveDays(from: LocalDate): Flow<Set<LocalDate>>
    fun observeCardsReviewed(date: LocalDate): Flow<Int>
    /** Key = deckId. */
    fun observeDailyStatuses(date: LocalDate): Flow<Map<String, DeckDailyStatus>>
    suspend fun activeDaysSince(from: LocalDate): Set<LocalDate>
    suspend fun markCompleted(deckId: String, date: LocalDate)
}

interface ReviewRepository {
    /** One transaction: update card, daily_activity +1, reviewed_once = true, new_started_count += 1 if wasNew. */
    suspend fun saveReview(card: Flashcard, date: LocalDate, wasNew: Boolean)
}

interface SettingsRepository {
    /** Default [com.mobeezz.bananacard.domain.planning.StudyPlanner.DEFAULT_CAP]. */
    val newCardsPerDay: Flow<Int>
    /** Requires value in [com.mobeezz.bananacard.domain.planning.StudyPlanner.ALLOWED_CAPS]. */
    suspend fun setNewCardsPerDay(value: Int)
    /** Default 0. */
    val bestStreak: Flow<Int>
    suspend fun setBestStreak(value: Int)
}
