package com.sit.litecad.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material.icons.filled.AutoFixOff
import androidx.compose.material.icons.filled.ChangeHistory
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Rectangle
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.sit.litecad.ui.icons.ArrowMenuOpen
import com.sit.litecad.ui.icons.ArrowTopLeft
import com.sit.litecad.ui.icons.LineEnd

/**
 * Боковой блок быстрого доступа к инструментам редактора.
 * Пуст, пока пользователь не «закрепит» инструменты long-press-ом на нижней панели.
 * Инструменты идут снизу вверх: первый добавленный — внизу, последний — вверху.
 */
@Composable
fun EditorQuickToolsPanel(
    selectedToolId: String,
    onToolSelect: (String) -> Unit,
    onToolLongPress: (String) -> Unit,
    toolIds: List<String>,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current

    if (toolIds.isEmpty()) return

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Рисуем сверху вниз в обратном порядке: последний добавленный — вверху, первый — внизу.
        toolIds.asReversed().forEach { id ->
            val isActive = id == selectedToolId
            val containerColor = if (isActive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
            val contentColor = if (isActive) {
                Color.White
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(containerColor)
                    .combinedClickable(
                        onClick = { onToolSelect(id) },
                        onLongClick = {
                            // Мгновенный вибро-отклик до изменения списка.
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToolLongPress(id)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconFor(id),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = contentColor
                )
            }
        }
    }
}

/** Иконка инструмента по его id (те же, что на нижней панели, чтобы пользователь узнавал). */
private fun iconFor(toolId: String): ImageVector = when (toolId) {
    "hand" -> Icons.Default.PanTool
    "line" -> Icons.Default.Edit
    "axis" -> Icons.Default.HorizontalRule
    "move" -> Icons.Default.OpenWith
    "circle" -> Icons.Default.Circle
    "sector" -> Icons.Default.PieChart
    "arc" -> Icons.Default.ChangeHistory
    "select" -> Icons.Default.AdsClick
    "erase" -> Icons.Default.AutoFixOff
    "dim" -> Icons.Default.Straighten
    "rect" -> Icons.Default.Rectangle
    "rotate" -> Icons.AutoMirrored.Filled.RotateRight
    "mirror" -> Icons.Default.Flip
    "offset" -> ArrowMenuOpen
    "extend" -> LineEnd
    "leader" -> ArrowTopLeft
    "fill" -> Icons.Default.FormatColorFill
    else -> Icons.Default.PanTool
}
