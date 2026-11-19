package com.johnlrt.freenotes.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Flare
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ShapeLine
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.johnlrt.freenotes.data.EraserMode
import com.johnlrt.freenotes.data.HighlighterStyle
import com.johnlrt.freenotes.data.NotePage
import com.johnlrt.freenotes.data.Notebook
import com.johnlrt.freenotes.data.PaperColor
import com.johnlrt.freenotes.data.PaperTemplate
import com.johnlrt.freenotes.data.ScrollDirection
import com.johnlrt.freenotes.data.ShapeKind
import com.johnlrt.freenotes.data.ToolType
import com.johnlrt.freenotes.ui.AppUiState
import com.johnlrt.freenotes.ui.MainViewModel
import com.johnlrt.freenotes.ui.ToolConfig
import java.io.File
import kotlin.math.roundToInt

private val PaletteColors = listOf(
    0xFF1A1C1EL, 0xFF475569L, 0xFF2563EBL, 0xFF0EA5E9L, 0xFF0D9488L,
    0xFF16A34AL, 0xFFFACC15L, 0xFFF59E0BL, 0xFFEA580CL, 0xFFDC2626L,
    0xFFDB2777L, 0xFF7C3AEDL, 0xFFFFFFFFL
)

@Composable
fun EditorScreen(
    state: AppUiState,
    viewModel: MainViewModel,
    onRequestMicPermission: () -> Unit,
    onRequestPickImage: () -> Unit,
    onRequestPickVideo: () -> Unit,
    onExportedFile: (File, String) -> Unit
) {
    val notebook = state.activeNotebook ?: return
    val page = state.activePage ?: return
    val editor = state.editor
    val cfg = editor.toolConfig

    var showToolSettings by remember { mutableStateOf(false) }
    var showColorWheel by remember { mutableStateOf(false) }
    var showLayersPanel by remember { mutableStateOf(false) }
    var showOutlinePanel by remember { mutableStateOf(false) }
    var showPageSorter by remember { mutableStateOf(false) }
    var showTemplateDialog by remember { mutableStateOf(false) }
    var showStickersDialog by remember { mutableStateOf(false) }
    var showFlashcardsDialog by remember { mutableStateOf(false) }
    var showAudioBar by remember { mutableStateOf(false) }
    var showImageCropDialog by remember { mutableStateOf(false) }

    var pendingTextEdit by remember { mutableStateOf<TextEditRequest?>(null) }
    var pendingHyperlinkPos by remember { mutableStateOf<Pair<Float, Float>?>(null) }

    var stickyOffsetX by remember { mutableFloatStateOf(16f) }
    var stickyOffsetY by remember { mutableFloatStateOf(130f) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(editor.statusMessage) {
        val msg = editor.statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    val selectedImage = remember(page.images, editor.selectedImageId) {
        page.images.find { it.id == editor.selectedImageId }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Top Notebook Tab & Action Bar
            TopEditorBar(
                state = state,
                notebook = notebook,
                viewModel = viewModel,
                showLayersPanel = showLayersPanel,
                showOutlinePanel = showOutlinePanel,
                onToggleLayers = { showLayersPanel = !showLayersPanel; if (showLayersPanel) showOutlinePanel = false },
                onToggleOutline = { showOutlinePanel = !showOutlinePanel; if (showOutlinePanel) showLayersPanel = false },
                onOpenFlashcards = { showFlashcardsDialog = true },
                onOpenPipVideo = onRequestPickVideo,
                onOpenPageSorter = { showPageSorter = true },
                onToggleAudioBar = { showAudioBar = !showAudioBar },
                onExportPdf = { viewModel.exportActiveNotebookToPdf(onExportedFile) },
                onExportPng = { viewModel.exportCurrentPageToPng(onExportedFile) },
                onExportBackup = { viewModel.exportActiveNotebookBackup(onExportedFile) }
            )

            HorizontalDivider(thickness = 0.75.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))

            // 2. Primary Freenotes Writing Toolbar
            PrimaryToolBar(
                cfg = cfg,
                showRuler = editor.showRuler,
                showZoomWindow = editor.showZoomWindow,
                allTapesRevealed = editor.allTapesRevealed,
                showToolSettings = showToolSettings,
                onSelectTool = { tool ->
                    when (tool) {
                        ToolType.IMAGE -> {
                            viewModel.selectTool(ToolType.IMAGE)
                            onRequestPickImage()
                        }
                        ToolType.STICKER -> showStickersDialog = true
                        else -> {
                            if (cfg.activeTool == tool) showToolSettings = !showToolSettings
                            else viewModel.selectTool(tool)
                        }
                    }
                },
                onToggleRuler = viewModel::toggleRuler,
                onToggleZoomWindow = viewModel::toggleZoomWindow,
                onToggleToolSettings = { showToolSettings = !showToolSettings },
                onOpenColorWheel = { showColorWheel = true },
                onPickColor = { argb ->
                    viewModel.updateToolConfig {
                        when (it.activeTool) {
                            ToolType.HIGHLIGHTER -> it.copy(highlighterColorArgb = argb)
                            ToolType.TAPE -> it.copy(tapeColorArgb = argb)
                            else -> it.copy(penColorArgb = argb)
                        }
                    }
                },
                onPickWidth = { w ->
                    viewModel.updateToolConfig {
                        when (it.activeTool) {
                            ToolType.HIGHLIGHTER -> it.copy(highlighterWidth = w)
                            ToolType.ERASER -> it.copy(eraserRadius = w)
                            ToolType.TAPE -> it.copy(tapeThickness = w)
                            else -> it.copy(penWidth = w)
                        }
                    }
                },
                onToggleAllTapes = viewModel::toggleAllTapesOnPage
            )

            if (showAudioBar) {
                AudioRecorderStrip(
                    notebook = notebook,
                    isRecording = editor.isRecordingAudio,
                    playingId = editor.playingRecordingId,
                    playbackSpeed = editor.audioPlaybackSpeed,
                    onToggleRecord = {
                        onRequestMicPermission()
                        viewModel.toggleAudioRecording()
                    },
                    onTogglePlay = { id, path -> viewModel.togglePlayRecording(id, path) },
                    onSeekBack = { viewModel.audioManager.seekBy(-10_000) },
                    onSeekForward = { viewModel.audioManager.seekBy(10_000) },
                    onCycleSpeed = viewModel::cyclePlaybackSpeed
                )
            }

            // 3. Main Canvas Workspace (+ Split View + Outline/Layers Side Sheets)
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (showOutlinePanel) {
                    OutlineBookmarksPanel(
                        notebook = notebook,
                        currentPageIndex = editor.currentPageIndex,
                        onJumpToPage = viewModel::selectPage,
                        onAddBookmark = viewModel::addBookmark,
                        onDeleteBookmark = viewModel::deleteBookmark,
                        onClose = { showOutlinePanel = false }
                    )
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (editor.splitScreenDualPage && notebook.pages.size > 1) {
                        val rightIdx = (editor.currentPageIndex + 1).coerceAtMost(notebook.pages.lastIndex)
                        Row(modifier = Modifier.fillMaxSize()) {
                            HandwritingCanvas(
                                pages = notebook.pages,
                                currentPageIndex = editor.currentPageIndex,
                                scrollDirection = ScrollDirection.HORIZONTAL_PAGING,
                                toolConfig = cfg,
                                stylusOnlyMode = editor.stylusOnlyMode,
                                zoomLocked = editor.zoomLocked,
                                darkPdfInvert = editor.darkPdfInvert,
                                showRuler = editor.showRuler,
                                showZoomWindow = editor.showZoomWindow,
                                zoomRect = editor.zoomRect,
                                selectedStrokeIds = editor.selectedStrokeIds,
                                selectedImageId = editor.selectedImageId,
                                isInfiniteCanvas = notebook.isInfiniteCanvas,
                                holdToShapeEnabled = editor.holdToShapeEnabled,
                                selectedStickerId = editor.selectedStickerId,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                onCommitStroke = viewModel::commitStrokeOnPage,
                                onEraseAt = viewModel::eraseAtOnPage,
                                onSelectLasso = viewModel::selectLassoPolygon,
                                onDragSelection = viewModel::translateSelectedStrokes,
                                onTapTape = viewModel::tryToggleTapeAt,
                                onTapHyperlink = viewModel::selectPage,
                                onSelectImage = viewModel::selectImage,
                                onSelectSticker = viewModel::selectSticker,
                                onTransformImage = viewModel::transformImage,
                                onTransformSticker = viewModel::transformSticker,
                                onTwoFingerUndo = viewModel::undo,
                                onThreeFingerRedo = viewModel::redo,
                                onRequestTextEdit = { x, y, id, text -> pendingTextEdit = TextEditRequest(x, y, id, text) },
                                onRequestHyperlinkCreate = { x, y -> pendingHyperlinkPos = x to y }
                            )
                            Box(modifier = Modifier.width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outline))
                            HandwritingCanvas(
                                pages = notebook.pages,
                                currentPageIndex = rightIdx,
                                scrollDirection = ScrollDirection.HORIZONTAL_PAGING,
                                toolConfig = cfg,
                                stylusOnlyMode = editor.stylusOnlyMode,
                                zoomLocked = editor.zoomLocked,
                                darkPdfInvert = editor.darkPdfInvert,
                                showRuler = false,
                                showZoomWindow = false,
                                zoomRect = editor.zoomRect,
                                selectedStrokeIds = emptySet(),
                                selectedImageId = null,
                                readOnly = true,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                onCommitStroke = { _, _, _ -> },
                                onEraseAt = { _, _, _, _ -> },
                                onSelectLasso = {},
                                onDragSelection = { _, _, _ -> },
                                onTapTape = { _, _ -> false },
                                onTapHyperlink = viewModel::selectPage,
                                onSelectImage = {},
                                onRequestTextEdit = { _, _, _, _ -> },
                                onRequestHyperlinkCreate = { _, _ -> }
                            )
                        }
                    } else {
                        HandwritingCanvas(
                            pages = notebook.pages,
                            currentPageIndex = editor.currentPageIndex,
                            scrollDirection = editor.scrollDirection,
                            toolConfig = cfg,
                            stylusOnlyMode = editor.stylusOnlyMode,
                            zoomLocked = editor.zoomLocked,
                            darkPdfInvert = editor.darkPdfInvert,
                            showRuler = editor.showRuler,
                            showZoomWindow = editor.showZoomWindow,
                            zoomRect = editor.zoomRect,
                            selectedStrokeIds = editor.selectedStrokeIds,
                            selectedImageId = editor.selectedImageId,
                            isInfiniteCanvas = notebook.isInfiniteCanvas,
                            holdToShapeEnabled = editor.holdToShapeEnabled,
                            selectedStickerId = editor.selectedStickerId,
                            modifier = Modifier.fillMaxSize(),
                            onCommitStroke = viewModel::commitStrokeOnPage,
                            onEraseAt = viewModel::eraseAtOnPage,
                            onSelectLasso = viewModel::selectLassoPolygon,
                            onDragSelection = viewModel::translateSelectedStrokes,
                            onTapTape = viewModel::tryToggleTapeAt,
                            onTapHyperlink = viewModel::selectPage,
                            onSelectImage = viewModel::selectImage,
                            onSelectSticker = viewModel::selectSticker,
                            onTransformImage = viewModel::transformImage,
                            onTransformSticker = viewModel::transformSticker,
                            onTwoFingerUndo = viewModel::undo,
                            onThreeFingerRedo = viewModel::redo,
                            onRequestTextEdit = { x, y, id, text -> pendingTextEdit = TextEditRequest(x, y, id, text) },
                            onRequestHyperlinkCreate = { x, y -> pendingHyperlinkPos = x to y }
                        )
                    }

                    if (showToolSettings) {
                        ToolSettingsCard(
                            cfg = cfg,
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                            onUpdate = viewModel::updateToolConfig,
                            onOpenColorWheel = { showToolSettings = false; showColorWheel = true },
                            onAddToFavorites = { viewModel.addCurrentToolToFavorites(); showToolSettings = false },
                            onClose = { showToolSettings = false }
                        )
                    }

                    // Lasso Action Bar (with ML Kit Handwriting-to-Text!)
                    if (editor.selectedStrokeIds.isNotEmpty()) {
                        LassoActionBar(
                            count = editor.selectedStrokeIds.size,
                            isRecognizing = editor.isRecognizingHandwriting,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
                            onRecolor = viewModel::recolorSelectedStrokes,
                            onConvertToText = viewModel::convertSelectedStrokesToText,
                            onDuplicate = viewModel::duplicateSelectedStrokes,
                            onDelete = viewModel::deleteSelectedStrokes,
                            onClear = viewModel::clearSelection
                        )
                    }

                    // Selected Image Action Pill (Freehand/Rect Crop, Duplicate & Delete)
                    if (selectedImage != null) {
                        Surface(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
                            shape = RoundedCornerShape(22.dp),
                            shadowElevation = 6.dp,
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TextButton(onClick = { showImageCropDialog = true }) {
                                    Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Crop")
                                }
                                IconButton(onClick = viewModel::duplicateSelectedImage, modifier = Modifier.size(30.dp)) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate Image", modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = viewModel::deleteSelectedImage, modifier = Modifier.size(30.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Image", tint = MaterialTheme.colorScheme.error)
                                }
                                IconButton(onClick = { viewModel.selectImage(null) }, modifier = Modifier.size(30.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Done")
                                }
                            }
                        }
                    }

                    // Selected Sticker Action Pill (Duplicate & Delete)
                    if (editor.selectedStickerId != null) {
                        Surface(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
                            shape = RoundedCornerShape(22.dp),
                            shadowElevation = 6.dp,
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TextButton(onClick = viewModel::duplicateSelectedSticker) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Duplicate")
                                }
                                IconButton(onClick = viewModel::deleteSelectedSticker, modifier = Modifier.size(30.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Sticker", tint = MaterialTheme.colorScheme.error)
                                }
                                IconButton(onClick = { viewModel.selectSticker(null) }, modifier = Modifier.size(30.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Done")
                                }
                            }
                        }
                    }

                    // Movable Floating Favorites Toolbar (`fragment_toolbar_sticky`)
                    if (editor.showStickyFavoritesBar && state.favoriteTools.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            shadowElevation = 6.dp,
                            tonalElevation = 4.dp,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                            modifier = Modifier
                                .offset { IntOffset(stickyOffsetX.roundToInt(), stickyOffsetY.roundToInt()) }
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        stickyOffsetX += dragAmount.x
                                        stickyOffsetY += dragAmount.y
                                    }
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Star, contentDescription = "Favorites", tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                                state.favoriteTools.forEach { preset ->
                                    val isCurrent = cfg.activeTool == preset.toolType && cfg.activeColorArgb == preset.colorArgb
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(Color(preset.colorArgb).copy(alpha = 0.14f))
                                            .border(
                                                width = if (isCurrent) 2.5.dp else 1.dp,
                                                color = if (isCurrent) MaterialTheme.colorScheme.primary else Color(preset.colorArgb),
                                                shape = CircleShape
                                            )
                                            .clickable { viewModel.applyFavoritePreset(preset) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size((preset.width.coerceIn(6f, 18f)).dp)
                                                .clip(CircleShape)
                                                .background(Color(preset.colorArgb))
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Floating PiP Lecture Video Study Window
                    editor.pipVideoUri?.let { uri ->
                        FloatingVideoOverlay(
                            videoUri = uri,
                            onCaptureFrame = viewModel::capturePipVideoFrame,
                            onClose = viewModel::closePipVideo
                        )
                    }

                    // Pomodoro Focus Timer Overlay
                    if (editor.showPomodoroWidget) {
                        PomodoroOverlay(
                            secondsLeft = editor.pomodoroSecondsLeft,
                            running = editor.pomodoroRunning,
                            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                            onToggleRun = viewModel::togglePomodoroRunning,
                            onReset = { viewModel.resetPomodoro(25) },
                            onClose = viewModel::togglePomodoroWidget
                        )
                    }
                }

                if (showLayersPanel) {
                    LayersSidePanel(
                        page = page,
                        activeLayerId = editor.activeLayerId,
                        onSelectLayer = viewModel::selectLayer,
                        onAddLayer = viewModel::addLayer,
                        onToggleVisibility = viewModel::toggleLayerVisibility,
                        onToggleLock = viewModel::toggleLayerLock,
                        onDeleteLayer = viewModel::deleteLayer,
                        onClose = { showLayersPanel = false }
                    )
                }
            }

            // Optional Docked Zoom Window (Magnifier Writing Box)
            if (editor.showZoomWindow) {
                ZoomWindowPanel(
                    page = page,
                    zoomRect = editor.zoomRect,
                    toolConfig = cfg,
                    onAdvanceRight = viewModel::advanceZoomBoxRight,
                    onStepLeft = viewModel::stepZoomBoxLeft,
                    onNewLine = viewModel::zoomBoxNewLine,
                    onCommitStroke = viewModel::commitStroke,
                    onClose = viewModel::toggleZoomWindow
                )
            }

            // 4. Bottom Page Navigation Strip
            BottomPageStrip(
                currentIndex = editor.currentPageIndex,
                totalPages = notebook.pages.size,
                templateLabel = "${page.template.label} • ${page.paperColor.label}",
                scrollModeLabel = editor.scrollDirection.label,
                onPrev = { viewModel.selectPage(editor.currentPageIndex - 1) },
                onNext = { viewModel.selectPage(editor.currentPageIndex + 1) },
                onAddPage = { viewModel.addPage() },
                onChangeTemplate = { showTemplateDialog = true },
                onToggleScrollMode = viewModel::toggleScrollDirection,
                onOpenPageSorter = { showPageSorter = true }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 56.dp)
        )
    }

    // Dialogs
    if (showColorWheel) {
        ColorPickerDialog(
            initialColorArgb = cfg.activeColorArgb,
            savedColors = state.customColors,
            onPickColor = { argb ->
                viewModel.updateToolConfig {
                    when (it.activeTool) {
                        ToolType.HIGHLIGHTER -> it.copy(highlighterColorArgb = argb)
                        ToolType.TAPE -> it.copy(tapeColorArgb = argb)
                        else -> it.copy(penColorArgb = argb)
                    }
                }
                showColorWheel = false
            },
            onSaveCustomColor = viewModel::saveCustomColor,
            onDismiss = { showColorWheel = false }
        )
    }

    if (showStickersDialog) {
        StickersDialog(
            onPickSticker = { tpl -> viewModel.addSticker(tpl); showStickersDialog = false },
            onDismiss = { showStickersDialog = false }
        )
    }

    if (showFlashcardsDialog) {
        FlashcardsDialog(
            cards = notebook.flashcards,
            onAddCard = viewModel::addFlashcard,
            onRateCard = viewModel::rateFlashcard,
            onDeleteCard = viewModel::deleteFlashcard,
            onDismiss = { showFlashcardsDialog = false }
        )
    }

    if (showImageCropDialog && selectedImage != null) {
        ImageCropDialog(
            imagePath = selectedImage.filePath,
            onCropRect = { l, t, r, b -> viewModel.cropSelectedImageRect(l, t, r, b); showImageCropDialog = false },
            onCropFreehand = { poly -> viewModel.cropSelectedImageFreehand(poly); showImageCropDialog = false },
            onDismiss = { showImageCropDialog = false }
        )
    }

    pendingTextEdit?.let { req ->
        var text by remember(req) { mutableStateOf(req.initialText) }
        AlertDialog(
            onDismissRequest = { pendingTextEdit = null },
            title = { Text(if (req.existingId == null) "Add Text Box" else "Edit Text Box") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Type your note...") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.addOrUpdateTextBox(req.x, req.y, text, req.existingId); pendingTextEdit = null }) {
                    Text("Save")
                }
            },
            dismissButton = { TextButton(onClick = { pendingTextEdit = null }) { Text("Cancel") } }
        )
    }

    pendingHyperlinkPos?.let { (hx, hy) ->
        var label by remember { mutableStateOf("") }
        var targetPage by remember { mutableIntStateOf((editor.currentPageIndex + 1).coerceAtMost(notebook.pages.lastIndex)) }
        AlertDialog(
            onDismissRequest = { pendingHyperlinkPos = null },
            title = { Text("Insert Page Hyperlink") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Link Label") },
                        placeholder = { Text("e.g., → See Formula Sheet") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Target Page: ${targetPage + 1} / ${notebook.pages.size}", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(notebook.pages.indices.toList()) { idx ->
                            FilterChip(
                                selected = targetPage == idx,
                                onClick = { targetPage = idx },
                                label = { Text("P.${idx + 1}") }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.addHyperlink(label, targetPage, hx, hy); pendingHyperlinkPos = null }) {
                    Text("Insert Link")
                }
            },
            dismissButton = { TextButton(onClick = { pendingHyperlinkPos = null }) { Text("Cancel") } }
        )
    }

    if (showPageSorter) {
        PageSorterDialog(
            pages = notebook.pages,
            currentIndex = editor.currentPageIndex,
            onSelect = { idx -> viewModel.selectPage(idx); showPageSorter = false },
            onDuplicate = viewModel::duplicatePage,
            onDelete = viewModel::deletePage,
            onMoveLeft = { idx -> viewModel.movePage(idx, idx - 1) },
            onMoveRight = { idx -> viewModel.movePage(idx, idx + 1) },
            onAddPage = { viewModel.addPage() },
            onDismiss = { showPageSorter = false }
        )
    }

    if (showTemplateDialog) {
        ChangeTemplateDialog(
            currentTemplate = page.template,
            currentColor = page.paperColor,
            onApply = { tpl, color -> viewModel.changeCurrentPageTemplate(tpl, color); showTemplateDialog = false },
            onDismiss = { showTemplateDialog = false }
        )
    }
}

private data class TextEditRequest(val x: Float, val y: Float, val existingId: String?, val initialText: String)

@Composable
private fun TopEditorBar(
    state: AppUiState,
    notebook: Notebook,
    viewModel: MainViewModel,
    showLayersPanel: Boolean,
    showOutlinePanel: Boolean,
    onToggleLayers: () -> Unit,
    onToggleOutline: () -> Unit,
    onOpenFlashcards: () -> Unit,
    onOpenPipVideo: () -> Unit,
    onOpenPageSorter: () -> Unit,
    onToggleAudioBar: () -> Unit,
    onExportPdf: () -> Unit,
    onExportPng: () -> Unit,
    onExportBackup: () -> Unit
) {
    val editor = state.editor
    var showMoreMenu by remember { mutableStateOf(false) }

    Surface(tonalElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = viewModel::closeEditorToLibrary) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Library")
            }

            LazyRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(editor.openNotebookIds) { id ->
                    val nb = state.notebooks.find { it.id == id } ?: return@items
                    val active = id == notebook.id
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.clickable { viewModel.openNotebook(id) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(nb.coverColorArgb)))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                nb.title,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (editor.openNotebookIds.size > 1) {
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close Tab",
                                    modifier = Modifier.size(14.dp).clickable { viewModel.closeNotebookTab(id) }
                                )
                            }
                        }
                    }
                }
            }

            IconButton(onClick = viewModel::undo, enabled = viewModel.canUndo()) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
            }
            IconButton(onClick = viewModel::redo, enabled = viewModel.canRedo()) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
            }
            IconButton(onClick = onToggleOutline) {
                Icon(
                    Icons.Default.Bookmarks,
                    contentDescription = "Outline & Search",
                    tint = if (showOutlinePanel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onOpenFlashcards) {
                Icon(Icons.Default.Style, contentDescription = "Flashcards", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onOpenPipVideo) {
                Icon(
                    Icons.Default.OndemandVideo,
                    contentDescription = "PiP Lecture Video",
                    tint = if (editor.pipVideoUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = viewModel::toggleSplitScreenDualPage) {
                Icon(
                    Icons.Default.VerticalSplit,
                    contentDescription = "Split Screen",
                    tint = if (editor.splitScreenDualPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onToggleLayers) {
                Icon(
                    Icons.Default.Layers,
                    contentDescription = "Layers",
                    tint = if (showLayersPanel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onToggleAudioBar) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = "Voice Notes",
                    tint = if (editor.isRecordingAudio) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = viewModel::togglePomodoroWidget) {
                Icon(
                    Icons.Default.Timer,
                    contentDescription = "Focus Timer",
                    tint = if (editor.showPomodoroWidget) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onOpenPageSorter) {
                Icon(Icons.Default.GridView, contentDescription = "Pages")
            }

            Box {
                IconButton(onClick = { showMoreMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                }
                DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (editor.holdToShapeEnabled) "✓ Hold-to-Shape Auto-Snap" else "Hold-to-Shape Auto-Snap") },
                        onClick = { viewModel.toggleHoldToShape(); showMoreMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text(if (editor.scribbleToEraseEnabled) "✓ Scribble-to-Erase Gesture" else "Scribble-to-Erase Gesture") },
                        onClick = { viewModel.toggleScribbleToErase(); showMoreMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text(if (notebook.isInfiniteCanvas) "✓ Infinite Canvas Mode" else "Infinite Canvas Mode") },
                        onClick = { viewModel.toggleInfiniteCanvas(); showMoreMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text(if (editor.stylusOnlyMode) "✓ Stylus-Only Palm Rejection" else "Stylus-Only Palm Rejection") },
                        onClick = { viewModel.toggleStylusOnlyMode(); showMoreMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text(if (editor.zoomLocked) "✓ Zoom Locked" else "Lock Zoom") },
                        onClick = { viewModel.toggleZoomLock(); showMoreMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text(if (editor.darkPdfInvert) "✓ Dark-Mode PDF Invert" else "Dark-Mode PDF Invert") },
                        onClick = { viewModel.toggleDarkPdfInvert(); showMoreMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text(if (editor.showStickyFavoritesBar) "✓ Floating Favorites Bar" else "Show Floating Favorites Bar") },
                        onClick = { viewModel.toggleStickyFavoritesBar(); showMoreMenu = false }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Export Multi-Page PDF") }, onClick = { showMoreMenu = false; onExportPdf() })
                    DropdownMenuItem(text = { Text("Export Current Page PNG") }, onClick = { showMoreMenu = false; onExportPng() })
                    DropdownMenuItem(text = { Text("Export .freenotes.json Backup") }, onClick = { showMoreMenu = false; onExportBackup() })
                }
            }
        }
    }
}

@Composable
private fun PrimaryToolBar(
    cfg: ToolConfig,
    showRuler: Boolean,
    showZoomWindow: Boolean,
    allTapesRevealed: Boolean,
    showToolSettings: Boolean,
    onSelectTool: (ToolType) -> Unit,
    onToggleRuler: () -> Unit,
    onToggleZoomWindow: () -> Unit,
    onToggleToolSettings: () -> Unit,
    onOpenColorWheel: () -> Unit,
    onPickColor: (Long) -> Unit,
    onPickWidth: (Float) -> Unit,
    onToggleAllTapes: () -> Unit
) {
    Surface(tonalElevation = 1.dp, color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Custom realistic Freenotes nib/brush icons!
            NibToolButton(ToolType.FOUNTAIN_PEN, "Fountain", Color(cfg.penColorArgb), cfg.activeTool == ToolType.FOUNTAIN_PEN) { onSelectTool(ToolType.FOUNTAIN_PEN) }
            NibToolButton(ToolType.BALLPOINT, "Ballpoint", Color(cfg.penColorArgb), cfg.activeTool == ToolType.BALLPOINT) { onSelectTool(ToolType.BALLPOINT) }
            NibToolButton(ToolType.PENCIL, "Pencil", Color(cfg.penColorArgb), cfg.activeTool == ToolType.PENCIL) { onSelectTool(ToolType.PENCIL) }
            NibToolButton(ToolType.ART_PEN, "Art", Color(cfg.penColorArgb), cfg.activeTool == ToolType.ART_PEN) { onSelectTool(ToolType.ART_PEN) }
            NibToolButton(ToolType.HIGHLIGHTER, "Highlight", Color(cfg.highlighterColorArgb), cfg.activeTool == ToolType.HIGHLIGHTER) { onSelectTool(ToolType.HIGHLIGHTER) }
            NibToolButton(ToolType.ERASER, "Eraser", Color(0xFFF472B6), cfg.activeTool == ToolType.ERASER) { onSelectTool(ToolType.ERASER) }
            NibToolButton(ToolType.LASSO, "Lasso", Color(0xFF2563EB), cfg.activeTool == ToolType.LASSO) { onSelectTool(ToolType.LASSO) }
            NibToolButton(ToolType.TAPE, "Tape", Color(cfg.tapeColorArgb), cfg.activeTool == ToolType.TAPE) { onSelectTool(ToolType.TAPE) }

            ToolButton(Icons.Default.ShapeLine, "Shape", cfg.activeTool == ToolType.SHAPE) { onSelectTool(ToolType.SHAPE) }
            ToolButton(Icons.Default.TextFields, "Text", cfg.activeTool == ToolType.TEXT) { onSelectTool(ToolType.TEXT) }
            ToolButton(Icons.Default.Image, "Photo", cfg.activeTool == ToolType.IMAGE) { onSelectTool(ToolType.IMAGE) }
            ToolButton(Icons.Default.EmojiEmotions, "Sticker", false) { onSelectTool(ToolType.STICKER) }
            ToolButton(Icons.Default.Link, "Link", cfg.activeTool == ToolType.HYPERLINK) { onSelectTool(ToolType.HYPERLINK) }
            ToolButton(Icons.Default.Straighten, "Ruler", showRuler, onToggleRuler)
            ToolButton(Icons.Default.ZoomIn, "ZoomBox", showZoomWindow, onToggleZoomWindow)
            ToolButton(Icons.Default.Flare, "Laser", cfg.activeTool == ToolType.LASER) { onSelectTool(ToolType.LASER) }

            Spacer(Modifier.width(6.dp))

            // 3 Quick Colors + Color Wheel
            val quickColors = if (cfg.activeTool == ToolType.HIGHLIGHTER) cfg.highlighterQuickColors else cfg.penQuickColors
            quickColors.forEach { c ->
                val isSelected = cfg.activeColorArgb == c
                Box(
                    modifier = Modifier
                        .size(25.dp)
                        .clip(CircleShape)
                        .background(Color(c))
                        .border(if (isSelected) 2.5.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f), CircleShape)
                        .clickable { onPickColor(c) }
                )
            }
            IconButton(onClick = onOpenColorWheel, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.ColorLens, contentDescription = "Color Wheel", tint = Color(cfg.activeColorArgb))
            }

            // 3 Quick Width Dots
            val widths = if (cfg.activeTool == ToolType.HIGHLIGHTER) listOf(18f, 26f, 38f) else listOf(2.5f, 4.5f, 8.5f)
            widths.forEach { w ->
                val isSel = kotlin.math.abs(cfg.activeWidth - w) < 0.8f
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(if (isSel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .clickable { onPickWidth(w) },
                    contentAlignment = Alignment.Center
                ) {
                    val dot = ((w / widths.last()) * 14f).coerceIn(5f, 15f)
                    Box(modifier = Modifier.size(dot.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface))
                }
            }

            IconButton(onClick = onToggleToolSettings, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Tune,
                    contentDescription = "Tool Settings",
                    tint = if (showToolSettings) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FilterChip(
                selected = allTapesRevealed,
                onClick = onToggleAllTapes,
                label = { Text(if (allTapesRevealed) "Cover Tape" else "Reveal Tape") },
                leadingIcon = {
                    Icon(if (allTapesRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            )
        }
    }
}

@Composable
private fun NibToolButton(
    tool: ToolType,
    label: String,
    activeColor: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FreenotesToolIcon(tool = tool, activeColor = activeColor, isSelected = selected, size = 22.dp)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = label, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ToolSettingsCard(
    cfg: ToolConfig,
    modifier: Modifier = Modifier,
    onUpdate: ((ToolConfig) -> ToolConfig) -> Unit,
    onOpenColorWheel: () -> Unit,
    onAddToFavorites: () -> Unit,
    onClose: () -> Unit
) {
    Card(
        modifier = modifier.width(430.dp),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${cfg.activeTool.name.replace('_', ' ')} Settings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row {
                    TextButton(onClick = onAddToFavorites) {
                        Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Pin Favorite")
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.Close, contentDescription = "Close") }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PaletteColors) { argb ->
                        val sel = cfg.activeColorArgb == argb
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .border(if (sel) 2.5.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.35f), CircleShape)
                                .clickable {
                                    onUpdate {
                                        when (it.activeTool) {
                                            ToolType.HIGHLIGHTER -> it.copy(highlighterColorArgb = argb)
                                            ToolType.TAPE -> it.copy(tapeColorArgb = argb)
                                            else -> it.copy(penColorArgb = argb)
                                        }
                                    }
                                }
                        )
                    }
                }
                TextButton(onClick = onOpenColorWheel) { Text("Wheel") }
            }

            val sizeRange = if (cfg.activeTool == ToolType.HIGHLIGHTER || cfg.activeTool == ToolType.TAPE) 10f..64f else 1f..22f
            Text("Stroke Width: ${cfg.activeWidth.roundToInt()} px", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = cfg.activeWidth.coerceIn(sizeRange.start, sizeRange.endInclusive),
                onValueChange = { v ->
                    onUpdate {
                        when (it.activeTool) {
                            ToolType.HIGHLIGHTER -> it.copy(highlighterWidth = v)
                            ToolType.ERASER -> it.copy(eraserRadius = v)
                            ToolType.TAPE -> it.copy(tapeThickness = v)
                            else -> it.copy(penWidth = v)
                        }
                    }
                },
                valueRange = sizeRange
            )

            if (cfg.activeTool in listOf(ToolType.FOUNTAIN_PEN, ToolType.BALLPOINT, ToolType.PENCIL, ToolType.ART_PEN)) {
                Text("1€ Pen Stability: ${(cfg.stability * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
                Slider(value = cfg.stability, onValueChange = { v -> onUpdate { it.copy(stability = v) } })
                Text("Pressure Sensitivity: ${(cfg.pressureSensitivity * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
                Slider(value = cfg.pressureSensitivity, onValueChange = { v -> onUpdate { it.copy(pressureSensitivity = v) } })
            }

            if (cfg.activeTool == ToolType.HIGHLIGHTER) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HighlighterStyle.entries.forEach { style ->
                        FilterChip(selected = cfg.highlighterStyle == style, onClick = { onUpdate { it.copy(highlighterStyle = style) } }, label = { Text(style.name) })
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Straight-Line Snap", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = cfg.highlighterStraightLine, onCheckedChange = { v -> onUpdate { it.copy(highlighterStraightLine = v) } })
                }
            }

            if (cfg.activeTool == ToolType.ERASER) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EraserMode.entries.forEach { mode ->
                        FilterChip(selected = cfg.eraserMode == mode, onClick = { onUpdate { it.copy(eraserMode = mode) } }, label = { Text(if (mode == EraserMode.WHOLE_STROKE) "Whole Stroke" else "Pixel / Split") })
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Erase Highlighter Only", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = cfg.eraseHighlighterOnly, onCheckedChange = { v -> onUpdate { it.copy(eraseHighlighterOnly = v) } })
                }
            }

            if (cfg.activeTool == ToolType.SHAPE) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(ShapeKind.entries) { kind ->
                        FilterChip(selected = cfg.shapeKind == kind, onClick = { onUpdate { it.copy(shapeKind = kind) } }, label = { Text(kind.name) })
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Fill Shape", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = cfg.shapeFilled, onCheckedChange = { v -> onUpdate { it.copy(shapeFilled = v) } })
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Dashed Border", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = cfg.shapeBorderDashed, onCheckedChange = { v -> onUpdate { it.copy(shapeBorderDashed = v) } })
                }
            }
        }
    }
}

@Composable
private fun LassoActionBar(
    count: Int,
    isRecognizing: Boolean,
    modifier: Modifier = Modifier,
    onRecolor: (Long) -> Unit,
    onConvertToText: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(24.dp), shadowElevation = 6.dp, color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("$count selected", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            listOf(0xFF1A1C1EL, 0xFF2563EBL, 0xFFDC2626L, 0xFF16A34AL).forEach { c ->
                Box(modifier = Modifier.size(22.dp).clip(CircleShape).background(Color(c)).clickable { onRecolor(c) })
            }
            TextButton(onClick = onConvertToText, enabled = !isRecognizing) {
                Text(if (isRecognizing) "OCR..." else "To Text")
            }
            IconButton(onClick = onDuplicate, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onClear, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun LayersSidePanel(
    page: NotePage,
    activeLayerId: String,
    onSelectLayer: (String) -> Unit,
    onAddLayer: () -> Unit,
    onToggleVisibility: (String) -> Unit,
    onToggleLock: (String) -> Unit,
    onDeleteLayer: (String) -> Unit,
    onClose: () -> Unit
) {
    Surface(modifier = Modifier.width(260.dp).fillMaxHeight(), tonalElevation = 4.dp, color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Layers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row {
                    IconButton(onClick = onAddLayer, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Add, contentDescription = "Add Layer") }
                    IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Close, contentDescription = "Close") }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(page.layers.reversed(), key = { it.id }) { layer ->
                    val isSelected = layer.id == activeLayerId
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth().clickable { onSelectLayer(layer.id) }
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(layer.name, style = MaterialTheme.typography.bodyMedium, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
                            IconButton(onClick = { onToggleVisibility(layer.id) }, modifier = Modifier.size(28.dp)) {
                                Icon(if (layer.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = "Visibility", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { onToggleLock(layer.id) }, modifier = Modifier.size(28.dp)) {
                                Icon(if (layer.isLocked) Icons.Default.Lock else Icons.Default.LockOpen, contentDescription = "Lock", modifier = Modifier.size(18.dp))
                            }
                            if (page.layers.size > 1) {
                                IconButton(onClick = { onDeleteLayer(layer.id) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(17.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioRecorderStrip(
    notebook: Notebook,
    isRecording: Boolean,
    playingId: String?,
    playbackSpeed: Float,
    onToggleRecord: () -> Unit,
    onTogglePlay: (String, String) -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onCycleSpeed: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(onClick = onToggleRecord, shape = RoundedCornerShape(10.dp)) {
                Icon(if (isRecording) Icons.Default.Stop else Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (isRecording) "Stop Recording" else "Record Lecture")
            }
            LazyRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(notebook.recordings, key = { it.id }) { rec ->
                    val playing = playingId == rec.id
                    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surface) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onTogglePlay(rec.id, rec.filePath) }, modifier = Modifier.size(28.dp)) {
                                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play")
                            }
                            Text(rec.title, style = MaterialTheme.typography.labelMedium)
                            if (playing) {
                                IconButton(onClick = onSeekBack, modifier = Modifier.size(26.dp)) { Icon(Icons.Default.Replay10, contentDescription = "-10s", modifier = Modifier.size(18.dp)) }
                                IconButton(onClick = onSeekForward, modifier = Modifier.size(26.dp)) { Icon(Icons.Default.Forward10, contentDescription = "+10s", modifier = Modifier.size(18.dp)) }
                                TextButton(onClick = onCycleSpeed) { Text("${playbackSpeed}x", style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PomodoroOverlay(
    secondsLeft: Int,
    running: Boolean,
    modifier: Modifier = Modifier,
    onToggleRun: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit
) {
    val mins = secondsLeft / 60
    val secs = secondsLeft % 60
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), shadowElevation = 6.dp, color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("%02d:%02d".format(mins, secs), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = onToggleRun, modifier = Modifier.size(30.dp)) {
                Icon(if (running) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Start/Pause")
            }
            TextButton(onClick = onReset) { Text("Reset") }
            IconButton(onClick = onClose, modifier = Modifier.size(26.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun BottomPageStrip(
    currentIndex: Int,
    totalPages: Int,
    templateLabel: String,
    scrollModeLabel: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onAddPage: () -> Unit,
    onChangeTemplate: () -> Unit,
    onToggleScrollMode: () -> Unit,
    onOpenPageSorter: () -> Unit
) {
    Surface(tonalElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = onChangeTemplate) { Text(templateLabel, style = MaterialTheme.typography.labelMedium) }
                TextButton(onClick = onToggleScrollMode) { Text(scrollModeLabel, style = MaterialTheme.typography.labelMedium) }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev, enabled = currentIndex > 0, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Page")
                }
                TextButton(onClick = onOpenPageSorter) {
                    Text("Page ${currentIndex + 1} / $totalPages", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
                IconButton(onClick = onNext, enabled = currentIndex < totalPages - 1, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next Page")
                }
            }

            TextButton(onClick = onAddPage) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add Page")
            }
        }
    }
}

@Composable
private fun PageSorterDialog(
    pages: List<NotePage>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    onDuplicate: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onMoveLeft: (Int) -> Unit,
    onMoveRight: (Int) -> Unit,
    onAddPage: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Pages (${pages.size})", fontWeight = FontWeight.Bold)
                TextButton(onClick = onAddPage) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("New Page")
                }
            }
        },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 130.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(4.dp),
                modifier = Modifier.height(340.dp)
            ) {
                itemsIndexed(pages, key = { _, p -> p.id }) { idx, p ->
                    val isCur = idx == currentIndex
                    Card(
                        modifier = Modifier
                            .clickable { onSelect(idx) }
                            .border(if (isCur) 2.5.dp else 1.dp, if (isCur) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(p.paperColor.argb))
                    ) {
                        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Page ${idx + 1}", fontWeight = FontWeight.Bold, color = if (p.paperColor == PaperColor.DARK) Color.White else Color.Black)
                            Text("${p.template.label} • ${p.strokes.size} strokes", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Spacer(Modifier.height(6.dp))
                            Row {
                                IconButton(onClick = { onMoveLeft(idx) }, enabled = idx > 0, modifier = Modifier.size(26.dp)) { Icon(Icons.Default.ChevronLeft, contentDescription = "Move Left", modifier = Modifier.size(16.dp)) }
                                IconButton(onClick = { onDuplicate(idx) }, modifier = Modifier.size(26.dp)) { Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate", modifier = Modifier.size(15.dp)) }
                                IconButton(onClick = { onDelete(idx) }, enabled = pages.size > 1, modifier = Modifier.size(26.dp)) { Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(15.dp)) }
                                IconButton(onClick = { onMoveRight(idx) }, enabled = idx < pages.lastIndex, modifier = Modifier.size(26.dp)) { Icon(Icons.Default.ChevronRight, contentDescription = "Move Right", modifier = Modifier.size(16.dp)) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun ChangeTemplateDialog(
    currentTemplate: PaperTemplate,
    currentColor: PaperColor,
    onApply: (PaperTemplate, PaperColor) -> Unit,
    onDismiss: () -> Unit
) {
    var tpl by remember { mutableStateOf(currentTemplate) }
    var color by remember { mutableStateOf(currentColor) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Page Template & Paper") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Paper Color", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaperColor.entries.forEach { pc ->
                        FilterChip(selected = color == pc, onClick = { color = pc }, label = { Text(pc.label) })
                    }
                }
                Text("Template", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PaperTemplate.entries) { t ->
                        FilterChip(selected = tpl == t, onClick = { tpl = t }, label = { Text(t.label) })
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onApply(tpl, color) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
