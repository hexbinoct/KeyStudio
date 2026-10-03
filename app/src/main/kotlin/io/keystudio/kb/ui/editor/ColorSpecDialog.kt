package io.keystudio.kb.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.keystudio.core.model.ColorSpec

private enum class SpecMode { Solid, Linear, Radial }

/**
 * Edits a whole [ColorSpec] -- the "solid or gradient" choice Keys Cafe offers
 * wherever it accepts a colour. Switching mode keeps the colours you already
 * chose, so trying a gradient and going back costs nothing.
 */
@Composable
fun ColorSpecDialog(
    title: String,
    initial: ColorSpec,
    onDismiss: () -> Unit,
    onLive: (ColorSpec) -> Unit,
    onConfirm: (ColorSpec) -> Unit,
    allowGradient: Boolean = true,
) {
    var mode by remember {
        mutableStateOf(
            when (initial) {
                is ColorSpec.Solid -> SpecMode.Solid
                is ColorSpec.LinearGradient -> SpecMode.Linear
                is ColorSpec.RadialGradient -> SpecMode.Radial
            }
        )
    }
    val colors = remember {
        mutableStateListOf<Int>().apply {
            when (initial) {
                is ColorSpec.Solid -> { add(initial.color); add(initial.color) }
                is ColorSpec.LinearGradient -> addAll(initial.colors.ifEmpty { listOf(0xFF000000.toInt()) })
                is ColorSpec.RadialGradient -> addAll(initial.colors.ifEmpty { listOf(0xFF000000.toInt()) })
            }
            if (size < 2) add(this[0])
        }
    }
    var angle by remember {
        mutableStateOf((initial as? ColorSpec.LinearGradient)?.angleDeg ?: 90f)
    }
    var centerX by remember {
        mutableStateOf((initial as? ColorSpec.RadialGradient)?.centerX ?: 0.5f)
    }
    var centerY by remember {
        mutableStateOf((initial as? ColorSpec.RadialGradient)?.centerY ?: 0.5f)
    }
    var radius by remember {
        mutableStateOf((initial as? ColorSpec.RadialGradient)?.radiusScale ?: 0.75f)
    }
    var editingStop by remember { mutableIntStateOf(-1) }

    fun build(): ColorSpec = when (mode) {
        SpecMode.Solid -> ColorSpec.Solid(colors.first())
        SpecMode.Linear -> ColorSpec.LinearGradient(colors.toList(), angle)
        SpecMode.Radial -> ColorSpec.RadialGradient(colors.toList(), centerX, centerY, radius)
    }

    fun push() = onLive(build())

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = io.keystudio.kb.ui.Studio.colors.elevated,
        ) {
            Column(
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    title,
                    style = io.keystudio.kb.ui.StudioType.title,
                    color = io.keystudio.kb.ui.Studio.colors.text,
                )

                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .background(build().toBrush(), RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
                )

                if (allowGradient) {
                    Segmented(
                        options = SpecMode.entries.toList(),
                        selected = mode,
                        onSelect = { mode = it; push() },
                        labelOf = { it.name },
                    )
                }

                if (mode == SpecMode.Solid) {
                    SectionLabel("Colour")
                    StopRow(
                        colors = listOf(colors.first()),
                        onEdit = { editingStop = 0 },
                        onAdd = null,
                        onRemove = null,
                    )
                } else {
                    SectionLabel("Gradient stops")
                    StopRow(
                        colors = colors,
                        onEdit = { editingStop = it },
                        onAdd = if (colors.size < 5) {
                            { colors.add(colors.last()); push() }
                        } else null,
                        onRemove = if (colors.size > 2) {
                            { colors.removeAt(colors.lastIndex); push() }
                        } else null,
                    )

                    if (mode == SpecMode.Linear) {
                        SliderRow(
                            label = "Angle",
                            value = angle,
                            range = 0f..360f,
                            onChange = { angle = it; push() },
                            format = { "${it.toInt()}°" },
                        )
                    } else {
                        SliderRow(
                            label = "Centre X",
                            value = centerX,
                            range = 0f..1f,
                            onChange = { centerX = it; push() },
                            format = { "%.2f".format(it) },
                        )
                        SliderRow(
                            label = "Centre Y",
                            value = centerY,
                            range = 0f..1f,
                            onChange = { centerY = it; push() },
                            format = { "%.2f".format(it) },
                        )
                        SliderRow(
                            label = "Radius",
                            value = radius,
                            range = 0.1f..2f,
                            onChange = { radius = it; push() },
                            format = { "%.2f".format(it) },
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = { onConfirm(build()) }) { Text("Done") }
                }
            }
        }
    }

    if (editingStop >= 0 && editingStop < colors.size) {
        val index = editingStop
        val before = colors[index]
        ColorPickerDialog(
            title = if (mode == SpecMode.Solid) "Colour" else "Stop ${index + 1}",
            initial = before,
            onDismiss = { colors[index] = before; editingStop = -1; push() },
            onLive = {
                colors[index] = it
                // A solid keeps both entries in step so switching to a gradient
                // starts from the colour you were just looking at.
                if (mode == SpecMode.Solid) {
                    for (i in colors.indices) colors[i] = it
                }
                push()
            },
            onConfirm = { editingStop = -1; push() },
        )
    }
}

@Composable
private fun StopRow(
    colors: List<Int>,
    onEdit: (Int) -> Unit,
    onAdd: (() -> Unit)?,
    onRemove: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        colors.forEachIndexed { index, color ->
            Box(
                Modifier
                    .size(40.dp)
                    .background(Color(color), RoundedCornerShape(10.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                    .clickable { onEdit(index) },
            )
        }
        if (onAdd != null) SmallGlyphButton("+", onAdd)
        if (onRemove != null) SmallGlyphButton("−", onRemove)
    }
}

@Composable
private fun SmallGlyphButton(glyph: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
