package com.sit.litecad.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material.icons.filled.AutoFixOff
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Rectangle
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Выдвижная нижняя панель инструментов.
 *
 * Первый ряд инструментов виден всегда. Дополнительные ряды скрыты по умолчанию
 * и выезжают вниз поверх холста по тапу на стрелку. Панель не сжимает холст —
 * она отображается как overlay (align(BottomCenter) в Box с холстом).
 *
 * Автозакрытия нет: панель сворачивается только стрелкой.
 */
@Composable
fun ToolsBottomPanel(
    selectedToolId: String,
    onToolSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    // Захардкоженный список рядов инструментов.
    // Первый ряд — всегда виден, остальные — выезжают при раскрытии.
    val rows = listOf(
        listOf(
            Triple("hand", Icons.Default.PanTool, "Рука"),
            Triple("line", Icons.Default.Edit, "Линия"),
            Triple("rect", Icons.Default.Rectangle, "Прямоуг."),
            Triple("dim", Icons.Default.Straighten, "Размер"),
            Triple("select", Icons.Default.AdsClick, "Выбор")
        ),
        listOf(
            Triple("erase", Icons.Default.AutoFixOff, "Ластик"),
            Triple("circle", Icons.Default.Circle, "Круг"),
            Triple("sector", Icons.Default.PieChart, "Сектор"),
            Triple("move", Icons.Default.OpenWith, "Переместить")
        )
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Маленькая стрелка по центру над первым рядом. Видна всегда.
            IconButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = if (expanded) "Свернуть панель инструментов" else "Развернуть панель инструментов",
                    modifier = Modifier.size(24.dp)
                )
            }

            // Первый ряд — виден всегда.
            ToolRow(
                tools = rows[0],
                selectedToolId = selectedToolId,
                onToolSelect = onToolSelect
            )

            // Дополнительные ряды — выезжают вниз.
            AnimatedVisibility(visible = expanded) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    for (row in rows.drop(1)) {
                        ToolRow(
                            tools = row,
                            selectedToolId = selectedToolId,
                            onToolSelect = onToolSelect
                        )
                    }
                }
            }
        }
    }
}

/** Один ряд инструментов: 48dp-иконка + подпись. */
@Composable
private fun ToolRow(
    tools: List<Triple<String, ImageVector, String>>,
    selectedToolId: String,
    onToolSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        tools.forEach { (id, icon, label) ->
            val isActive = selectedToolId == id
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FilledIconButton(
                    onClick = { onToolSelect(id) },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent,
                        contentColor = if (isActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(icon, contentDescription = label)
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isActive) MaterialTheme.colorScheme.primary else Color.Gray
                )
            }
        }
    }
}
