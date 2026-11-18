package com.chrononotes.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.material3.MaterialTheme
import com.chrononotes.app.data.AppSettings

@Composable
fun ChronoTheme(
    settings: AppSettings,
    content: @Composable () -> Unit,
) {
    val dark = when (settings.themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val accent = AccentColors[settings.accent.coerceIn(AccentColors.indices)]
    val scheme = if (dark) DarkColors else LightColors
    val colors = scheme.copy(
        primary = accent,
        onPrimary = Color.White,
        primaryContainer = accent.copy(alpha = 0.18f),
        onPrimaryContainer = if (dark) Color.White else accent,
        secondary = accent,
        onSecondary = Color.White,
    )
    val density = LocalDensity.current

    CompositionLocalProvider(
        LocalDensity provides Density(density.density, settings.fontScale.coerceIn(0.7f, 1.6f)),
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = AppTypography,
            content = content,
        )
    }
}

/** Утилита для подсветки поискового запроса в тексте. */
fun highlight(text: String, query: String, color: Color): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)
    val builder = AnnotatedString.Builder()
    var index = 0
    while (index < text.length) {
        val found = text.indexOf(query, index, ignoreCase = true)
        if (found < 0) {
            builder.append(text.substring(index))
            break
        }
        builder.append(text.substring(index, found))
        builder.pushStyle(androidx.compose.ui.text.SpanStyle(background = color.copy(alpha = 0.25f)))
        builder.append(text.substring(found, found + query.length))
        builder.pop()
        index = found + query.length
    }
    return builder.toAnnotatedString()
}
