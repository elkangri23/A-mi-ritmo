package com.teleprompter.phone.`import`

import java.io.File
import java.io.IOException

internal fun createImportScratchFile(cacheDir: File, prefix: String, suffix: String, format: String): File =
    try {
        removeStaleImportScratchFiles(cacheDir)
        File.createTempFile(prefix, suffix, cacheDir)
    } catch (error: IOException) {
        throw IOException(
            "No se pudo crear el archivo temporal para importar $format. Comprueba el espacio disponible e inténtalo de nuevo.",
            error,
        )
    }

private fun removeStaleImportScratchFiles(cacheDir: File) {
    val staleBefore = System.currentTimeMillis() - STALE_AFTER_MILLIS
    cacheDir.listFiles()
        ?.filter { file ->
            file.name.startsWith("pdf-import-") || file.name.startsWith("odt-import-")
        }
        ?.filter { it.lastModified() in 1 until staleBefore }
        ?.forEach { runCatching { it.delete() } }
}

internal fun insufficientScratchSpace(format: String, error: Throwable): IOException? {
    if (!isInsufficientSpace(error)) return null
    return IOException(
        "No hay espacio temporal suficiente para importar $format. Libera espacio en el teléfono e inténtalo de nuevo.",
        error,
    )
}

internal fun insufficientStorageMessage(error: Throwable): String? =
    if (isInsufficientSpace(error)) {
        "No hay espacio disponible para completar esta operación. Libera espacio en el teléfono e inténtalo de nuevo."
    } else {
        null
    }

private fun isInsufficientSpace(error: Throwable): Boolean {
    val markers = listOf(
        "enospc",
        "no space left",
        "not enough space",
        "insufficient storage",
        "disk full",
        "errno 28",
        "no hay espacio",
        "espacio insuficiente",
    )
    var current: Throwable? = error
    while (current != null) {
        val message = current.message?.lowercase().orEmpty()
        if (markers.any(message::contains)) return true
        current = current.cause
    }
    return false
}

private const val STALE_AFTER_MILLIS = 24L * 60 * 60 * 1000
