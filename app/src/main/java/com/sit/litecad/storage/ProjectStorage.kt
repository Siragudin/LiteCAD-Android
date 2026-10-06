package com.sit.litecad.storage

import android.content.Context
import android.graphics.Bitmap
import com.sit.litecad.model.CadDocument
import com.sit.litecad.storage.dto.ProjectDocumentDto
import com.sit.litecad.storage.dto.SitManifestDto
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class ProjectInfo(
    val filePath: String,
    val name: String,
    val modifiedUtc: Long
)

class ProjectStorage(private val context: Context) {

    companion object {
        const val PROJECT_JSON_ENTRY = "project.json"
        const val PREVIEW_PNG_ENTRY = "preview.png"
        const val MANIFEST_JSON_ENTRY = "manifest.json"
        const val SIT_EXTENSION = ".sit"
    }

    // Папка внутри filesDir/LiteCAD/Projects
    val projectsDirectory: File
        get() = File(File(context.filesDir, "LiteCAD"), "Projects").apply { mkdirs() }

    private val previewCacheDir: File
        get() = File(context.cacheDir, "LiteCADThumbnails").apply { mkdirs() }

    // --- ПУТИ ---
    fun getProjectFile(projectName: String): File =
        File(projectsDirectory, sanitizeName(projectName) + SIT_EXTENSION)

    fun getProjectDisplayName(file: File): String =
        file.name.removeSuffix(SIT_EXTENSION)

    // --- СПИСОК ПРОЕКТОВ ---
    fun listProjects(): List<ProjectInfo> {
        projectsDirectory.mkdirs()
        return projectsDirectory
            .listFiles { f -> f.extension == "sit" }
            ?.map { file ->
                ProjectInfo(
                    filePath = file.absolutePath,
                    name = getProjectDisplayName(file),
                    modifiedUtc = file.lastModified()
                )
            }
            ?.sortedByDescending { it.modifiedUtc }
            ?: emptyList()
    }

    fun projectExists(projectName: String): Boolean =
        getProjectFile(projectName).exists()

    // --- СОХРАНЕНИЕ ---
    fun saveProject(
        document: CadDocument,
        projectName: String,
        thumbnail: Bitmap?,
        linearDisplayUnit: String = "Millimeters"
    ): File {
        projectsDirectory.mkdirs()

        val projectJson = ProjectDocumentSerializer.serialize(document, linearDisplayUnit)

        val manifest = SitManifestDto(
            format = "LiteCad",
            version = 1,
            projectName = projectName,
            modifiedUtc = formatUtcNow()
        )
        val manifestJson = GsonProvider.instance.toJson(manifest)

        val targetFile = getProjectFile(projectName)
        val tempFile = File(targetFile.parentFile, targetFile.name + ".tmp")
        if (tempFile.exists()) tempFile.delete()

        try {
            ZipOutputStream(FileOutputStream(tempFile)).use { zip ->
                // project.json
                zip.putNextEntry(ZipEntry(PROJECT_JSON_ENTRY))
                zip.write(projectJson.toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                // preview.png
                if (thumbnail != null) {
                    zip.putNextEntry(ZipEntry(PREVIEW_PNG_ENTRY))
                    thumbnail.compress(Bitmap.CompressFormat.PNG, 100, zip)
                    zip.closeEntry()
                }

                // manifest.json
                zip.putNextEntry(ZipEntry(MANIFEST_JSON_ENTRY))
                zip.write(manifestJson.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            if (targetFile.exists()) targetFile.delete()
            tempFile.renameTo(targetFile)

            // Обновляем превью в кэше
            if (thumbnail != null) {
                savePreviewToCache(thumbnail, projectName)
            }

            return targetFile
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            throw e
        }
    }

    // --- ЗАГРУЗКА ---
    fun loadProject(projectName: String): ProjectDocumentDto? {
        val file = getProjectFile(projectName)
        if (!file.exists()) return null

        return try {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry(PROJECT_JSON_ENTRY) ?: return null
                val json = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                ProjectDocumentSerializer.deserialize(json)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun loadIntoDocument(projectName: String, document: CadDocument): Boolean {
        val dto = loadProject(projectName) ?: return false
        return try {
            ProjectDocumentSerializer.apply(document, dto)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // --- УДАЛЕНИЕ ---
    fun deleteProject(projectName: String): Boolean {
        val file = getProjectFile(projectName)
        val previewFile = File(previewCacheDir, sanitizeName(projectName) + ".png")
        if (previewFile.exists()) previewFile.delete()
        return file.exists() && file.delete()
    }

    // --- ПРЕВЬЮ ---
    fun getPreviewPath(projectName: String): String? {
        val cacheFile = File(previewCacheDir, sanitizeName(projectName) + ".png")

        // 1. Из кэша
        if (cacheFile.exists()) return cacheFile.absolutePath

        // 2. Из .sit
        val sitFile = getProjectFile(projectName)
        if (!sitFile.exists()) return null

        return try {
            ZipFile(sitFile).use { zip ->
                val entry = zip.getEntry(PREVIEW_PNG_ENTRY) ?: return null
                zip.getInputStream(entry).use { input ->
                    FileOutputStream(cacheFile).use { output ->
                        input.copyTo(output)
                    }
                }
                cacheFile.absolutePath
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun savePreviewToCache(bitmap: Bitmap, projectName: String) {
        val file = File(previewCacheDir, sanitizeName(projectName) + ".png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    // --- УТИЛИТЫ ---
    private fun sanitizeName(name: String): String {
        val invalid = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
        var result = name.trim()
        for (c in invalid) result = result.replace(c, '_')
        result = result.trim('_', '.', ' ')
        return if (result.isEmpty()) "Drawing" else result
    }

    private fun formatUtcNow(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }
}