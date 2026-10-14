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

/**
 * Экран настроек приложения (полноэкранный overlay).
 * Пока содержит только выбор языка.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val currentLanguage = remember { LanguagePreferences.getLanguage(context) }
    var languageDialogOpen by remember { mutableStateOf(false) }

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
}

/** Название языка для отображения. */
@Composable
private fun languageLabel(code: String): String = when (code) {
    "ru" -> stringResource(R.string.language_russian)
    else -> stringResource(R.string.language_english)
}
