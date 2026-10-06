package com.example.videoshield

import kotlin.math.ln
import kotlin.math.pow

object EqPolicy {
    val frequencies = intArrayOf(60, 230, 910, 3600, 14000)
    val presets = linkedMapOf(
        "Flat" to intArrayOf(0, 0, 0, 0, 0),
        "Bass" to intArrayOf(6, 4, 0, -2, -3),
        "Treble" to intArrayOf(-2, -1, 0, 4, 6),
        "Vocal" to intArrayOf(-3, -1, 4, 3, -2),
        "Rock" to intArrayOf(4, 2, -1, 3, 4)
    )

    fun gainAt(hz: Double, gains: IntArray): Double {
        require(gains.size == frequencies.size)
        val safe = gains.map { it.coerceIn(-12, 12).toDouble() }
        if (hz <= frequencies.first()) return safe.first()
        if (hz >= frequencies.last()) return safe.last()
        val upper = frequencies.indexOfFirst { it >= hz }
        val lower = upper - 1
        val fraction = ln(hz / frequencies[lower]) / ln(frequencies[upper].toDouble() / frequencies[lower])
        return safe[lower] + fraction * (safe[upper] - safe[lower])
    }

    fun headroom(gains: IntArray): Float =
        10.0.pow(-((gains.maxOrNull() ?: 0).coerceIn(0, 12)) / 20.0).toFloat()
}
