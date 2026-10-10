package com.zoromotion.core.animation

import kotlinx.serialization.Serializable

enum class Interpolation { LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT, BEZIER, HOLD }

/** CSS-style cubic bezier easing curve through (0,0) and (1,1). x1/x2 must stay in [0,1]. */
@Serializable
data class Bezier(val x1: Float = 0.42f, val y1: Float = 0f, val x2: Float = 0.58f, val y2: Float = 1f) {
    fun sanitized() = copy(x1 = x1.coerceIn(0f, 1f), x2 = x2.coerceIn(0f, 1f))

    /** Maps time progress x in [0,1] to eased progress (y may overshoot). Solved by bisection on x(t). */
    fun solve(x: Float): Float {
        if (x <= 0f) return 0f
        if (x >= 1f) return 1f
        val b = sanitized()
        var lo = 0.0; var hi = 1.0; var t = x.toDouble()
        repeat(40) {
            val cx = coord(t, b.x1.toDouble(), b.x2.toDouble())
            if (cx < x) lo = t else hi = t
            t = (lo + hi) / 2
        }
        return coord(t, b.y1.toDouble(), b.y2.toDouble()).toFloat()
    }

    private fun coord(t: Double, a: Double, b: Double): Double {
        val u = 1 - t
        return 3 * a * u * u * t + 3 * b * u * t * t + t * t * t
    }

    companion object {
        val EASE_IN = Bezier(0.42f, 0f, 1f, 1f)
        val EASE_OUT = Bezier(0f, 0f, 0.58f, 1f)
        val EASE_IN_OUT = Bezier(0.42f, 0f, 0.58f, 1f)
    }
}

object Easing {
    fun apply(kind: Interpolation, t: Float, custom: Bezier = Bezier()): Float {
        val x = t.coerceIn(0f, 1f)
        return when (kind) {
            Interpolation.LINEAR -> x
            Interpolation.EASE_IN -> Bezier.EASE_IN.solve(x)
            Interpolation.EASE_OUT -> Bezier.EASE_OUT.solve(x)
            Interpolation.EASE_IN_OUT -> Bezier.EASE_IN_OUT.solve(x)
            Interpolation.BEZIER -> custom.solve(x)
            Interpolation.HOLD -> if (x >= 1f) 1f else 0f
        }
    }
}
