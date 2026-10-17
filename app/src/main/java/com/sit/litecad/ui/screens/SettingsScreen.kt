package com.sit.litecad.ui.screens

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.sit.litecad.R
import com.sit.litecad.settings.AutosavePreferences
import com.sit.litecad.settings.LanguagePreferences
import com.sit.litecad.settings.ThemePreferences

/**
 * Содержимое панели настроек приложения (bottom sheet).
 * Показывает выбор языка и темы редактора.
 * Вызывается внутри ModalBottomSheet из HomeScreen (закрытие — через onDismissRequest).
 */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var currentLanguage by remember { mutableStateOf(LanguagePreferences.getLanguage(context)) }
    var currentTheme by remember { mutableStateOf(ThemePreferences.getTheme(context)) }
    var currentAutosave by remember { mutableStateOf(AutosavePreferences.getIntervalMinutes(context)) }
    var languageDialogOpen by remember { mutableStateOf(false) }
    var themeDialogOpen by remember { mutableStateOf(false) }
    var autosaveDialogOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        // Визуальная ручка drag-handle.
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .size(width = 32.dp, height = 4.dp)
                .background(Color.Gray, RoundedCornerShape(2.dp))
                .align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(12.dp))

        // Заголовок: иконка шестерёнки + «Настройки» — чтобы сразу было понятно, что это.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        // Пункт «Language» / «Язык» — тап открывает выбор.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { languageDialogOpen = true }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.settings_language),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = languageLabel(currentLanguage),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Пункт «Theme» / «Тема» — тап открывает выбор.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { themeDialogOpen = true }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.settings_theme),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = themeLabel(currentTheme),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Пункт «Autosave» / «Автосохранение» — тап открывает выбор интервала.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { autosaveDialogOpen = true }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.settings_autosave),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = autosaveLabel(currentAutosave),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (languageDialogOpen) {
        AlertDialog(
            onDismissRequest = { languageDialogOpen = false },
            title = { Text(stringResource(R.string.settings_language)) },
            text = {
                Column {
                    LanguagePreferences.supported.forEach { code ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    languageDialogOpen = false
                                    if (code != currentLanguage) {
                                        // Наша копия — для холодного старта (fallback, если система не сохранит локаль).
                                        LanguagePreferences.setLanguage(context, code)
                                        // Мгновенно обновляем UI панели.
                                        currentLanguage = code
                                        // Система применяет язык ко всему приложению без recreate().
                                        AppCompatDelegate.setApplicationLocales(
                                            LocaleListCompat.forLanguageTags(code)
                                        )
                                    }
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = code == currentLanguage,
                                onClick = null
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(languageLabel(code))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { languageDialogOpen = false }) {
                    Text(androidx.compose.ui.res.stringResource(android.R.string.ok))
                }
            }
        )
    }

    if (themeDialogOpen) {
        AlertDialog(
            onDismissRequest = { themeDialogOpen = false },
            title = { Text(stringResource(R.string.settings_theme)) },
            text = {
                Column {
                    ThemePreferences.supported.forEach { code ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    themeDialogOpen = false
                                    if (code != currentTheme) {
                                        ThemePreferences.setTheme(context, code)
                                        // Мгновенно обновляем UI панели; тема редактора применится при следующем входе.
                                        currentTheme = code
                                    }
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = code == currentTheme,
                                onClick = null
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(themeLabel(code))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { themeDialogOpen = false }) {
                    Text(androidx.compose.ui.res.stringResource(android.R.string.ok))
                }
            }
        )
    }

    if (autosaveDialogOpen) {
        AlertDialog(
            onDismissRequest = { autosaveDialogOpen = false },
            title = { Text(stringResource(R.string.settings_autosave)) },
            text = {
                Column {
                    AutosavePreferences.supported.forEach { minutes ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    autosaveDialogOpen = false
                                    if (minutes != currentAutosave) {
                                        AutosavePreferences.setIntervalMinutes(context, minutes)
                                        // Мгновенно обновляем UI панели; таймер редактора применится при следующем входе.
                                        currentAutosave = minutes
                                    }
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = minutes == currentAutosave,
                                onClick = null
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(autosaveLabel(minutes))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { autosaveDialogOpen = false }) {
                    Text(androidx.compose.ui.res.stringResource(android.R.string.ok))
                }
            }
        )
    }
}

/** Название интервала автосохранения для отображения. */
@Composable
private fun autosaveLabel(minutes: Int): String = when (minutes) {
    AutosavePreferences.MIN_3 -> stringResource(R.string.autosave_3min)
    AutosavePreferences.MIN_5 -> stringResource(R.string.autosave_5min)
    AutosavePreferences.MIN_10 -> stringResource(R.string.autosave_10min)
    else -> stringResource(R.string.autosave_off)
}

/** Название языка для отображения. */
@Composable
private fun languageLabel(code: String): String = when (code) {
    "ru" -> stringResource(R.string.language_russian)
    else -> stringResource(R.string.language_english)
}

/** Название темы для отображения. */
@Composable
private fun themeLabel(code: String): String = when (code) {
    "dark" -> stringResource(R.string.theme_dark)
    else -> stringResource(R.string.theme_light)
}
