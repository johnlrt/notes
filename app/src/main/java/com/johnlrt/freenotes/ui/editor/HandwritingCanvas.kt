package com.johnlrt.freenotes.ui.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as ComposeStroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.input.motionprediction.MotionEventPredictor
import com.johnlrt.freenotes.data.HighlighterStyle
import com.johnlrt.freenotes.data.NotePage
import com.johnlrt.freenotes.data.PaperTemplate
import com.johnlrt.freenotes.data.PdfEngine
import com.johnlrt.freenotes.data.ScrollDirection
import com.johnlrt.freenotes.data.Stroke
import com.johnlrt.freenotes.data.StrokePoint
import com.johnlrt.freenotes.data.TapeItem
import com.johnlrt.freenotes.data.ToolType
import com.johnlrt.freenotes.engine.HitTestEngine
import com.johnlrt.freenotes.engine.OneEuroFilter2D
import com.johnlrt.freenotes.engine.StrokeGeometry
import com.johnlrt.freenotes.engine.StylusPredictor
import com.johnlrt.freenotes.ui.ToolConfig
import kotlin.math.hypot
import kotlin.math.min

private const val PAGE_GAP_PX = 44f

private val InvertColorMatrix = ColorMatrix(
    floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f
    )
)

/**
 * Full-featured Freenotes tablet canvas supporting:
 * - Smooth polygonal variable-width ink ribbons (`StrokeGeometry.buildRibbonOutline`)
 * - Continuous vertical multi-page scrolling OR horizontal single-page mode
 * - 120Hz/240Hz `MotionEvent` historical batching + 1€ One-Euro filter + `StylusPredictor`
 * - Palm rejection (Stylus-only mode lets fingers pan/zoom smoothly) & S Pen side-button eraser
 * - On-canvas transparent magnetic Ruler
 * - Dark-mode PDF color inversion
 * - Zoom Window target box indicator
 * - Images, Stickers, Clickable Hyperlinks, Active Recall Tapes, Lasso, TextBoxes, Laser Pointer
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HandwritingCanvas(
    pages: List<NotePage>,
    currentPageIndex: Int,
    scrollDirection: ScrollDirection,
    toolConfig: ToolConfig,
    stylusOnlyMode: Boolean,
    zoomLocked: Boolean,
    darkPdfInvert: Boolean,
    showRuler: Boolean,
    showZoomWindow: Boolean,
    zoomRect: Rect,
    selectedStrokeIds: Set<String>,
    selectedImageId: String?,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    isInfiniteCanvas: Boolean = false,
    holdToShapeEnabled: Boolean = true,
    selectedStickerId: String? = null,
    onCommitStroke: (Int, List<StrokePoint>, ToolType?) -> Unit,
    onEraseAt: (Int, Float, Float, Boolean) -> Unit,
    onSelectLasso: (List<StrokePoint>) -> Unit,
    onDragSelection: (Float, Float, Boolean) -> Unit,
    onTapTape: (Float, Float) -> Boolean,
    onTapHyperlink: (Int) -> Unit,
    onSelectImage: (String?) -> Unit,
    onSelectSticker: (String?) -> Unit = {},
    onTransformImage: (String, Float, Float, Float?, Float?, Float?, Boolean) -> Unit = { _, _, _, _, _, _, _ -> },
    onTransformSticker: (String, Float, Float, Float?, Float?, Float?, Boolean) -> Unit = { _, _, _, _, _, _, _ -> },
    onTwoFingerUndo: () -> Unit = {},
    onThreeFingerRedo: () -> Unit = {},
    onRequestTextEdit: (Float, Float, String?, String) -> Unit,
    onRequestHyperlinkCreate: (Float, Float) -> Unit
) {
    val activePage = pages.getOrNull(currentPageIndex) ?: return
    val isContinuous = !isInfiniteCanvas && scrollDirection == ScrollDirection.VERTICAL_CONTINUOUS && pages.size > 1 && !readOnly

    val pageW = activePage.orientation.widthPx
    val pageH = activePage.orientation.heightPx

    // Compute vertical Y-offsets for each page in continuous scroll mode
    val pageTops = remember(pages, isContinuous) {
        if (!isContinuous) {
            listOf(0f)
        } else {
            var y = 0f
            pages.map { p ->
                val top = y
                y += p.orientation.heightPx + PAGE_GAP_PX
                top
            }
        }
    }

    var viewportSize by remember { mutableStateOf(IntSize(1, 1)) }
    var scale by remember(activePage.id, isContinuous) { mutableFloatStateOf(1f) }
    var offsetX by remember(activePage.id, isContinuous) { mutableFloatStateOf(0f) }
    var offsetY by remember(activePage.id, isContinuous) { mutableFloatStateOf(0f) }
    var initializedFit by remember(pages.firstOrNull()?.id, isContinuous) { mutableStateOf(false) }

    LaunchedEffect(viewportSize, pages.firstOrNull()?.id, isContinuous) {
        if (!initializedFit && viewportSize.width > 10 && viewportSize.height > 10) {
            val fitScale = min(
                (viewportSize.width * 0.92f) / pageW,
                (viewportSize.height * 0.92f) / pageH
            ).coerceIn(0.35f, 2.5f)
            scale = fitScale
            offsetX = (viewportSize.width - pageW * fitScale) / 2f
            offsetY = if (isContinuous) 24f else (viewportSize.height - pageH * fitScale) / 2f
            initializedFit = true
        }
    }

    // Cache PDF backdrop bitmaps by page ID
    val pdfBitmaps = remember { mutableStateMapOf<String, Bitmap>() }
    LaunchedEffect(pages) {
        pages.forEach { p ->
            if (p.pdfSourcePath != null && p.pdfPageIndex >= 0 && p.id !in pdfBitmaps) {
                PdfEngine.renderPdfPageBitmap(p.pdfSourcePath, p.pdfPageIndex, p.orientation.widthPx.toInt())?.let {
                    pdfBitmaps[p.id] = it
                }
            }
        }
    }

    // Cache inserted ImageItem bitmaps by file path
    val imageBitmaps = remember { mutableStateMapOf<String, Bitmap>() }
    LaunchedEffect(pages) {
        pages.flatMap { it.images }.forEach { img ->
            if (img.filePath !in imageBitmaps) {
                runCatching { BitmapFactory.decodeFile(img.filePath) }.getOrNull()?.let {
                    imageBitmaps[img.filePath] = it
                }
            }
        }
    }

    // Ruler state (in page coordinates)
    var rulerCx by remember { mutableFloatStateOf(pageW * 0.5f) }
    var rulerCy by remember { mutableFloatStateOf(pageH * 0.35f) }
    var rulerAngleDeg by remember { mutableFloatStateOf(0f) }

    // Zero-recomposition live stroke buffers + hardware front-buffer controller
    val hostView = LocalView.current
    val motionPredictor = remember(hostView) { MotionEventPredictor.newInstance(hostView) }
    val lowLatencyController = remember { LowLatencyInkController() }

    val livePoints = remember { ArrayList<StrokePoint>(256) }
    val laserTrail = remember { ArrayList<StrokePoint>(40) }
    var activeDrawingTool by remember { mutableStateOf<ToolType?>(null) }
    var drawingTargetPageIndex = remember { currentPageIndex }
    var strokeStartTimeMs = remember { 0L }
    var eraserHasHitInGesture = remember { false }
    var isDraggingLassoSelection = remember { false }
    var lastDragPageX = remember { 0f }
    var lastDragPageY = remember { 0f }
    var dragUndoRecorded = remember { false }

    var isPanZooming by remember { mutableStateOf(false) }
    var prevPinchDist by remember { mutableFloatStateOf(0f) }
    var prevMidX by remember { mutableFloatStateOf(0f) }
    var prevMidY by remember { mutableFloatStateOf(0f) }

    val oneEuro = remember { OneEuroFilter2D() }
    val predictor = remember { StylusPredictor(maxPredictMs = 14f, damping = 0.72f) }

    LaunchedEffect(toolConfig.stability) {
        oneEuro.setStability(toolConfig.stability)
    }

    fun screenToWorld(sx: Float, sy: Float): Pair<Float, Float> {
        return ((sx - offsetX) / scale) to ((sy - offsetY) / scale)
    }

    fun resolvePageAtWorldY(wy: Float): Pair<Int, Float> {
        if (!isContinuous) return currentPageIndex to wy
        for (i in pages.indices) {
            val top = pageTops[i]
            val bottom = top + pages[i].orientation.heightPx
            if (wy >= top - PAGE_GAP_PX * 0.5f && wy <= bottom + PAGE_GAP_PX * 0.5f) {
                return i to (wy - top)
            }
        }
        val lastIdx = pages.lastIndex
        return lastIdx to (wy - pageTops[lastIdx])
    }

    // Hold-to-Shape + Two/Three-Finger Undo/Redo + Direct Transform state
    var holdLastMoveMs = remember { 0L }
    var holdAnchorX = remember { 0f }
    var holdAnchorY = remember { 0f }
    var holdSnappedToShape = remember { false }

    var multiDownTimeMs = remember { 0L }
    var multiMaxPointers = remember { 0 }
    var multiStartMidX = remember { 0f }
    var multiStartMidY = remember { 0f }

    var directDragMode = remember { 0 } // 0=none, 1=rulerMove, 2=rulerRotate, 3=imgMove, 4=imgResize, 5=imgRotate, 6=stMove, 7=stResize, 8=stRotate
    var directTargetId = remember { "" }
    var directUndoRecorded = remember { false }

    fun pushFrontBufferFrame(tool: ToolType, pageTop: Float, predPts: List<StrokePoint>) {
        val ptsCopy = if (tool == ToolType.LASER) ArrayList(laserTrail) else ArrayList(livePoints)
        lowLatencyController.renderWetStroke(
            WetStrokeSnapshot(
                toolType = tool,
                points = ptsCopy,
                predictedPoints = predPts,
                colorArgb = if (tool == ToolType.HIGHLIGHTER) toolConfig.highlighterColorArgb else toolConfig.penColorArgb,
                baseWidth = if (tool == ToolType.HIGHLIGHTER) toolConfig.highlighterWidth else toolConfig.penWidth,
                opacity = if (tool == ToolType.HIGHLIGHTER) toolConfig.highlighterOpacity else toolConfig.penOpacity,
                pressureSensitivity = toolConfig.pressureSensitivity,
                nibAngleDeg = toolConfig.artNibAngleDeg,
                highlighterStyle = toolConfig.highlighterStyle,
                straightLine = toolConfig.highlighterStraightLine,
                shapeFilled = toolConfig.shapeFilled,
                shapeFillColorArgb = toolConfig.penColorArgb,
                shapeDashed = toolConfig.shapeBorderDashed,
                tapeThickness = toolConfig.tapeThickness,
                tapeColorArgb = toolConfig.tapeColorArgb,
                scale = scale,
                offsetX = offsetX,
                offsetY = offsetY,
                pageTopY = pageTop
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isInfiniteCanvas) Color(activePage.paperColor.argb) else Color(0xFFE5E9F0))
            .onSizeChanged { viewportSize = it }
            .pointerInteropFilter { event ->
                if (readOnly) {
                    handlePanZoomEvent(
                        event, zoomLocked, isPanZooming, { isPanZooming = it },
                        prevPinchDist, { prevPinchDist = it }, prevMidX, prevMidY,
                        { mx, my -> prevMidX = mx; prevMidY = my },
                        scale, offsetX, offsetY, isInfiniteCanvas
                    ) { ns, nx, ny -> scale = ns; offsetX = nx; offsetY = ny }
                    return@pointerInteropFilter true
                }

                val pointerCount = event.pointerCount
                val primaryToolType = event.getToolType(0)
                val isStylus = primaryToolType == MotionEvent.TOOL_TYPE_STYLUS ||
                    primaryToolType == MotionEvent.TOOL_TYPE_ERASER

                // Track 2-finger tap (Undo) & 3-finger tap (Redo)
                if (pointerCount >= 2) {
                    if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                        if (multiMaxPointers < 2) {
                            multiDownTimeMs = event.eventTime
                            multiStartMidX = (event.getX(0) + event.getX(1)) * 0.5f
                            multiStartMidY = (event.getY(0) + event.getY(1)) * 0.5f
                        }
                        if (pointerCount > multiMaxPointers) multiMaxPointers = pointerCount
                    }
                }

                if (pointerCount >= 2 || (stylusOnlyMode && !isStylus)) {
                    if (activeDrawingTool != null) {
                        livePoints.clear()
                        laserTrail.clear()
                        activeDrawingTool = null
                        holdSnappedToShape = false
                        lowLatencyController.clearImmediate()
                    }
                    handlePanZoomEvent(
                        event, zoomLocked, isPanZooming, { isPanZooming = it },
                        prevPinchDist, { prevPinchDist = it }, prevMidX, prevMidY,
                        { mx, my -> prevMidX = mx; prevMidY = my },
                        scale, offsetX, offsetY, isInfiniteCanvas
                    ) { ns, nx, ny -> scale = ns; offsetX = nx; offsetY = ny }
                    return@pointerInteropFilter true
                }

                if ((isPanZooming || multiMaxPointers >= 2) && event.actionMasked == MotionEvent.ACTION_UP) {
                    val dt = event.eventTime - multiDownTimeMs
                    val moved = hypot((event.x - multiStartMidX).toDouble(), (event.y - multiStartMidY).toDouble()).toFloat()
                    if (multiMaxPointers == 2 && dt in 1L..250L && moved < 32f) {
                        onTwoFingerUndo()
                    } else if (multiMaxPointers >= 3 && dt in 1L..290L && moved < 38f) {
                        onThreeFingerRedo()
                    }
                    multiMaxPointers = 0
                    isPanZooming = false
                    return@pointerInteropFilter true
                }

                val stylusButtonEraser =
                    primaryToolType == MotionEvent.TOOL_TYPE_ERASER ||
                        (event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0

                val effectiveTool = if (stylusButtonEraser) ToolType.ERASER else toolConfig.activeTool
                val (wx, wy) = screenToWorld(event.x, event.y)
                val (targetIdx, localY) = resolvePageAtWorldY(wy)
                val targetPage = pages.getOrNull(targetIdx) ?: activePage
                val (px, py) = if (showRuler && effectiveTool in listOf(
                        ToolType.FOUNTAIN_PEN, ToolType.BALLPOINT, ToolType.PENCIL,
                        ToolType.ART_PEN, ToolType.HIGHLIGHTER
                    )
                ) {
                    StrokeGeometry.snapPointToRuler(wx, localY, rulerCx, rulerCy, rulerAngleDeg)
                } else {
                    wx to localY
                }

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        // Disable VSync input batching so 120Hz/240Hz stylus packets arrive with ~0ms queue lag
                        hostView.requestUnbufferedDispatch(event)
                        motionPredictor.record(event)
                        drawingTargetPageIndex = targetIdx
                        directDragMode = 0
                        directTargetId = ""
                        directUndoRecorded = false
                        holdSnappedToShape = false
                        holdLastMoveMs = event.eventTime
                        holdAnchorX = px
                        holdAnchorY = py

                        val handleRadius = 34f / scale.coerceAtLeast(0.5f)

                        // 0A. Ruler direct drag & rotate handle
                        if (showRuler) {
                            val rad = Math.toRadians(rulerAngleDeg.toDouble())
                            val cosA = kotlin.math.cos(rad).toFloat()
                            val sinA = kotlin.math.sin(rad).toFloat()
                            val rotKnobX = rulerCx + 455f * cosA
                            val rotKnobY = rulerCy + 455f * sinA
                            if (hypot((wx - rotKnobX).toDouble(), (localY - rotKnobY).toDouble()) <= handleRadius * 1.3f) {
                                directDragMode = 2 // RULER_ROTATE
                                lastDragPageX = wx
                                lastDragPageY = localY
                                return@pointerInteropFilter true
                            }
                            val dxR = wx - rulerCx
                            val dyR = localY - rulerCy
                            val rLocalX = dxR * cosA + dyR * sinA
                            val rLocalY = -dxR * sinA + dyR * cosA
                            if (kotlin.math.abs(rLocalX) <= 440f && kotlin.math.abs(rLocalY) <= 36f) {
                                directDragMode = 1 // RULER_MOVE
                                lastDragPageX = wx
                                lastDragPageY = localY
                                return@pointerInteropFilter true
                            }
                        }

                        // 0B. Selected Image handles (resize / rotate / move)
                        if (selectedImageId != null) {
                            val selImg = targetPage.images.find { it.id == selectedImageId }
                            if (selImg != null) {
                                val rotX = selImg.x + selImg.width * 0.5f
                                val rotY = selImg.y - 34f / scale.coerceAtLeast(0.5f)
                                val brX = selImg.x + selImg.width
                                val brY = selImg.y + selImg.height
                                when {
                                    hypot((wx - rotX).toDouble(), (localY - rotY).toDouble()) <= handleRadius -> {
                                        directDragMode = 5 // IMAGE_ROTATE
                                        directTargetId = selImg.id
                                        lastDragPageX = wx; lastDragPageY = localY
                                        return@pointerInteropFilter true
                                    }
                                    hypot((wx - brX).toDouble(), (localY - brY).toDouble()) <= handleRadius -> {
                                        directDragMode = 4 // IMAGE_RESIZE
                                        directTargetId = selImg.id
                                        lastDragPageX = wx; lastDragPageY = localY
                                        return@pointerInteropFilter true
                                    }
                                    HitTestEngine.hitTestImage(selImg, wx, localY) -> {
                                        directDragMode = 3 // IMAGE_MOVE
                                        directTargetId = selImg.id
                                        lastDragPageX = wx; lastDragPageY = localY
                                        return@pointerInteropFilter true
                                    }
                                }
                            }
                        }

                        // 0C. Selected Sticker handles (resize / rotate / move)
                        if (selectedStickerId != null) {
                            val selSt = targetPage.stickers.find { it.id == selectedStickerId }
                            if (selSt != null) {
                                val rotX = selSt.x + selSt.width * 0.5f
                                val rotY = selSt.y - 34f / scale.coerceAtLeast(0.5f)
                                val brX = selSt.x + selSt.width
                                val brY = selSt.y + selSt.height
                                when {
                                    hypot((wx - rotX).toDouble(), (localY - rotY).toDouble()) <= handleRadius -> {
                                        directDragMode = 8 // STICKER_ROTATE
                                        directTargetId = selSt.id
                                        lastDragPageX = wx; lastDragPageY = localY
                                        return@pointerInteropFilter true
                                    }
                                    hypot((wx - brX).toDouble(), (localY - brY).toDouble()) <= handleRadius -> {
                                        directDragMode = 7 // STICKER_RESIZE
                                        directTargetId = selSt.id
                                        lastDragPageX = wx; lastDragPageY = localY
                                        return@pointerInteropFilter true
                                    }
                                    HitTestEngine.hitTestSticker(selSt, wx, localY) -> {
                                        directDragMode = 6 // STICKER_MOVE
                                        directTargetId = selSt.id
                                        lastDragPageX = wx; lastDragPageY = localY
                                        return@pointerInteropFilter true
                                    }
                                }
                            }
                        }

                        // 1. Check Hyperlink tap
                        if (effectiveTool != ToolType.ERASER && effectiveTool != ToolType.LASSO) {
                            val link = targetPage.hyperlinks.lastOrNull { HitTestEngine.hitTestHyperlink(it, wx, localY) }
                            if (link != null) {
                                onTapHyperlink(link.targetPageIndex)
                                return@pointerInteropFilter true
                            }
                        }

                        // 2. Check Image or Sticker tap (select for move/resize/rotate/crop/delete)
                        if (effectiveTool == ToolType.IMAGE || effectiveTool == ToolType.STICKER || effectiveTool == ToolType.LASSO) {
                            val st = targetPage.stickers.lastOrNull { HitTestEngine.hitTestSticker(it, wx, localY) }
                            if (st != null) {
                                onSelectSticker(st.id)
                                directDragMode = 6
                                directTargetId = st.id
                                lastDragPageX = wx; lastDragPageY = localY
                                return@pointerInteropFilter true
                            }
                            val img = targetPage.images.lastOrNull { HitTestEngine.hitTestImage(it, wx, localY) }
                            if (img != null) {
                                onSelectImage(img.id)
                                directDragMode = 3
                                directTargetId = img.id
                                lastDragPageX = wx; lastDragPageY = localY
                                return@pointerInteropFilter true
                            }
                            if (selectedImageId != null) onSelectImage(null)
                            if (selectedStickerId != null) onSelectSticker(null)
                        } else {
                            if (selectedImageId != null) onSelectImage(null)
                            if (selectedStickerId != null) onSelectSticker(null)
                        }

                        // 3. Check Active Recall Tape tap
                        if (effectiveTool != ToolType.ERASER && effectiveTool != ToolType.TAPE && effectiveTool != ToolType.LASSO) {
                            if (onTapTape(wx, localY)) return@pointerInteropFilter true
                        }

                        // 4. Text tool tap
                        if (effectiveTool == ToolType.TEXT) {
                            val existing = targetPage.textBoxes.lastOrNull { HitTestEngine.hitTestTextBox(it, wx, localY) }
                            onRequestTextEdit(existing?.x ?: wx, existing?.y ?: localY, existing?.id, existing?.text ?: "")
                            return@pointerInteropFilter true
                        }

                        // 5. Hyperlink creation tool tap
                        if (effectiveTool == ToolType.HYPERLINK) {
                            onRequestHyperlinkCreate(wx, localY)
                            return@pointerInteropFilter true
                        }

                        // 6. Drag existing Lasso selection
                        if (effectiveTool == ToolType.LASSO && selectedStrokeIds.isNotEmpty()) {
                            val selStrokes = targetPage.strokes.filter { it.id in selectedStrokeIds }
                            val box = HitTestEngine.boundingBoxOfStrokes(selStrokes)
                            if (box != null && wx in (box[0] - 16f)..(box[2] + 16f) &&
                                localY in (box[1] - 16f)..(box[3] + 16f)
                            ) {
                                isDraggingLassoSelection = true
                                dragUndoRecorded = false
                                lastDragPageX = wx
                                lastDragPageY = localY
                                return@pointerInteropFilter true
                            }
                        }

                        activeDrawingTool = effectiveTool
                        strokeStartTimeMs = event.eventTime
                        eraserHasHitInGesture = false
                        oneEuro.reset()
                        predictor.reset()
                        livePoints.clear()

                        if (effectiveTool == ToolType.ERASER) {
                            onEraseAt(targetIdx, wx, localY, true)
                            eraserHasHitInGesture = true
                        } else {
                            val (fx, fy) = oneEuro.filter(px, py, event.eventTime)
                            val pt = StrokePoint(fx, fy, event.pressure.coerceIn(0.05f, 1f), 0L)
                            if (effectiveTool == ToolType.LASER) {
                                laserTrail.clear()
                                laserTrail.add(pt)
                            } else {
                                livePoints.add(pt)
                                predictor.record(pt)
                            }
                        }
                    }

                    MotionEvent.ACTION_MOVE -> {
                        // Direct manipulation of Ruler, Image, or Sticker handles
                        if (directDragMode != 0) {
                            val dx = wx - lastDragPageX
                            val dy = localY - lastDragPageY
                            lastDragPageX = wx
                            lastDragPageY = localY
                            val recordUndo = !directUndoRecorded
                            directUndoRecorded = true

                            when (directDragMode) {
                                1 -> { // RULER_MOVE
                                    rulerCx += dx
                                    rulerCy += dy
                                }
                                2 -> { // RULER_ROTATE with magnetic 15-degree snap
                                    val rawDeg = Math.toDegrees(kotlin.math.atan2((localY - rulerCy).toDouble(), (wx - rulerCx).toDouble())).toFloat()
                                    val nearest15 = (kotlin.math.round(rawDeg / 15f) * 15f)
                                    rulerAngleDeg = if (kotlin.math.abs(rawDeg - nearest15) < 4.5f) nearest15 else rawDeg
                                }
                                3 -> onTransformImage(directTargetId, dx, dy, null, null, null, recordUndo)
                                4 -> {
                                    targetPage.images.find { it.id == directTargetId }?.let { img ->
                                        onTransformImage(directTargetId, 0f, 0f, (wx - img.x).coerceAtLeast(60f), (localY - img.y).coerceAtLeast(60f), null, recordUndo)
                                    }
                                }
                                5 -> {
                                    targetPage.images.find { it.id == directTargetId }?.let { img ->
                                        val cx = img.x + img.width * 0.5f
                                        val cy = img.y + img.height * 0.5f
                                        val deg = Math.toDegrees(kotlin.math.atan2((localY - cy).toDouble(), (wx - cx).toDouble())).toFloat() + 90f
                                        onTransformImage(directTargetId, 0f, 0f, null, null, deg, recordUndo)
                                    }
                                }
                                6 -> onTransformSticker(directTargetId, dx, dy, null, null, null, recordUndo)
                                7 -> {
                                    targetPage.stickers.find { it.id == directTargetId }?.let { st ->
                                        onTransformSticker(directTargetId, 0f, 0f, (wx - st.x).coerceAtLeast(80f), (localY - st.y).coerceAtLeast(48f), null, recordUndo)
                                    }
                                }
                                8 -> {
                                    targetPage.stickers.find { it.id == directTargetId }?.let { st ->
                                        val cx = st.x + st.width * 0.5f
                                        val cy = st.y + st.height * 0.5f
                                        val deg = Math.toDegrees(kotlin.math.atan2((localY - cy).toDouble(), (wx - cx).toDouble())).toFloat() + 90f
                                        onTransformSticker(directTargetId, 0f, 0f, null, null, deg, recordUndo)
                                    }
                                }
                            }
                            return@pointerInteropFilter true
                        }

                        if (isDraggingLassoSelection) {
                            val dx = wx - lastDragPageX
                            val dy = localY - lastDragPageY
                            lastDragPageX = wx
                            lastDragPageY = localY
                            onDragSelection(dx, dy, !dragUndoRecorded)
                            dragUndoRecorded = true
                            return@pointerInteropFilter true
                        }

                        val tool = activeDrawingTool ?: return@pointerInteropFilter true
                        val pageTop = if (isContinuous) pageTops.getOrElse(drawingTargetPageIndex) { 0f } else 0f

                        if (tool == ToolType.ERASER) {
                            for (h in 0 until event.historySize) {
                                val (hwx, hwy) = screenToWorld(event.getHistoricalX(h), event.getHistoricalY(h))
                                onEraseAt(drawingTargetPageIndex, hwx, hwy - pageTop, !eraserHasHitInGesture)
                                eraserHasHitInGesture = true
                            }
                            onEraseAt(drawingTargetPageIndex, wx, wy - pageTop, !eraserHasHitInGesture)
                            eraserHasHitInGesture = true
                            return@pointerInteropFilter true
                        }

                        motionPredictor.record(event)

                        for (h in 0 until event.historySize) {
                            val (hwx, hwy) = screenToWorld(event.getHistoricalX(h), event.getHistoricalY(h))
                            val hLocalY = hwy - pageTop
                            val (hpx, hpy) = if (showRuler) {
                                StrokeGeometry.snapPointToRuler(hwx, hLocalY, rulerCx, rulerCy, rulerAngleDeg)
                            } else hwx to hLocalY
                            val ht = event.getHistoricalEventTime(h)
                            val hp = event.getHistoricalPressure(h).coerceIn(0.05f, 1f)
                            val (fx, fy) = oneEuro.filter(hpx, hpy, ht)
                            val pt = StrokePoint(fx, fy, hp, (ht - strokeStartTimeMs).coerceAtLeast(0L))
                            if (tool == ToolType.LASER) {
                                laserTrail.add(pt)
                                if (laserTrail.size > 36) laserTrail.removeAt(0)
                            } else if (!holdSnappedToShape) {
                                livePoints.add(pt)
                                predictor.record(pt)
                            }
                        }

                        val curLocalY = wy - pageTop
                        val (cpx, cpy) = if (showRuler) {
                            StrokeGeometry.snapPointToRuler(wx, curLocalY, rulerCx, rulerCy, rulerAngleDeg)
                        } else wx to curLocalY
                        val (fx, fy) = oneEuro.filter(cpx, cpy, event.eventTime)
                        val pt = StrokePoint(fx, fy, event.pressure.coerceIn(0.05f, 1f), (event.eventTime - strokeStartTimeMs).coerceAtLeast(0L))
                        if (tool == ToolType.LASER) {
                            laserTrail.add(pt)
                            if (laserTrail.size > 36) laserTrail.removeAt(0)
                            pushFrontBufferFrame(tool, pageTop, emptyList())
                        } else {
                            // Freenotes Hold-to-Shape: pause stylus >= 420ms after drawing -> auto-snap to crisp shape!
                            if (holdToShapeEnabled && !holdSnappedToShape &&
                                tool in listOf(ToolType.FOUNTAIN_PEN, ToolType.BALLPOINT, ToolType.PENCIL, ToolType.ART_PEN, ToolType.HIGHLIGHTER)
                            ) {
                                val distFromAnchor = hypot((fx - holdAnchorX).toDouble(), (fy - holdAnchorY).toDouble()).toFloat()
                                if (distFromAnchor > 14f) {
                                    holdAnchorX = fx
                                    holdAnchorY = fy
                                    holdLastMoveMs = event.eventTime
                                } else if (livePoints.size >= 8 && (event.eventTime - holdLastMoveMs) >= 420L) {
                                    val span = hypot(
                                        (livePoints.last().x - livePoints.first().x).toDouble(),
                                        (livePoints.last().y - livePoints.first().y).toDouble()
                                    ).toFloat()
                                    if (span >= 36f || livePoints.size >= 18) {
                                        val snapped = StrokeGeometry.snapToShape(livePoints, com.johnlrt.freenotes.data.ShapeKind.AUTO)
                                        livePoints.clear()
                                        livePoints.addAll(snapped)
                                        holdSnappedToShape = true
                                        pushFrontBufferFrame(tool, pageTop, emptyList())
                                        return@pointerInteropFilter true
                                    }
                                }
                            }

                            if (!holdSnappedToShape) {
                                livePoints.add(pt)
                                predictor.record(pt)
                            } else if (livePoints.size == 2) {
                                // While holding a snapped straight line, let user adjust endpoint live!
                                livePoints[1] = pt
                            }

                            // Combine OS hardware Kalman predictor with StylusPredictor fallback
                            val predPts = if (!holdSnappedToShape && tool != ToolType.TAPE && tool != ToolType.LASSO) {
                                val osPred = motionPredictor.predict()
                                if (osPred != null) {
                                    val list = ArrayList<StrokePoint>(osPred.historySize + 1)
                                    for (hi in 0 until osPred.historySize) {
                                        val (pwx, pwy) = screenToWorld(osPred.getHistoricalX(hi), osPred.getHistoricalY(hi))
                                        val plY = pwy - pageTop
                                        val (spx, spy) = if (showRuler) {
                                            StrokeGeometry.snapPointToRuler(pwx, plY, rulerCx, rulerCy, rulerAngleDeg)
                                        } else pwx to plY
                                        list.add(
                                            StrokePoint(
                                                spx, spy,
                                                osPred.getHistoricalPressure(hi).coerceIn(0.05f, 1f),
                                                (osPred.getHistoricalEventTime(hi) - strokeStartTimeMs).coerceAtLeast(pt.timeOffsetMs)
                                            )
                                        )
                                    }
                                    val (pwx, pwy) = screenToWorld(osPred.x, osPred.y)
                                    val plY = pwy - pageTop
                                    val (spx, spy) = if (showRuler) {
                                        StrokeGeometry.snapPointToRuler(pwx, plY, rulerCx, rulerCy, rulerAngleDeg)
                                    } else pwx to plY
                                    list.add(
                                        StrokePoint(
                                            spx, spy,
                                            osPred.pressure.coerceIn(0.05f, 1f),
                                            (osPred.eventTime - strokeStartTimeMs).coerceAtLeast(pt.timeOffsetMs)
                                        )
                                    )
                                    osPred.recycle()
                                    list
                                } else {
                                    predictor.predict()
                                }
                            } else {
                                emptyList()
                            }

                            pushFrontBufferFrame(tool, pageTop, predPts)
                        }
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (directDragMode != 0) {
                            directDragMode = 0
                            directTargetId = ""
                            directUndoRecorded = false
                            return@pointerInteropFilter true
                        }
                        if (isDraggingLassoSelection) {
                            isDraggingLassoSelection = false
                            return@pointerInteropFilter true
                        }
                        val tool = activeDrawingTool
                        if (tool == ToolType.LASSO && livePoints.size >= 3) {
                            onSelectLasso(livePoints.toList())
                            lowLatencyController.clearImmediate()
                        } else if (tool == ToolType.LASER) {
                            laserTrail.clear()
                            lowLatencyController.clearImmediate()
                        } else if (tool != null && tool != ToolType.ERASER && livePoints.size >= 2) {
                            onCommitStroke(drawingTargetPageIndex, livePoints.toList(), tool)
                            lowLatencyController.clearAfterCommit()
                        } else {
                            lowLatencyController.clearImmediate()
                        }
                        livePoints.clear()
                        activeDrawingTool = null
                        holdSnappedToShape = false
                    }
                }
                true
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            withTransform({
                translate(left = offsetX, top = offsetY)
                scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
            }) {
                val pagesToDraw = if (isContinuous) pages.indices.toList() else listOf(currentPageIndex)

                for (pIdx in pagesToDraw) {
                    val p = pages[pIdx]
                    val topY = if (isContinuous) pageTops[pIdx] else 0f
                    val pw = p.orientation.widthPx
                    val ph = p.orientation.heightPx

                    withTransform({ translate(top = topY) }) {
                        if (isInfiniteCanvas) {
                            // Unbounded tiled procedural background across visible viewport
                            val worldLeft = -offsetX / scale - 120f
                            val worldTop = -offsetY / scale - 120f
                            val worldRight = (size.width - offsetX) / scale + 120f
                            val worldBottom = (size.height - offsetY) / scale + 120f
                            val gridColor = Color(p.paperColor.gridArgb)
                            val step = 48f
                            val startX = kotlin.math.floor(worldLeft / step) * step
                            val startY = kotlin.math.floor(worldTop / step) * step
                            when (p.template) {
                                PaperTemplate.DOTTED -> {
                                    var x = startX
                                    while (x <= worldRight) {
                                        var y = startY
                                        while (y <= worldBottom) {
                                            drawCircle(gridColor, radius = 2.2f, center = Offset(x, y))
                                            y += step
                                        }
                                        x += step
                                    }
                                }
                                PaperTemplate.RULED, PaperTemplate.WIDE_RULED -> {
                                    val rStep = if (p.template == PaperTemplate.WIDE_RULED) 68f else 48f
                                    var y = kotlin.math.floor(worldTop / rStep) * rStep
                                    while (y <= worldBottom) {
                                        drawLine(gridColor, Offset(worldLeft, y), Offset(worldRight, y), strokeWidth = 1.4f)
                                        y += rStep
                                    }
                                }
                                PaperTemplate.BLANK -> Unit
                                else -> {
                                    var x = startX
                                    while (x <= worldRight) {
                                        drawLine(gridColor, Offset(x, worldTop), Offset(x, worldBottom), strokeWidth = 1.2f)
                                        x += step
                                    }
                                    var y = startY
                                    while (y <= worldBottom) {
                                        drawLine(gridColor, Offset(worldLeft, y), Offset(worldRight, y), strokeWidth = 1.2f)
                                        y += step
                                    }
                                }
                            }
                        } else {
                            // Page drop shadow
                            drawRect(Color.Black.copy(alpha = 0.11f), Offset(6f, 8f), Size(pw, ph))

                            // 1. Paper surface
                            drawRect(Color(p.paperColor.argb), Offset.Zero, Size(pw, ph))

                            // 2. PDF backdrop (with optional Dark-Mode Inversion)
                            pdfBitmaps[p.id]?.let { bmp ->
                                drawImage(
                                    image = bmp.asImageBitmap(),
                                    dstOffset = IntOffset.Zero,
                                    dstSize = IntSize(pw.toInt(), ph.toInt()),
                                    colorFilter = if (darkPdfInvert) ColorFilter.colorMatrix(InvertColorMatrix) else null
                                )
                            }

                            // 3. Procedural paper template
                            drawPaperTemplate(p.template, Color(p.paperColor.gridArgb), pw, ph)
                        }

                        val visibleLayers = p.layers.filter { it.isVisible }.map { it.id }.toSet()
                        val handleR = 11f / scale.coerceAtLeast(0.4f)
                        val strokeW = 2.8f / scale.coerceAtLeast(0.4f)

                        // 4. Inserted Images (with rotation + Freenotes resize/rotate handles)
                        for (img in p.images) {
                            if (img.layerId !in visibleLayers) continue
                            imageBitmaps[img.filePath]?.let { bmp ->
                                rotate(degrees = img.rotationDeg, pivot = Offset(img.x + img.width * 0.5f, img.y + img.height * 0.5f)) {
                                    drawImage(
                                        image = bmp.asImageBitmap(),
                                        dstOffset = IntOffset(img.x.toInt(), img.y.toInt()),
                                        dstSize = IntSize(img.width.toInt(), img.height.toInt())
                                    )
                                }
                                if (img.id == selectedImageId) {
                                    drawRect(
                                        color = Color(0xFF2563EB),
                                        topLeft = Offset(img.x, img.y),
                                        size = Size(img.width, img.height),
                                        style = ComposeStroke(width = strokeW)
                                    )
                                    // Top rotation stem + knob
                                    val rotX = img.x + img.width * 0.5f
                                    val rotY = img.y - 34f / scale.coerceAtLeast(0.5f)
                                    drawLine(Color(0xFF2563EB), Offset(rotX, img.y), Offset(rotX, rotY), strokeWidth = strokeW)
                                    drawCircle(Color.White, radius = handleR, center = Offset(rotX, rotY))
                                    drawCircle(Color(0xFF2563EB), radius = handleR, center = Offset(rotX, rotY), style = ComposeStroke(width = strokeW))
                                    // Corner handles (bottom-right highlighted for resize)
                                    val corners = listOf(
                                        Offset(img.x, img.y),
                                        Offset(img.x + img.width, img.y),
                                        Offset(img.x, img.y + img.height),
                                        Offset(img.x + img.width, img.y + img.height)
                                    )
                                    corners.forEachIndexed { idx, c ->
                                        drawCircle(if (idx == 3) Color(0xFF2563EB) else Color.White, radius = handleR, center = c)
                                        drawCircle(Color(0xFF2563EB), radius = handleR, center = c, style = ComposeStroke(width = strokeW))
                                    }
                                }
                            }
                        }

                        // 5. Behind-ink Highlighters
                        for (stroke in p.strokes) {
                            if (stroke.layerId in visibleLayers && stroke.toolType == ToolType.HIGHLIGHTER) {
                                drawStrokeOnCanvas(stroke, isSelected = stroke.id in selectedStrokeIds)
                            }
                        }

                        // 6. Ink Strokes (Polygonal Ribbons, Pencils, Shapes)
                        for (stroke in p.strokes) {
                            if (stroke.layerId in visibleLayers && stroke.toolType != ToolType.HIGHLIGHTER) {
                                drawStrokeOnCanvas(stroke, isSelected = stroke.id in selectedStrokeIds)
                            }
                        }

                        // 7. Stickers (with rotation + Freenotes resize/rotate handles)
                        val stickerTextPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            textSize = 26f
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                        for (st in p.stickers) {
                            if (st.layerId !in visibleLayers) continue
                            rotate(degrees = st.rotationDeg, pivot = Offset(st.x + st.width * 0.5f, st.y + st.height * 0.5f)) {
                                drawRoundRect(
                                    color = Color(st.bgColorArgb),
                                    topLeft = Offset(st.x, st.y),
                                    size = Size(st.width, st.height),
                                    cornerRadius = CornerRadius(16f, 16f)
                                )
                                drawRoundRect(
                                    color = Color(st.borderColorArgb),
                                    topLeft = Offset(st.x, st.y),
                                    size = Size(st.width, st.height),
                                    cornerRadius = CornerRadius(16f, 16f),
                                    style = ComposeStroke(width = 3f)
                                )
                                stickerTextPaint.color = st.textColorArgb.toInt()
                                val label = listOf(st.emoji, st.label).filter { it.isNotBlank() }.joinToString(" ")
                                drawContext.canvas.nativeCanvas.drawText(
                                    label,
                                    st.x + st.width * 0.5f,
                                    st.y + st.height * 0.5f + 9f,
                                    stickerTextPaint
                                )
                            }
                            if (st.id == selectedStickerId) {
                                drawRect(
                                    color = Color(0xFF2563EB),
                                    topLeft = Offset(st.x, st.y),
                                    size = Size(st.width, st.height),
                                    style = ComposeStroke(width = strokeW)
                                )
                                val rotX = st.x + st.width * 0.5f
                                val rotY = st.y - 34f / scale.coerceAtLeast(0.5f)
                                drawLine(Color(0xFF2563EB), Offset(rotX, st.y), Offset(rotX, rotY), strokeWidth = strokeW)
                                drawCircle(Color.White, radius = handleR, center = Offset(rotX, rotY))
                                drawCircle(Color(0xFF2563EB), radius = handleR, center = Offset(rotX, rotY), style = ComposeStroke(width = strokeW))
                                val br = Offset(st.x + st.width, st.y + st.height)
                                drawCircle(Color(0xFF2563EB), radius = handleR, center = br)
                                drawCircle(Color.White, radius = handleR * 0.45f, center = br)
                            }
                        }

                        // 9. Clickable Internal Hyperlinks
                        val linkTextPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            textSize = 22f
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        }
                        for (link in p.hyperlinks) {
                            if (link.layerId !in visibleLayers) continue
                            val chipW = (link.label.length * 14f + 44f).coerceAtLeast(140f)
                            drawRoundRect(
                                color = Color(link.colorArgb).copy(alpha = 0.14f),
                                topLeft = Offset(link.x, link.y),
                                size = Size(chipW, 44f),
                                cornerRadius = CornerRadius(22f, 22f)
                            )
                            drawRoundRect(
                                color = Color(link.colorArgb),
                                topLeft = Offset(link.x, link.y),
                                size = Size(chipW, 44f),
                                cornerRadius = CornerRadius(22f, 22f),
                                style = ComposeStroke(width = 2f)
                            )
                            linkTextPaint.color = link.colorArgb.toInt()
                            drawContext.canvas.nativeCanvas.drawText(link.label, link.x + 18f, link.y + 30f, linkTextPaint)
                        }

                        // 10. Text Boxes
                        val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                        for (box in p.textBoxes) {
                            if (box.layerId !in visibleLayers) continue
                            textPaint.color = box.colorArgb.toInt()
                            textPaint.textSize = box.fontSizeSp * 1.4f
                            textPaint.typeface = if (box.isBold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
                            var yPos = box.y + textPaint.textSize
                            box.text.split("\n").forEach { line ->
                                drawContext.canvas.nativeCanvas.drawText(line, box.x, yPos, textPaint)
                                yPos += textPaint.textSize * 1.35f
                            }
                        }

                        // 11. Active Recall Tapes
                        for (tape in p.tapes) {
                            if (tape.layerId in visibleLayers) drawTapeItem(tape)
                        }

                        // 12. Lasso selection bounding box
                        if (pIdx == currentPageIndex && selectedStrokeIds.isNotEmpty()) {
                            val selStrokes = p.strokes.filter { it.id in selectedStrokeIds }
                            HitTestEngine.boundingBoxOfStrokes(selStrokes)?.let { b ->
                                val pad = 14f
                                drawRect(
                                    color = Color(0xFF2563EB).copy(alpha = 0.08f),
                                    topLeft = Offset(b[0] - pad, b[1] - pad),
                                    size = Size(b[2] - b[0] + pad * 2, b[3] - b[1] + pad * 2)
                                )
                                drawRect(
                                    color = Color(0xFF2563EB),
                                    topLeft = Offset(b[0] - pad, b[1] - pad),
                                    size = Size(b[2] - b[0] + pad * 2, b[3] - b[1] + pad * 2),
                                    style = ComposeStroke(
                                        width = 2.5f / scale.coerceAtLeast(0.5f),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                                    )
                                )
                            }
                        }

                        // 13. Zoom Window Target Box indicator
                        if (pIdx == currentPageIndex && showZoomWindow) {
                            drawRect(
                                color = Color(0xFF2563EB).copy(alpha = 0.08f),
                                topLeft = Offset(zoomRect.left, zoomRect.top),
                                size = Size(zoomRect.width, zoomRect.height)
                            )
                            drawRect(
                                color = Color(0xFF2563EB),
                                topLeft = Offset(zoomRect.left, zoomRect.top),
                                size = Size(zoomRect.width, zoomRect.height),
                                style = ComposeStroke(width = 3f)
                            )
                        }

                        // 14. On-Canvas Transparent Magnetic Ruler
                        if (pIdx == currentPageIndex && showRuler) {
                            rotate(degrees = rulerAngleDeg, pivot = Offset(rulerCx, rulerCy)) {
                                val rLen = 960f
                                val rH = 76f
                                drawRoundRect(
                                    color = Color(0xFF1E293B).copy(alpha = 0.14f),
                                    topLeft = Offset(rulerCx - rLen / 2f, rulerCy - rH / 2f),
                                    size = Size(rLen, rH),
                                    cornerRadius = CornerRadius(8f, 8f)
                                )
                                drawRoundRect(
                                    color = Color(0xFF2563EB).copy(alpha = 0.75f),
                                    topLeft = Offset(rulerCx - rLen / 2f, rulerCy - rH / 2f),
                                    size = Size(rLen, rH),
                                    cornerRadius = CornerRadius(8f, 8f),
                                    style = ComposeStroke(width = 2f)
                                )
                                var tx = rulerCx - rLen / 2f + 20f
                                var tickIdx = 0
                                while (tx < rulerCx + rLen / 2f - 40f) {
                                    val tickLen = if (tickIdx % 5 == 0) 20f else 10f
                                    drawLine(
                                        color = Color(0xFF1E293B).copy(alpha = 0.65f),
                                        start = Offset(tx, rulerCy - rH / 2f),
                                        end = Offset(tx, rulerCy - rH / 2f + tickLen),
                                        strokeWidth = 1.6f
                                    )
                                    tx += 20f
                                    tickIdx++
                                }
                                // Live degree readout in center of ruler
                                val degPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                    color = 0xFF1E3A8A.toInt()
                                    textSize = 22f
                                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                                    textAlign = android.graphics.Paint.Align.CENTER
                                }
                                drawContext.canvas.nativeCanvas.drawText(
                                    "${kotlin.math.round(rulerAngleDeg).toInt()}°",
                                    rulerCx,
                                    rulerCy + 14f,
                                    degPaint
                                )
                                // Right-end circular rotation handle
                                val knobCenter = Offset(rulerCx + 455f, rulerCy)
                                drawCircle(Color(0xFF2563EB), radius = 16f, center = knobCenter)
                                drawCircle(Color.White, radius = 7f, center = knobCenter)
                            }
                        }

                    }
                }
            }
        }

        // Hardware single-buffered GPU front-buffer ink layer (120Hz+, ~2-5ms ink-under-nib latency)
        if (!readOnly) {
            LowLatencyInkOverlay(
                controller = lowLatencyController,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun handlePanZoomEvent(
    event: MotionEvent,
    zoomLocked: Boolean,
    isPanZooming: Boolean,
    setPanZooming: (Boolean) -> Unit,
    prevPinchDist: Float,
    setPrevPinchDist: (Float) -> Unit,
    prevMidX: Float,
    prevMidY: Float,
    setPrevMid: (Float, Float) -> Unit,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    isInfiniteCanvas: Boolean = false,
    onUpdateTransform: (Float, Float, Float) -> Unit
) {
    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
            setPanZooming(true)
            if (event.pointerCount >= 2) {
                val dx = event.getX(0) - event.getX(1)
                val dy = event.getY(0) - event.getY(1)
                setPrevPinchDist(hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1f))
                setPrevMid((event.getX(0) + event.getX(1)) / 2f, (event.getY(0) + event.getY(1)) / 2f)
            } else {
                setPrevMid(event.x, event.y)
                setPrevPinchDist(0f)
            }
        }
        MotionEvent.ACTION_MOVE -> {
            if (!isPanZooming) return
            if (event.pointerCount >= 2) {
                val dx = event.getX(0) - event.getX(1)
                val dy = event.getY(0) - event.getY(1)
                val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1f)
                val midX = (event.getX(0) + event.getX(1)) / 2f
                val midY = (event.getY(0) + event.getY(1)) / 2f

                val zoomFactor = if (!zoomLocked && prevPinchDist > 10f) dist / prevPinchDist else 1f
                val minZoom = if (isInfiniteCanvas) 0.15f else 0.3f
                val maxZoom = if (isInfiniteCanvas) 5.0f else 4.5f
                val newScale = (scale * zoomFactor).coerceIn(minZoom, maxZoom)
                val actualZoom = newScale / scale

                val panX = midX - prevMidX
                val panY = midY - prevMidY
                val newOffX = midX - (midX - offsetX) * actualZoom + panX
                val newOffY = midY - (midY - offsetY) * actualZoom + panY

                setPrevPinchDist(dist)
                setPrevMid(midX, midY)
                onUpdateTransform(newScale, newOffX, newOffY)
            } else {
                val panX = event.x - prevMidX
                val panY = event.y - prevMidY
                setPrevMid(event.x, event.y)
                onUpdateTransform(scale, offsetX + panX, offsetY + panY)
            }
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            setPanZooming(false)
            setPrevPinchDist(0f)
        }
    }
}

private fun DrawScope.drawPaperTemplate(
    template: PaperTemplate,
    lineColor: Color,
    w: Float,
    h: Float
) {
    when (template) {
        PaperTemplate.BLANK -> Unit
        PaperTemplate.RULED, PaperTemplate.WIDE_RULED -> {
            val step = if (template == PaperTemplate.WIDE_RULED) 68f else 48f
            var y = 140f
            while (y < h - 60f) {
                drawLine(lineColor, Offset(64f, y), Offset(w - 64f, y), strokeWidth = 1.5f)
                y += step
            }
        }
        PaperTemplate.GRID -> {
            val step = 44f
            var x = step
            while (x < w) { drawLine(lineColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1.2f); x += step }
            var y = step
            while (y < h) { drawLine(lineColor, Offset(0f, y), Offset(w, y), strokeWidth = 1.2f); y += step }
        }
        PaperTemplate.DOTTED -> {
            val step = 44f
            var x = step
            while (x < w) {
                var y = step
                while (y < h) { drawCircle(lineColor, radius = 2.2f, center = Offset(x, y)); y += step }
                x += step
            }
        }
        PaperTemplate.CORNELL -> {
            val accent = lineColor.copy(alpha = 0.9f)
            drawLine(accent, Offset(0f, 160f), Offset(w, 160f), strokeWidth = 2.5f)
            drawLine(accent, Offset(300f, 160f), Offset(300f, h - 220f), strokeWidth = 2.5f)
            drawLine(accent, Offset(0f, h - 220f), Offset(w, h - 220f), strokeWidth = 2.5f)
            var y = 212f
            while (y < h - 220f) { drawLine(lineColor, Offset(320f, y), Offset(w - 48f, y), strokeWidth = 1.3f); y += 52f }
        }
        PaperTemplate.PLANNER -> {
            val step = 64f
            var y = 160f
            while (y < h - 80f) {
                drawRect(lineColor, Offset(72f, y - 22f), Size(24f, 24f), style = ComposeStroke(width = 2f))
                drawLine(lineColor, Offset(114f, y), Offset(w - 72f, y), strokeWidth = 1.5f)
                y += step
            }
        }
        PaperTemplate.MUSIC -> {
            var staffTop = 140f
            while (staffTop < h - 160f) {
                for (i in 0 until 5) {
                    val y = staffTop + i * 16f
                    drawLine(lineColor, Offset(72f, y), Offset(w - 72f, y), strokeWidth = 1.4f)
                }
                staffTop += 150f
            }
        }
        PaperTemplate.ISOMETRIC -> {
            val step = 52f
            val tan30 = 0.57735027f
            // Vertical lines
            var x = 0f
            while (x <= w) {
                drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1.1f)
                x += step
            }
            // +30 deg and -30 deg diagonals
            val dy = step * tan30
            var y = -w * tan30
            while (y <= h + w * tan30) {
                drawLine(gridColor, Offset(0f, y), Offset(w, y + w * tan30), strokeWidth = 1.1f)
                drawLine(gridColor, Offset(0f, y), Offset(w, y - w * tan30), strokeWidth = 1.1f)
                y += dy * 2f
            }
        }
        PaperTemplate.HEXAGONAL -> {
            val r = 28f
            val dx = r * 1.5f
            val dy = r * 1.7320508f
            var col = 0
            var cx = r
            while (cx < w + r) {
                var cy = if (col % 2 == 0) r else r + dy / 2f
                while (cy < h + r) {
                    val hexPath = Path()
                    for (k in 0..5) {
                        val a = Math.toRadians(60.0 * k)
                        val hx = cx + (r * kotlin.math.cos(a)).toFloat()
                        val hy = cy + (r * kotlin.math.sin(a)).toFloat()
                        if (k == 0) hexPath.moveTo(hx, hy) else hexPath.lineTo(hx, hy)
                    }
                    hexPath.close()
                    drawPath(hexPath, gridColor, style = ComposeStroke(width = 1.1f))
                    cy += dy
                }
                cx += dx
                col++
            }
        }
        PaperTemplate.WEEKLY_PLANNER -> {
            val pad = 48f
            val headerH = 120f
            drawLine(gridColor, Offset(pad, headerH), Offset(w - pad, headerH), strokeWidth = 2.5f)
            val cols = 2
            val rows = 4
            val cellW = (w - pad * 2) / cols
            val cellH = (h - headerH - pad) / rows
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    val left = pad + c * cellW
                    val top = headerH + 16f + r * cellH
                    drawRoundRect(
                        color = gridColor,
                        topLeft = Offset(left + 8f, top + 8f),
                        size = Size(cellW - 16f, cellH - 16f),
                        cornerRadius = CornerRadius(12f, 12f),
                        style = ComposeStroke(width = 2f)
                    )
                    drawLine(
                        color = gridColor,
                        start = Offset(left + 8f, top + 56f),
                        end = Offset(left + cellW - 8f, top + 56f),
                        strokeWidth = 1.6f
                    )
                }
            }
        }
        PaperTemplate.MONTHLY_CALENDAR -> {
            val pad = 44f
            val topY = 140f
            val cols = 7
            val rows = 5
            val cellW = (w - pad * 2) / cols
            val cellH = (h - topY - pad) / rows
            for (c in 0..cols) {
                val x = pad + c * cellW
                drawLine(gridColor, Offset(x, topY), Offset(x, topY + rows * cellH), strokeWidth = 1.6f)
            }
            for (r in 0..rows) {
                val y = topY + r * cellH
                drawLine(gridColor, Offset(pad, y), Offset(w - pad, y), strokeWidth = 1.6f)
            }
        }
        PaperTemplate.TWO_COLUMN -> {
            val midX = w * 0.5f
            drawLine(Color(0xFF94A3B8).copy(alpha = 0.6f), Offset(midX, 80f), Offset(midX, h - 60f), strokeWidth = 2.4f)
            var y = 130f
            while (y < h - 60f) {
                drawLine(gridColor, Offset(56f, y), Offset(midX - 24f, y), strokeWidth = 1.4f)
                drawLine(gridColor, Offset(midX + 24f, y), Offset(w - 56f, y), strokeWidth = 1.4f)
                y += 48f
            }
        }
        PaperTemplate.STORYBOARD -> {
            val pad = 56f
            val cols = 2
            val rows = 3
            val gap = 32f
            val frameW = (w - pad * 2 - gap) / cols
            val frameH = (h - pad * 2 - gap * 2) / rows
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    val left = pad + c * (frameW + gap)
                    val top = pad + r * (frameH + gap)
                    val boxH = frameH * 0.72f
                    drawRect(gridColor, Offset(left, top), Size(frameW, boxH), style = ComposeStroke(width = 2.2f))
                    drawLine(gridColor, Offset(left, top + boxH + 28f), Offset(left + frameW, top + boxH + 28f), strokeWidth = 1.4f)
                    drawLine(gridColor, Offset(left, top + boxH + 62f), Offset(left + frameW, top + boxH + 62f), strokeWidth = 1.4f)
                }
            }
        }
    }
}

private fun DrawScope.drawStrokeOnCanvas(stroke: Stroke, isSelected: Boolean) {
    if (stroke.points.size < 2) return
    val baseColor = Color(stroke.colorArgb)

    if (stroke.toolType == ToolType.HIGHLIGHTER || stroke.toolType == ToolType.SHAPE) {
        val path = Path().apply {
            moveTo(stroke.points.first().x, stroke.points.first().y)
            for (i in 1 until stroke.points.size) lineTo(stroke.points[i].x, stroke.points[i].y)
        }
        if (stroke.toolType == ToolType.SHAPE && stroke.isFilledShape) {
            drawPath(
                path = path,
                color = Color(stroke.fillColorArgb ?: stroke.colorArgb).copy(alpha = stroke.fillAlpha)
            )
        }
        val pathEffect = when {
            stroke.toolType == ToolType.HIGHLIGHTER && stroke.highlighterStyle == HighlighterStyle.DASHED ->
                PathEffect.dashPathEffect(floatArrayOf(28f, 16f), 0f)
            stroke.toolType == ToolType.HIGHLIGHTER && stroke.highlighterStyle == HighlighterStyle.DOTTED ->
                PathEffect.dashPathEffect(floatArrayOf(2f, stroke.baseWidth * 1.4f), 0f)
            stroke.toolType == ToolType.SHAPE && stroke.borderDashed ->
                PathEffect.dashPathEffect(floatArrayOf(18f, 12f), 0f)
            else -> null
        }
        val cap = if (stroke.toolType == ToolType.HIGHLIGHTER && stroke.highlighterStyle != HighlighterStyle.DOTTED) {
            StrokeCap.Square
        } else StrokeCap.Round
        val alpha = if (stroke.toolType == ToolType.HIGHLIGHTER) stroke.opacity.coerceIn(0.1f, 0.55f) else stroke.opacity
        drawPath(
            path = path,
            color = baseColor.copy(alpha = alpha),
            style = ComposeStroke(width = stroke.baseWidth, cap = cap, join = StrokeJoin.Round, pathEffect = pathEffect)
        )
        return
    }

    // Smooth filled polygonal ribbon for Fountain, Ballpoint, and Art Pen!
    if (stroke.toolType != ToolType.PENCIL) {
        val ribbon = StrokeGeometry.buildRibbonOutline(stroke)
        if (ribbon != null) {
            val path = Path().apply {
                moveTo(ribbon.leftEdge.first().first, ribbon.leftEdge.first().second)
                for (i in 1 until ribbon.leftEdge.size) {
                    lineTo(ribbon.leftEdge[i].first, ribbon.leftEdge[i].second)
                }
                for (i in ribbon.rightEdge.lastIndex downTo 0) {
                    lineTo(ribbon.rightEdge[i].first, ribbon.rightEdge[i].second)
                }
                close()
            }
            val color = baseColor.copy(alpha = stroke.opacity)
            drawPath(path = path, color = color)
            drawCircle(color, radius = ribbon.startCapRadius, center = Offset(ribbon.startCapCenter.first, ribbon.startCapCenter.second))
            drawCircle(color, radius = ribbon.endCapRadius, center = Offset(ribbon.endCapCenter.first, ribbon.endCapCenter.second))
            return
        }
    }

    // Pencil (pressure-modulated alpha grain)
    val segments = StrokeGeometry.buildSegments(stroke)
    for (seg in segments) {
        drawLine(
            color = baseColor.copy(alpha = seg.alpha),
            start = Offset(seg.x0, seg.y0),
            end = Offset(seg.x1, seg.y1),
            strokeWidth = if (isSelected) seg.width + 1.2f else seg.width,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawTapeItem(tape: TapeItem) {
    val color = Color(tape.colorArgb)
    if (tape.isRevealed) {
        drawLine(
            color = color.copy(alpha = 0.24f),
            start = Offset(tape.startX, tape.startY),
            end = Offset(tape.endX, tape.endY),
            strokeWidth = tape.thickness,
            cap = StrokeCap.Butt,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
        )
    } else {
        drawLine(
            color = color.copy(alpha = 0.96f),
            start = Offset(tape.startX, tape.startY),
            end = Offset(tape.endX, tape.endY),
            strokeWidth = tape.thickness,
            cap = StrokeCap.Butt
        )
        drawLine(
            color = Color.White.copy(alpha = 0.45f),
            start = Offset(tape.startX, tape.startY),
            end = Offset(tape.endX, tape.endY),
            strokeWidth = 2.5f,
            cap = StrokeCap.Butt,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f), 0f)
        )
    }
}
