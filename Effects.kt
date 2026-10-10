package com.zoromotion.core.effects

import com.zoromotion.core.animation.*
import kotlinx.serialization.Serializable
import kotlin.math.*

enum class EffectType { GLIDE, IMPACT, RIFT, VELOCITY, FLASH, RGB_SPLIT, GLOW, MOTION_BLUR }

data class ParamDef(val key: String, val label: String, val default: Float, val min: Float, val max: Float)

/** Single source of truth for every effect's controls. UI sliders are generated from this, so each control maps to evaluate(). */
object EffectCatalog {
    private val easing = ParamDef("easing", "Easing (0 lin,1 in,2 out,3 in-out)", 3f, 0f, 3f)
    fun title(t: EffectType) = "Zoro " + when (t) {
        EffectType.GLIDE -> "Glide"; EffectType.IMPACT -> "Impact"; EffectType.RIFT -> "Rift"
        EffectType.VELOCITY -> "Velocity"; EffectType.FLASH -> "Flash"; EffectType.RGB_SPLIT -> "RGB Split"
        EffectType.GLOW -> "Glow"; EffectType.MOTION_BLUR -> "Motion Blur"
    }
    fun defs(t: EffectType): List<ParamDef> = when (t) {
        EffectType.GLIDE -> listOf(ParamDef("zoom", "Zoom amount", 1.3f, 0.5f, 3f), ParamDef("moveX", "Move X", 0.05f, -0.5f, 0.5f),
            ParamDef("moveY", "Move Y", 0f, -0.5f, 0.5f), easing, ParamDef("motionBlur", "Motion blur", 0.3f, 0f, 1f))
        EffectType.IMPACT -> listOf(ParamDef("zoom", "Impact zoom", 0.25f, 0f, 1f), ParamDef("shake", "Shake strength", 0.03f, 0f, 0.2f),
            ParamDef("shakeAngle", "Shake direction (deg)", 0f, 0f, 360f), ParamDef("flash", "Flash", 0.4f, 0f, 1f), ParamDef("intensity", "Intensity", 1f, 0f, 2f))
        EffectType.RIFT -> listOf(ParamDef("rotation", "Rotation (deg)", 25f, -180f, 180f), ParamDef("zoom", "Zoom amount", 1.4f, 0.5f, 3f),
            ParamDef("moveAngle", "Move direction (deg)", 0f, 0f, 360f), ParamDef("moveDist", "Move distance", 0.1f, 0f, 0.5f),
            ParamDef("chroma", "Chromatic aberration (px)", 6f, 0f, 30f), easing)
        EffectType.VELOCITY -> listOf(ParamDef("speedStart", "Start speed", 0.3f, 0.1f, 4f), ParamDef("speedEnd", "End speed", 2f, 0.1f, 4f), easing)
        EffectType.FLASH -> listOf(ParamDef("color", "Colour (0 white,1 gold,2 red,3 cyan)", 0f, 0f, 3f), ParamDef("opacity", "Peak opacity", 0.9f, 0f, 1f),
            ParamDef("fadeIn", "Fade-in share", 0.1f, 0f, 1f), ParamDef("fadeOut", "Fade-out share", 0.6f, 0f, 1f))
        EffectType.RGB_SPLIT -> listOf(ParamDef("amount", "Separation (px)", 8f, 0f, 40f), ParamDef("angle", "Direction (deg)", 0f, 0f, 360f),
            ParamDef("animate", "Animate (0/1 pulse)", 0f, 0f, 1f))
        EffectType.GLOW -> listOf(ParamDef("strength", "Strength", 0.6f, 0f, 2f), ParamDef("radius", "Radius (px)", 12f, 1f, 60f),
            ParamDef("threshold", "Threshold", 0.6f, 0f, 1f))
        EffectType.MOTION_BLUR -> listOf(ParamDef("strength", "Strength (px)", 14f, 0f, 60f), ParamDef("angle", "Direction (deg)", 0f, 0f, 360f),
            ParamDef("timeBased", "Time-based (0/1)", 1f, 0f, 1f))
    }
    fun defaults(t: EffectType): Map<String, AnimatedValue> = defs(t).associate { it.key to AnimatedValue(it.default) }
}

@Serializable
data class EffectInstance(
    val id: String,
    val type: EffectType,
    val params: Map<String, AnimatedValue> = EffectCatalog.defaults(type),
    /** Clip-local start and length. */
    val startMs: Long = 0,
    val durationMs: Long = 500,
    val enabled: Boolean = true,
) {
    fun param(key: String, localMs: Long): Float =
        params[key]?.valueAt(localMs) ?: EffectCatalog.defs(type).first { it.key == key }.default
    fun withParam(key: String, v: Float, localMs: Long) =
        copy(params = params + (key to (params[key] ?: AnimatedValue(v)).withValueAt(localMs, v)))
}

@Serializable
data class EffectPreset(val id: String, val name: String, val effect: EffectInstance)

/** Everything the renderer needs for one clip at one instant. Identity = no visual change. */
data class EffectOutput(
    val zoom: Float = 1f, val dx: Float = 0f, val dy: Float = 0f, val rotationDeg: Float = 0f,
    val flashColor: Long = 0xFFFFFFFF, val flashAlpha: Float = 0f,
    val rgbSplitPx: Float = 0f, val rgbSplitAngle: Float = 0f,
    val blurPx: Float = 0f, val blurAngle: Float = 0f,
    val glowStrength: Float = 0f, val glowRadius: Float = 0f, val glowThreshold: Float = 0f,
    val speed: Float = 1f,
) {
    /** Combine two outputs (multiply zooms, add offsets, max for intensities). */
    operator fun plus(o: EffectOutput) = EffectOutput(
        zoom * o.zoom, dx + o.dx, dy + o.dy, rotationDeg + o.rotationDeg,
        if (o.flashAlpha > flashAlpha) o.flashColor else flashColor, max(flashAlpha, o.flashAlpha),
        max(rgbSplitPx, o.rgbSplitPx), if (o.rgbSplitPx > rgbSplitPx) o.rgbSplitAngle else rgbSplitAngle,
        max(blurPx, o.blurPx), if (o.blurPx > blurPx) o.blurAngle else blurAngle,
        max(glowStrength, o.glowStrength), max(glowRadius, o.glowRadius), max(glowThreshold, o.glowThreshold), speed * o.speed)
}

object EffectEngine {
    private val flashColors = longArrayOf(0xFFFFFFFF, 0xFFD4AF37, 0xFFFF2A2A, 0xFF22E0FF)
    private fun easingOf(v: Float) = when (v.roundToInt()) { 1 -> Interpolation.EASE_IN; 2 -> Interpolation.EASE_OUT; 3 -> Interpolation.EASE_IN_OUT; else -> Interpolation.LINEAR }

    /** Deterministic pseudo-noise in [-1,1] so preview, scrubbing and export agree. */
    fun noise(seed: Int): Float {
        var h = seed * 374761393 + 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)) and 0xFFFF) / 32767.5f - 1f
    }

    fun evaluate(fx: EffectInstance, clipLocalMs: Long, fps: Int = 30): EffectOutput {
        if (!fx.enabled || fx.durationMs <= 0) return EffectOutput()
        val rel = clipLocalMs - fx.startMs
        if (rel < 0 || rel > fx.durationMs) return EffectOutput()
        val p = rel.toFloat() / fx.durationMs
        fun q(k: String) = fx.param(k, clipLocalMs)
        return when (fx.type) {
            EffectType.GLIDE -> {
                val ease = easingOf(q("easing"))
                val e = Easing.apply(ease, p)
                val speed = (Easing.apply(ease, min(1f, p + 0.02f)) - Easing.apply(ease, max(0f, p - 0.02f))) / 0.04f
                EffectOutput(zoom = 1f + (q("zoom") - 1f) * e, dx = q("moveX") * e, dy = q("moveY") * e,
                    blurPx = q("motionBlur") * 30f * speed.coerceIn(0f, 3f) / 3f * (abs(q("moveX")) + abs(q("moveY")) + abs(q("zoom") - 1f)).coerceAtMost(1f),
                    blurAngle = Math.toDegrees(atan2(q("moveY").toDouble(), q("moveX").toDouble())).toFloat())
            }
            EffectType.IMPACT -> {
                val attack = 0.12f
                val env = (if (p < attack) p / attack else (1f - (p - attack) / (1f - attack)).pow(2)) * q("intensity")
                val frame = (clipLocalMs * fps / 1000).toInt()
                val ang = Math.toRadians(q("shakeAngle").toDouble())
                val n = noise(frame)
                EffectOutput(zoom = 1f + q("zoom") * env, dx = (cos(ang) * n * q("shake") * env).toFloat(),
                    dy = (sin(ang) * n * q("shake") * env).toFloat(), flashColor = 0xFFFFFFFF, flashAlpha = (q("flash") * env).coerceIn(0f, 1f))
            }
            EffectType.RIFT -> {
                val e = Easing.apply(easingOf(q("easing")), p)
                val a = Math.toRadians(q("moveAngle").toDouble())
                EffectOutput(zoom = 1f + (q("zoom") - 1f) * e, rotationDeg = q("rotation") * e,
                    dx = (cos(a) * q("moveDist") * e).toFloat(), dy = (sin(a) * q("moveDist") * e).toFloat(),
                    rgbSplitPx = q("chroma") * sin(PI * p).toFloat(), rgbSplitAngle = q("moveAngle"))
            }
            EffectType.VELOCITY -> EffectOutput(speed = velocitySpeed(fx, rel))
            EffectType.FLASH -> {
                val fi = q("fadeIn").coerceIn(0f, 1f); val fo = q("fadeOut").coerceIn(0f, 1f)
                val a = when {
                    fi > 0f && p < fi -> p / fi
                    fo > 0f && p > 1f - fo -> (1f - p) / fo
                    else -> 1f
                }
                EffectOutput(flashColor = flashColors[q("color").roundToInt().coerceIn(0, 3)], flashAlpha = (q("opacity") * a).coerceIn(0f, 1f))
            }
            EffectType.RGB_SPLIT -> EffectOutput(
                rgbSplitPx = q("amount") * (if (q("animate") >= 0.5f) sin(PI * p).toFloat() else 1f), rgbSplitAngle = q("angle"))
            EffectType.GLOW -> EffectOutput(glowStrength = q("strength"), glowRadius = q("radius"), glowThreshold = q("threshold"))
            EffectType.MOTION_BLUR -> EffectOutput(
                blurPx = q("strength") * (if (q("timeBased") >= 0.5f) sin(PI * p).toFloat() else 1f), blurAngle = q("angle"))
        }
    }

    fun evaluateAll(list: List<EffectInstance>, clipLocalMs: Long, fps: Int = 30) =
        list.fold(EffectOutput()) { acc, f -> acc + evaluate(f, clipLocalMs, fps) }

    /** Speed multiplier at effect-relative time (ms). Ramps start->end through the chosen easing. */
    fun velocitySpeed(fx: EffectInstance, relMs: Long): Float {
        val p = (relMs.toFloat() / fx.durationMs).coerceIn(0f, 1f)
        val e = Easing.apply(easingOf(fx.param("easing", fx.startMs + relMs)), p)
        val s0 = fx.param("speedStart", fx.startMs); val s1 = fx.param("speedEnd", fx.startMs)
        return s0 + (s1 - s0) * e
    }

    /**
     * Source-media time for a clip-local time under a velocity effect: integrates speed frame-by-frame (1/fps steps),
     * so remapped timestamps are frame-accurate and identical for preview and export.
     */
    fun sourceTimeMs(fx: EffectInstance?, clipLocalMs: Long, fps: Int = 30): Double {
        if (fx == null || !fx.enabled) return clipLocalMs.toDouble()
        val step = 1000.0 / fps
        var src = 0.0; var t = 0.0
        while (t + step <= clipLocalMs) {
            val inFx = t >= fx.startMs && t <= fx.startMs + fx.durationMs
            src += step * (if (inFx) velocitySpeed(fx, (t - fx.startMs).toLong()) else 1f)
            t += step
        }
        val tail = clipLocalMs - t
        val inFx = t >= fx.startMs && t <= fx.startMs + fx.durationMs
        return src + tail * (if (inFx) velocitySpeed(fx, (t - fx.startMs).toLong()) else 1f)
    }

    /** Fallback ladder for devices that can't sustain blur/glow: samples per pixel by performance tier. */
    fun blurSamples(tier: Int) = when (tier) { 0 -> 0; 1 -> 4; 2 -> 8; else -> 16 }
}
