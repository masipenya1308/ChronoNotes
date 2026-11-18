package com.chrononotes.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val AccentColors = listOf(
    Color(0xFF5B7CFA),
    Color(0xFFE05C76),
    Color(0xFF20A676),
    Color(0xFFF5A524),
    Color(0xFF9B6DDE),
)

val TagColors = listOf(
    "#5B7CFA", "#E05C76", "#20A676", "#F5A524", "#9B6DDE", "#00A6B8",
)

private val SeedLight = lightColorScheme()
private val SeedDark = darkColorScheme()

val LightColors = SeedLight.copy(
    background = Color(0xFFF7F5F0),
    onBackground = Color(0xFF232323),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF232323),
    surfaceVariant = Color(0xFFEDEAE2),
    onSurfaceVariant = Color(0xFF5D5A54),
    outline = Color(0xFFC9C4B8),
)

val DarkColors = SeedDark.copy(
    background = Color(0xFF16181D),
    onBackground = Color(0xFFECEEF2),
    surface = Color(0xFF22252C),
    onSurface = Color(0xFFECEEF2),
    surfaceVariant = Color(0xFF2C3038),
    onSurfaceVariant = Color(0xFFB4BAC5),
    outline = Color(0xFF454B56),
)

fun hexToColor(hex: String, fallback: Color = AccentColors[0]): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrDefault(fallback)
