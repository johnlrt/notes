package com.johnlrt.freenotes.ui.editor

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.johnlrt.freenotes.data.FlashcardItem
import com.johnlrt.freenotes.data.Notebook
import com.johnlrt.freenotes.engine.ReviewRating
import kotlin.math.roundToInt

data class StickerTemplate(
    val label: String,
    val emoji: String,
    val bgArgb: Long,
    val borderArgb: Long,
    val textArgb: Long,
    val category: String = "Study"
)

val BuiltInStickers = listOf(
    // Study & Exam Pack
    StickerTemplate("IMPORTANT", "⭐", 0xFFFEF3C7, 0xFFF59E0B, 0xFF92400E, "Study"),
    StickerTemplate("EXAM KEY", "🎯", 0xFFFEE2E2, 0xFFEF4444, 0xFF991B1B, "Study"),
    StickerTemplate("REVIEW", "📌", 0xFFDCFCE7, 0xFF16A34A, 0xFF14532D, "Study"),
    StickerTemplate("QUESTION", "❓", 0xFFF3E8FF, 0xFF9333EA, 0xFF581C87, "Study"),
    StickerTemplate("DEFINITION", "📖", 0xFFE0E7FF, 0xFF4F46E5, 0xFF312E81, "Study"),
    StickerTemplate("KEY POINT", "🔑", 0xFFFEF9C3, 0xFFCA8A04, 0xFF713F12, "Study"),
    StickerTemplate("SUMMARY", "📝", 0xFFE0F2FE, 0xFF0284C7, 0xFF0C4A6E, "Study"),
    StickerTemplate("MEMORIZE", "🧠", 0xFFFCE7F3, 0xFFDB2777, 0xFF831843, "Study"),
    StickerTemplate("WARNING", "⚠️", 0xFFFECDD3, 0xFFE11D48, 0xFF881337, "Study"),
    // Planner & Productivity Pack
    StickerTemplate("TO-DO", "☐", 0xFFDBEAFE, 0xFF2563EB, 0xFF1E3A8A, "Planner"),
    StickerTemplate("DONE", "✅", 0xFFCCFBF1, 0xFF0D9488, 0xFF134E4A, "Planner"),
    StickerTemplate("DEADLINE", "⏰", 0xFFFFEDD5, 0xFFEA580C, 0xFF7C2D12, "Planner"),
    StickerTemplate("URGENT", "🔥", 0xFFFEE2E2, 0xFFDC2626, 0xFF991B1B, "Planner"),
    StickerTemplate("MEETING", "📅", 0xFFE0E7FF, 0xFF4F46E5, 0xFF312E81, "Planner"),
    StickerTemplate("GOAL", "🏆", 0xFFFEF3C7, 0xFFD97706, 0xFF78350F, "Planner"),
    StickerTemplate("HABIT", "🔁", 0xFFDCFCE7, 0xFF16A34A, 0xFF14532D, "Planner"),
    StickerTemplate("IN PROGRESS", "⏳", 0xFFF3E8FF, 0xFF7C3AED, 0xFF4C1D95, "Planner"),
    StickerTemplate("PRIORITY #1", "🚀", 0xFFFCE7F3, 0xFFDB2777, 0xFF831843, "Planner"),
    // Pastel Callouts & Sticky Notes Pack
    StickerTemplate("IDEA", "💡", 0xFFFEF9C3, 0xFFEAB308, 0xFF713F12, "Callouts"),
    StickerTemplate("NOTE", "🗒️", 0xFFFEF3C7, 0xFFF59E0B, 0xFF78350F, "Callouts"),
    StickerTemplate("TIP", "✨", 0xFFECFCCB, 0xFF65A30D, 0xFF365314, "Callouts"),
    StickerTemplate("QUOTE", "❝", 0xFFF1F5F9, 0xFF64748B, 0xFF1E293B, "Callouts"),
    StickerTemplate("CHECK", "✔️", 0xFFD1FAE5, 0xFF059669, 0xFF064E3B, "Callouts"),
    StickerTemplate("LINK", "🔗", 0xFFDBEAFE, 0xFF2563EB, 0xFF1E3A8A, "Callouts"),
    StickerTemplate("EXAMPLE", "👉", 0xFFEDE9FE, 0xFF7C3AED, 0xFF4C1D95, "Callouts"),
    StickerTemplate("REMINDER", "🔔", 0xFFFFE4E6, 0xFFE11D48, 0xFF881337, "Callouts"),
    StickerTemplate("GREAT!", "🎉", 0xFFFFF7ED, 0xFFEA580C, 0xFF7C2D12, "Callouts"),
    // Academic & STEM Pack
    StickerTemplate("FORMULA", "∑", 0xFFDBEAFE, 0xFF2563EB, 0xFF1E3A8A, "STEM"),
    StickerTemplate("THEOREM", "⊢", 0xFFE0E7FF, 0xFF4338CA, 0xFF312E81, "STEM"),
    StickerTemplate("PROOF", "∎", 0xFFF1F5F9, 0xFF334155, 0xFF0F172A, "STEM"),
    StickerTemplate("PHYSICS", "⚛️", 0xFFE0F2FE, 0xFF0284C7, 0xFF0C4A6E, "STEM"),
    StickerTemplate("CHEM", "🧪", 0xFFDCFCE7, 0xFF15803D, 0xFF14532D, "STEM"),
    StickerTemplate("BIOLOGY", "🧬", 0xFFCCFBF1, 0xFF0F766E, 0xFF134E4A, "STEM"),
    StickerTemplate("MEDICINE", "🩺", 0xFFFEE2E2, 0xFFDC2626, 0xFF7F1D1D, "STEM"),
    StickerTemplate("CODE", "</>", 0xFFF3E8FF, 0xFF7E22CE, 0xFF581C87, "STEM"),
    StickerTemplate("DATA", "📊", 0xFFFFEDD5, 0xFFEA580C, 0xFF7C2D12, "STEM")
)

/**
 * Freenotes Categorized Stickers Picker & Custom Sticker creator dialog.
 */
@Composable
fun StickersDialog(
    onPickSticker: (StickerTemplate) -> Unit,
    onDismiss: () -> Unit
) {
    var customText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Study") }
    val categories = listOf("Study", "Planner", "Callouts", "STEM")
    val filtered = remember(selectedCategory) { BuiltInStickers.filter { it.category == selectedCategory } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Stickers & Study Packs", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { customText = it },
                        placeholder = { Text("Custom sticker text...") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        enabled = customText.isNotBlank(),
                        onClick = {
                            onPickSticker(
                                StickerTemplate(
                                    label = customText.trim().uppercase(),
                                    emoji = "✨",
                                    bgArgb = 0xFFDBEAFE,
                                    borderArgb = 0xFF2563EB,
                                    textArgb = 0xFF1E3A8A
                                )
                            )
                        }
                    ) { Text("Add") }
                }

                // Category selector pills
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.forEach { cat ->
                        val active = cat == selectedCategory
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { selectedCategory = cat }
                        ) {
                            Text(
                                cat,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(4.dp),
                    modifier = Modifier.height(280.dp)
                ) {
                    items(filtered) { st ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(st.bgArgb))
                                .border(1.5.dp, Color(st.borderArgb), RoundedCornerShape(12.dp))
                                .clickable { onPickSticker(st) }
                                .padding(horizontal = 12.dp, vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${st.emoji} ${st.label}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(st.textArgb)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

/**
 * Freenotes Outline, Bookmarks & In-Notebook Search side panel.
 */
@Composable
fun OutlineBookmarksPanel(
    notebook: Notebook,
    currentPageIndex: Int,
    onJumpToPage: (Int) -> Unit,
    onAddBookmark: (String) -> Unit,
    onDeleteBookmark: (String) -> Unit,
    onClose: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var newBookmarkTitle by remember { mutableStateOf("") }

    val searchHits = remember(notebook.pages, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) emptyList()
        else notebook.pages.flatMapIndexed { idx, p ->
            p.textBoxes.filter { it.text.lowercase().contains(q) }.map { idx to it.text }
        }
    }

    Surface(
        modifier = Modifier
            .width(290.dp)
            .fillMaxHeight(),
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Outline & Search", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = onClose, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search text in notebook...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (searchQuery.isNotBlank()) {
                Text("Matches (${searchHits.size})", style = MaterialTheme.typography.labelMedium)
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(searchHits) { (pageIdx, snippet) ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onJumpToPage(pageIdx) }
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Page ${pageIdx + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(snippet, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            }
                        }
                    }
                }
            } else {
                HorizontalDivider()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = newBookmarkTitle,
                        onValueChange = { newBookmarkTitle = it },
                        placeholder = { Text("Bookmark Page ${currentPageIndex + 1}...") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            onAddBookmark(newBookmarkTitle.ifBlank { "Page ${currentPageIndex + 1}" })
                            newBookmarkTitle = ""
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Bookmark")
                    }
                }

                Text("Bookmarks (${notebook.bookmarks.size})", style = MaterialTheme.typography.labelMedium)
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(notebook.bookmarks, key = { it.id }) { bm ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (bm.pageIndex == currentPageIndex) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onJumpToPage(bm.pageIndex) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(bm.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text("Page ${bm.pageIndex + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { onDeleteBookmark(bm.id) }, modifier = Modifier.size(26.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Freenotes Active-Recall Flashcards Study Modal with SM-2 Spaced Repetition.
 */
@Composable
fun FlashcardsDialog(
    cards: List<FlashcardItem>,
    onAddCard: (String, String) -> Unit,
    onRateCard: (String, ReviewRating) -> Unit,
    onDeleteCard: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var currentIndex by remember { mutableIntStateOf(0) }
    var flipped by remember { mutableStateOf(false) }
    var addingNew by remember { mutableStateOf(false) }
    var frontInput by remember { mutableStateOf("") }
    var backInput by remember { mutableStateOf("") }

    val safeIndex = currentIndex.coerceIn(0, (cards.size - 1).coerceAtLeast(0))
    val currentCard = cards.getOrNull(safeIndex)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Flashcards (${cards.size})", fontWeight = FontWeight.Bold)
                TextButton(onClick = { addingNew = !addingNew }) {
                    Text(if (addingNew) "Study Deck" else "+ New Card")
                }
            }
        },
        text = {
            if (addingNew) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = frontInput,
                        onValueChange = { frontInput = it },
                        label = { Text("Front (Question)") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = backInput,
                        onValueChange = { backInput = it },
                        label = { Text("Back (Answer)") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        enabled = frontInput.isNotBlank() && backInput.isNotBlank(),
                        onClick = {
                            onAddCard(frontInput.trim(), backInput.trim())
                            frontInput = ""
                            backInput = ""
                            addingNew = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Save Flashcard") }
                }
            } else if (currentCard == null) {
                Text("No flashcards yet. Tap '+ New Card' to build your active recall deck!")
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Card ${safeIndex + 1} of ${cards.size}  •  Interval: ${currentCard.intervalDays}d",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clickable { flipped = !flipped },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (flipped) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .padding(18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    if (flipped) "ANSWER" else "QUESTION (Tap to Flip)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    if (flipped) currentCard.back else currentCard.front,
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    if (flipped) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ReviewRating.entries.forEach { rating ->
                                OutlinedButton(
                                    onClick = {
                                        onRateCard(currentCard.id, rating)
                                        flipped = false
                                        currentIndex = (safeIndex + 1) % cards.size.coerceAtLeast(1)
                                    },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                ) {
                                    Text(rating.label, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(onClick = { onDeleteCard(currentCard.id) }) {
                                Text("Delete", color = MaterialTheme.colorScheme.error)
                            }
                            Button(onClick = { flipped = true }) {
                                Text("Show Answer")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

/**
 * Freenotes' signature Floating Picture-in-Picture (PiP) Lecture Video Window
 * with instant "Capture Frame to Page" screenshot button!
 */
@Composable
fun FloatingVideoOverlay(
    videoUri: Uri,
    onCaptureFrame: (Long) -> Unit,
    onClose: () -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(40f) }
    var offsetY by remember { mutableFloatStateOf(120f) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var isPlaying by remember { mutableStateOf(true) }

    Surface(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .width(340.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    offsetX += drag.x
                    offsetY += drag.y
                }
            },
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 10.dp,
        color = Color(0xFF0F172A)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("PiP Lecture Video", style = MaterialTheme.typography.labelMedium, color = Color.White)
                IconButton(onClick = onClose, modifier = Modifier.size(26.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close Video", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }

            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setVideoURI(videoUri)
                        start()
                        videoViewRef = this
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(185.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row {
                    IconButton(
                        onClick = {
                            videoViewRef?.let { v -> v.seekTo((v.currentPosition - 10_000).coerceAtLeast(0)) }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Replay10, contentDescription = "-10s", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = {
                            videoViewRef?.let { v ->
                                if (v.isPlaying) { v.pause(); isPlaying = false }
                                else { v.start(); isPlaying = true }
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            videoViewRef?.let { v -> v.seekTo(v.currentPosition + 10_000) }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Forward10, contentDescription = "+10s", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }

                TextButton(
                    onClick = {
                        val posMs = (videoViewRef?.currentPosition ?: 0).toLong()
                        onCaptureFrame(posMs)
                    }
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Capture Frame", color = Color(0xFF38BDF8), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
