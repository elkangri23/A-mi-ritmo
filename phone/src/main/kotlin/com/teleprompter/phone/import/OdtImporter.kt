package com.teleprompter.phone.`import`

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Xml
import com.teleprompter.shared.model.PreparedScript
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile
import java.util.UUID

/** Imports the readable structure from an OpenDocument Text package without retaining its source file. */
class OdtImporter(private val resolver: ContentResolver, private val cacheDir: File) {
    fun import(uri: Uri): PreparedScript {
        val displayName = resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } ?: "Guion.odt"
        if (!displayName.endsWith(".odt", ignoreCase = true)) {
            throw IOException("El importador ODT acepta archivos .odt.")
        }

        val archive = createImportScratchFile(cacheDir, "odt-import-", ".zip", "el ODT")
        val entries = linkedMapOf<String, ByteArray>()
        try {
            val stream = resolver.openInputStream(uri) ?: throw IOException("No se pudo abrir el archivo ODT.")
            stream.use { input -> FileOutputStream(archive).use(input::copyTo) }
            ZipFile(archive).use { zip ->
                for (name in listOf("styles.xml", "content.xml")) {
                    val entry = zip.getEntry(name) ?: continue
                    if (entry.size < 0L || entry.compressedSize < 0L) {
                        throw IOException("El ODT no ofrece tamaños verificables para proteger su contenido comprimido.")
                    }
                    val ratio = if (entry.size == 0L) 1.0 else entry.compressedSize.toDouble() / entry.size
                    val byteLimit = if (name == "content.xml") MAX_CONTENT_XML_BYTES else MAX_STYLES_XML_BYTES
                    if (ratio < MIN_INFLATE_RATIO || entry.size > byteLimit) {
                        throw IOException("El ODT supera los límites de seguridad del texto expandido. Guarda una versión más pequeña o sin compresión extrema.")
                    }
                    zip.getInputStream(entry).use { input -> entries[name] = input.readBounded(byteLimit) }
                }
            }
        } catch (error: IOException) {
            throw insufficientScratchSpace("el ODT", error) ?: error
        } catch (error: Exception) {
            val storageError = insufficientScratchSpace("el ODT", error)
            if (storageError != null) throw storageError
            throw IOException("El archivo ODT no se pudo leer.", error)
        } finally {
            archive.delete()
        }
        val content = entries["content.xml"] ?: throw IOException("El ODT no contiene texto principal.")
        val styles = buildMap {
            entries["styles.xml"]?.let { putAll(readStyles(it)) }
            putAll(readStyles(content))
        }
        val html = try {
            convertContent(content, styles)
        } catch (error: Exception) {
            throw IOException("No se pudo extraer el texto del ODT.", error)
        }
        val title = displayName.dropLast(".odt".length).ifBlank { "Guion" }
        return StructuredHtmlMapper.toPreparedScript(html, title)
    }

    private fun readStyles(xml: ByteArray): Map<String, TextStyle> {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
            setInput(xml.inputStream(), "UTF-8")
        }
        val styles = mutableMapOf<String, TextStyle>()
        var styleName: String? = null
        var bold = false
        var italic = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.localName() == "style") {
                styleName = parser.getAttributeValue("urn:oasis:names:tc:opendocument:xmlns:style:1.0", "name")
                bold = false
                italic = false
            } else if (event == XmlPullParser.START_TAG && parser.localName() == "text-properties" && styleName != null) {
                bold = parser.getAttributeValue("urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0", "font-weight")
                    ?.let { it.equals("bold", true) || it == "700" || it == "800" || it == "900" } == true
                italic = parser.getAttributeValue("urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0", "font-style")
                    ?.let { it.equals("italic", true) || it.equals("oblique", true) } == true
            } else if (event == XmlPullParser.END_TAG && parser.localName() == "style") {
                styleName?.let { styles[it] = TextStyle(bold, italic) }
                styleName = null
            }
            event = parser.next()
        }
        return styles
    }

    private fun convertContent(xml: ByteArray, styles: Map<String, TextStyle>): String {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
            setInput(xml.inputStream(), "UTF-8")
        }
        val html = StringBuilder()
        val openTags = ArrayDeque<String>()
        var inText = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = parser.localName()
                    val namespace = parser.namespace.orEmpty()
                    if (name == "text" && namespace.endsWith(":office:1.0")) inText = true
                    if (inText) {
                        val tag = when (name) {
                            "h" -> "h2"
                            "p" -> "p"
                            "list" -> if (parser.getAttributeValue("urn:oasis:names:tc:opendocument:xmlns:text:1.0", "style-name")
                                    ?.contains("number", ignoreCase = true) == true) "ol" else "ul"
                            "list-item" -> "li"
                            "table" -> "table"
                            "table-row" -> "tr"
                            "table-cell", "covered-table-cell" -> "td"
                            "line-break" -> "br"
                            "tab" -> null
                            "s" -> null
                            "span" -> styleTag(parser, styles)
                            else -> null
                        }
                        if (tag != null) {
                            html.append('<').append(tag).append('>')
                            if (tag != "br") openTags.addLast(tag)
                        } else if (name == "tab") {
                            html.append("&#9;")
                        } else if (name == "s") {
                            val count = parser.getAttributeValue("urn:oasis:names:tc:opendocument:xmlns:text:1.0", "c")?.toIntOrNull()?.coerceIn(1, 100) ?: 1
                            repeat(count) { html.append("&#160;") }
                        }
                    }
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> if (inText) html.append(escapeHtml(parser.text))
                XmlPullParser.END_TAG -> {
                    if (inText) {
                        val name = parser.localName()
                        val tag = when (name) {
                            "h" -> "h2"
                            "p" -> "p"
                            "list" -> openTags.lastOrNull()?.takeIf { it == "ol" } ?: "ul"
                            "list-item" -> "li"
                            "table" -> "table"
                            "table-row" -> "tr"
                            "table-cell", "covered-table-cell" -> "td"
                            "span" -> openTags.lastOrNull()?.takeIf { it in setOf("strong", "em") }
                            else -> null
                        }
                        if (tag != null && openTags.lastOrNull() == tag) {
                            html.append("</").append(openTags.removeLast()).append('>')
                        }
                        if (name == "text" && parser.namespace.orEmpty().endsWith(":office:1.0")) inText = false
                    }
                }
            }
            event = parser.next()
        }
        return html.toString()
    }

    private fun styleTag(parser: XmlPullParser, styles: Map<String, TextStyle>): String? {
        val name = parser.getAttributeValue("urn:oasis:names:tc:opendocument:xmlns:text:1.0", "style-name")
        val style = name?.let(styles::get) ?: return null
        return when {
            style.bold -> "strong"
            style.italic -> "em"
            else -> null
        }
    }

    private fun XmlPullParser.localName(): String = name.substringAfter(':')

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private data class TextStyle(val bold: Boolean, val italic: Boolean)

    private fun java.io.InputStream.readBounded(limit: Long): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            if (total > limit) throw IOException("El texto XML expandido del ODT excede el límite de memoria disponible.")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private companion object {
        const val MIN_INFLATE_RATIO = 0.01
        const val MAX_CONTENT_XML_BYTES = 32L * 1024 * 1024
        const val MAX_STYLES_XML_BYTES = 8L * 1024 * 1024
    }
}
