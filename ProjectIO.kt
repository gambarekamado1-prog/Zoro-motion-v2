package com.zoromotion.core.io

import com.zoromotion.core.model.*
import kotlinx.serialization.json.Json
import java.io.File

sealed interface LoadResult {
    data class Ok(val project: Project, val warnings: List<String> = emptyList(), val missingMedia: List<MediaRef> = emptyList()) : LoadResult
    data class Failed(val reason: String) : LoadResult
}

object ProjectIO {
    val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }

    fun encode(p: Project): String = json.encodeToString(Project.serializer(), p.copy(formatVersion = PROJECT_FORMAT_VERSION))

    /** Parses, version-checks, sanitises, and reports missing media. `mediaExists` is supplied by the platform (SAF/File). */
    fun decode(text: String, mediaExists: (MediaRef) -> Boolean = { true }): LoadResult {
        val p = try { json.decodeFromString(Project.serializer(), text) } catch (e: Exception) { return LoadResult.Failed("Project file is corrupt: ${e.message?.take(120)}") }
        if (p.formatVersion > PROJECT_FORMAT_VERSION) return LoadResult.Failed("Project was made with a newer Zoro Motion (format ${p.formatVersion}).")
        val warnings = mutableListOf<String>()
        val clean = sanitize(migrate(p), warnings)
        return LoadResult.Ok(clean, warnings, clean.media.filter { !mediaExists(it) })
    }

    /** Hook for future format changes: upgrade older versions step by step. */
    private fun migrate(p: Project): Project = p

    fun sanitize(p: Project, warnings: MutableList<String> = mutableListOf()): Project {
        val ids = mutableSetOf<String>()
        val tracks = p.tracks.map { t ->
            t.copy(clips = t.clips.mapNotNull { c ->
                when {
                    c.durationMs <= 0 -> { warnings += "Removed empty clip '${c.name}'"; null }
                    !ids.add(c.id) -> { warnings += "Removed duplicate clip id '${c.name}'"; null }
                    else -> c.copy(startMs = c.startMs.coerceAtLeast(0), trimInMs = c.trimInMs.coerceAtLeast(0), volume = c.volume.coerceIn(0f, 4f),
                        transform = c.transform.copy(opacity = c.transform.opacity.copy(base = c.transform.opacity.base.coerceIn(0f, 1f))))
                }
            })
        }
        val valid = tracks.flatMap { it.clips }.map { it.id }.toSet()
        return p.copy(width = p.width.coerceIn(16, 4096), height = p.height.coerceIn(16, 4096), fps = p.fps.coerceIn(1, 120),
            tracks = tracks, transitions = p.transitions.filter { it.fromClipId in valid && it.toClipId in valid })
    }
}

/** Atomic file store: writes temp -> backup old -> rename. Loading falls back to the .bak if the main file is corrupt. */
class ProjectStore(private val dir: File) {
    init { dir.mkdirs() }
    private fun file(id: String) = File(dir, "$id.zoro.json")

    fun save(p: Project) {
        val f = file(p.id); val tmp = File(dir, "${p.id}.tmp"); val bak = File(dir, "${p.id}.bak")
        tmp.writeText(ProjectIO.encode(p))
        if (f.exists()) f.copyTo(bak, overwrite = true)
        if (!tmp.renameTo(f)) { tmp.copyTo(f, overwrite = true); tmp.delete() }
    }

    fun load(id: String, mediaExists: (MediaRef) -> Boolean = { true }): LoadResult {
        val f = file(id)
        if (!f.exists()) return LoadResult.Failed("Project not found")
        val r = ProjectIO.decode(f.readText(), mediaExists)
        if (r is LoadResult.Ok) return r
        val bak = File(dir, "$id.bak")
        if (bak.exists()) {
            val b = ProjectIO.decode(bak.readText(), mediaExists)
            if (b is LoadResult.Ok) return b.copy(warnings = b.warnings + "Recovered from backup; latest changes may be lost.")
        }
        return r
    }

    fun list(): List<Project> = (dir.listFiles { x -> x.name.endsWith(".zoro.json") } ?: emptyArray())
        .mapNotNull { (ProjectIO.decode(it.readText()) as? LoadResult.Ok)?.project }.sortedByDescending { it.modifiedAtMs }

    fun delete(id: String) { file(id).delete(); File(dir, "$id.bak").delete() }
}
