package com.prempoddar.campfire.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.prempoddar.campfire.R
import com.prempoddar.campfire.ui.design.AppTheme
import com.prempoddar.campfire.ui.design.Appearance

/** Campfire's own colour: ember, like the flame in the icon. */
const val CAMPFIRE_BRAND = 0xE8763A

@OptIn(ExperimentalTextApi::class)
private fun figtree(weight: Int) = Font(
    R.font.figtree, weight = FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private val Figtree = FontFamily(figtree(400), figtree(600), figtree(750))

private val Type = Typography(
    displayMedium = TextStyle(fontFamily = Figtree, fontWeight = FontWeight(750), fontSize = 44.sp, letterSpacing = (-0.02).em, lineHeight = 48.sp),
    displaySmall = TextStyle(fontFamily = Figtree, fontWeight = FontWeight(750), fontSize = 34.sp, letterSpacing = (-0.02).em, lineHeight = 40.sp),
    headlineLarge = TextStyle(fontFamily = Figtree, fontWeight = FontWeight(750), fontSize = 30.sp, letterSpacing = (-0.01).em),
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

@Composable
fun CampfireTheme(appearance: Appearance, content: @Composable () -> Unit) = AppTheme(appearance, CAMPFIRE_BRAND, Type, content)
