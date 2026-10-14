package com.sit.litecad.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.RoundedCorner
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sit.litecad.R
import com.sit.litecad.model.EdgeLineType
import com.sit.litecad.settings.ColorTable
import com.sit.litecad.settings.LineTypeTable
import com.sit.litecad.settings.LineWeightTable

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
    currentLineType: EdgeLineType = EdgeLineType.SOLID,
    onLineTypeChange: (EdgeLineType) -> Unit = {},
    showControls: Boolean = true,
    showOrtho: Boolean = true,
    showExactInput: Boolean = true,
    showLineType: Boolean = true,
    roundingMm: Int = 0,
    onRoundingChange: (Int) -> Unit = {},
    showRounding: Boolean = false,
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
            if (showOrtho) {
                SettingsButtonDummy(
                    icon = Icons.Default.RoundedCorner,
                    label = stringResource(R.string.label_ortho),
                    isActive = isOrthoEnabled,
                    onClick = onOrthoToggle
                )
            }

            if (showExactInput) {
                SettingsButtonDummy(
                    icon = Icons.Default.Straighten,
                    label = stringResource(R.string.label_exact),
                    isActive = exactInputEnabled,
                    onClick = onExactInputToggle
                )
            }

            ColorSelector(
                currentColor = currentColor,
                onColorChange = onColorChange
            )

            ThicknessSelector(
                currentThickness = currentThickness,
                onThicknessChange = onThicknessChange
            )

            if (showLineType) {
                LineTypeSelector(
                    currentLineType = currentLineType,
                    onLineTypeChange = onLineTypeChange
                )
            }

            if (showRounding) {
                RoundingSelector(
                    roundingMm = roundingMm,
                    onRoundingChange = onRoundingChange
                )
            }
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
                    text = stringResource(LineWeightTable.displayRes(currentThickness)),
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
                    text = { Text(stringResource(LineWeightTable.displayRes(value))) },
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
                    text = stringResource(LineTypeTable.nameResFor(currentLineType)),
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
                    text = { Text(stringResource(entry.nameRes)) },
                    onClick = {
                        onLineTypeChange(entry.type)
                        expanded = false
                    }
                )
            }
        }
    }
}

/** Шаги округления длины в мм: 0 — выкл, 10 — 1 см, 100 — 10 см, 1000 — 1 м. */
private val ROUNDING_STEPS_MM = listOf(0, 10, 100, 1000)

/** Подпись режима округления для кнопки панели. */
@Composable
private fun roundingLabel(roundingMm: Int): String = when (roundingMm) {
    10 -> stringResource(R.string.label_rounding_1cm)
    100 -> stringResource(R.string.label_rounding_10cm)
    1000 -> stringResource(R.string.label_rounding_1m)
    else -> stringResource(R.string.label_rounding_off)
}

/** Выпадающий выбор шага округления длины (последний пункт панели свойств). */
@Composable
private fun RoundingSelector(
    roundingMm: Int,
    onRoundingChange: (Int) -> Unit
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
                Icon(
                    imageVector = Icons.Default.LinearScale,
                    contentDescription = stringResource(R.string.cd_rounding),
                    modifier = Modifier.size(18.dp),
                    tint = Color.Gray
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "≈ " + roundingLabel(roundingMm),
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
            ROUNDING_STEPS_MM.forEach { value ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (value == 0) stringResource(R.string.label_rounding_off) else roundingLabel(value))
                            if (value == roundingMm) {
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    onClick = {
                        onRoundingChange(value)
                        expanded = false
                    }
                )
            }
        }
    }
}