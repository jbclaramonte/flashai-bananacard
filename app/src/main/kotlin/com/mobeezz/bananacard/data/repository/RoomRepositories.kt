package com.mobeezz.bananacard.data.repository

import com.mobeezz.bananacard.data.db.BananaCardDatabase
import com.mobeezz.bananacard.data.db.toDomain
import com.mobeezz.bananacard.data.db.toEntity
import com.mobeezz.bananacard.domain.model.Deck
import com.mobeezz.bananacard.domain.model.DeckDailyStatus
import com.mobeezz.bananacard.domain.model.Flashcard
import com.mobeezz.bananacard.domain.repository.ActivityRepository
import com.mobeezz.bananacard.domain.repository.DeckRepository
import com.mobeezz.bananacard.domain.repository.FlashcardRepository
import com.mobeezz.bananacard.domain.repository.ReviewRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomDeckRepository(db: BananaCardDatabase) : DeckRepository {
    private val dao = db.deckDao()

    override fun observeDecks(): Flow<List<Deck>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeDeck(id: String): Flow<Deck?> =
        dao.observeById(id).map { it?.toDomain() }

    override suspend fun insert(deck: Deck) = dao.insert(deck.toEntity())

    /** Writes every column except createdAt, which is immutable. */
    override suspend fun update(deck: Deck) {
        val e = deck.toEntity()
        dao.updateEditable(
            id = e.id,
            name = e.name,
            icon = e.icon,
            iconColor = e.iconColor,
            type = e.type,
            sourceLanguage = e.sourceLanguage,
            targetLanguage = e.targetLanguage,
            description = e.description,
            isPaused = e.isPaused,
            updatedAt = e.updatedAt,
        )
    }

    override suspend fun delete(id: String) = dao.deleteById(id)
}

class RoomFlashcardRepository(db: BananaCardDatabase) : FlashcardRepository {
    private val dao = db.flashcardDao()

    override fun observeCards(deckId: String): Flow<List<Flashcard>> =
        dao.observeByDeck(deckId).map { rows -> rows.map { it.toDomain() } }

    override fun observeAllCards(): Flow<List<Flashcard>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun get(id: String): Flashcard? = dao.get(id)?.toDomain()

    override suspend fun insert(cards: List<Flashcard>) = dao.insertAll(cards.map { it.toEntity() })

    /** Writes question, answer and updatedAt only; schedule and createdAt are never overwritten. */
    override suspend fun update(card: Flashcard) =
        dao.updateText(card.id, card.question, card.answer, card.updatedAt.toEpochMilli())

    override suspend fun delete(ids: List<String>) = dao.deleteByIds(ids)
}

class RoomActivityRepository(db: BananaCardDatabase) : ActivityRepository {
    private val activityDao = db.dailyActivityDao()
    private val statusDao = db.deckDailyStatusDao()

    override fun observeActiveDays(from: LocalDate): Flow<Set<LocalDate>> =
        activityDao.observeActiveDays(from.toString())
            .map { rows -> rows.mapTo(HashSet()) { LocalDate.parse(it) } }

    override fun observeCardsReviewed(date: LocalDate): Flow<Int> =
        activityDao.observeCardsReviewed(date.toString()).map { it ?: 0 }

    override fun observeDailyStatuses(date: LocalDate): Flow<Map<String, DeckDailyStatus>> =
        statusDao.observeByDate(date.toString())
            .map { rows -> rows.associate { it.deckId to it.toDomain() } }

    override suspend fun activeDaysSince(from: LocalDate): Set<LocalDate> =
        activityDao.activeDaysSince(from.toString()).mapTo(HashSet()) { LocalDate.parse(it) }

    override suspend fun markCompleted(deckId: String, date: LocalDate) =
        statusDao.markCompleted(deckId, date.toString())
}

class RoomReviewRepository(db: BananaCardDatabase) : ReviewRepository {
    private val dao = db.reviewDao()

    override suspend fun saveReview(card: Flashcard, date: LocalDate, wasNew: Boolean) =
        dao.saveReview(card.toEntity(), date.toString(), wasNew)
}
