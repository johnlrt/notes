package com.johnlrt.freenotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.johnlrt.freenotes.data.AppAccentPalette
import com.johnlrt.freenotes.data.AppSettings
import com.johnlrt.freenotes.data.AppThemeMode
import com.johnlrt.freenotes.data.PressureCurve
import com.johnlrt.freenotes.data.StylusButtonAction
import com.johnlrt.freenotes.data.ToolbarPosition
import com.johnlrt.freenotes.data.WritingPosture
import kotlin.math.roundToLong

/**
 * Full Freenotes-style tablet Settings modal with 3 sections:
 * 1. App Style & Theme (Light/Dark/System, 7 Accent Palettes, Toolbar Top/Bottom, Left-Handed UI)
 * 2. Stylus & Pencil Support (Palm Rejection, Primary/Secondary Side-Button actions, Pressure Curve, Hover Cursor, Writing Posture)
 * 3. Gestures & 120Hz Performance (120Hz lock, GPU Front-Buffer, Hold-to-Shape delay, Scribble-to-Erase, Multi-finger Undo/Redo)
 */
@Composable
fun SettingsDialog(
    settings: AppSettings,
    displayRefreshHz: Int,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .widthIn(max = 820.dp)
                .height(580.dp),
            shape = RoundedCornerShape(22.dp),
            tonalElevation = 8.dp,
            shadowElevation = 16.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Settings & Stylus",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close Settings")
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    // Left category rail
                    Column(
                        modifier = Modifier
                            .width(210.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SettingsTabItem(
                            icon = Icons.Default.Palette,
                            label = "Style & Theme",
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 }
                        )
                        SettingsTabItem(
                            icon = Icons.Default.Brush,
                            label = "Stylus & Pencil",
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 }
                        )
                        SettingsTabItem(
                            icon = Icons.Default.Gesture,
                            label = "Gestures & 120Hz",
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 }
                        )
                    }

                    // Right content pane
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        when (selectedTab) {
                            0 -> AppearanceSettingsPane(settings, onUpdateSettings)
                            1 -> StylusSettingsPane(settings, onUpdateSettings)
                            2 -> GesturesAndPerformancePane(settings, displayRefreshHz, onUpdateSettings)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsTabItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val fg = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = fg)
    }
}

@Composable
private fun AppearanceSettingsPane(
    settings: AppSettings,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit
) {
    SectionHeader("App Theme")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AppThemeMode.entries.forEach { mode ->
            FilterChip(
                selected = settings.themeMode == mode,
                onClick = { onUpdate { it.copy(themeMode = mode) } },
                label = { Text(mode.label) }
            )
        }
    }

    SectionHeader("App Accent Color")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        AppAccentPalette.entries.forEach { palette ->
            val selected = settings.accentPalette == palette
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(palette.primaryLightArgb))
                    .border(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Black.copy(alpha = 0.15f),
                        shape = CircleShape
                    )
                    .clickable { onUpdate { it.copy(accentPalette = palette) } },
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
    Text(
        "Active Style: ${settings.accentPalette.label}",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary
    )

    HorizontalDivider()

    SectionHeader("Editor Toolbar Position")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ToolbarPosition.entries.forEach { pos ->
            FilterChip(
                selected = settings.toolbarPosition == pos,
                onClick = { onUpdate { it.copy(toolbarPosition = pos) } },
                label = { Text(pos.label) }
            )
        }
    }

    SettingsToggleRow(
        title = "Smart Dark Themes (Content Night Effect)",
        subtitle = "Automatically adapts PDF and canvas contrast in Dark Mode",
        checked = settings.smartDarkCanvas,
        onCheckedChange = { v -> onUpdate { it.copy(smartDarkCanvas = v) } }
    )

    SettingsToggleRow(
        title = "Left-Handed UI Layout",
        subtitle = "Mirrors side panels and floating controls for left-handed writing",
        checked = settings.leftHandedUi,
        onCheckedChange = { v -> onUpdate { it.copy(leftHandedUi = v) } }
    )
}

@Composable
private fun StylusSettingsPane(
    settings: AppSettings,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit
) {
    SectionHeader("Stylus Model / Brand Preset")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        com.johnlrt.freenotes.data.StylusBrand.entries.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { brand ->
                    FilterChip(
                        selected = settings.stylusBrand == brand,
                        onClick = { onUpdate { it.copy(stylusBrand = brand) } },
                        label = { Text(brand.label) }
                    )
                }
            }
        }
    }
    Text(
        "Active: ${settings.stylusBrand.label} — ${settings.stylusBrand.hint}",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary
    )

    HorizontalDivider()

    SettingsToggleRow(
        title = "Palm Rejection (Stylus-Only Writing)",
        subtitle = "Only active stylus writes; fingers smoothly pan & zoom the canvas",
        checked = settings.stylusOnlyMode,
        onCheckedChange = { v -> onUpdate { it.copy(stylusOnlyMode = v) } }
    )

    SettingsToggleRow(
        title = "Pressure-Sensitive Eraser",
        subtitle = "Press firmly with stylus while writing to temporarily activate the eraser, release to keep writing",
        checked = settings.pressureSensitiveEraser,
        onCheckedChange = { v -> onUpdate { it.copy(pressureSensitiveEraser = v) } }
    )

    SettingsToggleRow(
        title = "Stylus Hover Cursor Preview",
        subtitle = "Shows a live nib ring when hovering the stylus above the glass",
        checked = settings.showStylusHoverCursor,
        onCheckedChange = { v -> onUpdate { it.copy(showStylusHoverCursor = v) } }
    )

    HorizontalDivider()

    SectionHeader("Main Button / Long Press Action")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StylusButtonAction.entries.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { action ->
                    FilterChip(
                        selected = settings.stylusPrimaryButtonAction == action,
                        onClick = { onUpdate { it.copy(stylusPrimaryButtonAction = action) } },
                        label = { Text(action.label) }
                    )
                }
            }
        }
    }

    SectionHeader("Secondary Button / Double-Tap Action")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            StylusButtonAction.UNDO,
            StylusButtonAction.TOGGLE_ERASER,
            StylusButtonAction.HOLD_LASSO,
            StylusButtonAction.NONE
        ).forEach { action ->
            FilterChip(
                selected = settings.stylusSecondaryButtonAction == action,
                onClick = { onUpdate { it.copy(stylusSecondaryButtonAction = action) } },
                label = { Text(action.label) }
            )
        }
    }

    HorizontalDivider()

    SectionHeader("Global Pressure Sensitivity Curve")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PressureCurve.entries.forEach { curve ->
            FilterChip(
                selected = settings.pressureCurve == curve,
                onClick = { onUpdate { it.copy(pressureCurve = curve) } },
                label = { Text(curve.label) }
            )
        }
    }

    SectionHeader("Writing Posture (Palm Rejection Calibration)")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        WritingPosture.entries.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { posture ->
                    FilterChip(
                        selected = settings.writingPosture == posture,
                        onClick = { onUpdate { it.copy(writingPosture = posture) } },
                        label = { Text(posture.label) }
                    )
                }
            }
        }
    }
}

@Composable
private fun GesturesAndPerformancePane(
    settings: AppSettings,
    displayRefreshHz: Int,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                "Detected Display Max Refresh Rate: ${displayRefreshHz} Hz",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }

    SettingsToggleRow(
        title = "120Hz / Max Refresh Rate Render",
        subtitle = "Locks tablet display to peak native Hz + Android 14+ touch boost",
        checked = settings.highRefreshRate120Hz,
        onCheckedChange = { v -> onUpdate { it.copy(highRefreshRate120Hz = v) } }
    )

    SettingsToggleRow(
        title = "Zero-Latency GPU Front-Buffer Ink",
        subtitle = "Renders live stylus strokes directly into the GPU front buffer + OS MotionEventPredictor",
        checked = settings.lowLatencyFrontBuffer,
        onCheckedChange = { v -> onUpdate { it.copy(lowLatencyFrontBuffer = v) } }
    )

    HorizontalDivider()

    SettingsToggleRow(
        title = "Hold-to-Shape Auto-Snap",
        subtitle = "Pause stylus at end of a stroke to convert into a crisp line, circle, rectangle, or arrow",
        checked = settings.holdToShapeEnabled,
        onCheckedChange = { v -> onUpdate { it.copy(holdToShapeEnabled = v) } }
    )

    if (settings.holdToShapeEnabled) {
        Column {
            Text(
                "Hold-to-Shape Trigger Delay: ${settings.holdToShapeDelayMs} ms",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = settings.holdToShapeDelayMs.toFloat(),
                onValueChange = { ms -> onUpdate { it.copy(holdToShapeDelayMs = ms.roundToLong()) } },
                valueRange = 250f..700f
            )
        }
    }

    SettingsToggleRow(
        title = "Scribble to Erase",
        subtitle = "Scratch back-and-forth over any ink stroke with a pen to erase it instantly",
        checked = settings.scribbleToEraseEnabled,
        onCheckedChange = { v -> onUpdate { it.copy(scribbleToEraseEnabled = v) } }
    )

    SettingsToggleRow(
        title = "2-Finger Tap Undo / 3-Finger Tap Redo",
        subtitle = "2 finger tap to undo, 3 finger tap to redo directly on the canvas",
        checked = settings.multiFingerUndoRedoEnabled,
        onCheckedChange = { v -> onUpdate { it.copy(multiFingerUndoRedoEnabled = v) } }
    )

    SettingsToggleRow(
        title = "3-Finger Swipe Undo / Redo",
        subtitle = "3 finger swipe left to undo, 3 finger swipe right to redo",
        checked = settings.threeFingerSwipeUndoRedo,
        onCheckedChange = { v -> onUpdate { it.copy(threeFingerSwipeUndoRedo = v) } }
    )

    SettingsToggleRow(
        title = "One-Finger Double-Tap Zoom",
        subtitle = "When using Stylus-Only mode, double-tap with one finger to toggle zoom",
        checked = settings.oneFingerDoubleTapZoom,
        onCheckedChange = { v -> onUpdate { it.copy(oneFingerDoubleTapZoom = v) } }
    )
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
