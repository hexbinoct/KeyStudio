package io.keystudio.kb.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.keystudio.core.model.KeyColorEffect
import io.keystudio.core.model.KeyMotionEffect
import io.keystudio.core.model.ParticleEffect
import io.keystudio.kb.ui.Studio
import io.keystudio.kb.ui.StudioIcon
import io.keystudio.kb.ui.StudioIcons

/**
 * "Effects" -- Keys Cafe splits this into keyboard colour effect, key colour
 * effect and key motion effect, and keeps them independent of each other. So
 * does this: any of the three can be None without disturbing the others.
 *
 * There are no preview tiles here on purpose. The keyboard pinned below is the
 * real one, and tapping a key on it shows the effect properly -- a thumbnail
 * would only be a worse version of what is already on screen.
 */
@Composable
fun EffectsTab(editor: ProfileEditor) {
    val effects = editor.profile.effects
    var intTarget by remember { mutableStateOf<IntTarget?>(null) }

    Column(Modifier.fillMaxWidth()) {
        Section("Key motion", footer = "Tap the keyboard below to try each one.") {
            ChipRow(
                options = KeyMotionEffect.entries.toList(),
                selected = effects.motion,
                labelOf = { prettyName(it.name) },
                onSelect = { m ->
                    editor.edit { it.copy(effects = it.effects.copy(motion = m)) }
                    editor.settle()
                },
            )
        }

        Section("Key colour") {
            ChipRow(
                options = KeyColorEffect.entries.toList(),
                selected = effects.keyColor,
                labelOf = { prettyName(it.name) },
                onSelect = { k ->
                    editor.edit { it.copy(effects = it.effects.copy(keyColor = k)) }
                    editor.settle()
                },
            )
        }

        Section(
            "Particles",
            footer = if (effects.particle != ParticleEffect.None) {
                "Particles keep the keyboard drawing every frame while they fly."
            } else null,
        ) {
            ChipRow(
                options = ParticleEffect.entries.toList(),
                selected = effects.particle,
                labelOf = { prettyName(it.name) },
                onSelect = { p ->
                    editor.edit { it.copy(effects = it.effects.copy(particle = p)) }
                    editor.settle()
                },
            )
        }

        Section("Colour") {
            ColorIntRow("Effect colour", effects.accentColor) {
                intTarget = IntTarget("Effect colour", effects.accentColor) { c ->
                    editor.edit("accent") { it.copy(effects = it.effects.copy(accentColor = c)) }
                }
            }
            RowDivider()
            ListRow(
                "Particle palette",
                subtitle = if (effects.particleColors.isEmpty()) "Empty: particles use the effect colour"
                else "Particles cycle through these",
            )
            PaletteRow(
                colors = effects.particleColors,
                onEdit = { index ->
                    val current = effects.particleColors[index]
                    intTarget = IntTarget("Particle colour ${index + 1}", current) { c ->
                        editor.edit("particle-$index") { p ->
                            val list = p.effects.particleColors.toMutableList()
                            list[index] = c
                            p.copy(effects = p.effects.copy(particleColors = list))
                        }
                    }
                },
                onAdd = {
                    editor.edit {
                        it.copy(
                            effects = it.effects.copy(
                                particleColors = it.effects.particleColors + it.effects.accentColor,
                            ),
                        )
                    }
                    editor.settle()
                },
                onRemove = {
                    editor.edit {
                        it.copy(
                            effects = it.effects.copy(
                                particleColors = it.effects.particleColors.dropLast(1),
                            ),
                        )
                    }
                    editor.settle()
                },
            )
        }

        Section("Feel") {
            SliderRow(
                label = "Intensity",
                value = effects.intensity,
                range = 0f..2f,
                onChange = { v -> editor.edit("intensity") { it.copy(effects = it.effects.copy(intensity = v)) } },
                onSettled = editor::settle,
                format = { "%.1f×".format(it) },
            )
            RowDivider()
            SliderRow(
                label = "Duration",
                value = effects.durationMs.toFloat(),
                range = 80f..1200f,
                onChange = { v ->
                    editor.edit("duration") { it.copy(effects = it.effects.copy(durationMs = v.toInt())) }
                },
                onSettled = editor::settle,
                format = { "${it.toInt()} ms" },
            )
        }
    }

    ColorDialogHost(
        specTarget = null,
        intTarget = intTarget,
        onClose = { intTarget = null },
        onSettled = editor::settle,
    )
}

@Composable
private fun PaletteRow(
    colors: List<Int>,
    onEdit: (Int) -> Unit,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
) {
    val c = Studio.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        colors.forEachIndexed { index, color ->
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(color))
                    .border(1.dp, c.hairline, RoundedCornerShape(9.dp))
                    .clickable { onEdit(index) },
            )
        }
        if (colors.size < 6) PaletteButton(StudioIcons.Plus, onAdd)
        if (colors.isNotEmpty()) PaletteButton(StudioIcons.Minus, onRemove)
    }
}

@Composable
private fun PaletteButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val c = Studio.colors
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(c.fill)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        StudioIcon(icon, tint = c.textSecondary, size = 18.dp)
    }
}
