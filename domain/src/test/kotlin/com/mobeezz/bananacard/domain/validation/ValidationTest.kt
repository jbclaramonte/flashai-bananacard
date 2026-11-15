package com.mobeezz.bananacard.domain.validation

import org.junit.Test
import org.junit.Assert.assertEquals

class ValidationTest {
    private fun invalid(e: ValidationError) = ValidationResult.Invalid(e)

    @Test fun deckNameTooShort() = assertEquals(invalid(ValidationError.TOO_SHORT), validateDeckName(" a "))
    @Test fun deckNameRequired() = assertEquals(invalid(ValidationError.REQUIRED), validateDeckName(""))
    @Test fun deckNameTooLong() = assertEquals(invalid(ValidationError.TOO_LONG), validateDeckName("a".repeat(51)))
    @Test fun deckNameMaxOk() = assertEquals(ValidationResult.Valid("a".repeat(50)), validateDeckName("a".repeat(50)))
    @Test fun deckNameTrimmed() = assertEquals(ValidationResult.Valid("Spanish"), validateDeckName("  Spanish  "))

    @Test fun descriptionBlankBecomesNull() = assertEquals(ValidationResult.Valid(null), normalizeDeckDescription("   "))
    @Test fun descriptionNullBecomesNull() = assertEquals(ValidationResult.Valid(null), normalizeDeckDescription(null))
    @Test fun descriptionTrimmed() = assertEquals(ValidationResult.Valid("hi"), normalizeDeckDescription(" hi "))
    @Test fun descriptionTooLong() = assertEquals(invalid(ValidationError.TOO_LONG), normalizeDeckDescription("a".repeat(301)))

    @Test fun questionRequired() = assertEquals(invalid(ValidationError.REQUIRED), validateQuestion("  "))
    @Test fun questionTooLong() = assertEquals(invalid(ValidationError.TOO_LONG), validateQuestion("a".repeat(501)))
    @Test fun questionOk() = assertEquals(ValidationResult.Valid("q"), validateQuestion(" q "))

    @Test fun answerRequired() = assertEquals(invalid(ValidationError.REQUIRED), validateAnswer(" "))
    @Test fun answerTooLong() = assertEquals(invalid(ValidationError.TOO_LONG), validateAnswer("a".repeat(1001)))
    @Test fun answerOk() = assertEquals(ValidationResult.Valid("a"), validateAnswer("a"))
}
