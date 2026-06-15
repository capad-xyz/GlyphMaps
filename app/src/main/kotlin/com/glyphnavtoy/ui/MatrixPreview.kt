package com.glyphnavtoy.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.glyphnavtoy.glyph.MatrixFrame

/**
 * Compose preview of the Glyph Matrix. Renders the same [MatrixFrame] that
 * `GlyphRenderer.push` sends to the LEDs, so on-screen ≡ on-back-of-phone.
 *
 * Off pixels (inside the mask) are rendered as a faint dark dot — gives the
 * matrix a tactile look and makes the masked corners visually obvious.
 *
 * @param applyMask if true (default), only LED positions inside the circular
 *  mask are drawn — matches what the phone actually lights up. Set false for
 *  the alt-layout preview that shows the full 13×13 grid (useful when
 *  inspecting the arrow + distance compositing).
 */
@Composable
fun MatrixPreview(
    frame: MatrixFrame,
    modifier: Modifier = Modifier,
    applyMask: Boolean = true,
    sizeDp: Dp = 260.dp,
) {
    val onColor = MaterialTheme.colorScheme.secondary // warm-white glow
    val offColor = MaterialTheme.colorScheme.surfaceVariant
    val ringColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)

    Canvas(
        modifier = modifier.size(sizeDp)
    ) {
        val n = MatrixFrame.SIZE
        val cell = size.minDimension / n
        val pixel = cell * 0.78f
        val pad = (cell - pixel) / 2f

        // Subtle outer ring suggesting the circular Matrix glass.
        if (applyMask) {
            drawCircle(
                color = ringColor,
                radius = size.minDimension / 2f - 2f,
                center = Offset(size.width / 2f, size.height / 2f),
                style = Stroke(width = 2f),
            )
        }

        for (y in 0 until n) {
            for (x in 0 until n) {
                if (applyMask && !frame.isLitPixel(x, y)) continue // masked corner
                val b = frame.get(x, y)
                val color = if (b > 0) {
                    onColor.copy(alpha = (b / 255f).coerceIn(0.15f, 1f))
                } else {
                    offColor.copy(alpha = 0.35f)
                }
                drawRect(
                    color = color,
                    topLeft = Offset(x * cell + pad, y * cell + pad),
                    size = Size(pixel, pixel),
                )
            }
        }
    }
}
