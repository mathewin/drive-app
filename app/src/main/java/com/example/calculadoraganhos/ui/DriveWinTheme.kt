package com.example.calculadoraganhos.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val VerdeNeon = Color(0xFF31F900)
val RosaLilas = Color(0xFFC864AF)

private val DarkScheme = darkColorScheme(
    primary = VerdeNeon,
    secondary = RosaLilas,
    background = Color(0xFF0B0C0E),
    surface = Color(0xFF141519),
    surfaceVariant = Color(0xFF1C1D22),
    onPrimary = Color(0xFF000000),
    onSecondary = Color(0xFF000000),
    onBackground = Color(0xFFE8E8E8),
    onSurface = Color(0xFFE8E8E8)
)

@Composable
fun DriveWinTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, content = content)
}
