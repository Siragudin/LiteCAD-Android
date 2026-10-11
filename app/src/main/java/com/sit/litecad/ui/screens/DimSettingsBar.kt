package com.sit.litecad.ui.screens

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
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.RoundedCorner
import androidx.compose.material.icons.filled.Straighten
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.sit.litecad.model.DimensionExtensionStyle
import com.sit.litecad.settings.ColorTable
import com.sit.litecad.settings.LineWeightTable

/**
 * Панель свойств инструмента «Размер».
 * Порядок: Ortho, «Точный», стиль выносных линий (Полные/Короткие), цвет, толщина.
 */
@Composable
fun DimSettingsBar(
    orthoEnabled: Boolean,
    onOrthoToggle: () -> Unit,
    exactInputEnabled: Boolean,
    onExactInputToggle: () -> Unit,
    extensionStyle: DimensionExtensionStyle,
    onExtensionStyleToggle: () -> Unit,
    color: Int,
    onColorChange: (Int) -> Unit,
    thickness: Double,
    onThicknessChange: (Double) -> Unit,
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
        DimIconButton(
            icon = Icons.Default.RoundedCorner,
            label = "Ortho",
            isActive = orthoEnabled,
            onClick = onOrthoToggle
        )

        DimIconButton(
            icon = Icons.Default.Straighten,
            label = "Точный",
            isActive = exactInputEnabled,
            onClick = onExactInputToggle
        )

        // Тумблер стиля выносных линий: Полные (FULL) / Короткие (SHORT).
        DimIconButton(
            icon = Icons.Default.Height,
            label = if (extensionStyle == DimensionExtensionStyle.FULL) "Полные" else "Короткие",
            isActive = extensionStyle == DimensionExtensionStyle.FULL,
            onClick = onExtensionStyleToggle
        )

        DimColorSelector(
            currentColor = color,
            onColorChange = onColorChange
        )

        DimThicknessSelector(
            currentThickness = thickness,
            onThicknessChange = onThicknessChange
        )
    }
}

@Composable
private fun DimIconButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        border = if (isActive) null else androidx.compose.foundation.BorderStroke(1.dp, Color.Gray)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(18.dp),
                tint = if (isActive) MaterialTheme.colorScheme.primary else Color.Gray
            )
        }
    }
}

@Composable
private fun DimColorSelector(
    currentColor: Int,
    onColorChange: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(8.dp),
            color = Color.Transparent,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(Color(currentColor), RoundedCornerShape(3.dp))
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = ColorTable.nameFor(currentColor),
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
                            Text(entry.name)
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

@Composable
private fun DimThicknessSelector(
    currentThickness: Double,
    onThicknessChange: (Double) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(8.dp),
            color = Color.Transparent,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = LineWeightTable.display(currentThickness),
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
            LineWeightTable.values.forEach { value ->
                DropdownMenuItem(
                    text = { Text(LineWeightTable.display(value)) },
                    onClick = {
                        onThicknessChange(value)
                        expanded = false
                    }
                )
            }
        }
    }
}
