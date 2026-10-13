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
import com.sit.litecad.model.DimensionExtensionStyle
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

    // Пустая тонкая полоса панели свойств (для «hand», «dim», «erase» и прочих
    // инструментов без собственных настроек).
    val emptySettingsBar: @Composable () -> Unit = {
        TopSettingsBarDummy(
            isOrthoEnabled = settings.line.orthoEnabled,
            onOrthoToggle = {},
            exactInputEnabled = settings.line.exactInputEnabled,
            onExactInputToggle = {},
            currentThickness = settings.line.thickness,
            onThicknessChange = {},
            currentColor = settings.line.color,
            onColorChange = {},
            currentLineType = settings.line.lineType,
            onLineTypeChange = {},
            showControls = false
        )
    }

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
                    isOrthoEnabled = settings.line.orthoEnabled,
                )

                // Выдвижная панель инструментов — overlay поверх холста внизу рамки.
                Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                    ToolsBottomPanel(
                        selectedToolId = toolManager.activeToolId,
                        onToolSelect = { id -> toolManager.selectTool(id) },
                        settings = settings
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                ) {
                    when (toolManager.activeToolId) {
                        "line" -> {
                            TopSettingsBarDummy(
                                isOrthoEnabled = settings.line.orthoEnabled,
                                onOrthoToggle = { settings.line.orthoEnabled = !settings.line.orthoEnabled },
                                exactInputEnabled = settings.line.exactInputEnabled,
                                onExactInputToggle = { settings.line.exactInputEnabled = !settings.line.exactInputEnabled },
                                currentThickness = settings.line.thickness,
                                onThicknessChange = { settings.line.thickness = it },
                                currentColor = settings.line.color,
                                onColorChange = { settings.line.color = it },
                                currentLineType = settings.line.lineType,
                                onLineTypeChange = { settings.line.lineType = it },
                                showControls = true
                            )
                        }
                        "axis" -> {
                            TopSettingsBarDummy(
                                isOrthoEnabled = settings.axis.orthoEnabled,
                                onOrthoToggle = { settings.axis.orthoEnabled = !settings.axis.orthoEnabled },
                                exactInputEnabled = settings.axis.exactInputEnabled,
                                onExactInputToggle = { settings.axis.exactInputEnabled = !settings.axis.exactInputEnabled },
                                currentThickness = settings.axis.thickness,
                                onThicknessChange = { settings.axis.thickness = it },
                                currentColor = settings.axis.color,
                                onColorChange = { settings.axis.color = it },
                                showControls = true,
                                showOrtho = true,
                                showExactInput = true,
                                showLineType = false,
                            )
                        }
                        "rect" -> {
                            TopSettingsBarDummy(
                                isOrthoEnabled = false,
                                onOrthoToggle = {},
                                exactInputEnabled = settings.rect.exactInputEnabled,
                                onExactInputToggle = { settings.rect.exactInputEnabled = !settings.rect.exactInputEnabled },
                                currentThickness = settings.rect.thickness,
                                onThicknessChange = { settings.rect.thickness = it },
                                currentColor = settings.rect.color,
                                onColorChange = { settings.rect.color = it },
                                currentLineType = settings.rect.lineType,
                                onLineTypeChange = { settings.rect.lineType = it },
                                showControls = true,
                                // Ortho у прямоугольника не используется — кнопку скрываем.
                                showOrtho = false
                            )
                        }
                        "circle" -> {
                            TopSettingsBarDummy(
                                isOrthoEnabled = false,
                                onOrthoToggle = {},
                                exactInputEnabled = settings.circle.exactInputEnabled,
                                onExactInputToggle = { settings.circle.exactInputEnabled = !settings.circle.exactInputEnabled },
                                currentThickness = settings.circle.thickness,
                                onThicknessChange = { settings.circle.thickness = it },
                                currentColor = settings.circle.color,
                                onColorChange = { settings.circle.color = it },
                                currentLineType = settings.circle.lineType,
                                onLineTypeChange = { settings.circle.lineType = it },
                                showControls = true,
                                // Ortho у круга не используется — кнопку скрываем.
                                showOrtho = false
                            )
                        }
                        "sector" -> {
                            TopSettingsBarDummy(
                                isOrthoEnabled = false,
                                onOrthoToggle = {},
                                exactInputEnabled = settings.sector.exactInputEnabled,
                                onExactInputToggle = { settings.sector.exactInputEnabled = !settings.sector.exactInputEnabled },
                                currentThickness = settings.sector.thickness,
                                onThicknessChange = { settings.sector.thickness = it },
                                currentColor = settings.sector.color,
                                onColorChange = { settings.sector.color = it },
                                currentLineType = settings.sector.lineType,
                                onLineTypeChange = { settings.sector.lineType = it },
                                showControls = true,
                                // Ortho у сектора не используется — кнопку скрываем.
                                showOrtho = false
                            )
                        }
                        "arc" -> {
                            TopSettingsBarDummy(
                                isOrthoEnabled = false,
                                onOrthoToggle = {},
                                exactInputEnabled = settings.arc.exactInputEnabled,
                                onExactInputToggle = { settings.arc.exactInputEnabled = !settings.arc.exactInputEnabled },
                                currentThickness = settings.arc.thickness,
                                onThicknessChange = { settings.arc.thickness = it },
                                currentColor = settings.arc.color,
                                onColorChange = { settings.arc.color = it },
                                currentLineType = settings.arc.lineType,
                                onLineTypeChange = { settings.arc.lineType = it },
                                showControls = true,
                                showOrtho = false
                            )
                        }
                        "select" -> {
                            SelectionSettingsBar(
                                multiMode = settings.select.selectionMultiMode,
                                onMultiToggle = { settings.select.selectionMultiMode = !settings.select.selectionMultiMode },
                                onDelete = { toolManager.deleteSelected() },
                                onClear = { toolManager.selection.clear() }
                            )
                        }
                        "move" -> {
                            MoveSettingsBar(
                                orthoEnabled = settings.move.orthoEnabled,
                                onOrthoToggle = {
                                    settings.move.orthoEnabled = !settings.move.orthoEnabled
                                    if (!settings.move.orthoEnabled) {
                                        // Ortho выключили — сбрасываем «Точный», чтобы флаг не остался висеть
                                        settings.move.exactInputEnabled = false
                                    }
                                },
                                copyMode = settings.move.copyMode,
                                onCopyToggle = { settings.move.copyMode = !settings.move.copyMode },
                                exactInputEnabled = settings.move.exactInputEnabled,
                                onExactInputToggle = { settings.move.exactInputEnabled = !settings.move.exactInputEnabled },
                            )
                        }
                        "rotate" -> {
                            RotateSettingsBar(
                                copyMode = settings.rotate.copyMode,
                                onCopyToggle = { settings.rotate.copyMode = !settings.rotate.copyMode },
                                exactInputEnabled = settings.rotate.exactInputEnabled,
                                onExactInputToggle = { settings.rotate.exactInputEnabled = !settings.rotate.exactInputEnabled },
                            )
                        }
                        "mirror" -> {
                            MirrorSettingsBar(
                                orthoEnabled = settings.mirror.orthoEnabled,
                                onOrthoToggle = { settings.mirror.orthoEnabled = !settings.mirror.orthoEnabled },
                                copyMode = settings.mirror.copyMode,
                                onCopyToggle = { settings.mirror.copyMode = !settings.mirror.copyMode },
                            )
                        }
                        "offset" -> {
                            OffsetSettingsBar(
                                exactInputEnabled = settings.offset.exactInputEnabled,
                                onExactInputToggle = { settings.offset.exactInputEnabled = !settings.offset.exactInputEnabled },
                            )
                        }
                        "extend" -> {
                            // У Extend нет собственных настроек — пустая тонкая полоса.
                            emptySettingsBar()
                        }
                        "dim" -> {
                            DimSettingsBar(
                                orthoEnabled = settings.dim.orthoEnabled,
                                onOrthoToggle = { settings.dim.orthoEnabled = !settings.dim.orthoEnabled },
                                exactInputEnabled = settings.dim.exactInputEnabled,
                                onExactInputToggle = { settings.dim.exactInputEnabled = !settings.dim.exactInputEnabled },
                                extensionStyle = settings.dim.extensionStyle,
                                onExtensionStyleToggle = {
                                    settings.dim.extensionStyle =
                                        if (settings.dim.extensionStyle == DimensionExtensionStyle.FULL)
                                            DimensionExtensionStyle.SHORT else DimensionExtensionStyle.FULL
                                },
                                color = settings.dim.color,
                                onColorChange = { settings.dim.color = it },
                                thickness = settings.dim.thickness,
                                onThicknessChange = { settings.dim.thickness = it },
                            )
                        }
                        "erase" -> {
                            // Ластик: панель свойств скрыта — пустая тонкая полоса (как у «hand»).
                            emptySettingsBar()
                        }
                        else -> {
                            // hand, dim и всё остальное — пустая тонкая полоса
                            emptySettingsBar()
                        }
                    }
                }
            }
            val pending = toolManager.pendingInput
            when (pending) {
                is PendingInput.Length -> {
                    // У дуги «длина» — это высота (sagitta), у размера — отступ.
                    val label = when (toolManager.activeToolId) {
                        "arc" -> "Высота:"
                        "dim" -> "Отступ:"
                        else -> "Длина:"
                    }
                    Box(Modifier.align(Alignment.BottomCenter)) {
                        LengthInputPanel(
                            hint = pending.initial,
                            onSubmit = { toolManager.submitInput(it) },
                            onCancel = { toolManager.cancelInput() },
                            label = label
                        )
                    }
                }
                is PendingInput.RectangleSize -> {
                    Box(Modifier.align(Alignment.BottomCenter)) {
                        RectangleSizePanel(
                            hintWidth = pending.width,
                            hintHeight = pending.height,
                            onSubmit = { w, h -> toolManager.submitRectangleInput(w, h) },
                            onCancel = { toolManager.cancelRectangleInput() }
                        )
                    }
                }
                is PendingInput.SectorAngle -> {
                    Box(Modifier.align(Alignment.BottomCenter)) {
                        SectorAnglePanel(
                            initial = pending.initial,
                            onSubmit = { toolManager.submitSectorAngle(it) },
                            onCancel = { toolManager.cancelSectorAngle() }
                        )
                    }
                }
                is PendingInput.MoveDistance -> {
                    Box(Modifier.align(Alignment.BottomCenter)) {
                        MoveInputPanel(
                            initial = pending.initial,
                            axis = pending.axis,
                            onSubmit = { toolManager.submitMoveInput(it) },
                            onCancel = { toolManager.cancelMoveInput() }
                        )
                    }
                }
                is PendingInput.RotationAngle -> {
                    Box(Modifier.align(Alignment.BottomCenter)) {
                        RotationAnglePanel(
                            initial = pending.initial,
                            onSubmit = { toolManager.submitRotationAngle(it) },
                            onCancel = { toolManager.cancelRotationAngle() }
                        )
                    }
                }
                is PendingInput.OffsetDistance -> {
                    Box(Modifier.align(Alignment.BottomCenter)) {
                        OffsetInputPanel(
                            initial = pending.initial,
                            onSubmit = { toolManager.submitOffsetInput(it) },
                            onCancel = { toolManager.cancelOffsetInput() }
                        )
                    }
                }
                else -> {}
            }
        }
    }
}