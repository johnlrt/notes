package com.johnlrt.freenotes.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/**
 * Image import, Rectangular & Freehand Polygon Image Cropping (Freenotes' signature photo cropper),
 * and Lecture Video frame capture utilities.
 */
object ImageCropper {

    suspend fun importImageToLocal(context: Context, uri: Uri): Pair<File, Pair<Int, Int>> =
        withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "media/images").apply { mkdirs() }
            val outFile = File(dir, "img_${UUID.randomUUID()}.png")
            val bmp = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: error("Failed to decode selected image")

            // Scale down very large camera photos to keep tablet memory crisp
            val maxDim = 1600
            val scaled = if (bmp.width > maxDim || bmp.height > maxDim) {
                val ratio = min(maxDim.toFloat() / bmp.width, maxDim.toFloat() / bmp.height)
                Bitmap.createScaledBitmap(
                    bmp,
                    (bmp.width * ratio).toInt().coerceAtLeast(1),
                    (bmp.height * ratio).toInt().coerceAtLeast(1),
                    true
                ).also { if (it !== bmp) bmp.recycle() }
            } else bmp

            FileOutputStream(outFile).use { scaled.compress(Bitmap.CompressFormat.PNG, 92, it) }
            val dims = scaled.width to scaled.height
            scaled.recycle()
            outFile to dims
        }

    /**
     * Crops [sourcePath] using normalized rectangle bounds `[leftFrac, topFrac, rightFrac, bottomFrac]` in 0..1.
     */
    suspend fun cropRectangular(
        context: Context,
        sourcePath: String,
        leftFrac: Float,
        topFrac: Float,
        rightFrac: Float,
        bottomFrac: Float
    ): File = withContext(Dispatchers.IO) {
        val src = BitmapFactory.decodeFile(sourcePath) ?: error("Source image missing")
        val l = (leftFrac.coerceIn(0f, 0.95f) * src.width).toInt()
        val t = (topFrac.coerceIn(0f, 0.95f) * src.height).toInt()
        val r = (rightFrac.coerceIn(leftFrac + 0.05f, 1f) * src.width).toInt()
        val b = (bottomFrac.coerceIn(topFrac + 0.05f, 1f) * src.height).toInt()
        val w = (r - l).coerceAtLeast(8)
        val h = (b - t).coerceAtLeast(8)

        val cropped = Bitmap.createBitmap(src, l, t, w, h)
        src.recycle()

        val dir = File(context.filesDir, "media/images").apply { mkdirs() }
        val outFile = File(dir, "crop_${UUID.randomUUID()}.png")
        FileOutputStream(outFile).use { cropped.compress(Bitmap.CompressFormat.PNG, 95, it) }
        cropped.recycle()
        outFile
    }

    /**
     * Freehand polygon crop: masks [sourcePath] to the normalized polygon points (0..1)
     * and trims tightly to the polygon bounding box with transparent alpha outside!
     */
    suspend fun cropFreehand(
        context: Context,
        sourcePath: String,
        normalizedPolygon: List<Pair<Float, Float>>
    ): File = withContext(Dispatchers.IO) {
        if (normalizedPolygon.size < 3) error("Need at least 3 points for freehand crop")
        val src = BitmapFactory.decodeFile(sourcePath) ?: error("Source image missing")

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = 0f
        var maxY = 0f
        val pxPts = normalizedPolygon.map { (nx, ny) ->
            val px = nx.coerceIn(0f, 1f) * src.width
            val py = ny.coerceIn(0f, 1f) * src.height
            minX = min(minX, px)
            minY = min(minY, py)
            maxX = max(maxX, px)
            maxY = max(maxY, py)
            px to py
        }

        val outW = (maxX - minX).toInt().coerceAtLeast(16)
        val outH = (maxY - minY).toInt().coerceAtLeast(16)
        val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val path = Path().apply {
            moveTo(pxPts.first().first - minX, pxPts.first().second - minY)
            for (i in 1 until pxPts.size) {
                lineTo(pxPts[i].first - minX, pxPts[i].second - minY)
            }
            close()
        }

        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawPath(path, maskPaint)

        val imgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        }
        val srcRect = Rect(minX.toInt(), minY.toInt(), maxX.toInt(), maxY.toInt())
        val dstRect = Rect(0, 0, outW, outH)
        canvas.drawBitmap(src, srcRect, dstRect, imgPaint)
        src.recycle()

        val dir = File(context.filesDir, "media/images").apply { mkdirs() }
        val outFile = File(dir, "freehand_${UUID.randomUUID()}.png")
        FileOutputStream(outFile).use { output.compress(Bitmap.CompressFormat.PNG, 95, it) }
        output.recycle()
        outFile
    }

    /**
     * Captures a frame from a local lecture video URI at [positionMs] directly into an image file
     * so users can drop lecture screenshots onto their notes (Freenotes PiP video workflow).
     */
    suspend fun captureVideoFrame(context: Context, videoUri: Uri, positionMs: Long): Pair<File, Pair<Int, Int>> =
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, videoUri)
                val frame = retriever.getFrameAtTime(
                    positionMs * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                ) ?: error("Could not extract video frame")

                val dir = File(context.filesDir, "media/images").apply { mkdirs() }
                val outFile = File(dir, "video_cap_${UUID.randomUUID()}.png")
                FileOutputStream(outFile).use { frame.compress(Bitmap.CompressFormat.PNG, 92, it) }
                val dims = frame.width to frame.height
                frame.recycle()
                outFile to dims
            } finally {
                runCatching { retriever.release() }
            }
        }
}
