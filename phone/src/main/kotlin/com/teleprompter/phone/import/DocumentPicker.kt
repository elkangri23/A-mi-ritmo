package com.teleprompter.phone.`import`

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

object DocumentPicker {
    const val LOCAL_DOCUMENTS_AUTHORITY = "com.android.externalstorage.documents"
    const val LIBRARY_FOLDER_DOCUMENT_ID = "primary:Documents/TelePrompter"
    private const val LIBRARY_PARENT_DOCUMENT_ID = "primary:Documents"
    const val JSON_MIME_TYPE = "application/json"

    val initialLibraryParentUri: Uri = DocumentsContract.buildDocumentUri(
        LOCAL_DOCUMENTS_AUTHORITY,
        LIBRARY_PARENT_DOCUMENT_ID,
    )

    val sourceMimeTypes = arrayOf(
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.oasis.opendocument.text",
        "text/markdown",
        "text/plain",
        "text/html",
        "application/json",
        "*/*",
    )

    const val TREE_PERMISSION_FLAGS =
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    fun requireLocalLibraryTree(uri: Uri): String {
        require(uri.scheme == "content") { "Choose a folder in device storage." }
        require(uri.authority == LOCAL_DOCUMENTS_AUTHORITY) {
            "Choose the device's local Documents provider. Cloud folders are not supported."
        }
        require(DocumentsContract.isTreeUri(uri)) { "Choose the Documents/TelePrompter folder." }

        val documentId = DocumentsContract.getTreeDocumentId(uri)
        require(isLibraryFolderOrDescendant(documentId)) {
            "Choose Documents/TelePrompter in device storage."
        }
        return documentId
    }

    fun requireLocalLibraryDocument(treeUri: Uri, documentUri: Uri): String {
        val treeDocumentId = requireLocalLibraryTree(treeUri)
        require(documentUri.scheme == "content" && documentUri.authority == LOCAL_DOCUMENTS_AUTHORITY) {
            "The selected script is not in the local Documents provider."
        }
        val documentId = DocumentsContract.getDocumentId(documentUri)
        require(documentId.startsWith("$treeDocumentId/")) {
            "The selected script is outside the TelePrompter folder."
        }
        return documentId
    }

    private fun isLibraryFolderOrDescendant(documentId: String): Boolean =
        documentId == LIBRARY_FOLDER_DOCUMENT_ID ||
            documentId.startsWith("$LIBRARY_FOLDER_DOCUMENT_ID/")
}
