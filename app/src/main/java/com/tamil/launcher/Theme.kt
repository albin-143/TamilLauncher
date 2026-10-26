package com.tamil.launcher

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

private fun Typography.withFont(f: FontFamily) = Typography(
    displayLarge = displayLarge.copy(fontFamily = f), displayMedium = displayMedium.copy(fontFamily = f),
    displaySmall = displaySmall.copy(fontFamily = f), headlineLarge = headlineLarge.copy(fontFamily = f),
    headlineMedium = headlineMedium.copy(fontFamily = f), headlineSmall = headlineSmall.copy(fontFamily = f),
    titleLarge = titleLarge.copy(fontFamily = f), titleMedium = titleMedium.copy(fontFamily = f),
    titleSmall = titleSmall.copy(fontFamily = f), bodyLarge = bodyLarge.copy(fontFamily = f),
    bodyMedium = bodyMedium.copy(fontFamily = f), bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f), labelMedium = labelMedium.copy(fontFamily = f),
    labelSmall = labelSmall.copy(fontFamily = f)
)

@Composable
fun LauncherTheme(vm: LauncherViewModel, content: @Composable () -> Unit) {
    val dark = when (vm.theme) { 0 -> isSystemInDarkTheme(); 1 -> false; else -> true }
    val accent = Color(vm.accent)
    var cs = if (dark) darkColorScheme(primary = accent) else lightColorScheme(primary = accent)
    if (vm.theme == 3) cs = cs.copy(background = Color.Black, surface = Color.Black, surfaceVariant = Color(0xFF121212))
    val ff = when (vm.fontIdx) { 1 -> FontFamily.Serif; 2 -> FontFamily.Monospace; 3 -> FontFamily.Cursive; else -> FontFamily.Default }
    MaterialTheme(colorScheme = cs, typography = Typography().withFont(ff), content = content)
}
