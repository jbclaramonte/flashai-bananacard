package com.mobeezz.bananacard.data.db

import com.mobeezz.bananacard.domain.fsrs.CardState
import com.mobeezz.bananacard.domain.model.CardSchedule
import com.mobeezz.bananacard.domain.model.Deck
import com.mobeezz.bananacard.domain.model.DeckDailyStatus
import com.mobeezz.bananacard.domain.model.DeckType
import com.mobeezz.bananacard.domain.model.Flashcard
import java.time.Instant
import java.time.LocalDate

private fun DeckType.toDb(): String = name.lowercase()

private fun deckTypeFromDb(value: String): DeckType = when (value) {
    "language" -> DeckType.LANGUAGE
    "general" -> DeckType.GENERAL
    else -> error("Unknown deck type: $value")
}

private fun CardState.toDb(): String = name.lowercase()

private fun cardStateFromDb(value: String): CardState = when (value) {
    "learning" -> CardState.LEARNING
    "review" -> CardState.REVIEW
    "relearning" -> CardState.RELEARNING
    else -> error("Unknown card state: $value")
}

fun Deck.toEntity() = DeckEntity(
    id = id,
    name = name,
    icon = icon,
    iconColor = iconColor,
    type = type?.toDb(),
    sourceLanguage = sourceLanguage,
    targetLanguage = targetLanguage,
    description = description,
    isPaused = isPaused,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
)

fun DeckEntity.toDomain() = Deck(
    id = id,
    name = name,
    icon = icon,
    iconColor = iconColor,
    type = type?.let(::deckTypeFromDb),
    sourceLanguage = sourceLanguage,
    targetLanguage = targetLanguage,
    description = description,
    isPaused = isPaused,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

fun Flashcard.toEntity(): FlashcardEntity {
    val s = schedule
    return FlashcardEntity(
        id = id,
        deckId = deckId,
        question = question,
        answer = answer,
        state = s?.state?.toDb(),
        step = s?.step,
        stability = s?.stability,
        difficulty = s?.difficulty,
        due = s?.due?.toEpochMilli(),
        lastReview = s?.lastReview?.toEpochMilli(),
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli(),
    )
}

/** All six schedule columns null means a new card; otherwise state/stability/difficulty/due must be set. */
fun FlashcardEntity.toDomain(): Flashcard {
    val isNew = state == null && step == null && stability == null &&
        difficulty == null && due == null && lastReview == null
    val schedule = if (isNew) {
        null
    } else {
        CardSchedule(
            state = cardStateFromDb(checkNotNull(state) { "Card $id has a partial schedule: state is null" }),
            step = step,
            stability = checkNotNull(stability) { "Card $id has a partial schedule: stability is null" },
            difficulty = checkNotNull(difficulty) { "Card $id has a partial schedule: difficulty is null" },
            due = Instant.ofEpochMilli(checkNotNull(due) { "Card $id has a partial schedule: due is null" }),
            lastReview = lastReview?.let(Instant::ofEpochMilli),
        )
    }
    return Flashcard(
        id = id,
        deckId = deckId,
        question = question,
        answer = answer,
        schedule = schedule,
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt),
    )
}

fun DeckDailyStatusEntity.toDomain() = DeckDailyStatus(
    deckId = deckId,
    date = LocalDate.parse(date),
    completedOnce = completedOnce,
    reviewedOnce = reviewedOnce,
    newStartedCount = newStartedCount,
)
