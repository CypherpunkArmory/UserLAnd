package tech.ula.provider

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Root
import android.provider.DocumentsContract.Document
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import tech.ula.R
import tech.ula.utils.scopedStorageRoot
import java.io.File
import java.io.FileNotFoundException
import java.lang.Exception

class UlaDocProvider : DocumentsProvider() {

    private val defaultRootProjection: Array<String> = arrayOf(
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_FLAGS,
            Root.COLUMN_ICON,
            Root.COLUMN_TITLE,
            Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_AVAILABLE_BYTES
    )

    private val defaultDocumentProjection: Array<String> = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE
    )

    override fun onCreate(): Boolean { return true }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val result = MatrixCursor(projection ?: defaultRootProjection)
        return context?.let {
            addUlaRoots(result)
        } ?: result
    }

    override fun openDocument(docId: String, mode: String, signal: CancellationSignal): ParcelFileDescriptor {
        val file = getFileForDocId(docId)
        val accessMode = ParcelFileDescriptor.parseMode(mode)
        return ParcelFileDescriptor.open(file, accessMode)
    }

    override fun queryDocument(docId: String?, projection: Array<out String>?): Cursor {
        return MatrixCursor(projection ?: defaultDocumentProjection).apply {
            includeDocId(this, docId = docId ?: "")
        }
    }

    override fun queryChildDocuments(
        parentDocumentId: String?,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val parent: File = getFileForDocId(parentDocumentId ?: "")
        return MatrixCursor(projection ?: defaultDocumentProjection).apply {
            parent.listFiles()
                    .forEach { file ->
                        includeFile(this, file = file)
                    }
        }
    }

    override fun getDocumentType(documentId: String?): String {
        return getMimeType(getFileForDocId(docId = documentId ?: ""))
    }

    override fun createDocument(parentDocumentId: String?, mimeType: String?, displayName: String?): String {
        if (displayName == null) return ""
        val parent = getFileForDocId(parentDocumentId ?: "")
        val file = try {
            File(parent, displayName).apply {
                createNewFile()
                setWritable(true)
                setReadable(true)
            }
        } catch (err: Exception) {
            throw FileNotFoundException("Failed to create $displayName")
        }
        return getDocIdForFile(file)
    }

    private fun addUlaRoots(result: MatrixCursor): Cursor {
        val baseDir = File(context!!.scopedStorageRoot, "home")
        result.newRow().apply {
            add(Root.COLUMN_TITLE, context!!.getString(R.string.app_name))
            // Root for Ula storage should be the files dir
            add(Root.COLUMN_ROOT_ID, getDocIdForFile(baseDir))
            add(Root.COLUMN_DOCUMENT_ID, getDocIdForFile(baseDir))

            // Allow creation and searching
            add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE)
            add(Root.COLUMN_ICON, R.mipmap.ic_launcher)
            add(Root.COLUMN_AVAILABLE_BYTES, baseDir.freeSpace)
        }
        return result
    }

    private fun getDocIdForFile(file: File): String {
        return file.absolutePath
    }

    private fun getFileForDocId(docId: String): File {
        return File(docId)
    }

    private fun includeDocId(result: MatrixCursor, docId: String) {
        if (docId == "") return

        val file = getFileForDocId(docId)
        addToCursor(result, docId, file)
    }

    private fun includeFile(
        result: MatrixCursor,
        file: File
    ) {
        if (!file.exists()) return

        val docId = getDocIdForFile(file)
        addToCursor(result, docId, file)
    }

    private fun addToCursor(result: MatrixCursor, docId: String, file: File) {
        val editFlags = when {
            file.isDirectory && file.canWrite() -> Document.FLAG_DIR_SUPPORTS_CREATE
            file.canWrite() -> Document.FLAG_SUPPORTS_WRITE or Document.FLAG_SUPPORTS_DELETE
            else -> 0
        }

        val mimeType = getMimeType(file)
        val allFlags = if (mimeType.startsWith("image/")) {
            editFlags or Document.FLAG_SUPPORTS_THUMBNAIL
        } else editFlags

        result.newRow().apply {
            add(Document.COLUMN_DOCUMENT_ID, docId)
            add(Document.COLUMN_DISPLAY_NAME, file.name)
            add(Document.COLUMN_SIZE, file.length())
            add(Document.COLUMN_MIME_TYPE, mimeType)
            add(Document.COLUMN_LAST_MODIFIED, file.lastModified())
            add(Document.COLUMN_FLAGS, allFlags)
            add(Document.COLUMN_ICON, R.mipmap.ic_launcher)
        }
    }

    private fun getMimeType(file: File): String {
        if (file.isDirectory) return Document.MIME_TYPE_DIR
        val extension = file.name.substringAfterLast('.')
        if (extension != "") {
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            if (mimeType != null) return mimeType
        }
        return "application/octet-stream"
    }
}