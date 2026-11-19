package com.johnlrt.freenotes.engine

import com.johnlrt.freenotes.data.StrokePoint
import kotlin.math.hypot

/**
 * Lightweight damped velocity/acceleration stylus look-ahead predictor
 * inspired by TurboTouch (`optparam1130_best.cfg` / `libforecast.so`).
 *
 * Produces 1–2 speculative preview points ahead of the live pen tip so fast strokes
 * feel instantaneous on 60Hz/120Hz Android tablets, without mutating committed points.
 */
class StylusPredictor(
    private val maxPredictMs: Float = 16f,
    private val damping: Float = 0.65f
) {
    private val recent = ArrayDeque<StrokePoint>(6)

    fun reset() {
        recent.clear()
    }

    fun record(point: StrokePoint) {
        if (recent.size >= 5) {
            recent.removeFirst()
        }
        recent.addLast(point)
    }

    /**
     * Returns up to 2 predicted points for low-latency rendering.
     */
    fun predict(): List<StrokePoint> {
        if (recent.size < 3) return emptyList()

        val p0 = recent[recent.size - 3]
        val p1 = recent[recent.size - 2]
        val p2 = recent[recent.size - 1]

        val dt1 = (p1.timeOffsetMs - p0.timeOffsetMs).coerceAtLeast(1L).toFloat()
        val dt2 = (p2.timeOffsetMs - p1.timeOffsetMs).coerceAtLeast(1L).toFloat()

        val vx1 = (p1.x - p0.x) / dt1
        val vy1 = (p1.y - p0.y) / dt1
        val vx2 = (p2.x - p1.x) / dt2
        val vy2 = (p2.y - p1.y) / dt2

        val speed = hypot(vx2.toDouble(), vy2.toDouble()).toFloat()
        // Skip prediction when pen is nearly stationary to prevent tip wobble
        if (speed < 0.08f) return emptyList()

        val ax = ((vx2 - vx1) / dt2) * 0.25f
        val ay = ((vy2 - vy1) / dt2) * 0.25f

        val halfStep = maxPredictMs * 0.5f
        val fullStep = maxPredictMs

        val pred1 = StrokePoint(
            x = p2.x + (vx2 * halfStep + 0.5f * ax * halfStep * halfStep) * damping,
            y = p2.y + (vy2 * halfStep + 0.5f * ay * halfStep * halfStep) * damping,
            pressure = p2.pressure,
            timeOffsetMs = p2.timeOffsetMs + halfStep.toLong()
        )
        val pred2 = StrokePoint(
            x = p2.x + (vx2 * fullStep + 0.5f * ax * fullStep * fullStep) * (damping * 0.85f),
            y = p2.y + (vy2 * fullStep + 0.5f * ay * fullStep * fullStep) * (damping * 0.85f),
            pressure = p2.pressure,
            timeOffsetMs = p2.timeOffsetMs + fullStep.toLong()
        )

        return listOf(pred1, pred2)
    }
}
