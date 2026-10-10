package com.zoromotion.app.data

import android.content.Context

data class ExportSettings(val height: Int = 1080, val fps: Int = 30, val bitrateMbps: Int = 12, val quality: String = "High")

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("zoro", Context.MODE_PRIVATE)
    fun exportSettings() = ExportSettings(sp.getInt("h", 1080), sp.getInt("fps", 30), sp.getInt("br", 12), sp.getString("q", "High") ?: "High")
    fun save(s: ExportSettings) = sp.edit().putInt("h", s.height).putInt("fps", s.fps).putInt("br", s.bitrateMbps).putString("q", s.quality).apply()
}
