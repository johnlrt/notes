package com.johnlrt.freenotes

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.OpenableColumns
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
            viewModel.importPdf(uri, name)
        }
    }

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* handled smoothly when recording starts */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FreeNotesTheme {
                val state by viewModel.uiState.collectAsState()
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
                            onExportedPdf = ::sharePdfFile
                        )
                    } else {
                        LibraryScreen(
                            state = state,
                            viewModel = viewModel,
                            onRequestImportPdf = {
                                pdfPickerLauncher.launch(arrayOf("application/pdf"))
                            }
                        )
                    }
                }
            }
        }
    }

    private fun queryDisplayName(uri: android.net.Uri): String? {
        return contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }

    private fun sharePdfFile(file: File) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share Exported PDF"))
    }
}
