package com.johnlrt.freenotes.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class OneEuroFilterTest {

    @Test
    fun firstSamplePassesThroughUnchanged() {
        val filter = OneEuroFilter2D(minCutoff = 16.0f, beta = 0.01f)
        val (x, y) = filter.filter(120f, 240f, 1000L)
        assertEquals(120f, x, 1e-4f)
        assertEquals(240f, y, 1e-4f)
    }

    @Test
    fun highStabilityDampensSmallJitter() {
        val filter = OneEuroFilter2D()
        filter.setStability(0.9f)

        val jitter = listOf(100f, 102f, 99f, 101.5f, 98.5f, 100.5f)
        val outputs = jitter.mapIndexed { idx, v ->
            filter.filter(v, 50f, 1000L + idx * 8L).first
        }

        val inputSpread = jitter.max() - jitter.min()
        val outputSpread = outputs.drop(1).max() - outputs.drop(1).min()
        assertTrue("Expected smoothed spread ($outputSpread) < raw spread ($inputSpread)", outputSpread < inputSpread)
    }

    @Test
    fun fastMotionTracksWithLowLag() {
        val filter = OneEuroFilter2D(minCutoff = 16.0f, beta = 0.01f)
        var lastX = 0f
        for (i in 0..20) {
            lastX = filter.filter(i * 25f, 0f, i * 8L).first
        }
        assertTrue("Expected fast stroke to stay close to target 500f, got $lastX", abs(lastX - 500f) < 15f)
    }
}
