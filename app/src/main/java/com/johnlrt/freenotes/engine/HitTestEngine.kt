package com.johnlrt.freenotes.engine

import com.johnlrt.freenotes.data.Stroke
import com.johnlrt.freenotes.data.StrokePoint
import com.johnlrt.freenotes.data.TapeItem
import com.johnlrt.freenotes.data.TextBoxItem
import java.util.UUID
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Geometry hit-testing for Whole-Stroke Eraser, Partial/Pixel Eraser (stroke splitting),
 * Freehand Lasso polygon selection, and Active Recall Tape toggling.
 */
object HitTestEngine {

    fun distancePointToSegment(
        px: Float,
        py: Float,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float
    ): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        val lenSq = dx * dx + dy * dy
        if (lenSq < 1e-6f) {
            return hypot((px - x1).toDouble(), (py - y1).toDouble()).toFloat()
        }
        val t = (((px - x1) * dx + (py - y1) * dy) / lenSq).coerceIn(0f, 1f)
        val projX = x1 + t * dx
        val projY = y1 + t * dy
        return hypot((px - projX).toDouble(), (py - projY).toDouble()).toFloat()
    }

    /**
     * Returns true if the eraser circle `(cx, cy, radius)` intersects the given stroke.
     */
    fun hitTestStroke(stroke: Stroke, cx: Float, cy: Float, radius: Float): Boolean {
        val pts = stroke.points
        if (pts.isEmpty()) return false
        val threshold = radius + stroke.baseWidth * 0.5f

        if (pts.size == 1) {
            return hypot((cx - pts[0].x).toDouble(), (cy - pts[0].y).toDouble()) <= threshold
        }

        // Fast bounding-box reject
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (p in pts) {
            if (p.x < minX) minX = p.x
            if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x
            if (p.y > maxY) maxY = p.y
        }
        if (cx < minX - threshold || cx > maxX + threshold || cy < minY - threshold || cy > maxY + threshold) {
            return false
        }

        for (i in 1 until pts.size) {
            val d = distancePointToSegment(cx, cy, pts[i - 1].x, pts[i - 1].y, pts[i].x, pts[i].y)
            if (d <= threshold) return true
        }
        return false
    }

    /**
     * Partial/Pixel eraser: splits a stroke into surviving sub-strokes by removing points
     * that fall inside the eraser circle `(cx, cy, radius)`.
     */
    fun splitStrokeByEraser(stroke: Stroke, cx: Float, cy: Float, radius: Float): List<Stroke> {
        if (!hitTestStroke(stroke, cx, cy, radius)) return listOf(stroke)

        val threshold = radius + stroke.baseWidth * 0.45f
        val chunks = mutableListOf<MutableList<StrokePoint>>()
        var currentChunk = mutableListOf<StrokePoint>()

        for (pt in stroke.points) {
            val dist = hypot((pt.x - cx).toDouble(), (pt.y - cy).toDouble()).toFloat()
            if (dist <= threshold) {
                if (currentChunk.size >= 2) {
                    chunks.add(currentChunk)
                }
                currentChunk = mutableListOf()
            } else {
                currentChunk.add(pt)
            }
        }
        if (currentChunk.size >= 2) {
            chunks.add(currentChunk)
        }

        return chunks.mapIndexed { idx, pts ->
            stroke.copy(
                id = if (idx == 0) stroke.id else UUID.randomUUID().toString(),
                points = pts
            )
        }
    }

    /**
     * Ray-casting point-in-polygon check for freehand Lasso selection.
     */
    fun isPointInPolygon(px: Float, py: Float, polygon: List<StrokePoint>): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val xi = polygon[i].x
            val yi = polygon[i].y
            val xj = polygon[j].x
            val yj = polygon[j].y
            val intersects = ((yi > py) != (yj > py)) &&
                (px < (xj - xi) * (py - yi) / ((yj - yi).takeIf { it != 0f } ?: 1e-6f) + xi)
            if (intersects) inside = !inside
            j = i
        }
        return inside
    }

    /**
     * Returns true if at least half of a stroke's points fall inside the Lasso polygon.
     */
    fun isStrokeInLasso(stroke: Stroke, lassoPolygon: List<StrokePoint>): Boolean {
        if (stroke.points.isEmpty() || lassoPolygon.size < 3) return false
        var hits = 0
        for (p in stroke.points) {
            if (isPointInPolygon(p.x, p.y, lassoPolygon)) {
                hits++
            }
        }
        return hits * 2 >= stroke.points.size
    }

    /**
     * Hit-tests an Active Recall Tape strip for tap-to-reveal / tap-to-cover toggling.
     */
    fun hitTestTape(tape: TapeItem, x: Float, y: Float): Boolean {
        val d = distancePointToSegment(x, y, tape.startX, tape.startY, tape.endX, tape.endY)
        return d <= max(tape.thickness * 0.6f, 20f)
    }

    /**
     * Hit-tests a TextBox bounding area.
     */
    fun hitTestTextBox(box: TextBoxItem, x: Float, y: Float): Boolean {
        val lines = max(1, box.text.count { it == '\n' } + 1)
        val estimatedHeight = max(64f, lines * box.fontSizeSp * 1.6f + 24f)
        return x >= box.x - 8f && x <= box.x + box.width + 8f &&
            y >= box.y - 8f && y <= box.y + estimatedHeight + 8f
    }

    /**
     * Computes axis-aligned bounding box `(minX, minY, maxX, maxY)` of a set of strokes.
     */
    fun boundingBoxOfStrokes(strokes: List<Stroke>): FloatArray? {
        if (strokes.isEmpty()) return null
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var found = false
        for (s in strokes) {
            for (p in s.points) {
                found = true
                minX = min(minX, p.x)
                minY = min(minY, p.y)
                maxX = max(maxX, p.x)
                maxY = max(maxY, p.y)
            }
        }
        return if (found) floatArrayOf(minX, minY, maxX, maxY) else null
    }
}
