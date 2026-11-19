package com.johnlrt.freenotes.ui.editor

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * Freenotes-style Rectangular & Freehand Lasso Image Cropper dialog.
 */
@Composable
fun ImageCropDialog(
    imagePath: String,
    onCropRect: (Float, Float, Float, Float) -> Unit,
    onCropFreehand: (List<Pair<Float, Float>>) -> Unit,
    onDismiss: () -> Unit
) {
    val bitmap = remember(imagePath) { BitmapFactory.decodeFile(imagePath) }
    var isFreehand by remember { mutableStateOf(true) }
    var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }
    val freehandPts = remember { mutableStateListOf<Offset>() }

    var rectStart by remember { mutableStateOf(Offset(0.1f, 0.1f)) }
    var rectEnd by remember { mutableStateOf(Offset(0.9f, 0.9f)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crop Image (Rect / Freehand)", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = isFreehand,
                        onClick = { isFreehand = true; freehandPts.clear() },
                        label = { Text("Freehand Lasso") }
                    )
                    FilterChip(
                        selected = !isFreehand,
                        onClick = { isFreehand = false },
                        label = { Text("Rectangle") }
                    )
                }

                Text(
                    if (isFreehand) "Draw a closed loop with your stylus/finger around the area to keep."
                    else "Drag across the image to define the rectangular crop.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E293B))
                        .onSizeChanged { canvasSize = it }
                        .pointerInput(isFreehand) {
                            detectDragGestures(
                                onDragStart = { start ->
                                    if (isFreehand) {
                                        freehandPts.clear()
                                        freehandPts.add(start)
                                    } else {
                                        val nx = (start.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                        val ny = (start.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                        rectStart = Offset(nx, ny)
                                        rectEnd = Offset(nx, ny)
                                    }
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val pos = change.position
                                    if (isFreehand) {
                                        freehandPts.add(pos)
                                    } else {
                                        val nx = (pos.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                        val ny = (pos.y / size.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                        rectEnd = Offset(nx, ny)
                                    }
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        bitmap?.let { bmp ->
                            drawImage(
                                image = bmp.asImageBitmap(),
                                dstOffset = IntOffset.Zero,
                                dstSize = IntSize(size.width.toInt(), size.height.toInt())
                            )
                        }
                        if (isFreehand && freehandPts.size >= 2) {
                            val path = Path().apply {
                                moveTo(freehandPts.first().x, freehandPts.first().y)
                                for (i in 1 until freehandPts.size) {
                                    lineTo(freehandPts[i].x, freehandPts[i].y)
                                }
                            }
                            drawPath(
                                path = path,
                                color = Color(0xFF38BDF8),
                                style = Stroke(
                                    width = 4f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                                )
                            )
                        } else if (!isFreehand) {
                            val l = minOf(rectStart.x, rectEnd.x) * size.width
                            val t = minOf(rectStart.y, rectEnd.y) * size.height
                            val r = maxOf(rectStart.x, rectEnd.x) * size.width
                            val b = maxOf(rectStart.y, rectEnd.y) * size.height
                            drawRect(
                                color = Color(0xFF38BDF8).copy(alpha = 0.16f),
                                topLeft = Offset(l, t),
                                size = Size(r - l, b - t)
                            )
                            drawRect(
                                color = Color(0xFF38BDF8),
                                topLeft = Offset(l, t),
                                size = Size(r - l, b - t),
                                style = Stroke(width = 3.5f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isFreehand && freehandPts.size >= 3) {
                        val w = canvasSize.width.coerceAtLeast(1).toFloat()
                        val h = canvasSize.height.coerceAtLeast(1).toFloat()
                        onCropFreehand(freehandPts.map { (it.x / w) to (it.y / h) })
                    } else {
                        val l = minOf(rectStart.x, rectEnd.x)
                        val t = minOf(rectStart.y, rectEnd.y)
                        val r = maxOf(rectStart.x, rectEnd.x)
                        val b = maxOf(rectStart.y, rectEnd.y)
                        onCropRect(l, t, r, b)
                    }
                }
            ) {
                Text("Apply Crop")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
