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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
        var gridView by remember { mutableStateOf(false) }
        var selected by remember { mutableStateOf<StorageEntry?>(null) }
        var nameDialog by remember { mutableStateOf<String?>(null) }
        var inputName by remember { mutableStateOf("") }
        var confirmDelete by remember { mutableStateOf(false) }
        var pasteItem by remember { mutableStateOf<StorageEntry?>(null) }
        var pasteMove by remember { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val storageOps = remember(activeRoot?.documentId, activeRoot?.writable) {
            activeRoot?.let { NasStorageOps(this@MainActivity, it.documentId, it.writable) }
        }

        fun perform(success: String, operation: () -> Unit, after: () -> Unit = {}) {
            if (busy) return
            busy = true
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching { operation() } }
                busy = false
                Toast.makeText(
                    this@MainActivity,
                    if (result.isSuccess) success else "Błąd: " +
                        (result.exceptionOrNull()?.message ?: "operacja nie powiodła się"),
                    Toast.LENGTH_LONG
                ).show()
                if (result.isSuccess) {
                    refreshTick++
                    selected = null
                    after()
                }
            }
        }

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

        BackHandler(
            enabled = nameDialog == null && !confirmDelete && selected == null &&
                (pairingUrl != null || stack.isNotEmpty() || pasteItem != null)
        ) {
            when {
                pairingUrl != null -> pairingUrl = null
                stack.isNotEmpty() -> {
                    current = stack.last()
                    stack = stack.dropLast(1)
                }
                pasteItem != null -> pasteItem = null
            }
        }

        MaterialTheme {
            Column(
                Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFF101E2C), Color(0xFF070F19))))
                    .padding(horizontal = 30.dp, vertical = 22.dp)
            ) {
                // Compact header leaves more room for the content on TV displays.
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(51.dp).background(Color(0xFF66E4C9), RoundedCornerShape(15.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        NasIcon(NasGlyph.LOGO, Modifier.size(30.dp), Color(0xFF09202A))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Panel NAS", color = Color.White, fontSize = 28.sp,
                            fontWeight = FontWeight.Bold)
                        Text("Twoje pliki i transfer z telefonu • Google TV Streamer",
                            color = Color(0xFFA7BDCC), fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.background(Color(0xFF173B3C), RoundedCornerShape(40.dp))
                            .padding(horizontal = 13.dp, vertical = 8.dp)) {
                        NasIcon(NasGlyph.WIFI, Modifier.size(17.dp), Color(0xFF66E4C9))
                        Spacer(Modifier.width(7.dp))
                        Text("LAN aktywny", color = Color(0xFFCBFFF2),
                            fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(16.dp))
                    Text("v0.2.1 • UI", color = Color(0xFF879EAE), fontSize = 12.sp)
                }

                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatusCard(NasGlyph.DRIVE, "DYSK USB",
                        activeRoot?.filesystem?.ifBlank { "NTFS" } ?: "Brak dysku",
                        activeRoot?.title ?: "Sprawdź połączenie USB",
                        activeRoot != null, Modifier.weight(1f))
                    StatusCard(NasGlyph.DRIVE, "WOLNE MIEJSCE",
                        activeRoot?.let { formatSize(it.freeBytes) } ?: "—",
                        "Dostępne na nośniku", activeRoot != null, Modifier.weight(1f))
                    StatusCard(NasGlyph.CHECK, "UPRAWNIENIA",
                        when (activeRoot?.writable) {
                            true -> "Odczyt i zapis"
                            false -> "Tylko odczyt"
                            null -> "Brak dysku"
                        },
                        if (activeRoot?.writable == true) "Transfer na dysk gotowy"
                        else "Wymaga sprawdzenia", activeRoot?.writable == true, Modifier.weight(1f))
                    StatusCard(NasGlyph.USERS, "SPAROWANE",
                        "${uploadServer.pairedCount} telefony",
                        "Połączenia lokalne", true, Modifier.weight(1f))
                }
                Spacer(Modifier.height(17.dp))

                if (pairingUrl != null) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        NasIcon(NasGlyph.QR, Modifier.size(28.dp), Color(0xFF66E4C9))
                        Spacer(Modifier.width(12.dp))
                        Text("Parowanie telefonu", Modifier.weight(1f),
                            color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        TvButton("Wróć do plików", NasGlyph.BACK) { pairingUrl = null }
                    }
                    Spacer(Modifier.height(12.dp))
                    PairingPanel(pairingUrl!!)
                } else {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                        Column(
                            Modifier.width(185.dp).fillMaxHeight()
                                .background(Color(0xFF112232), RoundedCornerShape(16.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            Text("NAWIGACJA", color = Color(0xFF849DAF), fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 10.dp, top = 8.dp, bottom = 4.dp))
                            SideNav(NasGlyph.FOLDER, "Przeglądaj", true) {
                                current = activeRoot?.let { BrowserLocation(it.documentId, it.title) }
                                stack = emptyList()
                            }
                            SideNav(NasGlyph.QR, "Sparuj telefon", false) {
                                pairingUrl = uploadServer.beginPairing()
                            }
                            SideNav(NasGlyph.REFRESH, "Odśwież", false) { refreshTick++ }
                            SideNav(NasGlyph.USB, "Obsługa USB", false) { launchBackend() }

                            Spacer(Modifier.weight(1f))
                            Column(Modifier.fillMaxWidth()
                                .background(Color(0xFF1B3444), RoundedCornerShape(12.dp))
                                .padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    NasIcon(NasGlyph.WIFI, Modifier.size(17.dp), Color(0xFF66E4C9))
                                    Spacer(Modifier.width(7.dp))
                                    Text("ADRES NAS", color = Color(0xFF9FB8C8),
                                        fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.height(7.dp))
                                Text(uploadServer.address, color = Color.White, fontSize = 12.sp,
                                    maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                            Text("Sterowanie: pilot • OK • Wstecz",
                                color = Color(0xFF829AA9), fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp))
                        }

                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        NasIcon(NasGlyph.FOLDER, Modifier.size(23.dp), Color(0xFFFFC777))
                                        Spacer(Modifier.width(10.dp))
                                        Text("Pliki na dysku", color = Color.White, fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(Modifier.height(5.dp))
                                    Text(
                                        (location?.title ?: "Brak podłączonego dysku") +
                                            if (activeRoot != null) "  •  ${entries.size} elementów" else "",
                                        color = Color(0xFF9BB2C1), fontSize = 12.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                                TvButton(
                                    if (gridView) "Lista" else "Miniatury",
                                    if (gridView) NasGlyph.DOCUMENT else NasGlyph.IMAGE
                                ) { gridView = !gridView }
                                if (stack.isNotEmpty()) {
                                    Spacer(Modifier.width(8.dp))
                                    TvButton("Wstecz", NasGlyph.BACK) {
                                        current = stack.last()
                                        stack = stack.dropLast(1)
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            if (activeRoot != null) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    TvButton("Nowy folder", NasGlyph.FOLDER) {
                                        inputName = ""
                                        nameDialog = "new"
                                    }
                                    val pending = pasteItem
                                    if (pending != null) {
                                        TvButton(
                                            if (pasteMove) "Przenieś tutaj" else "Kopiuj tutaj",
                                            NasGlyph.UPLOAD
                                        ) {
                                            if (storageOps != null && location != null) {
                                                perform(
                                                    if (pasteMove) "Plik przeniesiony" else "Plik skopiowany",
                                                    { storageOps.copyOrMoveFile(
                                                        pending.documentId, location.documentId, pasteMove
                                                    ) },
                                                    after = { pasteItem = null }
                                                )
                                            }
                                        }
                                        TvButton("Anuluj", NasGlyph.BACK) { pasteItem = null }
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                            }
                            if (busy) {
                                Text("Trwa operacja na dysku…", color = Color(0xFF66E4C9),
                                    fontSize = 12.sp)
                            }
                            if (activeRoot == null) {
                                EmptyState(NasGlyph.USB, "Dysk USB nie jest dostępny",
                                    "Sprawdź zasilanie, kabel i uprawnienia aplikacji NTFS.") {
                                    launchBackend()
                                }
                            } else if (entries.isEmpty()) {
                                EmptyState(NasGlyph.FOLDER, "Brak plików w tym folderze",
                                    "Folder jest pusty lub odczyt nie powiódł się.") { refreshTick++ }
                            } else {
                                val openEntry: (StorageEntry) -> Unit = { entry ->
                                    if (entry.isDirectory) {
                                        location?.let { stack = stack + it }
                                        current = BrowserLocation(entry.documentId, entry.name)
                                    } else openFile(entry)
                                }
                                if (gridView) {
                                    LazyVerticalGrid(
                                        columns = GridCells.Adaptive(minSize = 146.dp),
                                        verticalArrangement = Arrangement.spacedBy(9.dp),
                                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                                        contentPadding = PaddingValues(bottom = 28.dp)
                                    ) {
                                        gridItems(entries, key = { it.documentId }) { entry ->
                                            FileTile(entry, { openEntry(entry) }, { selected = entry })
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(7.dp),
                                        contentPadding = PaddingValues(bottom = 24.dp)
                                    ) {
                                        items(entries, key = { it.documentId }) { entry ->
                                            FileRow(entry, { openEntry(entry) }, { selected = entry })
                                        }
                                    }
                                }
                            }
                        }

                        Column(Modifier.width(250.dp).fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(11.dp)) {
                            Column(Modifier.fillMaxWidth()
                                .background(Color(0xFF142E3B), RoundedCornerShape(16.dp))
                                .padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(37.dp)
                                        .background(Color(0xFF22535A), RoundedCornerShape(10.dp)),
                                        contentAlignment = Alignment.Center) {
                                        NasIcon(NasGlyph.PHONE, Modifier.size(23.dp), Color(0xFF66E4C9))
                                    }
                                    Spacer(Modifier.width(9.dp))
                                    Text("Telefon → NAS", color = Color.White, fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.height(10.dp))
                                Text("Przesyłaj zdjęcia, filmy i inne pliki bezpośrednio na USB.",
                                    color = Color(0xFFA8BFCC), fontSize = 12.sp, lineHeight = 17.sp)
                                Spacer(Modifier.height(13.dp))
                                TvButton("Sparuj przez QR", NasGlyph.QR, primary = true) {
                                    pairingUrl = uploadServer.beginPairing()
                                }
                            }

                            Column(Modifier.fillMaxWidth().weight(1f)
                                .background(Color(0xFF112232), RoundedCornerShape(16.dp))
                                .padding(15.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    NasIcon(NasGlyph.CLOCK, Modifier.size(19.dp), Color(0xFF66E4C9))
                                    Spacer(Modifier.width(9.dp))
                                    Text("Ostatnie transfery", color = Color.White,
                                        fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.height(13.dp))
                                val history = remember(refreshTick) { uploadServer.recentTransfers }
                                if (history.isEmpty()) {
                                    Text("Brak ukończonych transferów.",
                                        color = Color(0xFF9BB2C1), fontSize = 12.sp)
                                } else {
                                    history.take(4).forEach { transfer ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            NasIcon(NasGlyph.CHECK, Modifier.size(16.dp),
                                                Color(0xFF66E4C9))
                                            Spacer(Modifier.width(7.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(transfer.name, color = Color.White,
                                                    fontSize = 12.sp, maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis)
                                                Text(formatSize(transfer.size) + " • Zakończono",
                                                    color = Color(0xFF9FB8C8), fontSize = 11.sp)
                                            }
                                        }
                                        Spacer(Modifier.height(13.dp))
                                    }
                                }
                            }

                            Column(Modifier.fillMaxWidth()
                                .background(Color(0xFF112232), RoundedCornerShape(14.dp))
                                .padding(13.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    NasIcon(NasGlyph.INFO, Modifier.size(16.dp), Color(0xFF66E4C9))
                                    Spacer(Modifier.width(6.dp))
                                    Text("KOD AWARYJNY", color = Color(0xFF9FB8C8),
                                        fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(uploadServer.accessCode, color = Color(0xFF66E4C9),
                                    fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // TV remote-accessible item menu. Nothing destructive happens without confirmation.
        selected?.let { entry ->
            Dialog(onDismissRequest = { selected = null }) {
                Column(Modifier.width(390.dp)
                    .background(Color(0xFF142737), RoundedCornerShape(19.dp))
                    .padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(entry.name, color = Color.White, fontSize = 20.sp,
                        fontWeight = FontWeight.Bold, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                    Text(if (entry.isDirectory) "Katalog" else fileDetails(entry),
                        color = Color(0xFF9BB2C1), fontSize = 13.sp)
                    if (activeRoot?.writable == true) {
                        TvButton("Zmień nazwę", NasGlyph.DOCUMENT) {
                            inputName = entry.name
                            nameDialog = "rename"
                        }
                        if (!entry.isDirectory) {
                            TvButton("Kopiuj do…", NasGlyph.DOCUMENT) {
                                pasteItem = entry
                                pasteMove = false
                                selected = null
                            }
                            TvButton("Przenieś do…", NasGlyph.UPLOAD) {
                                pasteItem = entry
                                pasteMove = true
                                selected = null
                            }
                        }
                        TvButton("Usuń", NasGlyph.INFO) { confirmDelete = true }
                    } else {
                        Text("Dysk tylko do odczytu", color = Color(0xFFFFB7A5))
                    }
                    TvButton("Zamknij", NasGlyph.BACK) { selected = null }
                }
            }
        }

        if (nameDialog != null) {
            val mode = nameDialog
            val entry = selected
            Dialog(onDismissRequest = { nameDialog = null }) {
                Column(Modifier.width(400.dp)
                    .background(Color(0xFF142737), RoundedCornerShape(18.dp))
                    .padding(24.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Text(if (mode == "new") "Nowy katalog" else "Zmień nazwę",
                        color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = inputName,
                        onValueChange = { inputName = it },
                        label = { Text("Nazwa") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TvButton("Zapisz", NasGlyph.CHECK) {
                        val ops = storageOps
                        val destination = location
                        val value = inputName
                        if (ops != null && destination != null) {
                            if (mode == "new") {
                                perform("Katalog utworzony", {
                                    ops.createFolder(destination.documentId, value)
                                })
                            } else if (entry != null) {
                                perform("Zmieniono nazwę", { ops.rename(entry.documentId, value) })
                            }
                            nameDialog = null
                        }
                    }
                    TvButton("Anuluj", NasGlyph.BACK) { nameDialog = null }
                }
            }
        }

        if (confirmDelete && selected != null) {
            val entry = selected!!
            Dialog(onDismissRequest = { confirmDelete = false }) {
                Column(Modifier.width(410.dp)
                    .background(Color(0xFF142737), RoundedCornerShape(18.dp))
                    .padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Usunąć na stałe?", color = Color(0xFFFFB7A5),
                        fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Text(entry.name, color = Color.White, fontSize = 17.sp)
                    Text(
                        if (entry.isDirectory)
                            "Uwaga: usuwanie katalogu może usunąć całą jego zawartość. Brak kosza."
                        else "Nie można cofnąć tej operacji. Brak kosza.",
                        color = Color(0xFF9BB2C1), fontSize = 13.sp
                    )
                    TvButton("Tak, usuń", NasGlyph.INFO) {
                        storageOps?.let { ops ->
                            perform("Usunięto element", { ops.delete(entry.documentId) })
                        }
                        confirmDelete = false
                    }
                    TvButton("Zachowaj", NasGlyph.BACK) { confirmDelete = false }
                }
            }
        }
    }

    @Composable
    private fun SideNav(glyph: NasGlyph, label: String, active: Boolean, onClick: () -> Unit) {
        var focused by remember { mutableStateOf(false) }
        val shape = RoundedCornerShape(11.dp)
        val highlighted = focused || active
        Row(
            Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
                .clip(shape)
                .background(if (highlighted) Color(0xFF66E4C9) else Color.Transparent)
                .border(if (focused) 2.dp else 0.dp,
                    if (focused) Color.White else Color.Transparent, shape)
                .clickable(onClick = onClick).focusable()
                .padding(horizontal = 11.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NasIcon(glyph, Modifier.size(19.dp),
                if (highlighted) Color(0xFF09202A) else Color(0xFF9EB7C6))
            Spacer(Modifier.width(11.dp))
            Text(label, color = if (highlighted) Color(0xFF09202A) else Color(0xFFE0EAF1),
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
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

    @Composable
    private fun TvButton(
        label: String,
        glyph: NasGlyph,
        primary: Boolean = false,
        onClick: () -> Unit
    ) {
        var focused by remember { mutableStateOf(false) }
        val shape = RoundedCornerShape(11.dp)
        val bright = focused || primary
        Row(
            Modifier.onFocusChanged { focused = it.isFocused }
                .clip(shape)
                .background(if (bright) Color(0xFF66E4C9) else Color(0xFF223A4D))
                .border(if (focused) 2.dp else 0.dp,
                    if (focused) Color.White else Color.Transparent, shape)
                .clickable(onClick = onClick).focusable()
                .padding(horizontal = 15.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            NasIcon(glyph, Modifier.size(18.dp),
                if (bright) Color(0xFF09202A) else Color.White)
            Spacer(Modifier.width(9.dp))
            Text(label, color = if (bright) Color(0xFF09202A) else Color.White,
                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }

    @Composable
    private fun StatusCard(
        glyph: NasGlyph, title: String, value: String, detail: String,
        ok: Boolean, modifier: Modifier = Modifier
    ) {
        Row(
            modifier.background(Color(0xFF142737), RoundedCornerShape(14.dp))
                .padding(horizontal = 15.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(40.dp).background(
                if (ok) Color(0xFF1E4A4C) else Color(0xFF46373E),
                RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                NasIcon(glyph, Modifier.size(23.dp),
                    if (ok) Color(0xFF66E4C9) else Color(0xFFFFB7A5))
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color(0xFF9BB2C1), fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1)
                Text(value, color = if (ok) Color.White else Color(0xFFFFB7A5),
                    fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, color = Color(0xFF9BB2C1), fontSize = 10.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    @Composable
    private fun FileRow(
        entry: StorageEntry, onClick: () -> Unit, onManage: () -> Unit
    ) {
        var focused by remember { mutableStateOf(false) }
        val shape = RoundedCornerShape(12.dp)
        val glyph = when {
            entry.isDirectory -> NasGlyph.FOLDER
            entry.mimeType.startsWith("video/") -> NasGlyph.VIDEO
            entry.mimeType.startsWith("audio/") -> NasGlyph.AUDIO
            entry.mimeType.startsWith("image/") -> NasGlyph.IMAGE
            else -> NasGlyph.DOCUMENT
        }
        val iconTint = when (glyph) {
            NasGlyph.FOLDER -> Color(0xFFFFC777)
            NasGlyph.VIDEO -> Color(0xFFBEA6FF)
            NasGlyph.AUDIO -> Color(0xFFFF9AB6)
            NasGlyph.IMAGE -> Color(0xFF83DBBA)
            else -> Color(0xFF9DC9F9)
        }
        Row(
            Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
                .clip(shape)
                .background(if (focused) Color(0xFF22545B) else Color(0xFF142737))
                .border(if (focused) 2.dp else 1.dp,
                    if (focused) Color(0xFF66E4C9) else Color(0xFF1C3848), shape)
                .clickable(onClick = onClick).focusable()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(42.dp)
                .background(iconTint.copy(alpha = 0.13f), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center) {
                NasIcon(glyph, Modifier.size(25.dp), iconTint)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.name, color = Color.White,
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text(fileDetails(entry), color = Color(0xFF9BB2C1),
                    fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TvButton("Opcje", NasGlyph.INFO, onClick = onManage)
        }
    }

    @Composable
    private fun FileTile(
        entry: StorageEntry, onClick: () -> Unit, onManage: () -> Unit
    ) {
        var focused by remember { mutableStateOf(false) }
        val shape = RoundedCornerShape(13.dp)
        Column(Modifier.onFocusChanged { focused = it.hasFocus }
            .clip(shape)
            .background(if (focused) Color(0xFF22545B) else Color(0xFF142737))
            .border(if (focused) 2.dp else 1.dp,
                if (focused) Color(0xFF66E4C9) else Color(0xFF1C3848), shape)
            .padding(9.dp)) {
            Box(Modifier.fillMaxWidth().height(92.dp)
                .clip(RoundedCornerShape(9.dp))
                .clickable(onClick = onClick).focusable()) {
                NasThumbnailPreview(entry, Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(8.dp))
            Text(entry.name, color = Color.White, fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 2,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.height(34.dp))
            Text(fileDetails(entry), color = Color(0xFF9BB2C1),
                fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(7.dp))
            TvButton("Opcje", NasGlyph.INFO, onClick = onManage)
        }
    }

    @Composable
    private fun EmptyState(
        glyph: NasGlyph, title: String, detail: String, action: () -> Unit
    ) {
        Column(Modifier.fillMaxSize()
            .background(Color(0xFF142737), RoundedCornerShape(16.dp))
            .padding(18.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            NasIcon(glyph, Modifier.size(47.dp), Color(0xFF66E4C9))
            Spacer(Modifier.height(15.dp))
            Text(title, color = Color.White, fontSize = 19.sp,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(7.dp))
            Text(detail, color = Color(0xFF9BB2C1), fontSize = 12.sp,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(17.dp))
            TvButton("Sprawdź ponownie", NasGlyph.REFRESH, onClick = action)
        }
    }

    @Composable
    private fun PairingPanel(url: String) {
        val bitmap = remember(url) { qrBitmap(url) }
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF142737),
                RoundedCornerShape(18.dp)).padding(25.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(29.dp)
        ) {
            Image(bitmap.asImageBitmap(), contentDescription = "Kod QR do parowania telefonu",
                modifier = Modifier.size(246.dp).clip(RoundedCornerShape(13.dp))
                    .background(Color.White).padding(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NasIcon(NasGlyph.PHONE, Modifier.size(29.dp), Color(0xFF66E4C9))
                    Spacer(Modifier.width(11.dp))
                    Text("Połącz telefon z NAS", color = Color.White, fontSize = 24.sp,
                        fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(16.dp))
                Text("1. Otwórz aparat w telefonie\n2. Zeskanuj kod QR\n3. Otwórz stronę przesyłania",
                    color = Color(0xFFB5CAD7), fontSize = 17.sp, lineHeight = 29.sp)
                Spacer(Modifier.height(15.dp))
                Text("Kod ważny przez 2 minuty · jednorazowe parowanie",
                    color = Color(0xFF66E4C9), fontSize = 14.sp,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Po sparowaniu telefon zostanie zapamiętany.",
                    color = Color(0xFFAFBEC9), fontSize = 13.sp)
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
