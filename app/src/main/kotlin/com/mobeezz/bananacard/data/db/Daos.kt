package com.mobeezz.bananacard.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DeckDao {
    @Query("SELECT * FROM decks ORDER BY created_at DESC")
    fun observeAll(): Flow<List<DeckEntity>>

    @Query("SELECT * FROM decks WHERE id = :id")
    fun observeById(id: String): Flow<DeckEntity?>

    // Plain insert (ABORT): a REPLACE would delete the row first and cascade to its cards.
    @Insert
    suspend fun insert(deck: DeckEntity)

    @Update
    suspend fun update(deck: DeckEntity)

    @Query("DELETE FROM decks WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface FlashcardDao {
    @Query("SELECT * FROM flashcards WHERE deck_id = :deckId ORDER BY created_at ASC, rowid ASC")
    fun observeByDeck(deckId: String): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards ORDER BY created_at ASC, rowid ASC")
    fun observeAll(): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards WHERE id = :id")
    suspend fun get(id: String): FlashcardEntity?

    @Insert
    suspend fun insertAll(cards: List<FlashcardEntity>)

    @Update
    suspend fun update(card: FlashcardEntity)

    @Query("DELETE FROM flashcards WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}

@Dao
interface DailyActivityDao {
    @Query("SELECT date FROM daily_activity WHERE date >= :from AND cards_reviewed > 0")
    fun observeActiveDays(from: String): Flow<List<String>>

    @Query("SELECT date FROM daily_activity WHERE date >= :from AND cards_reviewed > 0")
    suspend fun activeDaysSince(from: String): List<String>

    @Query("SELECT cards_reviewed FROM daily_activity WHERE date = :date")
    fun observeCardsReviewed(date: String): Flow<Int?>

    @Query(
        "INSERT INTO daily_activity (date, cards_reviewed) VALUES (:date, :count) " +
            "ON CONFLICT(date) DO UPDATE SET cards_reviewed = :count",
    )
    suspend fun setCount(date: String, count: Int)
}

@Dao
abstract class DeckDailyStatusDao {
    @Query("SELECT * FROM deck_daily_status WHERE date = :date")
    abstract fun observeByDate(date: String): Flow<List<DeckDailyStatusEntity>>

    @Query(
        "INSERT INTO deck_daily_status (deck_id, date, completed_once, reviewed_once, new_started_count) " +
            "VALUES (:deckId, :date, 1, 0, 0) " +
            "ON CONFLICT(deck_id, date) DO UPDATE SET completed_once = 1",
    )
    protected abstract suspend fun upsertCompleted(deckId: String, date: String)

    @Transaction
    open suspend fun markCompleted(deckId: String, date: String) = upsertCompleted(deckId, date)
}

/** Holds the multi-table review write so it runs in a single transaction. */
@Dao
abstract class ReviewDao {
    @Update
    protected abstract suspend fun updateCard(card: FlashcardEntity)

    @Query(
        "INSERT INTO daily_activity (date, cards_reviewed) VALUES (:date, 1) " +
            "ON CONFLICT(date) DO UPDATE SET cards_reviewed = cards_reviewed + 1",
    )
    protected abstract suspend fun incrementActivity(date: String)

    @Query(
        "INSERT INTO deck_daily_status (deck_id, date, completed_once, reviewed_once, new_started_count) " +
            "VALUES (:deckId, :date, 0, 1, :newStarted) " +
            "ON CONFLICT(deck_id, date) DO UPDATE SET reviewed_once = 1, " +
            "new_started_count = new_started_count + :newStarted",
    )
    protected abstract suspend fun upsertReviewed(deckId: String, date: String, newStarted: Int)

    @Transaction
    open suspend fun saveReview(card: FlashcardEntity, date: String, wasNew: Boolean) {
        updateCard(card)
        incrementActivity(date)
        upsertReviewed(card.deckId, date, if (wasNew) 1 else 0)
    }
}
