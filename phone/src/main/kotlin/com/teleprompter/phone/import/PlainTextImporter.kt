package com.teleprompter.phone.`import`

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.teleprompter.shared.model.ContentBlock
import com.teleprompter.shared.model.InlineRun
import com.teleprompter.shared.model.PreparedScript
import com.teleprompter.shared.model.ScriptSection
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.UUID

class PlainTextImporter(private val resolver: ContentResolver) {
    fun import(uri: Uri): PreparedScript {
        val displayName = resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "Guion.txt"

        if (!displayName.endsWith(".txt", ignoreCase = true)) {
            throw IOException("El importador de texto acepta archivos .txt.")
        }

        val blocks = mutableListOf<ContentBlock>()
        val currentParagraph = StringBuilder()
        fun flushParagraph() {
            if (currentParagraph.isNotEmpty()) {
                val paragraph = currentParagraph.toString().trim()
                if (paragraph.isNotEmpty()) {
                    blocks += ContentBlock.Paragraph(
                        id = UUID.randomUUID().toString(),
                        runs = listOf(InlineRun(paragraph)),
                    )
                }
                currentParagraph.setLength(0)
            }
        }
        val input = resolver.openInputStream(uri) ?: throw IOException("No se pudo abrir el archivo seleccionado.")
        input.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
            var firstLine = true
            lines.forEach { sourceLine ->
                val line = if (firstLine) {
                    firstLine = false
                    sourceLine.removePrefix("\uFEFF")
                } else sourceLine
                if (line.isBlank()) {
                    flushParagraph()
                } else {
                    if (currentParagraph.isNotEmpty()) currentParagraph.append('\n')
                    currentParagraph.append(line)
                }
            }
        }
        flushParagraph()

        if (blocks.isEmpty()) {
            throw IOException("El archivo no contiene texto para leer.")
        }

        val title = displayName.removeSuffixIgnoreCase(".txt").ifBlank { "Guion" }
        return PreparedScript(
            schemaVersion = 1,
            scriptId = UUID.randomUUID().toString(),
            title = title,
            sections = listOf(
                ScriptSection(
                    id = UUID.randomUUID().toString(),
                    blocks = blocks,
                ),
            ),
            appearance = com.teleprompter.shared.model.ReaderAppearance(),
        )
    }

    private fun String.removeSuffixIgnoreCase(suffix: String): String =
        if (endsWith(suffix, ignoreCase = true)) dropLast(suffix.length) else this
}
