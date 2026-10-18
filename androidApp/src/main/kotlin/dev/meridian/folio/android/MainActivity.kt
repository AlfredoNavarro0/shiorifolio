package dev.meridian.folio.android

import android.os.Bundle
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
import dev.meridian.folio.ui.MeridianFolioApp
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val statePath = File(filesDir, "meridian_folio_state.json").absolutePath
        val repository = FolioRepository(storageFilePath = statePath)

        setContent {
            var isFullscreen by remember { mutableStateOf(false) }

            MeridianFolioApp(
                repository = repository,
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

