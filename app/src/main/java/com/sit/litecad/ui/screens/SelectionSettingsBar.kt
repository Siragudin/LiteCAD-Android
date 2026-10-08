package com.sit.litecad.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke

/**
 * Панель свойств инструмента Select.
 * 3 иконки: мульти / удалить / снять всё.
 *
 * Режим (тап / рамка) определяется автоматически по протяжке
 * (тап = короткое касание, рамка = протяжка).
 */
@Composable
fun SelectionSettingsBar(
    multiMode: Boolean,
    onMultiToggle: () -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit
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
        SelectionIconButton(
            icon = Icons.Default.Layers,
            label = "Мульти",
            isActive = multiMode,
            onClick = onMultiToggle
        )

        SelectionIconButton(
            icon = Icons.Default.Delete,
            label = "Удалить",
            isActive = false,
            onClick = onDelete
        )

        SelectionIconButton(
            icon = Icons.Default.Cancel,
            label = "Снять всё",
            isActive = false,
            onClick = onClear
        )
    }
}

@Composable
private fun SelectionIconButton(
    icon: ImageVector,
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
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(18.dp),
                tint = if (isActive) MaterialTheme.colorScheme.primary else Color.Gray
            )
        }
    }
}