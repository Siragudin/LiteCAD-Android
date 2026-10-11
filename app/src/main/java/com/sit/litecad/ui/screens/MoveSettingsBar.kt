package com.sit.litecad.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.RoundedCorner
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Панель свойств инструмента «Перемещение».
 * Порядок: Ortho, «Точный» (только при включённом Ortho), «Копия».
 */
@Composable
fun MoveSettingsBar(
    orthoEnabled: Boolean,
    onOrthoToggle: () -> Unit,
    copyMode: Boolean,
    onCopyToggle: () -> Unit,
    exactInputEnabled: Boolean,
    onExactInputToggle: () -> Unit,
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
        MoveIconButton(
            icon = Icons.Default.RoundedCorner,
            label = "Ortho",
            isActive = orthoEnabled,
            onClick = onOrthoToggle
        )

        // Точный ввод имеет смысл только при зафиксированной по оси протяжке.
        if (orthoEnabled) {
            MoveIconButton(
                icon = Icons.Default.Straighten,
                label = "Точный",
                isActive = exactInputEnabled,
                onClick = onExactInputToggle
            )
        }

        MoveIconButton(
            icon = Icons.Default.ContentCopy,
            label = "Копия",
            isActive = copyMode,
            onClick = onCopyToggle
        )
    }
}

@Composable
private fun MoveIconButton(
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
