package io.keystudio.kb.ui.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.keystudio.core.model.FontFamily
import io.keystudio.core.model.ShapeStyle
import io.keystudio.core.store.ProfileStore
import io.keystudio.kb.ui.StudioIcons

/**
 * "Shape & size" -- the keycaps themselves, the type on them, and how much of
 * the screen the whole thing takes.
 *
 * Shape and font belong to the light/dark variant being edited; metrics belong
 * to the profile, because a keyboard that changed height at sunset would be
 * unusable.
 */
@Composable
fun ShapeTab(editor: ProfileEditor, store: ProfileStore) {
    val theme = editor.theme
    val metrics = editor.profile.metrics
    val context = LocalContext.current

    val pickFont = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            store.importFont(context, uri)?.let { path ->
                editor.editTheme { it.copy(font = it.font.copy(customFontPath = path)) }
                editor.settle()
            }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Section("Keys") {
            ChipRow(
                options = ShapeStyle.entries.toList(),
                selected = theme.shape.style,
                labelOf = { prettyName(it.name) },
                onSelect = { style ->
                    editor.editTheme { it.copy(shape = it.shape.copy(style = style)) }
                    editor.settle()
                },
            )
            RowDivider(0.dp)
            SliderRow(
                label = "Corner radius",
                value = theme.shape.cornerRadiusDp,
                range = 0f..28f,
                onChange = { v ->
                    editor.editTheme("corner") { it.copy(shape = it.shape.copy(cornerRadiusDp = v)) }
                },
                onSettled = editor::settle,
                format = { "%.0f dp".format(it) },
            )
            RowDivider()
            SliderRow(
                label = "Key inset",
                value = theme.shape.insetDp,
                range = 0f..14f,
                onChange = { v ->
                    editor.editTheme("inset") { it.copy(shape = it.shape.copy(insetDp = v)) }
                },
                onSettled = editor::settle,
                format = { "%.1f dp".format(it) },
            )
            RowDivider()
            SliderRow(
                label = "Shadow",
                value = theme.elevationDp,
                range = 0f..12f,
                onChange = { v -> editor.editTheme("elevation") { it.copy(elevationDp = v) } },
                onSettled = editor::settle,
                format = { if (it < 0.5f) "Off" else "%.0f dp".format(it) },
            )
        }

        Section("Type") {
            ChipRow(
                options = FontFamily.entries.toList(),
                selected = theme.font.family,
                labelOf = { prettyName(it.name) },
                onSelect = { family ->
                    editor.editTheme {
                        // Choosing a built-in family means abandoning the custom file;
                        // otherwise the picker would appear to do nothing.
                        it.copy(font = it.font.copy(family = family, customFontPath = null))
                    }
                    editor.settle()
                },
            )
            RowDivider(0.dp)
            SliderRow(
                label = "Label size",
                value = theme.font.sizeScale,
                range = 0.6f..1.6f,
                onChange = { v -> editor.editTheme("fontSize") { it.copy(font = it.font.copy(sizeScale = v)) } },
                onSettled = editor::settle,
                format = { "%.2f×".format(it) },
            )
            RowDivider()
            SwitchRow(
                label = "Bold labels",
                checked = theme.font.bold,
                onChange = { on ->
                    editor.editTheme { it.copy(font = it.font.copy(bold = on)) }
                    editor.settle()
                },
            )
            RowDivider()
            if (theme.font.customFontPath != null) {
                ListRow("Using your own font file", subtitle = "Tap to go back to a built-in font", onClick = {
                    editor.editTheme { it.copy(font = it.font.copy(customFontPath = null)) }
                    editor.settle()
                })
            } else {
                GroupAction("Load a font file…", icon = StudioIcons.Type) { pickFont.launch("*/*") }
            }
        }

        Section("Size and spacing") {
            SliderRow(
                label = "Keyboard height",
                value = metrics.heightDp,
                range = 150f..320f,
                onChange = { v -> editor.edit("height") { it.copy(metrics = it.metrics.copy(heightDp = v)) } },
                onSettled = editor::settle,
                format = { "%.0f dp".format(it) },
            )
            RowDivider()
            SliderRow(
                label = "Key gap",
                value = metrics.keyGapDp,
                range = 0f..10f,
                onChange = { v -> editor.edit("gap") { it.copy(metrics = it.metrics.copy(keyGapDp = v)) } },
                onSettled = editor::settle,
                format = { "%.1f dp".format(it) },
            )
            RowDivider()
            SliderRow(
                label = "Side padding",
                value = metrics.sidePaddingDp,
                range = 0f..28f,
                onChange = { v ->
                    editor.edit("sidePad") { it.copy(metrics = it.metrics.copy(sidePaddingDp = v)) }
                },
                onSettled = editor::settle,
                format = { "%.0f dp".format(it) },
            )
            RowDivider()
            SliderRow(
                label = "Bottom padding",
                value = metrics.bottomPaddingDp,
                range = 0f..40f,
                onChange = { v ->
                    editor.edit("bottomPad") { it.copy(metrics = it.metrics.copy(bottomPaddingDp = v)) }
                },
                onSettled = editor::settle,
                format = { "%.0f dp".format(it) },
            )
            RowDivider()
            SliderRow(
                label = "Width",
                value = metrics.widthFraction,
                range = 0.6f..1f,
                onChange = { v ->
                    editor.edit("width") { it.copy(metrics = it.metrics.copy(widthFraction = v)) }
                },
                onSettled = editor::settle,
                format = { "${(it * 100).toInt()}%" },
            )
            if (metrics.widthFraction < 0.999f) {
                RowDivider()
                SliderRow(
                    label = "Horizontal offset",
                    value = metrics.horizontalOffsetDp,
                    range = -80f..80f,
                    onChange = { v ->
                        editor.edit("hOffset") {
                            it.copy(metrics = it.metrics.copy(horizontalOffsetDp = v))
                        }
                    },
                    onSettled = editor::settle,
                    format = { "%.0f dp".format(it) },
                )
            }
        }

        Section("Rows") {
            SwitchRow(
                label = "Number row",
                checked = metrics.showNumberRow,
                onChange = { on ->
                    editor.edit { it.copy(metrics = it.metrics.copy(showNumberRow = on)) }
                    editor.settle()
                },
            )
            RowDivider()
            SwitchRow(
                label = "Suggestion strip",
                subtitle = "Word suggestions and swipe alternatives",
                checked = metrics.showSuggestionStrip,
                onChange = { on ->
                    editor.edit { it.copy(metrics = it.metrics.copy(showSuggestionStrip = on)) }
                    editor.settle()
                },
            )
        }
    }
}

