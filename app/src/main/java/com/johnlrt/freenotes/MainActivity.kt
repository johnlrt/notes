package com.johnlrt.freenotes

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.FileProvider
import com.johnlrt.freenotes.ui.MainViewModel
import com.johnlrt.freenotes.ui.editor.EditorScreen
import com.johnlrt.freenotes.ui.library.LibraryScreen
import com.johnlrt.freenotes.ui.theme.FreeNotesTheme
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val pdfPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val name = queryDisplayName(uri) ?: "Imported PDF"
            if (name.endsWith(".json", ignoreCase = true)) {
                viewModel.importBackup(uri)
            } else {
                viewModel.importPdf(uri, name)
            }
        }
    }

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.insertImageFromUri(uri)
        }
    }

    private val videoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.openPipVideo(uri)
        }
    }

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* handled when recording starts */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        enableHighRefreshRate()

        setContent {
            val state by viewModel.uiState.collectAsState()
            var showSettings by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            val maxDisplayHz = androidx.compose.runtime.remember { queryMaxRefreshRateHz() }

            androidx.compose.runtime.LaunchedEffect(state.settings.highRefreshRate120Hz) {
                enableHighRefreshRate(state.settings.highRefreshRate120Hz)
            }

            FreeNotesTheme(
                themeMode = state.settings.themeMode,
                accentPalette = state.settings.accentPalette
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                ) {
                    if (state.activeNotebook != null) {
                        EditorScreen(
                            state = state,
                            viewModel = viewModel,
                            onRequestMicPermission = {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            onRequestPickImage = {
                                imagePickerLauncher.launch("image/*")
                            },
                            onRequestPickVideo = {
                                videoPickerLauncher.launch("video/*")
                            },
                            onOpenSettings = { showSettings = true },
                            onExportedFile = ::shareFile
                        )
                    } else {
                        LibraryScreen(
                            state = state,
                            viewModel = viewModel,
                            onRequestImportPdf = {
                                pdfPickerLauncher.launch(arrayOf("application/pdf", "application/json"))
                            },
                            onOpenSettings = { showSettings = true }
                        )
                    }

                    if (showSettings) {
                        com.johnlrt.freenotes.ui.SettingsDialog(
                            settings = state.settings,
                            displayRefreshHz = maxDisplayHz,
                            onUpdateSettings = viewModel::updateSettings,
                            onDismiss = { showSettings = false }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        enableHighRefreshRate(viewModel.uiState.value.settings.highRefreshRate120Hz)
    }

    @Suppress("DEPRECATION")
    private fun queryMaxRefreshRateHz(): Int {
        val currentDisplay: Display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display ?: windowManager.defaultDisplay
        } else {
            windowManager.defaultDisplay
        } ?: return 60
        return currentDisplay.supportedModes.maxOfOrNull { it.refreshRate }?.toInt() ?: 60
    }

    /**
     * Locks the window to the tablet's highest supported refresh rate (120Hz / 144Hz / 90Hz)
     * at native resolution and enables Android 14+ touch frame-rate boost for zero-lag inking.
     */
    @Suppress("DEPRECATION")
    private fun enableHighRefreshRate(enabled: Boolean = true) {
        val currentDisplay: Display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display ?: windowManager.defaultDisplay
        } else {
            windowManager.defaultDisplay
        } ?: return

        val currentMode = currentDisplay.mode
        val targetMode = if (enabled) {
            currentDisplay.supportedModes
                .filter {
                    it.physicalWidth == currentMode.physicalWidth &&
                        it.physicalHeight == currentMode.physicalHeight
                }
                .maxByOrNull { it.refreshRate }
                ?: currentMode
        } else {
            currentDisplay.supportedModes
                .filter {
                    it.physicalWidth == currentMode.physicalWidth &&
                        it.physicalHeight == currentMode.physicalHeight
                }
                .minByOrNull { it.refreshRate }
                ?: currentMode
        }

        val params = window.attributes
        if (params.preferredDisplayModeId != targetMode.modeId || params.preferredRefreshRate != targetMode.refreshRate) {
            params.preferredDisplayModeId = targetMode.modeId
            params.preferredRefreshRate = targetMode.refreshRate
            window.attributes = params
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            window.frameRateBoostOnTouchEnabled = enabled
        }
    }

    private fun queryDisplayName(uri: android.net.Uri): String? {
        return contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }

    private fun shareFile(file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share Exported File"))
    }
}
