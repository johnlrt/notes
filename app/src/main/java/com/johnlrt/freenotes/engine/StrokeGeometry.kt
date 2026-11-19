package com.johnlrt.freenotes.engine

import com.johnlrt.freenotes.data.ShapeKind
import com.johnlrt.freenotes.data.Stroke
import com.johnlrt.freenotes.data.StrokePoint
import com.johnlrt.freenotes.data.ToolType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class RibbonSegment(
    val x0: Float,
    val y0: Float,
    val x1: Float,
    val y1: Float,
    val width: Float,
    val alpha: Float
)

/**
 * Pure-Kotlin stroke geometry & shape recognition engine inspired by `Clipper2NativeLib`.
 * Computes pressure-sensitive widths, art-pen nib contrast, pencil grain alpha, and
 * auto-snapped geometric shapes.
 */
object StrokeGeometry {

    /**
     * Computes the effective stroke width at a given point based on tool type,
     * stylus pressure, velocity, and optional art-pen nib angle.
     */
    fun computeWidth(
        stroke: Stroke,
        prev: StrokePoint?,
        curr: StrokePoint,
        index: Int,
        totalCount: Int
    ): Float {
        val base = stroke.baseWidth.coerceAtLeast(0.8f)
        val p = curr.pressure.coerceIn(0.05f, 1.0f)
        val sens = stroke.pressureSensitivity.coerceIn(0f, 1f)

        return when (stroke.toolType) {
            ToolType.BALLPOINT, ToolType.HIGHLIGHTER, ToolType.SHAPE, ToolType.LASER -> base

            ToolType.FOUNTAIN_PEN -> {
                val pressureFactor = (1f - sens) + sens * (0.45f + 1.05f * p)
                val velocityFactor = if (prev != null) {
                    val dt = (curr.timeOffsetMs - prev.timeOffsetMs).coerceAtLeast(1L)
                    val dist = hypot((curr.x - prev.x).toDouble(), (curr.y - prev.y).toDouble()).toFloat()
                    val speed = dist / dt
                    (1.1f - (speed * 0.14f)).coerceIn(0.68f, 1.12f)
                } else 1f
                // Subtle entry/exit taper like a real fountain nib
                val taper = when {
                    totalCount > 6 && index < 2 -> 0.72f + 0.14f * index
                    totalCount > 6 && index > totalCount - 3 -> 0.72f + 0.14f * (totalCount - 1 - index)
                    else -> 1f
                }
                (base * pressureFactor * velocityFactor * taper).coerceIn(base * 0.35f, base * 1.85f)
            }

            ToolType.ART_PEN -> {
                val angleRad = stroke.nibAngleDeg * PI / 180.0
                val strokeAngle = if (prev != null) {
                    atan2((curr.y - prev.y).toDouble(), (curr.x - prev.x).toDouble())
                } else 0.0
                val projection = abs(sin(strokeAngle - angleRad)).toFloat()
                val nibFactor = 0.35f + 1.25f * projection
                val pressureFactor = (1f - sens * 0.5f) + (sens * 0.5f) * p
                (base * nibFactor * pressureFactor).coerceIn(base * 0.3f, base * 2.0f)
            }

            ToolType.PENCIL -> {
                val pressureFactor = (1f - sens * 0.4f) + (sens * 0.4f) * (0.65f + 0.55f * p)
                (base * pressureFactor).coerceIn(base * 0.5f, base * 1.35f)
            }

            else -> base
        }
    }

    /**
     * Computes per-segment alpha (Pencil varies opacity with pressure; Highlighter stays translucent).
     */
    fun computeAlpha(stroke: Stroke, curr: StrokePoint): Float {
        return when (stroke.toolType) {
            ToolType.PENCIL -> {
                val p = curr.pressure.coerceIn(0.15f, 1f)
                (stroke.opacity * (0.35f + 0.65f * p)).coerceIn(0.12f, 0.92f)
            }
            ToolType.HIGHLIGHTER -> stroke.opacity.coerceIn(0.1f, 0.55f)
            else -> stroke.opacity.coerceIn(0.05f, 1f)
        }
    }

    /**
     * Builds smoothed Catmull-Rom / quadratic-midpoint ribbon segments for rendering.
     */
    fun buildSegments(stroke: Stroke): List<RibbonSegment> {
        val pts = stroke.points
        if (pts.size < 2) return emptyList()

        val result = ArrayList<RibbonSegment>(pts.size - 1)
        var prevWidth = computeWidth(stroke, null, pts[0], 0, pts.size)

        for (i in 1 until pts.size) {
            val p0 = pts[i - 1]
            val p1 = pts[i]
            val targetWidth = computeWidth(stroke, p0, p1, i, pts.size)
            // Smooth width transitions to avoid staircase artifacts
            val smoothWidth = prevWidth * 0.6f + targetWidth * 0.4f
            prevWidth = smoothWidth

            result.add(
                RibbonSegment(
                    x0 = p0.x,
                    y0 = p0.y,
                    x1 = p1.x,
                    y1 = p1.y,
                    width = smoothWidth,
                    alpha = computeAlpha(stroke, p1)
                )
            )
        }
        return result
    }

    /**
     * Snaps a freehand stroke into a clean geometric shape (Line, Rectangle, Ellipse, Triangle, Arrow).
     */
    fun snapToShape(points: List<StrokePoint>, kind: ShapeKind = ShapeKind.AUTO): List<StrokePoint> {
        if (points.size < 2) return points
        val first = points.first()
        val last = points.last()

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var pathLen = 0f

        for (i in points.indices) {
            val pt = points[i]
            minX = min(minX, pt.x)
            minY = min(minY, pt.y)
            maxX = max(maxX, pt.x)
            maxY = max(maxY, pt.y)
            if (i > 0) {
                pathLen += hypot((pt.x - points[i - 1].x).toDouble(), (pt.y - points[i - 1].y).toDouble()).toFloat()
            }
        }

        val directDist = hypot((last.x - first.x).toDouble(), (last.y - first.y).toDouble()).toFloat()
        val boxW = (maxX - minX).coerceAtLeast(1f)
        val boxH = (maxY - minY).coerceAtLeast(1f)
        val diag = hypot(boxW.toDouble(), boxH.toDouble()).toFloat()

        val resolvedKind = if (kind != ShapeKind.AUTO) {
            kind
        } else {
            val isClosed = directDist < diag * 0.28f && pathLen > diag * 1.6f
            if (!isClosed && directDist > 12f && pathLen / directDist < 1.22f) {
                ShapeKind.LINE
            } else if (isClosed) {
                // Distinguish ellipse vs rectangle by measuring average distance to box corners vs center
                val cx = (minX + maxX) / 2f
                val cy = (minY + maxY) / 2f
                val rx = boxW / 2f
                val ry = boxH / 2f
                var ellipseErr = 0f
                for (pt in points) {
                    val nx = (pt.x - cx) / rx
                    val ny = (pt.y - cy) / ry
                    ellipseErr += abs(nx * nx + ny * ny - 1f)
                }
                ellipseErr /= points.size
                if (ellipseErr < 0.28f) ShapeKind.ELLIPSE else ShapeKind.RECTANGLE
            } else {
                ShapeKind.LINE
            }
        }

        return when (resolvedKind) {
            ShapeKind.LINE, ShapeKind.AUTO -> listOf(first, last)
            ShapeKind.RECTANGLE -> listOf(
                StrokePoint(minX, minY, 0.5f, 0L),
                StrokePoint(maxX, minY, 0.5f, 1L),
                StrokePoint(maxX, maxY, 0.5f, 2L),
                StrokePoint(minX, maxY, 0.5f, 3L),
                StrokePoint(minX, minY, 0.5f, 4L)
            )
            ShapeKind.TRIANGLE -> listOf(
                StrokePoint((minX + maxX) / 2f, minY, 0.5f, 0L),
                StrokePoint(maxX, maxY, 0.5f, 1L),
                StrokePoint(minX, maxY, 0.5f, 2L),
                StrokePoint((minX + maxX) / 2f, minY, 0.5f, 3L)
            )
            ShapeKind.ELLIPSE -> {
                val cx = (minX + maxX) / 2f
                val cy = (minY + maxY) / 2f
                val rx = boxW / 2f
                val ry = boxH / 2f
                val steps = 48
                (0..steps).map { i ->
                    val theta = 2.0 * PI * i / steps
                    StrokePoint(
                        x = cx + (rx * cos(theta)).toFloat(),
                        y = cy + (ry * sin(theta)).toFloat(),
                        pressure = 0.5f,
                        timeOffsetMs = i.toLong()
                    )
                }
            }
            ShapeKind.ARROW -> {
                val angle = atan2((last.y - first.y).toDouble(), (last.x - first.x).toDouble())
                val headLen = (directDist * 0.22f).coerceIn(14f, 42f)
                val wingAngle = PI / 6.0
                val leftX = last.x - (headLen * cos(angle - wingAngle)).toFloat()
                val leftY = last.y - (headLen * sin(angle - wingAngle)).toFloat()
                val rightX = last.x - (headLen * cos(angle + wingAngle)).toFloat()
                val rightY = last.y - (headLen * sin(angle + wingAngle)).toFloat()
                listOf(
                    first,
                    last,
                    StrokePoint(leftX, leftY, 0.5f, 2L),
                    last,
                    StrokePoint(rightX, rightY, 0.5f, 4L)
                )
            }
        }
    }
}
