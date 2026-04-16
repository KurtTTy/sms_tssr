package com.example.isdp2java.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = GreenPrimary,
    secondary = BluePrimary,
    tertiary = GreenLight,
    background = Color.Black,
    surface = Color(0xFF121212)
)

private val LightColorScheme = lightColorScheme(
    primary = GreenPrimary,
    secondary = BluePrimary,
    tertiary = BlueLight,
    background = Color.White,
    surface = Color.White
)

@Composable
fun ISDP2JAVATheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}