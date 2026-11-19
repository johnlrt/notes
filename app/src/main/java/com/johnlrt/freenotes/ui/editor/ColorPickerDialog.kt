package com.johnlrt.freenotes.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Full HSV Color Wheel + Saturation/Brightness sliders + Hex input + Saved Custom Swatches
 * (recreating Freenotes' `dialog_color_wheel.xml` & `dialog_color_exact.xml`).
 */
@Composable
fun ColorPickerDialog(
    initialColorArgb: Long,
    savedColors: List<Long>,
    onPickColor: (Long) -> Unit,
    onSaveCustomColor: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val initHsv = remember(initialColorArgb) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initialColorArgb.toInt(), it) }
    }
    var hue by remember { mutableFloatStateOf(initHsv[0]) }
    var sat by remember { mutableFloatStateOf(initHsv[1]) }
    var value by remember { mutableFloatStateOf(initHsv[2]) }

    val currentColor = Color.hsv(hue, sat, value)
    val currentArgb = (currentColor.toArgb().toLong() and 0xFFFFFFFFL)
    var hexText by remember(currentArgb) {
        mutableStateOf("#%06X".format(currentArgb and 0xFFFFFFL))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Color Wheel & Swatches", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Interactive Hue Wheel
                Box(
                    modifier = Modifier.size(190.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier
                            .size(190.dp)
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    val cx = size.width / 2f
                                    val cy = size.height / 2f
                                    val dx = offset.x - cx
                                    val dy = offset.y - cy
                                    val r = (hypot(dx.toDouble(), dy.toDouble()).toFloat() / (size.width / 2f)).coerceIn(0f, 1f)
                                    var angle = (atan2(dy.toDouble(), dx.toDouble()) * 180.0 / PI).toFloat()
                                    if (angle < 0f) angle += 360f
                                    hue = angle
                                    sat = r
                                }
                            }
                    ) {
                        val sweep = Brush.sweepGradient(
                            listOf(
                                Color.Red, Color.Yellow, Color.Green, Color.Cyan,
                                Color.Blue, Color.Magenta, Color.Red
                            )
                        )
                        drawCircle(brush = sweep)
                        val radial = Brush.radialGradient(
                            listOf(Color.White, Color.Transparent)
                        )
                        drawCircle(brush = radial)
                        // Current indicator dot
                        val rad = hue * PI / 180.0
                        val px = center.x + (kotlin.math.cos(rad) * sat * size.width * 0.48f).toFloat()
                        val py = center.y + (kotlin.math.sin(rad) * sat * size.height * 0.48f).toFloat()
                        drawCircle(Color.White, radius = 10f, center = Offset(px, py))
                        drawCircle(currentColor, radius = 7f, center = Offset(px, py))
                    }
                }

                // Brightness Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Brightness", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(76.dp))
                    Slider(
                        value = value,
                        onValueChange = { value = it },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Preview + Hex Input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(currentColor)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                    )
                    OutlinedTextField(
                        value = hexText,
                        onValueChange = { input ->
                            hexText = input
                            runCatching {
                                val clean = input.removePrefix("#")
                                if (clean.length == 6) {
                                    val parsed = (0xFF000000L or clean.toLong(16)).toInt()
                                    val hsv = FloatArray(3)
                                    android.graphics.Color.colorToHSV(parsed, hsv)
                                    hue = hsv[0]
                                    sat = hsv[1]
                                    value = hsv[2]
                                }
                            }
                        },
                        label = { Text("Hex") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { onSaveCustomColor(currentArgb) }) {
                        Text("+ Save")
                    }
                }

                // Saved Swatches Row
                if (savedColors.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(savedColors) { swatch ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(swatch))
                                    .border(1.dp, Color.Gray.copy(alpha = 0.4f), CircleShape)
                                    .clickable {
                                        val hsv = FloatArray(3)
                                        android.graphics.Color.colorToHSV(swatch.toInt(), hsv)
                                        hue = hsv[0]
                                        sat = hsv[1]
                                        value = hsv[2]
                                    }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onPickColor(currentArgb) }) {
                Text("Apply Color")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
