package com.teleprompter.phone.`import`

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.teleprompter.shared.model.ContentBlock
import com.teleprompter.shared.model.InlineRun
import com.teleprompter.shared.model.PreparedScript
import com.teleprompter.shared.model.ReaderAppearance
import com.teleprompter.shared.model.ScriptSection
import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.extractor.WordExtractor
import org.apache.poi.openxml4j.util.ZipSecureFile
import org.apache.poi.xwpf.usermodel.IBodyElement
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFTable
import org.apache.poi.xwpf.usermodel.XWPFTableCell
import org.apache.poi.xwpf.usermodel.XWPFRun
import org.apache.poi.xwpf.usermodel.XWPFStyles
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.UUID

class WordImporter(private val resolver: ContentResolver) {
    fun import(uri: Uri): PreparedScript {
        val displayName = resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "Guion.docx"

        val extension = displayName.substringAfterLast('.', "").lowercase()
        val title = displayName.removeSuffixIgnoreCase(".$extension").ifBlank { "Guion" }
        return when (extension) {
            "docx" -> importDocx(uri, title)
            "doc" -> importDoc(uri, title)
            else -> throw IOException("El importador Word acepta archivos .doc y .docx.")
        }
    }

    private fun importDocx(uri: Uri, title: String): PreparedScript {
        configureDocxArchiveProtection()
        val input = resolver.openInputStream(uri) ?: throw IOException("No se pudo abrir el documento Word.")
        val sections = mutableListOf<ScriptSection>()
        var currentSubtitle: String? = null
        var currentBlocks = mutableListOf<ContentBlock>()
        var currentListItems = mutableListOf<List<InlineRun>>()
        var currentListNumberingId: java.math.BigInteger? = null
        var currentListLevel: java.math.BigInteger? = null
        var currentListOrdered = false

        fun flushList() {
            if (currentListItems.isNotEmpty()) {
                currentBlocks += ContentBlock.ListBlock(
                    id = UUID.randomUUID().toString(),
                    ordered = currentListOrdered,
                    items = currentListItems,
                )
                currentListItems = mutableListOf()
            }
            currentListNumberingId = null
            currentListLevel = null
        }

        fun flushSection() {
            flushList()
            if (currentSubtitle != null || currentBlocks.isNotEmpty()) {
                sections += ScriptSection(UUID.randomUUID().toString(), currentSubtitle, currentBlocks)
                currentSubtitle = null
                currentBlocks = mutableListOf()
            }
        }

        try {
            XWPFDocument(input).use { document ->
                document.bodyElements.forEach { element: IBodyElement ->
                    when (element) {
                        is XWPFParagraph -> {
                            val runs = paragraphRuns(element)
                            val paragraphText = runs.joinToString(separator = "") { it.text }
                            if (isHeading(element, document.styles) && paragraphText.isNotBlank()) {
                                flushList()
                                flushSection()
                                currentSubtitle = paragraphText.trim()
                            } else if (element.numID != null && runs.isNotEmpty()) {
                                val numberingId = element.numID
                                val listLevel = element.numIlvl ?: java.math.BigInteger.ZERO
                                val ordered = !element.numFmt.equals("bullet", ignoreCase = true)
                                if (currentListItems.isNotEmpty() &&
                                    (currentListNumberingId != numberingId || currentListLevel != listLevel || currentListOrdered != ordered)
                                ) {
                                    flushList()
                                }
                                currentListNumberingId = numberingId
                                currentListLevel = listLevel
                                currentListOrdered = ordered
                                currentListItems += runs
                            } else if (runs.isNotEmpty()) {
                                flushList()
                                currentBlocks += ContentBlock.Paragraph(UUID.randomUUID().toString(), runs)
                            } else {
                                flushList()
                            }
                        }
                        is XWPFTable -> {
                            flushList()
                            addTable(element, currentBlocks)
                        }
                    }
                }
            }
        } catch (error: Exception) {
            throw IOException("No se pudo extraer texto del DOCX. Comprueba que no esté dañado, protegido ni supere los límites de seguridad del contenido comprimido.", error)
        } finally {
            input.close()
        }
        flushSection()
        return preparedScript(title, sections)
    }

    private fun importDoc(uri: Uri, title: String): PreparedScript {
        val input = resolver.openInputStream(uri) ?: throw IOException("No se pudo abrir el documento Word.")
        val text = try {
            HWPFDocument(input).use { document ->
                WordExtractor(document).use { extractor -> extractor.text }
            }
        } catch (error: Exception) {
            throw IOException("No se pudo extraer texto del documento DOC.", error)
        } finally {
            input.close()
        }

        val blocks = blocksFromExtractedText(text)
        return preparedScript(title, listOf(ScriptSection(UUID.randomUUID().toString(), blocks = blocks)))
    }

    private fun blocksFromExtractedText(text: String): List<ContentBlock> {
        val blocks = mutableListOf<ContentBlock>()
        val paragraph = StringBuilder()

        fun flushParagraph() {
            val value = paragraph.toString().trim()
            if (value.isNotEmpty()) {
                blocks += ContentBlock.Paragraph(
                    UUID.randomUUID().toString(),
                    listOf(InlineRun(value)),
                )
            }
            paragraph.setLength(0)
        }

        text.forEach { character ->
            if (character == '\r' || character == '\n') {
                flushParagraph()
            } else {
                paragraph.append(character)
            }
        }
        flushParagraph()
        return blocks
    }

    private fun addTable(table: XWPFTable, blocks: MutableList<ContentBlock>) {
        val rows = table.rows.map { row -> row.tableCells.map(::cellRuns) }
        val header = rows.firstOrNull()?.takeIf { it.isNotEmpty() } ?: return
        val columnCount = maxOf(header.size, rows.maxOfOrNull { it.size } ?: 0)
        val normalizedHeader = header + List((columnCount - header.size).coerceAtLeast(0)) { emptyList() }
        val normalizedRows = rows.drop(1).map { row ->
            row + List((columnCount - row.size).coerceAtLeast(0)) { emptyList() }
        }
        blocks += ContentBlock.Table(
            id = UUID.randomUUID().toString(),
            headerCells = normalizedHeader,
            rows = normalizedRows,
        )
    }

    private fun cellRuns(cell: XWPFTableCell): List<InlineRun> {
        val runs = cell.paragraphs.flatMap(::paragraphRuns)
        return runs.ifEmpty { cell.text.takeIf { it.isNotBlank() }?.let { listOf(InlineRun(it)) } ?: emptyList() }
    }

    private fun paragraphRuns(paragraph: XWPFParagraph): List<InlineRun> = paragraph.runs.mapNotNull { run ->
        run.getText(0)?.takeIf { it.isNotEmpty() }?.let { text ->
            InlineRun(text, bold = run.isBold, italic = run.isItalic)
        }
    }

    private fun isHeading(paragraph: XWPFParagraph, styles: XWPFStyles?): Boolean {
        val styleId = paragraph.styleID.orEmpty()
        val styleName = styles?.getStyle(styleId)?.name.orEmpty()
        return listOf(styleId, styleName).any { style ->
            val normalized = Normalizer.normalize(style, Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .lowercase()
                .replace(Regex("[^a-z0-9]"), "")
            normalized.startsWith("heading") ||
                normalized.startsWith("title") ||
                normalized.startsWith("subtitle") ||
                normalized.startsWith("titulo") ||
                normalized.startsWith("ttulo")
        }
    }

    private fun preparedScript(title: String, sections: List<ScriptSection>): PreparedScript {
        if (sections.none { section ->
                section.blocks.any { block ->
                    when (block) {
                        is ContentBlock.Paragraph -> block.runs.any { it.text.isNotBlank() }
                        is ContentBlock.ListBlock -> block.items.flatten().any { it.text.isNotBlank() }
                        is ContentBlock.Table -> (block.headerCells.flatten() + block.rows.flatten().flatten()).any { it.text.isNotBlank() }
                        is ContentBlock.Cue -> false
                    }
                }
            }
        ) {
            throw IOException("El documento Word no contiene texto para importar.")
        }
        return PreparedScript(
            schemaVersion = 1,
            scriptId = UUID.randomUUID().toString(),
            title = title,
            sections = sections,
            appearance = ReaderAppearance(),
        )
    }

    private fun String.removeSuffixIgnoreCase(suffix: String): String =
        if (suffix.isNotEmpty() && endsWith(suffix, ignoreCase = true)) dropLast(suffix.length) else this

    private fun configureDocxArchiveProtection() {
        try {
            ZipSecureFile.setMinInflateRatio(MIN_DOCX_INFLATE_RATIO)
            ZipSecureFile.setMaxEntrySize(MAX_DOCX_ENTRY_BYTES)
        } catch (error: Exception) {
            throw IOException("No se pudo activar la protección contra archivos comprimidos peligrosos.", error)
        }
    }

    private companion object {
        const val MIN_DOCX_INFLATE_RATIO = 0.01
        const val MAX_DOCX_ENTRY_BYTES = 64L * 1024 * 1024
    }
}
