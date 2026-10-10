package com.zoromotion.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import com.zoromotion.core.effects.EffectEngine
import com.zoromotion.core.model.Prop
import kotlin.math.*

/** Interactive preview: tap selects, drag moves, corner handle resizes. Gestures here never touch the playhead. */
@Composable
fun PreviewCanvas(st: EditorState, modifier: Modifier = Modifier) {
    val p = st.project
    var mode by remember { mutableIntStateOf(0) }   // 0 none, 1 move, 2 resize
    var sx0 by remember { mutableFloatStateOf(1f) }; var sy0 by remember { mutableFloatStateOf(1f) }; var r0 by remember { mutableFloatStateOf(1f) }

    fun hitInfo(pos: Offset, W: Float, H: Float): Pair<DrawItem, Geo>? {
        val (items, _) = frameItems(st.project, st.playheadMs)
        for (it in items.reversed()) {
            val fx = EffectEngine.evaluateAll(it.clip.effects, it.local, st.project.fps)
            val g = geometry(it.clip, it.local, fx, it.lf, W, H)
            val a = Math.toRadians(-g.rot.toDouble()); val dx = pos.x - g.cx; val dy = pos.y - g.cy
            val lx = ((dx * cos(a) - dy * sin(a)) / g.sx).toFloat(); val ly = ((dx * sin(a) + dy * cos(a)) / g.sy).toFloat()
            if (lx >= -g.ax * g.w && lx <= (1 - g.ax) * g.w && ly >= -g.ay * g.h && ly <= (1 - g.ay) * g.h) return it to g
        }
        return null
    }
    fun handlePos(g: Geo): Offset {
        val a = Math.toRadians(g.rot.toDouble()); val hx = (1 - g.ax) * g.w * g.sx; val hy = (1 - g.ay) * g.h * g.sy
        return Offset(g.cx + (hx * cos(a) - hy * sin(a)).toFloat(), g.cy + (hx * sin(a) + hy * cos(a)).toFloat())
    }

    Box(modifier.aspectRatio(p.width.toFloat() / p.height).background(Color.Black)) {
        Canvas(Modifier.fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { pos -> st.selectedId = hitInfo(pos, size.width.toFloat(), size.height.toFloat())?.first?.clip?.id } }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { pos ->
                        val W = size.width.toFloat(); val H = size.height.toFloat()
                        val sel = st.selected
                        val selItem = sel?.let { s -> frameItems(st.project, st.playheadMs).first.firstOrNull { it.clip.id == s.id } }
                        mode = 0
                        if (selItem != null) {
                            val g = geometry(selItem.clip, selItem.local, EffectEngine.evaluateAll(selItem.clip.effects, selItem.local), selItem.lf, W, H)
                            val h = handlePos(g)
                            if ((pos - h).getDistance() < 48f) { mode = 2; sx0 = g.sx; sy0 = g.sy; r0 = max(1f, (h - Offset(g.cx, g.cy)).getDistance()) }
                        }
                        if (mode == 0) hitInfo(pos, W, H)?.let { st.selectedId = it.first.clip.id; mode = 1 }
                    },
                    onDrag = { change, delta ->
                        change.consume()
                        val W = size.width.toFloat(); val H = size.height.toFloat()
                        val c = st.selected ?: return@detectDragGestures
                        if (mode == 1) {
                            val tr = c.transform; val t = st.localMs(c)
                            st.setProp(Prop.POS_X, tr.posX.valueAt(t) + delta.x / W); st.setProp(Prop.POS_Y, tr.posY.valueAt(t) + delta.y / H)
                        } else if (mode == 2) {
                            val item = frameItems(st.project, st.playheadMs).first.firstOrNull { it.clip.id == c.id } ?: return@detectDragGestures
                            val g = geometry(c, item.local, EffectEngine.evaluateAll(c.effects, item.local), item.lf, W, H)
                            val h = handlePos(g) + delta
                            val k = ((h - Offset(g.cx, g.cy)).getDistance() / r0).coerceAtLeast(0.05f)
                            st.setProp(Prop.SCALE_X, (sx0 * k).coerceIn(0.05f, 5f)); st.setProp(Prop.SCALE_Y, (sy0 * k).coerceIn(0.05f, 5f))
                        }
                    },
                    onDragEnd = { if (mode != 0) st.commitLive(); mode = 0 }, onDragCancel = { if (mode != 0) st.commitLive(); mode = 0 })
            }) {
            drawFrame(st.project, st.playheadMs, st.media)
            val sel = st.selected
            val item = sel?.let { s -> frameItems(st.project, st.playheadMs).first.firstOrNull { it.clip.id == s.id } }
            if (item != null) {
                val g = geometry(item.clip, item.local, EffectEngine.evaluateAll(item.clip.effects, item.local), item.lf, size.width, size.height)
                withTransform({ translate(g.cx, g.cy); rotate(g.rot, Offset.Zero); scale(g.sx, g.sy, Offset.Zero) }) {
                    drawRect(ZColors.Gold, Offset(-g.ax * g.w, -g.ay * g.h), Size(g.w, g.h), style = Stroke(2f / max(g.sx, 0.01f)))
                }
                drawCircle(ZColors.Gold, 14f, handlePos(g)); drawCircle(Color.Black, 6f, handlePos(g))
            }
        }
    }
}
