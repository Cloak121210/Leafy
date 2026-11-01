package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.example.R

val QuicksandFontFamily = FontFamily(
    Font(R.font.quicksand, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.quicksand_500, FontWeight.Medium, FontStyle.Normal),
    Font(R.font.quicksand_600, FontWeight.SemiBold, FontStyle.Normal),
    Font(R.font.quicksand_700, FontWeight.Bold, FontStyle.Normal)
)

val NunitoFontFamily = FontFamily(
    Font(R.font.nunito, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.nunito_400italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.nunito_500, FontWeight.Medium, FontStyle.Normal),
    Font(R.font.nunito_600, FontWeight.SemiBold, FontStyle.Normal),
    Font(R.font.nunito_700, FontWeight.Bold, FontStyle.Normal),
    Font(R.font.nunito_700italic, FontWeight.Bold, FontStyle.Italic)
)

val ComicSansFontFamily = FontFamily(
    Font(R.font.comic_neue, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.comic_neue_400italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.comic_neue, FontWeight.Medium, FontStyle.Normal),
    Font(R.font.comic_neue_700, FontWeight.SemiBold, FontStyle.Normal),
    Font(R.font.comic_neue_700, FontWeight.Bold, FontStyle.Normal),
    Font(R.font.comic_neue_700italic, FontWeight.Bold, FontStyle.Italic)
)

val JetBrainsMonoFontFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.jetbrains_mono_400italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.jetbrains_mono_500, FontWeight.Medium, FontStyle.Normal),
    Font(R.font.jetbrains_mono_600, FontWeight.SemiBold, FontStyle.Normal),
    Font(R.font.jetbrains_mono_700, FontWeight.Bold, FontStyle.Normal)
)

private val CompactPlatformStyle = PlatformTextStyle(includeFontPadding = false)
private val CompactLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

fun buildImportantTextGradientBrush(isDarkTheme: Boolean): Brush {
    return if (isDarkTheme) {
        Brush.linearGradient(
            colors = listOf(
                GradientTextDarkStart,
                GradientTextDarkMid,
                GradientTextDarkEnd
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                GradientTextLightStart,
                GradientTextLightMid,
                GradientTextLightEnd
            )
        )
    }
}

/**
 * Leafy Typography Scale with strict 4-tier visual hierarchy and compact vertical metrics:
 * - Level 1 (Screen & Hero Titles: displayLarge, displayMedium, headlineLarge, titleLarge): FontWeight.Bold (700)
 * - Level 2 (Section & Card Headings: headlineMedium, titleMedium): FontWeight.SemiBold (600)
 * - Level 3 (Sub-headings, Item Titles & Primary Actions: titleSmall, labelLarge): FontWeight.Medium (500)
 * - Level 4 (Badges, Telemetry & Body Copy: labelMedium = Medium, labelSmall/body* = Normal)
 */
fun buildLeafyTypography(
    useComicSans: Boolean,
    useGradientText: Boolean = false,
    isDarkTheme: Boolean = false
): Typography {
    val displayFamily = if (useComicSans) ComicSansFontFamily else QuicksandFontFamily
    val bodyFamily = if (useComicSans) ComicSansFontFamily else NunitoFontFamily
    val headingBrush = if (useGradientText) buildImportantTextGradientBrush(isDarkTheme) else null

    fun applyHeadingBrushIfEnabled(style: TextStyle): TextStyle {
        return if (headingBrush != null) style.copy(brush = headingBrush) else style
    }

    return Typography(
        displayLarge = applyHeadingBrushIfEnabled(
            TextStyle(
                fontFamily = displayFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                lineHeight = 34.sp,
                letterSpacing = (-0.2).sp,
                platformStyle = CompactPlatformStyle,
                lineHeightStyle = CompactLineHeightStyle
            )
        ),
        displayMedium = applyHeadingBrushIfEnabled(
            TextStyle(
                fontFamily = displayFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                lineHeight = 28.sp,
                platformStyle = CompactPlatformStyle,
                lineHeightStyle = CompactLineHeightStyle
            )
        ),
        headlineLarge = applyHeadingBrushIfEnabled(
            TextStyle(
                fontFamily = displayFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                lineHeight = 24.sp,
                platformStyle = CompactPlatformStyle,
                lineHeightStyle = CompactLineHeightStyle
            )
        ),
        headlineMedium = applyHeadingBrushIfEnabled(
            TextStyle(
                fontFamily = displayFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.5.sp,
                lineHeight = 21.sp,
                platformStyle = CompactPlatformStyle,
                lineHeightStyle = CompactLineHeightStyle
            )
        ),
        titleLarge = applyHeadingBrushIfEnabled(
            TextStyle(
                fontFamily = displayFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                platformStyle = CompactPlatformStyle,
                lineHeightStyle = CompactLineHeightStyle
            )
        ),
        titleMedium = applyHeadingBrushIfEnabled(
            TextStyle(
                fontFamily = displayFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.5.sp,
                lineHeight = 18.sp,
                letterSpacing = 0.1.sp,
                platformStyle = CompactPlatformStyle,
                lineHeightStyle = CompactLineHeightStyle
            )
        ),
        titleSmall = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.1.sp,
            platformStyle = CompactPlatformStyle,
            lineHeightStyle = CompactLineHeightStyle
        ),
        bodyLarge = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 14.5.sp,
            lineHeight = 18.5.sp,
            letterSpacing = 0.15.sp,
            platformStyle = CompactPlatformStyle,
            lineHeightStyle = CompactLineHeightStyle
        ),
        bodyMedium = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            lineHeight = 16.5.sp,
            letterSpacing = 0.1.sp,
            platformStyle = CompactPlatformStyle,
            lineHeightStyle = CompactLineHeightStyle
        ),
        bodySmall = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 11.5.sp,
            lineHeight = 14.5.sp,
            letterSpacing = 0.15.sp,
            platformStyle = CompactPlatformStyle,
            lineHeightStyle = CompactLineHeightStyle
        ),
        labelLarge = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 12.5.sp,
            lineHeight = 15.5.sp,
            letterSpacing = 0.1.sp,
            platformStyle = CompactPlatformStyle,
            lineHeightStyle = CompactLineHeightStyle
        ),
        labelMedium = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 13.5.sp,
            letterSpacing = 0.2.sp,
            platformStyle = CompactPlatformStyle,
            lineHeightStyle = CompactLineHeightStyle
        ),
        labelSmall = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 10.sp,
            lineHeight = 12.5.sp,
            letterSpacing = 0.2.sp,
            platformStyle = CompactPlatformStyle,
            lineHeightStyle = CompactLineHeightStyle
        )
    )
}
