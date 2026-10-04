package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = InstaMagenta,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4A1230),
    onPrimaryContainer = Color(0xFFFFD9E5),
    secondary = InstaCyan,
    onSecondary = Color(0xFF00262B),
    secondaryContainer = Color(0xFF003D47),
    onSecondaryContainer = Color(0xFFB8F8FF),
    tertiary = InstaOrange,
    onTertiary = Color(0xFF2B1400),
    background = CyberNavyDark,
    onBackground = Color(0xFFF2F3FA),
    surface = CyberSurfaceDark,
    onSurface = Color(0xFFF2F3FA),
    surfaceVariant = CyberSurfaceVariantDark,
    onSurfaceVariant = Color(0xFFC5C7DC)
)

private val LightColorScheme = lightColorScheme(
    primary = InstaMagentaDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD8E6),
    onPrimaryContainer = Color(0xFF3E001E),
    secondary = InstaCyanDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCF7FF),
    onSecondaryContainer = Color(0xFF002024),
    tertiary = InstaOrangeDark,
    onTertiary = Color.White,
    background = CyberLightBg,
    onBackground = Color(0xFF141522),
    surface = CyberLightSurface,
    onSurface = Color(0xFF141522),
    surfaceVariant = CyberLightSurfaceVariant,
    onSurfaceVariant = Color(0xFF454659)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
