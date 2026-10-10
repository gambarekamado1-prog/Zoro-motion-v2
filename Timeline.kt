package com.zoromotion.core.timeline

import com.zoromotion.core.model.*
import java.util.UUID

fun newId() = UUID.randomUUID().toString().take(8)

/** Pure functions Project -> Project. Locked clips/tracks are never modified. */
object Timeline {
    const val MIN_CLIP_MS = 100L

    private fun editable(p: Project, id: String): Boolean {
        val c = p.clip(id) ?: return false
        return !c.locked && p.trackOf(id)?.locked != true
    }

    /** Snap to playhead, 0 and clip edges (other clips) within threshold; returns the original time if nothing is near. */
    fun snap(p: Project, t: Long, playheadMs: Long, thresholdMs: Long, ignoreClipId: String? = null): Long {
        val targets = buildList {
            add(0L); add(playheadMs)
            p.allClips.filter { it.id != ignoreClipId }.forEach { add(it.startMs); add(it.endMs) }
        }
        return targets.minByOrNull { kotlin.math.abs(it - t) }?.takeIf { kotlin.math.abs(it - t) <= thresholdMs } ?: t
    }

    fun moveClip(p: Project, id: String, newStartMs: Long): Project =
        if (!editable(p, id)) p else p.mapClip(id) { it.copy(startMs = newStartMs.coerceAtLeast(0)) }.touch()

    fun trimStart(p: Project, id: String, newStartMs: Long): Project {
        if (!editable(p, id)) return p
        val c = p.clip(id)!!
        val ns = newStartMs.coerceIn(0, c.endMs - MIN_CLIP_MS)
        val delta = ns - c.startMs
        if (c.type != TrackType.IMAGE && c.type != TrackType.TEXT && c.type != TrackType.SHAPE && c.trimInMs + delta < 0) return p
        return p.mapClip(id) { it.copy(startMs = ns, durationMs = it.durationMs - delta, trimInMs = it.trimInMs + delta,
            transform = it.transform.shifted(-delta), effects = it.effects.map { e -> e.copy(startMs = e.startMs - delta) }) }.touch()
    }

    fun trimEnd(p: Project, id: String, newEndMs: Long): Project {
        if (!editable(p, id)) return p
        val c = p.clip(id)!!
        val maxLen = p.media.firstOrNull { it.id == c.mediaId }?.takeIf { it.durationMs > 0 && it.kind == TrackType.VIDEO }?.let { it.durationMs - c.trimInMs }
        var d = (newEndMs - c.startMs).coerceAtLeast(MIN_CLIP_MS)
        if (maxLen != null) d = d.coerceAtMost(maxLen)
        return p.mapClip(id) { it.copy(durationMs = d) }.touch()
    }

    fun splitClip(p: Project, id: String, atMs: Long): Project {
        if (!editable(p, id)) return p
        val c = p.clip(id)!!
        if (atMs <= c.startMs + MIN_CLIP_MS || atMs >= c.endMs - MIN_CLIP_MS) return p
        val off = atMs - c.startMs
        val left = c.copy(durationMs = off)
        val right = c.copy(id = newId(), name = c.name + " B", startMs = atMs, durationMs = c.durationMs - off, trimInMs = c.trimInMs + off,
            transform = c.transform.shifted(-off), effects = c.effects.map { it.copy(startMs = it.startMs - off) })
        return p.copy(tracks = p.tracks.map { t ->
            if (t.clips.any { it.id == id }) t.copy(clips = t.clips.flatMap { if (it.id == id) listOf(left, right) else listOf(it) }) else t
        }).touch()
    }

    fun duplicateClip(p: Project, id: String): Project {
        val c = p.clip(id) ?: return p
        val copy = c.copy(id = newId(), name = c.name + " copy", startMs = c.endMs, locked = false)
        return p.copy(tracks = p.tracks.map { t -> if (t.clips.any { it.id == id }) t.copy(clips = t.clips + copy) else t }).touch()
    }

    fun deleteClip(p: Project, id: String): Project =
        if (!editable(p, id)) p else p.copy(
            tracks = p.tracks.map { t -> t.copy(clips = t.clips.filter { it.id != id }) },
            transitions = p.transitions.filter { it.fromClipId != id && it.toClipId != id }).touch()

    fun renameClip(p: Project, id: String, name: String) = p.mapClip(id) { it.copy(name = name.ifBlank { it.name }) }.touch()
    fun setVisible(p: Project, id: String, v: Boolean) = p.mapClip(id) { it.copy(visible = v) }.touch()
    fun setLocked(p: Project, id: String, v: Boolean) = p.mapClip(id) { it.copy(locked = v) }.touch()

    /** Moves a track up/down in the stack (z-order). */
    fun reorderTrack(p: Project, trackId: String, delta: Int): Project {
        val i = p.tracks.indexOfFirst { it.id == trackId }
        val j = i + delta
        if (i < 0 || j !in p.tracks.indices) return p
        val l = p.tracks.toMutableList(); l.add(j, l.removeAt(i))
        return p.copy(tracks = l).touch()
    }

    fun addTrack(p: Project, type: TrackType, name: String = type.name.lowercase().replaceFirstChar { it.uppercase() }) =
        p.copy(tracks = p.tracks + Track(newId(), name, type)).touch()

    /** Adds a clip to the first unlocked track of this type (creating one) at `startMs`. */
    fun addClip(p: Project, clip: Clip): Project {
        val base = if (p.tracks.none { it.type == clip.type && !it.locked }) addTrack(p, clip.type) else p
        val target = base.tracks.last { it.type == clip.type && !it.locked }
        val proj = base.copy(tracks = base.tracks.map { if (it.id == target.id) it.copy(clips = it.clips + clip) else it })
        return proj.copy(durationMs = maxOf(proj.durationMs, clip.endMs)).touch()
    }

    /** Applies a transition between two clips that sit back-to-back (or overlap) on the same track. */
    fun applyTransition(p: Project, fromId: String, toId: String, params: com.zoromotion.core.effects.TransitionParams): Project {
        val a = p.clip(fromId) ?: return p; val b = p.clip(toId) ?: return p
        if (p.trackOf(fromId)?.id != p.trackOf(toId)?.id || a.type == TrackType.AUDIO) return p
        val t = com.zoromotion.core.effects.TransitionInstance(newId(), fromId, toId, params)
        return p.copy(transitions = p.transitions.filter { it.fromClipId != fromId || it.toClipId != toId } + t).touch()
    }

    private fun Project.touch() = copy(modifiedAtMs = System.currentTimeMillis())
}

/** Undo/redo over immutable project snapshots. */
class History(initial: Project, private val limit: Int = 100) {
    private val undo = ArrayDeque<Project>(); private val redo = ArrayDeque<Project>()
    var current: Project = initial; private set
    val canUndo get() = undo.isNotEmpty(); val canRedo get() = redo.isNotEmpty()
    fun push(p: Project) { if (p == current) return; undo.addLast(current); if (undo.size > limit) undo.removeFirst(); redo.clear(); current = p }
    fun undo(): Project { if (undo.isNotEmpty()) { redo.addLast(current); current = undo.removeLast() }; return current }
    fun redo(): Project { if (redo.isNotEmpty()) { undo.addLast(current); current = redo.removeLast() }; return current }
}
