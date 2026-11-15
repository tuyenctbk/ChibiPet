package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = MintPrimary,
    onPrimary = MintOnPrimary,
    primaryContainer = MintPrimaryContainer,
    onPrimaryContainer = MintOnPrimaryContainer,
    secondary = PeachSecondary,
    onSecondary = PeachOnSecondary,
    secondaryContainer = PeachSecondaryContainer,
    onSecondaryContainer = PeachOnSecondaryContainer,
    tertiary = LavenderTertiary,
    onTertiary = LavenderOnTertiary,
    tertiaryContainer = LavenderTertiaryContainer,
    onTertiaryContainer = LavenderOnTertiaryContainer,
    background = CreamBackground,
    onBackground = CocoaOnBackground,
    surface = IvorySurface,
    onSurface = CocoaOnBackground,
    surfaceVariant = WarmSandSurfaceVariant,
    onSurfaceVariant = MutedCocoa,
    outline = WarmOutline,
    error = ErrorCoral,
    errorContainer = ErrorContainerLight
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkMintPrimary,
    onPrimary = DarkMintOnPrimary,
    primaryContainer = DarkMintContainer,
    onPrimaryContainer = DarkMintOnContainer,
    secondary = DarkPeachSecondary,
    onSecondary = DarkPeachOnSecondary,
    secondaryContainer = DarkPeachContainer,
    onSecondaryContainer = DarkPeachOnContainer,
    tertiary = DarkLavenderTertiary,
    onTertiary = DarkLavenderOnTertiary,
    tertiaryContainer = DarkLavenderContainer,
    onTertiaryContainer = DarkLavenderOnContainer,
    background = TwilightBackground,
    onBackground = TwilightOnBackground,
    surface = TwilightSurface,
    onSurface = TwilightOnBackground,
    surfaceVariant = TwilightSurfaceVariant,
    onSurfaceVariant = TwilightMuted,
    outline = TwilightOutline,
    error = ErrorCoral
)

private val ChibiShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

@Composable
fun ChibiPetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = ChibiShapes,
        content = content
    )
}
