package com.sit.litecad.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sit.litecad.R
import com.sit.litecad.model.TextRotation
import com.sit.litecad.model.TextSizeSp
import com.sit.litecad.settings.ColorTable

/**
 * Панель свойств инструмента «Текст».
 * Порядок: размер шрифта (12/14/18/24 sp), поворот (0°/90°), цвет.
 *
 * Размер задаётся в sp — это фиксированный ЭКРАННЫЙ размер надписи: при зуме
 * двигается только её позиция, размер шрифта на экране не меняется.
 */
@Composable
fun TextSettingsBar(
    sizeSp: TextSizeSp,
    onSizeSpChange: (TextSizeSp) -> Unit,
    rotation: TextRotation,
    onRotationChange: (TextRotation) -> Unit,
    color: Int,
    onColorChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Размер шрифта: одна кнопка с текущим значением + выпадающий список 12/14/18/24.
        TextSizeSelector(
            sizeSp = sizeSp,
            onSizeSpChange = onSizeSpChange
        )

        // Тумблер поворота: 0° ↔ 90°.
        TextBarButton(
            label = stringResource(
                if (rotation == TextRotation.DEG_90) R.string.text_rotation_90
                else R.string.text_rotation_0
            ),
            isActive = rotation == TextRotation.DEG_90,
            onClick = {
                onRotationChange(
                    if (rotation == TextRotation.DEG_90) TextRotation.DEG_0 else TextRotation.DEG_90
                )
            }
        )

        TextColorSelector(
            currentColor = color,
            onColorChange = onColorChange
        )
    }
}

/**
 * Выбор размера шрифта: кнопка с текущим значением (12 / 14 / 18 / 24) и стрелкой ▼,
 * по тапу — выпадающее меню с теми же четырьмя вариантами (стиль — как у слотов нижней панели).
 */
@Composable
private fun TextSizeSelector(
    sizeSp: TextSizeSp,
    onSizeSpChange: (TextSizeSp) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            border = null
        ) {
            Row(
                modifier = Modifier.padding(start = 8.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(sizeLabelRes(sizeSp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = stringResource(R.string.cd_expand),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            TextSizeSp.entries.forEach { size ->
                DropdownMenuItem(
                    text = { Text(stringResource(sizeLabelRes(size))) },
                    onClick = {
                        onSizeSpChange(size)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun TextBarButton(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        border = if (isActive) null else BorderStroke(1.dp, Color.Gray)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isActive) MaterialTheme.colorScheme.primary else Color.Gray
            )
        }
    }
}

/** Подпись размера: значение в sp (совпадает с TextSizeSp.sp). */
private fun sizeLabelRes(size: TextSizeSp): Int = when (size) {
    TextSizeSp.SMALL -> R.string.text_size_12
    TextSizeSp.NORMAL -> R.string.text_size_14
    TextSizeSp.LARGE -> R.string.text_size_18
    TextSizeSp.XLARGE -> R.string.text_size_24
}

@Composable
private fun TextColorSelector(
    currentColor: Int,
    onColorChange: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(8.dp),
            color = Color.Transparent,
            border = BorderStroke(1.dp, Color.Gray)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.label_text_color),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(Color(currentColor), RoundedCornerShape(3.dp))
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(ColorTable.nameResFor(currentColor)),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                Spacer(Modifier.width(4.dp))
                Text("▾", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            ColorTable.values.forEach { entry ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .background(Color(entry.argb), RoundedCornerShape(3.dp))
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(entry.nameRes))
                        }
                    },
                    onClick = {
                        onColorChange(entry.argb)
                        expanded = false
                    }
                )
            }
        }
    }
}
