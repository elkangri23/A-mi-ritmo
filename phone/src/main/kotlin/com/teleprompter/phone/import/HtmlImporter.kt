package com.teleprompter.phone.`import`

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.teleprompter.shared.model.PreparedScript
import org.jsoup.Jsoup
import java.io.IOException

class HtmlImporter(private val resolver: ContentResolver) {
    fun import(uri: Uri): PreparedScript {
        val displayName = resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "Guion.html"
        if (!displayName.endsWith(".html", ignoreCase = true) &&
            !displayName.endsWith(".htm", ignoreCase = true)
        ) {
            throw IOException("El importador HTML acepta archivos .htm y .html.")
        }

        val body = resolver.openInputStream(uri)?.use { input ->
            Jsoup.parse(input, null, "").body()
        } ?: throw IOException("No se pudo abrir el archivo HTML.")
        val title = displayName.removeSuffixIgnoreCase(".html").removeSuffixIgnoreCase(".htm")
        return StructuredHtmlMapper.toPreparedScript(body, title)
    }

    private fun String.removeSuffixIgnoreCase(suffix: String): String =
        if (endsWith(suffix, ignoreCase = true)) dropLast(suffix.length) else this
}
