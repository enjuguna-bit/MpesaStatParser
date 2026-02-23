package com.mpesaparser.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF49B88B),
    onPrimary = Color(0xFF04281A),
    secondary = Color(0xFF8ED3C7),
    tertiary = Color(0xFFFFC887),
    background = Color(0xFF0D1714),
    surface = Color(0xFF13201C),
    surfaceVariant = Color(0xFF22332D)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0B6E4F),
    onPrimary = Color(0xFFF2FFF9),
    secondary = Color(0xFF0F9D90),
    tertiary = Color(0xFFE28C28),
    background = Color(0xFFF1F6F4),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE2ECE8)
)

private val AppTypography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.2.sp),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        bodyMedium = bodyMedium.copy(lineHeight = 20.sp)
    )
}

@Composable
fun MpesaParserTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
