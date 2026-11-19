package com.johnlrt.freenotes.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.johnlrt.freenotes.data.Folder
import com.johnlrt.freenotes.data.Notebook
import com.johnlrt.freenotes.data.PageOrientation
import com.johnlrt.freenotes.data.PaperColor
import com.johnlrt.freenotes.data.PaperTemplate
import com.johnlrt.freenotes.ui.AppUiState
import com.johnlrt.freenotes.ui.LibrarySection
import com.johnlrt.freenotes.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CoverPresets = listOf(
    0xFF4F46E5L to 0xFF818CF8L, // Indigo
    0xFF0D9488L to 0xFF2DD4BFL, // Teal
    0xFFEA580CL to 0xFFFB923CL, // Amber/Orange
    0xFFBE185DL to 0xFFF472B6L, // Rose
    0xFF1E293BL to 0xFF475569L, // Slate
    0xFF15803DL to 0xFF4ADE80L  // Emerald
)

@Composable
fun LibraryScreen(
    state: AppUiState,
    viewModel: MainViewModel,
    onRequestImportPdf: () -> Unit
) {
    var showNewNotebookDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }

    val filteredNotebooks = remember(
        state.notebooks,
        state.librarySection,
        state.selectedFolderId,
        state.searchQuery
    ) {
        val q = state.searchQuery.trim().lowercase()
        state.notebooks.filter { nb ->
            val sectionMatch = when (state.librarySection) {
                LibrarySection.ALL_NOTES -> !nb.isTrashed
                LibrarySection.RECENT -> !nb.isTrashed
                LibrarySection.FAVORITES -> !nb.isTrashed && nb.isFavorite
                LibrarySection.FOLDER -> !nb.isTrashed && nb.folderId == state.selectedFolderId
                LibrarySection.TRASH -> nb.isTrashed
            }
            val queryMatch = q.isEmpty() || nb.title.lowercase().contains(q)
            sectionMatch && queryMatch
        }.sortedByDescending { it.updatedAtMs }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Persistent Tablet Sidebar
        Surface(
            modifier = Modifier
                .width(260.dp)
                .fillMaxHeight(),
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "FreeNotes",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Tablet Studio",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                SidebarItem(
                    icon = Icons.Default.Description,
                    label = "All Notebooks",
                    count = state.notebooks.count { !it.isTrashed },
                    selected = state.librarySection == LibrarySection.ALL_NOTES,
                    onClick = { viewModel.selectLibrarySection(LibrarySection.ALL_NOTES) }
                )
                SidebarItem(
                    icon = Icons.Default.Schedule,
                    label = "Recent",
                    selected = state.librarySection == LibrarySection.RECENT,
                    onClick = { viewModel.selectLibrarySection(LibrarySection.RECENT) }
                )
                SidebarItem(
                    icon = Icons.Default.Star,
                    label = "Favorites",
                    count = state.notebooks.count { !it.isTrashed && it.isFavorite },
                    selected = state.librarySection == LibrarySection.FAVORITES,
                    onClick = { viewModel.selectLibrarySection(LibrarySection.FAVORITES) }
                )

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "FOLDERS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(
                        onClick = { showNewFolderDialog = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.CreateNewFolder,
                            contentDescription = "New Folder",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(state.folders, key = { it.id }) { folder ->
                        SidebarItem(
                            icon = Icons.Default.Folder,
                            iconTint = Color(folder.colorArgb),
                            label = folder.name,
                            count = state.notebooks.count { !it.isTrashed && it.folderId == folder.id },
                            selected = state.librarySection == LibrarySection.FOLDER &&
                                state.selectedFolderId == folder.id,
                            onClick = {
                                viewModel.selectLibrarySection(LibrarySection.FOLDER, folder.id)
                            }
                        )
                    }
                }

                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                SidebarItem(
                    icon = Icons.Default.Delete,
                    label = "Trash",
                    count = state.notebooks.count { it.isTrashed },
                    selected = state.librarySection == LibrarySection.TRASH,
                    onClick = { viewModel.selectLibrarySection(LibrarySection.TRASH) }
                )
            }
        }

        // Main Library Content Area
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(24.dp)
        ) {
            // Top Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::updateSearchQuery,
                    placeholder = { Text("Search notebooks...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = viewModel::toggleGridView) {
                    Icon(
                        if (state.isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                        contentDescription = "Toggle Layout"
                    )
                }

                OutlinedButton(
                    onClick = onRequestImportPdf,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Import PDF")
                }

                Button(
                    onClick = { showNewNotebookDialog = true },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("New Note")
                }
            }

            Spacer(Modifier.height(20.dp))

            val headerTitle = when (state.librarySection) {
                LibrarySection.ALL_NOTES -> "All Notebooks"
                LibrarySection.RECENT -> "Recent"
                LibrarySection.FAVORITES -> "Favorites"
                LibrarySection.FOLDER -> state.folders.find { it.id == state.selectedFolderId }?.name ?: "Folder"
                LibrarySection.TRASH -> "Trash"
            }

            Text(
                text = headerTitle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(16.dp))

            if (filteredNotebooks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "No notebooks here yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        if (state.librarySection != LibrarySection.TRASH) {
                            Button(onClick = { showNewNotebookDialog = true }) {
                                Text("Create Your First Notebook")
                            }
                        }
                    }
                }
            } else if (state.isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 195.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(filteredNotebooks, key = { it.id }) { notebook ->
                        NotebookGridCard(
                            notebook = notebook,
                            inTrash = state.librarySection == LibrarySection.TRASH,
                            onOpen = { viewModel.openNotebook(notebook.id) },
                            onToggleFavorite = { viewModel.toggleFavoriteNotebook(notebook.id) },
                            onTrash = { viewModel.moveNotebookToTrash(notebook.id) },
                            onRestore = { viewModel.restoreNotebookFromTrash(notebook.id) },
                            onDeleteForever = { viewModel.deleteNotebookPermanently(notebook.id) }
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(filteredNotebooks, key = { it.id }) { notebook ->
                        NotebookListRow(
                            notebook = notebook,
                            inTrash = state.librarySection == LibrarySection.TRASH,
                            onOpen = { viewModel.openNotebook(notebook.id) },
                            onToggleFavorite = { viewModel.toggleFavoriteNotebook(notebook.id) },
                            onTrash = { viewModel.moveNotebookToTrash(notebook.id) },
                            onRestore = { viewModel.restoreNotebookFromTrash(notebook.id) },
                            onDeleteForever = { viewModel.deleteNotebookPermanently(notebook.id) }
                        )
                    }
                }
            }
        }
    }

    if (showNewNotebookDialog) {
        CreateNotebookDialog(
            folders = state.folders,
            defaultFolderId = state.selectedFolderId,
            onDismiss = { showNewNotebookDialog = false },
            onCreate = { title, folderId, cover, accent, paperColor, template, orient, infinite ->
                viewModel.createNotebook(title, folderId, cover, accent, paperColor, template, orient, infinite)
                showNewNotebookDialog = false
            }
        )
    }

    if (showNewFolderDialog) {
        CreateFolderDialog(
            onDismiss = { showNewFolderDialog = false },
            onCreate = { name, color ->
                viewModel.createFolder(name, parentId = null, colorArgb = color)
                showNewFolderDialog = false
            }
        )
    }
}

@Composable
private fun SidebarItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    count: Int? = null,
    iconTint: Color? = null,
    onClick: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val fg = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = iconTint ?: fg,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (count != null && count > 0) {
            Text(
                "$count",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NotebookGridCard(
    notebook: Notebook,
    inTrash: Boolean,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTrash: () -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit
) {
    val dateText = remember(notebook.updatedAtMs) {
        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(notebook.updatedAtMs))
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !inTrash, onClick = onOpen),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Styled Notebook Cover
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.82f)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(notebook.coverColorArgb), Color(notebook.coverAccentArgb))
                        )
                    )
                    .padding(14.dp)
            ) {
                // Spine accent
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(6.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.28f))
                )

                // Top-right favorite / trash action
                Row(modifier = Modifier.align(Alignment.TopEnd)) {
                    if (!inTrash) {
                        IconButton(onClick = onToggleFavorite, modifier = Modifier.size(30.dp)) {
                            Icon(
                                if (notebook.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Favorite",
                                tint = if (notebook.isFavorite) Color(0xFFFACC15) else Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                }

                // Cover title label
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 14.dp, bottom = 6.dp)
                ) {
                    Text(
                        notebook.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${notebook.pages.size} page${if (notebook.pages.size == 1) "" else "s"}" +
                            if (notebook.isInfiniteCanvas) " • Infinite" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    dateText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (inTrash) {
                    Row {
                        IconButton(onClick = onRestore, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.RestoreFromTrash, contentDescription = "Restore", modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = onDeleteForever, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.DeleteForever, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        }
                    }
                } else {
                    IconButton(onClick = onTrash, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Move to Trash",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotebookListRow(
    notebook: Notebook,
    inTrash: Boolean,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTrash: () -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !inTrash, onClick = onOpen)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp, 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(notebook.coverColorArgb))
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(notebook.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "${notebook.pages.size} pages • ${notebook.pages.firstOrNull()?.template?.label ?: "Blank"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (inTrash) {
                IconButton(onClick = onRestore) { Icon(Icons.Default.RestoreFromTrash, contentDescription = "Restore") }
                IconButton(onClick = onDeleteForever) { Icon(Icons.Default.DeleteForever, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error) }
            } else {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (notebook.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Favorite",
                        tint = if (notebook.isFavorite) Color(0xFFFACC15) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onTrash) { Icon(Icons.Default.Delete, contentDescription = "Trash") }
            }
        }
    }
}

@Composable
private fun CreateNotebookDialog(
    folders: List<Folder>,
    defaultFolderId: String?,
    onDismiss: () -> Unit,
    onCreate: (String, String?, Long, Long, PaperColor, PaperTemplate, PageOrientation, Boolean) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedCover by remember { mutableStateOf(CoverPresets.first()) }
    var paperColor by remember { mutableStateOf(PaperColor.WHITE) }
    var template by remember { mutableStateOf(PaperTemplate.RULED) }
    var orientation by remember { mutableStateOf(PageOrientation.PORTRAIT) }
    var infiniteCanvas by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Notebook", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Notebook Title") },
                    placeholder = { Text("e.g., Physics Lecture Notes") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Cover Color", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CoverPresets.forEach { pair ->
                        val isSel = selectedCover == pair
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(pair.first))
                                .border(
                                    width = if (isSel) 3.dp else 0.dp,
                                    color = if (isSel) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedCover = pair }
                        )
                    }
                }

                Text("Paper Color", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaperColor.entries.forEach { pc ->
                        FilterChip(
                            selected = paperColor == pc,
                            onClick = { paperColor = pc },
                            label = { Text(pc.label) }
                        )
                    }
                }

                Text("Paper Template", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PaperTemplate.entries) { tpl ->
                        FilterChip(
                            selected = template == tpl,
                            onClick = { template = tpl },
                            label = { Text(tpl.label) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PageOrientation.entries.forEach { orient ->
                        FilterChip(
                            selected = orientation == orient,
                            onClick = { orientation = orient },
                            label = { Text(orient.label) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Infinite Canvas Mode", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = infiniteCanvas, onCheckedChange = { infiniteCanvas = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(
                        title,
                        defaultFolderId,
                        selectedCover.first,
                        selectedCover.second,
                        paperColor,
                        template,
                        orientation,
                        infiniteCanvas
                    )
                }
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onCreate: (String, Long) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(0xFF4F46E5L) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Folder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CoverPresets.forEach { (c, _) ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(
                                    if (color == c) 2.dp else 0.dp,
                                    MaterialTheme.colorScheme.onSurface,
                                    CircleShape
                                )
                                .clickable { color = c }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onCreate(name, color) }) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
