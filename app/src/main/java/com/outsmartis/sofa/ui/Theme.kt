package com.outsmartis.sofa.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFFFF8A3D)
val Surface1 = Color(0xFF16181D)
val Surface2 = Color(0xFF22252C)
val Surface3 = Color(0xFF2E323B)
val TextDim = Color(0xFF9AA0AA)
val Ok = Color(0xFF4ADE80)
val Warn = Color(0xFFFBBF24)
val Bad = Color(0xFFF87171)

@Composable
fun SofaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Color(0xFF1A0E05),
            background = Color(0xFF0E0F12),
            surface = Surface1,
            surfaceVariant = Surface2,
            surfaceContainer = Surface1,
            surfaceContainerHigh = Surface2,
            onSurface = Color(0xFFECEEF2),
            onSurfaceVariant = TextDim,
            outline = Surface3,
        ),
        content = content,
    )
}
