package com.sit.litecad.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sit.litecad.R
import com.sit.litecad.settings.LanguagePreferences
import com.sit.litecad.settings.ThemePreferences

/**
 * Экран настроек приложения (полноэкранный overlay).
 * Пока содержит выбор языка и темы редактора.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val currentLanguage = remember { LanguagePreferences.getLanguage(context) }
    val currentTheme = remember { ThemePreferences.getTheme(context) }
    var languageDialogOpen by remember { mutableStateOf(false) }
    var themeDialogOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.settings_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
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
                                        LanguagePreferences.setLanguage(context, code)
                                        // Пересоздаём Activity — Compose подхватит новую локаль.
                                        (context as? android.app.Activity)?.recreate()
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
                                        // Пересоздаём Activity — при следующем входе в редактор тема применится.
                                        (context as? android.app.Activity)?.recreate()
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
