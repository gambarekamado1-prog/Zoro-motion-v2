package com.zoromotion.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zoromotion.app.data.ExportSettings

/**
 * Export settings UI. The encoder (MediaCodec + OpenGL compositing) is NOT implemented yet (Milestone 8),
 * so the start button is disabled and says why rather than pretending to export.
 */
@Composable
fun ExportScreen(initial: ExportSettings, onSave: (ExportSettings) -> Unit, onBack: () -> Unit) {
    var s by remember { mutableStateOf(initial) }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            IconButton({ onSave(s); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Export settings", fontSize = 20.sp)
        }
        Text("Resolution (short side)", color = ZColors.Muted, modifier = Modifier.padding(top = 12.dp))
        ChipRow(listOf(720, 1080, 1440), s.height, { "${it}p" }) { s = s.copy(height = it) }
        Text("Frame rate", color = ZColors.Muted, modifier = Modifier.padding(top = 12.dp))
        ChipRow(listOf(24, 30, 60), s.fps, { "$it fps" }) { s = s.copy(fps = it) }
        Text("Quality", color = ZColors.Muted, modifier = Modifier.padding(top = 12.dp))
        ChipRow(listOf("Draft", "High", "Max"), s.quality, { it }) { s = s.copy(quality = it) }
        ParamRow("Bitrate (Mbps)", s.bitrateMbps.toFloat(), 2f, 60f, { s = s.copy(bitrateMbps = it.toInt()) }, {})
        Spacer(Modifier.height(16.dp))
        Card(colors = CardDefaults.cardColors(containerColor = ZColors.Surface2)) {
            Text("Video export is not implemented yet. These preferences are saved for when the MediaCodec/OpenGL pipeline lands (Milestone 8). " +
                "Progress, cancellation and error reporting will be wired to that pipeline.", Modifier.padding(12.dp), fontSize = 13.sp)
        }
        Spacer(Modifier.height(12.dp))
        Button({}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("Start export (coming in Milestone 8)") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton({ onSave(s); onBack() }, Modifier.fillMaxWidth()) { Text("Save preferences") }
    }
}
