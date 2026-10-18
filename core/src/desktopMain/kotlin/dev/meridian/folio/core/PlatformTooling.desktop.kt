package dev.meridian.folio.core

import java.util.concurrent.TimeUnit

actual object PlatformTooling {
    private var availabilityChecked = false
    private var available = false

    private fun epubcheckAvailable(): Boolean {
        if (!availabilityChecked) {
            availabilityChecked = true
            available = runCatching {
                val process = ProcessBuilder("epubcheck", "--version")
                    .redirectErrorStream(true)
                    .start()
                val finished = process.waitFor(20, TimeUnit.SECONDS)
                if (!finished) {
                    process.destroyForcibly()
                    false
                } else {
                    process.exitValue() == 0
                }
            }.getOrDefault(false)
        }
        return available
    }

    actual fun runEpubcheck(epubFilePath: String): String? {
        if (!epubcheckAvailable()) return null
        return runCatching {
            val process = ProcessBuilder("epubcheck", epubFilePath)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            if (!process.waitFor(90, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return null
            }
            output.trim().take(8000).ifBlank { null }
        }.getOrNull()
    }
}
