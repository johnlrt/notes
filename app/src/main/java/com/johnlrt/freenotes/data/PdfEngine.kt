package com.johnlrt.freenotes.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.johnlrt.freenotes.engine.StrokeGeometry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Native Android PDF import (via [PdfRenderer]), annotated PDF export (via [PdfDocument]),
 * single-page PNG export, and native `.freenotes.json` notebook backup/restore.
 */
object PdfEngine {

    private val backupJson = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun importPdfAsNotebook(
        context: Context,
        uri: Uri,
        title: String,
        folderId: String? = null
    ): Notebook = withContext(Dispatchers.IO) {
        val pdfDir = File(context.filesDir, "media/pdfs").apply { mkdirs() }
        val localFile = File(pdfDir, "${UUID.randomUUID()}.pdf")

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(localFile).use { output ->
                input.copyTo(output)
            }
        } ?: error("Unable to open PDF stream")

        val pages = mutableListOf<NotePage>()
        ParcelFileDescriptor.open(localFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                for (i in 0 until renderer.pageCount) {
                    renderer.openPage(i).use { pdfPage ->
                        val orient = if (pdfPage.width > pdfPage.height) {
                            PageOrientation.LANDSCAPE
                        } else {
                            PageOrientation.PORTRAIT
                        }
                        pages.add(
                            NotePage(
                                template = PaperTemplate.BLANK,
                                paperColor = PaperColor.WHITE,
                                orientation = orient,
                                pdfSourcePath = localFile.absolutePath,
                                pdfPageIndex = i
                            )
                        )
                    }
                }
            }
        }

        Notebook(
            title = title.ifBlank { "Imported PDF" },
            folderId = folderId,
            coverColorArgb = 0xFF0D9488,
            coverAccentArgb = 0xFF2DD4BF,
            pages = pages.ifEmpty { listOf(NotePage()) }
        )
    }

    suspend fun renderPdfPageBitmap(
        pdfPath: String,
        pageIndex: Int,
        targetWidth: Int = 1240
    ): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(pdfPath)
        if (!file.exists()) return@withContext null
        runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (pageIndex !in 0 until renderer.pageCount) return@withContext null
                    renderer.openPage(pageIndex).use { page ->
                        val scale = targetWidth.toFloat() / page.width.coerceAtLeast(1)
                        val outW = targetWidth
                        val outH = (page.height * scale).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                }
            }
        }.getOrNull()
    }

    suspend fun exportNotebookToPdf(context: Context, notebook: Notebook): File =
        withContext(Dispatchers.IO) {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val safeName = notebook.title.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "note" }
            val outFile = File(exportDir, "${safeName}_${System.currentTimeMillis()}.pdf")

            val document = PdfDocument()
            try {
                notebook.pages.forEachIndexed { idx, page ->
                    val w = page.orientation.widthPx.toInt()
                    val h = page.orientation.heightPx.toInt()
                    val pageInfo = PdfDocument.PageInfo.Builder(w, h, idx + 1).create()
                    val pdfPage = document.startPage(pageInfo)
                    drawPageToAndroidCanvas(pdfPage.canvas, page)
                    document.finishPage(pdfPage)
                }
                FileOutputStream(outFile).use { document.writeTo(it) }
            } finally {
                document.close()
            }
            outFile
        }

    suspend fun exportPageToPng(context: Context, title: String, page: NotePage): File =
        withContext(Dispatchers.IO) {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val safeName = title.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "page" }
            val outFile = File(exportDir, "${safeName}_${System.currentTimeMillis()}.png")

            val w = page.orientation.widthPx.toInt()
            val h = page.orientation.heightPx.toInt()
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawPageToAndroidCanvas(canvas, page)
            FileOutputStream(outFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 95, it) }
            bmp.recycle()
            outFile
        }

    suspend fun exportNotebookBackup(context: Context, notebook: Notebook): File =
        withContext(Dispatchers.IO) {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val safeName = notebook.title.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "backup" }
            val outFile = File(exportDir, "${safeName}.freenotes.json")
            outFile.writeText(backupJson.encodeToString(notebook))
            outFile
        }

    suspend fun importNotebookBackup(context: Context, uri: Uri): Notebook =
        withContext(Dispatchers.IO) {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("Could not read backup file")
            val parsed = backupJson.decodeFromString<Notebook>(text)
            parsed.copy(
                id = UUID.randomUUID().toString(),
                updatedAtMs = System.currentTimeMillis()
            )
        }

    private suspend fun drawPageToAndroidCanvas(canvas: Canvas, page: NotePage) {
        val w = page.orientation.widthPx
        val h = page.orientation.heightPx

        canvas.drawColor(page.paperColor.argb.toInt())

        if (page.pdfSourcePath != null && page.pdfPageIndex >= 0) {
            val bmp = renderPdfPageBitmap(page.pdfSourcePath, page.pdfPageIndex, w.toInt())
            if (bmp != null) {
                canvas.drawBitmap(bmp, 0f, 0f, null)
                bmp.recycle()
            }
        }

        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = page.paperColor.gridArgb.toInt()
            strokeWidth = 1.5f
        }
        when (page.template) {
            PaperTemplate.RULED, PaperTemplate.WIDE_RULED -> {
                val step = if (page.template == PaperTemplate.WIDE_RULED) 68f else 48f
                var y = 140f
                while (y < h - 60f) {
                    canvas.drawLine(64f, y, w - 64f, y, gridPaint)
                    y += step
                }
            }
            PaperTemplate.GRID -> {
                val step = 44f
                var x = step
                while (x < w) { canvas.drawLine(x, 0f, x, h, gridPaint); x += step }
                var y = step
                while (y < h) { canvas.drawLine(0f, y, w, y, gridPaint); y += step }
            }
            PaperTemplate.DOTTED -> {
                val step = 44f
                var x = step
                while (x < w) {
                    var y = step
                    while (y < h) { canvas.drawCircle(x, y, 2.2f, gridPaint); y += step }
                    x += step
                }
            }
            PaperTemplate.CORNELL -> {
                canvas.drawLine(0f, 160f, w, 160f, gridPaint)
                canvas.drawLine(300f, 160f, 300f, h - 220f, gridPaint)
                canvas.drawLine(0f, h - 220f, w, h - 220f, gridPaint)
                var y = 212f
                while (y < h - 220f) { canvas.drawLine(320f, y, w - 48f, y, gridPaint); y += 52f }
            }
            else -> Unit
        }

        val visibleLayerIds = page.layers.filter { it.isVisible }.map { it.id }.toSet()

        // Images
        for (img in page.images.filter { it.layerId in visibleLayerIds }) {
            val bmp = BitmapFactory.decodeFile(img.filePath) ?: continue
            canvas.save()
            if (img.rotationDeg != 0f) {
                canvas.rotate(img.rotationDeg, img.x + img.width * 0.5f, img.y + img.height * 0.5f)
            }
            canvas.drawBitmap(bmp, null, RectF(img.x, img.y, img.x + img.width, img.y + img.height), null)
            canvas.restore()
            bmp.recycle()
        }

        // Behind-ink Highlighters
        for (stroke in page.strokes.filter { it.layerId in visibleLayerIds && it.toolType == ToolType.HIGHLIGHTER }) {
            drawStroke(canvas, stroke)
        }
        // Ink strokes
        for (stroke in page.strokes.filter { it.layerId in visibleLayerIds && it.toolType != ToolType.HIGHLIGHTER }) {
            drawStroke(canvas, stroke)
        }

        // Stickers
        val stickerBg = Paint(Paint.ANTI_ALIAS_FLAG)
        val stickerText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        for (st in page.stickers.filter { it.layerId in visibleLayerIds }) {
            val rect = RectF(st.x, st.y, st.x + st.width, st.y + st.height)
            canvas.save()
            if (st.rotationDeg != 0f) {
                canvas.rotate(st.rotationDeg, rect.centerX(), rect.centerY())
            }
            stickerBg.style = Paint.Style.FILL
            stickerBg.color = st.bgColorArgb.toInt()
            canvas.drawRoundRect(rect, 18f, 18f, stickerBg)
            stickerBg.style = Paint.Style.STROKE
            stickerBg.strokeWidth = 3f
            stickerBg.color = st.borderColorArgb.toInt()
            canvas.drawRoundRect(rect, 18f, 18f, stickerBg)
            stickerText.color = st.textColorArgb.toInt()
            val content = listOf(st.emoji, st.label).filter { it.isNotBlank() }.joinToString(" ")
            canvas.drawText(content, rect.centerX(), rect.centerY() + 9f, stickerText)
            canvas.restore()
        }

        // Text boxes
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (box in page.textBoxes.filter { it.layerId in visibleLayerIds }) {
            textPaint.color = box.colorArgb.toInt()
            textPaint.textSize = box.fontSizeSp * 1.4f
            textPaint.typeface = if (box.isBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            var curY = box.y + textPaint.textSize
            box.text.split("\n").forEach { line ->
                canvas.drawText(line, box.x, curY, textPaint)
                curY += textPaint.textSize * 1.35f
            }
        }

        // Active Recall Tapes
        val tapePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.BUTT
        }
        for (tape in page.tapes.filter { it.layerId in visibleLayerIds }) {
            if (!tape.isRevealed) {
                tapePaint.color = tape.colorArgb.toInt()
                tapePaint.alpha = 245
                tapePaint.strokeWidth = tape.thickness
                canvas.drawLine(tape.startX, tape.startY, tape.endX, tape.endY, tapePaint)
            }
        }
    }

    private fun drawStroke(canvas: Canvas, stroke: Stroke) {
        if (stroke.points.size < 2) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = stroke.colorArgb.toInt()
            style = Paint.Style.STROKE
            strokeCap = if (stroke.toolType == ToolType.HIGHLIGHTER) Paint.Cap.SQUARE else Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        if (stroke.toolType == ToolType.HIGHLIGHTER || stroke.toolType == ToolType.SHAPE) {
            val path = Path().apply {
                moveTo(stroke.points.first().x, stroke.points.first().y)
                for (i in 1 until stroke.points.size) {
                    lineTo(stroke.points[i].x, stroke.points[i].y)
                }
            }
            if (stroke.toolType == ToolType.SHAPE && stroke.isFilledShape) {
                val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = (stroke.fillColorArgb ?: stroke.colorArgb).toInt()
                    alpha = (stroke.fillAlpha * 255).toInt()
                    style = Paint.Style.FILL
                }
                canvas.drawPath(path, fillPaint)
            }
            paint.alpha = if (stroke.toolType == ToolType.HIGHLIGHTER) {
                (stroke.opacity.coerceIn(0.1f, 0.55f) * 255).toInt()
            } else {
                (stroke.opacity * 255).toInt()
            }
            paint.strokeWidth = stroke.baseWidth
            when {
                stroke.toolType == ToolType.HIGHLIGHTER && stroke.highlighterStyle == HighlighterStyle.DASHED ->
                    paint.pathEffect = DashPathEffect(floatArrayOf(28f, 16f), 0f)
                stroke.toolType == ToolType.HIGHLIGHTER && stroke.highlighterStyle == HighlighterStyle.DOTTED -> {
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.pathEffect = DashPathEffect(floatArrayOf(2f, stroke.baseWidth * 1.4f), 0f)
                }
                stroke.toolType == ToolType.SHAPE && stroke.borderDashed ->
                    paint.pathEffect = DashPathEffect(floatArrayOf(18f, 12f), 0f)
            }
            canvas.drawPath(path, paint)
            return
        }

        val ribbon = StrokeGeometry.buildRibbonOutline(stroke)
        if (ribbon != null && stroke.toolType != ToolType.PENCIL) {
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
            paint.style = Paint.Style.FILL
            paint.alpha = (stroke.opacity * 255).toInt()
            canvas.drawPath(path, paint)
            canvas.drawCircle(ribbon.startCapCenter.first, ribbon.startCapCenter.second, ribbon.startCapRadius, paint)
            canvas.drawCircle(ribbon.endCapCenter.first, ribbon.endCapCenter.second, ribbon.endCapRadius, paint)
            return
        }

        val segments = StrokeGeometry.buildSegments(stroke)
        for (seg in segments) {
            paint.strokeWidth = seg.width
            paint.alpha = (seg.alpha * 255).toInt()
            canvas.drawLine(seg.x0, seg.y0, seg.x1, seg.y1, paint)
        }
    }
}
