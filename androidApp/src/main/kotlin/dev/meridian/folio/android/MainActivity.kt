package dev.meridian.folio.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.meridian.folio.core.FolioRepository
import dev.meridian.folio.ui.MeridianFolioApp
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val statePath = File(filesDir, "meridian_folio_state.json").absolutePath
        val repository = FolioRepository(storageFilePath = statePath)

        setContent {
            MeridianFolioApp(
                repository = repository,
            )
        }
    }
}
