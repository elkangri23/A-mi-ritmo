package com.teleprompter.phone.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teleprompter.phone.`import`.DocumentPicker
import com.teleprompter.phone.`import`.insufficientScratchSpace
import com.teleprompter.phone.`import`.insufficientStorageMessage
import com.teleprompter.phone.storage.ScriptFile
import com.teleprompter.phone.storage.ScriptFileRepository
import com.teleprompter.phone.storage.ScriptLibrarySnapshot
import com.teleprompter.shared.model.ContentBlock
import com.teleprompter.shared.model.InlineRun
import com.teleprompter.shared.model.PreparedScript
import com.teleprompter.shared.model.ReaderAppearance
import com.teleprompter.shared.serialization.ScriptFormatException
import com.teleprompter.shared.serialization.UnsupportedScriptSchemaException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    sharedText: String? = null,
    sharedTitle: String? = null,
    onSharedContentConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val repository = remember(context) { ScriptFileRepository(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var library by remember { mutableStateOf(ScriptLibrarySnapshot(emptyList(), 0)) }
    var selectedFolder by remember { mutableStateOf(repository.savedLibraryTree()) }
    var selectedScript by remember { mutableStateOf<ScriptFile?>(null) }
    var pendingExport by remember { mutableStateOf<PreparedScript?>(null) }
    var pendingReview by remember { mutableStateOf<PreparedScript?>(null) }
    var pendingReviewWarning by remember { mutableStateOf<String?>(null) }
    var isBusy by remember { mutableStateOf(false) }
    var activeImportJob by remember { mutableStateOf<Job?>(null) }
    var activeImportLabel by remember { mutableStateOf<String?>(null) }
    var isCreatingNote by remember { mutableStateOf(false) }
    var showPrivacyInfo by remember { mutableStateOf(false) }
    var noteTitle by remember { mutableStateOf("") }
    var noteBody by remember { mutableStateOf("") }

    suspend fun refreshLibrary() {
        library = withContext(Dispatchers.IO) { repository.loadLibrary() }
    }

    suspend fun showMessage(message: String) {
        snackbarHostState.showSnackbar(message)
    }

    fun stageReview(script: PreparedScript, warning: String? = null) {
        pendingReview = script
        pendingReviewWarning = warning
    }

    LaunchedEffect(selectedFolder) {
        if (selectedFolder != null) {
            try {
                refreshLibrary()
            } catch (error: Exception) {
                    showMessage(userFacingError(error, "No se pudo abrir la carpeta de guiones."))
            }
        }
    }

    LaunchedEffect(sharedText, sharedTitle) {
        val incomingText = sharedText?.trim()?.takeIf(String::isNotEmpty) ?: return@LaunchedEffect
        noteTitle = sharedTitle?.trim()?.takeIf(String::isNotEmpty) ?: "Nota compartida"
        noteBody = incomingText
        isCreatingNote = true
        pendingReview = null
        pendingReviewWarning = null
        onSharedContentConsumed()
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            coroutineScope.launch {
                isBusy = true
                try {
                    withContext(Dispatchers.IO) { repository.rememberLibraryTree(treeUri) }
                    selectedFolder = treeUri
                    refreshLibrary()
                    showMessage("Carpeta de guiones lista")
                } catch (error: Exception) {
                    showMessage(userFacingError(error, "No se pudo usar esa carpeta."))
                } finally {
                    isBusy = false
                }
            }
        }
    }

    val sourceDocumentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { sourceUri: Uri? ->
        if (sourceUri != null && activeImportJob?.isActive != true) {
            isBusy = true
            activeImportLabel = "Preparando documento…"
            activeImportJob = coroutineScope.launch {
                try {
                    val displayName = withContext(Dispatchers.IO) {
                        context.contentResolver.query(
                            sourceUri,
                            arrayOf(OpenableColumns.DISPLAY_NAME),
                            null,
                            null,
                            null,
                        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                    }.orEmpty()
                    val extension = displayName.substringAfterLast('.', "").lowercase()
                    val handler = when (extension) {
                        "json" -> DocumentImportHandler("el guion JSON", null) { repository.prepareJson(sourceUri) }
                        "txt" -> DocumentImportHandler(
                            "el archivo de texto",
                            "Texto plano: se conservan el texto y los saltos de párrafo. Comprueba las separaciones y añade los subtítulos que necesites.",
                        ) { repository.preparePlainText(sourceUri) }
                        "md" -> DocumentImportHandler(
                            "Markdown",
                            "Markdown: se convierten títulos, listas, tablas GFM y énfasis básicos. Las listas anidadas se aplanan y la numeración puede reiniciarse; otros estilos o extensiones pueden simplificarse.",
                        ) { repository.prepareMarkdown(sourceUri) }
                        "htm", "html" -> DocumentImportHandler(
                            "HTML",
                            "HTML estático: se importa el texto y la estructura básica. Las listas anidadas se aplanan y la numeración puede reiniciarse; los estilos complejos y algunos elementos pueden no conservarse.",
                        ) { repository.prepareHtml(sourceUri) }
                        "pdf" -> DocumentImportHandler(
                            "PDF",
                            "PDF: se extrae el texto seleccionable. El orden de lectura, las columnas y los saltos pueden variar; los PDF escaneados no se importan.",
                        ) { repository.preparePdf(sourceUri) }
                        "doc", "docx" -> DocumentImportHandler(
                            "Word",
                            "Word: DOCX conserva títulos, listas, tablas y énfasis básicos; los niveles y la numeración de listas pueden simplificarse. DOC se extrae como texto y puede perder formato. Revisa secciones y tablas.",
                        ) { repository.prepareWord(sourceUri) }
                        "odt" -> DocumentImportHandler(
                            "ODT",
                            "ODT: se conservan estructuras y énfasis básicos. Las listas anidadas se aplanan y la numeración puede reiniciarse; la maquetación y los estilos complejos pueden cambiar.",
                        ) { repository.prepareOdt(sourceUri) }
                        else -> throw UnsupportedDocumentImportFormatException()
                    }
                    activeImportLabel = "Preparando ${handler.label}…"
                    val draft = withContext(Dispatchers.IO) { handler.prepare() }
                    currentCoroutineContext().ensureActive()
                    stageReview(draft, handler.warning)
                    showMessage("${draft.title} listo para revisar")
                } catch (_: CancellationException) {
                    coroutineScope.launch { showMessage("Importación cancelada; no se guardó ningún borrador.") }
                } catch (_: OutOfMemoryError) {
                    showMessage("El documento requiere más memoria de la disponible. Libera espacio de memoria o prueba una versión más ligera; no se guardó ningún borrador.")
                } catch (error: Exception) {
                    showMessage(insufficientScratchSpace("el documento", error)?.message ?: userFacingError(error, "No se pudo importar el documento."))
                } finally {
                    activeImportLabel = null
                    activeImportJob = null
                    isBusy = false
                }
            }
        }
    }

    val exportPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(DocumentPicker.JSON_MIME_TYPE),
    ) { destinationUri: Uri? ->
        val script = pendingExport
        if (destinationUri != null && script != null) {
            coroutineScope.launch {
                isBusy = true
                try {
                    withContext(Dispatchers.IO) { repository.exportScript(script, destinationUri) }
                    showMessage("Guion exportado")
                } catch (error: Exception) {
                    showMessage(userFacingError(error, "No se pudo exportar el guion."))
                } finally {
                    isBusy = false
                    pendingExport = null
                }
            }
        } else {
            pendingExport = null
        }
    }

    fun leaveReader() {
        val current = selectedScript ?: return
        val persisted = library.scripts.firstOrNull { it.script.scriptId == current.script.scriptId }
        if (persisted == null || persisted.script.speedMultiplier == current.script.speedMultiplier) {
            selectedScript = null
            return
        }
        if (isBusy) return
        isBusy = true
        coroutineScope.launch {
            try {
                val updated = withContext(Dispatchers.IO) {
                    repository.updateScript(persisted, current.script)
                }
                library = library.copy(
                    scripts = library.scripts.map { file ->
                        if (file.script.scriptId == updated.script.scriptId) updated else file
                    },
                )
                selectedScript = null
                showMessage("Velocidad guardada en el guion")
            } catch (error: Exception) {
                showMessage(userFacingError(error, "No se pudo guardar la velocidad del guion."))
            } finally {
                isBusy = false
            }
        }
    }

    BackHandler(enabled = selectedScript != null) { leaveReader() }

    if (showPrivacyInfo) {
        AlertDialog(
            onDismissRequest = { showPrivacyInfo = false },
            title = { Text("Privacidad y datos") },
            text = {
                Text(
                    "Tus documentos se importan y preparan en este teléfono. El archivo original no se modifica; " +
                        "el guion preparado y la última posición de lectura se guardan localmente. La app funciona sin conexión para importar y leer.\n\n" +
                        "El reloj recibe estado mínimo de reproducción (sección, avance, velocidad y estado), nunca el título, el texto ni las notas del guion. " +
                        "Wear OS Data Layer cifra los mensajes y puede retransmitir esos metadatos mediante servicios de Google si Bluetooth no está disponible. " +
                        "A mi ritmo no envía guiones a un servidor propio ni incluye analítica.",
                )
            },
            confirmButton = { TextButton(onClick = { showPrivacyInfo = false }) { Text("Entendido") } },
        )
    }

    if (activeImportLabel != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(activeImportLabel ?: "Importando") },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                    Text("Extrayendo el documento. El guion se abrirá para revisión cuando termine.")
                }
            },
            confirmButton = {
                TextButton(
                    enabled = activeImportJob?.isActive == true && activeImportLabel != "Cancelando importación…",
                    onClick = {
                        activeImportLabel = "Cancelando importación…"
                        activeImportJob?.cancel()
                    },
                ) { Text(if (activeImportLabel == "Cancelando importación…") "Cancelando…" else "Cancelar") }
            },
        )
    }

    val title = when {
        isCreatingNote -> "Nueva nota"
        pendingReview != null -> "Revisar guion"
        selectedScript != null -> selectedScript!!.script.title
        else -> "Mis guiones"
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                actions = {
                    if (selectedScript == null && !isCreatingNote && pendingReview == null) {
                        TextButton(onClick = { showPrivacyInfo = true }) { Text("Privacidad") }
                    }
                },
                navigationIcon = {
                    if (selectedScript != null || isCreatingNote || pendingReview != null) {
                        TextButton(
                            enabled = !isBusy,
                            onClick = {
                                if (selectedScript != null) {
                                    leaveReader()
                                } else {
                                    selectedScript = null
                                    isCreatingNote = false
                                    pendingReview = null
                                    pendingReviewWarning = null
                                }
                            },
                        ) { Text("Atrás") }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        val currentScript = selectedScript
        if (pendingReview != null) {
            ScriptReview(
                script = pendingReview!!,
                importWarning = pendingReviewWarning,
                isBusy = isBusy,
                onScriptChange = { pendingReview = it },
                onCancel = {
                    pendingReview = null
                    pendingReviewWarning = null
                },
                onSave = {
                    val draft = pendingReview!!
                    val validationError = reviewValidationError(draft)
                    if (validationError != null) {
                        coroutineScope.launch { showMessage(validationError) }
                    } else {
                        coroutineScope.launch {
                            isBusy = true
                            try {
                                withContext(Dispatchers.IO) { repository.saveNewScript(draft) }
                                refreshLibrary()
                                pendingReview = null
                                pendingReviewWarning = null
                                showMessage("Guion guardado en Mis guiones")
                            } catch (error: Exception) {
                                showMessage(userFacingError(error, "No se pudo guardar el guion."))
                            } finally {
                                isBusy = false
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize().padding(contentPadding),
            )
        } else if (isCreatingNote) {
            NewNoteScreen(
                title = noteTitle,
                body = noteBody,
                isBusy = isBusy,
                onTitleChange = { noteTitle = it },
                onBodyChange = { noteBody = it },
                onCancel = { isCreatingNote = false },
                onSave = {
                    val paragraphs = noteBody
                        .replace("\r\n", "\n")
                        .replace('\r', '\n')
                        .split(Regex("\\n[\\t ]*\\n+"))
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    if (noteTitle.isBlank() || paragraphs.isEmpty()) {
                        coroutineScope.launch { showMessage("Escribe un título y algo de texto.") }
                    } else {
                        val script = PreparedScript(
                            schemaVersion = 1,
                            scriptId = UUID.randomUUID().toString(),
                            title = noteTitle.trim(),
                            sections = listOf(
                                com.teleprompter.shared.model.ScriptSection(
                                    id = UUID.randomUUID().toString(),
                                    blocks = paragraphs.map { paragraph ->
                                        ContentBlock.Paragraph(
                                            id = UUID.randomUUID().toString(),
                                            runs = listOf(InlineRun(paragraph)),
                                        )
                                    },
                                ),
                            ),
                            appearance = com.teleprompter.shared.model.ReaderAppearance(),
                        )
                        coroutineScope.launch {
                            isBusy = true
                            try {
                                withContext(Dispatchers.IO) { repository.saveNewScript(script) }
                                refreshLibrary()
                                noteTitle = ""
                                noteBody = ""
                                isCreatingNote = false
                                showMessage("Nota guardada en Mis guiones")
                            } catch (error: Exception) {
                                showMessage(userFacingError(error, "No se pudo guardar la nota."))
                            } finally {
                                isBusy = false
                            }
                        }
                    }
                },
            )
        } else if (currentScript != null) {
            ScriptReaderScreen(
                script = currentScript.script,
                isBusy = isBusy,
                onSpeedChange = { speed ->
                    selectedScript = selectedScript?.copy(
                        script = selectedScript!!.script.copy(speedMultiplier = speed),
                    )
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )
        } else {
            LibraryContent(
                library = library,
                folderSelected = selectedFolder != null,
                isBusy = isBusy,
                onSelectFolder = { folderPicker.launch(DocumentPicker.initialLibraryParentUri) },
                onImportDocument = {
                    if (selectedFolder == null) {
                        coroutineScope.launch { showMessage("Elige primero la carpeta Documents/TelePrompter.") }
                    } else {
                        sourceDocumentPicker.launch(DocumentPicker.sourceMimeTypes)
                    }
                },
                onCreateNote = {
                    if (selectedFolder == null) {
                        coroutineScope.launch { showMessage("Elige primero la carpeta Documents/TelePrompter.") }
                    } else {
                        noteTitle = ""
                        noteBody = ""
                        isCreatingNote = true
                    }
                },
                onOpen = { selectedScript = it },
                onExport = { scriptFile ->
                    pendingExport = scriptFile.script
                    exportPicker.launch("${scriptFile.script.title}.json")
                },
                onDelete = { scriptFile ->
                    coroutineScope.launch {
                        isBusy = true
                        try {
                            withContext(Dispatchers.IO) { repository.deleteScript(scriptFile) }
                            refreshLibrary()
                            showMessage("Guion eliminado")
                        } catch (error: Exception) {
                            showMessage(userFacingError(error, "No se pudo eliminar el guion."))
                        } finally {
                            isBusy = false
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun LibraryContent(
    library: ScriptLibrarySnapshot,
    folderSelected: Boolean,
    isBusy: Boolean,
    onSelectFolder: () -> Unit,
    onImportDocument: () -> Unit,
    onCreateNote: () -> Unit,
    onOpen: (ScriptFile) -> Unit,
    onExport: (ScriptFile) -> Unit,
    onDelete: (ScriptFile) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("A mi ritmo", style = MaterialTheme.typography.headlineMedium)
        Text("Guiones preparados sin conexión", style = MaterialTheme.typography.bodyLarge)

        if (!folderSelected) {
            Text("En el selector, abre Documents y crea o elige la carpeta TelePrompter.")
        } else {
            Text("Carpeta: Documents/TelePrompter")
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !isBusy,
            onClick = onSelectFolder,
        ) {
            Text(if (folderSelected) "Cambiar carpeta" else "Elegir carpeta de guiones")
        }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = !isBusy && folderSelected,
            onClick = onImportDocument,
        ) {
            Text("Importar documento")
        }
        Text(
            "Admite PDF, DOC/DOCX, ODT, Markdown, TXT, HTML y guiones JSON. Se preparará para que lo revises antes de guardarlo.",
            style = MaterialTheme.typography.bodyMedium,
        )

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = !isBusy && folderSelected,
            onClick = onCreateNote,
        ) {
            Text("Escribir notas")
        }

        if (library.unreadableFileCount > 0) {
            Text(
                text = "${library.unreadableFileCount} archivo(s) JSON no se pudieron abrir.",
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (library.scripts.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Todavía no hay guiones en esta carpeta.", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(library.scripts, key = { it.script.scriptId }) { scriptFile ->
                    ScriptListItem(
                        scriptFile = scriptFile,
                        enabled = !isBusy,
                        onOpen = { onOpen(scriptFile) },
                        onExport = { onExport(scriptFile) },
                        onDelete = { onDelete(scriptFile) },
                    )
                }
            }
        }
    }
}

private fun userFacingError(error: Throwable, fallback: String): String {
    insufficientStorageMessage(error)?.let { return it }
    return when (error) {
        is UnsupportedScriptSchemaException ->
            "Este guion usa la versión de formato ${error.foundVersion}, que esta versión de A mi ritmo no admite. Conserva el archivo original para abrirlo con una versión compatible."
        is UnsupportedDocumentImportFormatException ->
            "No se pudo procesar el archivo: su formato no está admitido. Se aceptan PDF, DOC/DOCX, ODT, Markdown (.md), texto (.txt), HTML (.htm/.html) y guiones JSON."
        is ScriptFormatException -> when (error.message) {
            "The script JSON root must be an object" -> "El archivo JSON del guion debe contener un objeto válido."
            "The script file is not valid JSON" -> "El archivo seleccionado no contiene un JSON válido."
            "The script is missing a valid schemaVersion" -> "El archivo no indica una versión válida del formato del guion."
            "The script does not match schema version 1" -> "El archivo no cumple el formato de guion compatible con esta versión."
            else -> fallback
        }
        else -> fallback
    }
}

private data class DocumentImportHandler(
    val label: String,
    val warning: String?,
    val prepare: () -> PreparedScript,
)

private class UnsupportedDocumentImportFormatException : java.io.IOException()

private fun reviewValidationError(script: PreparedScript): String? {
    if (script.title.isBlank()) return "Escribe un título para el guion."
    if (script.sections.any { section ->
            section.blocks.any { block -> block is ContentBlock.Cue && block.label.isBlank() }
        }
    ) {
        return "Completa la etiqueta de cada señal de pausa o elimina ese bloque."
    }

    val hasReadableText = script.sections.any { section ->
        section.blocks.any { block ->
            when (block) {
                is ContentBlock.Paragraph -> block.runs.any { it.text.isNotBlank() }
                is ContentBlock.ListBlock -> block.items.flatten().any { it.text.isNotBlank() }
                is ContentBlock.Table -> (block.headerCells.flatten() + block.rows.flatten().flatten())
                    .any { it.text.isNotBlank() }
                is ContentBlock.Cue -> false
            }
        }
    }
    if (!hasReadableText) {
        return "Añade texto en un párrafo, elemento de lista o celda de tabla antes de guardar."
    }
    return null
}

@Composable
private fun ScriptReview(
    script: PreparedScript,
    importWarning: String?,
    isBusy: Boolean,
    onScriptChange: (PreparedScript) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    fun updateSection(sectionIndex: Int, updatedSection: com.teleprompter.shared.model.ScriptSection) {
        onScriptChange(
            script.copy(
                sections = script.sections.mapIndexed { index, section ->
                    if (index == sectionIndex) updatedSection else section
                },
            ),
        )
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Revisa y corrige el guion. Los cambios se guardan en la copia preparada.")
        if (importWarning != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                ),
            ) {
                Text(
                    text = importWarning,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = script.title,
            onValueChange = { onScriptChange(script.copy(title = it)) },
            label = { Text("Título del guion") },
            singleLine = true,
            enabled = !isBusy,
        )
        AppearancePalette(
            appearance = script.appearance,
            enabled = !isBusy,
            onAppearanceChange = { onScriptChange(script.copy(appearance = it)) },
        )
        script.sections.forEachIndexed { index, section ->
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = section.subtitle.orEmpty(),
                        onValueChange = { subtitle ->
                            updateSection(index, section.copy(subtitle = subtitle.ifBlank { null }))
                        },
                        label = { Text("Subtítulo de sección ${index + 1}") },
                        singleLine = true,
                        enabled = !isBusy,
                    )
                    section.blocks.forEachIndexed { blockIndex, block ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                when (block) {
                                    is ContentBlock.Paragraph -> EditableRuns(
                                        label = "Párrafo",
                                        runs = block.runs,
                                        enabled = !isBusy,
                                        onRunsChange = { runs ->
                                            updateSection(index, section.withBlock(blockIndex, block.copy(runs = runs)))
                                        },
                                    )
                                    is ContentBlock.ListBlock -> {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(if (block.ordered) "Lista numerada" else "Lista con viñetas", modifier = Modifier.weight(1f))
                                            TextButton(
                                                enabled = !isBusy,
                                                onClick = {
                                                    updateSection(index, section.withBlock(blockIndex, block.copy(ordered = !block.ordered)))
                                                },
                                            ) { Text("Cambiar tipo") }
                                        }
                                        block.items.forEachIndexed { itemIndex, item ->
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                EditableRuns(
                                                    modifier = Modifier.weight(1f),
                                                    label = "Elemento ${itemIndex + 1}",
                                                    runs = item,
                                                    enabled = !isBusy,
                                                    onRunsChange = { runs ->
                                                        updateSection(
                                                            index,
                                                            section.withBlock(blockIndex, block.copy(items = block.items.replaceAt(itemIndex, runs))),
                                                        )
                                                    },
                                                )
                                                TextButton(
                                                    enabled = !isBusy && block.items.size > 1,
                                                    onClick = {
                                                        updateSection(
                                                            index,
                                                            section.withBlock(blockIndex, block.copy(items = block.items.filterIndexed { i, _ -> i != itemIndex })),
                                                        )
                                                    },
                                                ) { Text("Quitar") }
                                            }
                                        }
                                        TextButton(
                                            enabled = !isBusy,
                                            onClick = {
                                                updateSection(index, section.withBlock(blockIndex, block.copy(items = block.items + listOf(listOf(InlineRun(""))))))
                                            },
                                        ) { Text("Añadir elemento") }
                                    }
                                    is ContentBlock.Table -> {
                                        Text("Tabla", fontWeight = FontWeight.Bold)
                                        block.headerCells.forEachIndexed { cellIndex, cell ->
                                            EditableRuns(
                                                label = "Encabezado ${cellIndex + 1}",
                                                runs = cell,
                                                enabled = !isBusy,
                                                onRunsChange = { runs ->
                                                    updateSection(index, section.withBlock(blockIndex, block.copy(headerCells = block.headerCells.replaceAt(cellIndex, runs))))
                                                },
                                            )
                                        }
                                        block.rows.forEachIndexed { rowIndex, row ->
                                            Text("Fila ${rowIndex + 1}", style = MaterialTheme.typography.labelLarge)
                                            row.forEachIndexed { cellIndex, cell ->
                                                EditableRuns(
                                                    label = "Columna ${cellIndex + 1}",
                                                    runs = cell,
                                                    enabled = !isBusy,
                                                    onRunsChange = { runs ->
                                                        val rows = block.rows.replaceAt(rowIndex, row.replaceAt(cellIndex, runs))
                                                        updateSection(index, section.withBlock(blockIndex, block.copy(rows = rows)))
                                                    },
                                                )
                                            }
                                        }
                                        TextButton(
                                            enabled = !isBusy && block.headerCells.isNotEmpty(),
                                            onClick = {
                                                updateSection(
                                                    index,
                                                    section.withBlock(
                                                        blockIndex,
                                                        block.copy(rows = block.rows + listOf(List(block.headerCells.size) { listOf(InlineRun("")) })),
                                                    ),
                                                )
                                            },
                                        ) { Text("Añadir fila") }
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            TextButton(
                                                enabled = !isBusy,
                                                onClick = {
                                                    val header = block.headerCells + listOf(listOf(InlineRun("")))
                                                    val rows = block.rows.map { row -> row + listOf(listOf(InlineRun(""))) }
                                                    updateSection(index, section.withBlock(blockIndex, block.copy(headerCells = header, rows = rows)))
                                                },
                                            ) { Text("Añadir columna") }
                                            TextButton(
                                                enabled = !isBusy && block.headerCells.size > 1,
                                                onClick = {
                                                    val header = block.headerCells.dropLast(1)
                                                    val rows = block.rows.map { row -> row.dropLast(1) }
                                                    updateSection(index, section.withBlock(blockIndex, block.copy(headerCells = header, rows = rows)))
                                                },
                                            ) { Text("Quitar última columna") }
                                        }
                                    }
                                    is ContentBlock.Cue -> OutlinedTextField(
                                        modifier = Modifier.fillMaxWidth(),
                                        value = block.label,
                                        onValueChange = { label -> updateSection(index, section.withBlock(blockIndex, block.copy(label = label))) },
                                        label = { Text("Señal de pausa") },
                                        singleLine = true,
                                        enabled = !isBusy,
                                    )
                                }
                                TextButton(
                                    enabled = !isBusy,
                                    onClick = { updateSection(index, section.copy(blocks = section.blocks.filterIndexed { i, _ -> i != blockIndex })) },
                                ) { Text("Eliminar bloque") }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            enabled = !isBusy,
                            onClick = { updateSection(index, section.copy(blocks = section.blocks + ContentBlock.Paragraph(UUID.randomUUID().toString(), listOf(InlineRun(""))))) },
                        ) { Text("Añadir párrafo") }
                        TextButton(
                            enabled = !isBusy,
                            onClick = { updateSection(index, section.copy(blocks = section.blocks + ContentBlock.ListBlock(UUID.randomUUID().toString(), false, listOf(listOf(InlineRun("")))))) },
                        ) { Text("Añadir lista") }
                        TextButton(
                            enabled = !isBusy,
                            onClick = { updateSection(index, section.copy(blocks = section.blocks + ContentBlock.Cue(UUID.randomUUID().toString()))) },
                        ) { Text("Añadir pausa") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            enabled = !isBusy && index > 0,
                            onClick = {
                                val reordered = script.sections.toMutableList().apply { add(index - 1, removeAt(index)) }
                                onScriptChange(script.copy(sections = reordered))
                            },
                        ) { Text("↑ Subir sección") }
                        TextButton(
                            enabled = !isBusy && index < script.sections.lastIndex,
                            onClick = {
                                val reordered = script.sections.toMutableList().apply { add(index + 1, removeAt(index)) }
                                onScriptChange(script.copy(sections = reordered))
                            },
                        ) { Text("↓ Bajar sección") }
                        TextButton(
                            enabled = !isBusy && script.sections.size > 1,
                            onClick = { onScriptChange(script.copy(sections = script.sections.filterIndexed { sectionIndex, _ -> sectionIndex != index })) },
                        ) { Text("Eliminar sección") }
                    }
                }
            }
        }
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = !isBusy,
            onClick = {
                onScriptChange(
                    script.copy(
                        sections = script.sections + com.teleprompter.shared.model.ScriptSection(
                            id = UUID.randomUUID().toString(),
                            subtitle = "",
                            blocks = listOf(ContentBlock.Paragraph(UUID.randomUUID().toString(), listOf(InlineRun("")))),
                        ),
                    ),
                )
            },
        ) { Text("Añadir sección") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !isBusy, onClick = onCancel) { Text("Cancelar") }
            Button(enabled = !isBusy, onClick = onSave) { Text("Guardar en Mis guiones") }
        }
    }
}

@Composable
private fun EditableRuns(
    modifier: Modifier = Modifier,
    label: String,
    runs: List<InlineRun>,
    enabled: Boolean,
    onRunsChange: (List<InlineRun>) -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        runs.forEachIndexed { index, run ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = run.text,
                    onValueChange = { text -> onRunsChange(runs.replaceAt(index, run.copy(text = text))) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (run.bold) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (run.italic) FontStyle.Italic else FontStyle.Normal,
                    ),
                    minLines = 1,
                    enabled = enabled,
                    label = { if (runs.size > 1) Text("Fragmento ${index + 1}") },
                )
                TextButton(
                    enabled = enabled,
                    onClick = { onRunsChange(runs.replaceAt(index, run.copy(bold = !run.bold))) },
                ) { Text("N", fontWeight = FontWeight.Bold) }
                TextButton(
                    enabled = enabled,
                    onClick = { onRunsChange(runs.replaceAt(index, run.copy(italic = !run.italic))) },
                ) { Text("C", fontStyle = FontStyle.Italic) }
                TextButton(
                    enabled = enabled && runs.size > 1,
                    onClick = { onRunsChange(runs.filterIndexed { runIndex, _ -> runIndex != index }) },
                ) { Text("×") }
            }
        }
        TextButton(enabled = enabled, onClick = { onRunsChange(runs + InlineRun("")) }) { Text("Añadir fragmento") }
    }
}

private fun <T> List<T>.replaceAt(index: Int, value: T): List<T> = mapIndexed { i, old -> if (i == index) value else old }

private fun com.teleprompter.shared.model.ScriptSection.withBlock(index: Int, block: ContentBlock) =
    copy(blocks = blocks.replaceAt(index, block))

private data class AppearancePreset(
    val name: String,
    val text: String,
    val background: String,
)

@Composable
private fun AppearancePalette(
    appearance: com.teleprompter.shared.model.ReaderAppearance,
    enabled: Boolean,
    onAppearanceChange: (com.teleprompter.shared.model.ReaderAppearance) -> Unit,
) {
    val presets = listOf(
        AppearancePreset("Blanco sobre negro", "#FFFFFFFF", "#FF000000"),
        AppearancePreset("Negro sobre blanco", "#FF111111", "#FFFFFFFF"),
        AppearancePreset("Ámbar sobre oscuro", "#FFFFD166", "#FF1A1A1A"),
        AppearancePreset("Azul claro sobre oscuro", "#FFE3F2FD", "#FF102027"),
    )
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Aspecto de lectura", style = MaterialTheme.typography.titleMedium)
            Text("Combinaciones preparadas para leer con claridad.")
            presets.forEach { preset ->
                val selected = appearance.textColorArgb == preset.text && appearance.backgroundColorArgb == preset.background
                val action: () -> Unit = {
                    onAppearanceChange(
                        appearance.copy(
                            textColorArgb = preset.text,
                            backgroundColorArgb = preset.background,
                        ),
                    )
                }
                if (selected) {
                    Button(modifier = Modifier.fillMaxWidth(), enabled = enabled, onClick = action) {
                        Text(preset.name)
                    }
                } else {
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = enabled, onClick = action) {
                        Text(preset.name)
                    }
                }
            }
            Text("Tamaño del texto: ${appearance.fontSizeSp.toInt()} sp")
            Slider(
                value = appearance.fontSizeSp.toFloat().coerceIn(ReaderAppearance.MIN_FONT_SIZE_SP.toFloat(), ReaderAppearance.MAX_FONT_SIZE_SP.toFloat()),
                onValueChange = { value ->
                    onAppearanceChange(appearance.copy(fontSizeSp = value.roundToInt().toDouble()))
                },
                valueRange = ReaderAppearance.MIN_FONT_SIZE_SP.toFloat()..ReaderAppearance.MAX_FONT_SIZE_SP.toFloat(),
                steps = (ReaderAppearance.MAX_FONT_SIZE_SP - ReaderAppearance.MIN_FONT_SIZE_SP).toInt() - 1,
                enabled = enabled,
            )
            Text("Interlineado: %.1f".format(java.util.Locale.getDefault(), appearance.lineSpacingMultiplier))
            Slider(
                value = appearance.lineSpacingMultiplier.toFloat().coerceIn(ReaderAppearance.MIN_LINE_SPACING.toFloat(), ReaderAppearance.MAX_LINE_SPACING.toFloat()),
                onValueChange = { value ->
                    val snappedValue = (value * 10).roundToInt() / 10.0
                    onAppearanceChange(appearance.copy(lineSpacingMultiplier = snappedValue))
                },
                valueRange = ReaderAppearance.MIN_LINE_SPACING.toFloat()..ReaderAppearance.MAX_LINE_SPACING.toFloat(),
                steps = ((ReaderAppearance.MAX_LINE_SPACING - ReaderAppearance.MIN_LINE_SPACING) * 10).toInt() - 1,
                enabled = enabled,
            )
            val textColor = Color(android.graphics.Color.parseColor(appearance.textColorArgb))
            val backgroundColor = Color(android.graphics.Color.parseColor(appearance.backgroundColorArgb))
            Text(
                text = "Vista previa del texto",
                modifier = Modifier.fillMaxWidth().background(backgroundColor).padding(12.dp),
                color = textColor,
                fontSize = appearance.fontSizeSp.sp,
                lineHeight = (appearance.fontSizeSp * appearance.lineSpacingMultiplier).sp,
            )
        }
    }
}


@Composable
private fun NewNoteScreen(
    title: String,
    body: String,
    isBusy: Boolean,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = title,
            onValueChange = onTitleChange,
            label = { Text("Título") },
            singleLine = true,
            enabled = !isBusy,
        )
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            value = body,
            onValueChange = onBodyChange,
            label = { Text("Escribe o pega tus notas") },
            minLines = 8,
            enabled = !isBusy,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !isBusy, onClick = onCancel) { Text("Cancelar") }
            Button(enabled = !isBusy, onClick = onSave) { Text("Guardar") }
        }
    }
}

@Composable
private fun ScriptListItem(
    scriptFile: ScriptFile,
    enabled: Boolean,
    onOpen: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(scriptFile.script.title, style = MaterialTheme.typography.titleMedium)
            Text("${scriptFile.script.sections.size} secciones · ${scriptFile.displayName}")
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(enabled = enabled, onClick = onOpen) { Text("Abrir") }
                TextButton(enabled = enabled, onClick = onExport) { Text("Exportar") }
                TextButton(enabled = enabled, onClick = onDelete) { Text("Eliminar") }
            }
        }
    }
}

@Composable
private fun ScriptPreview(script: PreparedScript, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        script.sections.forEachIndexed { index, section ->
            Text(
                text = section.subtitle ?: "Sección ${index + 1}",
                style = MaterialTheme.typography.titleLarge,
            )
            section.blocks.forEach { block ->
                when (block) {
                    is ContentBlock.Paragraph -> Text(block.runs.renderAnnotated())
                    is ContentBlock.ListBlock -> block.items.forEachIndexed { itemIndex, item ->
                        val marker = if (block.ordered) "${itemIndex + 1}." else "•"
                        Text(marker + item.renderAnnotated())
                    }
                    is ContentBlock.Table -> {
                        Text(block.headerCells.joinToString("  ·  ") { it.renderText() })
                        block.rows.forEach { row ->
                            Text(row.joinToString("  ·  ") { it.renderText() })
                        }
                    }
                    is ContentBlock.Cue -> Text(
                        text = block.label,
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

private fun List<InlineRun>.renderText(): String = joinToString(separator = "") { it.text }

private fun List<InlineRun>.renderAnnotated() = buildAnnotatedString {
    forEach { run ->
        val weight = if (run.bold) FontWeight.Bold else FontWeight.Normal
        val style = if (run.italic) FontStyle.Italic else FontStyle.Normal
        withStyle(SpanStyle(fontWeight = weight, fontStyle = style)) { append(run.text) }
    }
}
