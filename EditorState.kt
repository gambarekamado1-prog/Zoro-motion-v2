package com.zoromotion.app.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.zoromotion.core.animation.*
import com.zoromotion.core.effects.*
import com.zoromotion.core.io.ProjectStore
import com.zoromotion.core.model.*
import com.zoromotion.core.timeline.*
import kotlin.math.abs

/** All editor state. Live edits (drags/sliders) call live(); finished gestures call commit() so each gesture is one undo step. */
class EditorState(initial: Project, private val store: ProjectStore) {
    private val history = History(initial)
    var project by mutableStateOf(initial); private set
    var playheadMs by mutableLongStateOf(0L)
    var playing by mutableStateOf(false)
    var selectedId by mutableStateOf<String?>(null)
    var snapEnabled by mutableStateOf(true)
    var activeProp by mutableStateOf(Prop.POS_X)
    var message by mutableStateOf<String?>(null)
    var dirty by mutableStateOf(false)
    val media = mutableStateMapOf<String, ImageBitmap>()
    val canUndo get() = history.canUndo || project != history.current
    val canRedo get() = history.canRedo

    val selected: Clip? get() = selectedId?.let { project.clip(it) }
    fun localMs(c: Clip) = (playheadMs - c.startMs).coerceIn(0, c.durationMs)

    fun live(p: Project) { project = p; dirty = true }
    fun commit(p: Project = project) { history.push(p); project = history.current; dirty = true }
    fun commitLive() = commit(project)
    fun edit(f: (Project) -> Project) = commit(f(project))
    fun undo() { project = history.undo(); dirty = true }
    fun redo() { project = history.redo(); dirty = true }
    fun seek(t: Long) { playheadMs = t.coerceIn(0, project.durationMs) }

    fun save() { try { store.save(project); dirty = false; message = "Saved" } catch (e: Exception) { message = "Save failed: ${e.message}" } }

    // ---- selected-clip helpers ----
    fun updateSelected(live: Boolean = false, f: (Clip) -> Clip) {
        val id = selectedId ?: return
        val c = project.clip(id) ?: return
        if (c.locked) return
        val p = project.mapClip(id, f)
        if (live) live(p) else commit(p)
    }

    /** Sets the property at the playhead: edits base if not animated, else creates/updates a keyframe. */
    fun setProp(prop: Prop, v: Float, live: Boolean = true) = updateSelected(live) { c ->
        c.copy(transform = prop.set(c.transform, prop.get(c.transform).withValueAt((playheadMs - c.startMs).coerceIn(0, c.durationMs), v)))
    }

    fun toggleKeyframe(prop: Prop) = updateSelected { c ->
        val t = (playheadMs - c.startMs).coerceIn(0, c.durationMs); val a = prop.get(c.transform)
        val na = if (a.hasKeyframeAt(t)) a.withoutKeyframeAt(t) else a.withKeyframe(Keyframe(t, a.valueAt(t)))
        c.copy(transform = prop.set(c.transform, na))
    }

    fun setInterpolation(prop: Prop, k: Interpolation, bz: Bezier? = null, live: Boolean = false) = updateSelected(live) { c ->
        val t = (playheadMs - c.startMs).coerceIn(0, c.durationMs)
        c.copy(transform = prop.set(c.transform, prop.get(c.transform).withInterpolationAt(t, k, bz)))
    }

    fun jumpKeyframe(forward: Boolean) {
        val c = selected ?: return
        val times = Prop.values().flatMap { p -> p.get(c.transform).keyframes.map { c.startMs + it.timeMs } }.distinct().sorted()
        val t = if (forward) times.firstOrNull { it > playheadMs } else times.lastOrNull { it < playheadMs }
        if (t != null) seek(t)
    }

    // ---- layers ----
    fun addShape() = addLayer(Clip(newId(), "Shape", TrackType.SHAPE, startMs = playheadMs, durationMs = 3000))
    fun addText() = addLayer(Clip(newId(), "Text", TrackType.TEXT, startMs = playheadMs, durationMs = 3000, widthN = 0.8f, heightN = 0.2f, text = TextStyle()))
    fun addAdjustment() = addLayer(Clip(newId(), "Adjustment", TrackType.ADJUSTMENT, startMs = playheadMs, durationMs = 3000, widthN = 1f, heightN = 1f))
    private fun addLayer(c: Clip) { edit { Timeline.addClip(it, c) }; selectedId = c.id }

    fun addMedia(ctx: Context, uris: List<Uri>) {
        var p = project
        for (u in uris) {
            try { ctx.contentResolver.takePersistableUriPermission(u, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            val mime = ctx.contentResolver.getType(u) ?: ""
            val kind = when { mime.startsWith("video") -> TrackType.VIDEO; mime.startsWith("audio") -> TrackType.AUDIO; mime.startsWith("image") -> TrackType.IMAGE; else -> { message = "Unsupported file type"; continue } }
            var dur = 0L; var w = 0; var h = 0; var name = u.lastPathSegment ?: "media"
            try {
                if (kind != TrackType.IMAGE) MediaMetadataRetriever().use { r ->
                    r.setDataSource(ctx, u)
                    dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
                    w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                } else ctx.contentResolver.openInputStream(u)?.use { s ->
                    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeStream(s, null, o); w = o.outWidth; h = o.outHeight
                }
            } catch (e: Exception) { message = "Can't read ${name}: ${e.message}"; continue }
            val ref = MediaRef(newId(), u.toString(), name, kind, dur, w, h)
            val ar = if (w > 0 && h > 0) w.toFloat() / h else 1f; val car = p.width.toFloat() / p.height
            val (wn, hn) = if (ar > car) 1f to car / ar else ar / car to 1f
            val clip = Clip(newId(), name, kind, ref.id, playheadMs, if (kind == TrackType.IMAGE) 3000 else dur.coerceAtLeast(500), widthN = wn, heightN = hn)
            p = Timeline.addClip(p.copy(media = p.media + ref), clip); selectedId = clip.id
            loadBitmap(ctx, ref)
        }
        commit(p)
    }

    /** Preview shows a still (image or first video frame). Real video playback is not implemented yet. */
    fun loadBitmap(ctx: Context, ref: MediaRef) {
        if (ref.kind == TrackType.AUDIO || media.containsKey(ref.id)) return
        try {
            val u = Uri.parse(ref.uri)
            val bmp = if (ref.kind == TrackType.IMAGE) ctx.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 2 }) }
            else MediaMetadataRetriever().use { it.setDataSource(ctx, u); it.getFrameAtTime(0) }
            if (bmp != null) media[ref.id] = bmp.asImageBitmap()
        } catch (_: Exception) { /* missing media: layer renders as placeholder */ }
    }

    fun nearestSnap(t: Long, pxPerMs: Float, ignore: String?): Long =
        if (!snapEnabled) t else Timeline.snap(project, t, playheadMs, (12 / pxPerMs).toLong(), ignore)

    fun nextClipOnTrack(c: Clip): Clip? = project.trackOf(c.id)?.clips?.filter { it.startMs >= c.startMs && it.id != c.id }?.minByOrNull { it.startMs }
}

inline fun <T : MediaMetadataRetriever, R> T.use(block: (T) -> R): R = try { block(this) } finally { release() }
