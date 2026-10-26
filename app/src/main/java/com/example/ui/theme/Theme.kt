package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightBotanicalColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = OnEmeraldPrimary,
    primaryContainer = MintPrimaryContainer,
    onPrimaryContainer = OnMintPrimaryContainer,
    secondary = SunbeamAmberSecondary,
    onSecondary = OnSunbeamSecondary,
    secondaryContainer = SunbeamSecondaryContainer,
    onSecondaryContainer = OnSunbeamSecondaryContainer,
    tertiary = DewdropTealTertiary,
    onTertiary = OnDewdropTertiary,
    tertiaryContainer = DewdropTertiaryContainer,
    onTertiaryContainer = OnDewdropTertiaryContainer,
    background = SunlitDewBackground,
    onBackground = DeepForestInk,
    surface = SunlitDewBackground,
    onSurface = DeepForestInk,
    surfaceVariant = SoftSageSurface,
    onSurfaceVariant = MutedMossText,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F9EE),
    surfaceContainer = SoftSageSurface,
    surfaceContainerHigh = BotanicalMistContainer,
    surfaceContainerHighest = Color(0xFFCFE8C9),
    outline = LeafVeinOutline,
    outlineVariant = Color(0xFFD5EAD0),
    error = BotanicalCoralAccent,
    onError = Color.White
)

private val DarkBotanicalColorScheme = darkColorScheme(
    primary = BrightChlorophyllPrimary,
    onPrimary = OnBrightChlorophyll,
    primaryContainer = DarkEmeraldContainer,
    onPrimaryContainer = OnDarkEmeraldContainer,
    secondary = WarmPollenSecondary,
    onSecondary = OnWarmPollen,
    secondaryContainer = DarkAmberContainer,
    onSecondaryContainer = Color(0xFFFFDDB7),
    tertiary = AquaBiolumTertiary,
    onTertiary = Color(0xFF003640),
    tertiaryContainer = Color(0xFF004E5C),
    onTertiaryContainer = Color(0xFFC4F1F9),
    background = DarkMossBackground,
    onBackground = DarkMistText,
    surface = DarkMossBackground,
    onSurface = DarkMistText,
    surfaceVariant = DarkCanopySurface,
    onSurfaceVariant = DarkMutedSage,
    surfaceContainerLowest = Color(0xFF09120D),
    surfaceContainerLow = DarkCanopySurface,
    surfaceContainer = DarkCardContainer,
    surfaceContainerHigh = Color(0xFF254030),
    surfaceContainerHighest = Color(0xFF2F503C),
    outline = DarkVeinOutline,
    outlineVariant = Color(0xFF223B2C),
    error = Color(0xFFFFB4A8),
    onError = Color(0xFF690005)
)

val LeafyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun LeafyTheme(
    darkTheme: Boolean = false,
    useComicSans: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkBotanicalColorScheme else LightBotanicalColorScheme
    val typography = buildLeafyTypography(useComicSans = useComicSans)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = LeafyShapes,
        content = content
    )
}
