package com.johnlrt.freenotes.engine

import com.johnlrt.freenotes.data.BookmarkItem
import com.johnlrt.freenotes.data.FlashcardItem
import com.johnlrt.freenotes.data.HyperlinkItem
import com.johnlrt.freenotes.data.NotePage
import com.johnlrt.freenotes.data.Notebook
import com.johnlrt.freenotes.data.StickerItem
import com.johnlrt.freenotes.data.Stroke
import com.johnlrt.freenotes.data.StrokePoint
import com.johnlrt.freenotes.data.ToolType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlashcardAndSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun sm2SchedulerAdvancesIntervalsAndResetsOnAgain() {
        val now = 1_700_000_000_000L
        var card = FlashcardItem(front = "What is SM-2?", back = "Spaced repetition algorithm")

        // First review: Good -> 1 day, reps = 1
        card = FlashcardScheduler.review(card, ReviewRating.GOOD, now)
        assertEquals(1, card.repetitions)
        assertEquals(1, card.intervalDays)
        assertTrue(card.nextReviewAtMs > now)

        // Second review: Easy -> 6 days, reps = 2, ease increases
        val prevEase = card.easeFactor
        card = FlashcardScheduler.review(card, ReviewRating.EASY, now)
        assertEquals(2, card.repetitions)
        assertEquals(6, card.intervalDays)
        assertTrue(card.easeFactor > prevEase)

        // Third review: Hard -> reps = 3, interval grows
        card = FlashcardScheduler.review(card, ReviewRating.HARD, now)
        assertEquals(3, card.repetitions)
        assertTrue(card.intervalDays >= 6)

        // Lapse: Again -> resets repetitions to 0 and interval to 1
        card = FlashcardScheduler.review(card, ReviewRating.AGAIN, now)
        assertEquals(0, card.repetitions)
        assertEquals(1, card.intervalDays)
        assertTrue(card.easeFactor >= 1.3f)
    }

    @Test
    fun notebookFullJsonRoundTripPreservesAllStudyData() {
        val page = NotePage(
            strokes = listOf(
                Stroke(
                    toolType = ToolType.SHAPE,
                    points = listOf(StrokePoint(0f, 0f), StrokePoint(100f, 100f)),
                    isFilledShape = true,
                    fillColorArgb = 0x332563EB,
                    borderDashed = true
                )
            ),
            stickers = listOf(StickerItem(label = "Key", emoji = "💡", x = 120f, y = 140f)),
            hyperlinks = listOf(HyperlinkItem(label = "See Page 2", targetPageIndex = 1, x = 80f, y = 220f))
        )
        val notebook = Notebook(
            title = "Anatomy 101",
            pages = listOf(page, NotePage()),
            bookmarks = listOf(BookmarkItem(pageIndex = 0, title = "Intro")),
            flashcards = listOf(FlashcardItem(front = "Q1", back = "A1"))
        )

        val encoded = json.encodeToString(notebook)
        val decoded = json.decodeFromString<Notebook>(encoded)

        assertEquals(notebook.title, decoded.title)
        assertEquals(2, decoded.pages.size)
        assertEquals(true, decoded.pages[0].strokes[0].borderDashed)
        assertEquals(true, decoded.pages[0].strokes[0].isFilledShape)
        assertEquals(0x332563EBL, decoded.pages[0].strokes[0].fillColorArgb)
        assertEquals("Key", decoded.pages[0].stickers[0].label)
        assertEquals("💡", decoded.pages[0].stickers[0].emoji)
        assertEquals(1, decoded.pages[0].hyperlinks[0].targetPageIndex)
        assertEquals("Intro", decoded.bookmarks[0].title)
        assertEquals("Q1", decoded.flashcards[0].front)
    }
}
