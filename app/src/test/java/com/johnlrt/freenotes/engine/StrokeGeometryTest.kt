package com.johnlrt.freenotes.engine

import com.johnlrt.freenotes.data.ShapeKind
import com.johnlrt.freenotes.data.Stroke
import com.johnlrt.freenotes.data.StrokePoint
import com.johnlrt.freenotes.data.ToolType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class StrokeGeometryTest {

    @Test
    fun fountainPenWidensWithHigherPressure() {
        val stroke = Stroke(toolType = ToolType.FOUNTAIN_PEN, baseWidth = 6f, pressureSensitivity = 1f)
        val light = StrokePoint(10f, 10f, pressure = 0.2f, timeOffsetMs = 10L)
        val heavy = StrokePoint(12f, 10f, pressure = 0.95f, timeOffsetMs = 18L)
        val wLight = StrokeGeometry.computeWidth(stroke, null, light, 3, 10)
        val wHeavy = StrokeGeometry.computeWidth(stroke, null, heavy, 3, 10)
        assertTrue("Expected heavy width ($wHeavy) > light width ($wLight)", wHeavy > wLight)
    }

    @Test
    fun snapToShapeRecognizesStraightLine() {
        val pts = (0..10).map { i -> StrokePoint(i * 20f, 100f + (i % 2) * 1.5f, 0.5f, i * 8L) }
        val snapped = StrokeGeometry.snapToShape(pts, ShapeKind.AUTO)
        assertEquals(2, snapped.size)
        assertEquals(0f, snapped.first().x, 1e-3f)
        assertEquals(200f, snapped.last().x, 1e-3f)
    }

    @Test
    fun snapToShapeRecognizesClosedCircleAsEllipse() {
        val pts = (0..36).map { i ->
            val a = 2.0 * PI * i / 36.0
            StrokePoint(200f + (80f * cos(a)).toFloat(), 200f + (80f * sin(a)).toFloat(), 0.5f, i * 8L)
        }
        val snapped = StrokeGeometry.snapToShape(pts, ShapeKind.AUTO)
        assertEquals(49, snapped.size)
    }

    @Test
    fun snapToShapeGeneratesTriangleAndArrow() {
        val pts = listOf(StrokePoint(0f, 0f), StrokePoint(100f, 100f))
        assertEquals(4, StrokeGeometry.snapToShape(pts, ShapeKind.TRIANGLE).size)
        assertEquals(5, StrokeGeometry.snapToShape(pts, ShapeKind.ARROW).size)
    }
}
