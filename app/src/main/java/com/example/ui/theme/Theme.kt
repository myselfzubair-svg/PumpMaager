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
    primary = Color(0xFF8DA2FB), // Pastel Blue/Indigo
    secondary = Color(0xFF5D75E3),
    tertiary = BrandSuccess,
    background = Color(0xFF0F172A), // Dark slate/navy
    surface = Color(0xFF1E293B),
    onPrimary = Color(0xFF0F172A),
    onSecondary = Color(0xFFFFFFFF),
    onBackground = Color(0xFFF1F5F9),
    onSurface = Color(0xFFF1F5F9),
    primaryContainer = Color(0xFF1E293B),
    secondaryContainer = Color(0xFF1E293B),
    surfaceVariant = Color(0xFF1E293B),
    outline = Color(0xFF334155),
    outlineVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF94A3B8)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = BrandNavy,
    secondary = BrandNavyLight,
    tertiary = BrandSuccess,
    background = BrandBackground,
    surface = BrandSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = BrandLabelNavy,
    onSurface = BrandLabelNavy,
    primaryContainer = BrandIceBlue,
    onPrimaryContainer = BrandNavy,
    secondaryContainer = BrandSurface,
    surfaceVariant = BrandSurface,
    outline = BrandBorderLight,
    outlineVariant = BrandDividerLight,
    onSurfaceVariant = BrandLabelDark
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
