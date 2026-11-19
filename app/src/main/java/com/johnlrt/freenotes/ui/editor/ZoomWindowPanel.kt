package com.johnlrt.freenotes.ui.editor

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.johnlrt.freenotes.data.NotePage
import com.johnlrt.freenotes.data.Stroke
import com.johnlrt.freenotes.data.StrokePoint
import com.johnlrt.freenotes.data.ToolType
import com.johnlrt.freenotes.engine.OneEuroFilter2D
import com.johnlrt.freenotes.engine.StrokeGeometry
import com.johnlrt.freenotes.ui.ToolConfig

/**
 * Freenotes' signature **Zoom Window (Magnifier Writing Box)** docked at the bottom of the tablet.
 *
 * Magnifies a target box [zoomRect] on the page so the user can write neatly with a stylus,
 * with a green **Auto-Advance** margin on the right edge that automatically shifts the box
 * forward when a stroke ends inside it, plus `←`, `→`, and `↵` (next line) controls.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ZoomWindowPanel(
    page: NotePage,
    zoomRect: Rect,
    toolConfig: ToolConfig,
    onAdvanceRight: () -> Unit,
    onStepLeft: () -> Unit,
    onNewLine: () -> Unit,
    onCommitStroke: (List<StrokePoint>, ToolType?) -> Unit,
    onClose: () -> Unit
) {
    var boxSize by remember { mutableStateOf(IntSize(1, 1)) }
    val livePoints = remember { mutableStateListOf<StrokePoint>() }
    var startTimeMs by remember { mutableStateOf(0L) }
    val oneEuro = remember { OneEuroFilter2D() }

    LaunchedEffect(toolConfig.stability) {
        oneEuro.setStability(toolConfig.stability)
    }

    val scaleX = if (zoomRect.width > 1f) boxSize.width / zoomRect.width else 1f
    val scaleY = if (zoomRect.height > 1f) boxSize.height / zoomRect.height else 1f
    val zoomScale = minOf(scaleX, scaleY).coerceAtLeast(0.5f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Compact Zoom Window Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Zoom Writing Box  •  Auto-Advance Active",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onStepLeft, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Move Left", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onAdvanceRight, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Move Right", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onNewLine, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardReturn, contentDescription = "Next Line", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close Zoom Box", modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Magnified Writing Surface
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(6.dp)
                    .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                    .onSizeChanged { boxSize = it }
                    .pointerInteropFilter { event ->
                        val pageX = zoomRect.left + event.x / zoomScale
                        val pageY = zoomRect.top + event.y / zoomScale

                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                startTimeMs = event.eventTime
                                oneEuro.reset()
                                livePoints.clear()
                                val (fx, fy) = oneEuro.filter(pageX, pageY, event.eventTime)
                                livePoints.add(StrokePoint(fx, fy, event.pressure.coerceIn(0.05f, 1f), 0L))
                            }
                            MotionEvent.ACTION_MOVE -> {
                                for (h in 0 until event.historySize) {
                                    val hx = zoomRect.left + event.getHistoricalX(h) / zoomScale
                                    val hy = zoomRect.top + event.getHistoricalY(h) / zoomScale
                                    val ht = event.getHistoricalEventTime(h)
                                    val (fx, fy) = oneEuro.filter(hx, hy, ht)
                                    livePoints.add(
                                        StrokePoint(fx, fy, event.getHistoricalPressure(h).coerceIn(0.05f, 1f), (ht - startTimeMs).coerceAtLeast(0L))
                                    )
                                }
                                val (fx, fy) = oneEuro.filter(pageX, pageY, event.eventTime)
                                livePoints.add(
                                    StrokePoint(fx, fy, event.pressure.coerceIn(0.05f, 1f), (event.eventTime - startTimeMs).coerceAtLeast(0L))
                                )
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                if (livePoints.size >= 2) {
                                    val endedInAdvanceZone = event.x > boxSize.width * 0.78f
                                    onCommitStroke(livePoints.toList(), null)
                                    if (endedInAdvanceZone) {
                                        onAdvanceRight()
                                    }
                                }
                                livePoints.clear()
                            }
                        }
                        true
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Paper background
                    drawRect(Color(page.paperColor.argb))

                    // Auto-advance green margin zone (right 22% of box)
                    drawRect(
                        color = Color(0xFF10B981).copy(alpha = 0.10f),
                        topLeft = Offset(size.width * 0.78f, 0f),
                        size = Size(size.width * 0.22f, size.height)
                    )

                    withTransform({
                        scale(scaleX = zoomScale, scaleY = zoomScale, pivot = Offset.Zero)
                        translate(left = -zoomRect.left, top = -zoomRect.top)
                    }) {
                        // Draw existing strokes visible in this magnified region
                        val visibleLayers = page.layers.filter { it.isVisible }.map { it.id }.toSet()
                        for (stroke in page.strokes) {
                            if (stroke.layerId !in visibleLayers) continue
                            val segs = StrokeGeometry.buildSegments(stroke)
                            val c = Color(stroke.colorArgb)
                            for (seg in segs) {
                                drawLine(
                                    color = c.copy(alpha = seg.alpha),
                                    start = Offset(seg.x0, seg.y0),
                                    end = Offset(seg.x1, seg.y1),
                                    strokeWidth = seg.width,
                                    cap = StrokeCap.Round
                                )
                            }
                        }

                        // Draw live stroke
                        if (livePoints.size >= 2) {
                            val live = Stroke(
                                toolType = toolConfig.activeTool,
                                colorArgb = toolConfig.activeColorArgb,
                                baseWidth = toolConfig.activeWidth,
                                opacity = if (toolConfig.activeTool == ToolType.HIGHLIGHTER) toolConfig.highlighterOpacity else toolConfig.penOpacity,
                                pressureSensitivity = toolConfig.pressureSensitivity,
                                points = livePoints.toList()
                            )
                            val segs = StrokeGeometry.buildSegments(live)
                            val c = Color(live.colorArgb)
                            for (seg in segs) {
                                drawLine(
                                    color = c.copy(alpha = seg.alpha),
                                    start = Offset(seg.x0, seg.y0),
                                    end = Offset(seg.x1, seg.y1),
                                    strokeWidth = seg.width,
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
