package com.johnlrt.freenotes.engine

import com.johnlrt.freenotes.data.FlashcardItem
import kotlin.math.max
import kotlin.math.roundToInt

enum class ReviewRating(val quality: Int, val label: String) {
    AGAIN(1, "Again"),
    HARD(3, "Hard"),
    GOOD(4, "Good"),
    EASY(5, "Easy")
}

/**
 * SM-2 spaced-repetition scheduler for Freenotes Flashcards study decks.
 */
object FlashcardScheduler {

    private const val DAY_MS = 24L * 60L * 60L * 1000L

    fun review(card: FlashcardItem, rating: ReviewRating, nowMs: Long = System.currentTimeMillis()): FlashcardItem {
        val q = rating.quality
        val newEase = max(
            1.3f,
            card.easeFactor + (0.1f - (5 - q) * (0.08f + (5 - q) * 0.02f))
        )

        val (newReps, newInterval) = if (q < 3) {
            0 to 1
        } else {
            val reps = card.repetitions + 1
            val interval = when (reps) {
                1 -> 1
                2 -> 6
                else -> (card.intervalDays * newEase).roundToInt().coerceAtLeast(1)
            }
            reps to interval
        }

        return card.copy(
            repetitions = newReps,
            intervalDays = newInterval,
            easeFactor = newEase,
            nextReviewAtMs = nowMs + newInterval * DAY_MS
        )
    }
}
