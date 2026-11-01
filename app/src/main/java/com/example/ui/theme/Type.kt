package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
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

fun buildLeafyTypography(useComicSans: Boolean): Typography {
    val displayFamily = if (useComicSans) ComicSansFontFamily else QuicksandFontFamily
    val bodyFamily = if (useComicSans) ComicSansFontFamily else NunitoFontFamily

    return Typography(
        displayLarge = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 36.sp,
            lineHeight = 44.sp,
            letterSpacing = (-0.2).sp
        ),
        displayMedium = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            lineHeight = 36.sp
        ),
        headlineLarge = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            lineHeight = 32.sp
        ),
        headlineMedium = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = 28.sp
        ),
        titleLarge = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            lineHeight = 25.sp
        ),
        titleMedium = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.1.sp
        ),
        titleSmall = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.2.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            letterSpacing = 0.15.sp
        ),
        bodySmall = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.2.sp
        ),
        labelLarge = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp
        ),
        labelMedium = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.3.sp
        ),
        labelSmall = TextStyle(
            fontFamily = bodyFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            letterSpacing = 0.3.sp
        )
    )
}
