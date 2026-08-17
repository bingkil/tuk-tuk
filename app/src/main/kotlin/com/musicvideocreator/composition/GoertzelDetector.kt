package com.musicvideocreator.composition

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/** Streaming single-bin Goertzel tone-energy detector; feed all samples, then read [magnitude]. */
class GoertzelDetector(targetHz: Double, sampleRateHz: Int) {
    private val coeff = 2.0 * cos(2.0 * PI * targetHz / sampleRateHz)
    private var sPrev = 0.0
    private var sPrev2 = 0.0
    private var sampleCount = 0L

    fun process(sample: Short) {
        val s = sample.toDouble() + coeff * sPrev - sPrev2
        sPrev2 = sPrev
        sPrev = s
        sampleCount++
    }

    /** Magnitude normalized by sample count, comparable across detectors run on the same signal. */
    val magnitude: Double
        get() {
            if (sampleCount == 0L) return 0.0
            val power = sPrev2 * sPrev2 + sPrev * sPrev - coeff * sPrev * sPrev2
            return sqrt(power) / sampleCount
        }
}
