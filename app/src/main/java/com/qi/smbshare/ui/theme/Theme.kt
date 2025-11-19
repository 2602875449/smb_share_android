package com.qi.smbshare.ui.theme

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// 深色主题颜色方案
private val DarkColorScheme = darkColorScheme(
    primary = DarkColors.Primary,
    onPrimary = DarkColors.PrimaryForeground,
    primaryContainer = DarkColors.Muted,
    onPrimaryContainer = DarkColors.MutedForeground,
    secondary = DarkColors.Secondary,
    onSecondary = DarkColors.SecondaryForeground,
    secondaryContainer = DarkColors.Accent,
    onSecondaryContainer = DarkColors.AccentForeground,
    tertiary = DarkColors.Ring,
    onTertiary = DarkColors.Foreground,
    error = DarkColors.Destructive,
    onError = DarkColors.DestructiveForeground,
    errorContainer = DarkColors.DestructiveContainer,
    onErrorContainer = DarkColors.DestructiveContainerForeground,
    background = DarkColors.Background,
    onBackground = DarkColors.Foreground,
    surface = DarkColors.Card,
    onSurface = DarkColors.CardForeground,
    surfaceVariant = DarkColors.Popover,
    onSurfaceVariant = DarkColors.PopoverForeground,
    outline = DarkColors.Border,
    outlineVariant = DarkColors.Input,
    scrim = Color.Black.copy(alpha = 0.32f),
    inverseSurface = DarkColors.Foreground,
    inverseOnSurface = DarkColors.Background,
    inversePrimary = DarkColors.Primary,
    surfaceTint = DarkColors.Primary
)

// 浅色主题颜色方案
private val LightColorScheme = lightColorScheme(
    primary = LightColors.Primary,
    onPrimary = LightColors.PrimaryForeground,
    primaryContainer = LightColors.Muted,
    onPrimaryContainer = LightColors.MutedForeground,
    secondary = LightColors.Secondary,
    onSecondary = LightColors.SecondaryForeground,
    secondaryContainer = LightColors.Accent,
    onSecondaryContainer = LightColors.AccentForeground,
    tertiary = LightColors.Ring,
    onTertiary = LightColors.Foreground,
    error = LightColors.Destructive,
    onError = LightColors.DestructiveForeground,
    errorContainer = LightColors.DestructiveContainer,
    onErrorContainer = LightColors.DestructiveContainerForeground,
    background = LightColors.Background,
    onBackground = LightColors.Foreground,
    surface = LightColors.Card,
    onSurface = LightColors.CardForeground,
    surfaceVariant = LightColors.Popover,
    onSurfaceVariant = LightColors.PopoverForeground,
    outline = LightColors.Border,
    outlineVariant = LightColors.Input,
    scrim = Color.Black.copy(alpha = 0.32f),
    inverseSurface = LightColors.Foreground,
    inverseOnSurface = LightColors.Background,
    inversePrimary = LightColors.Primary,
    surfaceTint = LightColors.Primary
)

@Composable
fun SmbShareAndroidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // 改为 false 以使用自定义颜色方案
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
            // 设置状态栏颜色为surface颜色，与背景一致
            window.statusBarColor = colorScheme.surface.toArgb()
            // 根据主题设置状态栏文字颜色：深色主题用浅色文字，浅色主题用深色文字
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

