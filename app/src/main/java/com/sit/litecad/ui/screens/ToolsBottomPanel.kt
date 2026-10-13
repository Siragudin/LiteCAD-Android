package com.sit.litecad.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoFixOff
import androidx.compose.material.icons.filled.ChangeHistory
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Rectangle
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.sit.litecad.settings.ToolSettings

/**
 * Элемент панели инструментов: одиночная кнопка либо слот-группа.
 * Слот показывает текущий инструмент и стрелку раскрытия со списком остальных.
 */
sealed interface ToolBarItem {
    /** Обычный инструмент — иконка + лейбл, тап активирует. */
    data class Single(
        val id: String,
        val icon: ImageVector,
        val label: String
    ) : ToolBarItem

    /** Слот-группа: отображает текущий инструмент + стрелку раскрытия. */
    data class Group(
        val items: List<Single>,
        val defaultId: String,
        /** ID текущего инструмента слота — читается/пишется из ToolSettings.common. */
        val currentIdProvider: () -> String,
        val onSelect: (String) -> Unit
    ) : ToolBarItem
}

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
    onToolSelect: (String) -> Unit,
    settings: ToolSettings
) {
    var expanded by remember { mutableStateOf(false) }

    // Первый ряд — виден всегда. Два слота: [Линия/Ось] и [Круг/Сектор/Дуга].
    val firstRow: List<ToolBarItem> = listOf(
        ToolBarItem.Single("hand", Icons.Default.PanTool, "Рука"),
        ToolBarItem.Group(
            items = listOf(
                ToolBarItem.Single("line", Icons.Default.Edit, "Линия"),
                ToolBarItem.Single("axis", Icons.Default.HorizontalRule, "Ось")
            ),
            defaultId = "line",
            currentIdProvider = { settings.common.activeLineTool },
            onSelect = { settings.common.activeLineTool = it }
        ),
        ToolBarItem.Single("move", Icons.Default.OpenWith, "Переместить"),
        ToolBarItem.Group(
            items = listOf(
                ToolBarItem.Single("circle", Icons.Default.Circle, "Круг"),
                ToolBarItem.Single("sector", Icons.Default.PieChart, "Сектор"),
                ToolBarItem.Single("arc", Icons.Default.ChangeHistory, "Дуга")
            ),
            defaultId = "circle",
            currentIdProvider = { settings.common.activeCurveTool },
            onSelect = { settings.common.activeCurveTool = it }
        ),
        ToolBarItem.Single("select", Icons.Default.AdsClick, "Выбор")
    )

    // Второй ряд — выезжает при раскрытии.
    val secondRow: List<ToolBarItem> = listOf(
        ToolBarItem.Single("erase", Icons.Default.AutoFixOff, "Ластик"),
        ToolBarItem.Single("dim", Icons.Default.Straighten, "Размер"),
        ToolBarItem.Single("rect", Icons.Default.Rectangle, "Прямоуг."),
        ToolBarItem.Single("rotate", Icons.AutoMirrored.Filled.RotateRight, "Поворот"),
        ToolBarItem.Single("mirror", Icons.Default.Flip, "Зеркало")
    )

    // Третий ряд — выезжает вместе со вторым.
    val thirdRow: List<ToolBarItem> = listOf(
        ToolBarItem.Single("offset", Icons.Default.Straighten, "Эквидистанта")
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
                items = firstRow,
                selectedToolId = selectedToolId,
                onToolSelect = onToolSelect
            )

            // Дополнительные ряды — выезжают вниз.
            AnimatedVisibility(visible = expanded) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ToolRow(
                        items = secondRow,
                        selectedToolId = selectedToolId,
                        onToolSelect = onToolSelect
                    )
                    ToolRow(
                        items = thirdRow,
                        selectedToolId = selectedToolId,
                        onToolSelect = onToolSelect
                    )
                }
            }
        }
    }
}

/** Один ряд инструментов: слоты и одиночные кнопки. */
@Composable
private fun ToolRow(
    items: List<ToolBarItem>,
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
        items.forEach { item ->
            when (item) {
                is ToolBarItem.Single -> SingleToolButton(item, selectedToolId, onToolSelect)
                is ToolBarItem.Group -> GroupToolButton(item, selectedToolId, onToolSelect)
            }
        }
    }
}

/** Одиночная кнопка инструмента: 48dp-иконка + подпись. */
@Composable
private fun SingleToolButton(
    item: ToolBarItem.Single,
    selectedToolId: String,
    onToolSelect: (String) -> Unit
) {
    val isActive = selectedToolId == item.id
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = { onToolSelect(item.id) },
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent,
                contentColor = if (isActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.size(48.dp)
        ) {
            Icon(item.icon, contentDescription = item.label)
        }
        Text(
            item.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) MaterialTheme.colorScheme.primary else Color.Gray
        )
    }
}

/** Кнопка слота: текущий инструмент + узкая стрелка раскрытия меню. */
@Composable
private fun GroupToolButton(
    group: ToolBarItem.Group,
    selectedToolId: String,
    onToolSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val currentId = group.currentIdProvider()
    val current = group.items.firstOrNull { it.id == currentId }
        ?: group.items.firstOrNull { it.id == group.defaultId }
        ?: group.items.first()

    val isActive = selectedToolId == current.id

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Основная иконка — тап активирует ТЕКУЩИЙ инструмент слота.
            FilledIconButton(
                onClick = { onToolSelect(current.id) },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent,
                    contentColor = if (isActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(current.icon, contentDescription = current.label)
            }

            // Стрелка — узкая, высотой с иконку. Тап раскрывает меню.
            Box {
                IconButton(
                    onClick = { expanded = true },
                    modifier = Modifier.size(width = 16.dp, height = 48.dp)
                ) {
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = "Раскрыть",
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    group.items.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item.label) },
                            leadingIcon = { Icon(item.icon, contentDescription = item.label) },
                            onClick = {
                                group.onSelect(item.id)
                                onToolSelect(item.id)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
        Text(
            current.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) MaterialTheme.colorScheme.primary else Color.Gray
        )
    }
}
