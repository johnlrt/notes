package com.johnlrt.freenotes.ui

import android.app.Application
import android.net.Uri
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.johnlrt.freenotes.data.AudioRecorderManager
import com.johnlrt.freenotes.data.BookmarkItem
import com.johnlrt.freenotes.data.EraserMode
import com.johnlrt.freenotes.data.FavoriteToolPreset
import com.johnlrt.freenotes.data.FlashcardItem
import com.johnlrt.freenotes.data.Folder
import com.johnlrt.freenotes.data.HighlighterStyle
import com.johnlrt.freenotes.data.HyperlinkItem
import com.johnlrt.freenotes.data.ImageCropper
import com.johnlrt.freenotes.data.ImageItem
import com.johnlrt.freenotes.data.Layer
import com.johnlrt.freenotes.data.NotePage
import com.johnlrt.freenotes.data.Notebook
import com.johnlrt.freenotes.data.NotebookRepository
import com.johnlrt.freenotes.data.PageOrientation
import com.johnlrt.freenotes.data.PaperColor
import com.johnlrt.freenotes.data.PaperTemplate
import com.johnlrt.freenotes.data.PdfEngine
import com.johnlrt.freenotes.data.ScrollDirection
import com.johnlrt.freenotes.data.ShapeKind
import com.johnlrt.freenotes.data.StickerItem
import com.johnlrt.freenotes.data.Stroke
import com.johnlrt.freenotes.data.StrokePoint
import com.johnlrt.freenotes.data.TapeItem
import com.johnlrt.freenotes.data.TextBoxItem
import com.johnlrt.freenotes.data.ToolType
import com.johnlrt.freenotes.data.WorkspaceIndex
import com.johnlrt.freenotes.engine.FlashcardScheduler
import com.johnlrt.freenotes.engine.HandwritingRecognizer
import com.johnlrt.freenotes.engine.HitTestEngine
import com.johnlrt.freenotes.engine.ReviewRating
import com.johnlrt.freenotes.engine.StrokeGeometry
import com.johnlrt.freenotes.ui.editor.StickerTemplate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

enum class LibrarySection {
    ALL_NOTES,
    RECENT,
    FAVORITES,
    FOLDER,
    TRASH
}

data class ToolConfig(
    val activeTool: ToolType = ToolType.FOUNTAIN_PEN,
    val penColorArgb: Long = 0xFF1A1C1E,
    val highlighterColorArgb: Long = 0xFFFACC15,
    val tapeColorArgb: Long = 0xFFF59E0B,
    val penQuickColors: List<Long> = listOf(0xFF1A1C1E, 0xFF2563EB, 0xFFDC2626),
    val highlighterQuickColors: List<Long> = listOf(0xFFFACC15, 0xFF34D399, 0xFFF472B6),
    val penWidth: Float = 4.5f,
    val highlighterWidth: Float = 26f,
    val eraserRadius: Float = 22f,
    val tapeThickness: Float = 38f,
    val penOpacity: Float = 1f,
    val highlighterOpacity: Float = 0.35f,
    val stability: Float = 0.15f,
    val pressureSensitivity: Float = 0.72f,
    val artNibAngleDeg: Float = 35f,
    val highlighterStyle: HighlighterStyle = HighlighterStyle.SOLID,
    val highlighterStraightLine: Boolean = true,
    val eraserMode: EraserMode = EraserMode.WHOLE_STROKE,
    val eraseHighlighterOnly: Boolean = false,
    val shapeKind: ShapeKind = ShapeKind.AUTO,
    val shapeFilled: Boolean = false,
    val shapeBorderDashed: Boolean = false
) {
    val activeColorArgb: Long
        get() = when (activeTool) {
            ToolType.HIGHLIGHTER -> highlighterColorArgb
            ToolType.TAPE -> tapeColorArgb
            else -> penColorArgb
        }

    val activeWidth: Float
        get() = when (activeTool) {
            ToolType.HIGHLIGHTER -> highlighterWidth
            ToolType.ERASER -> eraserRadius
            ToolType.TAPE -> tapeThickness
            else -> penWidth
        }
}

data class EditorUiState(
    val openNotebookIds: List<String> = emptyList(),
    val activeNotebookId: String? = null,
    val currentPageIndex: Int = 0,
    val activeLayerId: String = "layer_default",
    val toolConfig: ToolConfig = ToolConfig(),
    val scrollDirection: ScrollDirection = ScrollDirection.VERTICAL_CONTINUOUS,
    val stylusOnlyMode: Boolean = false,
    val zoomLocked: Boolean = false,
    val darkPdfInvert: Boolean = false,
    val showRuler: Boolean = false,
    val holdToShapeEnabled: Boolean = true,
    val scribbleToEraseEnabled: Boolean = true,
    val showZoomWindow: Boolean = false,
    val zoomRect: Rect = Rect(120f, 220f, 620f, 400f),
    val splitScreenDualPage: Boolean = false,
    val showStickyFavoritesBar: Boolean = true,
    val selectedStrokeIds: Set<String> = emptySet(),
    val selectedImageId: String? = null,
    val selectedStickerId: String? = null,
    val allTapesRevealed: Boolean = false,
    val isRecordingAudio: Boolean = false,
    val playingRecordingId: String? = null,
    val audioPlaybackSpeed: Float = 1.0f,
    val pipVideoUri: Uri? = null,
    val pomodoroSecondsLeft: Int = 25 * 60,
    val pomodoroRunning: Boolean = false,
    val showPomodoroWidget: Boolean = false,
    val isRecognizingHandwriting: Boolean = false,
    val statusMessage: String? = null
)

data class AppUiState(
    val notebooks: List<Notebook> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val favoriteTools: List<FavoriteToolPreset> = emptyList(),
    val customColors: List<Long> = emptyList(),
    val librarySection: LibrarySection = LibrarySection.ALL_NOTES,
    val selectedFolderId: String? = null,
    val searchQuery: String = "",
    val isGridView: Boolean = true,
    val editor: EditorUiState = EditorUiState()
) {
    val activeNotebook: Notebook?
        get() = notebooks.find { it.id == editor.activeNotebookId }

    val activePage: NotePage?
        get() = activeNotebook?.pages?.getOrNull(editor.currentPageIndex)
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = NotebookRepository(application.applicationContext)
    val audioManager = AudioRecorderManager(application.applicationContext)

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val undoStacks = mutableMapOf<String, ArrayDeque<NotePage>>()
    private val redoStacks = mutableMapOf<String, ArrayDeque<NotePage>>()

    private var saveJob: Job? = null
    private var pomodoroJob: Job? = null

    init {
        viewModelScope.launch {
            val workspace = repo.loadWorkspace()
            val notebooks = repo.loadAllNotebooks()
            _uiState.update {
                it.copy(
                    notebooks = notebooks,
                    folders = workspace.folders,
                    favoriteTools = workspace.favoriteTools,
                    customColors = workspace.customColors
                )
            }
        }
    }

    // region Library Actions

    fun selectLibrarySection(section: LibrarySection, folderId: String? = null) {
        _uiState.update { it.copy(librarySection = section, selectedFolderId = folderId) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun toggleGridView() {
        _uiState.update { it.copy(isGridView = !it.isGridView) }
    }

    fun createFolder(name: String, parentId: String? = null, colorArgb: Long = 0xFF2563EB) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val currentFolders = _uiState.value.folders
        if (repo.folderDepth(parentId, currentFolders) >= 5) return
        val folder = Folder(name = trimmed, parentId = parentId, colorArgb = colorArgb)
        _uiState.update { it.copy(folders = currentFolders + folder) }
        persistWorkspace()
    }

    fun createNotebook(
        title: String,
        folderId: String?,
        coverColorArgb: Long,
        coverAccentArgb: Long,
        paperColor: PaperColor,
        template: PaperTemplate,
        orientation: PageOrientation,
        isInfiniteCanvas: Boolean,
        coverPattern: com.johnlrt.freenotes.data.CoverPattern = com.johnlrt.freenotes.data.CoverPattern.CLASSIC_SPINE
    ) {
        val notebook = Notebook(
            title = title.trim().ifEmpty { "Untitled Notebook" },
            folderId = folderId,
            coverColorArgb = coverColorArgb,
            coverAccentArgb = coverAccentArgb,
            coverPattern = coverPattern,
            isInfiniteCanvas = isInfiniteCanvas,
            pages = listOf(
                NotePage(template = template, paperColor = paperColor, orientation = orientation)
            )
        )
        _uiState.update { state -> state.copy(notebooks = listOf(notebook) + state.notebooks) }
        viewModelScope.launch { repo.saveNotebook(notebook) }
        openNotebook(notebook.id)
    }

    fun importPdf(uri: Uri, displayName: String) {
        viewModelScope.launch {
            runCatching {
                val folderId = _uiState.value.selectedFolderId
                val notebook = PdfEngine.importPdfAsNotebook(
                    getApplication(),
                    uri,
                    displayName.removeSuffix(".pdf"),
                    folderId
                )
                _uiState.update { state -> state.copy(notebooks = listOf(notebook) + state.notebooks) }
                repo.saveNotebook(notebook)
                openNotebook(notebook.id)
            }.onFailure { err ->
                showStatus("PDF import failed: ${err.localizedMessage}")
            }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val notebook = PdfEngine.importNotebookBackup(getApplication(), uri)
                _uiState.update { state -> state.copy(notebooks = listOf(notebook) + state.notebooks) }
                repo.saveNotebook(notebook)
                openNotebook(notebook.id)
                showStatus("Restored backup: ${notebook.title}")
            }.onFailure { err ->
                showStatus("Backup restore failed: ${err.localizedMessage}")
            }
        }
    }

    fun toggleFavoriteNotebook(notebookId: String) {
        updateNotebookById(notebookId) { it.copy(isFavorite = !it.isFavorite) }
    }

    fun moveNotebookToTrash(notebookId: String) {
        updateNotebookById(notebookId) { it.copy(isTrashed = true) }
        if (_uiState.value.editor.activeNotebookId == notebookId) {
            closeNotebookTab(notebookId)
        }
    }

    fun restoreNotebookFromTrash(notebookId: String) {
        updateNotebookById(notebookId) { it.copy(isTrashed = false) }
    }

    fun deleteNotebookPermanently(notebookId: String) {
        _uiState.update { state -> state.copy(notebooks = state.notebooks.filterNot { it.id == notebookId }) }
        viewModelScope.launch { repo.deleteNotebookPermanently(notebookId) }
    }

    // endregion

    // region Tabs, Pages, Scroll, Zoom Window, Ruler

    fun openNotebook(notebookId: String) {
        val nb = _uiState.value.notebooks.find { it.id == notebookId } ?: return
        val firstLayerId = nb.pages.firstOrNull()?.layers?.firstOrNull()?.id ?: "layer_default"
        _uiState.update { state ->
            val tabs = if (notebookId in state.editor.openNotebookIds) {
                state.editor.openNotebookIds
            } else {
                (state.editor.openNotebookIds + notebookId).takeLast(6)
            }
            state.copy(
                editor = state.editor.copy(
                    openNotebookIds = tabs,
                    activeNotebookId = notebookId,
                    currentPageIndex = 0,
                    activeLayerId = firstLayerId,
                    selectedStrokeIds = emptySet(),
                    selectedImageId = null
                )
            )
        }
    }

    fun closeEditorToLibrary() {
        _uiState.update {
            it.copy(editor = it.editor.copy(activeNotebookId = null, selectedStrokeIds = emptySet(), selectedImageId = null))
        }
    }

    fun closeNotebookTab(notebookId: String) {
        _uiState.update { state ->
            val remaining = state.editor.openNotebookIds.filterNot { it == notebookId }
            val nextActive = if (state.editor.activeNotebookId == notebookId) remaining.lastOrNull() else state.editor.activeNotebookId
            state.copy(
                editor = state.editor.copy(
                    openNotebookIds = remaining,
                    activeNotebookId = nextActive,
                    currentPageIndex = 0,
                    selectedStrokeIds = emptySet(),
                    selectedImageId = null
                )
            )
        }
    }

    fun selectPage(index: Int) {
        val nb = _uiState.value.activeNotebook ?: return
        val clamped = index.coerceIn(0, nb.pages.lastIndex.coerceAtLeast(0))
        val layerId = nb.pages[clamped].layers.firstOrNull()?.id ?: "layer_default"
        _uiState.update {
            it.copy(
                editor = it.editor.copy(
                    currentPageIndex = clamped,
                    activeLayerId = layerId,
                    selectedStrokeIds = emptySet(),
                    selectedImageId = null
                )
            )
        }
    }

    fun addPage(template: PaperTemplate? = null, paperColor: PaperColor? = null) {
        val nb = _uiState.value.activeNotebook ?: return
        val ref = _uiState.value.activePage ?: nb.pages.lastOrNull() ?: NotePage()
        val newPage = NotePage(
            template = template ?: ref.template,
            paperColor = paperColor ?: ref.paperColor,
            orientation = ref.orientation
        )
        val insertIndex = (_uiState.value.editor.currentPageIndex + 1).coerceIn(0, nb.pages.size)
        val updatedPages = nb.pages.toMutableList().apply { add(insertIndex, newPage) }
        updateNotebookById(nb.id) { it.copy(pages = updatedPages) }
        selectPage(insertIndex)
    }

    fun duplicatePage(index: Int) {
        val nb = _uiState.value.activeNotebook ?: return
        val src = nb.pages.getOrNull(index) ?: return
        val copy = src.copy(id = UUID.randomUUID().toString())
        val updated = nb.pages.toMutableList().apply { add(index + 1, copy) }
        updateNotebookById(nb.id) { it.copy(pages = updated) }
        selectPage(index + 1)
    }

    fun deletePage(index: Int) {
        val nb = _uiState.value.activeNotebook ?: return
        if (nb.pages.size <= 1) return
        val updated = nb.pages.toMutableList().apply { removeAt(index) }
        updateNotebookById(nb.id) { it.copy(pages = updated) }
        selectPage((index - 1).coerceAtLeast(0))
    }

    fun movePage(fromIndex: Int, toIndex: Int) {
        val nb = _uiState.value.activeNotebook ?: return
        if (fromIndex !in nb.pages.indices || toIndex !in nb.pages.indices || fromIndex == toIndex) return
        val list = nb.pages.toMutableList()
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        updateNotebookById(nb.id) { it.copy(pages = list) }
        selectPage(toIndex)
    }

    fun changeCurrentPageTemplate(template: PaperTemplate, paperColor: PaperColor) {
        mutateCurrentPage(recordUndo = true) { page ->
            page.copy(template = template, paperColor = paperColor)
        }
    }

    fun toggleScrollDirection() {
        _uiState.update { state ->
            val next = if (state.editor.scrollDirection == ScrollDirection.VERTICAL_CONTINUOUS) {
                ScrollDirection.HORIZONTAL_PAGING
            } else {
                ScrollDirection.VERTICAL_CONTINUOUS
            }
            state.copy(editor = state.editor.copy(scrollDirection = next))
        }
    }

    fun toggleDarkPdfInvert() {
        _uiState.update { it.copy(editor = it.editor.copy(darkPdfInvert = !it.editor.darkPdfInvert)) }
    }

    fun toggleRuler() {
        _uiState.update { it.copy(editor = it.editor.copy(showRuler = !it.editor.showRuler)) }
    }

    fun toggleZoomWindow() {
        _uiState.update { it.copy(editor = it.editor.copy(showZoomWindow = !it.editor.showZoomWindow)) }
    }

    fun advanceZoomBoxRight() {
        val page = _uiState.value.activePage ?: return
        val pageW = page.orientation.widthPx
        val pageH = page.orientation.heightPx
        _uiState.update { state ->
            val r = state.editor.zoomRect
            val step = r.width * 0.72f
            val nextRect = if (r.right + step <= pageW - 40f) {
                r.translate(step, 0f)
            } else {
                val nextTop = (r.top + r.height * 0.85f).coerceAtMost(pageH - r.height - 40f)
                Rect(80f, nextTop, 80f + r.width, nextTop + r.height)
            }
            state.copy(editor = state.editor.copy(zoomRect = nextRect))
        }
    }

    fun stepZoomBoxLeft() {
        _uiState.update { state ->
            val r = state.editor.zoomRect
            val newLeft = (r.left - r.width * 0.5f).coerceAtLeast(40f)
            state.copy(editor = state.editor.copy(zoomRect = Rect(newLeft, r.top, newLeft + r.width, r.bottom)))
        }
    }

    fun zoomBoxNewLine() {
        val page = _uiState.value.activePage ?: return
        val pageH = page.orientation.heightPx
        _uiState.update { state ->
            val r = state.editor.zoomRect
            val nextTop = (r.top + r.height * 0.85f).coerceAtMost(pageH - r.height - 40f)
            state.copy(editor = state.editor.copy(zoomRect = Rect(80f, nextTop, 80f + r.width, nextTop + r.height)))
        }
    }

    // endregion

    // region Tool Config & Favorites

    fun updateToolConfig(transform: (ToolConfig) -> ToolConfig) {
        _uiState.update { state ->
            val nextCfg = transform(state.editor.toolConfig)
            state.copy(
                editor = state.editor.copy(
                    toolConfig = nextCfg,
                    selectedStrokeIds = if (nextCfg.activeTool != ToolType.LASSO) emptySet() else state.editor.selectedStrokeIds
                )
            )
        }
    }

    fun selectTool(tool: ToolType) {
        updateToolConfig { it.copy(activeTool = tool) }
    }

    fun saveCustomColor(argb: Long) {
        _uiState.update { state ->
            val updated = (listOf(argb) + state.customColors.filterNot { it == argb }).take(16)
            state.copy(customColors = updated)
        }
        persistWorkspace()
    }

    fun applyFavoritePreset(preset: FavoriteToolPreset) {
        updateToolConfig { cfg ->
            when (preset.toolType) {
                ToolType.HIGHLIGHTER -> cfg.copy(
                    activeTool = ToolType.HIGHLIGHTER,
                    highlighterColorArgb = preset.colorArgb,
                    highlighterWidth = preset.width,
                    highlighterOpacity = preset.opacity,
                    highlighterStyle = preset.highlighterStyle
                )
                ToolType.TAPE -> cfg.copy(
                    activeTool = ToolType.TAPE,
                    tapeColorArgb = preset.colorArgb,
                    tapeThickness = preset.width
                )
                else -> cfg.copy(
                    activeTool = preset.toolType,
                    penColorArgb = preset.colorArgb,
                    penWidth = preset.width,
                    penOpacity = preset.opacity
                )
            }
        }
    }

    fun addCurrentToolToFavorites() {
        val cfg = _uiState.value.editor.toolConfig
        val preset = FavoriteToolPreset(
            label = cfg.activeTool.name.lowercase().replaceFirstChar { it.uppercase() },
            toolType = cfg.activeTool,
            colorArgb = cfg.activeColorArgb,
            width = cfg.activeWidth,
            opacity = if (cfg.activeTool == ToolType.HIGHLIGHTER) cfg.highlighterOpacity else cfg.penOpacity,
            highlighterStyle = cfg.highlighterStyle
        )
        _uiState.update { it.copy(favoriteTools = (it.favoriteTools + preset).takeLast(14)) }
        persistWorkspace()
    }

    fun toggleStylusOnlyMode() {
        _uiState.update { it.copy(editor = it.editor.copy(stylusOnlyMode = !it.editor.stylusOnlyMode)) }
    }

    fun toggleZoomLock() {
        _uiState.update { it.copy(editor = it.editor.copy(zoomLocked = !it.editor.zoomLocked)) }
    }

    fun toggleSplitScreenDualPage() {
        _uiState.update { it.copy(editor = it.editor.copy(splitScreenDualPage = !it.editor.splitScreenDualPage)) }
    }

    fun toggleStickyFavoritesBar() {
        _uiState.update { it.copy(editor = it.editor.copy(showStickyFavoritesBar = !it.editor.showStickyFavoritesBar)) }
    }

    // endregion

    // region Drawing, Eraser, Lasso, ML Kit OCR, Tape, Text, Images, Stickers, Hyperlinks, Layers

    fun commitStrokeOnPage(pageIndex: Int, rawPoints: List<StrokePoint>, toolOverride: ToolType? = null) {
        if (pageIndex != _uiState.value.editor.currentPageIndex) {
            selectPage(pageIndex)
        }
        commitStroke(rawPoints, toolOverride)
    }

    fun commitStroke(rawPoints: List<StrokePoint>, toolOverride: ToolType? = null) {
        if (rawPoints.size < 2) return
        val page = _uiState.value.activePage ?: return
        val layer = page.layers.find { it.id == _uiState.value.editor.activeLayerId }
        if (layer?.isLocked == true) {
            showStatus("Active layer is locked")
            return
        }

        val cfg = _uiState.value.editor.toolConfig
        val effectiveTool = toolOverride ?: cfg.activeTool

        // Freenotes Scribble-to-Erase gesture in any pen mode
        if (_uiState.value.editor.scribbleToEraseEnabled &&
            effectiveTool in listOf(ToolType.FOUNTAIN_PEN, ToolType.BALLPOINT, ToolType.PENCIL, ToolType.ART_PEN) &&
            StrokeGeometry.isScribbleGesture(rawPoints)
        ) {
            val box = StrokeGeometry.boundingBoxOfPoints(rawPoints)
            if (box != null) {
                val lockedLayerIds = page.layers.filter { it.isLocked || !it.isVisible }.map { it.id }.toSet()
                val pad = 18f
                val remaining = page.strokes.filterNot { s ->
                    if (s.layerId in lockedLayerIds) false
                    else s.points.any { p ->
                        p.x in (box[0] - pad)..(box[2] + pad) && p.y in (box[1] - pad)..(box[3] + pad)
                    }
                }
                val erasedCount = page.strokes.size - remaining.size
                if (erasedCount > 0) {
                    mutateCurrentPage(recordUndo = true) { p -> p.copy(strokes = remaining) }
                    showStatus("Scribble erased $erasedCount stroke${if (erasedCount == 1) "" else "s"}")
                    return
                }
            }
        }

        if (effectiveTool == ToolType.TAPE) {
            val tape = TapeItem(
                layerId = _uiState.value.editor.activeLayerId,
                startX = rawPoints.first().x,
                startY = rawPoints.first().y,
                endX = rawPoints.last().x,
                endY = rawPoints.last().y,
                thickness = cfg.tapeThickness,
                colorArgb = cfg.tapeColorArgb,
                isRevealed = false
            )
            mutateCurrentPage(recordUndo = true) { p -> p.copy(tapes = p.tapes + tape) }
            return
        }

        val finalPoints = when {
            effectiveTool == ToolType.SHAPE -> StrokeGeometry.snapToShape(rawPoints, cfg.shapeKind)
            effectiveTool == ToolType.HIGHLIGHTER && cfg.highlighterStraightLine -> listOf(rawPoints.first(), rawPoints.last())
            else -> rawPoints
        }

        val stroke = Stroke(
            layerId = _uiState.value.editor.activeLayerId,
            toolType = effectiveTool,
            colorArgb = if (effectiveTool == ToolType.HIGHLIGHTER) cfg.highlighterColorArgb else cfg.penColorArgb,
            baseWidth = if (effectiveTool == ToolType.HIGHLIGHTER) cfg.highlighterWidth else cfg.penWidth,
            opacity = if (effectiveTool == ToolType.HIGHLIGHTER) cfg.highlighterOpacity else cfg.penOpacity,
            pressureSensitivity = cfg.pressureSensitivity,
            nibAngleDeg = cfg.artNibAngleDeg,
            highlighterStyle = cfg.highlighterStyle,
            isStraightLine = effectiveTool == ToolType.HIGHLIGHTER && cfg.highlighterStraightLine,
            isFilledShape = effectiveTool == ToolType.SHAPE && cfg.shapeFilled,
            fillColorArgb = if (effectiveTool == ToolType.SHAPE && cfg.shapeFilled) cfg.penColorArgb else null,
            borderDashed = effectiveTool == ToolType.SHAPE && cfg.shapeBorderDashed,
            points = finalPoints
        )

        mutateCurrentPage(recordUndo = true) { p -> p.copy(strokes = p.strokes + stroke) }
    }

    fun eraseAtOnPage(pageIndex: Int, x: Float, y: Float, recordUndoOnFirstHit: Boolean) {
        if (pageIndex != _uiState.value.editor.currentPageIndex) {
            selectPage(pageIndex)
        }
        eraseAt(x, y, recordUndoOnFirstHit)
    }

    fun eraseAt(x: Float, y: Float, recordUndoOnFirstHit: Boolean) {
        val page = _uiState.value.activePage ?: return
        val cfg = _uiState.value.editor.toolConfig
        val lockedLayerIds = page.layers.filter { it.isLocked || !it.isVisible }.map { it.id }.toSet()

        var changed = false
        val updatedStrokes = mutableListOf<Stroke>()

        for (stroke in page.strokes) {
            if (stroke.layerId in lockedLayerIds) {
                updatedStrokes.add(stroke)
                continue
            }
            if (cfg.eraseHighlighterOnly && stroke.toolType != ToolType.HIGHLIGHTER) {
                updatedStrokes.add(stroke)
                continue
            }
            when (cfg.eraserMode) {
                EraserMode.WHOLE_STROKE -> {
                    if (HitTestEngine.hitTestStroke(stroke, x, y, cfg.eraserRadius)) {
                        changed = true
                    } else {
                        updatedStrokes.add(stroke)
                    }
                }
                EraserMode.PARTIAL_PIXEL -> {
                    val split = HitTestEngine.splitStrokeByEraser(stroke, x, y, cfg.eraserRadius)
                    if (split.size != 1 || split.first() != stroke) changed = true
                    updatedStrokes.addAll(split)
                }
            }
        }

        val updatedTapes = page.tapes.filterNot { tape ->
            val hit = tape.layerId !in lockedLayerIds && !cfg.eraseHighlighterOnly && HitTestEngine.hitTestTape(tape, x, y)
            if (hit) changed = true
            hit
        }

        if (changed) {
            mutateCurrentPage(recordUndo = recordUndoOnFirstHit) { p ->
                p.copy(strokes = updatedStrokes, tapes = updatedTapes)
            }
        }
    }

    fun selectLassoPolygon(polygon: List<StrokePoint>) {
        val page = _uiState.value.activePage ?: return
        val visibleUnlocked = page.layers.filter { it.isVisible && !it.isLocked }.map { it.id }.toSet()
        val selected = page.strokes.filter {
            it.layerId in visibleUnlocked && HitTestEngine.isStrokeInLasso(it, polygon)
        }.map { it.id }.toSet()
        _uiState.update { it.copy(editor = it.editor.copy(selectedStrokeIds = selected)) }
    }

    fun translateSelectedStrokes(dx: Float, dy: Float, recordUndo: Boolean = false) {
        val selected = _uiState.value.editor.selectedStrokeIds
        if (selected.isEmpty()) return
        mutateCurrentPage(recordUndo = recordUndo) { page ->
            page.copy(
                strokes = page.strokes.map { stroke ->
                    if (stroke.id in selected) {
                        stroke.copy(points = stroke.points.map { pt -> pt.copy(x = pt.x + dx, y = pt.y + dy) })
                    } else stroke
                }
            )
        }
    }

    fun recolorSelectedStrokes(colorArgb: Long) {
        val selected = _uiState.value.editor.selectedStrokeIds
        if (selected.isEmpty()) return
        mutateCurrentPage(recordUndo = true) { page ->
            page.copy(strokes = page.strokes.map { if (it.id in selected) it.copy(colorArgb = colorArgb) else it })
        }
    }

    fun duplicateSelectedStrokes() {
        val selected = _uiState.value.editor.selectedStrokeIds
        if (selected.isEmpty()) return
        val newIds = mutableSetOf<String>()
        mutateCurrentPage(recordUndo = true) { page ->
            val clones = page.strokes.filter { it.id in selected }.map { stroke ->
                val cloneId = UUID.randomUUID().toString()
                newIds.add(cloneId)
                stroke.copy(id = cloneId, points = stroke.points.map { it.copy(x = it.x + 28f, y = it.y + 28f) })
            }
            page.copy(strokes = page.strokes + clones)
        }
        _uiState.update { it.copy(editor = it.editor.copy(selectedStrokeIds = newIds)) }
    }

    fun deleteSelectedStrokes() {
        val selected = _uiState.value.editor.selectedStrokeIds
        if (selected.isEmpty()) return
        mutateCurrentPage(recordUndo = true) { page ->
            page.copy(strokes = page.strokes.filterNot { it.id in selected })
        }
        _uiState.update { it.copy(editor = it.editor.copy(selectedStrokeIds = emptySet())) }
    }

    /**
     * Converts Lasso-selected handwritten strokes into an editable [TextBoxItem] using
     * Google ML Kit Digital Ink Recognition (matching Freenotes' Handwriting-to-Text).
     */
    fun convertSelectedStrokesToText() {
        val page = _uiState.value.activePage ?: return
        val selectedIds = _uiState.value.editor.selectedStrokeIds
        val strokes = page.strokes.filter { it.id in selectedIds }
        if (strokes.isEmpty()) return
        val box = HitTestEngine.boundingBoxOfStrokes(strokes) ?: return

        _uiState.update { it.copy(editor = it.editor.copy(isRecognizingHandwriting = true)) }
        viewModelScope.launch {
            val result = HandwritingRecognizer.recognize(strokes)
            _uiState.update { it.copy(editor = it.editor.copy(isRecognizingHandwriting = false)) }
            result.onSuccess { recognized ->
                mutateCurrentPage(recordUndo = true) { p ->
                    p.copy(
                        strokes = p.strokes.filterNot { it.id in selectedIds },
                        textBoxes = p.textBoxes + TextBoxItem(
                            layerId = _uiState.value.editor.activeLayerId,
                            x = box[0],
                            y = box[1],
                            width = (box[2] - box[0]).coerceAtLeast(240f),
                            text = recognized,
                            colorArgb = strokes.first().colorArgb
                        )
                    )
                }
                clearSelection()
                showStatus("Converted handwriting → \"$recognized\"")
            }.onFailure { err ->
                showStatus("Handwriting OCR: ${err.localizedMessage ?: "model downloading"}")
            }
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(editor = it.editor.copy(selectedStrokeIds = emptySet(), selectedImageId = null)) }
    }

    fun tryToggleTapeAt(x: Float, y: Float): Boolean {
        val page = _uiState.value.activePage ?: return false
        val target = page.tapes.lastOrNull { HitTestEngine.hitTestTape(it, x, y) } ?: return false
        mutateCurrentPage(recordUndo = false) { p ->
            p.copy(tapes = p.tapes.map { if (it.id == target.id) it.copy(isRevealed = !it.isRevealed) else it })
        }
        return true
    }

    fun toggleAllTapesOnPage() {
        val reveal = !_uiState.value.editor.allTapesRevealed
        mutateCurrentPage(recordUndo = false) { page ->
            page.copy(tapes = page.tapes.map { it.copy(isRevealed = reveal) })
        }
        _uiState.update { it.copy(editor = it.editor.copy(allTapesRevealed = reveal)) }
    }

    fun addOrUpdateTextBox(x: Float, y: Float, text: String, existingId: String? = null) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() && existingId != null) {
            mutateCurrentPage(recordUndo = true) { p -> p.copy(textBoxes = p.textBoxes.filterNot { it.id == existingId }) }
            return
        }
        if (trimmed.isEmpty()) return
        val color = _uiState.value.editor.toolConfig.penColorArgb
        mutateCurrentPage(recordUndo = true) { page ->
            if (existingId != null) {
                page.copy(textBoxes = page.textBoxes.map { if (it.id == existingId) it.copy(text = trimmed) else it })
            } else {
                page.copy(
                    textBoxes = page.textBoxes + TextBoxItem(
                        layerId = _uiState.value.editor.activeLayerId,
                        x = x,
                        y = y,
                        text = trimmed,
                        colorArgb = color
                    )
                )
            }
        }
    }

    // Images & Freehand/Rect Crop
    fun insertImageFromUri(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val (file, dims) = ImageCropper.importImageToLocal(getApplication(), uri)
                val aspect = dims.first.toFloat() / dims.second.coerceAtLeast(1)
                val targetW = 420f
                val targetH = (targetW / aspect).coerceIn(140f, 540f)
                val item = ImageItem(
                    layerId = _uiState.value.editor.activeLayerId,
                    filePath = file.absolutePath,
                    x = 180f,
                    y = 240f,
                    width = targetW,
                    height = targetH
                )
                mutateCurrentPage(recordUndo = true) { p -> p.copy(images = p.images + item) }
                _uiState.update { it.copy(editor = it.editor.copy(selectedImageId = item.id)) }
                showStatus("Image added — tap image to crop or delete")
            }.onFailure { err ->
                showStatus("Image import failed: ${err.localizedMessage}")
            }
        }
    }

    fun selectImage(imageId: String?) {
        _uiState.update { it.copy(editor = it.editor.copy(selectedImageId = imageId)) }
    }

    fun cropSelectedImageRect(l: Float, t: Float, r: Float, b: Float) {
        val page = _uiState.value.activePage ?: return
        val img = page.images.find { it.id == _uiState.value.editor.selectedImageId } ?: return
        viewModelScope.launch {
            runCatching {
                val croppedFile = ImageCropper.cropRectangular(getApplication(), img.filePath, l, t, r, b)
                mutateCurrentPage(recordUndo = true) { p ->
                    p.copy(images = p.images.map { if (it.id == img.id) it.copy(filePath = croppedFile.absolutePath) else it })
                }
                showStatus("Image cropped")
            }.onFailure { showStatus("Crop failed: ${it.localizedMessage}") }
        }
    }

    fun cropSelectedImageFreehand(polygon: List<Pair<Float, Float>>) {
        val page = _uiState.value.activePage ?: return
        val img = page.images.find { it.id == _uiState.value.editor.selectedImageId } ?: return
        viewModelScope.launch {
            runCatching {
                val croppedFile = ImageCropper.cropFreehand(getApplication(), img.filePath, polygon)
                mutateCurrentPage(recordUndo = true) { p ->
                    p.copy(images = p.images.map { if (it.id == img.id) it.copy(filePath = croppedFile.absolutePath) else it })
                }
                showStatus("Freehand crop applied")
            }.onFailure { showStatus("Freehand crop failed: ${it.localizedMessage}") }
        }
    }

    fun deleteSelectedImage() {
        val imgId = _uiState.value.editor.selectedImageId ?: return
        mutateCurrentPage(recordUndo = true) { p -> p.copy(images = p.images.filterNot { it.id == imgId }) }
        _uiState.update { it.copy(editor = it.editor.copy(selectedImageId = null)) }
    }

    fun duplicateSelectedImage() {
        val imgId = _uiState.value.editor.selectedImageId ?: return
        val page = _uiState.value.activePage ?: return
        val img = page.images.find { it.id == imgId } ?: return
        val copy = img.copy(id = UUID.randomUUID().toString(), x = img.x + 36f, y = img.y + 36f)
        mutateCurrentPage(recordUndo = true) { p -> p.copy(images = p.images + copy) }
        _uiState.update { it.copy(editor = it.editor.copy(selectedImageId = copy.id)) }
    }

    fun transformImage(
        imageId: String,
        dx: Float = 0f,
        dy: Float = 0f,
        newWidth: Float? = null,
        newHeight: Float? = null,
        newRotationDeg: Float? = null,
        recordUndo: Boolean = false
    ) {
        mutateCurrentPage(recordUndo = recordUndo) { p ->
            p.copy(
                images = p.images.map { img ->
                    if (img.id == imageId) {
                        img.copy(
                            x = img.x + dx,
                            y = img.y + dy,
                            width = (newWidth ?: img.width).coerceIn(60f, 2400f),
                            height = (newHeight ?: img.height).coerceIn(60f, 2400f),
                            rotationDeg = newRotationDeg ?: img.rotationDeg
                        )
                    } else img
                }
            )
        }
    }

    // Stickers
    fun addSticker(tpl: StickerTemplate) {
        val item = StickerItem(
            layerId = _uiState.value.editor.activeLayerId,
            label = tpl.label,
            emoji = tpl.emoji,
            x = 220f,
            y = 260f,
            width = 190f,
            height = 76f,
            bgColorArgb = tpl.bgArgb,
            borderColorArgb = tpl.borderArgb,
            textColorArgb = tpl.textArgb
        )
        mutateCurrentPage(recordUndo = true) { p -> p.copy(stickers = p.stickers + item) }
        _uiState.update { it.copy(editor = it.editor.copy(selectedStickerId = item.id, selectedImageId = null)) }
    }

    fun selectSticker(stickerId: String?) {
        _uiState.update {
            it.copy(
                editor = it.editor.copy(
                    selectedStickerId = stickerId,
                    selectedImageId = if (stickerId != null) null else it.editor.selectedImageId
                )
            )
        }
    }

    fun transformSticker(
        stickerId: String,
        dx: Float = 0f,
        dy: Float = 0f,
        newWidth: Float? = null,
        newHeight: Float? = null,
        newRotationDeg: Float? = null,
        recordUndo: Boolean = false
    ) {
        mutateCurrentPage(recordUndo = recordUndo) { p ->
            p.copy(
                stickers = p.stickers.map { st ->
                    if (st.id == stickerId) {
                        st.copy(
                            x = st.x + dx,
                            y = st.y + dy,
                            width = (newWidth ?: st.width).coerceIn(80f, 900f),
                            height = (newHeight ?: st.height).coerceIn(48f, 600f),
                            rotationDeg = newRotationDeg ?: st.rotationDeg
                        )
                    } else st
                }
            )
        }
    }

    fun duplicateSelectedSticker() {
        val stId = _uiState.value.editor.selectedStickerId ?: return
        val page = _uiState.value.activePage ?: return
        val st = page.stickers.find { it.id == stId } ?: return
        val copy = st.copy(id = UUID.randomUUID().toString(), x = st.x + 32f, y = st.y + 32f)
        mutateCurrentPage(recordUndo = true) { p -> p.copy(stickers = p.stickers + copy) }
        _uiState.update { it.copy(editor = it.editor.copy(selectedStickerId = copy.id)) }
    }

    fun deleteSelectedSticker() {
        val stId = _uiState.value.editor.selectedStickerId ?: return
        mutateCurrentPage(recordUndo = true) { p -> p.copy(stickers = p.stickers.filterNot { it.id == stId }) }
        _uiState.update { it.copy(editor = it.editor.copy(selectedStickerId = null)) }
    }

    fun toggleHoldToShape() {
        _uiState.update { it.copy(editor = it.editor.copy(holdToShapeEnabled = !it.editor.holdToShapeEnabled)) }
    }

    fun toggleScribbleToErase() {
        _uiState.update { it.copy(editor = it.editor.copy(scribbleToEraseEnabled = !it.editor.scribbleToEraseEnabled)) }
    }

    fun toggleInfiniteCanvas() {
        val nb = _uiState.value.activeNotebook ?: return
        updateNotebookById(nb.id) { it.copy(isInfiniteCanvas = !it.isInfiniteCanvas) }
    }

    // Hyperlinks
    fun addHyperlink(label: String, targetPageIndex: Int, x: Float = 240f, y: Float = 320f) {
        val item = HyperlinkItem(
            layerId = _uiState.value.editor.activeLayerId,
            label = label.ifBlank { "→ Page ${targetPageIndex + 1}" },
            targetPageIndex = targetPageIndex,
            x = x,
            y = y
        )
        mutateCurrentPage(recordUndo = true) { p -> p.copy(hyperlinks = p.hyperlinks + item) }
    }

    // Bookmarks
    fun addBookmark(title: String) {
        val nb = _uiState.value.activeNotebook ?: return
        val bm = BookmarkItem(pageIndex = _uiState.value.editor.currentPageIndex, title = title)
        updateNotebookById(nb.id) { it.copy(bookmarks = it.bookmarks + bm) }
    }

    fun deleteBookmark(id: String) {
        val nb = _uiState.value.activeNotebook ?: return
        updateNotebookById(nb.id) { it.copy(bookmarks = it.bookmarks.filterNot { b -> b.id == id }) }
    }

    // Flashcards
    fun addFlashcard(front: String, back: String) {
        val nb = _uiState.value.activeNotebook ?: return
        updateNotebookById(nb.id) { it.copy(flashcards = it.flashcards + FlashcardItem(front = front, back = back)) }
    }

    fun rateFlashcard(cardId: String, rating: ReviewRating) {
        val nb = _uiState.value.activeNotebook ?: return
        updateNotebookById(nb.id) { notebook ->
            notebook.copy(
                flashcards = notebook.flashcards.map { c ->
                    if (c.id == cardId) FlashcardScheduler.review(c, rating) else c
                }
            )
        }
    }

    fun deleteFlashcard(cardId: String) {
        val nb = _uiState.value.activeNotebook ?: return
        updateNotebookById(nb.id) { it.copy(flashcards = it.flashcards.filterNot { c -> c.id == cardId }) }
    }

    // PiP Lecture Video
    fun openPipVideo(uri: Uri) {
        _uiState.update { it.copy(editor = it.editor.copy(pipVideoUri = uri)) }
    }

    fun closePipVideo() {
        _uiState.update { it.copy(editor = it.editor.copy(pipVideoUri = null)) }
    }

    fun capturePipVideoFrame(positionMs: Long) {
        val uri = _uiState.value.editor.pipVideoUri ?: return
        viewModelScope.launch {
            runCatching {
                val (file, dims) = ImageCropper.captureVideoFrame(getApplication(), uri, positionMs)
                val aspect = dims.first.toFloat() / dims.second.coerceAtLeast(1)
                val w = 480f
                val h = (w / aspect).coerceIn(180f, 400f)
                val item = ImageItem(
                    layerId = _uiState.value.editor.activeLayerId,
                    filePath = file.absolutePath,
                    x = 160f,
                    y = 220f,
                    width = w,
                    height = h
                )
                mutateCurrentPage(recordUndo = true) { p -> p.copy(images = p.images + item) }
                showStatus("Captured lecture video frame to page!")
            }.onFailure { err ->
                showStatus("Frame capture failed: ${err.localizedMessage}")
            }
        }
    }

    // Layers
    fun addLayer() {
        val page = _uiState.value.activePage ?: return
        val newLayer = Layer(name = "Layer ${page.layers.size + 1}")
        mutateCurrentPage(recordUndo = true) { p -> p.copy(layers = p.layers + newLayer) }
        _uiState.update { it.copy(editor = it.editor.copy(activeLayerId = newLayer.id)) }
    }

    fun selectLayer(layerId: String) {
        _uiState.update { it.copy(editor = it.editor.copy(activeLayerId = layerId)) }
    }

    fun toggleLayerVisibility(layerId: String) {
        mutateCurrentPage(recordUndo = true) { p ->
            p.copy(layers = p.layers.map { if (it.id == layerId) it.copy(isVisible = !it.isVisible) else it })
        }
    }

    fun toggleLayerLock(layerId: String) {
        mutateCurrentPage(recordUndo = true) { p ->
            p.copy(layers = p.layers.map { if (it.id == layerId) it.copy(isLocked = !it.isLocked) else it })
        }
    }

    fun deleteLayer(layerId: String) {
        val page = _uiState.value.activePage ?: return
        if (page.layers.size <= 1) return
        mutateCurrentPage(recordUndo = true) { p ->
            p.copy(
                layers = p.layers.filterNot { it.id == layerId },
                strokes = p.strokes.filterNot { it.layerId == layerId },
                tapes = p.tapes.filterNot { it.layerId == layerId },
                textBoxes = p.textBoxes.filterNot { it.layerId == layerId },
                images = p.images.filterNot { it.layerId == layerId },
                stickers = p.stickers.filterNot { it.layerId == layerId },
                hyperlinks = p.hyperlinks.filterNot { it.layerId == layerId }
            )
        }
        val fallback = _uiState.value.activePage?.layers?.firstOrNull()?.id ?: "layer_default"
        _uiState.update { it.copy(editor = it.editor.copy(activeLayerId = fallback)) }
    }

    // Undo / Redo
    fun canUndo(): Boolean = !undoStacks[_uiState.value.activePage?.id].isNullOrEmpty()
    fun canRedo(): Boolean = !redoStacks[_uiState.value.activePage?.id].isNullOrEmpty()

    fun undo() {
        val page = _uiState.value.activePage ?: return
        val stack = undoStacks[page.id] ?: return
        if (stack.isEmpty()) return
        val previous = stack.removeLast()
        redoStacks.getOrPut(page.id) { ArrayDeque() }.addLast(page)
        replaceCurrentPage(previous)
    }

    fun redo() {
        val page = _uiState.value.activePage ?: return
        val stack = redoStacks[page.id] ?: return
        if (stack.isEmpty()) return
        val next = stack.removeLast()
        undoStacks.getOrPut(page.id) { ArrayDeque() }.addLast(page)
        replaceCurrentPage(next)
    }

    // endregion

    // region Audio, Pomodoro & Exports

    fun toggleAudioRecording() {
        val nb = _uiState.value.activeNotebook ?: return
        if (audioManager.isRecording) {
            val rec = audioManager.stopRecording("Lecture ${nb.recordings.size + 1}")
            if (rec != null) {
                updateNotebookById(nb.id) { it.copy(recordings = it.recordings + rec) }
                showStatus("Saved voice note")
            }
            _uiState.update { it.copy(editor = it.editor.copy(isRecordingAudio = false)) }
        } else {
            val started = audioManager.startRecording() != null
            _uiState.update { it.copy(editor = it.editor.copy(isRecordingAudio = started)) }
            if (!started) showStatus("Microphone permission required")
        }
    }

    fun togglePlayRecording(recordingId: String, filePath: String) {
        if (_uiState.value.editor.playingRecordingId == recordingId) {
            audioManager.stopPlayback()
            _uiState.update { it.copy(editor = it.editor.copy(playingRecordingId = null)) }
        } else {
            val speed = _uiState.value.editor.audioPlaybackSpeed
            audioManager.play(filePath, speed) {
                _uiState.update { it.copy(editor = it.editor.copy(playingRecordingId = null)) }
            }
            _uiState.update { it.copy(editor = it.editor.copy(playingRecordingId = recordingId)) }
        }
    }

    fun cyclePlaybackSpeed() {
        val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        val curr = _uiState.value.editor.audioPlaybackSpeed
        val next = speeds[(speeds.indexOfFirst { it >= curr } + 1) % speeds.size]
        audioManager.setPlaybackSpeed(next)
        _uiState.update { it.copy(editor = it.editor.copy(audioPlaybackSpeed = next)) }
    }

    fun togglePomodoroWidget() {
        _uiState.update { it.copy(editor = it.editor.copy(showPomodoroWidget = !it.editor.showPomodoroWidget)) }
    }

    fun togglePomodoroRunning() {
        val running = !_uiState.value.editor.pomodoroRunning
        _uiState.update { it.copy(editor = it.editor.copy(pomodoroRunning = running)) }
        pomodoroJob?.cancel()
        if (running) {
            pomodoroJob = viewModelScope.launch {
                while (_uiState.value.editor.pomodoroRunning && _uiState.value.editor.pomodoroSecondsLeft > 0) {
                    delay(1000L)
                    _uiState.update { state ->
                        val next = (state.editor.pomodoroSecondsLeft - 1).coerceAtLeast(0)
                        state.copy(editor = state.editor.copy(pomodoroSecondsLeft = next, pomodoroRunning = next > 0))
                    }
                }
            }
        }
    }

    fun resetPomodoro(minutes: Int = 25) {
        pomodoroJob?.cancel()
        _uiState.update {
            it.copy(editor = it.editor.copy(pomodoroRunning = false, pomodoroSecondsLeft = minutes * 60))
        }
    }

    fun exportActiveNotebookToPdf(onExported: (File, String) -> Unit) {
        val nb = _uiState.value.activeNotebook ?: return
        viewModelScope.launch {
            runCatching {
                val file = PdfEngine.exportNotebookToPdf(getApplication(), nb)
                showStatus("Exported PDF: ${file.name}")
                onExported(file, "application/pdf")
            }.onFailure { showStatus("PDF export failed: ${it.localizedMessage}") }
        }
    }

    fun exportCurrentPageToPng(onExported: (File, String) -> Unit) {
        val nb = _uiState.value.activeNotebook ?: return
        val page = _uiState.value.activePage ?: return
        viewModelScope.launch {
            runCatching {
                val file = PdfEngine.exportPageToPng(getApplication(), nb.title, page)
                showStatus("Exported PNG: ${file.name}")
                onExported(file, "image/png")
            }.onFailure { showStatus("PNG export failed: ${it.localizedMessage}") }
        }
    }

    fun exportActiveNotebookBackup(onExported: (File, String) -> Unit) {
        val nb = _uiState.value.activeNotebook ?: return
        viewModelScope.launch {
            runCatching {
                val file = PdfEngine.exportNotebookBackup(getApplication(), nb)
                showStatus("Backup saved: ${file.name}")
                onExported(file, "application/json")
            }.onFailure { showStatus("Backup export failed: ${it.localizedMessage}") }
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(editor = it.editor.copy(statusMessage = null)) }
    }

    // endregion

    // region Internal Helpers

    private fun showStatus(msg: String) {
        _uiState.update { it.copy(editor = it.editor.copy(statusMessage = msg)) }
    }

    private fun mutateCurrentPage(recordUndo: Boolean, transform: (NotePage) -> NotePage) {
        val nb = _uiState.value.activeNotebook ?: return
        val idx = _uiState.value.editor.currentPageIndex
        val page = nb.pages.getOrNull(idx) ?: return

        if (recordUndo) {
            val stack = undoStacks.getOrPut(page.id) { ArrayDeque() }
            if (stack.size >= 80) stack.removeFirst()
            stack.addLast(page)
            redoStacks[page.id]?.clear()
        }

        val updatedPage = transform(page)
        val updatedPages = nb.pages.toMutableList().apply { set(idx, updatedPage) }
        updateNotebookById(nb.id) { it.copy(pages = updatedPages) }
    }

    private fun replaceCurrentPage(page: NotePage) {
        val nb = _uiState.value.activeNotebook ?: return
        val idx = _uiState.value.editor.currentPageIndex
        if (idx !in nb.pages.indices) return
        val updatedPages = nb.pages.toMutableList().apply { set(idx, page) }
        updateNotebookById(nb.id) { it.copy(pages = updatedPages) }
    }

    private fun updateNotebookById(id: String, transform: (Notebook) -> Notebook) {
        var target: Notebook? = null
        _uiState.update { state ->
            val updatedList = state.notebooks.map { nb ->
                if (nb.id == id) {
                    transform(nb).copy(updatedAtMs = System.currentTimeMillis()).also { target = it }
                } else nb
            }
            state.copy(notebooks = updatedList)
        }
        val toSave = target ?: return
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(250L)
            repo.saveNotebook(toSave)
        }
    }

    private fun persistWorkspace() {
        val state = _uiState.value
        viewModelScope.launch {
            repo.saveWorkspace(
                WorkspaceIndex(
                    folders = state.folders,
                    favoriteTools = state.favoriteTools,
                    customColors = state.customColors
                )
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioManager.release()
    }

    // endregion
}
