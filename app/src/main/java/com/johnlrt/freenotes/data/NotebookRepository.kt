package com.johnlrt.freenotes.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Local file-backed repository storing each notebook as an atomic JSON file under
 * `filesDir/notebooks/<id>.json` and workspace metadata (folders, favorite tool presets)
 * under `filesDir/workspace.json`.
 */
class NotebookRepository(private val context: Context) {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val notebooksDir: File
        get() = File(context.filesDir, "notebooks").apply { mkdirs() }

    private val mediaDir: File
        get() = File(context.filesDir, "media").apply { mkdirs() }

    private val workspaceFile: File
        get() = File(context.filesDir, "workspace.json")

    fun mediaDirectory(): File = mediaDir

    suspend fun loadWorkspace(): WorkspaceIndex = withContext(Dispatchers.IO) {
        if (!workspaceFile.exists()) {
            val initial = WorkspaceIndex(
                folders = listOf(
                    Folder(id = "folder_study", name = "Study & Lectures", colorArgb = 0xFF4F46E5),
                    Folder(id = "folder_pdfs", name = "PDF Annotations", colorArgb = 0xFF0D9488)
                )
            )
            saveWorkspace(initial)
            return@withContext initial
        }
        runCatching {
            json.decodeFromString<WorkspaceIndex>(workspaceFile.readText())
        }.getOrElse { WorkspaceIndex() }
    }

    suspend fun saveWorkspace(index: WorkspaceIndex) = withContext(Dispatchers.IO) {
        atomicWrite(workspaceFile, json.encodeToString(index))
    }

    suspend fun loadAllNotebooks(): List<Notebook> = withContext(Dispatchers.IO) {
        val files = notebooksDir.listFiles { f -> f.extension == "json" } ?: emptyArray()
        if (files.isEmpty()) {
            val welcome = createSampleWelcomeNotebook()
            saveNotebook(welcome)
            return@withContext listOf(welcome)
        }
        files.mapNotNull { file ->
            runCatching { json.decodeFromString<Notebook>(file.readText()) }.getOrNull()
        }.sortedByDescending { it.updatedAtMs }
    }

    suspend fun loadNotebook(id: String): Notebook? = withContext(Dispatchers.IO) {
        val file = File(notebooksDir, "$id.json")
        if (!file.exists()) return@withContext null
        runCatching { json.decodeFromString<Notebook>(file.readText()) }.getOrNull()
    }

    suspend fun saveNotebook(notebook: Notebook) = withContext(Dispatchers.IO) {
        val updated = notebook.copy(updatedAtMs = System.currentTimeMillis())
        val file = File(notebooksDir, "${updated.id}.json")
        atomicWrite(file, json.encodeToString(updated))
    }

    suspend fun deleteNotebookPermanently(id: String) = withContext(Dispatchers.IO) {
        File(notebooksDir, "$id.json").delete()
    }

    fun folderDepth(folderId: String?, folders: List<Folder>): Int {
        var depth = 0
        var curr = folderId
        val byId = folders.associateBy { it.id }
        while (curr != null && depth < 10) {
            depth++
            curr = byId[curr]?.parentId
        }
        return depth
    }

    private fun atomicWrite(target: File, content: String) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(content)
        if (!tmp.renameTo(target)) {
            target.writeText(content)
            tmp.delete()
        }
    }

    private fun createSampleWelcomeNotebook(): Notebook {
        val page1 = NotePage(
            template = PaperTemplate.CORNELL,
            paperColor = PaperColor.CREAM,
            orientation = PageOrientation.PORTRAIT,
            textBoxes = listOf(
                TextBoxItem(
                    x = 340f,
                    y = 85f,
                    width = 760f,
                    text = "Welcome to FreeNotes Tablet",
                    fontSizeSp = 30f,
                    colorArgb = 0xFF1E293B,
                    isBold = true
                ),
                TextBoxItem(
                    x = 340f,
                    y = 210f,
                    width = 780f,
                    text = "• Fountain, Ballpoint, Pencil, Art Pen & Behind-Ink Highlighter\n" +
                        "• 1€ One-Euro Stylus Stabilization + Low-Latency Prediction\n" +
                        "• Tap the amber Active Recall Tape below to test your memory!\n" +
                        "• Use the Floating Favorites bar, Layers, Split-View & PDF Export",
                    fontSizeSp = 19f,
                    colorArgb = 0xFF334155
                ),
                TextBoxItem(
                    x = 340f,
                    y = 520f,
                    width = 720f,
                    text = "Active Recall Key Formula:  E = mc²   |   F = m · a",
                    fontSizeSp = 22f,
                    colorArgb = 0xFF0F172A,
                    isBold = true
                )
            ),
            tapes = listOf(
                TapeItem(
                    startX = 635f,
                    startY = 538f,
                    endX = 965f,
                    endY = 538f,
                    thickness = 46f,
                    colorArgb = 0xFFF59E0B,
                    isRevealed = false
                )
            )
        )
        val page2 = NotePage(
            template = PaperTemplate.DOTTED,
            paperColor = PaperColor.WHITE,
            orientation = PageOrientation.PORTRAIT
        )
        return Notebook(
            id = UUID.randomUUID().toString(),
            title = "Quick Start & Active Recall",
            folderId = "folder_study",
            coverColorArgb = 0xFF4F46E5,
            coverAccentArgb = 0xFF818CF8,
            isFavorite = true,
            pages = listOf(page1, page2)
        )
    }
}
