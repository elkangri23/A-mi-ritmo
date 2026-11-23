package com.teleprompter.phone.`import`

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.teleprompter.shared.model.ContentBlock
import com.teleprompter.shared.model.InlineRun
import com.teleprompter.shared.model.PreparedScript
import com.teleprompter.shared.model.ReaderAppearance
import com.teleprompter.shared.model.ScriptSection
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.UUID

class PdfImporter(
    context: Context,
    private val resolver: ContentResolver = context.contentResolver,
) {
    private val appContext = context.applicationContext

    fun import(uri: Uri): PreparedScript {
        val displayName = resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "Guion.pdf"
        if (!displayName.endsWith(".pdf", ignoreCase = true)) {
            throw IOException("El importador PDF acepta archivos .pdf.")
        }

        val extractedText = createImportScratchFile(appContext.cacheDir, "pdf-import-", ".txt", "el PDF")
        val blocks = try {
            val input = resolver.openInputStream(uri) ?: throw IOException("No se pudo abrir el PDF.")
            input.use {
                PDFBoxResourceLoader.init(appContext)
                val memoryUsage = MemoryUsageSetting.setupTempFileOnly().setTempDir(appContext.cacheDir)
                PDDocument.load(input, memoryUsage).use { document ->
                    OutputStreamWriter(FileOutputStream(extractedText), StandardCharsets.UTF_8).buffered().use { writer ->
                        PDFTextStripper().apply { setSortByPosition(true) }.writeText(document, writer)
                    }
                }
            }
            readParagraphBlocks(extractedText)
        } catch (error: Exception) {
            val storageError = insufficientScratchSpace("el PDF", error)
            if (storageError != null) throw storageError
            if (error is IOException && error.message?.startsWith("No se pudo crear el archivo temporal") == true) {
                throw error
            }
            throw IOException("No se pudo extraer texto del PDF. Comprueba que no esté dañado o protegido.", error)
        } finally {
            extractedText.delete()
        }
        if (blocks.isEmpty()) {
            throw IOException("El PDF no contiene texto seleccionable. Los PDF escaneados necesitan OCR, que no está disponible.")
        }

        val title = displayName.removeSuffixIgnoreCase(".pdf").ifBlank { "Guion" }
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
            appearance = ReaderAppearance(),
        )
    }

    private fun String.removeSuffixIgnoreCase(suffix: String): String =
        if (endsWith(suffix, ignoreCase = true)) dropLast(suffix.length) else this

    private fun readParagraphBlocks(file: File): List<ContentBlock> {
        val blocks = mutableListOf<ContentBlock>()
        val currentParagraph = StringBuilder()

        fun flushParagraph() {
            val paragraph = currentParagraph.toString().trim()
            if (paragraph.isNotEmpty()) {
                blocks += ContentBlock.Paragraph(
                    id = UUID.randomUUID().toString(),
                    runs = listOf(InlineRun(paragraph)),
                )
            }
            currentParagraph.setLength(0)
        }

        file.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
            lines.forEach { line ->
                if (line.all { it == ' ' || it == '\t' }) {
                    if (currentParagraph.isNotEmpty()) {
                        flushParagraph()
                    }
                } else {
                    if (currentParagraph.isNotEmpty()) currentParagraph.append('\n')
                    currentParagraph.append(line)
                }
            }
        }
        if (currentParagraph.isNotEmpty()) flushParagraph()
        return blocks
    }
}
