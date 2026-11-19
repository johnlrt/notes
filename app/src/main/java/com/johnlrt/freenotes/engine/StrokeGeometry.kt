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
 * Closed polygon outline (left edge forward + right edge backward) for smooth
 * hardware-accelerated filled ink rendering without segment-joint artifacts.
 */
data class RibbonOutline(
    val leftEdge: List<Pair<Float, Float>>,
    val rightEdge: List<Pair<Float, Float>>,
    val startCapCenter: Pair<Float, Float>,
    val startCapRadius: Float,
    val endCapCenter: Pair<Float, Float>,
    val endCapRadius: Float
)

/**
 * Pure-Kotlin stroke geometry, polygonal ink ribbon, ruler snapping, and shape recognition engine
 * inspired by Freenotes' `Clipper2NativeLib`.
 */
object StrokeGeometry {

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
                val pressureFactor = (1f - sens) + sens * (0.42f + 1.12f * p)
                val velocityFactor = if (prev != null) {
                    val dt = (curr.timeOffsetMs - prev.timeOffsetMs).coerceAtLeast(1L)
                    val dist = hypot((curr.x - prev.x).toDouble(), (curr.y - prev.y).toDouble()).toFloat()
                    val speed = dist / dt
                    (1.12f - (speed * 0.15f)).coerceIn(0.65f, 1.14f)
                } else 1f
                val taper = when {
                    totalCount > 6 && index < 2 -> 0.65f + 0.17f * index
                    totalCount > 6 && index > totalCount - 3 -> 0.65f + 0.17f * (totalCount - 1 - index)
                    else -> 1f
                }
                (base * pressureFactor * velocityFactor * taper).coerceIn(base * 0.32f, base * 1.9f)
            }

            ToolType.ART_PEN -> {
                val angleRad = stroke.nibAngleDeg * PI / 180.0
                val strokeAngle = if (prev != null) {
                    atan2((curr.y - prev.y).toDouble(), (curr.x - prev.x).toDouble())
                } else 0.0
                val projection = abs(sin(strokeAngle - angleRad)).toFloat()
                val nibFactor = 0.32f + 1.32f * projection
                val pressureFactor = (1f - sens * 0.5f) + (sens * 0.5f) * p
                (base * nibFactor * pressureFactor).coerceIn(base * 0.28f, base * 2.05f)
            }

            ToolType.PENCIL -> {
                val pressureFactor = (1f - sens * 0.4f) + (sens * 0.4f) * (0.65f + 0.55f * p)
                (base * pressureFactor).coerceIn(base * 0.5f, base * 1.35f)
            }

            else -> base
        }
    }

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
     * Subdivides raw points with quadratic midpoint smoothing and computes a closed
     * variable-width ribbon outline for buttery-smooth calligraphic ink rendering.
     */
    fun buildRibbonOutline(stroke: Stroke): RibbonOutline? {
        val raw = stroke.points
        if (raw.size < 2) return null

        // Light midpoint smoothing pass so corners are round and organic
        val pts = ArrayList<StrokePoint>(raw.size)
        pts.add(raw.first())
        for (i in 1 until raw.lastIndex) {
            val prev = raw[i - 1]
            val curr = raw[i]
            val next = raw[i + 1]
            pts.add(
                curr.copy(
                    x = prev.x * 0.2f + curr.x * 0.6f + next.x * 0.2f,
                    y = prev.y * 0.2f + curr.y * 0.6f + next.y * 0.2f
                )
            )
        }
        pts.add(raw.last())

        val left = ArrayList<Pair<Float, Float>>(pts.size)
        val right = ArrayList<Pair<Float, Float>>(pts.size)

        var prevWidth = computeWidth(stroke, null, pts[0], 0, pts.size)
        val widths = FloatArray(pts.size)
        widths[0] = prevWidth
        for (i in 1 until pts.size) {
            val target = computeWidth(stroke, pts[i - 1], pts[i], i, pts.size)
            prevWidth = prevWidth * 0.55f + target * 0.45f
            widths[i] = prevWidth
        }

        var lastNx = 0f
        var lastNy = 1f

        for (i in pts.indices) {
            val pPrev = pts[(i - 1).coerceAtLeast(0)]
            val pNext = pts[(i + 1).coerceAtMost(pts.lastIndex)]
            val dx = pNext.x - pPrev.x
            val dy = pNext.y - pPrev.y
            val len = hypot(dx.toDouble(), dy.toDouble()).toFloat()
            val (nx, ny) = if (len > 1e-4f) {
                (-dy / len) to (dx / len)
            } else {
                lastNx to lastNy
            }
            lastNx = nx
            lastNy = ny

            val halfW = widths[i] * 0.5f
            left.add((pts[i].x + nx * halfW) to (pts[i].y + ny * halfW))
            right.add((pts[i].x - nx * halfW) to (pts[i].y - ny * halfW))
        }

        return RibbonOutline(
            leftEdge = left,
            rightEdge = right,
            startCapCenter = pts.first().x to pts.first().y,
            startCapRadius = widths.first() * 0.5f,
            endCapCenter = pts.last().x to pts.last().y,
            endCapRadius = widths.last() * 0.5f
        )
    }

    fun buildSegments(stroke: Stroke): List<RibbonSegment> {
        val pts = stroke.points
        if (pts.size < 2) return emptyList()

        val result = ArrayList<RibbonSegment>(pts.size - 1)
        var prevWidth = computeWidth(stroke, null, pts[0], 0, pts.size)

        for (i in 1 until pts.size) {
            val p0 = pts[i - 1]
            val p1 = pts[i]
            val targetWidth = computeWidth(stroke, p0, p1, i, pts.size)
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
     * If a stylus point is within [snapThreshold] of the top or bottom edge of the on-canvas ruler
     * centered at `(rulerCx, rulerCy)` rotated by [rulerAngleDeg] with [rulerHalfThickness],
     * projects the point cleanly onto that ruler edge!
     */
    fun snapPointToRuler(
        px: Float,
        py: Float,
        rulerCx: Float,
        rulerCy: Float,
        rulerAngleDeg: Float,
        rulerHalfThickness: Float = 38f,
        snapThreshold: Float = 36f
    ): Pair<Float, Float> {
        val rad = rulerAngleDeg * PI / 180.0
        val cosA = cos(rad).toFloat()
        val sinA = sin(rad).toFloat()

        // Transform point into ruler-local coordinates
        val dx = px - rulerCx
        val dy = py - rulerCy
        val localX = dx * cosA + dy * sinA
        val localY = -dx * sinA + dy * cosA

        val distTop = abs(localY - (-rulerHalfThickness))
        val distBottom = abs(localY - rulerHalfThickness)

        val snappedLocalY = when {
            distTop <= snapThreshold && distTop <= distBottom -> -rulerHalfThickness - 2f
            distBottom <= snapThreshold -> rulerHalfThickness + 2f
            else -> return px to py
        }

        val worldX = rulerCx + localX * cosA - snappedLocalY * sinA
        val worldY = rulerCy + localX * sinA + snappedLocalY * cosA
        return worldX to worldY
    }

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
