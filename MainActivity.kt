package com.zoromotion.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zoromotion.app.data.Prefs
import com.zoromotion.app.ui.*
import com.zoromotion.core.io.LoadResult
import com.zoromotion.core.io.ProjectStore
import java.io.File

private sealed interface Screen { object Home : Screen; object Settings : Screen; data class Editor(val st: EditorState) : Screen; data class Export(val st: EditorState) : Screen }

class MainActivity : ComponentActivity() {
    /** True if the persisted URI can still be opened (file moved/deleted or permission revoked -> false). */
    private fun mediaReadable(uri: String) = try { contentResolver.openFileDescriptor(android.net.Uri.parse(uri), "r")?.use { true } ?: false } catch (e: Exception) { false }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = ProjectStore(File(filesDir, "projects")); val prefs = Prefs(this)
        setContent {
            ZoroTheme {
                var screen by remember { mutableStateOf<Screen>(Screen.Home) }
                var error by remember { mutableStateOf<String?>(null) }
                Box(Modifier.fillMaxSize().background(ZColors.Bg)) {
                    when (val s = screen) {
                        Screen.Home -> HomeScreen(store, onOpen = { id ->
                            error = null
                            when (val r = store.load(id) { m -> mediaReadable(m.uri) }) {
                                is LoadResult.Ok -> { val st = EditorState(r.project, store)
                                    if (r.missingMedia.isNotEmpty()) st.message = "${r.missingMedia.size} media file(s) missing"
                                    r.warnings.firstOrNull()?.let { st.message = it }; screen = Screen.Editor(st) }
                                is LoadResult.Failed -> error = r.reason
                            }
                        }, onSettings = { screen = Screen.Settings })
                        Screen.Settings -> ExportScreen(prefs.exportSettings(), prefs::save) { screen = Screen.Home }
                        is Screen.Editor -> EditorScreen(s.st, { screen = Screen.Home }, { screen = Screen.Export(s.st) })
                        is Screen.Export -> ExportScreen(prefs.exportSettings(), prefs::save) { screen = Screen.Editor(s.st) }
                    }
                    error?.let { Text("Couldn't open project: $it", Modifier.padding(16.dp)) }
                }
            }
        }
    }
}
