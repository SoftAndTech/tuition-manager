package com.softandtech.tuitionmanager.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Placeholder brand palette (indigo + amber); final colours are an open question in the plan.
private val Indigo = Color(0xFF3949AB)
private val IndigoLight = Color(0xFFB0BEFF)
private val Amber = Color(0xFFFFB300)

private val LightColors = lightColorScheme(
    primary = Indigo,
    secondary = Amber,
)

private val DarkColors = darkColorScheme(
    primary = IndigoLight,
    secondary = Amber,
)

@Composable
fun TuitionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
