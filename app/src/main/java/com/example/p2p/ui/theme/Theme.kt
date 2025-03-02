package com.example.p2papp.ui.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun P2PAppTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    val colors = darkColors(
        primary = Color(0xFF388E3C),    // Dark green for buttons, titles
        secondary = Color(0xFF81C784),  // Light green for progress bars, accents
        background = Color(0xFF212121)  // Dark gray background
    )
    MaterialTheme(colors = colors, content = content)
}
