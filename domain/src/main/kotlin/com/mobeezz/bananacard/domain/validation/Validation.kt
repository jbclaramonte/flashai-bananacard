package com.mobeezz.bananacard.domain.validation

enum class ValidationError { REQUIRED, TOO_SHORT, TOO_LONG }

sealed interface ValidationResult<out T> {
    data class Valid<T>(val value: T) : ValidationResult<T>
    data class Invalid(val error: ValidationError) : ValidationResult<Nothing>
}

private const val DECK_NAME_MIN = 2
private const val DECK_NAME_MAX = 50
private const val DESCRIPTION_MAX = 300
private const val QUESTION_MAX = 500
private const val ANSWER_MAX = 1000

fun validateDeckName(raw: String): ValidationResult<String> {
    val v = raw.trim()
    return when {
        v.isEmpty() -> ValidationResult.Invalid(ValidationError.REQUIRED)
        v.length < DECK_NAME_MIN -> ValidationResult.Invalid(ValidationError.TOO_SHORT)
        v.length > DECK_NAME_MAX -> ValidationResult.Invalid(ValidationError.TOO_LONG)
        else -> ValidationResult.Valid(v)
    }
}

fun normalizeDeckDescription(raw: String?): ValidationResult<String?> {
    val v = raw?.trim().orEmpty()
    return when {
        v.isEmpty() -> ValidationResult.Valid(null)
        v.length > DESCRIPTION_MAX -> ValidationResult.Invalid(ValidationError.TOO_LONG)
        else -> ValidationResult.Valid(v)
    }
}

fun validateQuestion(raw: String): ValidationResult<String> = validateRequired(raw, QUESTION_MAX)

fun validateAnswer(raw: String): ValidationResult<String> = validateRequired(raw, ANSWER_MAX)

private fun validateRequired(raw: String, max: Int): ValidationResult<String> {
    val v = raw.trim()
    return when {
        v.isEmpty() -> ValidationResult.Invalid(ValidationError.REQUIRED)
        v.length > max -> ValidationResult.Invalid(ValidationError.TOO_LONG)
        else -> ValidationResult.Valid(v)
    }
}
