package com.johnlrt.freenotes.engine

import com.johnlrt.freenotes.data.Stroke
import com.johnlrt.freenotes.data.StrokePoint
import com.johnlrt.freenotes.data.TapeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HitTestEngineTest {

    @Test
    fun partialEraserSplitsStrokeCleanly() {
        val pts = (0..20).map { i -> StrokePoint(i * 10f, 50f, 0.5f, i * 8L) }
        val stroke = Stroke(baseWidth = 4f, points = pts)
        val split = HitTestEngine.splitStrokeByEraser(stroke, cx = 100f, cy = 50f, radius = 15f)
        assertEquals(2, split.size)
        assertTrue(split[0].points.last().x < 90f)
        assertTrue(split[1].points.first().x > 110f)
    }

    @Test
    fun lassoSelectsStrokeInsidePolygon() {
        val poly = listOf(
            StrokePoint(0f, 0f),
            StrokePoint(100f, 0f),
            StrokePoint(100f, 100f),
            StrokePoint(0f, 100f)
        )
        val inside = Stroke(points = listOf(StrokePoint(20f, 20f), StrokePoint(80f, 80f)))
        val outside = Stroke(points = listOf(StrokePoint(150f, 150f), StrokePoint(200f, 200f)))

        assertTrue(HitTestEngine.isStrokeInLasso(inside, poly))
        assertFalse(HitTestEngine.isStrokeInLasso(outside, poly))
    }

    @Test
    fun tapeHitTestDetectsTapOnStrip() {
        val tape = TapeItem(startX = 100f, startY = 200f, endX = 400f, endY = 200f, thickness = 40f)
        assertTrue(HitTestEngine.hitTestTape(tape, 250f, 210f))
        assertFalse(HitTestEngine.hitTestTape(tape, 250f, 300f))
    }
}
