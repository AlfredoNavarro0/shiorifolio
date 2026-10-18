package dev.meridian.folio.android

import android.os.Bundle
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.meridian.folio.core.FolioRepository
import dev.meridian.folio.core.PlatformLibraryIo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import dev.meridian.folio.ui.MeridianFolioApp
import java.io.File

class MainActivity : ComponentActivity() {
    private val libraryScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var repository: FolioRepository

    override fun onResume() {
        super.onResume()
        if (::repository.isInitialized) libraryScope.launch { repository.library.synchronizeFolders() }
    }

    override fun onDestroy() {
        libraryScope.cancel()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PlatformLibraryIo.initialize(this)
        val statePath = File(filesDir, "meridian_folio_state.json").absolutePath
        repository = FolioRepository(storageFilePath = statePath)

        setContent {
            var isFullscreen by remember { mutableStateOf(false) }
            var folderSelected by remember { mutableStateOf<((String) -> Unit)?>(null) }
            val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                if (uri != null) {
                    // Read access is sufficient; deletion separately asks the document provider.
                    runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
                        .recoverCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                        .onSuccess { folderSelected?.invoke(uri.toString()) }
                }
                folderSelected = null
            }

            MeridianFolioApp(
                repository = repository,
                onPickFolder = { callback -> folderSelected = callback; folderPicker.launch(null) },
                isWindowFullscreen = isFullscreen,
                onToggleWindowFullscreen = {
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    if (isFullscreen) {
                        controller.show(WindowInsetsCompat.Type.systemBars())
                        isFullscreen = false
                    } else {
                        controller.hide(WindowInsetsCompat.Type.systemBars())
                        isFullscreen = true
                    }
                },
            )
        }
    }
}

