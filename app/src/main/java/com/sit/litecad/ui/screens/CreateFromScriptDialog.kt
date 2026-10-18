package com.sit.litecad.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.sit.litecad.R
import com.sit.litecad.scripting.ProjectCreator
import com.sit.litecad.scripting.ScriptCreationResult
import com.sit.litecad.scripting.ScriptPromptGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Цвет акцентных кнопок диалога — тот же, что используется на HomeScreen. */
private val AccentColor = Color(0xFF383838)

/**
 * Диалог создания чертежа из скрипта (DSL).
 *
 * Два сценария:
 *  1. Пользователь вводит готовый скрипт и жмёт «Создать» — приложение строит
 *     [com.sit.litecad.model.CadDocument] через [ProjectCreator] и по успеху сообщает
 *     имя проекта наружу через [onCreated].
 *  2. Пользователь описывает желаемый чертёж, скачивает Markdown-промпт
 *     («Скачать файл для ИИ»), отдаёт его браузерной нейросети, а полученный код
 *     вставляет в поле скрипта.
 *
 * @param onDismiss запрос на закрытие диалога (тап вне/кнопка «Отмена»).
 * @param onCreated вызывается с именем созданного проекта после успешного сохранения.
 */
@Composable
fun CreateFromScriptDialog(
    onDismiss: () -> Unit,
    onCreated: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Строки читаем один раз в композиции (не внутри колбэков) — так требует Compose-lint.
    val aiFileSavedMessage = stringResource(R.string.script_ai_file_saved)
    val aiFileErrorMessage = stringResource(R.string.script_ai_file_error)

    var scriptText by remember { mutableStateOf("") }
    var aiPrompt by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    // Текст ошибки создания скрипта; показывается под полем ввода, сбрасывается при новой попытке.
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val isCreateEnabled = scriptText.isNotBlank() && !isGenerating

    AlertDialog(
        // Во время генерации диалог нельзя закрыть случайным тапом снаружи.
        onDismissRequest = { if (!isGenerating) onDismiss() },
        title = { Text(stringResource(R.string.script_dialog_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // --- Поле кода скрипта ---
                OutlinedTextField(
                    value = scriptText,
                    onValueChange = { scriptText = it },
                    placeholder = { Text(stringResource(R.string.script_dialog_placeholder)) },
                    enabled = !isGenerating,
                    singleLine = false,
                    minLines = 6,
                    maxLines = 12,
                    // Моноширинный шрифт — так код скрипта читается заметно лучше.
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp)
                )

                // Ошибка создания скрипта — с номером строки, если он известен.
                errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (isGenerating) {
                    val percent = (progress * 100f).toInt()
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = stringResource(R.string.script_progress_percent, percent),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                HorizontalDivider()

                // --- Генерация промпта для ИИ ---
                OutlinedTextField(
                    value = aiPrompt,
                    onValueChange = { aiPrompt = it },
                    label = { Text(stringResource(R.string.script_ai_prompt_label)) },
                    placeholder = { Text(stringResource(R.string.script_ai_prompt_placeholder)) },
                    enabled = !isGenerating,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedButton(
                    onClick = {
                        // Сохранение файла — это I/O, уводим на Dispatchers.IO.
                        scope.launch {
                            val saved = withContext(Dispatchers.IO) {
                                ScriptPromptGenerator.saveMarkdownToDownloads(context, aiPrompt)
                            }
                            Toast.makeText(
                                context,
                                if (saved != null) aiFileSavedMessage else aiFileErrorMessage,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    enabled = !isGenerating,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                    Text(
                        text = stringResource(R.string.script_download_ai_button),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val projectName = "AI_Чертеж_${System.currentTimeMillis()}"
                    errorMessage = null
                    isGenerating = true
                    progress = 0f

                    scope.launch {
                        // Тяжёлая работа идёт на Dispatchers.IO внутри ProjectCreator,
                        // поэтому главный поток (UI) не блокируется.
                        val result = ProjectCreator.createFromScriptDetailed(
                            context = context,
                            projectName = projectName,
                            scriptText = scriptText
                        ) { updated ->
                            // Колбэк вызывается из фонового потока; запись snapshot-состояния
                            // потокобезопасна, рекомпозиция произойдёт на главном потоке.
                            progress = updated
                        }

                        when (result) {
                            is ScriptCreationResult.Success -> onCreated(projectName)

                            is ScriptCreationResult.ParseFailure -> {
                                // Сброс прогресса и возобновление ввода; показываем причину.
                                isGenerating = false
                                progress = 0f
                                errorMessage = if (result.line > 0) {
                                    "Ошибка в строке ${result.line}: ${result.message}"
                                } else {
                                    result.message
                                }
                            }

                            ScriptCreationResult.EmptyScript -> {
                                isGenerating = false
                                progress = 0f
                                errorMessage = "Введите скрипт"
                            }
                        }
                    }
                },
                enabled = isCreateEnabled,
                colors = ButtonDefaults.buttonColors(containerColor = AccentColor)
            ) { Text(stringResource(R.string.button_create)) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isGenerating,
                colors = ButtonDefaults.textButtonColors(contentColor = Color.Gray)
            ) { Text(stringResource(R.string.button_cancel)) }
        }
    )
}
