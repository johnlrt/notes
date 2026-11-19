package com.johnlrt.freenotes.data

import kotlinx.serialization.Serializable
import java.util.UUID

enum class ToolType {
    FOUNTAIN_PEN,
    BALLPOINT,
    PENCIL,
    ART_PEN,
    HIGHLIGHTER,
    ERASER,
    LASSO,
    TAPE,
    SHAPE,
    TEXT,
    IMAGE,
    LASER
}

@Serializable
enum class HighlighterStyle {
    SOLID,
    DASHED,
    DOTTED
}

@Serializable
enum class EraserMode {
    WHOLE_STROKE,
    PARTIAL_PIXEL
}

@Serializable
enum class ShapeKind {
    AUTO,
    LINE,
    RECTANGLE,
    ELLIPSE,
    TRIANGLE,
    ARROW
}

@Serializable
enum class PaperColor(val argb: Long, val gridArgb: Long, val label: String) {
    WHITE(0xFFFFFFFF, 0xFFE2E6EC, "White"),
    CREAM(0xFFFDF8E8, 0xFFE6DCC0, "Cream"),
    DARK(0xFF1C1C1E, 0xFF333338, "Dark")
}

@Serializable
enum class PaperTemplate(val label: String) {
    BLANK("Blank"),
    RULED("Ruled"),
    WIDE_RULED("Wide Ruled"),
    GRID("Grid"),
    DOTTED("Dotted"),
    CORNELL("Cornell"),
    PLANNER("To-Do Planner"),
    MUSIC("Music Staff")
}

@Serializable
enum class PageOrientation(val widthPx: Float, val heightPx: Float, val label: String) {
    PORTRAIT(1240f, 1754f, "Portrait (A4)"),
    LANDSCAPE(1754f, 1240f, "Landscape (A4)")
}

@Serializable
data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 0.5f,
    val timeOffsetMs: Long = 0L
)

@Serializable
data class Stroke(
    val id: String = UUID.randomUUID().toString(),
    val layerId: String = "layer_default",
    val toolType: ToolType = ToolType.FOUNTAIN_PEN,
    val colorArgb: Long = 0xFF1A1C1E,
    val baseWidth: Float = 4.5f,
    val opacity: Float = 1f,
    val pressureSensitivity: Float = 0.7f,
    val nibAngleDeg: Float = 35f,
    val highlighterStyle: HighlighterStyle = HighlighterStyle.SOLID,
    val isStraightLine: Boolean = false,
    val isFilledShape: Boolean = false,
    val points: List<StrokePoint> = emptyList()
)

@Serializable
data class TapeItem(
    val id: String = UUID.randomUUID().toString(),
    val layerId: String = "layer_default",
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val thickness: Float = 36f,
    val colorArgb: Long = 0xFFF59E0B,
    val isRevealed: Boolean = false
)

@Serializable
data class TextBoxItem(
    val id: String = UUID.randomUUID().toString(),
    val layerId: String = "layer_default",
    val x: Float,
    val y: Float,
    val width: Float = 320f,
    val text: String = "",
    val fontSizeSp: Float = 20f,
    val colorArgb: Long = 0xFF1A1C1E,
    val isBold: Boolean = false
)

@Serializable
data class ImageItem(
    val id: String = UUID.randomUUID().toString(),
    val layerId: String = "layer_default",
    val filePath: String,
    val x: Float,
    val y: Float,
    val width: Float = 360f,
    val height: Float = 270f,
    val rotationDeg: Float = 0f
)

@Serializable
data class Layer(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Layer 1",
    val isVisible: Boolean = true,
    val isLocked: Boolean = false
)

@Serializable
data class NotePage(
    val id: String = UUID.randomUUID().toString(),
    val template: PaperTemplate = PaperTemplate.RULED,
    val paperColor: PaperColor = PaperColor.WHITE,
    val orientation: PageOrientation = PageOrientation.PORTRAIT,
    val pdfSourcePath: String? = null,
    val pdfPageIndex: Int = -1,
    val layers: List<Layer> = listOf(Layer(id = "layer_default", name = "Main Layer")),
    val strokes: List<Stroke> = emptyList(),
    val tapes: List<TapeItem> = emptyList(),
    val textBoxes: List<TextBoxItem> = emptyList(),
    val images: List<ImageItem> = emptyList()
)

@Serializable
data class VoiceRecording(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val filePath: String,
    val durationMs: Long = 0L,
    val createdAtMs: Long = System.currentTimeMillis()
)

@Serializable
data class Notebook(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val folderId: String? = null,
    val coverColorArgb: Long = 0xFF4F46E5,
    val coverAccentArgb: Long = 0xFF818CF8,
    val isFavorite: Boolean = false,
    val isTrashed: Boolean = false,
    val isInfiniteCanvas: Boolean = false,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis(),
    val pages: List<NotePage> = listOf(NotePage()),
    val recordings: List<VoiceRecording> = emptyList()
)

@Serializable
data class Folder(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val parentId: String? = null,
    val colorArgb: Long = 0xFF3B82F6,
    val createdAtMs: Long = System.currentTimeMillis()
)

@Serializable
data class FavoriteToolPreset(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val toolType: ToolType,
    val colorArgb: Long,
    val width: Float,
    val opacity: Float = 1f,
    val highlighterStyle: HighlighterStyle = HighlighterStyle.SOLID
)

@Serializable
data class WorkspaceIndex(
    val folders: List<Folder> = emptyList(),
    val favoriteTools: List<FavoriteToolPreset> = defaultFavoriteTools()
)

fun defaultFavoriteTools(): List<FavoriteToolPreset> = listOf(
    FavoriteToolPreset(
        id = "fav_black_fountain",
        label = "Black Pen",
        toolType = ToolType.FOUNTAIN_PEN,
        colorArgb = 0xFF1A1C1E,
        width = 4.0f
    ),
    FavoriteToolPreset(
        id = "fav_blue_ballpoint",
        label = "Blue Pen",
        toolType = ToolType.BALLPOINT,
        colorArgb = 0xFF2563EB,
        width = 3.5f
    ),
    FavoriteToolPreset(
        id = "fav_red_pen",
        label = "Red Pen",
        toolType = ToolType.FOUNTAIN_PEN,
        colorArgb = 0xFFDC2626,
        width = 3.5f
    ),
    FavoriteToolPreset(
        id = "fav_yellow_hl",
        label = "Yellow HL",
        toolType = ToolType.HIGHLIGHTER,
        colorArgb = 0xFFFACC15,
        width = 24f,
        opacity = 0.35f
    ),
    FavoriteToolPreset(
        id = "fav_mint_hl",
        label = "Mint HL",
        toolType = ToolType.HIGHLIGHTER,
        colorArgb = 0xFF34D399,
        width = 24f,
        opacity = 0.35f
    ),
    FavoriteToolPreset(
        id = "fav_tape",
        label = "Recall Tape",
        toolType = ToolType.TAPE,
        colorArgb = 0xFFF59E0B,
        width = 36f
    )
)
