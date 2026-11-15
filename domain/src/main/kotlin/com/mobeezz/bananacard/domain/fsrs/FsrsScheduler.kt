package com.mobeezz.bananacard.domain.fsrs

import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.random.Random

/**
 * FSRS-6 scheduler, a line-by-line port of `Scheduler` from the Dart `fsrs` 2.0.1 package.
 *
 * Numeric semantics follow Dart exactly so results match the reference fixtures:
 * - durations are handled in whole microseconds, like Dart's `Duration` and `DateTime`;
 * - `Duration.inDays` truncates toward zero;
 * - `num.round()` rounds half away from zero ([dartRound]).
 *
 * Time is always passed in; the scheduler never reads the clock.
 */
class FsrsScheduler(
    val parameters: List<Double> = DEFAULT_PARAMETERS,
    val desiredRetention: Double = 0.9,
    val learningSteps: List<Duration> = listOf(Duration.ofMinutes(1), Duration.ofMinutes(10)),
    val relearningSteps: List<Duration> = listOf(Duration.ofMinutes(10)),
    val maximumInterval: Int = 36500,
    val enableFuzzing: Boolean = true,
    private val random: Random = Random.Default,
) {
    private val decay: Double
    private val factor: Double

    init {
        validateParameters(parameters)
        decay = -parameters[20]
        factor = Math.pow(0.9, 1 / decay) - 1
    }

    /**
     * Predicted probability that [card] is recalled at [at]. Zero for a card never reviewed.
     */
    fun retrievability(card: FsrsCard, at: Instant): Double {
        val lastReview = card.lastReview ?: return 0.0
        val elapsedDays = maxOf(0L, inDays(lastReview, at))
        return Math.pow(1 + factor * elapsedDays / card.stability!!, decay)
    }

    /** Reviews [card] with [rating] at [reviewAt] and returns the updated card. */
    fun reviewCard(card: FsrsCard, rating: Rating, reviewAt: Instant): FsrsCard {
        var state = card.state
        var step = card.step
        var stability = card.stability
        var difficulty = card.difficulty

        val daysSinceLastReview = card.lastReview?.let { inDays(it, reviewAt) }

        val nextInterval: Duration

        when (card.state) {
            CardState.LEARNING -> {
                // update the card's stability and difficulty
                if (stability == null && difficulty == null) {
                    stability = initialStability(rating)
                    difficulty = initialDifficulty(rating)
                } else if (daysSinceLastReview != null && daysSinceLastReview < 1) {
                    stability = shortTermStability(stability!!, rating)
                    difficulty = nextDifficulty(difficulty!!, rating)
                } else {
                    stability = nextStability(
                        difficulty = difficulty!!,
                        stability = stability!!,
                        retrievability = retrievability(card, reviewAt),
                        rating = rating,
                    )
                    difficulty = nextDifficulty(difficulty, rating)
                }

                // first clause handles a card previously scheduled with more learning steps
                if (learningSteps.isEmpty() ||
                    (step!! >= learningSteps.size && rating != Rating.AGAIN)
                ) {
                    state = CardState.REVIEW
                    step = null
                    nextInterval = Duration.ofDays(nextInterval(stability).toLong())
                } else {
                    when (rating) {
                        Rating.AGAIN -> {
                            step = 0
                            nextInterval = learningSteps[step]
                        }

                        Rating.HARD -> {
                            // card step stays the same
                            nextInterval = hardStepInterval(learningSteps, step)
                        }

                        Rating.GOOD -> {
                            if (step + 1 == learningSteps.size) {
                                // the last step
                                state = CardState.REVIEW
                                step = null
                                nextInterval = Duration.ofDays(nextInterval(stability).toLong())
                            } else {
                                step += 1
                                nextInterval = learningSteps[step]
                            }
                        }

                        Rating.EASY -> {
                            state = CardState.REVIEW
                            step = null
                            nextInterval = Duration.ofDays(nextInterval(stability).toLong())
                        }
                    }
                }
            }

            CardState.REVIEW -> {
                // update the card's stability and difficulty
                stability = if (daysSinceLastReview != null && daysSinceLastReview < 1) {
                    shortTermStability(stability!!, rating)
                } else {
                    nextStability(
                        difficulty = difficulty!!,
                        stability = stability!!,
                        retrievability = retrievability(card, reviewAt),
                        rating = rating,
                    )
                }
                difficulty = nextDifficulty(difficulty!!, rating)

                nextInterval = when (rating) {
                    Rating.AGAIN -> if (relearningSteps.isEmpty()) {
                        // no relearning steps: stay in review
                        Duration.ofDays(nextInterval(stability).toLong())
                    } else {
                        state = CardState.RELEARNING
                        step = 0
                        relearningSteps[0]
                    }

                    Rating.HARD, Rating.GOOD, Rating.EASY ->
                        Duration.ofDays(nextInterval(stability).toLong())
                }
            }

            CardState.RELEARNING -> {
                // update the card's stability and difficulty
                if (daysSinceLastReview != null && daysSinceLastReview < 1) {
                    stability = shortTermStability(stability!!, rating)
                    difficulty = nextDifficulty(difficulty!!, rating)
                } else {
                    stability = nextStability(
                        difficulty = difficulty!!,
                        stability = stability!!,
                        retrievability = retrievability(card, reviewAt),
                        rating = rating,
                    )
                    difficulty = nextDifficulty(difficulty, rating)
                }

                // first clause handles a card previously scheduled with more relearning steps
                if (relearningSteps.isEmpty() ||
                    (step!! >= relearningSteps.size && rating != Rating.AGAIN)
                ) {
                    state = CardState.REVIEW
                    step = null
                    nextInterval = Duration.ofDays(nextInterval(stability).toLong())
                } else {
                    when (rating) {
                        Rating.AGAIN -> {
                            step = 0
                            nextInterval = relearningSteps[step]
                        }

                        Rating.HARD -> {
                            // card step stays the same
                            nextInterval = hardStepInterval(relearningSteps, step)
                        }

                        Rating.GOOD -> {
                            if (step + 1 == relearningSteps.size) {
                                // the last step
                                state = CardState.REVIEW
                                step = null
                                nextInterval = Duration.ofDays(nextInterval(stability).toLong())
                            } else {
                                step += 1
                                nextInterval = relearningSteps[step]
                            }
                        }

                        Rating.EASY -> {
                            state = CardState.REVIEW
                            step = null
                            nextInterval = Duration.ofDays(nextInterval(stability).toLong())
                        }
                    }
                }
            }
        }

        val scheduledInterval = if (enableFuzzing && state == CardState.REVIEW) {
            fuzzedInterval(nextInterval)
        } else {
            nextInterval
        }

        return FsrsCard(
            state = state,
            step = step,
            stability = stability,
            difficulty = difficulty,
            due = reviewAt.plus(scheduledInterval),
            lastReview = reviewAt,
        )
    }

    /** Interval for a HARD rating while in (re)learning steps. */
    private fun hardStepInterval(steps: List<Duration>, step: Int): Duration = when {
        // Dart `Duration * 1.5`: microseconds multiplied, then rounded
        step == 0 && steps.size == 1 -> fromMicros(dartRound(toMicros(steps[0]) * 1.5))
        // Dart `Duration ~/ 2`: microseconds truncating-divided
        step == 0 && steps.size >= 2 -> fromMicros((toMicros(steps[0]) + toMicros(steps[1])) / 2)
        else -> steps[step]
    }

    private fun clampDifficulty(difficulty: Double): Double =
        difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

    private fun clampStability(stability: Double): Double = maxOf(stability, STABILITY_MIN)

    private fun initialStability(rating: Rating): Double =
        clampStability(parameters[rating.value - 1])

    private fun initialDifficulty(rating: Rating): Double {
        val initialDifficulty =
            parameters[4] - Math.exp(parameters[5] * (rating.value - 1)) + 1
        return clampDifficulty(initialDifficulty)
    }

    private fun nextInterval(stability: Double): Int {
        val raw = (stability / factor) * (Math.pow(desiredRetention, 1 / decay) - 1)
        var nextInterval = dartRound(raw) // intervals are full days
        nextInterval = maxOf(nextInterval, 1L) // must be at least 1 day long
        nextInterval = minOf(nextInterval, maximumInterval.toLong())
        return nextInterval.toInt()
    }

    private fun shortTermStability(stability: Double, rating: Rating): Double {
        var increase = Math.exp(parameters[17] * (rating.value - 3 + parameters[18])) *
            Math.pow(stability, -parameters[19])

        if (rating == Rating.GOOD || rating == Rating.EASY) {
            increase = maxOf(increase, 1.0)
        }

        return clampStability(stability * increase)
    }

    private fun nextDifficulty(difficulty: Double, rating: Rating): Double {
        val arg1 = initialDifficulty(Rating.EASY)

        val deltaDifficulty = -(parameters[6] * (rating.value - 3))
        // linear damping
        val arg2 = difficulty + (10.0 - difficulty) * deltaDifficulty / 9.0

        // mean reversion
        val next = parameters[7] * arg1 + (1 - parameters[7]) * arg2

        return clampDifficulty(next)
    }

    private fun nextStability(
        difficulty: Double,
        stability: Double,
        retrievability: Double,
        rating: Rating,
    ): Double {
        val next = if (rating == Rating.AGAIN) {
            nextForgetStability(difficulty, stability, retrievability)
        } else {
            nextRecallStability(difficulty, stability, retrievability, rating)
        }
        return clampStability(next)
    }

    private fun nextForgetStability(
        difficulty: Double,
        stability: Double,
        retrievability: Double,
    ): Double {
        val longTerm = parameters[11] *
            Math.pow(difficulty, -parameters[12]) *
            (Math.pow(stability + 1, parameters[13]) - 1) *
            Math.exp((1 - retrievability) * parameters[14])

        val shortTerm = stability / Math.exp(parameters[17] * parameters[18])

        return minOf(longTerm, shortTerm)
    }

    private fun nextRecallStability(
        difficulty: Double,
        stability: Double,
        retrievability: Double,
        rating: Rating,
    ): Double {
        val hardPenalty = if (rating == Rating.HARD) parameters[15] else 1.0
        val easyBonus = if (rating == Rating.EASY) parameters[16] else 1.0

        return stability *
            (
                1 +
                    Math.exp(parameters[8]) *
                    (11 - difficulty) *
                    Math.pow(stability, -parameters[9]) *
                    (Math.exp((1 - retrievability) * parameters[10]) - 1) *
                    hardPenalty *
                    easyBonus
                )
    }

    /** Adds a small random fuzz to a review interval. Intervals under 2.5 days are left as-is. */
    private fun fuzzedInterval(interval: Duration): Duration {
        val intervalDays = toMicros(interval) / MICROS_PER_DAY

        if (intervalDays < 2.5) {
            return interval
        }

        var delta = 1.0
        for (range in FUZZ_RANGES) {
            delta += range.factor * maxOf(minOf(intervalDays.toDouble(), range.end) - range.start, 0.0)
        }

        var minIvl = dartRound(intervalDays - delta)
        var maxIvl = dartRound(intervalDays + delta)

        // make sure minIvl and maxIvl fall into a valid range
        minIvl = maxOf(2L, minIvl)
        maxIvl = minOf(maxIvl, maximumInterval.toLong())
        minIvl = minOf(minIvl, maxIvl)

        // a random value between minIvl and maxIvl
        val fuzzedDays = (random.nextDouble() * (maxIvl - minIvl + 1)) + minIvl
        val days = minOf(dartRound(fuzzedDays), maximumInterval.toLong())

        return Duration.ofDays(days)
    }

    private class FuzzRange(val start: Double, val end: Double, val factor: Double)

    companion object {
        /** FSRS-6 default weights (Dart `fsrs` 2.0.1 `defaultParameters`). */
        val DEFAULT_PARAMETERS: List<Double> = listOf(
            0.2172,
            1.1771,
            3.2602,
            16.1507,
            7.0114,
            0.57,
            2.0966,
            0.0069,
            1.5261,
            0.112,
            1.0178,
            1.849,
            0.1133,
            0.3127,
            2.2934,
            0.2191,
            3.0004,
            0.7536,
            0.3332,
            0.1437,
            0.2,
        )

        private const val STABILITY_MIN = 0.001
        private const val INITIAL_STABILITY_MAX = 100.0
        private const val MIN_DIFFICULTY = 1.0
        private const val MAX_DIFFICULTY = 10.0
        private const val MICROS_PER_DAY = 86_400_000_000L

        private val LOWER_BOUNDS_PARAMETERS: List<Double> = listOf(
            STABILITY_MIN,
            STABILITY_MIN,
            STABILITY_MIN,
            STABILITY_MIN,
            1.0,
            0.001,
            0.001,
            0.001,
            0.0,
            0.0,
            0.001,
            0.001,
            0.001,
            0.001,
            0.0,
            0.0,
            1.0,
            0.0,
            0.0,
            0.0,
            0.1,
        )

        private val UPPER_BOUNDS_PARAMETERS: List<Double> = listOf(
            INITIAL_STABILITY_MAX,
            INITIAL_STABILITY_MAX,
            INITIAL_STABILITY_MAX,
            INITIAL_STABILITY_MAX,
            10.0,
            4.0,
            4.0,
            0.75,
            4.5,
            0.8,
            3.5,
            5.0,
            0.25,
            0.9,
            4.0,
            1.0,
            6.0,
            2.0,
            2.0,
            0.8,
            0.8,
        )

        private val FUZZ_RANGES: List<FuzzRange> = listOf(
            FuzzRange(start = 2.5, end = 7.0, factor = 0.15),
            FuzzRange(start = 7.0, end = 20.0, factor = 0.1),
            FuzzRange(start = 20.0, end = Double.POSITIVE_INFINITY, factor = 0.05),
        )

        private fun validateParameters(parameters: List<Double>) {
            require(parameters.size == LOWER_BOUNDS_PARAMETERS.size) {
                "Expected ${LOWER_BOUNDS_PARAMETERS.size} parameters, got ${parameters.size}."
            }

            val errors = parameters.indices.mapNotNull { i ->
                val parameter = parameters[i]
                val lower = LOWER_BOUNDS_PARAMETERS[i]
                val upper = UPPER_BOUNDS_PARAMETERS[i]
                if (lower <= parameter && parameter <= upper) {
                    null
                } else {
                    "parameters[$i] = $parameter is out of bounds: ($lower, $upper)"
                }
            }

            require(errors.isEmpty()) {
                "One or more parameters are out of bounds:\n${errors.joinToString("\n")}"
            }
        }

        /** Dart `DateTime.difference(...).inDays`: whole microseconds, truncated toward zero. */
        private fun inDays(from: Instant, to: Instant): Long =
            ChronoUnit.MICROS.between(from, to) / MICROS_PER_DAY

        /** Dart `num.round()`: rounds half away from zero (Math.round alone rounds -2.5 to -2). */
        private fun dartRound(value: Double): Long =
            if (value < 0) -Math.round(-value) else Math.round(value)

        private fun toMicros(duration: Duration): Long =
            Math.addExact(Math.multiplyExact(duration.seconds, 1_000_000L), duration.nano / 1_000L)

        private fun fromMicros(micros: Long): Duration = Duration.of(micros, ChronoUnit.MICROS)
    }
}
