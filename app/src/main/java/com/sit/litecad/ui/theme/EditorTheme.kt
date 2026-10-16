package com.sit.litecad.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Цвета тем редактора (не всего приложения — только EditorScreen).
 * HomeScreen и остальные экраны живут под глобальной Theme.LiteCAD.
 */

// --- Светлая тема ---
private val EditorLightPrimary = Color(0xFF383838)
private val EditorLightOnPrimary = Color(0xFFFFFFFF)
private val EditorLightSurface = Color(0xFFFFFFFF)          // верхний топбар
private val EditorLightSurfaceVariant = Color(0xFFCCCCCC)   // панели (инструментов, свойств, второй ряд топбара)
private val EditorLightBackground = Color(0xFFE0E0E0)       // фон вокруг холста
private val EditorLightOutline = Color(0xFF999999)          // рамка холста
private val EditorLightOnSurface = Color(0xFF111111)        // текст/иконки на светлом

/** Светлая тема редактора. */
val EditorLightColorScheme = lightColorScheme(
    primary = EditorLightPrimary,
    onPrimary = EditorLightOnPrimary,
    surface = EditorLightSurface,
    surfaceVariant = EditorLightSurfaceVariant,
    background = EditorLightBackground,
    outline = EditorLightOutline,
    onSurface = EditorLightOnSurface,
    onSurfaceVariant = EditorLightOnSurface,
)

// --- Тёмная тема ---
private val EditorDarkPrimary = Color(0xFFCCCCCC)
private val EditorDarkOnPrimary = Color(0xFF111111)
private val EditorDarkSurface = Color(0xFF1F1F1F)          // верхний топбар
private val EditorDarkSurfaceVariant = Color(0xFF2C2C2C)   // панели (инструментов, свойств, второй ряд топбара)
private val EditorDarkBackground = Color(0xFF1A1A1A)       // фон вокруг холста
private val EditorDarkOutline = Color(0xFF505050)          // рамка холста
private val EditorDarkOnSurface = Color(0xFFEEEEEE)        // текст/иконки на тёмном

/** Тёмная тема редактора. */
val EditorDarkColorScheme = darkColorScheme(
    primary = EditorDarkPrimary,
    onPrimary = EditorDarkOnPrimary,
    surface = EditorDarkSurface,
    surfaceVariant = EditorDarkSurfaceVariant,
    background = EditorDarkBackground,
    outline = EditorDarkOutline,
    onSurface = EditorDarkOnSurface,
    onSurfaceVariant = EditorDarkOnSurface,
)
