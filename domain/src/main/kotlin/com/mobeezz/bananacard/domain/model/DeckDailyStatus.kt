package com.mobeezz.bananacard.domain.model

import java.time.LocalDate

data class DeckDailyStatus(
    val deckId: String,
    val date: LocalDate,
    val completedOnce: Boolean,
    val reviewedOnce: Boolean,
    val newStartedCount: Int,
)
