package com.zoromotion.core.animation

import kotlinx.serialization.Serializable

/** Keyframe time is CLIP-LOCAL milliseconds (0 = clip start). The interpolation applies to the segment that starts here. */
@Serializable
data class Keyframe(
    val timeMs: Long,
    val value: Float,
    val interpolation: Interpolation = Interpolation.LINEAR,
    val bezier: Bezier = Bezier(),
)

/** A property that is either constant (no keyframes) or evaluated from keyframes at a time. */
@Serializable
data class AnimatedValue(val base: Float, val keyframes: List<Keyframe> = emptyList()) {
    val isAnimated get() = keyframes.isNotEmpty()

    fun valueAt(localMs: Long): Float {
        if (keyframes.isEmpty()) return base
        val first = keyframes.first()
        if (localMs <= first.timeMs) return first.value
        val last = keyframes.last()
        if (localMs >= last.timeMs) return last.value
        val i = keyframes.indexOfLast { it.timeMs <= localMs }
        val a = keyframes[i]; val b = keyframes[i + 1]
        val p = (localMs - a.timeMs).toFloat() / (b.timeMs - a.timeMs).toFloat()
        val e = Easing.apply(a.interpolation, p, a.bezier)
        return a.value + (b.value - a.value) * e
    }

    fun hasKeyframeAt(localMs: Long, toleranceMs: Long = 0) = keyframes.any { kotlin.math.abs(it.timeMs - localMs) <= toleranceMs }

    fun withKeyframe(k: Keyframe) =
        copy(keyframes = (keyframes.filter { it.timeMs != k.timeMs } + k).sortedBy { it.timeMs })

    fun withoutKeyframeAt(localMs: Long) = copy(keyframes = keyframes.filter { it.timeMs != localMs })

    /** Sets the value at a time: edits base when static, otherwise creates/updates a keyframe (keeping its easing). */
    fun withValueAt(localMs: Long, v: Float): AnimatedValue {
        if (!isAnimated) return copy(base = v)
        val existing = keyframes.firstOrNull { it.timeMs == localMs }
        return withKeyframe((existing ?: Keyframe(localMs, v)).copy(value = v))
    }

    fun withInterpolationAt(localMs: Long, kind: Interpolation, bezier: Bezier? = null) =
        copy(keyframes = keyframes.map {
            if (it.timeMs == localMs) it.copy(interpolation = kind, bezier = bezier ?: it.bezier) else it
        })

    /** For split/trim: shifts keyframe times; if some fall before 0, bakes the value at t=0 so motion stays continuous. */
    fun shifted(deltaMs: Long): AnimatedValue {
        if (keyframes.isEmpty()) return this
        val moved = keyframes.map { it.copy(timeMs = it.timeMs + deltaMs) }
        val kept = moved.filter { it.timeMs >= 0 }
        if (kept.size == moved.size) return copy(keyframes = kept)
        val at0 = valueAt(-deltaMs)
        val bakedFirst = if (kept.firstOrNull()?.timeMs == 0L) emptyList() else listOf(Keyframe(0, at0))
        return copy(keyframes = bakedFirst + kept)
    }
}
