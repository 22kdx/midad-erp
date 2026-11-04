package com.example.midad.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.example.midad.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ثيم ميزان — المرجع البصري المعتمد لكل شاشات التطبيق.
 * التوكنز مطابقة حرفياً لـ00-tokens.js في حزمة ميزان.
 *
 * ملاحظة الخطوط: حزمة ميزان توفر IBM Plex Sans Arabic بصيغة woff2 (ويب).
 * أندرويد يحتاج TTF/OTF — ضع الملفات في res/font وأسندها في Type.kt.
 * حتى ذلك الحين نستخدم خط النظام العربي.
 */
data class MizanColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val stroke: Color,
    val text: Color,
    val text2: Color,
    val text3: Color,
    val primary: Color,
    val primarySoft: Color,
    val mint: Color,
    val coral: Color,
    val amber: Color,
    val blue: Color,
)

val MizanLight = MizanColors(
    bg = Color(0xFFF5F7FA),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF0F4F3),
    surface3 = Color(0xFFE6EEEB),
    stroke = Color(0xFFE1E8E5),
    text = Color(0xFF172521),
    text2 = Color(0xFF5B6C66),
    text3 = Color(0xFF87958F),
    primary = Color(0xFF147D64),
    primarySoft = Color(0xFF0F624F),
    mint = Color(0xFF0B9A6A),
    coral = Color(0xFFE5384F),
    amber = Color(0xFFC97A00),
    blue = Color(0xFF1F7AE0),
)

val MizanDark = MizanColors(
    bg = Color(0xFF101816),
    surface = Color(0xFF18211E),
    surface2 = Color(0xFF202C28),
    surface3 = Color(0xFF2B3934),
    stroke = Color(0xFF2B3934),
    text = Color(0xFFF1F6F3),
    text2 = Color(0xFFABBAB3),
    text3 = Color(0xFF71837B),
    primary = Color(0xFF4DBB98),
    primarySoft = Color(0xFF7CD3B5),
    mint = Color(0xFF2ED3A0),
    coral = Color(0xFFFF6B7D),
    amber = Color(0xFFFFB547),
    blue = Color(0xFF4DA3FF),
)

val LocalMizan = staticCompositionLocalOf { MizanLight }

private val MizanShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

/** خط مِداد — IBM Plex Sans Arabic مضمَّن داخل التطبيق (بلا إنترنت) */
private val PlexArabic = FontFamily(
    Font(R.font.plex_arabic_regular, FontWeight.Normal),
    Font(R.font.plex_arabic_medium, FontWeight.Medium),
    Font(R.font.plex_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.plex_arabic_bold, FontWeight.Bold),
)

private val MizanTypography = Typography(
    displaySmall = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Bold, fontSize = 30.sp),
    headlineMedium = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleLarge = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    titleMedium = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Normal, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    labelLarge = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Medium, fontSize = 13.sp),
    labelSmall = TextStyle(fontFamily = PlexArabic, fontWeight = FontWeight.Normal, fontSize = 11.sp),
)

@Composable
fun MidadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val mizan = if (darkTheme) MizanDark else MizanLight

    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = mizan.primary, onPrimary = Color.White,
            background = mizan.bg, onBackground = mizan.text,
            surface = mizan.surface, onSurface = mizan.text,
            surfaceVariant = mizan.surface2, onSurfaceVariant = mizan.text2,
            outline = mizan.stroke,
            error = mizan.coral,
        )
    } else {
        lightColorScheme(
            primary = mizan.primary, onPrimary = Color.White,
            background = mizan.bg, onBackground = mizan.text,
            surface = mizan.surface, onSurface = mizan.text,
            surfaceVariant = mizan.surface2, onSurfaceVariant = mizan.text2,
            outline = mizan.stroke,
            error = mizan.coral,
        )
    }

    CompositionLocalProvider(LocalMizan provides mizan) {
        MaterialTheme(
            colorScheme = scheme,
            typography = MizanTypography,
            shapes = MizanShapes,
            content = content,
        )
    }
}
