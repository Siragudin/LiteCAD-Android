package com.sit.litecad.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.sit.litecad.model.CadDocument
import com.sit.litecad.storage.ProjectStorage
import com.sit.litecad.storage.ThumbnailGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Периодическое автосохранение документа по таймеру.
 *
 * - [intervalMinutes] <= 0 → таймер не запускается (Выкл).
 * - Иначе цикл раз в [intervalMinutes] минут проверяет, менялась ли ревизия документа,
 *   и сохраняет тем же путём, что и ручное сохранение (превью + ProjectStorage).
 * - I/O выполняется на [Dispatchers.IO]; ошибки глотаются молча (без тостов и краша).
 * - Отсчёт инициализируется текущей ревизией — при открытии проекта ничего не сохраняется,
 *   пока пользователь не изменит документ.
 * - При уходе из EditorScreen `LaunchedEffect` отменяется автоматически.
 */
@Composable
fun AutosaveTimer(
    document: CadDocument,
    projectName: String,
    storage: ProjectStorage,
    intervalMinutes: Int,
) {
    // Инициализируем текущей ревизией: не сохранять сразу при открытии, пока нет правок.
    var lastSavedRevision by remember { mutableStateOf(document.revision) }

    LaunchedEffect(intervalMinutes, projectName) {
        if (intervalMinutes <= 0) return@LaunchedEffect

        // Отсчёт начинается от состояния на момент (пере)запуска таймера.
        lastSavedRevision = document.revision

        while (isActive) {
            delay(intervalMinutes * 60_000L)
            if (document.revision != lastSavedRevision) {
                try {
                    withContext(Dispatchers.IO) {
                        val thumb = ThumbnailGenerator.generate(document)
                        storage.saveProject(document, projectName, thumb)
                    }
                    lastSavedRevision = document.revision
                } catch (e: Exception) {
                    // Автосохранение — тихо, без тостов и краша.
                    e.printStackTrace()
                }
            }
        }
    }
}