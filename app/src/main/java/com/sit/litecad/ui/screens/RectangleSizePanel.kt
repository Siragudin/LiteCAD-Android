package com.sit.litecad.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun RectangleSizePanel(
    hintWidth: Double,
    hintHeight: Double,
    onSubmit: (Double, Double) -> Unit,
    onCancel: () -> Unit
) {
    var widthText by remember { mutableStateOf("") }
    var heightText by remember { mutableStateOf("") }

    val widthFocus = remember { FocusRequester() }
    val heightFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        widthFocus.requestFocus()
        keyboard?.show()
    }

    val submit: () -> Unit = {
        val w = widthText.replace(',', '.').toDoubleOrNull()
        val h = heightText.replace(',', '.').toDoubleOrNull()
        if (w != null && h != null) onSubmit(w, h)
        // если пусто — ничего не делаем (пусть пользователь введёт или отменит)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .imePadding(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Размер:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))

            SizeField(
                value = widthText,
                hint = "≈ ${formatSize(hintWidth)}",
                focusRequester = widthFocus,
                imeAction = ImeAction.Next,
                onDone = { heightFocus.requestFocus() },
                onValueChange = { widthText = it },
                modifier = Modifier.weight(1f)
            )

            Text(
                text = "×",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            SizeField(
                value = heightText,
                hint = "≈ ${formatSize(hintHeight)}",
                focusRequester = heightFocus,
                imeAction = ImeAction.Done,
                onDone = { submit() },
                onValueChange = { heightText = it },
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = { submit() }) {
                Icon(Icons.Default.Check, contentDescription = "Применить", tint = Color(0xFF2E7D32))
            }
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "Отмена", tint = Color(0xFFC62828))
            }
        }
    }
}

@Composable
private fun SizeField(
    value: String,
    hint: String,
    focusRequester: FocusRequester,
    imeAction: ImeAction,
    onDone: () -> Unit,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        if (value.isEmpty()) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
        BasicTextField(
            value = value,
            onValueChange = { new ->
                onValueChange(new.filter { it.isDigit() || it == '.' || it == ',' })
            },
            singleLine = true,
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = MaterialTheme.typography.bodyLarge.fontSize
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = imeAction
            ),
            keyboardActions = KeyboardActions(
                onNext = { onDone() },
                onDone = { onDone() }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
        )
    }
}

private fun formatSize(value: Double): String {
    return if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        String.format("%.2f", value)
    }
}
