package com.johnlrt.freenotes.engine

import kotlin.math.PI
import kotlin.math.abs

/**
 * 1€ (One-Euro) low-pass filter for jitter-free stylus input smoothing.
 *
 * Default parameters match the values observed in Freenotes 3.29.0's `optparam1130_best.cfg`:
 *   - `one-euro-mincutoff: 16`
 *   - `one-euro-beta: 0.01`
 *   - `one-euro-freq: 120`
 *
 * A user-facing `stability` factor in [0f, 1f] maps smoothly to lower `minCutoff`
 * values for steadier handwriting without introducing sluggish lag during fast strokes.
 */
class OneEuroFilter1D(
    private var minCutoff: Float = 16.0f,
    private var beta: Float = 0.01f,
    private val dCutoff: Float = 1.0f
) {
    private var lastValue: Float? = null
    private var lastDerivative: Float = 0f
    private var lastTimeSec: Double? = null

    fun reset() {
        lastValue = null
        lastDerivative = 0f
        lastTimeSec = null
    }

    fun configure(minCutoff: Float, beta: Float) {
        this.minCutoff = minCutoff.coerceIn(0.5f, 40f)
        this.beta = beta.coerceIn(0.0001f, 1.0f)
    }

    fun filter(value: Float, timestampSec: Double): Float {
        val prevVal = lastValue
        val prevTime = lastTimeSec
        if (prevVal == null || prevTime == null) {
            lastValue = value
            lastTimeSec = timestampSec
            lastDerivative = 0f
            return value
        }

        val dt = (timestampSec - prevTime).coerceAtLeast(1e-4)
        val freq = (1.0 / dt).toFloat()

        val rawDerivative = (value - prevVal) * freq
        val alphaD = smoothingAlpha(dCutoff, freq)
        val filteredDerivative = alphaD * rawDerivative + (1f - alphaD) * lastDerivative

        val dynamicCutoff = minCutoff + beta * abs(filteredDerivative)
        val alpha = smoothingAlpha(dynamicCutoff, freq)
        val filtered = alpha * value + (1f - alpha) * prevVal

        lastValue = filtered
        lastDerivative = filteredDerivative
        lastTimeSec = timestampSec
        return filtered
    }

    private fun smoothingAlpha(cutoff: Float, freq: Float): Float {
        val tau = 1.0 / (2.0 * PI * cutoff)
        val te = 1.0 / freq
        return (1.0 / (1.0 + tau / te)).toFloat().coerceIn(0f, 1f)
    }
}

/**
 * 2D vector One-Euro Filter for stylus `(x, y)` coordinates.
 */
class OneEuroFilter2D(
    minCutoff: Float = 16.0f,
    beta: Float = 0.01f
) {
    private val filterX = OneEuroFilter1D(minCutoff = minCutoff, beta = beta)
    private val filterY = OneEuroFilter1D(minCutoff = minCutoff, beta = beta)

    fun reset() {
        filterX.reset()
        filterY.reset()
    }

    /**
     * Maps a UI stability slider [0f..1f] to One-Euro cutoff parameters.
     * - 0f: raw / minimal smoothing (minCutoff = 32f)
     * - 0.5f: Freenotes default (minCutoff = 16f, beta = 0.01f)
     * - 1.0f: strong calligraphic stabilization (minCutoff = 3.5f, beta = 0.004f)
     */
    fun setStability(stability: Float) {
        val s = stability.coerceIn(0f, 1f)
        val cutoff = 32f * (1f - s) + 3.5f * s
        val beta = 0.018f * (1f - s) + 0.004f * s
        filterX.configure(cutoff, beta)
        filterY.configure(cutoff, beta)
    }

    fun filter(x: Float, y: Float, timestampMs: Long): Pair<Float, Float> {
        val tSec = timestampMs / 1000.0
        return filterX.filter(x, tSec) to filterY.filter(y, tSec)
    }
}
