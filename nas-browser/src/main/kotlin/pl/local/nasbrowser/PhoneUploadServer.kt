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
import kotlin.random.Random

data class UploadDestination(val treeUri: Uri, val rootDocumentId: String, val title: String, val writable: Boolean, val freeBytes: Long)

class PhoneUploadServer(private val context: Context, private val onUploaded: (String) -> Unit) : NanoHTTPD(PORT) {
    private val prefs = context.getSharedPreferences("phone_upload", Context.MODE_PRIVATE)
    @Volatile private var pairingToken: String? = null
    @Volatile private var pairingExpiresAt: Long = 0
    @Volatile var destination: UploadDestination? = null
    val accessCode: String = context.getSharedPreferences("phone_upload", Context.MODE_PRIVATE).getString("code", null)
        ?: Random.nextInt(100_000, 1_000_000).toString().also { context.getSharedPreferences("phone_upload", Context.MODE_PRIVATE).edit().putString("code", it).apply() }
    val address: String get() = "http://${localIpv4()}:$PORT"
    val pairingUrl: String? get() = pairingToken?.takeIf { System.currentTimeMillis() < pairingExpiresAt }?.let { "$address/pair?token=$it" }
    val pairedCount: Int get() = prefs.getStringSet("paired_tokens", emptySet()).orEmpty().size

    fun beginPairing(): String {
        pairingToken = randomToken(24)
        pairingExpiresAt = System.currentTimeMillis() + 120_000
        return pairingUrl!!
    }

    override fun serve(session: IHTTPSession): Response {
        if (session.method == Method.GET && session.uri == "/pair") return pair(session)
        if (session.method == Method.GET) return page(null, isPaired(session))
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
            val parent = DocumentsContract.buildDocumentUriUsingTree(target.treeUri, target.rootDocumentId)
            val created = DocumentsContract.createDocument(context.contentResolver, parent, mime, name)
                ?: return page("Nie udało się utworzyć pliku na dysku.", paired)
            try {
                context.contentResolver.openOutputStream(created, "w")!!.use { output -> source.inputStream().use { it.copyTo(output, 256 * 1024) } }
            } catch (error: Exception) {
                runCatching { DocumentsContract.deleteDocument(context.contentResolver, created) }
                throw error
            }
            onUploaded(name)
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
        return response
    }

    private fun isPaired(session: IHTTPSession): Boolean {
        val cookie = session.headers["cookie"].orEmpty().split(';').map { it.trim() }
            .firstOrNull { it.startsWith("nas_device=") }?.substringAfter('=') ?: return false
        return hash(cookie) in prefs.getStringSet("paired_tokens", emptySet()).orEmpty()
    }

    private fun page(message: String?, paired: Boolean): Response {
        val target = destination
        val status = when { target == null -> "Dysk niepodłączony"; target.writable -> "Cel: ${escape(target.title)}"; else -> "${escape(target.title)} — tylko odczyt" }
        val note = message?.let { "<p class=message>${escape(it)}</p>" }.orEmpty()
        val credential = if (paired) "<p><small>✓ Sparowany telefon</small></p>" else "<label>Kod z telewizora<input name=code inputmode=numeric pattern=\"[0-9]{6}\" required></label>"
        val html = """<!doctype html><html lang=pl><head><meta charset=utf-8><meta name=viewport content="width=device-width,initial-scale=1"><title>Dysk domowy</title><style>
        body{font-family:system-ui;background:#07131f;color:#fff;margin:0;padding:32px}main{max-width:560px;margin:auto}.card{background:#102a43;padding:24px;border-radius:18px}
        input,button{box-sizing:border-box;width:100%;font-size:18px;padding:14px;margin-top:14px;border-radius:10px;border:1px solid #486581}button{background:#72d5c3;color:#07131f;font-weight:700;border:0}.message{background:#243b53;padding:12px;border-radius:8px}small{color:#bcccdc}</style></head>
        <body><main><h1>Dysk domowy</h1><p>$status</p><div class=card>$note<form method=post enctype=multipart/form-data>$credential
        <label>Wybierz materiał<input type=file name=file required></label><button type=submit>Wyślij na dysk</button></form><p><small>Nie odłączaj dysku podczas przesyłania.</small></p></div></main></body></html>"""
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
    }

    private fun htmlPage(message: String) = newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", """<!doctype html><html lang=pl><head><meta charset=utf-8><meta name=viewport content="width=device-width,initial-scale=1"><style>body{font-family:system-ui;background:#07131f;color:white;padding:32px;max-width:560px;margin:auto}a{color:#72d5c3}</style></head><body><h1>Panel NAS</h1><p>$message</p></body></html>""")
    private fun randomToken(bytes: Int): String = ByteArray(bytes).also { SecureRandom().nextBytes(it) }.let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun safeName(name: String) = name.replace(Regex("[^\\p{L}\\p{N}._() -]"), "_").take(180).ifBlank { "plik" }
    private fun escape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun localIpv4(): String = Collections.list(NetworkInterface.getNetworkInterfaces()).asSequence().filter { it.isUp && !it.isLoopback }
        .flatMap { Collections.list(it.inetAddresses).asSequence() }.firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains(':') == false }?.hostAddress ?: "0.0.0.0"
    companion object { const val PORT = 8789 }
}
