package com.aymanelbanhawy.enterprisepdf.app.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF155EEF),
    onPrimary = Color(0xFFF8FBFF),
    primaryContainer = Color(0xFFDCE7FF),
    onPrimaryContainer = Color(0xFF062A72),
    secondary = Color(0xFF006B6B),
    onSecondary = Color(0xFFF2FFFE),
    secondaryContainer = Color(0xFFC1F3F1),
    onSecondaryContainer = Color(0xFF003738),
    tertiary = Color(0xFF7A35F5),
    onTertiary = Color(0xFFFBF9FF),
    tertiaryContainer = Color(0xFFECDDFF),
    onTertiaryContainer = Color(0xFF30006F),
    error = Color(0xFFC62828),
    onError = Color(0xFFFFFBFF),
    background = Color(0xFFF4F7FD),
    onBackground = Color(0xFF0B1220),
    surface = Color(0xFFFCFDFF),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFE8EEF9),
    onSurfaceVariant = Color(0xFF334155),
    outline = Color(0xFF66758D),
    outlineVariant = Color(0xFFC9D4E6),
    scrim = Color(0xFF05070B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFAAC7FF),
    onPrimary = Color(0xFF002D72),
    primaryContainer = Color(0xFF1245AA),
    onPrimaryContainer = Color(0xFFDEE8FF),
    secondary = Color(0xFF74E5E0),
    onSecondary = Color(0xFF003738),
    secondaryContainer = Color(0xFF005657),
    onSecondaryContainer = Color(0xFFC6FFFB),
    tertiary = Color(0xFFD5BCFF),
    onTertiary = Color(0xFF46119D),
    tertiaryContainer = Color(0xFF6433C8),
    onTertiaryContainer = Color(0xFFF2E8FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    background = Color(0xFF050D18),
    onBackground = Color(0xFFE8EEF9),
    surface = Color(0xFF0A1320),
    onSurface = Color(0xFFF1F5FB),
    surfaceVariant = Color(0xFF172334),
    onSurfaceVariant = Color(0xFFC1CCE1),
    outline = Color(0xFF91A0B8),
    outlineVariant = Color(0xFF243247),
    scrim = Color(0xFF000000),
)

private val EnterpriseTypography = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 38.sp, lineHeight = 44.sp, letterSpacing = (-0.8).sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.45).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 29.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 25.sp, letterSpacing = 0.15.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp, letterSpacing = 0.2.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.24.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.18.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.28.sp),
)

private val EnterpriseShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(18.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(32.dp),
    extraLarge = RoundedCornerShape(42.dp),
)

@Composable
fun EnterprisePdfTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = EnterpriseTypography,
        shapes = EnterpriseShapes,
        content = content,
    )
}
