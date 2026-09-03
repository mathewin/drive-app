package com.example.calculadoraganhos.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

val VerdeNeon = Color(0xFF31F900)
val RosaLilas = Color(0xFFC864AF)

object AppTheme {
    var dark by mutableStateOf(true)
}

private val DarkScheme = darkColorScheme(
    primary = VerdeNeon,
    secondary = RosaLilas,
    background = Color(0xFF0B0C0E),
    surface = Color(0xFF141519),
    surfaceVariant = Color(0xFF1C1D22),
    onPrimary = Color(0xFF000000),
    onSecondary = Color(0xFF000000),
    onBackground = Color(0xFFE8E8E8),
    onSurface = Color(0xFFE8E8E8),
    onSurfaceVariant = Color(0xFF8A8A8A),
    outline = Color(0xFF33363D),
    outlineVariant = Color(0xFF222327)
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0E9F00),
    secondary = Color(0xFFA84A90),
    background = Color(0xFFF2F3F6),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE8EAEF),
    onPrimary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFFFFFFFF),
    onBackground = Color(0xFF191A1E),
    onSurface = Color(0xFF191A1E),
    onSurfaceVariant = Color(0xFF5F636E),
    outline = Color(0xFFC6C9D1),
    outlineVariant = Color(0xFFD5D8DF)
)

@Composable
fun DriveWinTheme(content: @Composable () -> Unit) {
    val scheme = if (AppTheme.dark) DarkScheme else LightScheme
    MaterialTheme(colorScheme = scheme, content = content)
}
