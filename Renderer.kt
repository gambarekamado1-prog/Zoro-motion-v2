package com.zoromotion.app.ui

import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.zoromotion.core.effects.*
import com.zoromotion.core.model.*
import kotlin.math.*

/**
 * CPU/Canvas preview renderer. It evaluates keyframes, effects and transitions from the core engine at timeline time `t`.
 * Blur/glow/RGB-split here are PREVIEW APPROXIMATIONS (trail copies / offset channel copies); the GPU export path is not built yet.
 */
data class Geo(val cx: Float, val cy: Float, val w: Float, val h: Float, val sx: Float, val sy: Float, val rot: Float, val ax: Float, val ay: Float)
data class DrawItem(val clip: Clip, val local: Long, val lf: LayerFx)

fun frameItems(p: Project, t: Long): Pair<List<DrawItem>, Float> {
    var flash = 0f
    val tw = p.transitions.mapNotNull { ti -> val a = p.clip(ti.fromClipId); val b = p.clip(ti.toClipId); if (a == null || b == null) null else Triple(ti, a, b) }
    val list = mutableListOf<DrawItem>()
    for (track in p.tracks) {
        if (track.hidden) continue
        for (c in track.clips) {
            if (!c.visible || c.type == TrackType.AUDIO) continue
            var lf = LayerFx(); var active = c.isActiveAt(t)
            for ((ti, a, b) in tw) {
                val d = ti.params.durationMs.coerceAtLeast(1); val s = b.startMs - d / 2
                if (t in s until s + d && (c.id == a.id || c.id == b.id)) {
                    val fr = TransitionEngine.evaluate(ti.params, (t - s).toFloat() / d, p.fps, (t * p.fps / 1000).toInt())
                    lf = if (c.id == a.id) fr.outgoing else fr.incoming; active = true; flash = max(flash, fr.flashAlpha)
                }
            }
            if (active) list += DrawItem(c, c.localTime(t).coerceAtLeast(0), lf)
        }
    }
    return list to flash
}

fun geometry(c: Clip, local: Long, fx: EffectOutput, lf: LayerFx, W: Float, H: Float): Geo {
    val tr = c.transform
    val z = fx.zoom * lf.zoom
    return Geo(tr.posX.valueAt(local) * W + (fx.dx + lf.dx) * W, tr.posY.valueAt(local) * H + (fx.dy + lf.dy) * H,
        c.widthN * W, c.heightN * H, tr.scaleX.valueAt(local) * z, tr.scaleY.valueAt(local) * z,
        tr.rotation.valueAt(local) + fx.rotationDeg + lf.rotationDeg, tr.anchorX.valueAt(local), tr.anchorY.valueAt(local))
}

private fun blend(m: String) = when (m) { "ADD" -> BlendMode.Plus; "SCREEN" -> BlendMode.Screen; "MULTIPLY" -> BlendMode.Multiply; else -> BlendMode.SrcOver }
val BlendModes = listOf("NORMAL", "ADD", "SCREEN", "MULTIPLY")

fun DrawScope.drawFrame(p: Project, t: Long, media: Map<String, ImageBitmap>, quality: Int = 3) {
    drawRect(Color.Black)
    val (items, tFlash) = frameItems(p, t)
    var flashA = tFlash; var flashC = 0xFFFFFFFF
    for (it in items) {
        val fx = EffectEngine.evaluateAll(it.clip.effects, it.local, p.fps)
        if (fx.flashAlpha > flashA) { flashA = fx.flashAlpha; flashC = fx.flashColor }
        drawClip(it.clip, it.local, fx, it.lf, media[it.clip.mediaId], quality)
    }
    if (flashA > 0f) drawRect(Color(flashC).copy(alpha = flashA.coerceIn(0f, 1f)))
}

private fun DrawScope.drawClip(c: Clip, local: Long, fx: EffectOutput, lf: LayerFx, bmp: ImageBitmap?, quality: Int) {
    val g = geometry(c, local, fx, lf, size.width, size.height)
    val alpha = (c.transform.opacity.valueAt(local) * lf.opacity).coerceIn(0f, 1f)
    if (alpha <= 0f) return
    withTransform({ translate(g.cx, g.cy); rotate(g.rot, Offset.Zero); scale(g.sx, g.sy, Offset.Zero) }) {
        val tl = Offset(-g.ax * g.w, -g.ay * g.h)
        fun layer(o: Offset, a: Float, ch: Int, bm: BlendMode) = drawContent(c, bmp, tl + o, g.w, g.h, a, ch, bm)
        if (fx.rgbSplitPx > 0f) {
            val r = Math.toRadians(fx.rgbSplitAngle.toDouble()); val o = Offset((cos(r) * fx.rgbSplitPx).toFloat(), (sin(r) * fx.rgbSplitPx).toFloat())
            layer(o, alpha, 0, BlendMode.Plus); layer(Offset.Zero, alpha, 1, BlendMode.Plus); layer(Offset(-o.x, -o.y), alpha, 2, BlendMode.Plus)
        } else {
            val n = EffectEngine.blurSamples(quality); val blur = max(fx.blurPx, lf.blurPx)
            if (blur > 0f && n > 0) {
                val r = Math.toRadians(fx.blurAngle.toDouble())
                for (i in n downTo 1) { val k = blur * i / n; layer(Offset((cos(r) * k).toFloat(), (sin(r) * k).toFloat()), alpha * 0.5f / n, -1, BlendMode.SrcOver) }
            }
            if (fx.glowStrength > 0f && n > 0) for (k in 0 until 6) {
                val a = k * PI / 3; layer(Offset((cos(a) * fx.glowRadius).toFloat(), (sin(a) * fx.glowRadius).toFloat()), (alpha * fx.glowStrength * 0.18f).coerceAtMost(1f), -1, BlendMode.Plus)
            }
            layer(Offset.Zero, alpha, -1, blend(c.blendMode))
        }
    }
}

private fun chan(c: Color, ch: Int) = when (ch) { 0 -> Color(c.red, 0f, 0f, c.alpha); 1 -> Color(0f, c.green, 0f, c.alpha); 2 -> Color(0f, 0f, c.blue, c.alpha); else -> c }
private fun chanFilter(ch: Int): ColorFilter? = when (ch) {
    0 -> ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
    1 -> ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
    2 -> ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
    else -> null
}

private fun DrawScope.drawContent(c: Clip, bmp: ImageBitmap?, tl: Offset, w: Float, h: Float, a: Float, ch: Int, bm: BlendMode) {
    val ts = c.text
    when {
        ts != null -> drawIntoCanvas { cv ->
            val col = chan(Color(ts.colorArgb.toInt()), ch)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = col.toArgb(); alpha = (a * col.alpha * 255).toInt().coerceIn(0, 255); textSize = ts.sizeN * size.height
                isFakeBoldText = ts.bold; if (bm == BlendMode.Plus) xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
                textAlign = when (ts.align) { "LEFT" -> Paint.Align.LEFT; "RIGHT" -> Paint.Align.RIGHT; else -> Paint.Align.CENTER }
            }
            val x = when (ts.align) { "LEFT" -> tl.x; "RIGHT" -> tl.x + w; else -> tl.x + w / 2 }
            cv.nativeCanvas.drawText(ts.text, x, tl.y + h / 2 + paint.textSize / 3, paint)
        }
        bmp != null && c.type != TrackType.ADJUSTMENT -> drawImage(bmp, IntOffset.Zero, IntSize(bmp.width, bmp.height),
            IntOffset(tl.x.roundToInt(), tl.y.roundToInt()), IntSize(w.roundToInt(), h.roundToInt()), a, androidx.compose.ui.graphics.drawscope.Fill, chanFilter(ch), bm)
        c.type == TrackType.ADJUSTMENT -> drawRect(Color(0x33FFFFFF), tl, Size(w, h), alpha = a)
        else -> drawRect(chan(Color(c.colorArgb.toInt()), ch), tl, Size(w, h), alpha = a, blendMode = bm)
    }
}
