package com.zoromotion.app.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.zoromotion.core.model.*
import com.zoromotion.core.timeline.Timeline
import kotlin.math.abs

private fun typeColor(t: TrackType) = when (t) {
    TrackType.VIDEO -> Color(0xFF3B6FB6); TrackType.IMAGE -> Color(0xFF2E8B73); TrackType.AUDIO -> Color(0xFF8A4FB3)
    TrackType.TEXT -> Color(0xFFB8862B); TrackType.SHAPE -> Color(0xFFB5523B); TrackType.ADJUSTMENT -> Color(0xFF6B6B75)
}

/** Multi-track timeline. Drag clip body = move (snaps), drag clip edges = trim, drag ruler = scrub, drag empty space = pan. Long-press = menu. */
@Composable
fun TimelineView(st: EditorState, pxPerMs: Float, scrollMs: Long, onScroll: (Long) -> Unit, modifier: Modifier = Modifier) {
    val d = LocalDensity.current.density
    val rowH = 40f * d; val ruler = 22f * d; val edge = 16f * d
    var mode by remember { mutableIntStateOf(0) }       // 1 scrub, 2 move, 3 trimStart, 4 trimEnd, 5 pan
    var dragId by remember { mutableStateOf<String?>(null) }; var grab by remember { mutableLongStateOf(0L) }
    var menuFor by remember { mutableStateOf<String?>(null) }; var renaming by remember { mutableStateOf<String?>(null) }
    val p = st.project
    val rows = p.tracks.asReversed()   // top row = top z-order

    fun timeAt(x: Float) = scrollMs + (x / pxPerMs).toLong()
    fun hit(pos: Offset): Pair<Clip, Int>? {
        if (pos.y < ruler) return null
        val tr = rows.getOrNull(((pos.y - ruler) / rowH).toInt()) ?: return null
        val t = timeAt(pos.x)
        val c = tr.clips.lastOrNull { t >= it.startMs && t <= it.endMs } ?: return null
        val x0 = (c.startMs - scrollMs) * pxPerMs; val x1 = (c.endMs - scrollMs) * pxPerMs
        return c to when { abs(pos.x - x0) < edge -> 3; abs(pos.x - x1) < edge -> 4; else -> 2 }
    }

    Box(modifier.background(ZColors.Surface)) {
        Canvas(Modifier.fillMaxSize()
            .pointerInput(pxPerMs, scrollMs, p.tracks.size) {
                detectTapGestures(
                    onTap = { pos -> if (pos.y < ruler) st.seek(timeAt(pos.x)) else st.selectedId = hit(pos)?.first?.id },
                    onLongPress = { pos -> hit(pos)?.first?.let { st.selectedId = it.id; menuFor = it.id } })
            }
            .pointerInput(pxPerMs, scrollMs, p.tracks.size) {
                detectDragGestures(
                    onDragStart = { pos ->
                        val h = hit(pos)
                        mode = when { pos.y < ruler -> 1; h != null -> { st.selectedId = h.first.id; dragId = h.first.id; grab = timeAt(pos.x) - h.first.startMs; h.second }; else -> 5 }
                        if (mode == 1) st.seek(timeAt(pos.x))
                    },
                    onDrag = { change, delta ->
                        change.consume()
                        val t = timeAt(change.position.x); val id = dragId
                        when (mode) {
                            1 -> st.seek(t)
                            2 -> if (id != null) {
                                val c = st.project.clip(id)!!
                                var s = (t - grab).coerceAtLeast(0)
                                val e = st.nearestSnap(s + c.durationMs, pxPerMs, id); val s2 = st.nearestSnap(s, pxPerMs, id)
                                s = if (s2 != s) s2 else if (e != s + c.durationMs) e - c.durationMs else s
                                st.live(Timeline.moveClip(st.project, id, s))
                            }
                            3 -> if (id != null) st.live(Timeline.trimStart(st.project, id, st.nearestSnap(t, pxPerMs, id)))
                            4 -> if (id != null) st.live(Timeline.trimEnd(st.project, id, st.nearestSnap(t, pxPerMs, id)))
                            5 -> onScroll((scrollMs - (delta.x / pxPerMs).toLong()).coerceAtLeast(0))
                        }
                    },
                    onDragEnd = { if (mode in 2..4) st.commitLive(); mode = 0; dragId = null },
                    onDragCancel = { if (mode in 2..4) st.commitLive(); mode = 0; dragId = null })
            }) {
            drawTimeline(st, rows, pxPerMs, scrollMs, rowH, ruler)
        }
        DropdownMenu(menuFor != null, { menuFor = null }, offset = androidx.compose.ui.unit.DpOffset(24.dp, 8.dp)) {
            val id = menuFor; val c = id?.let { st.project.clip(it) }
            if (c != null) {
                DropdownMenuItem({ Text("Split at playhead") }, { st.edit { Timeline.splitClip(it, c.id, st.playheadMs) }; menuFor = null })
                DropdownMenuItem({ Text("Duplicate") }, { st.edit { Timeline.duplicateClip(it, c.id) }; menuFor = null })
                DropdownMenuItem({ Text("Rename") }, { renaming = c.id; menuFor = null })
                DropdownMenuItem({ Text(if (c.visible) "Hide" else "Show") }, { st.edit { Timeline.setVisible(it, c.id, !c.visible) }; menuFor = null })
                DropdownMenuItem({ Text(if (c.locked) "Unlock" else "Lock") }, { st.edit { Timeline.setLocked(it, c.id, !c.locked) }; menuFor = null })
                DropdownMenuItem({ Text("Delete", color = ZColors.Danger) }, { st.edit { Timeline.deleteClip(it, c.id) }; st.selectedId = null; menuFor = null })
            }
        }
    }
    renaming?.let { id -> RenameDialog(st.project.clip(id)?.name ?: "", { renaming = null }) { n -> st.edit { Timeline.renameClip(it, id, n) }; renaming = null } }
}

private fun DrawScope.drawTimeline(st: EditorState, rows: List<Track>, pxPerMs: Float, scrollMs: Long, rowH: Float, ruler: Float) {
    val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF2F2F2.toInt(); textSize = rowH * 0.3f }
    val dim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF9A9AA2.toInt(); textSize = ruler * 0.5f }
    val stepMs = if (pxPerMs * 1000 < 40) 5000L else if (pxPerMs * 1000 < 90) 2000L else 1000L
    var t = (scrollMs / stepMs) * stepMs
    drawRect(ZColors.Surface2, Offset.Zero, Size(size.width, ruler))
    while ((t - scrollMs) * pxPerMs < size.width) {
        val x = (t - scrollMs) * pxPerMs
        drawLine(ZColors.Muted, Offset(x, ruler * 0.6f), Offset(x, ruler)); drawIntoCanvas { it.nativeCanvas.drawText("${t / 1000}s", x + 4, ruler * 0.55f, dim) }
        t += stepMs
    }
    rows.forEachIndexed { i, tr ->
        val y = ruler + i * rowH
        drawRect(if (i % 2 == 0) Color(0xFF1D1D21) else Color(0xFF202025), Offset(0f, y), Size(size.width, rowH))
        for (c in tr.clips) {
            val x0 = (c.startMs - scrollMs) * pxPerMs; val w = c.durationMs * pxPerMs
            if (x0 + w < 0 || x0 > size.width) continue
            val sel = c.id == st.selectedId
            drawRoundRect(typeColor(c.type).copy(alpha = if (c.visible && !tr.hidden) 1f else 0.35f), Offset(x0, y + 3), Size(w, rowH - 6), CornerRadius(8f))
            if (sel) drawRoundRect(ZColors.Gold, Offset(x0, y + 3), Size(w, rowH - 6), CornerRadius(8f), style = Stroke(3f))
            drawIntoCanvas { it.nativeCanvas.drawText((if (c.locked) "[L] " else "") + c.name, maxOf(x0, 0f) + 8, y + rowH * 0.62f, txt) }
            if (sel) for (prop in Prop.values()) for (k in prop.get(c.transform).keyframes) {
                val kx = (c.startMs + k.timeMs - scrollMs) * pxPerMs; val ky = y + rowH - 9
                val dm = Path().apply { moveTo(kx, ky - 6); lineTo(kx + 6, ky); lineTo(kx, ky + 6); lineTo(kx - 6, ky); close() }
                drawPath(dm, if (prop == st.activeProp) ZColors.Gold else Color.White)
            }
            c.effects.forEach { e -> val ex = (c.startMs + e.startMs - scrollMs) * pxPerMs; drawRect(Color(0x99FFD54A), Offset(ex, y + 3), Size(e.durationMs * pxPerMs, 4f)) }
        }
    }
    st.project.transitions.forEach { ti ->
        val b = st.project.clip(ti.toClipId) ?: return@forEach
        val row = rows.indexOfFirst { r -> r.clips.any { it.id == b.id } }.takeIf { it >= 0 } ?: return@forEach
        val x = (b.startMs - scrollMs) * pxPerMs; val y = ruler + row * rowH + rowH / 2
        drawRect(Color(0xCCFFFFFF), Offset(x - ti.params.durationMs * pxPerMs / 2, y - 5), Size(ti.params.durationMs * pxPerMs, 10f))
    }
    val px = (st.playheadMs - scrollMs) * pxPerMs
    drawLine(ZColors.Gold, Offset(px, 0f), Offset(px, size.height), 3f); drawCircle(ZColors.Gold, 9f, Offset(px, 9f))
}

@Composable
fun RenameDialog(initial: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var v by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Rename") }, text = { OutlinedTextField(v, { v = it }, singleLine = true) },
        confirmButton = { TextButton({ onOk(v) }) { Text("OK") } }, dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}
