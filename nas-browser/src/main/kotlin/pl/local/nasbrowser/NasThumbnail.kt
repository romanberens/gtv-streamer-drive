package pl.local.nasbrowser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Preview decoded only for visible LazyVerticalGrid items, never on the main thread. */
@Composable
internal fun NasThumbnailPreview(entry: StorageEntry, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val glyph = when {
        entry.isDirectory -> NasGlyph.FOLDER
        entry.mimeType.startsWith("video/") -> NasGlyph.VIDEO
        entry.mimeType.startsWith("audio/") -> NasGlyph.AUDIO
        entry.mimeType.startsWith("image/") -> NasGlyph.IMAGE
        else -> NasGlyph.DOCUMENT
    }
    val preview by produceState<Bitmap?>(initialValue = null, entry.documentId) {
        value = withContext(Dispatchers.IO) {
            loadPreview(context, entry)
        }
    }
    Box(modifier.clip(RoundedCornerShape(11.dp))
        .background(Color(0xFF1B3547)), contentAlignment = Alignment.Center) {
        val bitmap = preview
        if (bitmap == null) {
            NasIcon(glyph, Modifier.size(44.dp), Color(0xFF84D9C6))
        } else {
            Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop)
        }
    }
}

private fun loadPreview(context: Context, entry: StorageEntry): Bitmap? = runCatching {
    when {
        entry.isDirectory -> null
        entry.mimeType.startsWith("image/") -> {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(entry.contentUri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) null else {
                var sample = 1
                while ((bounds.outWidth / sample) > 360 || (bounds.outHeight / sample) > 360) {
                    sample *= 2
                }
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                context.contentResolver.openInputStream(entry.contentUri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }
            }
        }
        entry.mimeType.startsWith("video/") -> {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, entry.contentUri)
                val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frame != null) {
                    val ratio = minOf(1f, 360f / maxOf(frame.width, frame.height).toFloat())
                    if (ratio < 1f) {
                        val small = Bitmap.createScaledBitmap(
                            frame, (frame.width * ratio).toInt().coerceAtLeast(1),
                            (frame.height * ratio).toInt().coerceAtLeast(1), true
                        )
                        frame.recycle()
                        small
                    } else frame
                } else null
            } finally {
                retriever.release()
            }
        }
        else -> null
    }
}.getOrNull()
