package com.zoromotion.app.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zoromotion.core.effects.TransitionParams
import com.zoromotion.core.timeline.Timeline

fun timecode(ms: Long, fps: Int): String {
    val s = ms / 1000; val f = ((ms % 1000) * fps / 1000).toInt()
    return "%02d:%02d:%02d".format(s / 60, s % 60, f)
}

@Composable
fun EditorScreen(st: EditorState, onBack: () -> Unit, onExport: () -> Unit) {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(1) }
    var pxPerMs by remember { mutableFloatStateOf(0.08f) }; var scrollMs by remember { mutableLongStateOf(0L) }
    var tparams by remember { mutableStateOf(TransitionParams()) }
    var addMenu by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> -> if (uris.isNotEmpty()) st.addMedia(ctx, uris) }

    // load stills for existing media (missing media is reported, never crashes)
    LaunchedEffect(st.project.media) { st.project.media.forEach { st.loadBitmap(ctx, it) } }
    LaunchedEffect(st.message) { st.message?.let { snack.showSnackbar(it); st.message = null } }
    // playback clock: advances the playhead by real elapsed time; every consumer evaluates from playheadMs
    LaunchedEffect(st.playing) {
        if (!st.playing) return@LaunchedEffect
        if (st.playheadMs >= st.project.durationMs) st.seek(0)
        var last = withFrameMillisCompat()
        while (st.playing) {
            val now = withFrameMillisCompat(); st.seek(st.playheadMs + (now - last)); last = now
            if (st.playheadMs >= st.project.durationMs) st.playing = false
        }
    }
    LaunchedEffect(st.playheadMs) { // keep playhead visible while playing
        val w = 360 / pxPerMs
        if (st.playing && (st.playheadMs < scrollMs || st.playheadMs > scrollMs + w)) scrollMs = (st.playheadMs - 200).coerceAtLeast(0)
    }
    BackHandler { st.save(); onBack() }

    Scaffold(snackbarHost = { SnackbarHost(snack) }, containerColor = ZColors.Bg, topBar = {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ st.save(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text(st.project.name + if (st.dirty) " •" else "", Modifier.weight(1f), maxLines = 1, fontSize = 15.sp)
            IconButton({ st.undo() }, enabled = st.canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo") }
            IconButton({ st.redo() }, enabled = st.canRedo) { Icon(Icons.AutoMirrored.Filled.Redo, "Redo") }
            IconButton({ st.save() }) { Icon(Icons.Default.Save, "Save") }
            Button({ st.save(); onExport() }, contentPadding = PaddingValues(horizontal = 14.dp)) { Text("Export") }
        }
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(0.38f).padding(4.dp), Alignment.Center) { PreviewCanvas(st, Modifier.fillMaxHeight()) }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ st.playing = !st.playing }) { Icon(if (st.playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play", tint = ZColors.Gold) }
                IconButton({ st.seek(0); st.playing = false }) { Icon(Icons.Default.SkipPrevious, "Start") }
                Text(timecode(st.playheadMs, st.project.fps) + " / " + timecode(st.project.durationMs, st.project.fps), fontSize = 12.sp, color = ZColors.Muted)
                Spacer(Modifier.weight(1f))
                IconButton({ pxPerMs = (pxPerMs / 1.4f).coerceAtLeast(0.01f) }) { Icon(Icons.Default.ZoomOut, "Zoom out") }
                IconButton({ pxPerMs = (pxPerMs * 1.4f).coerceAtMost(0.8f) }) { Icon(Icons.Default.ZoomIn, "Zoom in") }
                FilterChip(st.snapEnabled, { st.snapEnabled = !st.snapEnabled }, { Text("Snap", fontSize = 11.sp) })
                Box {
                    IconButton({ addMenu = true }) { Icon(Icons.Default.AddCircle, "Add", tint = ZColors.Gold) }
                    DropdownMenu(addMenu, { addMenu = false }) {
                        DropdownMenuItem({ Text("Import media") }, { addMenu = false; picker.launch(arrayOf("video/*", "image/*", "audio/*")) })
                        DropdownMenuItem({ Text("Text") }, { addMenu = false; st.addText() })
                        DropdownMenuItem({ Text("Shape") }, { addMenu = false; st.addShape() })
                        DropdownMenuItem({ Text("Adjustment layer") }, { addMenu = false; st.addAdjustment() })
                    }
                }
            }
            TimelineView(st, pxPerMs, scrollMs, { scrollMs = it }, Modifier.fillMaxWidth().weight(0.27f))
            ScrollableTabRow(tab, containerColor = ZColors.Bg, edgePadding = 0.dp) {
                listOf("Layers", "Properties", "Effects", "Transitions").forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t, fontSize = 12.sp) }) }
            }
            Box(Modifier.fillMaxWidth().weight(0.35f).background(ZColors.Surface)) {
                when (tab) { 0 -> LayersPanel(st); 1 -> PropertiesPanel(st); 2 -> EffectsPanel(st); else -> TransitionPanel(st, tparams) { tparams = it } }
            }
        }
    }
}

private suspend fun withFrameMillisCompat(): Long = androidx.compose.runtime.withFrameMillis { it }
