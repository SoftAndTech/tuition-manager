package com.softandtech.tuitionmanager.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Placeholder brand palette (indigo); final colours are an open question in the plan.
// Material uses `secondary` for text (e.g. the selected tab label), so it must stay dark enough to read.
private val Indigo = Color(0xFF3949AB)
private val IndigoDark = Color(0xFF283593)
private val IndigoLight = Color(0xFFB0BEFF)
private val IndigoPale = Color(0xFFC5CAE9)

private val LightColors = lightColorScheme(
    primary = Indigo,
    secondary = IndigoDark,
)

private val DarkColors = darkColorScheme(
    primary = IndigoLight,
    secondary = IndigoPale,
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
