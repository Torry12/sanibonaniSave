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
val Forest       = Color(0xFF1E3A1A)
val ForestMid    = Color(0xFF345A2D)
val ForestLight  = Color(0xFF56854A)
val Gold         = Color(0xFFE19E00)
val GoldLight    = Color(0xFFFFD54F)
val Terra        = Color(0xFFD35400)
val Cream        = Color(0xFFFDF5E6)
val Cream2       = Color(0xFFFAF0DC)
val Charcoal     = Color(0xFF332D2D)
val MidGray      = Color(0xFF7D746D)
val LightGray    = Color(0xFFE5DED0)

// ── Sophisticated UI Accents ──────────────────────────────────────────────────
val SurfaceGlass = Color(0xFFFFFFFF).copy(alpha = 0.7f)
val ForestDeep   = Color(0xFF132711)
val GoldMuted    = Color(0xFFC68C00)
val GradientForest = listOf(Forest, ForestMid)
val GradientGold   = listOf(Gold, Color(0xFFF9A825))
val GradientTerra  = listOf(Terra, Color(0xFFBF360C))

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
