package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SainiDarkColorScheme = darkColorScheme(
    primary = SunsetGold,
    onPrimary = CityObsidian,
    secondary = ElectricCyan,
    onSecondary = CityObsidian,
    tertiary = EmeraldPulse,
    background = CityObsidian,
    onBackground = TextPrimary,
    surface = CitySlate,
    onSurface = TextPrimary,
    surfaceVariant = CityCard,
    onSurfaceVariant = TextSecondary,
    outline = CityBorder,
    error = CrimsonAlert
)

@Composable
fun SainiSimulatorTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SainiDarkColorScheme,
        typography = SainiTypography,
        content = content
    )
}
