package com.johnlrt.freenotes.ui.editor

import android.graphics.Bitmap
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as ComposeStroke
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
import kotlin.math.max
import kotlin.math.min

/**
 * Low-latency tablet handwriting canvas supporting:
 * - Raw [MotionEvent] historical batching (120Hz/240Hz stylus fidelity)
 * - 1€ One-Euro stabilization (matching Freenotes `optparam1130_best.cfg`)
 * - Speculative tip look-ahead via [StylusPredictor]
 * - Hardware palm rejection (Stylus-only mode lets fingers pan/zoom smoothly)
 * - S Pen / Stylus side-button (`BUTTON_STYLUS_PRIMARY`) temporary eraser shortcut
 * - Behind-ink highlighters (solid/dashed/dotted + straight-line snap)
 * - Active Recall Tape strips (tap to toggle cover/reveal)
 * - Freehand Lasso selection & drag-to-move
 * - Procedural paper templates + native PDF backdrop rendering
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HandwritingCanvas(
    page: NotePage,
    toolConfig: ToolConfig,
    stylusOnlyMode: Boolean,
    zoomLocked: Boolean,
    selectedStrokeIds: Set<String>,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    onCommitStroke: (List<StrokePoint>, ToolType?) -> Unit,
    onEraseAt: (Float, Float, Boolean) -> Unit,
    onSelectLasso: (List<StrokePoint>) -> Unit,
    onDragSelection: (Float, Float, Boolean) -> Unit,
    onTapTape: (Float, Float) -> Boolean,
    onRequestTextEdit: (Float, Float, String?, String) -> Unit
) {
    val pageW = page.orientation.widthPx
    val pageH = page.orientation.heightPx

    var viewportSize by remember { mutableStateOf(IntSize(1, 1)) }
    var scale by remember(page.id) { mutableFloatStateOf(1f) }
    var offsetX by remember(page.id) { mutableFloatStateOf(0f) }
    var offsetY by remember(page.id) { mutableFloatStateOf(0f) }
    var initializedFit by remember(page.id) { mutableStateOf(false) }

    // Fit page nicely into tablet viewport on first layout
    LaunchedEffect(viewportSize, page.id) {
        if (!initializedFit && viewportSize.width > 10 && viewportSize.height > 10) {
            val fitScale = min(
                (viewportSize.width * 0.92f) / pageW,
                (viewportSize.height * 0.92f) / pageH
            ).coerceIn(0.35f, 2.5f)
            scale = fitScale
            offsetX = (viewportSize.width - pageW * fitScale) / 2f
            offsetY = (viewportSize.height - pageH * fitScale) / 2f
            initializedFit = true
        }
    }

    // Load PDF backdrop bitmap if this page came from an imported PDF
    var pdfBitmap by remember(page.pdfSourcePath, page.pdfPageIndex) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(page.pdfSourcePath, page.pdfPageIndex) {
        val path = page.pdfSourcePath
        pdfBitmap = if (path != null && page.pdfPageIndex >= 0) {
            PdfEngine.renderPdfPageBitmap(path, page.pdfPageIndex, pageW.toInt())
        } else null
    }

    // Live in-progress stroke state
    val livePoints = remember { mutableStateListOf<StrokePoint>() }
    val predictedPoints = remember { mutableStateListOf<StrokePoint>() }
    val laserTrail = remember { mutableStateListOf<StrokePoint>() }
    var activeDrawingTool by remember { mutableStateOf<ToolType?>(null) }
    var strokeStartTimeMs by remember { mutableStateOf(0L) }
    var eraserHasHitInGesture by remember { mutableStateOf(false) }
    var isDraggingLassoSelection by remember { mutableStateOf(false) }
    var lastDragPageX by remember { mutableFloatStateOf(0f) }
    var lastDragPageY by remember { mutableFloatStateOf(0f) }
    var dragUndoRecorded by remember { mutableStateOf(false) }

    // Pan/zoom gesture state
    var isPanZooming by remember { mutableStateOf(false) }
    var prevPinchDist by remember { mutableFloatStateOf(0f) }
    var prevMidX by remember { mutableFloatStateOf(0f) }
    var prevMidY by remember { mutableFloatStateOf(0f) }

    val oneEuro = remember { OneEuroFilter2D() }
    val predictor = remember { StylusPredictor() }

    LaunchedEffect(toolConfig.stability) {
        oneEuro.setStability(toolConfig.stability)
    }

    fun screenToPage(sx: Float, sy: Float): Pair<Float, Float> {
        return ((sx - offsetX) / scale) to ((sy - offsetY) / scale)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFE5E9F0))
            .onSizeChanged { viewportSize = it }
            .pointerInteropFilter { event ->
                if (readOnly) {
                    // Allow pan/zoom only in read-only split pane
                    handlePanZoomEvent(
                        event = event,
                        zoomLocked = zoomLocked,
                        isPanZooming = isPanZooming,
                        setPanZooming = { isPanZooming = it },
                        prevPinchDist = prevPinchDist,
                        setPrevPinchDist = { prevPinchDist = it },
                        prevMidX = prevMidX,
                        prevMidY = prevMidY,
                        setPrevMid = { mx, my -> prevMidX = mx; prevMidY = my },
                        scale = scale,
                        offsetX = offsetX,
                        offsetY = offsetY,
                        onUpdateTransform = { ns, nx, ny -> scale = ns; offsetX = nx; offsetY = ny }
                    )
                    return@pointerInteropFilter true
                }

                val pointerCount = event.pointerCount
                val primaryToolType = event.getToolType(0)
                val isStylus = primaryToolType == MotionEvent.TOOL_TYPE_STYLUS ||
                    primaryToolType == MotionEvent.TOOL_TYPE_ERASER

                // Multi-touch (2+ fingers) or finger-when-stylus-only -> Pan & Zoom!
                if (pointerCount >= 2 || (stylusOnlyMode && !isStylus)) {
                    livePoints.clear()
                    predictedPoints.clear()
                    activeDrawingTool = null
                    handlePanZoomEvent(
                        event = event,
                        zoomLocked = zoomLocked,
                        isPanZooming = isPanZooming,
                        setPanZooming = { isPanZooming = it },
                        prevPinchDist = prevPinchDist,
                        setPrevPinchDist = { prevPinchDist = it },
                        prevMidX = prevMidX,
                        prevMidY = prevMidY,
                        setPrevMid = { mx, my -> prevMidX = mx; prevMidY = my },
                        scale = scale,
                        offsetX = offsetX,
                        offsetY = offsetY,
                        onUpdateTransform = { ns, nx, ny -> scale = ns; offsetX = nx; offsetY = ny }
                    )
                    return@pointerInteropFilter true
                }

                if (isPanZooming && event.actionMasked == MotionEvent.ACTION_UP) {
                    isPanZooming = false
                    return@pointerInteropFilter true
                }

                // S Pen / Stylus side button or eraser tail -> temporary eraser!
                val stylusButtonEraser =
                    primaryToolType == MotionEvent.TOOL_TYPE_ERASER ||
                        (event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0

                val effectiveTool = if (stylusButtonEraser) ToolType.ERASER else toolConfig.activeTool
                val (px, py) = screenToPage(event.x, event.y)

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        // First check if user tapped an Active Recall Tape strip (with non-eraser/non-tape tools)
                        if (effectiveTool != ToolType.ERASER && effectiveTool != ToolType.TAPE &&
                            effectiveTool != ToolType.LASSO
                        ) {
                            if (onTapTape(px, py)) {
                                return@pointerInteropFilter true
                            }
                        }

                        if (effectiveTool == ToolType.TEXT) {
                            val existing = page.textBoxes.lastOrNull { HitTestEngine.hitTestTextBox(it, px, py) }
                            onRequestTextEdit(
                                existing?.x ?: px,
                                existing?.y ?: py,
                                existing?.id,
                                existing?.text ?: ""
                            )
                            return@pointerInteropFilter true
                        }

                        if (effectiveTool == ToolType.LASSO && selectedStrokeIds.isNotEmpty()) {
                            val selStrokes = page.strokes.filter { it.id in selectedStrokeIds }
                            val box = HitTestEngine.boundingBoxOfStrokes(selStrokes)
                            if (box != null && px in (box[0] - 16f)..(box[2] + 16f) &&
                                py in (box[1] - 16f)..(box[3] + 16f)
                            ) {
                                isDraggingLassoSelection = true
                                dragUndoRecorded = false
                                lastDragPageX = px
                                lastDragPageY = py
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
                            onEraseAt(px, py, true)
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
                            val dx = px - lastDragPageX
                            val dy = py - lastDragPageY
                            lastDragPageX = px
                            lastDragPageY = py
                            onDragSelection(dx, dy, !dragUndoRecorded)
                            dragUndoRecorded = true
                            return@pointerInteropFilter true
                        }

                        val tool = activeDrawingTool ?: return@pointerInteropFilter true

                        if (tool == ToolType.ERASER) {
                            for (h in 0 until event.historySize) {
                                val (hx, hy) = screenToPage(event.getHistoricalX(h), event.getHistoricalY(h))
                                onEraseAt(hx, hy, !eraserHasHitInGesture)
                                eraserHasHitInGesture = true
                            }
                            onEraseAt(px, py, !eraserHasHitInGesture)
                            eraserHasHitInGesture = true
                            return@pointerInteropFilter true
                        }

                        // Consume all batched 120Hz/240Hz historical stylus samples
                        for (h in 0 until event.historySize) {
                            val (hx, hy) = screenToPage(event.getHistoricalX(h), event.getHistoricalY(h))
                            val ht = event.getHistoricalEventTime(h)
                            val hp = event.getHistoricalPressure(h).coerceIn(0.05f, 1f)
                            val (fx, fy) = oneEuro.filter(hx, hy, ht)
                            val pt = StrokePoint(fx, fy, hp, (ht - strokeStartTimeMs).coerceAtLeast(0L))
                            if (tool == ToolType.LASER) {
                                laserTrail.add(pt)
                                if (laserTrail.size > 36) laserTrail.removeAt(0)
                            } else {
                                livePoints.add(pt)
                                predictor.record(pt)
                            }
                        }

                        val (fx, fy) = oneEuro.filter(px, py, event.eventTime)
                        val pt = StrokePoint(
                            fx,
                            fy,
                            event.pressure.coerceIn(0.05f, 1f),
                            (event.eventTime - strokeStartTimeMs).coerceAtLeast(0L)
                        )
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
                            onCommitStroke(livePoints.toList(), tool)
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
                // Page drop shadow
                drawRect(
                    color = Color.Black.copy(alpha = 0.12f),
                    topLeft = Offset(6f, 8f),
                    size = Size(pageW, pageH)
                )

                // 1. Paper surface
                drawRect(
                    color = Color(page.paperColor.argb),
                    topLeft = Offset.Zero,
                    size = Size(pageW, pageH)
                )

                // 2. PDF backdrop if imported
                pdfBitmap?.let { bmp ->
                    drawImage(
                        image = bmp.asImageBitmap(),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(pageW.toInt(), pageH.toInt())
                    )
                }

                // 3. Procedural paper template
                drawPaperTemplate(page.template, Color(page.paperColor.gridArgb), pageW, pageH)

                val visibleLayers = page.layers.filter { it.isVisible }.map { it.id }.toSet()

                // 4. Behind-ink Highlighters
                for (stroke in page.strokes) {
                    if (stroke.layerId in visibleLayers && stroke.toolType == ToolType.HIGHLIGHTER) {
                        drawStrokeOnCanvas(stroke, isSelected = stroke.id in selectedStrokeIds)
                    }
                }

                // 5. Ink Strokes (Fountain, Ballpoint, Pencil, Art Pen, Shapes)
                for (stroke in page.strokes) {
                    if (stroke.layerId in visibleLayers && stroke.toolType != ToolType.HIGHLIGHTER) {
                        drawStrokeOnCanvas(stroke, isSelected = stroke.id in selectedStrokeIds)
                    }
                }

                // 6. Live in-progress stroke + predicted tip points
                val curTool = activeDrawingTool
                if (curTool != null && livePoints.size >= 2) {
                    when (curTool) {
                        ToolType.LASSO -> {
                            val path = Path().apply {
                                moveTo(livePoints.first().x, livePoints.first().y)
                                for (i in 1 until livePoints.size) lineTo(livePoints[i].x, livePoints[i].y)
                            }
                            drawPath(
                                path = path,
                                color = Color(0xFF4F46E5),
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
                                points = previewPoints
                            )
                            drawStrokeOnCanvas(liveStroke, isSelected = false)
                        }
                    }
                }

                // 7. Text Boxes
                val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                for (box in page.textBoxes) {
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

                // 8. Active Recall Tapes
                for (tape in page.tapes) {
                    if (tape.layerId in visibleLayers) {
                        drawTapeItem(tape)
                    }
                }

                // 9. Lasso selection bounding box
                if (selectedStrokeIds.isNotEmpty()) {
                    val selStrokes = page.strokes.filter { it.id in selectedStrokeIds }
                    HitTestEngine.boundingBoxOfStrokes(selStrokes)?.let { b ->
                        val pad = 14f
                        drawRect(
                            color = Color(0xFF4F46E5).copy(alpha = 0.08f),
                            topLeft = Offset(b[0] - pad, b[1] - pad),
                            size = Size(b[2] - b[0] + pad * 2, b[3] - b[1] + pad * 2)
                        )
                        drawRect(
                            color = Color(0xFF4F46E5),
                            topLeft = Offset(b[0] - pad, b[1] - pad),
                            size = Size(b[2] - b[0] + pad * 2, b[3] - b[1] + pad * 2),
                            style = ComposeStroke(
                                width = 2.5f / scale.coerceAtLeast(0.5f),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                            )
                        )
                    }
                }

                // 10. Fading Laser Pointer trail
                if (laserTrail.size >= 2) {
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
            while (x < w) {
                drawLine(lineColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1.2f)
                x += step
            }
            var y = step
            while (y < h) {
                drawLine(lineColor, Offset(0f, y), Offset(w, y), strokeWidth = 1.2f)
                y += step
            }
        }
        PaperTemplate.DOTTED -> {
            val step = 44f
            var x = step
            while (x < w) {
                var y = step
                while (y < h) {
                    drawCircle(lineColor, radius = 2.2f, center = Offset(x, y))
                    y += step
                }
                x += step
            }
        }
        PaperTemplate.CORNELL -> {
            val accent = lineColor.copy(alpha = 0.9f)
            drawLine(accent, Offset(0f, 160f), Offset(w, 160f), strokeWidth = 2.5f)
            drawLine(accent, Offset(300f, 160f), Offset(300f, h - 220f), strokeWidth = 2.5f)
            drawLine(accent, Offset(0f, h - 220f), Offset(w, h - 220f), strokeWidth = 2.5f)
            var y = 212f
            while (y < h - 220f) {
                drawLine(lineColor, Offset(320f, y), Offset(w - 48f, y), strokeWidth = 1.3f)
                y += 52f
            }
        }
        PaperTemplate.PLANNER -> {
            val step = 64f
            var y = 160f
            while (y < h - 80f) {
                drawRect(
                    color = lineColor,
                    topLeft = Offset(72f, y - 22f),
                    size = Size(24f, 24f),
                    style = ComposeStroke(width = 2f)
                )
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

    if (stroke.toolType == ToolType.HIGHLIGHTER) {
        val path = Path().apply {
            moveTo(stroke.points.first().x, stroke.points.first().y)
            for (i in 1 until stroke.points.size) {
                lineTo(stroke.points[i].x, stroke.points[i].y)
            }
        }
        val pathEffect = when (stroke.highlighterStyle) {
            HighlighterStyle.SOLID -> null
            HighlighterStyle.DASHED -> PathEffect.dashPathEffect(floatArrayOf(28f, 16f), 0f)
            HighlighterStyle.DOTTED -> PathEffect.dashPathEffect(floatArrayOf(2f, stroke.baseWidth * 1.4f), 0f)
        }
        val cap = if (stroke.highlighterStyle == HighlighterStyle.DOTTED) StrokeCap.Round else StrokeCap.Square
        drawPath(
            path = path,
            color = baseColor.copy(alpha = stroke.opacity.coerceIn(0.1f, 0.55f)),
            style = ComposeStroke(
                width = stroke.baseWidth,
                cap = cap,
                join = StrokeJoin.Round,
                pathEffect = pathEffect
            )
        )
        return
    }

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
        // Subtle tactile center stitch line (Freenotes Active Recall tape look)
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
