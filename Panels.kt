package com.zoromotion.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zoromotion.core.animation.*
import com.zoromotion.core.effects.*
import com.zoromotion.core.model.*
import com.zoromotion.core.timeline.*
import kotlin.math.roundToInt

@Composable
fun ParamRow(label: String, value: Float, min: Float, max: Float, onLive: (Float) -> Unit, onDone: () -> Unit, keyed: Boolean? = null, onKey: (() -> Unit)? = null, onSelect: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().clickable(enabled = onSelect != null) { onSelect?.invoke() }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(96.dp), fontSize = 12.sp, color = ZColors.Muted, maxLines = 2)
        Slider(value.coerceIn(min, max), onLive, Modifier.weight(1f), valueRange = min..max, onValueChangeFinished = onDone)
        Text("%.2f".format(value), Modifier.width(48.dp), fontSize = 11.sp)
        if (onKey != null) TextButton(onKey, contentPadding = PaddingValues(0.dp), modifier = Modifier.width(32.dp)) { Text(if (keyed == true) "◆" else "◇", color = ZColors.Gold, fontSize = 18.sp) }
    }
}

@Composable
fun <T> ChipRow(items: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { FilterChip(it == selected, { onPick(it) }, { Text(label(it), fontSize = 11.sp) }) }
    }
}
// ------------------------------------------------------------------ Properties
@Composable
fun PropertiesPanel(st: EditorState) {
    val c = st.selected
    if (c == null) { Hint("Select a layer on the canvas or timeline."); return }
    val t = st.localMs(c)
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        Text("${c.name}  ·  ${c.type.name.lowercase()}" + if (c.locked) "  (locked)" else "", color = ZColors.Gold, fontSize = 13.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton({ st.jumpKeyframe(false) }) { Text("◀ Key") }; TextButton({ st.jumpKeyframe(true) }) { Text("Key ▶") }
        }
        for (prop in Prop.values()) {
            val a = prop.get(c.transform)
            ParamRow(prop.label, a.valueAt(t), prop.min, prop.max, { st.setProp(prop, it); st.activeProp = prop }, { st.commitLive() },
                keyed = a.hasKeyframeAt(t), onKey = { st.activeProp = prop; st.toggleKeyframe(prop) }, onSelect = { st.activeProp = prop })
        }
        ParamRow("Width", c.widthN, 0.05f, 2f, { v -> st.updateSelected(true) { it.copy(widthN = v) } }, { st.commitLive() })
        ParamRow("Height", c.heightN, 0.05f, 2f, { v -> st.updateSelected(true) { it.copy(heightN = v) } }, { st.commitLive() })
        Text("Blend mode", fontSize = 12.sp, color = ZColors.Muted)
        ChipRow(BlendModes, c.blendMode, { it }) { m -> st.updateSelected { it.copy(blendMode = m) } }
        val a = st.activeProp.get(c.transform); val k = a.keyframes.firstOrNull { it.timeMs == t }
        Spacer(Modifier.height(8.dp))
        if (k == null) Text("Keyframe interpolation and curve appear when the playhead is on a ◆ keyframe of ${st.activeProp.label}.", fontSize = 11.sp, color = ZColors.Muted)
        else {
            Text("${st.activeProp.label} keyframe easing (segment to next keyframe)", fontSize = 12.sp, color = ZColors.Muted)
            ChipRow(Interpolation.values().toList(), k.interpolation, { it.name.lowercase().replace('_', '-') }) { st.setInterpolation(st.activeProp, it) }
            if (k.interpolation == Interpolation.BEZIER) EasingCurveEditor(k.bezier, { st.setInterpolation(st.activeProp, Interpolation.BEZIER, it, live = true) }, { st.commitLive() })
        }
        c.text?.let { ts ->
            Spacer(Modifier.height(8.dp)); Text("Text", color = ZColors.Gold, fontSize = 13.sp)
            OutlinedTextField(ts.text, { v -> st.updateSelected(true) { it.copy(text = ts.copy(text = v)) }; st.commitLive() }, Modifier.fillMaxWidth())
            ParamRow("Size", ts.sizeN, 0.02f, 0.4f, { v -> st.updateSelected(true) { it.copy(text = ts.copy(sizeN = v)) } }, { st.commitLive() })
            ChipRow(listOf("LEFT", "CENTER", "RIGHT"), ts.align, { it }) { al -> st.updateSelected { it.copy(text = ts.copy(align = al)) } }
            Text("Colour", fontSize = 12.sp, color = ZColors.Muted)
            Palette { col -> st.updateSelected { it.copy(text = ts.copy(colorArgb = col)) } }
            ChipRow(listOf("sans-serif", "serif", "monospace"), ts.fontFamily, { it }) { f -> st.updateSelected { it.copy(text = ts.copy(fontFamily = f)) } }
        }
        if (c.text == null) { Text("Fill colour", fontSize = 12.sp, color = ZColors.Muted); Palette { col -> st.updateSelected { it.copy(colorArgb = col) } } }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable private fun Palette(onPick: (Long) -> Unit) = Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    listOf(0xFFFFFFFF, 0xFFD4AF37, 0xFFFF2A2A, 0xFF22E0FF, 0xFF7C4DFF, 0xFF000000).forEach { col ->
        Box(Modifier.size(28.dp).background(Color(col), RoundedCornerShape(14.dp)).clickable { onPick(col) })
    }
}

@Composable fun Hint(s: String) = Box(Modifier.fillMaxSize().padding(16.dp), Alignment.Center) { Text(s, color = ZColors.Muted, fontSize = 13.sp) }

/** Drag the two gold handles to shape a cubic-bezier easing curve; vertical range allows overshoot. */
@Composable
fun EasingCurveEditor(b: Bezier, onLive: (Bezier) -> Unit, onDone: () -> Unit) {
    var drag by remember { mutableIntStateOf(0) }
    Canvas(Modifier.padding(vertical = 8.dp).size(190.dp).background(ZColors.Surface2, RoundedCornerShape(12.dp)).pointerInput(b) {
        val pad = 20f
        fun toPx(x: Float, y: Float) = Offset(pad + x * (size.width - 2 * pad), pad + (1.5f - y) / 2f * (size.height - 2 * pad))
        detectDragGestures(
            onDragStart = { pos -> drag = if ((pos - toPx(b.x1, b.y1)).getDistance() < (pos - toPx(b.x2, b.y2)).getDistance()) 1 else 2 },
            onDrag = { ch, _ ->
                ch.consume()
                val x = ((ch.position.x - pad) / (size.width - 2 * pad)).coerceIn(0f, 1f)
                val y = (1.5f - (ch.position.y - pad) / (size.height - 2 * pad) * 2f).coerceIn(-0.5f, 1.5f)
                onLive(if (drag == 1) b.copy(x1 = x, y1 = y) else b.copy(x2 = x, y2 = y))
            }, onDragEnd = onDone, onDragCancel = onDone)
    }) {
        val pad = 20f
        fun pt(x: Float, y: Float) = Offset(pad + x * (size.width - 2 * pad), pad + (1.5f - y) / 2f * (size.height - 2 * pad))
        drawLine(Color(0xFF444449), pt(0f, 0f), pt(1f, 1f)); drawLine(Color(0xFF444449), pt(0f, 1f), pt(1f, 1f)); drawLine(Color(0xFF444449), pt(0f, 0f), pt(1f, 0f))
        val path = Path().apply { moveTo(pt(0f, 0f).x, pt(0f, 0f).y); for (i in 1..60) { val x = i / 60f; val o = pt(x, b.solve(x)); lineTo(o.x, o.y) } }
        drawPath(path, Color.White, style = Stroke(4f))
        drawLine(ZColors.Gold, pt(0f, 0f), pt(b.x1, b.y1)); drawLine(ZColors.Gold, pt(1f, 1f), pt(b.x2, b.y2))
        drawCircle(ZColors.Gold, 12f, pt(b.x1, b.y1)); drawCircle(ZColors.Gold, 12f, pt(b.x2, b.y2))
    }
}

// ------------------------------------------------------------------ Layers
@Composable
fun LayersPanel(st: EditorState) {
    var renaming by remember { mutableStateOf<String?>(null) }
    val rows = st.project.tracks.asReversed()
    if (rows.all { it.clips.isEmpty() }) { Hint("No layers yet. Use Add to import media, text or shapes."); return }
    LazyColumn(Modifier.padding(horizontal = 8.dp)) {
        rows.forEach { tr ->
            item(key = tr.id) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${tr.name} track", Modifier.weight(1f), color = ZColors.Muted, fontSize = 12.sp)
                    IconButton({ st.edit { Timeline.reorderTrack(it, tr.id, 1) } }) { Icon(Icons.Default.ArrowUpward, "Up") }
                    IconButton({ st.edit { Timeline.reorderTrack(it, tr.id, -1) } }) { Icon(Icons.Default.ArrowDownward, "Down") }
                }
            }
            items(tr.clips.sortedBy { it.startMs }, key = { it.id }) { c ->
                Row(Modifier.fillMaxWidth().background(if (c.id == st.selectedId) ZColors.Surface2 else Color.Transparent, RoundedCornerShape(8.dp)).clickable { st.selectedId = c.id },
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(c.name, Modifier.weight(1f).padding(start = 8.dp), maxLines = 1)
                    IconButton({ st.edit { Timeline.setVisible(it, c.id, !c.visible) } }) { Icon(if (c.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff, "Visible") }
                    IconButton({ st.edit { Timeline.setLocked(it, c.id, !c.locked) } }) { Icon(if (c.locked) Icons.Default.Lock else Icons.Default.LockOpen, "Lock") }
                    IconButton({ renaming = c.id }) { Icon(Icons.Default.Edit, "Rename") }
                    IconButton({ st.edit { Timeline.duplicateClip(it, c.id) } }) { Icon(Icons.Default.ContentCopy, "Duplicate") }
                    IconButton({ st.edit { Timeline.deleteClip(it, c.id) }; if (st.selectedId == c.id) st.selectedId = null }) { Icon(Icons.Default.Delete, "Delete", tint = ZColors.Danger) }
                }
            }
        }
    }
    renaming?.let { id -> RenameDialog(st.project.clip(id)?.name ?: "", { renaming = null }) { n -> st.edit { Timeline.renameClip(it, id, n) }; renaming = null } }
}

// ------------------------------------------------------------------ Effects
@Composable
fun EffectsPanel(st: EditorState) {
    val c = st.selected
    if (c == null) { Hint("Select a layer to add effects."); return }
    var naming by remember { mutableStateOf<EffectInstance?>(null) }
    var renamePreset by remember { mutableStateOf<EffectPreset?>(null) }
    val t = st.localMs(c)
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        Text("Add effect at playhead", fontSize = 12.sp, color = ZColors.Muted)
        ChipRow(EffectType.values().toList(), null as EffectType?, { EffectCatalog.title(it!!) }) { ty ->
            val fx = EffectInstance(newId(), ty!!, startMs = t, durationMs = 600.coerceAtMost((c.durationMs - t).toInt().coerceAtLeast(100)).toLong())
            st.updateSelected { it.copy(effects = it.effects + fx) }
        }
        c.effects.forEach { fx ->
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = ZColors.Surface2)) {
                Column(Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(EffectCatalog.title(fx.type), Modifier.weight(1f), color = ZColors.Gold)
                        Switch(fx.enabled, { v -> upd(st, fx) { it.copy(enabled = v) } })
                    }
                    ParamRow("Start (ms)", fx.startMs.toFloat(), 0f, c.durationMs.toFloat(), { v -> upd(st, fx, true) { it.copy(startMs = v.toLong()) } }, { st.commitLive() })
                    ParamRow("Duration (ms)", fx.durationMs.toFloat(), 50f, 4000f, { v -> upd(st, fx, true) { it.copy(durationMs = v.toLong()) } }, { st.commitLive() })
                    EffectCatalog.defs(fx.type).forEach { d ->
                        val a = fx.params[d.key] ?: AnimatedValue(d.default)
                        ParamRow(d.label, a.valueAt(t), d.min, d.max, { v -> upd(st, fx, true) { it.withParam(d.key, v, t) } }, { st.commitLive() },
                            keyed = a.hasKeyframeAt(t), onKey = { upd(st, fx) { e -> val cur = e.params[d.key] ?: AnimatedValue(d.default)
                                e.copy(params = e.params + (d.key to if (cur.hasKeyframeAt(t)) cur.withoutKeyframeAt(t) else cur.withKeyframe(Keyframe(t, cur.valueAt(t))))) } })
                    }
                    Row {
                        TextButton({ upd(st, fx) { it.copy(params = EffectCatalog.defaults(it.type)) } }) { Text("Reset") }
                        TextButton({ st.updateSelected { cc -> cc.copy(effects = cc.effects + fx.copy(id = newId())) } }) { Text("Duplicate") }
                        TextButton({ naming = fx }) { Text("Save preset") }
                        TextButton({ st.updateSelected { cc -> cc.copy(effects = cc.effects.filter { it.id != fx.id }) } }) { Text("Delete", color = ZColors.Danger) }
                    }
                }
            }
        }
        if (st.project.savedEffects.isNotEmpty()) Text("Saved effects", color = ZColors.Gold, modifier = Modifier.padding(top = 8.dp))
        st.project.savedEffects.forEach { pr ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(pr.name, Modifier.weight(1f))
                TextButton({ st.updateSelected { it.copy(effects = it.effects + pr.effect.copy(id = newId(), startMs = t)) } }) { Text("Apply") }
                TextButton({ st.edit { p -> p.copy(savedEffects = p.savedEffects + pr.copy(id = newId(), name = pr.name + " copy")) } }) { Text("Dup") }
                TextButton({ renamePreset = pr }) { Text("Rename") }
                TextButton({ st.edit { p -> p.copy(savedEffects = p.savedEffects.filter { it.id != pr.id }) } }) { Text("Del", color = ZColors.Danger) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    naming?.let { fx -> RenameDialog(EffectCatalog.title(fx.type), { naming = null }) { n -> st.edit { p -> p.copy(savedEffects = p.savedEffects + EffectPreset(newId(), n, fx)) }; naming = null } }
    renamePreset?.let { pr -> RenameDialog(pr.name, { renamePreset = null }) { n -> st.edit { p -> p.copy(savedEffects = p.savedEffects.map { if (it.id == pr.id) it.copy(name = n) else it }) }; renamePreset = null } }
}

private fun upd(st: EditorState, fx: EffectInstance, live: Boolean = false, f: (EffectInstance) -> EffectInstance) =
    st.updateSelected(live) { c -> c.copy(effects = c.effects.map { if (it.id == fx.id) f(it) else it }) }

// ------------------------------------------------------------------ Transition builder
@Composable
fun TransitionPanel(st: EditorState, tp: TransitionParams, onChange: (TransitionParams) -> Unit) {
    val c = st.selected
    val next = c?.let { st.nextClipOnTrack(it) }
    var naming by remember { mutableStateOf(false) }; var renameP by remember { mutableStateOf<TransitionPreset?>(null) }
    var prog by remember { mutableFloatStateOf(0f) }; var loop by remember { mutableStateOf(true) }
    LaunchedEffect(loop, tp.durationMs) {
        while (loop) {
            val start = withFrameNanosCompat(); var p = 0f
            while (p < 1f) { val now = withFrameNanosCompat(); p = ((now - start) / 1e6f / tp.durationMs.coerceAtLeast(100)).coerceAtMost(1f); prog = p }
            kotlinx.coroutines.delay(400)
        }
    }
    val existing = c?.let { cc -> st.project.transitions.firstOrNull { it.fromClipId == cc.id } }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
        TransitionPreviewBox(tp, prog)
        Row { TextButton({ loop = !loop }) { Text(if (loop) "Pause preview" else "Loop preview") } }
        ChipRow(TransitionType.values().toList(), tp.type, { it.name.lowercase().replace('_', ' ') }) { onChange(tp.copy(type = it)) }
        fun row(l: String, v: Float, mn: Float, mx: Float, f: (Float) -> TransitionParams) = @Composable { ParamRow(l, v, mn, mx, { onChange(f(it)) }, {}) }
        row("Duration (ms)", tp.durationMs.toFloat(), 100f, 1500f) { tp.copy(durationMs = it.toLong()) }()
        row("Direction (deg)", tp.directionDeg, 0f, 360f) { tp.copy(directionDeg = it) }()
        row("Intensity", tp.intensity, 0f, 2f) { tp.copy(intensity = it) }()
        row("Zoom amount", tp.zoomAmount, 0f, 3f) { tp.copy(zoomAmount = it) }()
        row("Rotation (deg)", tp.rotationDeg, -360f, 360f) { tp.copy(rotationDeg = it) }()
        row("Shake", tp.shakeStrength, 0f, 0.1f) { tp.copy(shakeStrength = it) }()
        row("Blur", tp.blurStrength, 0f, 1f) { tp.copy(blurStrength = it) }()
        row("Flash", tp.flashStrength, 0f, 1f) { tp.copy(flashStrength = it) }()
        row("Start at", tp.startAt, 0f, 0.9f) { tp.copy(startAt = it.coerceAtMost(tp.endAt - 0.05f)) }()
        row("End at", tp.endAt, 0.1f, 1f) { tp.copy(endAt = it.coerceAtLeast(tp.startAt + 0.05f)) }()
        ChipRow(Interpolation.values().filter { it != Interpolation.HOLD }, tp.easing, { it.name.lowercase().replace('_', '-') }) { onChange(tp.copy(easing = it)) }
        if (tp.easing == Interpolation.BEZIER) EasingCurveEditor(tp.bezier, { onChange(tp.copy(bezier = it)) }, {})
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button({ if (c != null && next != null) st.edit { Timeline.applyTransition(it, c.id, next.id, tp) } }, enabled = c != null && next != null) { Text("Apply to cut") }
            Spacer(Modifier.width(8.dp)); OutlinedButton({ naming = true }) { Text("Save preset") }
            if (existing != null) TextButton({ onChange(existing.params) }) { Text("Load applied") }
            if (existing != null) TextButton({ st.edit { p -> p.copy(transitions = p.transitions.filter { it.id != existing.id }) } }) { Text("Remove", color = ZColors.Danger) }
        }
        Text(if (c == null) "Select the first of two clips." else if (next == null) "Selected clip has no following clip on its track." else "Between '${c.name}' and '${next.name}'", fontSize = 11.sp, color = ZColors.Muted)
        if (st.project.savedTransitions.isNotEmpty()) Text("Saved transitions", color = ZColors.Gold, modifier = Modifier.padding(top = 8.dp))
        st.project.savedTransitions.forEach { pr ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(pr.name, Modifier.weight(1f))
                TextButton({ onChange(pr.params) }) { Text("Edit") }
                TextButton({ if (c != null && next != null) st.edit { Timeline.applyTransition(it, c.id, next.id, pr.params) } }) { Text("Apply") }
                TextButton({ st.edit { p -> p.copy(savedTransitions = p.savedTransitions + pr.copy(id = newId(), name = pr.name + " copy")) } }) { Text("Dup") }
                TextButton({ renameP = pr }) { Text("Rename") }
                TextButton({ st.edit { p -> p.copy(savedTransitions = p.savedTransitions.filter { it.id != pr.id }) } }) { Text("Del", color = ZColors.Danger) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (naming) RenameDialog("My transition", { naming = false }) { n -> st.edit { p -> p.copy(savedTransitions = p.savedTransitions + TransitionPreset(newId(), n, tp)) }; naming = false }
    renameP?.let { pr -> RenameDialog(pr.name, { renameP = null }) { n -> st.edit { p -> p.copy(savedTransitions = p.savedTransitions.map { if (it.id == pr.id) it.copy(name = n) else it }) }; renameP = null } }
}

private suspend fun withFrameNanosCompat(): Long = androidx.compose.runtime.withFrameNanos { it }

@Composable
private fun TransitionPreviewBox(tp: TransitionParams, prog: Float) {
    Canvas(Modifier.fillMaxWidth().height(110.dp).background(Color.Black, RoundedCornerShape(12.dp))) {
        val fr = TransitionEngine.evaluate(tp, prog, 30, (prog * 100).toInt())
        fun box(color: Color, lf: LayerFx) {
            if (lf.opacity <= 0f) return
            val w = size.height * 0.7f * lf.zoom
            androidx.compose.ui.graphics.drawscope.withTransform({
                translate(size.width / 2 + lf.dx * size.width, size.height / 2 + lf.dy * size.height); rotate(lf.rotationDeg, Offset.Zero) }) {
                drawRect(color.copy(alpha = 1f - (lf.blurPx / 60f).coerceIn(0f, 0.6f)), Offset(-w * 1.3f, -w / 2), androidx.compose.ui.geometry.Size(w * 2.6f, w))
            }
        }
        box(Color(0xFF3B6FB6), fr.outgoing); box(ZColors.Gold, fr.incoming)
        if (fr.flashAlpha > 0f) drawRect(Color.White.copy(alpha = fr.flashAlpha))
    }
}
