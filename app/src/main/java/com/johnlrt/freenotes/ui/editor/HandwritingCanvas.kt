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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
    onCommitStroke: (Int, List<StrokePoint>, ToolType?) -> Unit,
    onEraseAt: (Int, Float, Float, Boolean) -> Unit,
    onSelectLasso: (List<StrokePoint>) -> Unit,
    onDragSelection: (Float, Float, Boolean) -> Unit,
    onTapTape: (Float, Float) -> Boolean,
    onTapHyperlink: (Int) -> Unit,
    onSelectImage: (String?) -> Unit,
    onRequestTextEdit: (Float, Float, String?, String) -> Unit,
    onRequestHyperlinkCreate: (Float, Float) -> Unit
) {
    val activePage = pages.getOrNull(currentPageIndex) ?: return
    val isContinuous = scrollDirection == ScrollDirection.VERTICAL_CONTINUOUS && pages.size > 1 && !readOnly

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

    // Live stroke state
    val livePoints = remember { mutableStateListOf<StrokePoint>() }
    val predictedPoints = remember { mutableStateListOf<StrokePoint>() }
    val laserTrail = remember { mutableStateListOf<StrokePoint>() }
    var activeDrawingTool by remember { mutableStateOf<ToolType?>(null) }
    var drawingTargetPageIndex by remember { mutableIntStateOf(currentPageIndex) }
    var strokeStartTimeMs by remember { mutableStateOf(0L) }
    var eraserHasHitInGesture by remember { mutableStateOf(false) }
    var isDraggingLassoSelection by remember { mutableStateOf(false) }
    var lastDragPageX by remember { mutableFloatStateOf(0f) }
    var lastDragPageY by remember { mutableFloatStateOf(0f) }
    var dragUndoRecorded by remember { mutableStateOf(false) }

    var isPanZooming by remember { mutableStateOf(false) }
    var prevPinchDist by remember { mutableFloatStateOf(0f) }
    var prevMidX by remember { mutableFloatStateOf(0f) }
    var prevMidY by remember { mutableFloatStateOf(0f) }

    val oneEuro = remember { OneEuroFilter2D() }
    val predictor = remember { StylusPredictor() }

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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFE5E9F0))
            .onSizeChanged { viewportSize = it }
            .pointerInteropFilter { event ->
                if (readOnly) {
                    handlePanZoomEvent(
                        event, zoomLocked, isPanZooming, { isPanZooming = it },
                        prevPinchDist, { prevPinchDist = it }, prevMidX, prevMidY,
                        { mx, my -> prevMidX = mx; prevMidY = my },
                        scale, offsetX, offsetY
                    ) { ns, nx, ny -> scale = ns; offsetX = nx; offsetY = ny }
                    return@pointerInteropFilter true
                }

                val pointerCount = event.pointerCount
                val primaryToolType = event.getToolType(0)
                val isStylus = primaryToolType == MotionEvent.TOOL_TYPE_STYLUS ||
                    primaryToolType == MotionEvent.TOOL_TYPE_ERASER

                if (pointerCount >= 2 || (stylusOnlyMode && !isStylus)) {
                    livePoints.clear()
                    predictedPoints.clear()
                    activeDrawingTool = null
                    handlePanZoomEvent(
                        event, zoomLocked, isPanZooming, { isPanZooming = it },
                        prevPinchDist, { prevPinchDist = it }, prevMidX, prevMidY,
                        { mx, my -> prevMidX = mx; prevMidY = my },
                        scale, offsetX, offsetY
                    ) { ns, nx, ny -> scale = ns; offsetX = nx; offsetY = ny }
                    return@pointerInteropFilter true
                }

                if (isPanZooming && event.actionMasked == MotionEvent.ACTION_UP) {
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
                        drawingTargetPageIndex = targetIdx

                        // 1. Check Hyperlink tap
                        if (effectiveTool != ToolType.ERASER && effectiveTool != ToolType.LASSO) {
                            val link = targetPage.hyperlinks.lastOrNull { HitTestEngine.hitTestHyperlink(it, wx, localY) }
                            if (link != null) {
                                onTapHyperlink(link.targetPageIndex)
                                return@pointerInteropFilter true
                            }
                        }

                        // 2. Check Image tap (select for cropping/deleting)
                        if (effectiveTool == ToolType.IMAGE || effectiveTool == ToolType.LASSO) {
                            val img = targetPage.images.lastOrNull { HitTestEngine.hitTestImage(it, wx, localY) }
                            if (img != null) {
                                onSelectImage(img.id)
                                return@pointerInteropFilter true
                            } else if (selectedImageId != null) {
                                onSelectImage(null)
                            }
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
                        predictedPoints.clear()

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
                            } else {
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
                        } else {
                            livePoints.add(pt)
                            predictor.record(pt)
                            predictedPoints.clear()
                            if (tool != ToolType.TAPE && tool != ToolType.LASSO) {
                                predictedPoints.addAll(predictor.predict())
                            }
                        }
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (isDraggingLassoSelection) {
                            isDraggingLassoSelection = false
                            return@pointerInteropFilter true
                        }
                        val tool = activeDrawingTool
                        if (tool == ToolType.LASSO && livePoints.size >= 3) {
                            onSelectLasso(livePoints.toList())
                        } else if (tool == ToolType.LASER) {
                            laserTrail.clear()
                        } else if (tool != null && tool != ToolType.ERASER && livePoints.size >= 2) {
                            onCommitStroke(drawingTargetPageIndex, livePoints.toList(), tool)
                        }
                        livePoints.clear()
                        predictedPoints.clear()
                        activeDrawingTool = null
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

                        val visibleLayers = p.layers.filter { it.isVisible }.map { it.id }.toSet()

                        // 4. Inserted Images
                        for (img in p.images) {
                            if (img.layerId !in visibleLayers) continue
                            imageBitmaps[img.filePath]?.let { bmp ->
                                drawImage(
                                    image = bmp.asImageBitmap(),
                                    dstOffset = IntOffset(img.x.toInt(), img.y.toInt()),
                                    dstSize = IntSize(img.width.toInt(), img.height.toInt())
                                )
                                if (img.id == selectedImageId) {
                                    drawRect(
                                        color = Color(0xFF2563EB),
                                        topLeft = Offset(img.x, img.y),
                                        size = Size(img.width, img.height),
                                        style = ComposeStroke(width = 3.5f / scale.coerceAtLeast(0.5f))
                                    )
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

                        // 7. Live stroke on this page
                        val curTool = activeDrawingTool
                        if (pIdx == drawingTargetPageIndex && curTool != null && livePoints.size >= 2) {
                            when (curTool) {
                                ToolType.LASSO -> {
                                    val path = Path().apply {
                                        moveTo(livePoints.first().x, livePoints.first().y)
                                        for (i in 1 until livePoints.size) lineTo(livePoints[i].x, livePoints[i].y)
                                    }
                                    drawPath(
                                        path = path,
                                        color = Color(0xFF2563EB),
                                        style = ComposeStroke(
                                            width = 2.5f / scale.coerceAtLeast(0.5f),
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                                        )
                                    )
                                }
                                ToolType.TAPE -> {
                                    drawTapeItem(
                                        TapeItem(
                                            startX = livePoints.first().x,
                                            startY = livePoints.first().y,
                                            endX = livePoints.last().x,
                                            endY = livePoints.last().y,
                                            thickness = toolConfig.tapeThickness,
                                            colorArgb = toolConfig.tapeColorArgb,
                                            isRevealed = false
                                        )
                                    )
                                }
                                else -> {
                                    val previewPoints = if (curTool == ToolType.HIGHLIGHTER && toolConfig.highlighterStraightLine) {
                                        listOf(livePoints.first(), livePoints.last())
                                    } else {
                                        livePoints + predictedPoints
                                    }
                                    val liveStroke = Stroke(
                                        toolType = curTool,
                                        colorArgb = if (curTool == ToolType.HIGHLIGHTER) toolConfig.highlighterColorArgb else toolConfig.penColorArgb,
                                        baseWidth = if (curTool == ToolType.HIGHLIGHTER) toolConfig.highlighterWidth else toolConfig.penWidth,
                                        opacity = if (curTool == ToolType.HIGHLIGHTER) toolConfig.highlighterOpacity else toolConfig.penOpacity,
                                        pressureSensitivity = toolConfig.pressureSensitivity,
                                        nibAngleDeg = toolConfig.artNibAngleDeg,
                                        highlighterStyle = toolConfig.highlighterStyle,
                                        isFilledShape = curTool == ToolType.SHAPE && toolConfig.shapeFilled,
                                        fillColorArgb = if (curTool == ToolType.SHAPE && toolConfig.shapeFilled) toolConfig.penColorArgb else null,
                                        borderDashed = curTool == ToolType.SHAPE && toolConfig.shapeBorderDashed,
                                        points = previewPoints
                                    )
                                    drawStrokeOnCanvas(liveStroke, isSelected = false)
                                }
                            }
                        }

                        // 8. Stickers
                        val stickerTextPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            textSize = 26f
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                        for (st in p.stickers) {
                            if (st.layerId !in visibleLayers) continue
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
                                while (tx < rulerCx + rLen / 2f - 10f) {
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
                            }
                        }

                        // 15. Fading Laser Pointer
                        if (pIdx == drawingTargetPageIndex && laserTrail.size >= 2) {
                            for (i in 1 until laserTrail.size) {
                                val frac = i.toFloat() / laserTrail.size
                                drawLine(
                                    color = Color(0xFFEF4444).copy(alpha = frac * 0.9f),
                                    start = Offset(laserTrail[i - 1].x, laserTrail[i - 1].y),
                                    end = Offset(laserTrail[i].x, laserTrail[i].y),
                                    strokeWidth = 3f + frac * 7f,
                                    cap = StrokeCap.Round
                                )
                            }
                        }
                    }
                }
            }
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
                val newScale = (scale * zoomFactor).coerceIn(0.3f, 4.5f)
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
