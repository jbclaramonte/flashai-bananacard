package com.mobeezz.bananacard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    secondary = BrandGreen,
    onSecondary = Color.White,
    tertiary = StreakOrange,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceContainer = LightSurface,
    secondaryContainer = Color(0xFFDDF4FF),
)

private val DarkColors = darkColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    secondary = BrandGreen,
    onSecondary = Color.White,
    tertiary = StreakOrange,
    onTertiary = Color.White,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceContainer = DarkSurface,
    secondaryContainer = Color(0xFF263A43),
)

private val BananaCardShapes = Shapes(medium = RoundedCornerShape(16.dp))

/** App theme. Dynamic color is deliberately not used. */
@Composable
fun BananaCardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = BananaCardTypography,
        shapes = BananaCardShapes,
        content = content,
    )
}
