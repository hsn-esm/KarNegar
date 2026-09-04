package ir.karnegar.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import ir.karnegar.app.ui.ThemeMode

// ---------- پالت رنگ: سبز-فیروزه‌ای آرام، مینیمال ----------

private val Teal600 = Color(0xFF0F766E)
private val Teal500 = Color(0xFF14907F)
private val TealDeep = Color(0xFF0B6157)
private val Teal200 = Color(0xFF7BE0C3)
private val Teal900 = Color(0xFF06403A)

private val Amber500 = Color(0xFFD97706)
private val Amber200 = Color(0xFFFCD9A1)

private val Rose500 = Color(0xFFDC2626)
private val Rose200 = Color(0xFFFFC9C9)

private val LightBg = Color(0xFFF7F8F7)
private val LightSurface = Color(0xFFFFFFFF)
private val LightSurfaceVariant = Color(0xFFEDF1EF)
private val LightOn = Color(0xFF14201C)

private val DarkBg = Color(0xFF0D1211)
private val DarkSurface = Color(0xFF151C1A)
private val DarkSurfaceVariant = Color(0xFF212B28)
private val DarkOn = Color(0xFFE6EDEA)

private val LightColors = lightColorScheme(
    primary = Teal600,
    onPrimary = Color.White,
    primaryContainer = Teal200,
    onPrimaryContainer = Teal900,
    secondary = Amber500,
    onSecondary = Color.White,
    secondaryContainer = Amber200,
    onSecondaryContainer = Color(0xFF4A2A00),
    tertiary = TealDeep,
    error = Rose500,
    errorContainer = Rose200,
    background = LightBg,
    onBackground = LightOn,
    surface = LightSurface,
    onSurface = LightOn,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF44514D),
    outline = Color(0xFFBFCAC6),
    outlineVariant = Color(0xFFDCE4E1)
)

private val DarkColors = darkColorScheme(
    primary = Teal200,
    onPrimary = Teal900,
    primaryContainer = Color(0xFF13544B),
    onPrimaryContainer = Teal200,
    secondary = Amber200,
    onSecondary = Color(0xFF3A2100),
    secondaryContainer = Color(0xFF6B3D00),
    onSecondaryContainer = Amber200,
    tertiary = Teal200,
    error = Color(0xFFFF8A80),
    errorContainer = Color(0xFF6E1414),
    background = DarkBg,
    onBackground = DarkOn,
    surface = DarkSurface,
    onSurface = DarkOn,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFB6C2BE),
    outline = Color(0xFF465350),
    outlineVariant = Color(0xFF2C3634)
)

private val KarNegarTypography = Typography(
    displaySmall = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium)
)

/** رنگ اختصاصی هر نوع شیفت — برای نقطه‌های تقویم و نمودار خلاصه */
object ShiftPalette {
    // پالت تم روشن
    val lightMorning = Color(0xFF2E9E7C)
    val lightAfternoon = Color(0xFF2F6FB5)
    val lightEvening = Color(0xFFB07A1E)
    val lightHoliday = Color(0xFF9B4DCA)
    val lightNight = Color(0xFF3F3D9E)

    // پالت تم تاریک — روشن‌تر تا روی پس‌زمینه‌ی #0D1211 خوانا باشد
    val darkMorning = Color(0xFF6FDCB6)
    val darkAfternoon = Color(0xFF7FB0E8)
    val darkEvening = Color(0xFFE8C06A)
    val darkHoliday = Color(0xFFCB94EE)
    val darkNight = Color(0xFF8F8CE8)
}

@Composable
fun KarNegarTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colors = if (dark) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !dark
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = KarNegarTypography,
        content = content
    )
}
