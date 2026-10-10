package com.zoromotion.core.model

import com.zoromotion.core.animation.AnimatedValue
import com.zoromotion.core.effects.*
import kotlinx.serialization.Serializable

const val PROJECT_FORMAT_VERSION = 1

enum class TrackType { VIDEO, IMAGE, AUDIO, TEXT, SHAPE, ADJUSTMENT }

/**
 * Normalised units: position 0..1 of canvas (0.5,0.5 = centre), scale 1 = 100%, rotation degrees,
 * opacity 0..1, anchor 0..1 within the object's own box. Keyframe times are clip-local ms.
 */
@Serializable
data class Transform(
    val posX: AnimatedValue = AnimatedValue(0.5f), val posY: AnimatedValue = AnimatedValue(0.5f),
    val scaleX: AnimatedValue = AnimatedValue(1f), val scaleY: AnimatedValue = AnimatedValue(1f),
    val rotation: AnimatedValue = AnimatedValue(0f), val opacity: AnimatedValue = AnimatedValue(1f),
    val anchorX: AnimatedValue = AnimatedValue(0.5f), val anchorY: AnimatedValue = AnimatedValue(0.5f),
) {
    fun shifted(d: Long) = Transform(posX.shifted(d), posY.shifted(d), scaleX.shifted(d), scaleY.shifted(d),
        rotation.shifted(d), opacity.shifted(d), anchorX.shifted(d), anchorY.shifted(d))
}

/** Property keys used by UI and tests to address a Transform field. */
enum class Prop(val label: String, val min: Float, val max: Float) {
    POS_X("Position X", -0.5f, 1.5f), POS_Y("Position Y", -0.5f, 1.5f), SCALE_X("Scale X", 0.05f, 5f), SCALE_Y("Scale Y", 0.05f, 5f),
    ROTATION("Rotation", -360f, 360f), OPACITY("Opacity", 0f, 1f), ANCHOR_X("Anchor X", 0f, 1f), ANCHOR_Y("Anchor Y", 0f, 1f);

    fun get(t: Transform) = when (this) {
        POS_X -> t.posX; POS_Y -> t.posY; SCALE_X -> t.scaleX; SCALE_Y -> t.scaleY
        ROTATION -> t.rotation; OPACITY -> t.opacity; ANCHOR_X -> t.anchorX; ANCHOR_Y -> t.anchorY
    }
    fun set(t: Transform, v: AnimatedValue) = when (this) {
        POS_X -> t.copy(posX = v); POS_Y -> t.copy(posY = v); SCALE_X -> t.copy(scaleX = v); SCALE_Y -> t.copy(scaleY = v)
        ROTATION -> t.copy(rotation = v); OPACITY -> t.copy(opacity = v); ANCHOR_X -> t.copy(anchorX = v); ANCHOR_Y -> t.copy(anchorY = v)
    }
}

@Serializable
data class TextStyle(
    val text: String = "ZORO", val sizeN: Float = 0.12f, val colorArgb: Long = 0xFFFFFFFF,
    val align: String = "CENTER", val bold: Boolean = true, val fontFamily: String = "sans-serif",
)

@Serializable
data class MediaRef(val id: String, val uri: String, val displayName: String, val kind: TrackType, val durationMs: Long = 0, val width: Int = 0, val height: Int = 0)

@Serializable
data class Clip(
    val id: String, val name: String, val type: TrackType, val mediaId: String? = null,
    val startMs: Long, val durationMs: Long, val trimInMs: Long = 0,
    val visible: Boolean = true, val locked: Boolean = false,
    val transform: Transform = Transform(),
    val widthN: Float = 0.4f, val heightN: Float = 0.4f,
    val blendMode: String = "NORMAL", val colorArgb: Long = 0xFFD4AF37,
    val text: TextStyle? = null, val volume: Float = 1f, val muted: Boolean = false,
    val effects: List<EffectInstance> = emptyList(),
) {
    val endMs get() = startMs + durationMs
    fun localTime(timelineMs: Long) = timelineMs - startMs
    fun isActiveAt(t: Long) = visible && t >= startMs && t < endMs
}

@Serializable
data class Track(val id: String, val name: String, val type: TrackType, val clips: List<Clip> = emptyList(), val hidden: Boolean = false, val locked: Boolean = false)

@Serializable
data class AudioSettings(val masterVolume: Float = 1f, val preserveSourceAudio: Boolean = true)

@Serializable
data class Project(
    val formatVersion: Int = PROJECT_FORMAT_VERSION,
    val id: String, val name: String,
    val width: Int = 1080, val height: Int = 1920, val fps: Int = 30,
    val durationMs: Long = 10_000,
    val media: List<MediaRef> = emptyList(),
    /** Index 0 = bottom layer; later tracks draw on top. */
    val tracks: List<Track> = emptyList(),
    val transitions: List<TransitionInstance> = emptyList(),
    val savedEffects: List<EffectPreset> = emptyList(),
    val savedTransitions: List<TransitionPreset> = emptyList(),
    val audio: AudioSettings = AudioSettings(),
    val modifiedAtMs: Long = 0,
) {
    val allClips get() = tracks.flatMap { it.clips }
    fun clip(id: String) = allClips.firstOrNull { it.id == id }
    fun trackOf(clipId: String) = tracks.firstOrNull { t -> t.clips.any { it.id == clipId } }
    fun contentEndMs() = allClips.maxOfOrNull { it.endMs } ?: 0L

    fun mapClip(id: String, f: (Clip) -> Clip) =
        copy(tracks = tracks.map { t -> if (t.clips.any { it.id == id }) t.copy(clips = t.clips.map { c -> if (c.id == id) f(c) else c }) else t })
}
