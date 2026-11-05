package pl.local.nasbrowser

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.unit.dp

/** Small, resolution-independent icons. No emoji fonts or extended icon dependency. */
internal enum class NasGlyph {
    LOGO, DRIVE, FOLDER, PHONE, WIFI, REFRESH, USB, UPLOAD,
    VIDEO, AUDIO, IMAGE, DOCUMENT, USERS, BACK, CHECK, QR, CLOCK, INFO
}

@Composable
internal fun NasIcon(
    glyph: NasGlyph,
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    Canvas(modifier.size(24.dp)) {
        val sx = size.width / 24f
        val sy = size.height / 24f
        withTransform({ scale(sx, sy, pivot = Offset.Zero) }) {
            val outline = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
                drawLine(tint, Offset(x1, y1), Offset(x2, y2), strokeWidth = 1.8f, cap = StrokeCap.Round)
            fun rect(x: Float, y: Float, w: Float, h: Float, radius: Float = 2.2f) =
                drawRoundRect(tint, Offset(x, y), Size(w, h), CornerRadius(radius), style = outline)
            fun dot(x: Float, y: Float, radius: Float = 1.25f) =
                drawCircle(tint, radius, Offset(x, y))
            fun path(vararg points: Pair<Float, Float>, closed: Boolean = false) {
                val p = Path().apply {
                    points.firstOrNull()?.let { moveTo(it.first, it.second) }
                    points.drop(1).forEach { lineTo(it.first, it.second) }
                    if (closed) close()
                }
                drawPath(p, tint, style = outline)
            }

            when (glyph) {
                NasGlyph.LOGO -> {
                    rect(4f, 3.5f, 16f, 6.5f)
                    rect(4f, 14f, 16f, 6.5f)
                    dot(7.5f, 7f)
                    dot(7.5f, 17.5f)
                    line(11f, 7f, 17f, 7f)
                    line(11f, 17.5f, 17f, 17.5f)
                }
                NasGlyph.DRIVE -> {
                    rect(3f, 5f, 18f, 14f, 3f)
                    line(3f, 14f, 21f, 14f)
                    dot(7f, 17f)
                    line(12f, 17f, 17f, 17f)
                }
                NasGlyph.FOLDER -> {
                    val p = Path().apply {
                        moveTo(3f, 7f)
                        lineTo(3f, 19f)
                        lineTo(21f, 19f)
                        lineTo(21f, 9f)
                        lineTo(11f, 9f)
                        lineTo(8f, 6f)
                        lineTo(3f, 6f)
                        close()
                    }
                    drawPath(p, tint, style = outline)
                }
                NasGlyph.PHONE -> {
                    rect(7f, 2f, 10f, 20f, 2.2f)
                    line(10f, 5f, 14f, 5f)
                    dot(12f, 19f, 0.9f)
                }
                NasGlyph.WIFI -> {
                    drawArc(tint, 222f, 96f, false, topLeft = Offset(2f, 5f),
                        size = Size(20f, 16f), style = outline)
                    drawArc(tint, 225f, 90f, false, topLeft = Offset(6f, 10f),
                        size = Size(12f, 9f), style = outline)
                    dot(12f, 20f, 1.3f)
                }
                NasGlyph.REFRESH -> {
                    drawArc(tint, 45f, 286f, false, topLeft = Offset(4f, 4f),
                        size = Size(16f, 16f), style = outline)
                    path(16f to 3f, 20f to 5f, 20f to 10f)
                    path(20f to 5f, 16f to 8f)
                }
                NasGlyph.USB -> {
                    line(12f, 3f, 12f, 17f)
                    path(9f to 6f, 12f to 3f, 15f to 6f)
                    line(12f, 11f, 6f, 11f)
                    line(6f, 11f, 6f, 8f)
                    rect(4.7f, 6.4f, 2.6f, 2.6f, 0.4f)
                    line(12f, 15f, 18f, 12f)
                    dot(18.5f, 11.5f, 1.8f)
                    dot(12f, 20f, 2f)
                }
                NasGlyph.UPLOAD -> {
                    rect(3f, 14f, 18f, 7f, 2f)
                    line(12f, 16f, 12f, 3f)
                    path(7f to 8f, 12f to 3f, 17f to 8f)
                }
                NasGlyph.VIDEO -> {
                    rect(2.8f, 5f, 18.4f, 14f, 2.3f)
                    path(10f to 9f, 16f to 12f, 10f to 15f, closed = true)
                }
                NasGlyph.AUDIO -> {
                    line(15f, 4f, 15f, 17f)
                    path(15f to 4f, 21f to 2.5f, 21f to 8f)
                    dot(11f, 18f, 3f)
                    line(21f, 8f, 21f, 15f)
                    dot(18f, 17f, 3f)
                }
                NasGlyph.IMAGE -> {
                    rect(3f, 3f, 18f, 18f, 2.5f)
                    dot(8f, 8f, 1.8f)
                    path(4f to 18f, 10f to 12f, 13f to 15f, 16f to 11f, 20f to 16f)
                }
                NasGlyph.DOCUMENT -> {
                    path(5f to 2f, 15f to 2f, 20f to 7f, 20f to 22f, 5f to 22f, closed = true)
                    path(15f to 2f, 15f to 7f, 20f to 7f)
                    line(8f, 12f, 17f, 12f)
                    line(8f, 16f, 17f, 16f)
                }
                NasGlyph.USERS -> {
                    drawCircle(tint, 3.3f, Offset(9f, 8f), style = outline)
                    drawArc(tint, 200f, 140f, false, topLeft = Offset(2f, 14f),
                        size = Size(14f, 11f), style = outline)
                    drawArc(tint, 245f, 200f, false, topLeft = Offset(14f, 5f),
                        size = Size(7f, 7f), style = outline)
                    line(18f, 15f, 22f, 19f)
                }
                NasGlyph.BACK -> {
                    line(20f, 12f, 4f, 12f)
                    path(11f to 5f, 4f to 12f, 11f to 19f)
                }
                NasGlyph.CHECK -> path(4f to 12f, 10f to 18f, 20f to 6f)
                NasGlyph.QR -> {
                    rect(2f, 2f, 7f, 7f, 0.4f)
                    rect(15f, 2f, 7f, 7f, 0.4f)
                    rect(2f, 15f, 7f, 7f, 0.4f)
                    line(16f, 15f, 16f, 19f)
                    line(19f, 15f, 22f, 15f)
                    line(22f, 18f, 22f, 22f)
                    line(15f, 22f, 19f, 22f)
                    dot(5.5f, 5.5f)
                    dot(18.5f, 5.5f)
                    dot(5.5f, 18.5f)
                }
                NasGlyph.CLOCK -> {
                    drawCircle(tint, 9f, Offset(12f, 12f), style = outline)
                    path(12f to 7f, 12f to 12f, 16f to 14f)
                }
                NasGlyph.INFO -> {
                    drawCircle(tint, 9f, Offset(12f, 12f), style = outline)
                    dot(12f, 7.2f)
                    line(12f, 11f, 12f, 17f)
                }
            }
        }
    }
}
