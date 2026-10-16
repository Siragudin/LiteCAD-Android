package com.sit.litecad.ui.screens

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sit.litecad.R
import com.sit.litecad.dxf.DxfExporter
import com.sit.litecad.geometry.PolygonGeometry
import com.sit.litecad.geometry.TopologyService
import com.sit.litecad.math.MathUtils
import com.sit.litecad.math.Point2
import com.sit.litecad.model.CadDocument
import com.sit.litecad.model.DimensionExtensionStyle
import com.sit.litecad.model.PolygonType
import com.sit.litecad.model.Selection
import com.sit.litecad.pdf.PdfExporter
import com.sit.litecad.services.DocumentHistoryService
import com.sit.litecad.services.SnapService
import com.sit.litecad.settings.QuickToolsPreferences
import com.sit.litecad.settings.ThemePreferences
import com.sit.litecad.settings.ToolSettings
import com.sit.litecad.storage.ProjectStorage
import com.sit.litecad.storage.ThumbnailGenerator
import com.sit.litecad.tools.PendingInput
import com.sit.litecad.tools.ToolManager
import com.sit.litecad.ui.canvas.CadCanvas
import com.sit.litecad.ui.canvas.Viewport
import com.sit.litecad.ui.theme.EditorDarkColorScheme
import com.sit.litecad.ui.theme.EditorLightColorScheme

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

    val context = LocalContext.current

    // Тема редактора читается один раз при входе в экран.
    val editorTheme = remember { ThemePreferences.getTheme(context) }
    val editorColorScheme = if (editorTheme == "dark") {
        EditorDarkColorScheme
    } else {
        EditorLightColorScheme
    }

    // Цвет холста задаётся отдельно от схемы: в тёмной теме — чёрный, в светлой — белый.
    val canvasColor = if (editorTheme == "dark") Color.Black else Color.White

    // Координаты пальца в мировых мм — приходят из CadCanvas (null, когда палец не на экране).
    var cursorWorld by remember { mutableStateOf<Point2?>(null) }

    // Второй ряд топбара: слева — длина/значения превью, справа — координаты пальца.
    // Ревизии читаем явно, чтобы ряд пересчитывался при изменениях документа/выделения.
    @Suppress("UNUSED_EXPRESSION")
    document.revision
    @Suppress("UNUSED_EXPRESSION")
    selection.revision

    val leftText = toolManager.activeTool?.previewMeasurements
        ?.takeIf { it.isNotEmpty() }
        ?.joinToString("  ") { "${it.label}: ${it.value}" }
        ?: buildSelectionSummaryText(document, selection)

    val rightText = cursorWorld?.let { "X: ${it.X.toInt()}  Y: ${it.Y.toInt()}" } ?: "X: 0  Y: 0"

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

    // Список инструментов быстрого доступа (боковой блок); читается из prefs.
    var quickTools by remember { mutableStateOf(QuickToolsPreferences.getTools(context)) }

    // Переключение инструмента в боковом блоке (long-press): добавить/убрать.
    val onQuickToolToggle: (String) -> Unit = { id ->
        QuickToolsPreferences.toggle(context, id)
        quickTools = QuickToolsPreferences.getTools(context)
    }

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

    MaterialTheme(colorScheme = editorColorScheme) {
        Scaffold(
            topBar = {
                Column(modifier = Modifier.statusBarsPadding()) {
                    // Верхний ряд: собственные размеры (≤44 dp) вместо штатных 64 dp топбара.
                    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { saveAndExit() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
                            }
                            Text(
                                text = projectName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp)
                            )
                            IconButton(onClick = { saveAndExit() }) {
                                Icon(Icons.Default.Save, stringResource(R.string.cd_save))
                            }
                            IconButton(
                                onClick = { toolManager.undo() },
                                enabled = toolManager.canUndo
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.cd_undo))
                            }
                            IconButton(
                                onClick = { toolManager.redo() },
                                enabled = toolManager.canRedo
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Redo, stringResource(R.string.cd_redo))
                            }
                            Box {
                                IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.cd_menu)) }
                                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text(if (isGridVisible) stringResource(R.string.menu_hide_grid) else stringResource(R.string.menu_show_grid)) },
                                        onClick = { isGridVisible = !isGridVisible; showMenu = false }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_export_pdf)) },
                                        onClick = {
                                            showMenu = false
                                            val result = PdfExporter.exportToDownloads(
                                                context = context,
                                                document = document,
                                                projectName = projectName
                                            )
                                            Toast.makeText(
                                                context,
                                                if (result != null) context.getString(R.string.pdf_saved, result)
                                                else context.getString(R.string.pdf_save_failed),
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_export_dxf)) },
                                        onClick = {
                                            showMenu = false
                                            val result = DxfExporter.exportToDownloads(
                                                context = context,
                                                document = document,
                                                projectName = projectName
                                            )
                                            Toast.makeText(
                                                context,
                                                if (result != null) context.getString(R.string.dxf_saved, result)
                                                else context.getString(R.string.dxf_save_failed),
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Второй ряд: слева — длина/значения превью инструмента, справа — координаты пальца.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(leftText, fontSize = 12.sp)
                        Text(rightText, fontSize = 12.sp)
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline)
                        .background(canvasColor)
                        .clipToBounds()
                ) {
                    CadCanvas(
                        viewport = viewport,
                        document = document,
                        toolManager = toolManager,
                        isGridVisible = isGridVisible,
                        isOrthoEnabled = settings.line.orthoEnabled,
                        isDarkTheme = (editorTheme == "dark"),
                        onCursorWorldChanged = { cursorWorld = it },
                    )

                    // Боковой блок быстрого доступа — над свёрнутой нижней панелью.
                    // Идёт в композиции ДО нижней панели, чтобы при раскрытии панель рисовалась поверх.
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 12.dp, bottom = 128.dp)
                    ) {
                        EditorQuickToolsPanel(
                            selectedToolId = toolManager.activeToolId,
                            onToolSelect = { id -> toolManager.selectTool(id) },
                            onToolLongPress = onQuickToolToggle,
                            toolIds = quickTools,
                        )
                    }

                    // Выдвижная панель инструментов — overlay поверх холста внизу рамки.
                    Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                        ToolsBottomPanel(
                            selectedToolId = toolManager.activeToolId,
                            onToolSelect = { id -> toolManager.selectTool(id) },
                            onToolLongPress = onQuickToolToggle,
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
                                    showControls = true,
                                    roundingMm = settings.line.roundingMm,
                                    onRoundingChange = { settings.line.roundingMm = it },
                                    showRounding = true
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
                                    roundingMm = settings.axis.roundingMm,
                                    onRoundingChange = { settings.axis.roundingMm = it },
                                    showRounding = true,
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
                                    showOrtho = false,
                                    roundingMm = settings.rect.roundingMm,
                                    onRoundingChange = { settings.rect.roundingMm = it },
                                    showRounding = true
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
                            "leader" -> {
                                LeaderSettingsBar(
                                    color = settings.leader.color,
                                    onColorChange = { settings.leader.color = it },
                                )
                            }
                            "fill" -> {
                                FillSettingsBar(
                                    color = settings.fill.color,
                                    onColorChange = { settings.fill.color = it },
                                )
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
                            "arc" -> stringResource(R.string.label_height)
                            "dim" -> stringResource(R.string.label_dim_offset)
                            else -> stringResource(R.string.label_length)
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
}

/** Текст сводки выделения для второго ряда топбара: площадь граней, иначе длина ребра/оси. */
private fun buildSelectionSummaryText(document: CadDocument, selection: Selection): String {
    // 1. Грани — суммарная площадь (только FACE). Значения в мм² → м².
    val faceAreaMm2 = selection.selectedPolygonIds
        .asSequence()
        .mapNotNull { id -> document.polygons.firstOrNull { p -> p.id == id } }
        .filter { it.type == PolygonType.FACE }
        .map { PolygonGeometry.getArea(document, it) }
        .sum()
    if (faceAreaMm2 > 0.0) {
        return String.format(java.util.Locale.US, "S: %.2f м²", faceAreaMm2 / 1_000_000.0)
    }

    // 2. Одно ребро / одна ось → длина.
    val singleEdge = if (selection.selectedEdgeIds.size == 1 &&
        selection.selectedAxisIds.isEmpty() && selection.selectedPolygonIds.isEmpty()
    ) {
        document.edgeById(selection.selectedEdgeIds.first())
    } else null
    val singleAxis = if (singleEdge == null && selection.selectedAxisIds.size == 1 &&
        selection.selectedEdgeIds.isEmpty() && selection.selectedPolygonIds.isEmpty()
    ) {
        document.axisById(selection.selectedAxisIds.first())
    } else null

    val length = when {
        singleEdge != null -> {
            val s = TopologyService.getEdgeStartPoint(document, singleEdge)
            val e = TopologyService.getEdgeEndPoint(document, singleEdge)
            MathUtils.distance(s, e)
        }
        singleAxis != null -> MathUtils.distance(singleAxis.Start, singleAxis.End)
        else -> 0.0
    }
    return "L: ${length.toInt()} мм"
}
