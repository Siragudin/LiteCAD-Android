package com.sit.litecad.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Rectangle
import androidx.compose.material.icons.filled.RoundedCorner
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.sit.litecad.model.EdgeLineType
import com.sit.litecad.settings.ColorTable
import com.sit.litecad.settings.LineTypeTable
import com.sit.litecad.settings.LineWeightTable

@Composable
fun ToolsPanelDummy(
    selectedToolId: String,
    onToolSelect: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tools = listOf(
                Triple("hand", Icons.Default.PanTool, "Рука"),
                Triple("line", Icons.Default.Edit, "Линия"),
                Triple("rect", Icons.Default.Rectangle, "Прямоуг."),
                Triple("dim", Icons.Default.Straighten, "Размер"),
                Triple("select", Icons.Default.AdsClick, "Выбор")
            )

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
                    Text(label, style = MaterialTheme.typography.labelSmall, color = if (isActive) MaterialTheme.colorScheme.primary else Color.Gray)
                }
            }
        }
    }
}

@Composable
fun TopSettingsBarDummy(
    isOrthoEnabled: Boolean,
    onOrthoToggle: () -> Unit,
    exactInputEnabled: Boolean,
    onExactInputToggle: () -> Unit,
    currentThickness: Double,
    onThicknessChange: (Double) -> Unit,
    currentColor: Int,
    onColorChange: (Int) -> Unit,
    currentLineType: EdgeLineType,
    onLineTypeChange: (EdgeLineType) -> Unit,
    showControls: Boolean = true
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
        if (showControls) {
            SettingsButtonDummy(
                icon = Icons.Default.RoundedCorner,
                label = "Ortho",
                isActive = isOrthoEnabled,
                onClick = onOrthoToggle
            )

            SettingsButtonDummy(
                icon = Icons.Default.Straighten,
                label = "Точный",
                isActive = exactInputEnabled,
                onClick = onExactInputToggle
            )

            ColorSelector(
                currentColor = currentColor,
                onColorChange = onColorChange
            )

            ThicknessSelector(
                currentThickness = currentThickness,
                onThicknessChange = onThicknessChange
            )

            LineTypeSelector(
                currentLineType = currentLineType,
                onLineTypeChange = onLineTypeChange
            )
        }
    }
}

@Composable
private fun SettingsButtonDummy(
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
private fun ColorSelector(
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
private fun ThicknessSelector(
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

@Composable
private fun LineTypeSelector(
    currentLineType: EdgeLineType,
    onLineTypeChange: (EdgeLineType) -> Unit
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
                    text = LineTypeTable.nameFor(currentLineType),
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
            LineTypeTable.values.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(entry.name) },
                    onClick = {
                        onLineTypeChange(entry.type)
                        expanded = false
                    }
                )
            }
        }
    }
}