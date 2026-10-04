package com.sit.litecad.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sit.litecad.model.CadDocument
import com.sit.litecad.tools.ToolManager
import com.sit.litecad.ui.canvas.CadCanvas
import com.sit.litecad.ui.canvas.Viewport

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    projectName: String,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    // 1. ИНИЦИАЛИЗАЦИЯ: Поднимаем камеру, кроссплатформенный документ графа и менеджер инструментов
    val viewport = remember { Viewport() }
    val document = remember { CadDocument() }
    val toolManager = remember { ToolManager(document) }

    // Управляющие стейты интерфейса
    var isGridVisible by remember { mutableStateOf(true) }
    var isOrthoEnabled by remember { mutableStateOf(false) }
    var isSnapEnabled by remember { mutableStateOf(true) }
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { Text(projectName, style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = {
                        IconButton(onClick = { onBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { /* Undo (Будущая команда) */ }) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo") }
                        IconButton(onClick = { /* Redo (Будущая команда) */ }) { Icon(Icons.AutoMirrored.Filled.Redo, "Redo") }
                        Box {
                            IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "Menu") }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (isGridVisible) "Скрыть сетку" else "Показать сетку") },
                                    onClick = { isGridVisible = !isGridVisible; showMenu = false }
                                )
                            }
                        }
                    }
                )

                // Если активна не рука — выводим верхнюю контекстную панель параметров
                if (toolManager.activeToolId != "hand") {
                    TopSettingsBarDummy(
                        isOrthoEnabled = isOrthoEnabled,
                        isSnapEnabled = isSnapEnabled,
                        onOrthoToggle = { isOrthoEnabled = !isOrthoEnabled },
                        onSnapToggle = { isSnapEnabled = !isSnapEnabled }
                    )
                }
            }
        },
        bottomBar = {
            // Подключаем кнопки тулбара к стейту менеджера инструментов
            ToolsPanelDummy(
                selectedToolId = toolManager.activeToolId,
                onToolSelect = { id -> toolManager.selectTool(id) }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFFE0E0E0)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .border(1.dp, Color.Gray)
                    .background(Color.White)
                    .clipToBounds()
            ) {
                // ИНТЕГРАЦИЯ: Передаем наш новый CadDocument в обновленный холст
                CadCanvas(
                    viewport = viewport,
                    document = document,
                    toolManager = toolManager,
                    isGridVisible = isGridVisible
                )

                // Панель отладочной информации поверх холста
                Surface(
                    modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(text = "Инструмент: ${toolManager.activeToolId.uppercase()}", color = Color.White)
                        Text(text = "Ребер (Edges): ${document.edges.size}", color = Color.Green, style = MaterialTheme.typography.bodySmall)
                        Text(text = "Вершин (Vertices): ${document.vertices.size}", color = Color.Cyan, style = MaterialTheme.typography.bodySmall)
                        Text(text = "Zoom: ${String.format("%.2f", viewport.zoom)}", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
