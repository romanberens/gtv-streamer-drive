package pl.local.nasbrowser

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.provider.DocumentsContract

private const val CHANNEL_ID = "nas_upload"
private const val NOTIFICATION_ID = 8789
private const val AUTHORITY = "app.fayaz.otgmaster.documents"
private const val BRIDGE_AUTHORITY = "app.fayaz.otgmaster.tvbridge"

object UploadServerRuntime {
    @Volatile private var instance: PhoneUploadServer? = null

    @Synchronized
    fun get(context: Context, onUploaded: (String) -> Unit = {}): PhoneUploadServer {
        instance?.let { return it }
        return PhoneUploadServer(context.applicationContext, onUploaded).also { server ->
            server.destination = readDestination(context)
            server.start(5_000, false)
            instance = server
        }
    }

    fun refreshDestination(context: Context) {
        instance?.destination = readDestination(context)
    }

    private fun readDestination(context: Context): UploadDestination? = runCatching {
        context.contentResolver.query(
            Uri.parse("content://$BRIDGE_AUTHORITY/roots"),
            arrayOf("document_id", "title", "writable", "free_bytes"),
            null, null, null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val id = cursor.getString(cursor.getColumnIndexOrThrow("document_id"))
            UploadDestination(
                DocumentsContract.buildTreeDocumentUri(AUTHORITY, id),
                id,
                cursor.getString(cursor.getColumnIndexOrThrow("title")),
                cursor.getLong(cursor.getColumnIndexOrThrow("writable")) == 1L,
                cursor.getLong(cursor.getColumnIndexOrThrow("free_bytes"))
            )
        }
    }.getOrNull()
}

class UploadServerService : Service() {
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Serwer NAS", NotificationManager.IMPORTANCE_LOW))
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Panel NAS")
            .setContentText("Przesyłanie z telefonu jest dostępne na porcie 8789")
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
        UploadServerRuntime.get(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        UploadServerRuntime.refreshDestination(this)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
