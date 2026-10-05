package com.prempoddar.campfire.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.prempoddar.campfire.R
import com.prempoddar.campfire.host.ThemeMode

// Pine & ember: the same palette as the browser chat.
val Ember = Color(0xFFE8772E)
val EmberDark = Color(0xFFFF9A52)

private val Light = lightColorScheme(
    primary = Color(0xFF1F6F5C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDEFE8),
    onPrimaryContainer = Color(0xFF0B3A2F),
    secondary = Ember,
    onSecondary = Color.White,
    background = Color(0xFFF3F5F4),
    onBackground = Color(0xFF0F1714),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F1714),
    surfaceVariant = Color(0xFFEEF2F0),
    onSurfaceVariant = Color(0xFF5E6B66),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFEEF2F0),
    outline = Color(0xFFD3DBD7),
    outlineVariant = Color(0xFFE1E7E4),
    error = Color(0xFFC2364B),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF5ACFAE),
    onPrimary = Color(0xFF062019),
    primaryContainer = Color(0xFF173A31),
    onPrimaryContainer = Color(0xFFCDEFE3),
    secondary = EmberDark,
    onSecondary = Color(0xFF2E1300),
    background = Color(0xFF0D1412),
    onBackground = Color(0xFFE4EDE9),
    surface = Color(0xFF141D1A),
    onSurface = Color(0xFFE4EDE9),
    surfaceVariant = Color(0xFF1A2622),
    onSurfaceVariant = Color(0xFF8FA19A),
    surfaceContainer = Color(0xFF141D1A),
    surfaceContainerHigh = Color(0xFF1A2622),
    surfaceContainerHighest = Color(0xFF22302B),
    outline = Color(0xFF2E3D37),
    outlineVariant = Color(0xFF22302B),
    error = Color(0xFFFF7D90),
)

@OptIn(ExperimentalTextApi::class)
private fun figtree(weight: Int) = Font(
    R.font.figtree, weight = FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private val Figtree = FontFamily(figtree(400), figtree(600), figtree(750))

private val Type = Typography(
    displaySmall = TextStyle(fontFamily = Figtree, fontWeight = FontWeight(750), fontSize = 32.sp, letterSpacing = (-0.02).em),
    headlineMedium = TextStyle(fontFamily = Figtree, fontWeight = FontWeight(750), fontSize = 26.sp, letterSpacing = (-0.01).em),
    headlineSmall = TextStyle(fontFamily = Figtree, fontWeight = FontWeight(750), fontSize = 22.sp),
    titleLarge = TextStyle(fontFamily = Figtree, fontWeight = FontWeight(750), fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = Figtree, fontWeight = FontWeight(600), fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = Figtree, fontWeight = FontWeight(600), fontSize = 14.sp),
    bodyLarge = TextStyle(fontFamily = Figtree, fontWeight = FontWeight(400), fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Figtree, fontWeight = FontWeight(400), fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Figtree, fontWeight = FontWeight(400), fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Figtree, fontWeight = FontWeight(600), fontSize = 15.sp),
    labelMedium = TextStyle(fontFamily = Figtree, fontWeight = FontWeight(600), fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = Figtree, fontWeight = FontWeight(750), fontSize = 11.sp, letterSpacing = 0.06.em),
)

private val ShapeSet = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun isDark(mode: ThemeMode) = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun CampfireTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isDark(mode)) Dark else Light, typography = Type, shapes = ShapeSet, content = content)
}
