package io.keystudio.kb.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * HSV colour picker: saturation/value field, hue bar, alpha bar and a hex field
 * that reads and writes the same value.
 *
 * Every gesture reports through [onLive] while the finger is still down, because
 * the point of this editor is that the keyboard behind it changes as you drag.
 * [onConfirm] only settles the result; [onDismiss] restores what was there.
 */
@Composable
fun ColorPickerDialog(
    title: String,
    initial: Int,
    onDismiss: () -> Unit,
    onLive: (Int) -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    var alpha by remember { mutableFloatStateOf(android.graphics.Color.alpha(initial) / 255f) }
    var hexText by remember { mutableStateOf(initial.toHex()) }

    fun current(): Int = android.graphics.Color.HSVToColor(
        (alpha * 255f).toInt().coerceIn(0, 255),
        floatArrayOf(hue, sat, value),
    )

    fun push() {
        val c = current()
        hexText = c.toHex()
        onLive(c)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = io.keystudio.kb.ui.Studio.colors.elevated,
        ) {
            Column(
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    title,
                    style = io.keystudio.kb.ui.StudioType.title,
                    color = io.keystudio.kb.ui.Studio.colors.text,
                )

                SaturationValueField(
                    hue = hue,
                    saturation = sat,
                    value = value,
                    onChange = { s, v -> sat = s; value = v; push() },
                )

                HueBar(hue = hue, onChange = { hue = it; push() })

                AlphaBar(
                    color = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)),
                    alpha = alpha,
                    onChange = { alpha = it; push() },
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .background(Color(current()), RoundedCornerShape(10.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)),
                    )
                    HexField(
                        text = hexText,
                        onText = { typed ->
                            hexText = typed
                            parseHex(typed)?.let { parsed ->
                                android.graphics.Color.colorToHSV(parsed, hsv)
                                hue = hsv[0]; sat = hsv[1]; value = hsv[2]
                                alpha = android.graphics.Color.alpha(parsed) / 255f
                                onLive(parsed)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = { onConfirm(current()) }) { Text("Done") }
                }
            }
        }
    }
}

@Composable
private fun SaturationValueField(
    hue: Float,
    saturation: Float,
    value: Float,
    onChange: (Float, Float) -> Unit,
) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.35f)
            .clip(RoundedCornerShape(14.dp))
            .trackTouch { offset, size ->
                onChange(
                    (offset.x / size.width).coerceIn(0f, 1f),
                    1f - (offset.y / size.height).coerceIn(0f, 1f),
                )
            },
    ) {
        drawRect(
            Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))),
        )
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))

        val cx = saturation * size.width
        val cy = (1f - value) * size.height
        // Two rings, dark over light, so the handle stays visible on any colour.
        drawCircle(Color.Black, radius = 11f, center = Offset(cx, cy), style = Stroke(width = 4f))
        drawCircle(Color.White, radius = 11f, center = Offset(cx, cy), style = Stroke(width = 2f))
    }
}

@Composable
private fun HueBar(hue: Float, onChange: (Float) -> Unit) {
    val spectrum = remember {
        (0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) }
    }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .trackTouch { offset, size ->
                onChange((offset.x / size.width).coerceIn(0f, 1f) * 360f)
            },
    ) {
        drawRect(Brush.horizontalGradient(spectrum))
        drawThumb(hue / 360f * size.width)
    }
}

@Composable
private fun AlphaBar(color: Int, alpha: Float, onChange: (Float) -> Unit) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .trackTouch { offset, size ->
                onChange((offset.x / size.width).coerceIn(0f, 1f))
            },
    ) {
        drawCheckerboard()
        drawRect(
            Brush.horizontalGradient(
                listOf(Color(color).copy(alpha = 0f), Color(color).copy(alpha = 1f)),
            ),
        )
        drawThumb(alpha * size.width)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawThumb(x: Float) {
    val cx = x.coerceIn(6f, size.width - 6f)
    val cy = size.height / 2f
    drawCircle(Color.Black, radius = 12f, center = Offset(cx, cy), style = Stroke(width = 5f))
    drawCircle(Color.White, radius = 12f, center = Offset(cx, cy), style = Stroke(width = 2.5f))
}

/** The familiar grey chequer, so partial alpha reads as transparency. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCheckerboard(cell: Float = 12f) {
    var y = 0f
    var row = 0
    while (y < size.height) {
        var x = 0f
        var col = 0
        while (x < size.width) {
            val dark = (row + col) % 2 == 0
            drawRect(
                color = if (dark) Color(0xFFBDBDBD) else Color(0xFFE8E8E8),
                topLeft = Offset(x, y),
                size = androidx.compose.ui.geometry.Size(
                    minOf(cell, size.width - x),
                    minOf(cell, size.height - y),
                ),
            )
            x += cell; col++
        }
        y += cell; row++
    }
}

/**
 * Down-and-drag tracking that reports the very first touch, unlike
 * `detectDragGestures`, which waits for touch slop. On a colour field a tap has
 * to land immediately or the control feels broken.
 */
private fun Modifier.trackTouch(
    onTouch: (Offset, androidx.compose.ui.unit.IntSize) -> Unit,
): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val down = awaitFirstDown(requireUnconsumed = false)
            onTouch(down.position, size)
            down.consume()
            var pressed = true
            while (pressed) {
                val event = awaitPointerEvent()
                event.changes.forEach { change ->
                    if (change.pressed) {
                        onTouch(change.position, size)
                        change.consume()
                    }
                }
                pressed = event.changes.any { it.pressed }
            }
        }
    }
}

@Composable
private fun HexField(text: String, onText: (String) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        BasicTextField(
            value = text,
            onValueChange = onText,
            singleLine = true,
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp,
            ),
            cursorBrush = Brush.verticalGradient(
                listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
