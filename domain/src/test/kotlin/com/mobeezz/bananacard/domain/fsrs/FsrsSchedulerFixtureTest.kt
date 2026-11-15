package com.mobeezz.bananacard.domain.fsrs

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.math.abs

/**
 * Replays the reference scenarios generated from the Dart `fsrs` 2.0.1 package
 * (tools/fsrs-fixtures) and checks the Kotlin port produces the same cards.
 */
class FsrsSchedulerFixtureTest {

    @Serializable
    private data class Fixtures(val scenarios: List<Scenario>)

    @Serializable
    private data class Scenario(val name: String, val steps: List<Step>)

    @Serializable
    private data class Step(val rating: Int, val reviewAt: String, val expected: Expected)

    @Serializable
    private data class Expected(
        val state: Int,
        val step: Int?,
        val stability: Double?,
        val difficulty: Double?,
        val due: String,
        val lastReview: String?,
    )

    private fun loadFixtures(): Fixtures {
        val stream = requireNotNull(javaClass.getResourceAsStream("/fsrs/fixtures.json")) {
            "fixtures.json not found on the test classpath"
        }
        val text = stream.bufferedReader().use { it.readText() }
        return Json.decodeFromString(Fixtures.serializer(), text)
    }

    @Test
    fun scenariosMatchDartReference() {
        val fixtures = loadFixtures()
        assertEquals("expected 8 reference scenarios", 8, fixtures.scenarios.size)

        val scheduler = FsrsScheduler(enableFuzzing = false)

        for (scenario in fixtures.scenarios) {
            var card = FsrsCard.new(Instant.parse(scenario.steps.first().reviewAt))
            scenario.steps.forEachIndexed { index, step ->
                val case = "${scenario.name}[$index]"
                val rating = Rating.entries.single { it.value == step.rating }
                card = scheduler.reviewCard(card, rating, Instant.parse(step.reviewAt))
                val expected = step.expected

                assertEquals("$case state", expected.state, card.state.value)
                assertEquals("$case step", expected.step, card.step)
                assertClose("$case stability", expected.stability, card.stability)
                assertClose("$case difficulty", expected.difficulty, card.difficulty)
                assertEquals(
                    "$case due",
                    Instant.parse(expected.due).toEpochMilli(),
                    card.due.toEpochMilli(),
                )
                assertEquals(
                    "$case lastReview",
                    expected.lastReview?.let { Instant.parse(it).toEpochMilli() },
                    card.lastReview?.toEpochMilli(),
                )
            }
        }
    }

    private fun assertClose(message: String, expected: Double?, actual: Double?) {
        if (expected == null) {
            assertNull(message, actual)
            return
        }
        assertNotNull(message, actual)
        assertTrue(
            "$message: expected $expected but was $actual",
            abs(expected - actual!!) <= TOLERANCE,
        )
    }

    private companion object {
        const val TOLERANCE = 1e-9
    }
}
