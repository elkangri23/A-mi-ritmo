package com.teleprompter.phone.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.teleprompter.phone.`import`.DocumentPicker
import com.teleprompter.phone.`import`.PlainTextImporter
import com.teleprompter.phone.`import`.MarkdownImporter
import com.teleprompter.phone.`import`.HtmlImporter
import com.teleprompter.phone.`import`.PdfImporter
import com.teleprompter.phone.`import`.WordImporter
import com.teleprompter.phone.`import`.OdtImporter
import com.teleprompter.shared.model.PreparedScript
import com.teleprompter.shared.serialization.ScriptJsonCodec
import java.io.IOException
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

data class ScriptFile(
    val uri: Uri,
    val displayName: String,
    val script: PreparedScript,
)

data class ScriptLibrarySnapshot(
    val scripts: List<ScriptFile>,
    val unreadableFileCount: Int,
)

class ScriptFileRepository(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun savedLibraryTree(): Uri? = preferences.getString(KEY_LIBRARY_TREE_URI, null)?.let(Uri::parse)

    fun rememberLibraryTree(uri: Uri) {
        DocumentPicker.requireLocalLibraryTree(uri)
        try {
            resolver.takePersistableUriPermission(uri, DocumentPicker.TREE_PERMISSION_FLAGS)
        } catch (error: SecurityException) {
            throw IOException("La carpeta seleccionada no concedió acceso de lectura y escritura.", error)
        }
        preferences.edit().putString(KEY_LIBRARY_TREE_URI, uri.toString()).apply()
    }

    fun loadLibrary(): ScriptLibrarySnapshot {
        val treeUri = savedLibraryTree() ?: return ScriptLibrarySnapshot(emptyList(), 0)
        val treeDocumentId = DocumentPicker.requireLocalLibraryTree(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocumentId)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            OpenableColumns.DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        val scripts = mutableListOf<ScriptFile>()
        var unreadableCount = 0

        resolver.query(childrenUri, columns, null, null, OpenableColumns.DISPLAY_NAME)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

            while (cursor.moveToNext()) {
                val documentId = cursor.getString(idColumn)
                val displayName = cursor.getString(nameColumn) ?: "script.json"
                val mimeType = cursor.getString(mimeColumn)
                if (!displayName.endsWith(".json", ignoreCase = true) && mimeType != DocumentPicker.JSON_MIME_TYPE) {
                    continue
                }

                val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                try {
                    DocumentPicker.requireLocalLibraryDocument(treeUri, documentUri)
                    val source = readUtf8(documentUri, "Could not open the prepared script.")
                    scripts += ScriptFile(documentUri, displayName, decodeScript(source))
                } catch (error: InsufficientMemoryException) {
                    throw error
                } catch (error: OutOfMemoryError) {
                    throw insufficientMemory(error)
                } catch (_: Exception) {
                    unreadableCount++
                }
            }
        } ?: throw IOException("La carpeta seleccionada no está disponible. Vuelve a elegirla para recuperar el acceso.")

        return ScriptLibrarySnapshot(scripts.sortedBy { it.script.title.lowercase() }, unreadableCount)
    }

    fun saveNewScript(script: PreparedScript): ScriptFile {
        val treeUri = savedLibraryTree() ?: throw IOException("Elige la carpeta local de guiones antes de guardar.")
        val treeDocumentId = DocumentPicker.requireLocalLibraryTree(treeUri)
        val encoded = encodeScript(script)
        val displayName = uniqueFileName(treeUri, treeDocumentId, script.title)
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocumentId)
        val createdUri = DocumentsContract.createDocument(
            resolver,
            parentUri,
            DocumentPicker.JSON_MIME_TYPE,
            displayName,
        ) ?: throw IOException("No se pudo crear el archivo del guion en la carpeta seleccionada.")

        try {
            val output = resolver.openOutputStream(createdUri, "w")
                ?: throw IOException("No se pudo abrir el guion nuevo para escribirlo en la carpeta seleccionada.")
            writeUtf8(output, encoded)

            val verifiedSource = readUtf8(createdUri, "The saved script could not be reopened for verification.")
            val verifiedScript = decodeScript(verifiedSource)
            if (verifiedScript.scriptId != script.scriptId) {
                throw IOException("No se pudo verificar el guion guardado.")
            }
            return ScriptFile(createdUri, displayName, verifiedScript)
        } catch (error: OutOfMemoryError) {
            runCatching { DocumentsContract.deleteDocument(resolver, createdUri) }
            throw insufficientMemory(error)
        } catch (error: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, createdUri) }
            throw error
        }
    }

    /** Writes and verifies a replacement before deleting the prior prepared script. */
    fun updateScript(scriptFile: ScriptFile, updatedScript: PreparedScript): ScriptFile {
        val treeUri = savedLibraryTree() ?: throw IOException("Elige la carpeta local de guiones antes de guardar.")
        DocumentPicker.requireLocalLibraryDocument(treeUri, scriptFile.uri)
        if (scriptFile.script.scriptId != updatedScript.scriptId) {
            throw IOException("La actualización debe conservar el identificador del guion preparado.")
        }
        val currentSource = readUtf8(scriptFile.uri, "The current script could not be reopened for safe update.")
        if (decodeScript(currentSource) != scriptFile.script) {
            throw IOException("El guion ha cambiado desde que lo abriste. Vuelve a cargarlo antes de guardar estos ajustes.")
        }

        val verifiedReplacement = saveNewScript(updatedScript)
        if (!DocumentsContract.deleteDocument(resolver, scriptFile.uri)) {
            runCatching { DocumentsContract.deleteDocument(resolver, verifiedReplacement.uri) }
            throw IOException("El guion actualizado se verificó, pero no se pudo sustituir la versión anterior.")
        }

        val renamedUri = runCatching {
            DocumentsContract.renameDocument(resolver, verifiedReplacement.uri, scriptFile.displayName)
        }.getOrNull()
        return ScriptFile(
            uri = renamedUri ?: verifiedReplacement.uri,
            displayName = if (renamedUri != null) scriptFile.displayName else verifiedReplacement.displayName,
            script = verifiedReplacement.script,
        )
    }

    fun prepareJson(sourceUri: Uri): PreparedScript {
        val source = readUtf8(sourceUri, "The selected file could not be opened.")
        return decodeScript(source)
    }

    fun preparePlainText(sourceUri: Uri): PreparedScript = PlainTextImporter(resolver).import(sourceUri)

    fun prepareMarkdown(sourceUri: Uri): PreparedScript = MarkdownImporter(resolver).import(sourceUri)

    fun prepareHtml(sourceUri: Uri): PreparedScript = HtmlImporter(resolver).import(sourceUri)

    fun preparePdf(sourceUri: Uri): PreparedScript = PdfImporter(appContext).import(sourceUri)

    fun prepareWord(sourceUri: Uri): PreparedScript = WordImporter(resolver).import(sourceUri)

    fun prepareOdt(sourceUri: Uri): PreparedScript = OdtImporter(resolver, appContext.cacheDir).import(sourceUri)

    fun exportScript(script: PreparedScript, destinationUri: Uri) {
        val encoded = encodeScript(script)
        try {
            val output = resolver.openOutputStream(destinationUri, "wt")
                ?: throw IOException("No se pudo abrir el destino seleccionado para escribir.")
            writeUtf8(output, encoded)
        } catch (error: OutOfMemoryError) {
            runCatching { DocumentsContract.deleteDocument(resolver, destinationUri) }
            throw insufficientMemory(error)
        } catch (error: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, destinationUri) }
            throw error
        }
    }

    fun deleteScript(scriptFile: ScriptFile) {
        val treeUri = savedLibraryTree() ?: throw IOException("Vuelve a elegir la carpeta local de guiones.")
        DocumentPicker.requireLocalLibraryDocument(treeUri, scriptFile.uri)
        if (!DocumentsContract.deleteDocument(resolver, scriptFile.uri)) {
            throw IOException("No se pudo eliminar el guion de la carpeta seleccionada.")
        }
        ReadingPositionStore(appContext).delete(scriptFile.script.scriptId)
    }

    private fun uniqueFileName(treeUri: Uri, treeDocumentId: String, title: String): String {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocumentId)
        val existingNames = mutableSetOf<String>()
        resolver.query(
            childrenUri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
            while (cursor.moveToNext()) cursor.getString(nameColumn)?.let(existingNames::add)
        } ?: throw IOException("No se pudo leer la carpeta seleccionada.")

        val safeTitle = title
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .trim()
            .trimEnd('.')
            .ifBlank { "Script" }
            .take(MAX_FILE_TITLE_LENGTH)
        var candidate = "$safeTitle.json"
        var suffix = 2
        while (candidate in existingNames) {
            candidate = "$safeTitle ($suffix).json"
            suffix++
        }
        return candidate
    }

    private fun readUtf8(uri: Uri, errorMessage: String): String = try {
        resolver.openInputStream(uri)?.use { input ->
            input.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        } ?: throw IOException(errorMessage)
    } catch (error: OutOfMemoryError) {
        throw insufficientMemory(error)
    }

    private fun decodeScript(source: String): PreparedScript = try {
        ScriptJsonCodec.decode(source)
    } catch (error: OutOfMemoryError) {
        throw insufficientMemory(error)
    }

    private fun encodeScript(script: PreparedScript): String = try {
        ScriptJsonCodec.encode(script)
    } catch (error: OutOfMemoryError) {
        throw insufficientMemory(error)
    }

    private fun writeUtf8(output: java.io.OutputStream, source: String) {
        try {
            output.use { stream ->
                OutputStreamWriter(stream, StandardCharsets.UTF_8).use { writer -> writer.write(source) }
            }
        } catch (error: OutOfMemoryError) {
            runCatching { output.close() }
            throw insufficientMemory(error)
        }
    }

    private fun insufficientMemory(error: OutOfMemoryError) = InsufficientMemoryException(
        "No hay memoria suficiente para procesar el guion. Cierra otras aplicaciones y vuelve a intentarlo; no se guardaron cambios parciales.",
        error,
    )

    private class InsufficientMemoryException(message: String, cause: Throwable) : IOException(message, cause)

    private companion object {
        const val PREFERENCES_NAME = "prepared_script_library"
        const val KEY_LIBRARY_TREE_URI = "library_tree_uri"
        const val MAX_FILE_TITLE_LENGTH = 100
    }
}
