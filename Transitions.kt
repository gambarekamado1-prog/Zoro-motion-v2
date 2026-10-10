package com.zoromotion.core.effects

import com.zoromotion.core.animation.*
import kotlinx.serialization.Serializable
import kotlin.math.*

enum class TransitionType { ZOOM, SLIDE, SPIN, SHAKE_CUT, FLASH_CUT }

@Serializable
data class TransitionParams(
    val type: TransitionType = TransitionType.ZOOM,
    val durationMs: Long = 400,
    val directionDeg: Float = 0f,
    val intensity: Float = 1f,
    val zoomAmount: Float = 0.8f,
    val rotationDeg: Float = 0f,
    val shakeStrength: Float = 0f,
    val blurStrength: Float = 0.4f,
    val flashStrength: Float = 0.5f,
    val easing: Interpolation = Interpolation.EASE_IN_OUT,
    val bezier: Bezier = Bezier(),
    /** Portion of the duration (0..1) where the motion starts/ends. */
    val startAt: Float = 0f,
    val endAt: Float = 1f,
)

@Serializable data class TransitionPreset(val id: String, val name: String, val params: TransitionParams)
/** A transition placed between two clips; centred on the cut where `from` ends and `to` starts. */
@Serializable data class TransitionInstance(val id: String, val fromClipId: String, val toClipId: String, val params: TransitionParams)

data class LayerFx(val zoom: Float = 1f, val dx: Float = 0f, val dy: Float = 0f, val rotationDeg: Float = 0f, val opacity: Float = 1f, val blurPx: Float = 0f)
data class TransitionFrame(val outgoing: LayerFx, val incoming: LayerFx, val flashAlpha: Float)

object TransitionEngine {
    /** progress in [0,1] across the transition. Outgoing clip leaves in the first half, incoming arrives in the second. */
    fun evaluate(tp: TransitionParams, progress: Float, fps: Int = 30, frameSeed: Int = 0): TransitionFrame {
        val span = (tp.endAt - tp.startAt).coerceAtLeast(0.01f)
        val local = ((progress - tp.startAt) / span).coerceIn(0f, 1f)
        val e = Easing.apply(tp.easing, local, tp.bezier)
        val dir = Math.toRadians(tp.directionDeg.toDouble())
        val dirX = cos(dir).toFloat(); val dirY = sin(dir).toFloat()
        val bell = sin(PI * e).toFloat()
        val shake = tp.shakeStrength * bell
        val sx = EffectEngine.noise(frameSeed * 2) * shake
        val sy = EffectEngine.noise(frameSeed * 2 + 1) * shake
        val blur = tp.blurStrength * bell * 40f
        val rot = tp.rotationDeg * e * tp.intensity
        val flash = (tp.flashStrength * bell).coerceIn(0f, 1f).let { if (tp.type == TransitionType.FLASH_CUT) min(1f, it * 1.6f) else it }
        val firstHalf = e < 0.5f
        val out = LayerFx(
            zoom = 1f + tp.zoomAmount * e * tp.intensity,
            dx = (if (tp.type == TransitionType.SLIDE) dirX * e * tp.intensity else 0f) + sx,
            dy = (if (tp.type == TransitionType.SLIDE) dirY * e * tp.intensity else 0f) + sy,
            rotationDeg = if (tp.type == TransitionType.SPIN) rot + 180f * e * 0f else rot,
            opacity = if (firstHalf) 1f else 0f, blurPx = blur)
        val inc = LayerFx(
            zoom = 1f + tp.zoomAmount * (1f - e) * tp.intensity,
            dx = (if (tp.type == TransitionType.SLIDE) -dirX * (1f - e) * tp.intensity else 0f) + sx,
            dy = (if (tp.type == TransitionType.SLIDE) -dirY * (1f - e) * tp.intensity else 0f) + sy,
            rotationDeg = -tp.rotationDeg * (1f - e) * tp.intensity,
            opacity = if (firstHalf) 0f else 1f, blurPx = blur)
        return TransitionFrame(out, inc, flash)
    }
}
