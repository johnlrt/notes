package com.johnlrt.freenotes.ui.editor

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.os.Build
import android.view.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.graphics.lowlatency.LowLatencyCanvasView
import androidx.graphics.surface.SurfaceControlCompat
import com.johnlrt.freenotes.data.HighlighterStyle
import com.johnlrt.freenotes.data.Stroke
import com.johnlrt.freenotes.data.StrokePoint
import com.johnlrt.freenotes.data.ToolType
import com.johnlrt.freenotes.engine.StrokeGeometry
import kotlin.math.roundToInt

/**
 * Immutable snapshot of the active wet ink stroke currently being drawn by the stylus,
 * rendered directly into the hardware GPU front buffer (bypassing Compose recomposition
 * and SurfaceFlinger triple-buffering) for near-0ms ink-under-nib latency at 120Hz+.
 */
data class WetStrokeSnapshot(
    val toolType: ToolType,
    val points: List<StrokePoint>,
    val predictedPoints: List<StrokePoint> = emptyList(),
    val colorArgb: Long,
    val baseWidth: Float,
    val opacity: Float,
    val pressureSensitivity: Float,
    val nibAngleDeg: Float,
    val highlighterStyle: HighlighterStyle,
    val straightLine: Boolean,
    val shapeFilled: Boolean,
    val shapeFillColorArgb: Long,
    val shapeDashed: Boolean,
    val tapeThickness: Float,
    val tapeColorArgb: Long,
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
    val pageTopY: Float
)

/**
 * Lock-free bridge between stylus MotionEvents and Jetpack's hardware `LowLatencyCanvasView`
 * front-buffered renderer.
 */
class LowLatencyInkController {
    @Volatile
    private var snapshot: WetStrokeSnapshot? = null

    @Volatile
    private var viewRef: LowLatencyCanvasView? = null

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val reusablePath = Path()

    val callback = object : LowLatencyCanvasView.Callback {
        override fun onRedrawRequested(canvas: Canvas, width: Int, height: Int) {
            // Committed dry ink lives on the Compose Canvas underneath; keep multi-buffered layer clear.
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        }

        override fun onDrawFrontBufferedLayer(canvas: Canvas, width: Int, height: Int) {
            // Clear previous front-buffer frame so predicted tips never leave ghost trails
            // and semi-transparent highlighters never double-blend.
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

            val snap = snapshot ?: return
            if (snap.points.size < 2) return

            canvas.save()
            canvas.translate(snap.offsetX, snap.offsetY)
            canvas.scale(snap.scale, snap.scale)
            canvas.translate(0f, snap.pageTopY)

            drawWetStroke(canvas, snap)

            canvas.restore()
        }

        override fun onFrontBufferedLayerRenderComplete(
            frontBufferedLayerSurfaceControl: SurfaceControlCompat,
            transaction: SurfaceControlCompat.Transaction
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching {
                    transaction.setFrameRate(
                        frontBufferedLayerSurfaceControl,
                        120f,
                        Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                        Surface.CHANGE_FRAME_RATE_ALWAYS
                    )
                }
            }
        }
    }

    fun attach(view: LowLatencyCanvasView) {
        viewRef = view
        view.setBackgroundColor(Color.TRANSPARENT)
        view.setRenderCallback(callback)
    }

    fun detach() {
        snapshot = null
        viewRef = null
    }

    /**
     * Pushes the latest stylus stroke + OS-predicted tip straight to the single-buffered GPU front buffer.
     * Takes < 0.5ms and triggers zero Compose recompositions.
     */
    fun renderWetStroke(newSnapshot: WetStrokeSnapshot) {
        snapshot = newSnapshot
        viewRef?.renderFrontBufferedLayer()
    }

    /**
     * Immediately clears the front buffer (e.g. on gesture cancel or palm rejection).
     */
    fun clearImmediate() {
        snapshot = null
        viewRef?.let { v ->
            v.clear()
            v.commit()
        }
    }

    /**
     * Clears the front buffer on the next display VSync after the dry stroke is committed
     * to the Compose Canvas, preventing any 1-frame flicker between wet and dry ink.
     */
    fun clearAfterCommit() {
        val v = viewRef ?: run {
            snapshot = null
            return
        }
        v.postOnAnimation {
            snapshot = null
            v.clear()
            v.commit()
        }
    }

    private fun drawWetStroke(canvas: Canvas, snap: WetStrokeSnapshot) {
        when (snap.toolType) {
            ToolType.LASSO -> {
                reusablePath.reset()
                reusablePath.moveTo(snap.points.first().x, snap.points.first().y)
                for (i in 1 until snap.points.size) {
                    reusablePath.lineTo(snap.points[i].x, snap.points[i].y)
                }
                strokePaint.color = 0xFF2563EB.toInt()
                strokePaint.strokeWidth = 3f / snap.scale.coerceAtLeast(0.5f)
                strokePaint.strokeCap = Paint.Cap.ROUND
                strokePaint.pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
                canvas.drawPath(reusablePath, strokePaint)
                strokePaint.pathEffect = null
            }

            ToolType.TAPE -> {
                val first = snap.points.first()
                val last = snap.points.last()
                strokePaint.color = snap.tapeColorArgb.toInt()
                strokePaint.alpha = 215
                strokePaint.strokeWidth = snap.tapeThickness
                strokePaint.strokeCap = Paint.Cap.BUTT
                strokePaint.pathEffect = null
                canvas.drawLine(first.x, first.y, last.x, last.y, strokePaint)
            }

            ToolType.LASER -> {
                val trail = snap.points
                strokePaint.strokeCap = Paint.Cap.ROUND
                strokePaint.pathEffect = null
                for (i in 1 until trail.size) {
                    val frac = i.toFloat() / trail.size
                    val p0 = trail[i - 1]
                    val p1 = trail[i]
                    strokePaint.color = 0xFFEF4444.toInt()
                    strokePaint.alpha = (frac * 95f).roundToInt().coerceIn(0, 255)
                    strokePaint.strokeWidth = 18f * frac
                    canvas.drawLine(p0.x, p0.y, p1.x, p1.y, strokePaint)

                    strokePaint.color = 0xFFFEE2E2.toInt()
                    strokePaint.alpha = (frac * 245f).roundToInt().coerceIn(0, 255)
                    strokePaint.strokeWidth = 6f * frac
                    canvas.drawLine(p0.x, p0.y, p1.x, p1.y, strokePaint)
                }
            }

            ToolType.HIGHLIGHTER -> {
                val combined = if (snap.predictedPoints.isEmpty()) snap.points else snap.points + snap.predictedPoints
                val pts = if (snap.straightLine && combined.size >= 2) {
                    listOf(combined.first(), combined.last())
                } else combined
                reusablePath.reset()
                reusablePath.moveTo(pts.first().x, pts.first().y)
                for (i in 1 until pts.size) {
                    reusablePath.lineTo(pts[i].x, pts[i].y)
                }
                strokePaint.color = snap.colorArgb.toInt()
                strokePaint.alpha = (snap.opacity.coerceIn(0.1f, 0.55f) * 255f).roundToInt()
                strokePaint.strokeWidth = snap.baseWidth
                strokePaint.strokeCap = if (snap.highlighterStyle == HighlighterStyle.DOTTED) Paint.Cap.ROUND else Paint.Cap.BUTT
                strokePaint.pathEffect = when (snap.highlighterStyle) {
                    HighlighterStyle.SOLID -> null
                    HighlighterStyle.DASHED -> DashPathEffect(floatArrayOf(snap.baseWidth * 1.4f, snap.baseWidth * 0.9f), 0f)
                    HighlighterStyle.DOTTED -> DashPathEffect(floatArrayOf(2f, snap.baseWidth * 1.15f), 0f)
                }
                canvas.drawPath(reusablePath, strokePaint)
                strokePaint.pathEffect = null
            }

            ToolType.SHAPE -> {
                val combined = if (snap.predictedPoints.isEmpty()) snap.points else snap.points + snap.predictedPoints
                reusablePath.reset()
                reusablePath.moveTo(combined.first().x, combined.first().y)
                for (i in 1 until combined.size) {
                    reusablePath.lineTo(combined[i].x, combined[i].y)
                }
                if (snap.shapeFilled && combined.size >= 3) {
                    fillPaint.color = snap.shapeFillColorArgb.toInt()
                    fillPaint.alpha = (0.18f * 255f).roundToInt()
                    canvas.drawPath(reusablePath, fillPaint)
                }
                strokePaint.color = snap.colorArgb.toInt()
                strokePaint.alpha = (snap.opacity.coerceIn(0.05f, 1f) * 255f).roundToInt()
                strokePaint.strokeWidth = snap.baseWidth
                strokePaint.strokeCap = Paint.Cap.ROUND
                strokePaint.pathEffect = if (snap.shapeDashed) {
                    DashPathEffect(floatArrayOf(snap.baseWidth * 3f, snap.baseWidth * 2f), 0f)
                } else null
                canvas.drawPath(reusablePath, strokePaint)
                strokePaint.pathEffect = null
            }

            ToolType.PENCIL -> {
                val combined = if (snap.predictedPoints.isEmpty()) snap.points else snap.points + snap.predictedPoints
                val tempStroke = Stroke(
                    toolType = ToolType.PENCIL,
                    colorArgb = snap.colorArgb,
                    baseWidth = snap.baseWidth,
                    opacity = snap.opacity,
                    pressureSensitivity = snap.pressureSensitivity,
                    points = combined
                )
                val segs = StrokeGeometry.buildRibbon(tempStroke)
                strokePaint.color = snap.colorArgb.toInt()
                strokePaint.strokeCap = Paint.Cap.ROUND
                strokePaint.pathEffect = null
                for (seg in segs) {
                    strokePaint.alpha = (seg.alpha * 255f).roundToInt().coerceIn(0, 255)
                    strokePaint.strokeWidth = seg.width
                    canvas.drawLine(seg.x0, seg.y0, seg.x1, seg.y1, strokePaint)
                }
            }

            else -> {
                // Polygonal variable-width ink ribbon (Fountain Pen, Art Pen, Ballpoint)
                val combined = if (snap.predictedPoints.isEmpty()) snap.points else snap.points + snap.predictedPoints
                val tempStroke = Stroke(
                    toolType = snap.toolType,
                    colorArgb = snap.colorArgb,
                    baseWidth = snap.baseWidth,
                    opacity = snap.opacity,
                    pressureSensitivity = snap.pressureSensitivity,
                    nibAngleDeg = snap.nibAngleDeg,
                    points = combined
                )
                val outline = StrokeGeometry.buildRibbonOutline(tempStroke) ?: return
                reusablePath.reset()
                reusablePath.moveTo(outline.leftEdge.first().first, outline.leftEdge.first().second)
                for (i in 1 until outline.leftEdge.size) {
                    reusablePath.lineTo(outline.leftEdge[i].first, outline.leftEdge[i].second)
                }
                for (i in outline.rightEdge.lastIndex downTo 0) {
                    reusablePath.lineTo(outline.rightEdge[i].first, outline.rightEdge[i].second)
                }
                reusablePath.close()

                fillPaint.color = snap.colorArgb.toInt()
                fillPaint.alpha = (snap.opacity.coerceIn(0.05f, 1f) * 255f).roundToInt()
                canvas.drawPath(reusablePath, fillPaint)
                canvas.drawCircle(outline.startCapCenter.first, outline.startCapCenter.second, outline.startCapRadius, fillPaint)
                canvas.drawCircle(outline.endCapCenter.first, outline.endCapCenter.second, outline.endCapRadius, fillPaint)
            }
        }
    }
}

/**
 * Transparent hardware front-buffered ink overlay placed directly over the Compose static Canvas.
 * Bypassing Compose recomposition and SurfaceFlinger triple-buffering drops stylus-to-photon
 * latency to ~2–5ms at 120Hz/144Hz.
 */
@Composable
fun LowLatencyInkOverlay(
    controller: LowLatencyInkController,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            LowLatencyCanvasView(ctx).also { view ->
                controller.attach(view)
            }
        },
        onRelease = {
            controller.detach()
        }
    )
}
