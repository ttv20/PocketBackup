package com.ttv20.rsyncbackup.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RsyncColorScheme = lightColorScheme(
    primary = Color(0xFF2F6F73),
    onPrimary = Color.White,
    secondary = Color(0xFF586B63),
    tertiary = Color(0xFF8A6A12),
    surface = Color(0xFFFBFCFA),
    surfaceVariant = Color(0xFFE7EFEC),
    background = Color(0xFFF7FAF9),
    error = Color(0xFFB3261E),
)

@Composable
fun RsyncBackupTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RsyncColorScheme,
        content = content,
    )
}
