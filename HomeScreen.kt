package com.zoromotion.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zoromotion.core.io.ProjectStore
import com.zoromotion.core.model.Project
import com.zoromotion.core.timeline.newId

private val Aspects = listOf("9:16" to (1080 to 1920), "16:9" to (1920 to 1080), "1:1" to (1080 to 1080), "4:5" to (1080 to 1350))

@Composable
fun HomeScreen(store: ProjectStore, onOpen: (String) -> Unit, onSettings: () -> Unit) {
    val ctx = LocalContext.current
    var projects by remember { mutableStateOf(store.list()) }
    fun refresh() { projects = store.list() }
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Project?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    val media = remember { mutableStateMapOf<String, androidx.compose.ui.graphics.ImageBitmap>() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val p = Project(newId(), "Imported project", modifiedAtMs = System.currentTimeMillis())
            val st = EditorState(p, store); st.addMedia(ctx, uris); st.save(); refresh(); onOpen(p.id)
        }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("ZORO", fontSize = 30.sp, fontWeight = FontWeight.Black, color = ZColors.Gold, letterSpacing = 6.sp)
                Text("MOTION", fontSize = 14.sp, letterSpacing = 10.sp, color = ZColors.Muted)
            }
            IconButton(onSettings) { Icon(Icons.Default.Settings, "Settings") }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ creating = true }, Modifier.weight(1f)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Create project") }
            OutlinedButton({ picker.launch(arrayOf("video/*", "image/*", "audio/*")) }, Modifier.weight(1f)) { Icon(Icons.Default.FileOpen, null); Spacer(Modifier.width(6.dp)); Text("Import media") }
        }
        Text("Recent projects", Modifier.padding(top = 20.dp, bottom = 8.dp), color = ZColors.Muted)
        if (projects.isEmpty()) Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No projects yet", color = ZColors.Muted) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(projects, key = { it.id }) { p ->
                Row(Modifier.fillMaxWidth().clip(ZShape).background(ZColors.Surface, ZShape).clickable { onOpen(p.id) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    val ar = p.width.toFloat() / p.height
                    Canvas(Modifier.height(72.dp).aspectRatio(ar.coerceIn(0.5f, 1.8f)).clip(RoundedCornerShape(8.dp))) { drawFrame(p, 0, media) }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(p.name, maxLines = 1); Text("${p.width}×${p.height} · ${p.fps}fps · ${p.allClips.size} clips", fontSize = 12.sp, color = ZColors.Muted)
                    }
                    Box {
                        IconButton({ menuFor = p.id }) { Icon(Icons.Default.MoreVert, "Actions") }
                        DropdownMenu(menuFor == p.id, { menuFor = null }) {
                            DropdownMenuItem({ Text("Rename") }, { renaming = p; menuFor = null })
                            DropdownMenuItem({ Text("Duplicate") }, { store.save(p.copy(id = newId(), name = p.name + " copy", modifiedAtMs = System.currentTimeMillis())); refresh(); menuFor = null })
                            DropdownMenuItem({ Text("Delete", color = ZColors.Danger) }, { store.delete(p.id); refresh(); menuFor = null })
                        }
                    }
                }
            }
        }
    }
    if (creating) {
        var name by remember { mutableStateOf("My edit") }; var asp by remember { mutableStateOf(Aspects[0]) }; var fps by remember { mutableIntStateOf(30) }
        AlertDialog({ creating = false }, title = { Text("New project") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") })
                ChipRow(Aspects, asp, { it.first }) { asp = it }
                ChipRow(listOf(24, 30, 60), fps, { "$it fps" }) { fps = it }
            }
        }, confirmButton = { TextButton({
            val p = Project(newId(), name.ifBlank { "Untitled" }, asp.second.first, asp.second.second, fps, modifiedAtMs = System.currentTimeMillis())
            store.save(p); creating = false; refresh(); onOpen(p.id)
        }) { Text("Create") } }, dismissButton = { TextButton({ creating = false }) { Text("Cancel") } })
    }
    renaming?.let { p -> RenameDialog(p.name, { renaming = null }) { n -> store.save(p.copy(name = n, modifiedAtMs = System.currentTimeMillis())); renaming = null; refresh() } }
}

