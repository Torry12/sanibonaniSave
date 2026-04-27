package com.sanibonani.save.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────────────────────
//  BRAND COLOURS
// ─────────────────────────────────────────────────────────────────────────────
val Forest       = Color(0xFF1A3A2A)
val ForestMid    = Color(0xFF2D5A3D)
val ForestLight  = Color(0xFF4A8560)
val Gold         = Color(0xFFD4A017)
val GoldLight    = Color(0xFFF2C94C)
val Terra        = Color(0xFFC2612A)
val Cream        = Color(0xFFFEF8F0)
val Cream2       = Color(0xFFFAF0E0)
val Charcoal     = Color(0xFF2C2C2C)
val MidGray      = Color(0xFF6B7280)
val LightGray    = Color(0xFFE5DDD0)

val SuccessGreen = Color(0xFF16A34A)
val SuccessBg    = Color(0xFFD1FAE5)
val WarningYellow = Color(0xFFFFB300) // Vibrant Warning Yellow
val WarningAmber = Color(0xFFD97706)
val WarningBg    = Color(0xFFFEF3C7)
val ErrorRed     = Color(0xFFDC2626)
val ErrorBg      = Color(0xFFFEE2E2)
val InfoBlue     = Color(0xFF2563EB)
val InfoBg       = Color(0xFFDBEAFE)

// ─────────────────────────────────────────────────────────────────────────────
//  TYPOGRAPHY
//  Using FontFamily.Serif (Playfair-like) and SansSerif (Nunito-like)
//  To use actual downloaded fonts, drop .ttf files in res/font/ and use Font()
// ─────────────────────────────────────────────────────────────────────────────
val DisplayFont = FontFamily.Serif
val BodyFont    = FontFamily.SansSerif

val SanibonaniTypography = Typography(
    displayLarge   = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Black,    fontSize = 48.sp, lineHeight = 56.sp),
    displayMedium  = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Bold,     fontSize = 36.sp, lineHeight = 44.sp),
    displaySmall   = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Bold,     fontSize = 28.sp, lineHeight = 36.sp),
    headlineLarge  = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    headlineMedium = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    headlineSmall  = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleLarge     = TextStyle(fontFamily = BodyFont,    fontWeight = FontWeight.ExtraBold,fontSize = 18.sp),
    titleMedium    = TextStyle(fontFamily = BodyFont,    fontWeight = FontWeight.Bold,     fontSize = 16.sp),
    titleSmall     = TextStyle(fontFamily = BodyFont,    fontWeight = FontWeight.Bold,     fontSize = 14.sp),
    bodyLarge      = TextStyle(fontFamily = BodyFont,    fontWeight = FontWeight.Normal,   fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium     = TextStyle(fontFamily = BodyFont,    fontWeight = FontWeight.Normal,   fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall      = TextStyle(fontFamily = BodyFont,    fontWeight = FontWeight.Normal,   fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge     = TextStyle(fontFamily = BodyFont,    fontWeight = FontWeight.Bold,     fontSize = 14.sp, letterSpacing = 0.1.sp),
    labelMedium    = TextStyle(fontFamily = BodyFont,    fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.5.sp),
    labelSmall     = TextStyle(fontFamily = BodyFont,    fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.5.sp),
)

// ─────────────────────────────────────────────────────────────────────────────
//  COLOUR SCHEMES
// ─────────────────────────────────────────────────────────────────────────────
private val LightColorScheme = lightColorScheme(
    primary              = Forest,
    onPrimary            = Cream,
    primaryContainer     = ForestLight,
    onPrimaryContainer   = Cream,
    secondary            = Gold,
    onSecondary          = Forest,
    secondaryContainer   = GoldLight,
    onSecondaryContainer = Forest,
    tertiary             = Terra,
    onTertiary           = Cream,
    background           = Cream,
    onBackground         = Charcoal,
    surface              = Cream,
    onSurface            = Charcoal,
    surfaceVariant       = Cream2,
    onSurfaceVariant     = MidGray,
    outline              = LightGray,
    error                = ErrorRed,
    onError              = Cream,
)

// ─────────────────────────────────────────────────────────────────────────────
//  THEME
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SanibonaniTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,   // Dark theme: add DarkColorScheme and toggle here
        typography  = SanibonaniTypography,
        content     = content
    )
}
