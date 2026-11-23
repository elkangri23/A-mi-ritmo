package com.teleprompter.phone.`import`

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.teleprompter.shared.model.ContentBlock
import com.teleprompter.shared.model.InlineRun
import com.teleprompter.shared.model.PreparedScript
import com.teleprompter.shared.model.ReaderAppearance
import com.teleprompter.shared.model.ScriptSection
import org.commonmark.Extension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

class MarkdownImporter(private val resolver: ContentResolver) {
    fun import(uri: Uri): PreparedScript {
        val displayName = queryDisplayName(uri)
        if (!displayName.endsWith(".md", ignoreCase = true)) {
            throw IOException("El importador Markdown acepta archivos .md.")
        }
        val extensions = listOf<Extension>(TablesExtension.create())
        val document = resolver.openInputStream(uri)?.use { input ->
            InputStreamReader(input, StandardCharsets.UTF_8).buffered().use { reader ->
                reader.mark(1)
                if (reader.read() != '\uFEFF'.code) reader.reset()
                Parser.builder().extensions(extensions).build().parseReader(reader)
            }
        } ?: throw IOException("No se pudo abrir el archivo Markdown.")
        val html = HtmlRenderer.builder().extensions(extensions).build().render(document)
        return StructuredHtmlMapper.toPreparedScript(html, displayName.removeSuffixIgnoreCase(".md"))
    }

    private fun queryDisplayName(uri: Uri): String = resolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: "Guion.md"

    private fun String.removeSuffixIgnoreCase(suffix: String): String =
        if (endsWith(suffix, ignoreCase = true)) dropLast(suffix.length) else this
}

internal object StructuredHtmlMapper {
    fun toPreparedScript(html: String, title: String): PreparedScript =
        toPreparedScript(org.jsoup.Jsoup.parseBodyFragment(html).body(), title)

    fun toPreparedScript(body: org.jsoup.nodes.Element, title: String): PreparedScript {
        val sections = mutableListOf<ScriptSection>()
        var currentSubtitle: String? = null
        var currentBlocks = mutableListOf<ContentBlock>()

        fun flushSection() {
            if (currentSubtitle != null || currentBlocks.isNotEmpty()) {
                sections += ScriptSection(
                    id = java.util.UUID.randomUUID().toString(),
                    subtitle = currentSubtitle,
                    blocks = currentBlocks,
                )
                currentSubtitle = null
                currentBlocks = mutableListOf()
            }
        }

        fun addParagraph(element: org.jsoup.nodes.Element) {
            val runs = inlineRuns(element)
            if (runs.any { it.text.isNotBlank() }) {
                currentBlocks += ContentBlock.Paragraph(java.util.UUID.randomUUID().toString(), runs)
            }
        }

        fun addParagraph(nodes: List<org.jsoup.nodes.Node>) {
            val runs = inlineRuns(nodes)
            if (runs.any { it.text.isNotBlank() }) {
                currentBlocks += ContentBlock.Paragraph(java.util.UUID.randomUUID().toString(), runs)
            }
        }

        fun addList(element: org.jsoup.nodes.Element) {
            val ordered = element.tagName() == "ol"
            var items = mutableListOf<List<InlineRun>>()

            fun flushItems() {
                if (items.isNotEmpty()) {
                    currentBlocks += ContentBlock.ListBlock(
                        id = java.util.UUID.randomUUID().toString(),
                        ordered = ordered,
                        items = items,
                    )
                    items = mutableListOf()
                }
            }

            element.children().filter { it.tagName() == "li" }.forEach { item ->
                val runs = inlineRunsExcluding(item, LIST_TAGS)
                if (runs.any { it.text.isNotBlank() }) items += runs

                val nestedLists = item.select("ul, ol").filter { nestedList ->
                    nestedList.parents().firstOrNull { it.tagName() in LIST_TAGS } === element
                }
                if (nestedLists.isNotEmpty()) {
                    flushItems()
                    nestedLists.forEach(::addList)
                }
            }
            flushItems()
        }

        fun addTable(element: org.jsoup.nodes.Element) {
            val rows = element.select("tr").filter { row ->
                row.parents().firstOrNull { it.tagName() == "table" } === element
            }
            if (rows.isEmpty()) return
            val headerRow = rows.firstOrNull { row -> row.children().any { it.tagName() == "th" } }
                ?: rows.firstOrNull()
                ?: return
            val rawHeader = headerRow.children().map(::inlineRuns)
            val rawRows = rows.filter { it !== headerRow }.map { row ->
                row.children().map(::inlineRuns)
            }
            val columnCount = maxOf(rawHeader.size, rawRows.maxOfOrNull { it.size } ?: 0)
            if (columnCount == 0) return
            val header = rawHeader + List((columnCount - rawHeader.size).coerceAtLeast(0)) { emptyList() }
            val normalizedRows = rawRows.map { row ->
                row + List((columnCount - row.size).coerceAtLeast(0)) { emptyList() }
            }
            currentBlocks += ContentBlock.Table(
                id = java.util.UUID.randomUUID().toString(),
                headerCells = header,
                rows = normalizedRows,
            )
        }

        lateinit var process: (org.jsoup.nodes.Element) -> Unit
        lateinit var processChildren: (org.jsoup.nodes.Element) -> Unit

        processChildren = { container ->
            val inlineNodes = mutableListOf<org.jsoup.nodes.Node>()
            fun flushInlineContent() {
                addParagraph(inlineNodes)
                inlineNodes.clear()
            }
            container.childNodes().forEach { node ->
                val childElement = node as? org.jsoup.nodes.Element
                if (childElement != null && childElement.tagName() in BLOCK_TAGS) {
                    flushInlineContent()
                    process(childElement)
                } else {
                    inlineNodes += node
                }
            }
            flushInlineContent()
        }

        process = { element ->
            when {
                element.tagName() in setOf("h1", "h2", "h3", "h4", "h5", "h6") -> {
                    flushSection()
                    currentSubtitle = element.text().trim().ifBlank { null }
                }
                element.tagName() == "p" -> addParagraph(element)
                element.tagName() == "ul" || element.tagName() == "ol" -> addList(element)
                element.tagName() == "table" -> addTable(element)
                element.tagName() == "pre" -> {
                    val text = element.text()
                    if (text.isNotBlank()) {
                        currentBlocks += ContentBlock.Paragraph(
                            java.util.UUID.randomUUID().toString(),
                            listOf(InlineRun(text)),
                        )
                    }
                }
                else -> processChildren(element)
            }
        }

        processChildren(body)
        flushSection()
        if (sections.isEmpty()) {
            throw IOException("El archivo no contiene texto que se pueda importar.")
        }

        return PreparedScript(
            schemaVersion = 1,
            scriptId = java.util.UUID.randomUUID().toString(),
            title = title.ifBlank { "Guion" },
            sections = sections,
            appearance = ReaderAppearance(),
        )
    }

    private fun inlineRuns(parent: org.jsoup.nodes.Node): List<InlineRun> {
        return inlineRuns(parent.childNodes())
    }

    private fun inlineRunsExcluding(parent: org.jsoup.nodes.Node, excludedTags: Set<String>): List<InlineRun> =
        inlineRuns(parent.childNodes(), excludedTags)

    private fun inlineRuns(nodes: List<org.jsoup.nodes.Node>): List<InlineRun> {
        return inlineRuns(nodes, emptySet())
    }

    private fun inlineRuns(nodes: List<org.jsoup.nodes.Node>, excludedTags: Set<String>): List<InlineRun> {
        val runs = mutableListOf<InlineRun>()

        fun append(text: String, bold: Boolean, italic: Boolean) {
            if (text.isEmpty()) return
            val previous = runs.lastOrNull()
            if (previous != null && previous.bold == bold && previous.italic == italic) {
                runs[runs.lastIndex] = previous.copy(text = previous.text + text)
            } else {
                runs += InlineRun(text, bold, italic)
            }
        }

        fun visit(node: org.jsoup.nodes.Node, bold: Boolean = false, italic: Boolean = false) {
            when (node) {
                is org.jsoup.nodes.TextNode -> append(node.wholeText, bold, italic)
                is org.jsoup.nodes.Element -> {
                    if (node.tagName() in excludedTags) return
                    when (node.tagName()) {
                        "script", "style" -> return
                        "br" -> append("\n", bold, italic)
                        "img" -> append(node.attr("alt"), bold, italic)
                        else -> {
                            val childBold = bold || node.tagName() in setOf("strong", "b")
                            val childItalic = italic || node.tagName() in setOf("em", "i")
                            node.childNodes().forEach { child -> visit(child, childBold, childItalic) }
                        }
                    }
                }
                else -> node.childNodes().forEach { child -> visit(child, bold, italic) }
            }
        }

        nodes.forEach { visit(it) }
        return runs
    }

    private val BLOCK_TAGS = setOf(
        "article", "blockquote", "dd", "div", "dl", "dt", "figcaption", "figure", "footer", "header", "hr",
        "h1", "h2", "h3", "h4", "h5", "h6", "main", "ol", "p", "pre", "section", "table", "ul",
    )
    private val LIST_TAGS = setOf("ol", "ul")
}
