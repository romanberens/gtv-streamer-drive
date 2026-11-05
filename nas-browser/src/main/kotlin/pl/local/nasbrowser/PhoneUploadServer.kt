package pl.local.nasbrowser

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import fi.iki.elonen.NanoHTTPD
import java.net.NetworkInterface
import java.net.URLConnection
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Collections
import org.json.JSONArray
import org.json.JSONObject

data class UploadDestination(val treeUri: Uri, val rootDocumentId: String, val title: String, val writable: Boolean, val freeBytes: Long)

data class RecentTransfer(val name: String, val size: Long, val timestamp: Long)

class PhoneUploadServer(private val context: Context, private val onUploaded: (String) -> Unit) : NanoHTTPD(PORT) {
    private val prefs = context.getSharedPreferences("phone_upload", Context.MODE_PRIVATE)
    private val csrfSecret = randomToken(32)
    @Volatile private var pairingToken: String? = null
    @Volatile private var pairingExpiresAt: Long = 0
    @Volatile var destination: UploadDestination? = null
    @Volatile var onScreenRefresh: (() -> Unit)? = null
    val accessCode: String = context.getSharedPreferences("phone_upload", Context.MODE_PRIVATE).getString("code", null)
        ?: (SecureRandom().nextInt(900_000) + 100_000).toString().also { context.getSharedPreferences("phone_upload", Context.MODE_PRIVATE).edit().putString("code", it).apply() }
    val address: String get() = "http://${localIpv4()}:$PORT"
    val pairingUrl: String? get() = pairingToken?.takeIf { System.currentTimeMillis() < pairingExpiresAt }?.let { "$address/pair?token=$it" }
    val pairedCount: Int get() = prefs.getStringSet("paired_tokens", emptySet()).orEmpty().size

    val recentTransfers: List<RecentTransfer> get() = runCatching {
        val stored = JSONArray(prefs.getString("recent_transfers", "[]"))
        (0 until minOf(stored.length(), 5)).map { index ->
            val entry = stored.getJSONObject(index)
            RecentTransfer(entry.getString("name"), entry.getLong("size"), entry.getLong("timestamp"))
        }
    }.getOrDefault(emptyList())

    private fun recordTransfer(name: String, size: Long) {
        runCatching {
            val history = JSONArray(prefs.getString("recent_transfers", "[]"))
            val updated = JSONArray()
            updated.put(JSONObject().put("name", name).put("size", size)
                .put("timestamp", System.currentTimeMillis()))
            for (index in 0 until minOf(history.length(), 4)) {
                updated.put(history.getJSONObject(index))
            }
            prefs.edit().putString("recent_transfers", updated.toString()).apply()
        }
    }

    fun beginPairing(): String {
        pairingToken = randomToken(24)
        pairingExpiresAt = System.currentTimeMillis() + 120_000
        return pairingUrl!!
    }

    override fun serve(session: IHTTPSession): Response {
        if (session.uri.startsWith("/api/")) return serveApi(session)
        if (session.method == Method.GET && session.uri == "/pair") return pair(session)
        if (session.method == Method.GET) return page(null, isPaired(session), csrfFor(session))
        if (session.method != Method.POST) return newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, MIME_PLAINTEXT, "Method not allowed")
        return try {
            val tempFiles = HashMap<String, String>()
            session.parseBody(tempFiles)
            val paired = isPaired(session)
            if (!paired && session.parameters["code"]?.firstOrNull() != accessCode) return page("Nieprawidłowy kod dostępu.", false)
            val target = destination ?: return page("Dysk docelowy nie jest podłączony.", paired)
            if (!target.writable) return page("Dysk jest chroniony przed zapisem. Napraw NTFS w Windows poleceniem chkdsk /f.", paired)
            val tempPath = tempFiles["file"] ?: return page("Nie wybrano pliku.", paired)
            val source = java.io.File(tempPath)
            if (source.length() > target.freeBytes) return page("Za mało wolnego miejsca na dysku.", paired)
            val original = session.parameters["file"]?.firstOrNull()?.substringAfterLast('/')?.substringAfterLast('\\')?.takeIf { it.isNotBlank() }
                ?: "plik-${System.currentTimeMillis()}"
            val name = safeName(original)
            val mime = URLConnection.guessContentTypeFromName(name) ?: "application/octet-stream"
            val ops = NasStorageOps(context, target.rootDocumentId, target.writable)
            val folderId = session.parameters["folder"]?.firstOrNull()
                ?.takeIf { it.isNotBlank() } ?: target.rootDocumentId
            check(ops.item(folderId).isDirectory) { "Nieprawidlowy folder docelowy" }
            check(ops.children(folderId).none { it.name.equals(name, ignoreCase = true) }) {
                "Plik o tej nazwie juz istnieje. Zmien nazwe przed wyslaniem."
            }
            val parent = DocumentsContract.buildDocumentUriUsingTree(target.treeUri, folderId)
            val created = DocumentsContract.createDocument(context.contentResolver, parent, mime, name)
                ?: return page("Nie udało się utworzyć pliku na dysku.", paired)
            try {
                val bytes = context.contentResolver.openOutputStream(created, "w")!!.use { output ->
                    source.inputStream().use { it.copyTo(output, 256 * 1024) }
                }
                check(bytes == source.length()) { "Niepelen transfer - plik docelowy zostanie usuniety" }
            } catch (error: Exception) {
                runCatching { DocumentsContract.deleteDocument(context.contentResolver, created) }
                throw error
            }
            recordTransfer(name, source.length())
            onUploaded(name)
            onScreenRefresh?.invoke()
            page("Zapisano na ${escape(target.title)}: ${escape(name)}", paired)
        } catch (error: Exception) { page("Błąd zapisu na dysku: ${escape(error.message ?: "nieznany")}", isPaired(session)) }
    }

    private fun pair(session: IHTTPSession): Response {
        val supplied = session.parameters["token"]?.firstOrNull()
        val valid = supplied != null && supplied == pairingToken && System.currentTimeMillis() < pairingExpiresAt
        if (!valid) return htmlPage("Link parowania wygasł. Wyświetl nowy kod QR na telewizorze.")
        pairingToken = null
        pairingExpiresAt = 0
        val deviceToken = randomToken(32)
        val hashes = prefs.getStringSet("paired_tokens", emptySet()).orEmpty().toMutableSet().apply { add(hash(deviceToken)) }
        prefs.edit().putStringSet("paired_tokens", hashes).apply()
        val response = htmlPage("Telefon został sparowany. Możesz teraz wysyłać pliki bez wpisywania kodu.<br><br><a href='/'>Otwórz dysk domowy</a>")
        response.addHeader("Set-Cookie", "nas_device=$deviceToken; Path=/; HttpOnly; SameSite=Strict; Max-Age=31536000")
        onScreenRefresh?.invoke()
        return response
    }

    private fun pairedCookie(session: IHTTPSession): String? =
        session.headers["cookie"].orEmpty().split(';').map { it.trim() }
            .firstOrNull { it.startsWith("nas_device=") }?.substringAfter('=')

    private fun csrfFor(session: IHTTPSession): String? {
        val token = pairedCookie(session) ?: return null
        if (hash(token) !in prefs.getStringSet("paired_tokens", emptySet()).orEmpty()) return null
        return hash(token + "." + csrfSecret)
    }

    private fun json(status: Response.Status, payload: JSONObject): Response =
        newFixedLengthResponse(status, "application/json; charset=utf-8", payload.toString())

    private fun apiError(status: Response.Status, error: String): Response =
        json(status, JSONObject().put("ok", false).put("error", error))

    private fun serveApi(session: IHTTPSession): Response {
        if (!isPaired(session)) {
            return apiError(Response.Status.FORBIDDEN, "Sparuj telefon kodem QR, aby zarzadzac dyskiem.")
        }
        val target = destination
            ?: return apiError(Response.Status.SERVICE_UNAVAILABLE, "Dysk USB nie jest dostepny.")
        val ops = NasStorageOps(context, target.rootDocumentId, target.writable)

        return try {
            if (session.method == Method.GET && session.uri == "/api/list") {
                val folder = session.parameters["folder"]?.firstOrNull()
                    ?.takeIf { it.isNotBlank() } ?: target.rootDocumentId
                check(ops.item(folder).isDirectory) { "To nie jest katalog" }
                val data = JSONObject().put("ok", true).put("folder", folder)
                    .put("root", target.rootDocumentId)
                    .put("writable", target.writable)
                val entries = JSONArray()
                ops.children(folder).forEach { item ->
                    entries.put(JSONObject().put("id", item.id).put("name", item.name)
                        .put("directory", item.isDirectory).put("size", item.size)
                        .put("modified", item.modified))
                }
                return json(Response.Status.OK, data.put("items", entries))
            }
            if (session.method != Method.POST) {
                return apiError(Response.Status.METHOD_NOT_ALLOWED, "Nieobslugiwana operacja")
            }

            // File management is limited to paired browsers. Per-cookie CSRF token
            // prevents a different website from issuing destructive requests.
            session.parseBody(HashMap<String, String>())
            val csrf = csrfFor(session)
            if (csrf == null || session.parameters["csrf"]?.firstOrNull() != csrf) {
                return apiError(Response.Status.FORBIDDEN, "Nieprawidlowy token operacji. Odswiez strone.")
            }
            val origin = session.headers["origin"]
            val host = session.headers["host"]
            if (origin != null && (host == null || origin != "http://" + host)) {
                return apiError(Response.Status.FORBIDDEN, "Nieprawidlowe pochodzenie zadania.")
            }
            check(target.writable) { "Dysk jest tylko do odczytu" }
            fun param(name: String) = session.parameters[name]?.firstOrNull()
                ?: throw IllegalArgumentException("Brakuje parametru: " + name)

            when (session.uri) {
                "/api/mkdir" -> {
                    ops.createFolder(param("folder"), param("name"))
                    json(Response.Status.OK, JSONObject().put("ok", true))
                }
                "/api/rename" -> {
                    ops.rename(param("id"), param("name"))
                    json(Response.Status.OK, JSONObject().put("ok", true))
                }
                "/api/delete" -> {
                    ops.delete(param("id"))
                    json(Response.Status.OK, JSONObject().put("ok", true))
                }
                else -> apiError(Response.Status.NOT_FOUND, "Nieznana operacja")
            }
        } catch (error: Exception) {
            apiError(Response.Status.BAD_REQUEST, error.message ?: "Nie mozna wykonac operacji")
        }
    }

    private fun isPaired(session: IHTTPSession): Boolean {
        val cookie = pairedCookie(session) ?: return false
        return hash(cookie) in prefs.getStringSet("paired_tokens", emptySet()).orEmpty()
    }

    private fun page(message: String?, paired: Boolean, csrf: String? = null): Response {
        val target = destination
        val status = when {
            target == null -> "Dysk niepodlaczony"
            target.writable -> "Zapis dostepny: " + target.title
            else -> target.title + " - tylko odczyt"
        }
        val html = renderPhonePage(escape(status), paired, csrf.orEmpty(), message)
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
    }

    private fun htmlPage(message: String) = newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", """<!doctype html><html lang=pl><head><meta charset=utf-8><meta name=viewport content="width=device-width,initial-scale=1"><style>body{font-family:system-ui;background:#07131f;color:white;padding:32px;max-width:560px;margin:auto}a{color:#72d5c3}</style></head><body><h1>Panel NAS</h1><p>$message</p></body></html>""")
    private fun randomToken(bytes: Int): String = ByteArray(bytes).also { SecureRandom().nextBytes(it) }.let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun safeName(name: String) = name.replace(Regex("[^\\p{L}\\p{N}._() -]"), "_")
        .trim().take(120).trimEnd('.', ' ').ifBlank { "plik" }
    private fun escape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun localIpv4(): String = Collections.list(NetworkInterface.getNetworkInterfaces()).asSequence().filter { it.isUp && !it.isLoopback }
        .flatMap { Collections.list(it.inetAddresses).asSequence() }.firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains(':') == false }?.hostAddress ?: "0.0.0.0"
    companion object { const val PORT = 8789 }
}
