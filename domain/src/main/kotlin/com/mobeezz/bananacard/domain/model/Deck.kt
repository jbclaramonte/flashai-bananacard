package com.mobeezz.bananacard.domain.model

import java.time.Instant

enum class DeckType { LANGUAGE, GENERAL }

/** [type] null means auto-detected. */
data class Deck(
    val id: String,
    val name: String,
    val icon: String,
    val iconColor: Long,
    val type: DeckType?,
    val sourceLanguage: String?,
    val targetLanguage: String?,
    val description: String?,
    val isPaused: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        val DEFAULT_ICON: String = "🇪🇸"

        val ICON_PRESETS: List<String> =
            listOf("🇪🇸", "📚", "🎨", "🧬", "🔍", "🇺🇸", "🎹", "🚀", "⚽", "🐶", "💼")

        val PASTEL_COLORS: List<Long> = listOf(
            0xFFFFE5E5, 0xFFE5F0FF, 0xFFF0E5FF, 0xFFFFF4E5,
            0xFFEBF5FF, 0xFFE6FAF4, 0xFFFFF0F0, 0xFFF5FFE5,
        )
    }
}
