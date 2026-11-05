package pl.local.nasbrowser

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

private const val AUTHORITY = "app.fayaz.otgmaster.documents"
private const val BRIDGE_AUTHORITY = "app.fayaz.otgmaster.tvbridge"
private const val BACKEND_PACKAGE = "app.fayaz.otgmaster"

data class StorageEntry(val documentId: String, val name: String, val mimeType: String, val size: Long, val modified: Long, val uri: Uri? = null) {
    val isDirectory get() = mimeType == Document.MIME_TYPE_DIR
    val contentUri: Uri get() = uri ?: DocumentsContract.buildDocumentUri(AUTHORITY, documentId)
}
data class StorageRoot(val documentId: String, val title: String, val writable: Boolean, val freeBytes: Long, val filesystem: String)
data class BrowserLocation(val documentId: String, val title: String)

class MainActivity : ComponentActivity() {
    private var refreshTick by mutableIntStateOf(0)
    private lateinit var uploadServer: PhoneUploadServer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val serviceIntent = Intent(this, UploadServerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent) else startService(serviceIntent)
        uploadServer = UploadServerRuntime.get(this)
        uploadServer.onScreenRefresh = { runOnUiThread { refreshTick++ } }
        setContent { BrowserApp() }
    }

    override fun onResume() {
        super.onResume()
        refreshTick++
    }


    override fun onDestroy() {
        if (::uploadServer.isInitialized) uploadServer.onScreenRefresh = null
        super.onDestroy()
    }

    @Composable
    private fun BrowserApp() {
        val roots = remember(refreshTick) { loadRoots() }
        val activeRoot = roots.firstOrNull()
        var current by remember { mutableStateOf<BrowserLocation?>(null) }
        var stack by remember { mutableStateOf(emptyList<BrowserLocation>()) }
        var pairingUrl by remember { mutableStateOf<String?>(null) }

        // A refresh after an upload must not jump back to the USB root directory.
        LaunchedEffect(activeRoot?.documentId) {
            current = activeRoot?.let { BrowserLocation(it.documentId, it.title) }
            stack = emptyList()
        }

        val location = current ?: activeRoot?.let { BrowserLocation(it.documentId, it.title) }
        val entries = remember(activeRoot?.documentId, location?.documentId, refreshTick) {
            if (activeRoot == null || location == null) emptyList()
            else loadChildren(activeRoot.documentId, location.documentId)
        }

        BackHandler(enabled = pairingUrl != null || stack.isNotEmpty()) {
            if (pairingUrl != null) {
                pairingUrl = null
            } else {
                current = stack.last()
                stack = stack.dropLast(1)
            }
        }

        MaterialTheme {
            Column(Modifier.fillMaxSize().background(Color(0xFF08131D))
                .padding(horizontal = 30.dp, vertical = 22.dp)) {

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).background(Color(0xFF65DEC5),
                        RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        Text("▤", fontSize = 29.sp, color = Color(0xFF08131D),
                            fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(15.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Panel NAS", color = Color.White, fontSize = 29.sp,
                            fontWeight = FontWeight.Bold)
                        Text("Dysk lokalny  •  Pliki  •  Transfer Wi-Fi",
                            color = Color(0xFFAAC0CB), fontSize = 13.sp)
                    }
                    Text("●  Google TV Streamer", color = Color(0xFF65DEC5), fontSize = 13.sp)
                    Spacer(Modifier.width(20.dp))
                    Text("v0.2 beta", color = Color(0xFFAAC0CB), fontSize = 12.sp)
                }

                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatusCard("DYSK USB",
                        activeRoot?.filesystem?.ifBlank { "NTFS" } ?: "Niepodłączony",
                        activeRoot?.title ?: "Uruchom obsługę USB",
                        activeRoot != null, Modifier.weight(1f))
                    StatusCard("WOLNE MIEJSCE",
                        activeRoot?.let { formatSize(it.freeBytes) } ?: "—",
                        "Dostępne na dysku", activeRoot != null, Modifier.weight(1f))
                    StatusCard("ZAPIS",
                        if (activeRoot?.writable == true) "Odczyt i zapis" else "Niedostępny",
                        if (activeRoot?.writable == true) "Telefon → USB" else "Sprawdź dostęp",
                        activeRoot?.writable == true, Modifier.weight(1f))
                    StatusCard("TELEFONY", uploadServer.pairedCount.toString() + " sparowanych",
                        "Transfer w sieci LAN", true, Modifier.weight(1f))
                }

                Spacer(Modifier.height(18.dp))
                if (pairingUrl != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Parowanie telefonu", color = Color.White, fontSize = 23.sp,
                            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TvButton("Wróć do plików") { pairingUrl = null }
                    }
                    Spacer(Modifier.height(12.dp))
                    PairingPanel(pairingUrl!!)
                } else {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.width(174.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("MENU", color = Color(0xFFAAC0CB), fontSize = 12.sp,
                                fontWeight = FontWeight.Bold)
                            SideNav("▤  Przeglądaj", true) { }
                            SideNav("▣  Sparuj telefon", false) {
                                pairingUrl = uploadServer.beginPairing()
                            }
                            SideNav("↻  Odśwież", false) { refreshTick++ }
                            SideNav("◉  Obsługa USB", false) { launchBackend() }
                            Spacer(Modifier.height(12.dp))
                            Column(Modifier.fillMaxWidth()
                                .background(Color(0xFF122332), RoundedCornerShape(12.dp))
                                .padding(14.dp)) {
                                Text("LOKALNA SIEĆ", color = Color(0xFFAAC0CB),
                                    fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(7.dp))
                                Text(uploadServer.address, color = Color(0xFF65DEC5),
                                    fontSize = 12.sp, maxLines = 3,
                                    overflow = TextOverflow.Ellipsis)
                            }
                        }

                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Przeglądarka plików", color = Color.White,
                                        fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                    Text(location?.title ?: "Brak dysku", color = Color(0xFFAAC0CB),
                                        fontSize = 13.sp, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis)
                                }
                                if (stack.isNotEmpty()) TvButton("← Wstecz") {
                                    current = stack.last()
                                    stack = stack.dropLast(1)
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            if (activeRoot == null) {
                                Column(Modifier.fillMaxSize()
                                    .background(Color(0xFF122332), RoundedCornerShape(13.dp))
                                    .padding(20.dp),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Dysk USB nie jest dostępny", color = Color.White,
                                        fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(10.dp))
                                    Text("Otwórz backend i zaakceptuj dostęp do urządzenia USB.",
                                        color = Color(0xFFAAC0CB), fontSize = 13.sp)
                                    Spacer(Modifier.height(15.dp))
                                    TvButton("Otwórz obsługę USB") { launchBackend() }
                                }
                            } else if (entries.isEmpty()) {
                                EmptyState("Folder jest pusty lub nie udało się odczytać plików.")
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(bottom = 22.dp)) {
                                    items(entries, key = { it.documentId }) { entry ->
                                        FileRow(entry) {
                                            if (entry.isDirectory) {
                                                location?.let { stack = stack + it }
                                                current = BrowserLocation(entry.documentId, entry.name)
                                            } else openFile(entry)
                                        }
                                    }
                                }
                            }
                        }

                        Column(Modifier.width(258.dp).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.fillMaxWidth()
                                .background(Color(0xFF122332), RoundedCornerShape(14.dp))
                                .padding(16.dp)) {
                                Text("Sparuj telefon", color = Color.White,
                                    fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                                Text("Wyślij plik bezpośrednio na dysk USB z telefonu.",
                                    color = Color(0xFFAAC0CB), fontSize = 13.sp)
                                Spacer(Modifier.height(13.dp))
                                TvButton("Pokaż kod QR") {
                                    pairingUrl = uploadServer.beginPairing()
                                }
                            }
                            Column(Modifier.fillMaxWidth().weight(1f)
                                .background(Color(0xFF122332), RoundedCornerShape(14.dp))
                                .padding(16.dp)) {
                                Text("Ostatnie transfery", color = Color.White,
                                    fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(12.dp))
                                val history = remember(refreshTick) { uploadServer.recentTransfers }
                                if (history.isEmpty()) {
                                    Text("Nie przesłano jeszcze żadnych plików.",
                                        color = Color(0xFFAAC0CB), fontSize = 13.sp)
                                } else {
                                    history.take(4).forEach { transfer ->
                                        Text("✓  " + transfer.name, color = Color.White,
                                            fontSize = 13.sp, maxLines = 1,
                                            overflow = TextOverflow.Ellipsis)
                                        Text(formatSize(transfer.size),
                                            color = Color(0xFF65DEC5), fontSize = 11.sp)
                                        Spacer(Modifier.height(12.dp))
                                    }
                                }
                            }
                            Column(Modifier.fillMaxWidth()
                                .background(Color(0xFF122332), RoundedCornerShape(14.dp))
                                .padding(16.dp)) {
                                Text("KOD AWARYJNY", color = Color(0xFFAAC0CB),
                                    fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(uploadServer.accessCode, color = Color(0xFF65DEC5),
                                    fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                Text("Widoczny na telewizorze", color = Color(0xFFAAC0CB),
                                    fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SideNav(label: String, active: Boolean, onClick: () -> Unit) {
        var focused by remember { mutableStateOf(false) }
        val shape = RoundedCornerShape(11.dp)
        val highlighted = focused || active
        Box(Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(if (highlighted) Color(0xFF65DEC5) else Color(0xFF182D3C))
            .border(if (focused) 2.dp else 0.dp, if (focused) Color.White else Color.Transparent, shape)
            .clickable(onClick = onClick).focusable()
            .padding(horizontal = 13.dp, vertical = 15.dp)) {
            Text(label, color = if (highlighted) Color(0xFF08131D) else Color.White,
                fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }

    private fun loadRoots(): List<StorageRoot> = runCatching {
        contentResolver.query(Uri.parse("content://$BRIDGE_AUTHORITY/roots"), arrayOf("document_id", "title", "writable", "free_bytes", "filesystem"), null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(StorageRoot(cursor.text("document_id"), cursor.text("title"), cursor.long("writable") == 1L, cursor.long("free_bytes"), cursor.text("filesystem")))
            }
        }.orEmpty().also { roots ->
            uploadServer.destination = roots.firstOrNull()?.let { root ->
                UploadDestination(DocumentsContract.buildTreeDocumentUri(AUTHORITY, root.documentId), root.documentId, root.title, root.writable, root.freeBytes)
            }
            UploadServerRuntime.refreshDestination(this)
        }
    }.getOrDefault(emptyList())

    private fun loadChildren(rootDocumentId: String, documentId: String): List<StorageEntry> = runCatching {
        contentResolver.query(
            DocumentsContract.buildChildDocumentsUriUsingTree(DocumentsContract.buildTreeDocumentUri(AUTHORITY, rootDocumentId), documentId),
            arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED),
            null, null, null
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(StorageEntry(
                    cursor.text(Document.COLUMN_DOCUMENT_ID), cursor.text(Document.COLUMN_DISPLAY_NAME),
                    cursor.text(Document.COLUMN_MIME_TYPE), cursor.long(Document.COLUMN_SIZE), cursor.long(Document.COLUMN_LAST_MODIFIED),
                    DocumentsContract.buildDocumentUriUsingTree(DocumentsContract.buildTreeDocumentUri(AUTHORITY, rootDocumentId), cursor.text(Document.COLUMN_DOCUMENT_ID))
                ))
            }.sortedWith(compareByDescending<StorageEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
        }.orEmpty()
    }.getOrDefault(emptyList())

    private fun Cursor.text(column: String) = getString(getColumnIndexOrThrow(column)) ?: ""
    private fun Cursor.long(column: String) = getLong(getColumnIndexOrThrow(column))

    private fun launchBackend() {
        try { startActivity(Intent().setComponent(ComponentName(BACKEND_PACKAGE, "$BACKEND_PACKAGE.MainActivity"))) }
        catch (_: ActivityNotFoundException) { Toast.makeText(this, "Brakuje modułu obsługi NTFS", Toast.LENGTH_LONG).show() }
    }

    private fun openFile(entry: StorageEntry) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(entry.contentUri, entry.mimeType.ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newRawUri(entry.name, entry.contentUri)
        }
        try { startActivity(intent) }
        catch (_: ActivityNotFoundException) { Toast.makeText(this, "Brak aplikacji do otwarcia tego pliku", Toast.LENGTH_LONG).show() }
        catch (e: SecurityException) { Toast.makeText(this, "Brak dostępu dla odtwarzacza", Toast.LENGTH_LONG).show() }
    }

    @Composable private fun TvButton(label: String, onClick: () -> Unit) {
        var focused by remember { mutableStateOf(false) }
        Box(Modifier.onFocusChanged { focused = it.isFocused }.clip(RoundedCornerShape(10.dp))
            .background(if (focused) Color(0xFF72D5C3) else Color(0xFF243B53)).clickable(onClick = onClick).focusable()
            .padding(horizontal = 24.dp, vertical = 14.dp)) {
            Text(label, color = if (focused) Color(0xFF07131F) else Color.White, fontWeight = FontWeight.Bold)
        }
    }

    @Composable private fun StatusCard(title: String, value: String, detail: String, ok: Boolean, modifier: Modifier = Modifier) {
        Column(modifier.background(Color(0xFF102A43), RoundedCornerShape(12.dp)).padding(16.dp)) {
            Text(title, color = Color(0xFF9FB3C8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(value, color = if (ok) Color(0xFF72D5C3) else Color(0xFFFFB4A9), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }

    @Composable private fun FileRow(entry: StorageEntry, onClick: () -> Unit) {
        var focused by remember { mutableStateOf(false) }
        Row(Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.clip(RoundedCornerShape(12.dp))
            .background(if (focused) Color(0xFF163A4A) else Color(0xFF102A43))
            .border(2.dp, if (focused) Color(0xFF72D5C3) else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).focusable().padding(horizontal = 20.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(if (entry.isDirectory) "📁" else fileIcon(entry.mimeType), fontSize = 28.sp, modifier = Modifier.size(46.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.name, color = Color.White, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(fileDetails(entry), color = Color(0xFF9FB3C8), fontSize = 13.sp)
            }
            Text(if (entry.isDirectory) "›" else "OK", color = Color(0xFF72D5C3), fontSize = 20.sp)
        }
    }

    @Composable private fun EmptyState(message: String) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(message, color = Color(0xFFBCCCDC), fontSize = 20.sp) }
    }

    @Composable private fun PairingPanel(url: String) {
        val bitmap = remember(url) { qrBitmap(url) }
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF102A43), RoundedCornerShape(16.dp)).padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(30.dp)
        ) {
            Image(bitmap.asImageBitmap(), contentDescription = "Kod QR do parowania telefonu", Modifier.size(270.dp).background(Color.White).padding(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Sparuj telefon", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text("1. Otwórz aparat w telefonie\n2. Zeskanuj kod QR\n3. Potwierdź otwarcie strony", color = Color(0xFFBCCCDC), fontSize = 18.sp, lineHeight = 28.sp)
                Spacer(Modifier.height(16.dp))
                Text("Kod jest jednorazowy i ważny przez 2 minuty.", color = Color(0xFF72D5C3), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Po sparowaniu ten telefon nie będzie już wymagał kodu.", color = Color.White, fontSize = 15.sp)
            }
        }
    }

    private fun qrBitmap(value: String): Bitmap {
        val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 640, 640)
        val pixels = IntArray(matrix.width * matrix.height) { index ->
            if (matrix[index % matrix.width, index / matrix.width]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
        return Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.RGB_565).apply { setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height) }
    }

    private fun fileIcon(type: String) = when {
        type.startsWith("video/") -> "🎬"; type.startsWith("audio/") -> "🎵"; type.startsWith("image/") -> "🖼"; else -> "📄"
    }
    private fun fileDetails(entry: StorageEntry): String {
        if (entry.isDirectory) return "Folder"
        val size = entry.size.takeIf { it > 0 }?.let(::formatSize) ?: "Nieznany rozmiar"
        val date = entry.modified.takeIf { it > 0 }?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)) }
        return listOfNotNull(size, date).joinToString(" · ")
    }
    private fun formatSize(bytes: Long) = when {
        bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1_024 -> "%.1f KB".format(bytes / 1_024.0)
        else -> "$bytes B"
    }
}
