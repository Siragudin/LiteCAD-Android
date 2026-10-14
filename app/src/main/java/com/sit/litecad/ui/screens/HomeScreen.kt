package com.sit.litecad.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.sit.litecad.R
import com.sit.litecad.model.CadDocument
import com.sit.litecad.pdf.PdfExporter
import com.sit.litecad.storage.ProjectInfo
import com.sit.litecad.storage.ProjectStorage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    storage: ProjectStorage,
    onOpenProject: (String) -> Unit
) {
    val context = LocalContext.current

    val projects = remember { mutableStateListOf<ProjectInfo>() }
    var searchQuery by remember { mutableStateOf("") }

    val displayedProjects = if (searchQuery.isEmpty()) {
        projects
    } else {
        projects.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    fun refreshProjects() {
        projects.clear()
        projects.addAll(storage.listProjects())
    }

    LaunchedEffect(Unit) { refreshProjects() }

    var showCreateDialog by remember { mutableStateOf(false) }
    var newProjectName by remember { mutableStateOf("") }

    var projectToDelete by remember { mutableStateOf<String?>(null) }
    var projectToRename by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }

    var importError by remember { mutableStateOf<String?>(null) }

    var showSettings by remember { mutableStateOf(false) }

    val headerColor = Color(0xFF383838)
    val bodyColor = Color(0xFF6E6E6E)

    // Лончер для выбора .sit файла
    val openLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val result = try {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException(context.getString(R.string.import_error_open_file))

                val fileName = queryFileName(context, uri) ?: "Imported"
                val baseName = fileName.removeSuffix(".sit").removeSuffix(".SIT")
                    .ifEmpty { "Imported" }

                val targetFile = storage.getUniqueFile(baseName)

                inputStream.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                null
            } catch (e: Exception) {
                e.message ?: context.getString(R.string.import_error_generic)
            }

            if (result == null) {
                refreshProjects()
            } else {
                importError = result
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Кнопка "Открыть"
                SmallFloatingActionButton(
                    onClick = {
                        openLauncher.launch(arrayOf("*/*"))
                    },
                    containerColor = headerColor,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, stringResource(R.string.fab_open), modifier = Modifier.size(28.dp))
                }

                // Кнопка "Создать"
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = headerColor,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(Icons.Default.Add, stringResource(R.string.fab_create), modifier = Modifier.size(32.dp))
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(bodyColor)
                .padding(padding)
        ) {
            // --- ШАПКА ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.25f)
                    .background(headerColor)
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.ui_header_title),
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 4.sp
                    )

                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(stringResource(R.string.search_projects_placeholder), color = Color.Gray) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp)),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true
                    )
                }
            }

            // --- ТЕЛО ---
            Box(
                modifier = Modifier
                    .weight(0.75f)
                    .fillMaxWidth()
            ) {
                if (displayedProjects.isEmpty()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = Color.LightGray
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(
                                if (searchQuery.isEmpty()) R.string.empty_project_list_hint
                                else R.string.empty_project_list_message
                            ),
                            color = Color.LightGray
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 140.dp),
                        contentPadding = PaddingValues(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(displayedProjects, key = { it.filePath }) { info ->
                            ProjectCard(
                                info = info,
                                previewPath = storage.getPreviewPath(info.name),
                                onClick = { onOpenProject(info.name) },
                                onDelete = { projectToDelete = info.name },
                                onRename = {
                                    projectToRename = info.name
                                    renameText = info.name
                                },
                                onShare = {
                                    val file = storage.getProjectFile(info.name)
                                    shareFile(context, file, "application/zip")
                                },
                                onExportPdf = {
                                    val doc = CadDocument()
                                    storage.loadIntoDocument(info.name, doc)
                                    val result = PdfExporter.exportToDownloads(context, doc, info.name)
                                    Toast.makeText(
                                        context,
                                        if (result != null) context.getString(R.string.pdf_saved, result)
                                        else context.getString(R.string.pdf_save_failed),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            )
                        }
                        item { Spacer(Modifier.height(160.dp)) }
                    }
                }
            }
        }

            // Кнопка "Настройки" (шестерёнка) — слева внизу.
            FloatingActionButton(
                onClick = { showSettings = true },
                containerColor = headerColor,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
                    .size(56.dp)
            ) {
                Icon(Icons.Default.Settings, stringResource(R.string.fab_settings), modifier = Modifier.size(28.dp))
            }
        }
    }

    // --- ДИАЛОГ СОЗДАНИЯ ---
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(stringResource(R.string.dialog_new_project_title)) },
            text = {
                OutlinedTextField(
                    value = newProjectName,
                    onValueChange = { newProjectName = it },
                    label = { Text(stringResource(R.string.input_name_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newProjectName.trim()
                        if (name.isNotEmpty()) {
                            showCreateDialog = false
                            newProjectName = ""
                            onOpenProject(name)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = headerColor)
                ) { Text(stringResource(R.string.button_create)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCreateDialog = false; newProjectName = "" },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Gray)
                ) { Text(stringResource(R.string.button_cancel)) }
            }
        )
    }

    // --- ДИАЛОГ УДАЛЕНИЯ ---
    projectToDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = { Text(stringResource(R.string.delete_project_title)) },
            text = { Text(stringResource(R.string.delete_project_message, name)) },
            confirmButton = {
                Button(
                    onClick = {
                        storage.deleteProject(name)
                        projectToDelete = null
                        refreshProjects()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB00020))
                ) { Text(stringResource(R.string.menu_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) { Text(stringResource(R.string.button_cancel)) }
            }
        )
    }

    // --- ДИАЛОГ ПЕРЕИМЕНОВАНИЯ ---
    projectToRename?.let { oldName ->
        AlertDialog(
            onDismissRequest = { projectToRename = null },
            title = { Text(stringResource(R.string.rename_project_title)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text(stringResource(R.string.rename_project_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newName = renameText.trim()
                        if (newName.isNotEmpty() && newName != oldName) {
                            val oldFile = storage.getProjectFile(oldName)
                            val newFile = storage.getProjectFile(newName)
                            oldFile.renameTo(newFile)
                            storage.deleteProject(oldName)
                        }
                        projectToRename = null
                        refreshProjects()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = headerColor)
                ) { Text(stringResource(R.string.button_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { projectToRename = null }) { Text(stringResource(R.string.button_cancel)) }
            }
        )
    }

    // --- ОШИБКА ИМПОРТА ---
    importError?.let { msg ->
        AlertDialog(
            onDismissRequest = { importError = null },
            title = { Text(stringResource(R.string.import_error_title)) },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { importError = null }) { Text(stringResource(R.string.button_ok)) }
            }
        )
    }

    // --- ЭКРАН НАСТРОЕК (полноэкранный overlay поверх HomeScreen) ---
    if (showSettings) {
        SettingsScreen(onBack = { showSettings = false })
    }
}

// Утилита: получить имя файла по content Uri
private fun queryFileName(context: Context, uri: android.net.Uri): String? {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
        }
    } catch (e: Exception) {
        null
    }
}

@Composable
fun ProjectCard(
    info: ProjectInfo,
    previewPath: String?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onExportPdf: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val date = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        .format(Date(info.modifiedUtc))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.85f)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFD9D9D9)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    if (previewPath != null) {
                        AsyncImage(
                            model = File(previewPath),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Icon(
                            Icons.Outlined.Description,
                            null,
                            tint = Color.LightGray,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Text(
                        text = info.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = date,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }

            Box(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        null,
                        tint = Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_export_pdf)) },
                        onClick = { onExportPdf(); showMenu = false },
                        leadingIcon = { Icon(Icons.Default.PictureAsPdf, null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_share)) },
                        onClick = { onShare(); showMenu = false },
                        leadingIcon = { Icon(Icons.Default.Share, null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_rename)) },
                        onClick = { onRename(); showMenu = false },
                        leadingIcon = { Icon(Icons.Default.Edit, null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_delete)) },
                        onClick = { onDelete(); showMenu = false },
                        leadingIcon = { Icon(Icons.Default.Delete, null) }
                    )
                }
            }
        }
    }
}

fun shareFile(context: Context, file: File, mimeType: String) {
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(intent, context.getString(R.string.share_chooser_title))
    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}