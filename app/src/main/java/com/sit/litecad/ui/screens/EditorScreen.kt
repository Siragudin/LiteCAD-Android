package com.sit.litecad.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import com.sit.litecad.model.Selection
import com.sit.litecad.services.DocumentHistoryService
import com.sit.litecad.services.SnapService
import com.sit.litecad.settings.ToolSettings
import com.sit.litecad.storage.ProjectStorage
import com.sit.litecad.storage.ThumbnailGenerator
import com.sit.litecad.tools.PendingInput
import com.sit.litecad.tools.ToolManager
import com.sit.litecad.ui.canvas.CadCanvas
import com.sit.litecad.ui.canvas.Viewport

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    projectName: String,
    storage: ProjectStorage,
    onBack: () -> Unit
) {
    // 1. ИНИЦИАЛИЗАЦИЯ
    val viewport = remember { Viewport() }
    val document = remember { CadDocument() }
    val snapService = remember { SnapService() }
    val historyService = remember { DocumentHistoryService() }
    val settings = remember { ToolSettings() }
    val selection = remember { Selection() }
    val toolManager = remember { ToolManager(document, snapService, historyService, settings, selection) }

    // Загрузка проекта при открытии
    LaunchedEffect(projectName) {
        storage.loadIntoDocument(projectName, document)
    }

    // Сохранение и выход
    val saveAndExit: () -> Unit = {
        try {
            val thumb = ThumbnailGenerator.generate(document)
            storage.saveProject(document, projectName, thumb)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        onBack()
    }

    BackHandler { saveAndExit() }

    // Управляющие стейты интерфейса
    var isGridVisible by remember { mutableStateOf(true) }

    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                    title = { Text(projectName, style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = {
                        IconButton(onClick = { saveAndExit() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { saveAndExit() }) {
                            Icon(Icons.Default.Save, "Save")
                        }
                        IconButton(
                            onClick = { toolManager.undo() },
                            enabled = toolManager.canUndo
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, "Undo")
                        }
                        IconButton(
                            onClick = { toolManager.redo() },
                            enabled = toolManager.canRedo
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Redo, "Redo")
                        }
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
        },
        bottomBar = {
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
                CadCanvas(
                    viewport = viewport,
                    document = document,
                    toolManager = toolManager,
                    isGridVisible = isGridVisible,
                    isOrthoEnabled = settings.orthoEnabled,
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                ) {
                    when (toolManager.activeToolId) {
                        "line" -> {
                            TopSettingsBarDummy(
                                isOrthoEnabled = settings.orthoEnabled,
                                onOrthoToggle = { settings.orthoEnabled = !settings.orthoEnabled },
                                exactInputEnabled = settings.exactInputEnabled,
                                onExactInputToggle = { settings.exactInputEnabled = !settings.exactInputEnabled },
                                currentThickness = settings.thickness,
                                onThicknessChange = { settings.thickness = it },
                                currentColor = settings.color,
                                onColorChange = { settings.color = it },
                                currentLineType = settings.lineType,
                                onLineTypeChange = { settings.lineType = it },
                                showControls = true
                            )
                        }
                        "select" -> {
                            SelectionSettingsBar(
                                multiMode = settings.selectionMultiMode,
                                onMultiToggle = { settings.selectionMultiMode = !settings.selectionMultiMode },
                                onDelete = { toolManager.deleteSelected() },
                                onClear = { toolManager.selection.clear() }
                            )
                        }
                        else -> {
                            // hand и всё остальное — пустая тонкая полоса
                            TopSettingsBarDummy(
                                isOrthoEnabled = settings.orthoEnabled,
                                onOrthoToggle = {},
                                exactInputEnabled = settings.exactInputEnabled,
                                onExactInputToggle = {},
                                currentThickness = settings.thickness,
                                onThicknessChange = {},
                                currentColor = settings.color,
                                onColorChange = {},
                                currentLineType = settings.lineType,
                                onLineTypeChange = {},
                                showControls = false
                            )
                        }
                    }
                }
            }
            val pending = toolManager.pendingInput
            if (pending is PendingInput.Length) {
                Box(Modifier.align(Alignment.BottomCenter)) {
                    LengthInputPanel(
                        hint = pending.initial,
                        onSubmit = { toolManager.submitInput(it) },
                        onCancel = { toolManager.cancelInput() }
                    )
                }
            }
        }
    }
}