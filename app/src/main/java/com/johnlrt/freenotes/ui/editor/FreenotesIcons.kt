package com.johnlrt.freenotes.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.johnlrt.freenotes.data.ToolType

/**
 * Custom realistic Freenotes-style vector tool icons that tint their nib/tip
 * with the user's active ink, highlighter, or tape color.
 */
@Composable
fun FreenotesToolIcon(
    tool: ToolType,
    activeColor: Color,
    isSelected: Boolean,
    size: Dp = 24.dp
) {
    val bodyColor = if (isSelected) Color(0xFF1E293B) else Color(0xFF64748B)
    val inkTint = if (activeColor == Color.White) Color(0xFF94A3B8) else activeColor

    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        when (tool) {
            ToolType.FOUNTAIN_PEN -> {
                // Classic tapered fountain nib
                val nib = Path().apply {
                    moveTo(w * 0.5f, h * 0.08f)
                    lineTo(w * 0.72f, h * 0.48f)
                    lineTo(w * 0.64f, h * 0.68f)
                    lineTo(w * 0.36f, h * 0.68f)
                    lineTo(w * 0.28f, h * 0.48f)
                    close()
                }
                drawPath(nib, color = inkTint)
                drawLine(Color.White, Offset(w * 0.5f, h * 0.14f), Offset(w * 0.5f, h * 0.44f), strokeWidth = 1.6f)
                drawCircle(Color.White, radius = w * 0.055f, center = Offset(w * 0.5f, h * 0.44f))
                drawRoundRect(
                    color = bodyColor,
                    topLeft = Offset(w * 0.32f, h * 0.68f),
                    size = Size(w * 0.36f, h * 0.26f),
                    cornerRadius = CornerRadius(4f, 4f)
                )
            }

            ToolType.BALLPOINT -> {
                // Sleek conical ballpoint tip + barrel
                val tip = Path().apply {
                    moveTo(w * 0.5f, h * 0.10f)
                    lineTo(w * 0.66f, h * 0.42f)
                    lineTo(w * 0.34f, h * 0.42f)
                    close()
                }
                drawPath(tip, color = bodyColor.copy(alpha = 0.75f))
                drawCircle(inkTint, radius = w * 0.07f, center = Offset(w * 0.5f, h * 0.12f))
                drawRoundRect(
                    color = inkTint,
                    topLeft = Offset(w * 0.32f, h * 0.42f),
                    size = Size(w * 0.36f, h * 0.50f),
                    cornerRadius = CornerRadius(5f, 5f)
                )
            }

            ToolType.PENCIL -> {
                // Sharpened graphite pencil
                val wood = Path().apply {
                    moveTo(w * 0.5f, h * 0.08f)
                    lineTo(w * 0.68f, h * 0.38f)
                    lineTo(w * 0.32f, h * 0.38f)
                    close()
                }
                drawPath(wood, color = Color(0xFFE2C391))
                val lead = Path().apply {
                    moveTo(w * 0.5f, h * 0.08f)
                    lineTo(w * 0.57f, h * 0.21f)
                    lineTo(w * 0.43f, h * 0.21f)
                    close()
                }
                drawPath(lead, color = inkTint)
                drawRect(color = bodyColor, topLeft = Offset(w * 0.32f, h * 0.38f), size = Size(w * 0.36f, h * 0.54f))
                drawLine(Color.White.copy(alpha = 0.35f), Offset(w * 0.44f, h * 0.38f), Offset(w * 0.44f, h * 0.92f), 1.5f)
            }

            ToolType.ART_PEN -> {
                // Broad angled calligraphic brush nib
                val brush = Path().apply {
                    moveTo(w * 0.36f, h * 0.12f)
                    lineTo(w * 0.72f, h * 0.24f)
                    lineTo(w * 0.64f, h * 0.56f)
                    lineTo(w * 0.32f, h * 0.56f)
                    close()
                }
                drawPath(brush, color = inkTint)
                drawRoundRect(
                    color = bodyColor,
                    topLeft = Offset(w * 0.30f, h * 0.56f),
                    size = Size(w * 0.40f, h * 0.36f),
                    cornerRadius = CornerRadius(6f, 6f)
                )
            }

            ToolType.HIGHLIGHTER -> {
                // Chisel-tip highlighter marker
                val chisel = Path().apply {
                    moveTo(w * 0.34f, h * 0.24f)
                    lineTo(w * 0.68f, h * 0.10f)
                    lineTo(w * 0.68f, h * 0.42f)
                    lineTo(w * 0.34f, h * 0.42f)
                    close()
                }
                drawPath(chisel, color = inkTint)
                drawRoundRect(
                    color = bodyColor,
                    topLeft = Offset(w * 0.26f, h * 0.42f),
                    size = Size(w * 0.48f, h * 0.50f),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                drawRect(
                    color = inkTint.copy(alpha = 0.85f),
                    topLeft = Offset(w * 0.26f, h * 0.56f),
                    size = Size(w * 0.48f, h * 0.14f)
                )
            }

            ToolType.ERASER -> {
                // Two-tone pink/white block eraser
                drawRoundRect(
                    color = Color(0xFFF472B6),
                    topLeft = Offset(w * 0.20f, h * 0.18f),
                    size = Size(w * 0.60f, h * 0.40f),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                drawRoundRect(
                    color = bodyColor,
                    topLeft = Offset(w * 0.20f, h * 0.50f),
                    size = Size(w * 0.60f, h * 0.34f),
                    cornerRadius = CornerRadius(5f, 5f)
                )
            }

            ToolType.LASSO -> {
                drawOval(
                    color = if (isSelected) Color(0xFF2563EB) else bodyColor,
                    topLeft = Offset(w * 0.14f, h * 0.16f),
                    size = Size(w * 0.72f, h * 0.56f),
                    style = Stroke(
                        width = 2.4f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
                    )
                )
                drawCircle(
                    color = if (isSelected) Color(0xFF2563EB) else bodyColor,
                    radius = w * 0.08f,
                    center = Offset(w * 0.5f, h * 0.78f)
                )
            }

            ToolType.TAPE -> {
                // Active Recall striped tape band
                drawRoundRect(
                    color = inkTint,
                    topLeft = Offset(w * 0.12f, h * 0.30f),
                    size = Size(w * 0.76f, h * 0.40f),
                    cornerRadius = CornerRadius(4f, 4f)
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.8f),
                    start = Offset(w * 0.18f, h * 0.50f),
                    end = Offset(w * 0.82f, h * 0.50f),
                    strokeWidth = 2.2f,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f), 0f)
                )
            }

            else -> {
                drawCircle(color = bodyColor, radius = w * 0.3f)
            }
        }
    }
}
