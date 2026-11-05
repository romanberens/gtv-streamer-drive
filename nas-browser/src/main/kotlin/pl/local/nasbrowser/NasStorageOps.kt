package pl.local.nasbrowser

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.security.MessageDigest
import java.security.DigestInputStream

data class NasDiskItem(
    val id: String,
    val name: String,
    val mime: String,
    val size: Long,
    val modified: Long
) {
    val isDirectory: Boolean get() = mime == Document.MIME_TYPE_DIR
}

/**
 * Operations are restricted to a single mounted drive and Android's granted SAF tree.
 * No raw filesystem paths are accepted. File moves are copy+verify+delete, because
 * the installed backend does not implement DocumentsProvider.moveDocument().
 */
class NasStorageOps(
    private val context: Context,
    private val rootId: String,
    private val writable: Boolean
) {
    private val resolver = context.contentResolver
    private val authority = "app.fayaz.otgmaster.documents"
    private val tree: Uri = DocumentsContract.buildTreeDocumentUri(authority, rootId)

    init { require(rootId.endsWith(":/")) { "Invalid root document ID" } }

    fun validId(documentId: String): Boolean {
        if (documentId != rootId && !documentId.startsWith(rootId)) return false
        val relative = documentId.removePrefix(rootId)
        return relative.length <= 2048 && relative.split('/').none {
            it == "." || it == ".." || it.contains('\\') || it.any { c -> c.code < 32 }
        }
    }

    private fun requireId(id: String, allowRoot: Boolean = true) {
        require(validId(id) && (allowRoot || id != rootId)) { "Element poza dyskiem lub katalog glowny" }
    }

    private fun requireWrite() {
        check(writable) { "Dysk jest w trybie tylko do odczytu" }
    }

    private fun document(id: String): Uri {
        requireId(id)
        return DocumentsContract.buildDocumentUriUsingTree(tree, id)
    }

    private fun childDocuments(parentId: String): Uri {
        requireId(parentId)
        return DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
    }

    fun item(id: String): NasDiskItem {
        requireId(id)
        val projection = arrayOf(
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED
        )
        return resolver.query(document(id), projection, null, null, null)?.use { c ->
            if (!c.moveToFirst()) error("Nie znaleziono elementu")
            NasDiskItem(
                c.getString(c.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)),
                c.getString(c.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)) ?: "",
                c.getString(c.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)) ?: "",
                c.getLong(c.getColumnIndexOrThrow(Document.COLUMN_SIZE)),
                c.getLong(c.getColumnIndexOrThrow(Document.COLUMN_LAST_MODIFIED))
            )
        } ?: error("Brak dostepu do dysku")
    }

    fun children(parentId: String): List<NasDiskItem> {
        requireId(parentId)
        val projection = arrayOf(
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED
        )
        return resolver.query(childDocuments(parentId), projection, null, null, null)?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val id = c.getString(c.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID))
                    if (!validId(id)) continue
                    add(NasDiskItem(
                        id,
                        c.getString(c.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)) ?: "",
                        c.getString(c.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)) ?: "",
                        c.getLong(c.getColumnIndexOrThrow(Document.COLUMN_SIZE)),
                        c.getLong(c.getColumnIndexOrThrow(Document.COLUMN_LAST_MODIFIED))
                    ))
                }
            }.sortedWith(compareByDescending<NasDiskItem> { it.isDirectory }.thenBy { it.name.lowercase() })
        } ?: error("Nie mozna odczytac katalogu")
    }

    private fun safeNewName(raw: String): String {
        val name = raw.trim()
        require(name.isNotBlank() && name != "." && name != ".." && name.length <= 120) {
            "Nazwa powinna miec od 1 do 120 znakow"
        }
        val forbidden = "\\/:*?\"<>|"
        require(name.none { it.code < 32 || it in forbidden } && !name.endsWith('.')) {
            "Nazwa zawiera niedozwolone znaki"
        }
        return name
    }

    private fun requireNoCollision(parentId: String, name: String, exceptId: String? = null) {
        if (children(parentId).any { it.name.equals(name, ignoreCase = true) && it.id != exceptId }) {
            error("Element o tej nazwie juz istnieje")
        }
    }

    fun createFolder(parentId: String, proposedName: String): String {
        requireWrite()
        check(item(parentId).isDirectory) { "Wybierz katalog docelowy" }
        val name = safeNewName(proposedName)
        requireNoCollision(parentId, name)
        val created = DocumentsContract.createDocument(
            resolver, document(parentId), Document.MIME_TYPE_DIR, name
        ) ?: error("Nie udalo sie utworzyc katalogu")
        return DocumentsContract.getDocumentId(created)
    }

    fun rename(id: String, proposedName: String): String {
        requireWrite()
        requireId(id, allowRoot = false)
        item(id)
        val name = safeNewName(proposedName)
        val parentId = id.substringBeforeLast('/', rootId).let {
            if (it.endsWith(':')) it + "/" else it
        }
        requireNoCollision(parentId, name, exceptId = id)
        val updated = DocumentsContract.renameDocument(resolver, document(id), name)
            ?: error("Zmiana nazwy nie powiodla sie")
        return DocumentsContract.getDocumentId(updated)
    }

    fun delete(id: String) {
        requireWrite()
        requireId(id, allowRoot = false)
        item(id)
        check(DocumentsContract.deleteDocument(resolver, document(id))) { "Nie udalo sie usunac" }
    }

    fun copyOrMoveFile(sourceId: String, targetFolderId: String, move: Boolean) {
        requireWrite()
        requireId(sourceId, allowRoot = false)
        requireId(targetFolderId)
        val source = item(sourceId)
        require(!source.isDirectory) {
            "Przenoszenie katalogow nie jest jeszcze dostepne. Przenos pojedyncze pliki."
        }
        check(item(targetFolderId).isDirectory) { "Cel nie jest katalogiem" }
        requireNoCollision(targetFolderId, source.name)
        val created = DocumentsContract.createDocument(
            resolver, document(targetFolderId), source.mime.ifBlank { "application/octet-stream" },
            source.name
        ) ?: error("Nie udalo sie utworzyc pliku docelowego")

        var copyCompleted = false
        try {
            val sourceDigest = MessageDigest.getInstance("SHA-256")
            val bytes = (resolver.openInputStream(document(sourceId))
                ?: error("Nie mozna odczytac pliku")).use { input ->
                DigestInputStream(input, sourceDigest).use { hashedInput ->
                    (resolver.openOutputStream(created, "w")
                        ?: error("Nie mozna zapisac pliku")).use { output ->
                        hashedInput.copyTo(output, 128 * 1024)
                    }
                }
            }
            check(bytes == source.size) {
                "Rozmiar kopii nie zgadza sie z oryginalem; oryginal zachowany"
            }
            // Read the copy back from USB; don't delete source on silent disk corruption.
            val targetDigest = MessageDigest.getInstance("SHA-256")
            val storedBytes = (resolver.openInputStream(created)
                ?: error("Nie mozna zweryfikowac kopii")).use { input ->
                DigestInputStream(input, targetDigest).use { digestStream ->
                    val buffer = ByteArray(128 * 1024)
                    var count = 0L
                    while (true) {
                        val read = digestStream.read(buffer)
                        if (read < 0) break
                        count += read
                    }
                    count
                }
            }
            check(storedBytes == bytes &&
                MessageDigest.isEqual(sourceDigest.digest(), targetDigest.digest())) {
                "Kopia nie przeszla weryfikacji SHA-256; oryginal zachowany"
            }
            copyCompleted = true
        } finally {
            if (!copyCompleted) runCatching { DocumentsContract.deleteDocument(resolver, created) }
        }
        if (move) {
            check(DocumentsContract.deleteDocument(resolver, document(sourceId))) {
                "Kopia utworzona, ale nie usunieto oryginalu. Oba pliki pozostaly na dysku."
            }
        }
    }
}
