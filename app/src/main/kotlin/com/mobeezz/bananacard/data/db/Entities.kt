package com.mobeezz.bananacard.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "decks")
data class DeckEntity(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String,
    @ColumnInfo(name = "icon_color") val iconColor: Long,
    /** `language` / `general` / null. */
    val type: String?,
    @ColumnInfo(name = "source_language") val sourceLanguage: String?,
    @ColumnInfo(name = "target_language") val targetLanguage: String?,
    val description: String?,
    @ColumnInfo(name = "is_paused") val isPaused: Boolean,
    /** Epoch milliseconds. */
    @ColumnInfo(name = "created_at") val createdAt: Long,
    /** Epoch milliseconds. */
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** The six schedule columns are all null for a new card. */
@Entity(
    tableName = "flashcards",
    foreignKeys = [
        ForeignKey(
            entity = DeckEntity::class,
            parentColumns = ["id"],
            childColumns = ["deck_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("deck_id")],
)
data class FlashcardEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "deck_id") val deckId: String,
    val question: String,
    val answer: String,
    /** `learning` / `review` / `relearning` / null. */
    val state: String?,
    val step: Int?,
    val stability: Double?,
    val difficulty: Double?,
    /** Epoch milliseconds. */
    val due: Long?,
    /** Epoch milliseconds. */
    @ColumnInfo(name = "last_review") val lastReview: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(tableName = "daily_activity")
data class DailyActivityEntity(
    /** ISO `YYYY-MM-DD`. */
    @PrimaryKey val date: String,
    @ColumnInfo(name = "cards_reviewed") val cardsReviewed: Int,
)

@Entity(
    tableName = "deck_daily_status",
    primaryKeys = ["deck_id", "date"],
    foreignKeys = [
        ForeignKey(
            entity = DeckEntity::class,
            parentColumns = ["id"],
            childColumns = ["deck_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class DeckDailyStatusEntity(
    @ColumnInfo(name = "deck_id") val deckId: String,
    /** ISO `YYYY-MM-DD`. */
    val date: String,
    @ColumnInfo(name = "completed_once") val completedOnce: Boolean,
    @ColumnInfo(name = "reviewed_once") val reviewedOnce: Boolean,
    @ColumnInfo(name = "new_started_count") val newStartedCount: Int,
)
