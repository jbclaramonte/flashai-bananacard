package com.mobeezz.bananacard.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Default = Typography()

val BananaCardTypography = Typography(
    headlineMedium = Default.headlineMedium.copy(fontSize = 32.sp, fontWeight = FontWeight.Black),
    titleLarge = Default.titleLarge.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
    bodyMedium = Default.bodyMedium.copy(fontSize = 14.sp),
    labelMedium = Default.labelMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
)
