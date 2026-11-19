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
    STICKER,
    HYPERLINK,
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
enum class ScrollDirection(val label: String) {
    VERTICAL_CONTINUOUS("Vertical Scroll"),
    HORIZONTAL_PAGING("Horizontal Pages")
}

@Serializable
enum class PaperColor(val argb: Long, val gridArgb: Long, val label: String) {
    WHITE(0xFFFFFFFF, 0xFFD6D6D6, "White"),
    CREAM(0xFFFFFEEA, 0xFFE6DFC3, "Yellow Paper"),
    DARK(0xFF191A1A, 0xFF333434, "Dark")
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
    MUSIC("Music Staff"),
    ISOMETRIC("Isometric 3D"),
    HEXAGONAL("Hexagonal Chem"),
    WEEKLY_PLANNER("Weekly Planner"),
    MONTHLY_CALENDAR("Monthly Calendar"),
    TWO_COLUMN("Two-Column Notes"),
    STORYBOARD("Storyboard")
}

@Serializable
enum class CoverPattern(val label: String) {
    CLASSIC_SPINE("Classic Spine"),
    MINIMAL_BAND("Minimal Band"),
    GRID_BLUEPRINT("Blueprint Grid"),
    LINEN_STRIPE("Linen Stripe"),
    DUOTONE_ARCH("Duotone Arch"),
    CORNER_BADGE("Corner Badge")
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
    val fillColorArgb: Long? = null,
    val fillAlpha: Float = 0.18f,
    val borderDashed: Boolean = false,
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
    val width: Float = 360f,
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
data class StickerItem(
    val id: String = UUID.randomUUID().toString(),
    val layerId: String = "layer_default",
    val label: String,
    val emoji: String = "",
    val x: Float,
    val y: Float,
    val width: Float = 150f,
    val height: Float = 120f,
    val rotationDeg: Float = 0f,
    val bgColorArgb: Long = 0xFFFEF3C7,
    val borderColorArgb: Long = 0xFFF59E0B,
    val textColorArgb: Long = 0xFF78350F
)

@Serializable
data class HyperlinkItem(
    val id: String = UUID.randomUUID().toString(),
    val layerId: String = "layer_default",
    val label: String,
    val targetPageIndex: Int,
    val x: Float,
    val y: Float,
    val colorArgb: Long = 0xFF2563EB
)

@Serializable
data class BookmarkItem(
    val id: String = UUID.randomUUID().toString(),
    val pageIndex: Int,
    val title: String,
    val createdAtMs: Long = System.currentTimeMillis()
)

@Serializable
data class FlashcardItem(
    val id: String = UUID.randomUUID().toString(),
    val front: String,
    val back: String,
    val repetitions: Int = 0,
    val intervalDays: Int = 1,
    val easeFactor: Float = 2.5f,
    val nextReviewAtMs: Long = System.currentTimeMillis()
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
    val images: List<ImageItem> = emptyList(),
    val stickers: List<StickerItem> = emptyList(),
    val hyperlinks: List<HyperlinkItem> = emptyList()
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
    val coverColorArgb: Long = 0xFF2563EB,
    val coverAccentArgb: Long = 0xFF60A5FA,
    val coverPattern: CoverPattern = CoverPattern.CLASSIC_SPINE,
    val isFavorite: Boolean = false,
    val isTrashed: Boolean = false,
    val isInfiniteCanvas: Boolean = false,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis(),
    val pages: List<NotePage> = listOf(NotePage()),
    val recordings: List<VoiceRecording> = emptyList(),
    val bookmarks: List<BookmarkItem> = emptyList(),
    val flashcards: List<FlashcardItem> = emptyList()
)

@Serializable
data class Folder(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val parentId: String? = null,
    val colorArgb: Long = 0xFF2563EB,
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
enum class AppThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

@Serializable
enum class AppAccentPalette(
    val label: String,
    val primaryLightArgb: Long,
    val containerLightArgb: Long,
    val primaryDarkArgb: Long,
    val containerDarkArgb: Long
) {
    COBALT("Freenotes Blue", 0xFF047AFF, 0xFFE9F3FF, 0xFF0A84FF, 0xFF1A355B),
    INDIGO("Indigo Studio", 0xFF4F46E5, 0xFFE0E7FF, 0xFF818CF8, 0xFF312E81),
    EMERALD("Botanical Mint", 0xFF059669, 0xFFD1FAE5, 0xFF34D399, 0xFF065F46),
    ROSE("Sakura Rose", 0xFFE11D48, 0xFFFFE4E6, 0xFFFB7185, 0xFF881337),
    AMBER("Warm Amber", 0xFFD97706, 0xFFFEF3C7, 0xFFFBBF24, 0xFF78350F),
    VIOLET("Royal Violet", 0xFF7C3AED, 0xFFEDE9FE, 0xFFA78BFA, 0xFF4C1D95),
    GRAPHITE("Minimal Graphite", 0xFF2E302F, 0xFFEDEBEB, 0xFFCBD5E1, 0xFF2C2C2E)
}

@Serializable
enum class StylusBrand(val label: String, val hint: String) {
    SAMSUNG_SPEN("Samsung S Pen", "Long-press side button"),
    XIAOMI_FOCUS("Xiaomi Stylus / Focus Pen", "Main & secondary buttons / double-tap"),
    HUAWEI_MPENCIL("Huawei M-Pencil", "Double-tap barrel"),
    HONOR_MAGIC("Honor Magic Pencil", "Double-tap barrel"),
    OPPO_PENCIL("OPPO Pencil 2 / 3", "Double-tap / one-key"),
    VIVO_PENCIL("vivo Pencil / 2S", "Main & secondary buttons / double-tap"),
    LENOVO_PEN("Lenovo Precision Pen", "Long-press side button"),
    GENERIC_USI("Generic / USI Stylus", "Standard Android stylus")
}

@Serializable
enum class ToolbarPosition(val label: String) {
    TOP("Top"),
    BOTTOM("Bottom")
}

@Serializable
enum class StylusButtonAction(val label: String) {
    HOLD_ERASER("Hold for Eraser"),
    TOGGLE_ERASER("Toggle Pen / Eraser"),
    HOLD_LASSO("Hold for Lasso"),
    UNDO("Undo Last Stroke"),
    NONE("Disabled")
}

@Serializable
enum class PressureCurve(val label: String, val gamma: Float) {
    SOFT("Soft (Light Touch)", 0.65f),
    NORMAL("Balanced (Default)", 1.0f),
    FIRM("Firm (Heavy Press)", 1.45f)
}

@Serializable
enum class WritingPosture(val label: String) {
    RIGHT_NORMAL("Right Hand — Normal"),
    RIGHT_HOOK("Right Hand — Hook"),
    RIGHT_LOW("Right Hand — Low"),
    LEFT_NORMAL("Left Hand — Normal"),
    LEFT_HOOK("Left Hand — Hook"),
    LEFT_LOW("Left Hand — Low")
}

@Serializable
data class AppSettings(
    // Personalized Themes (Freenotes 3.29.0 Display and Theme)
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val accentPalette: AppAccentPalette = AppAccentPalette.COBALT,
    val smartDarkCanvas: Boolean = false,
    val toolbarPosition: ToolbarPosition = ToolbarPosition.TOP,
    val leftHandedUi: Boolean = false,
    // Stylus & Pencil Support (Samsung / Xiaomi / Huawei / Honor / OPPO / vivo / Lenovo)
    val stylusBrand: StylusBrand = StylusBrand.SAMSUNG_SPEN,
    val stylusOnlyMode: Boolean = false,
    val stylusPrimaryButtonAction: StylusButtonAction = StylusButtonAction.HOLD_ERASER,
    val stylusSecondaryButtonAction: StylusButtonAction = StylusButtonAction.UNDO,
    val pressureSensitiveEraser: Boolean = false,
    val pressureCurve: PressureCurve = PressureCurve.NORMAL,
    val showStylusHoverCursor: Boolean = true,
    val writingPosture: WritingPosture = WritingPosture.RIGHT_NORMAL,
    // Gesture Settings & 120Hz Performance
    val highRefreshRate120Hz: Boolean = true,
    val lowLatencyFrontBuffer: Boolean = true,
    val holdToShapeEnabled: Boolean = true,
    val holdToShapeDelayMs: Long = 420L,
    val scribbleToEraseEnabled: Boolean = true,
    val multiFingerUndoRedoEnabled: Boolean = true,
    val threeFingerSwipeUndoRedo: Boolean = true,
    val oneFingerDoubleTapZoom: Boolean = true
)

@Serializable
data class WorkspaceIndex(
    val folders: List<Folder> = emptyList(),
    val favoriteTools: List<FavoriteToolPreset> = defaultFavoriteTools(),
    val customColors: List<Long> = listOf(0xFF1A1C1E, 0xFF2563EB, 0xFFDC2626, 0xFF0D9488, 0xFFF59E0B, 0xFF7C3AED),
    val customStickers: List<String> = listOf("IMPORTANT", "EXAM", "REVIEW", "FORMULA"),
    val settings: AppSettings = AppSettings()
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
        width = 26f,
        opacity = 0.35f
    ),
    FavoriteToolPreset(
        id = "fav_mint_hl",
        label = "Mint HL",
        toolType = ToolType.HIGHLIGHTER,
        colorArgb = 0xFF34D399,
        width = 26f,
        opacity = 0.35f
    ),
    FavoriteToolPreset(
        id = "fav_tape",
        label = "Recall Tape",
        toolType = ToolType.TAPE,
        colorArgb = 0xFFF59E0B,
        width = 38f
    )
)
