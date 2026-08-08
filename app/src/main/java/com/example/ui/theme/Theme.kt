package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = Color(0xFF60A5FA), // Light Blue
    secondary = AccentViolet,
    tertiary = SuccessGreen,
    background = Color(0xFF020617),
    surface = DarkSlate,
    onPrimary = Color(0xFF020617),
    onSecondary = Color.White,
    onBackground = Color(0xFFF1F5F9),
    onSurface = Color(0xFFF1F5F9),
    primaryContainer = DarkSlate,
    secondaryContainer = DarkSlate,
    surfaceVariant = DarkSlate,
    outline = Color(0xFF334155),
    outlineVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF94A3B8)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = PrimaryBlue,
    secondary = DarkBlue,
    tertiary = SuccessGreen,
    background = DashboardBackground,
    surface = CardWhite,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = DarkSlate,
    onSurface = DarkSlate,
    primaryContainer = Color(0xFFEFF6FF),
    onPrimaryContainer = PrimaryBlue,
    secondaryContainer = CardWhite,
    surfaceVariant = CardWhite,
    outline = Color(0xFFE2E8F0),
    outlineVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF64748B)
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
