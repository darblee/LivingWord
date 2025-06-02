package com.darblee.livingword.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class ColorThemeOption { System, Light, Dark }

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun SetColorTheme(
    currentColorTheme: ColorThemeOption,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val darkTheme: Boolean
    val dynamicColorSupported = true
    val colorScheme = if (currentColorTheme == ColorThemeOption.System) {
        if (isSystemInDarkTheme()) {
            darkTheme = true
            if (dynamicColorSupported)
                dynamicDarkColorScheme(context)
            else
                DarkColorScheme
        } else {
            darkTheme = false
            if (dynamicColorSupported)
                dynamicLightColorScheme(context)
            else
                LightColorScheme
        }
    } else {
        if (currentColorTheme == ColorThemeOption.Dark) {
            darkTheme = true
            if (dynamicColorSupported)
                dynamicDarkColorScheme(context)
            else
                DarkColorScheme
        } else {
            darkTheme = false
            if (dynamicColorSupported)
                dynamicLightColorScheme(context)
            else
                LightColorScheme
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}