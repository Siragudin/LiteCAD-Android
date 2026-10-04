package com.sit.litecad.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.RoundedCorner
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

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
    isSnapEnabled: Boolean,
    onOrthoToggle: () -> Unit,
    onSnapToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsButtonDummy(
            icon = Icons.Default.RoundedCorner,
            label = "Ortho",
            isActive = isOrthoEnabled,
            onClick = onOrthoToggle
        )
        SettingsButtonDummy(
            icon = Icons.Default.Straighten,
            label = "Snap",
            isActive = isSnapEnabled,
            onClick = onSnapToggle
        )
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
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, modifier = Modifier.size(16.dp), tint = if (isActive) MaterialTheme.colorScheme.primary else Color.Gray)
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = if (isActive) MaterialTheme.colorScheme.primary else Color.Gray)
        }
    }
}
