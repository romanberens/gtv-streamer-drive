package app.fayaz.otgmaster.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract
import app.fayaz.otgmaster.OtgMasterState

/**
 * Signature-protected handshake for the TV frontend. The system document provider must keep
 * MANAGE_DOCUMENTS, so this provider returns mounted roots and grants the signed frontend
 * prefix access to each tree. It never exposes raw block devices or credentials.
 */
class TvBridgeProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val columns = projection ?: arrayOf("document_id", "title", "writable", "free_bytes", "filesystem")
        val result = MatrixCursor(columns)
        val caller = callingPackage ?: return result
        val ctx = context ?: return result
        for (drive in OtgMasterState.mountedDrives) {
            val docId = VeraCryptDocumentProvider.rootDocIdForDrive(drive.id)
            val tree = DocumentsContract.buildTreeDocumentUri(VeraCryptDocumentProvider.AUTHORITY, docId)
            var grantFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            if (!drive.isReadOnly) grantFlags = grantFlags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            ctx.grantUriPermission(caller, tree, grantFlags)
            val values = mapOf(
                "document_id" to docId,
                "title" to drive.name,
                "writable" to if (drive.isReadOnly) 0 else 1,
                "free_bytes" to drive.fileSystem.freeSpace,
                "filesystem" to drive.filesystemName,
            )
            result.newRow().also { row -> columns.forEach { row.add(it, values[it]) } }
        }
        return result
    }

    override fun getType(uri: Uri) = "vnd.android.cursor.dir/vnd.pl.local.nasbrowser.root"
    override fun insert(uri: Uri, values: ContentValues?) = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
