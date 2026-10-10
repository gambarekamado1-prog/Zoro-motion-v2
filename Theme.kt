package com.zoromotion.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object ZColors {
    val Bg = Color(0xFF0D0D0F); val Surface = Color(0xFF18181B); val Surface2 = Color(0xFF232327)
    val Gold = Color(0xFFD4AF37); val Text = Color(0xFFF2F2F2); val Muted = Color(0xFF9A9AA2); val Danger = Color(0xFFE5484D)
}
val ZShape = RoundedCornerShape(12.dp)

@Composable
fun ZoroTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(primary = ZColors.Gold, onPrimary = Color.Black, background = ZColors.Bg, surface = ZColors.Surface,
        onSurface = ZColors.Text, onBackground = ZColors.Text, surfaceVariant = ZColors.Surface2, onSurfaceVariant = ZColors.Muted,
        secondary = ZColors.Gold, error = ZColors.Danger),
    content = content)
