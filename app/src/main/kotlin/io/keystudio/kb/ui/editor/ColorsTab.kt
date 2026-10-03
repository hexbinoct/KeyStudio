package io.keystudio.kb.ui.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.keystudio.core.model.AnimatedBackground
import io.keystudio.core.model.BackgroundSpec
import io.keystudio.core.model.ColorSpec
import io.keystudio.core.model.StrokeSpec
import io.keystudio.core.store.ProfileStore

/**
 * "Colors" -- every paint on the keyboard, in the order you notice them:
 * the backdrop, then the keys, then the text on the keys.
 */
@Composable
fun ColorsTab(editor: ProfileEditor, store: ProfileStore) {
    val theme = editor.theme
    var specTarget by remember { mutableStateOf<SpecTarget?>(null) }
    var intTarget by remember { mutableStateOf<IntTarget?>(null) }

    Column(Modifier.fillMaxWidth()) {
        Section("Background") {
            BackgroundSection(
                editor = editor,
                store = store,
                onPickFill = { current ->
                    specTarget = SpecTarget("Background", current) { spec ->
                        editor.editTheme("bg-fill") { it.copy(background = BackgroundSpec.Fill(spec)) }
                    }
                },
                onPickAnimatedColor = { index, current ->
                    intTarget = IntTarget("Background colour ${index + 1}", current) { color ->
                        editor.editTheme("bg-anim-$index") { t ->
                            val bg = t.background as? BackgroundSpec.Animated ?: return@editTheme t
                            val colors = bg.colors.toMutableList()
                            if (index in colors.indices) colors[index] = color else colors.add(color)
                            t.copy(background = bg.copy(colors = colors))
                        }
                    }
                },
            )
        }

        Section("Keys") {
            ColorSlotRow("Letter keys", theme.keyFill) {
                specTarget = SpecTarget("Letter keys", theme.keyFill) { spec ->
                    editor.editTheme("keyFill") { it.copy(keyFill = spec) }
                }
            }
            RowDivider()
            ColorSlotRow("Modifier keys", theme.modifierFill) {
                specTarget = SpecTarget("Modifier keys", theme.modifierFill) { spec ->
                    editor.editTheme("modFill") { it.copy(modifierFill = spec) }
                }
            }
            RowDivider()
            ColorSlotRow("Action key", theme.accentFill) {
                specTarget = SpecTarget("Action key", theme.accentFill) { spec ->
                    editor.editTheme("accFill") { it.copy(accentFill = spec) }
                }
            }
            RowDivider()
            ColorSlotRow("Pressed", theme.pressedFill) {
                specTarget = SpecTarget("Pressed key", theme.pressedFill) { spec ->
                    editor.editTheme("pressFill") { it.copy(pressedFill = spec) }
                }
            }
        }

        Section("Text") {
            ColorIntRow("Letter labels", theme.textColor) {
                intTarget = IntTarget("Letter labels", theme.textColor) { c ->
                    editor.editTheme("text") { it.copy(textColor = c) }
                }
            }
            RowDivider()
            ColorIntRow("Modifier labels", theme.modifierTextColor) {
                intTarget = IntTarget("Modifier labels", theme.modifierTextColor) { c ->
                    editor.editTheme("modText") { it.copy(modifierTextColor = c) }
                }
            }
            RowDivider()
            ColorIntRow("Action label", theme.accentTextColor) {
                intTarget = IntTarget("Action label", theme.accentTextColor) { c ->
                    editor.editTheme("accText") { it.copy(accentTextColor = c) }
                }
            }
            RowDivider()
            ColorIntRow("Long-press hints", theme.hintColor) {
                intTarget = IntTarget("Long-press hints", theme.hintColor) { c ->
                    editor.editTheme("hint") { it.copy(hintColor = c) }
                }
            }
        }

        Section("Outline") {
            SwitchRow(
                label = "Outline keys",
                checked = theme.stroke != null,
                onChange = { on ->
                    editor.editTheme {
                        it.copy(stroke = if (on) StrokeSpec(it.textColor, 1f) else null)
                    }
                    editor.settle()
                },
            )
            theme.stroke?.let { stroke ->
                RowDivider()
                ColorIntRow("Outline colour", stroke.color) {
                    intTarget = IntTarget("Outline colour", stroke.color) { c ->
                        editor.editTheme("strokeColor") {
                            it.copy(stroke = it.stroke?.copy(color = c))
                        }
                    }
                }
                RowDivider()
                SliderRow(
                    label = "Outline width",
                    value = stroke.widthDp,
                    range = 0.5f..6f,
                    onChange = { w ->
                        editor.editTheme("strokeWidth") { it.copy(stroke = it.stroke?.copy(widthDp = w)) }
                    },
                    onSettled = editor::settle,
                    format = { "%.1f dp".format(it) },
                )
            }
        }

        Section("Long-press popup") {
            SwitchRow(
                label = "Custom popup colours",
                checked = theme.popupFill != null,
                onChange = { on ->
                    editor.editTheme {
                        if (on) it.copy(popupFill = it.modifierFill, popupTextColor = it.textColor)
                        else it.copy(popupFill = null, popupTextColor = null)
                    }
                    editor.settle()
                },
            )
            theme.popupFill?.let { fill ->
                RowDivider()
                ColorSlotRow("Popup background", fill) {
                    specTarget = SpecTarget("Popup background", fill) { spec ->
                        editor.editTheme("popupFill") { it.copy(popupFill = spec) }
                    }
                }
                RowDivider()
                ColorIntRow("Popup text", theme.popupTextColor ?: theme.textColor) {
                    intTarget = IntTarget("Popup text", theme.popupTextColor ?: theme.textColor) { c ->
                        editor.editTheme("popupText") { it.copy(popupTextColor = c) }
                    }
                }
            }
        }
    }

    ColorDialogHost(
        specTarget = specTarget,
        intTarget = intTarget,
        onClose = { specTarget = null; intTarget = null },
        onSettled = editor::settle,
    )
}

private enum class BackgroundKind { Colour, Animated, Image }

@Composable
private fun BackgroundSection(
    editor: ProfileEditor,
    store: ProfileStore,
    onPickFill: (ColorSpec) -> Unit,
    onPickAnimatedColor: (Int, Int) -> Unit,
) {
    val context = LocalContext.current
    val background = editor.theme.background
    val kind = when (background) {
        is BackgroundSpec.Fill -> BackgroundKind.Colour
        is BackgroundSpec.Animated -> BackgroundKind.Animated
        is BackgroundSpec.Image -> BackgroundKind.Image
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            store.importImage(context, uri)?.let { path ->
                editor.editTheme { theme ->
                    // Swapping the photo keeps the dim and blur already dialled
                    // in; they describe the treatment, not the particular image.
                    val previous = theme.background as? BackgroundSpec.Image
                    theme.copy(
                        background = BackgroundSpec.Image(
                            uri = path,
                            scrim = previous?.scrim ?: 0.2f,
                            blurRadius = previous?.blurRadius ?: 0f,
                        ),
                    )
                }
                editor.settle()
                // The file it replaced is now unreferenced.
                store.pruneImages()
            }
        }
    }

    Segmented(
        options = BackgroundKind.entries.toList(),
        selected = kind,
        labelOf = { it.name },
        modifier = Modifier.padding(12.dp),
        onSelect = { chosen ->
            if (chosen == kind) return@Segmented
            when (chosen) {
                BackgroundKind.Colour -> editor.editTheme {
                    it.copy(background = BackgroundSpec.Fill(fallbackFill(it.background)))
                }
                BackgroundKind.Animated -> editor.editTheme {
                    it.copy(
                        background = BackgroundSpec.Animated(
                            kind = AnimatedBackground.Aurora,
                            colors = animatedSeed(it.background),
                        ),
                    )
                }
                BackgroundKind.Image -> pickImage.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            }
            editor.settle()
        },
    )

    when (background) {
        is BackgroundSpec.Fill -> {
            RowDivider(0.dp)
            ColorSlotRow("Colour", background.color) { onPickFill(background.color) }
        }

        is BackgroundSpec.Animated -> {
            RowDivider(0.dp)
            ChipRow(
                options = AnimatedBackground.entries.toList(),
                selected = background.kind,
                labelOf = { prettyName(it.name) },
                onSelect = { k ->
                    editor.editTheme { it.copy(background = background.copy(kind = k)) }
                    editor.settle()
                },
            )
            background.colors.forEachIndexed { index, color ->
                RowDivider()
                ColorIntRow("Colour ${index + 1}", color) { onPickAnimatedColor(index, color) }
            }
            RowDivider()
            SliderRow(
                label = "Speed",
                value = background.speed,
                range = 0.1f..3f,
                onChange = { s ->
                    editor.editTheme("bgSpeed") { it.copy(background = background.copy(speed = s)) }
                },
                onSettled = editor::settle,
                format = { "%.1f×".format(it) },
            )
            RowDivider()
            SliderRow(
                label = "Intensity",
                value = background.intensity,
                range = 0f..2f,
                onChange = { i ->
                    editor.editTheme("bgIntensity") {
                        it.copy(background = background.copy(intensity = i))
                    }
                },
                onSettled = editor::settle,
                format = { "%.1f×".format(it) },
            )
        }

        is BackgroundSpec.Image -> {
            RowDivider(0.dp)
            SliderRow(
                label = "Dim",
                value = background.scrim,
                range = 0f..0.85f,
                onChange = { v ->
                    editor.editTheme("bgScrim") { it.copy(background = background.copy(scrim = v)) }
                },
                onSettled = editor::settle,
                format = { "${(it * 100).toInt()}%" },
            )
            RowDivider()
            SliderRow(
                label = "Blur",
                value = background.blurRadius,
                range = 0f..24f,
                onChange = { v ->
                    editor.editTheme("bgBlur") {
                        it.copy(background = background.copy(blurRadius = v))
                    }
                },
                onSettled = editor::settle,
                format = { "%.0f".format(it) },
            )
            RowDivider()
            GroupAction("Choose a different image", icon = io.keystudio.kb.ui.StudioIcons.Image) {
                pickImage.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            }
        }
    }
}

/** Keeps a colour when switching background kinds instead of resetting to black. */
private fun fallbackFill(previous: BackgroundSpec): ColorSpec = when (previous) {
    is BackgroundSpec.Fill -> previous.color
    is BackgroundSpec.Animated -> ColorSpec.Solid(previous.colors.firstOrNull() ?: 0xFF101014.toInt())
    is BackgroundSpec.Image -> ColorSpec.Solid(0xFF101014.toInt())
}

private fun animatedSeed(previous: BackgroundSpec): List<Int> = when (previous) {
    is BackgroundSpec.Animated -> previous.colors
    is BackgroundSpec.Fill -> when (val c = previous.color) {
        is ColorSpec.Solid -> listOf(c.color, c.color)
        is ColorSpec.LinearGradient -> c.colors
        is ColorSpec.RadialGradient -> c.colors
    }
    is BackgroundSpec.Image -> listOf(0xFF1B1B2F.toInt(), 0xFF4A2FBD.toInt())
}.let { if (it.size >= 2) it else it + listOf(0xFF4A2FBD.toInt()) }
