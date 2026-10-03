package io.keystudio.kb.ui.layout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.keystudio.core.model.KeyCode
import io.keystudio.core.model.KeyDef
import io.keystudio.core.model.LayerId
import io.keystudio.core.store.ProfileStore
import io.keystudio.core.view.KeyboardView
import io.keystudio.kb.ui.Studio
import io.keystudio.kb.ui.StudioIcon
import io.keystudio.kb.ui.StudioIcons
import io.keystudio.kb.ui.StudioType
import io.keystudio.kb.ui.editor.GroupAction
import io.keystudio.kb.ui.editor.IconAction
import io.keystudio.kb.ui.editor.ListRow
import io.keystudio.kb.ui.editor.RowDivider
import io.keystudio.kb.ui.editor.Section
import io.keystudio.kb.ui.editor.Segmented
import io.keystudio.kb.ui.editor.SliderRow
import io.keystudio.kb.ui.editor.SwitchRow
import io.keystudio.kb.ui.editor.prettyName

/**
 * The Keys Cafe "Make your own keyboard" screen.
 *
 * Tap a key on the keyboard below to select it; everything about it -- what it
 * types, how wide it is, what its long press offers, where it sits -- is then
 * editable above. Like the theme editor there is no Save: each change is
 * written through and the real keyboard follows.
 */
@Composable
fun LayoutEditorScreen(
    layoutId: String,
    store: ProfileStore,
    onClose: () -> Unit,
) {
    val c = Studio.colors
    val scope = rememberCoroutineScope()
    val editor = rememberLayoutEditor(store, layoutId, scope)
    var layer by rememberSaveable { mutableStateOf(LayerId.Letters) }
    var profile by remember { mutableStateOf(store.activeProfile()) }
    var menuOpen by remember { mutableStateOf(false) }

    fun leave() {
        editor.flush()
        onClose()
    }
    BackHandler { leave() }

    // A new selection starts a new undo step for the text fields.
    LaunchedEffect(editor.selection) { editor.settle() }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .windowInsetsPadding(WindowInsets.systemBars)
            .imePadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAction(StudioIcons.Back, "Back", onClick = ::leave)
            Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                Text("Make your own keyboard", style = StudioType.headline, color = c.text, maxLines = 1)
                Text(
                    if (editor.isModified) "${editor.layout.name}, edited" else editor.layout.name,
                    style = StudioType.caption,
                    color = c.textSecondary,
                )
            }
            IconAction(StudioIcons.Undo, "Undo", enabled = editor.canUndo, onClick = editor::undo)
            IconAction(StudioIcons.Redo, "Redo", enabled = editor.canRedo, onClick = editor::redo)
            Box {
                IconAction(StudioIcons.More, "More", onClick = { menuOpen = true })
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    shape = RoundedCornerShape(12.dp),
                    containerColor = c.elevated,
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (editor.isBuiltIn) "Reset to ${editor.layout.name}" else "Undo all changes",
                                style = StudioType.body,
                                color = if (editor.isModified) c.danger else c.textTertiary,
                            )
                        },
                        leadingIcon = { StudioIcon(StudioIcons.Reset, tint = c.danger, size = 18.dp) },
                        enabled = editor.isModified,
                        onClick = {
                            menuOpen = false
                            editor.selection = null
                            editor.reset()
                        },
                    )
                }
            }
        }

        Segmented(
            options = LayerId.entries.toList(),
            selected = layer,
            onSelect = {
                layer = it
                editor.selection = null
            },
            labelOf = { it.title },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            val key = editor.selectedKey
            if (key == null) {
                NothingSelected()
                if (layer == LayerId.Letters) {
                    Section(
                        "Rows",
                        footer = "The top row sits above the letters and is yours to fill: " +
                            "cursor keys, symbols you use a lot, macros.",
                    ) {
                        SwitchRow(
                            label = "Pinned top row",
                            checked = profile.metrics.showTopRow,
                            onChange = { on ->
                                if (on) editor.ensureTopRow()
                                profile = profile.copy(metrics = profile.metrics.copy(showTopRow = on))
                                store.saveProfile(profile)
                            },
                        )
                        RowDivider()
                        SwitchRow(
                            label = "Number row",
                            checked = profile.metrics.showNumberRow,
                            onChange = { on ->
                                profile = profile.copy(metrics = profile.metrics.copy(showNumberRow = on))
                                store.saveProfile(profile)
                            },
                        )
                    }
                }
            } else {
                KeyInspector(editor, key)
            }
            Spacer(Modifier.height(24.dp))
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
        EditableKeyboard(editor, layer, profile)
    }
}

@Composable
private fun NothingSelected() {
    val c = Studio.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 28.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(52.dp).background(c.fill, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            StudioIcon(StudioIcons.Layout, tint = c.textSecondary, size = 24.dp)
        }
        Spacer(Modifier.height(14.dp))
        Text("Tap a key below to edit it", style = StudioType.headline, color = c.text)
        Spacer(Modifier.height(4.dp))
        Text(
            "Change what it types, resize it, fill its long press, move it, or add keys beside it.",
            style = StudioType.body,
            color = c.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeyInspector(editor: LayoutEditor, key: KeyDef) {
    val c = Studio.colors
    val row = editor.selectedRow ?: return
    val ref = editor.selection ?: return
    var palette by remember { mutableStateOf<PaletteMode?>(null) }
    val typesText = key.code == KeyCode.Text || key.code == KeyCode.Macro

    Section(describe(key)) {
        if (typesText) {
            TextFieldRow("Label", key.label, placeholder = "Shown on the key") { v ->
                editor.editKey("label") { it.copy(label = v) }
            }
            RowDivider()
            TextFieldRow(
                if (key.code == KeyCode.Macro) "Macro" else "Types",
                key.output,
                placeholder = key.label.ifEmpty { "Same as label" },
            ) { v ->
                editor.editKey("output") {
                    it.copy(output = v, code = if ('%' in v) KeyCode.Macro else KeyCode.Text)
                }
            }
            RowDivider()
            TextFieldRow("With shift", key.shiftLabel.orEmpty(), placeholder = "Same as label") { v ->
                editor.editKey("shift") { it.copy(shiftLabel = v.ifEmpty { null }) }
            }
            RowDivider()
            GroupAction("Replace from palette…", icon = StudioIcons.Sparkles) {
                palette = PaletteMode.Replace
            }
        } else {
            ListRow("Function") {
                Text(prettyName(key.code.name), style = StudioType.body, color = c.textSecondary)
            }
        }
    }

    Section("Size") {
        SliderRow(
            label = "Width",
            value = key.widthWeight,
            range = 0.4f..6f,
            onChange = { v -> editor.editKey("width") { it.copy(widthWeight = v) } },
            onSettled = editor::settle,
            format = { "%.2f keys".format(it) },
        )
        RowDivider()
        SliderRow(
            label = "Row height",
            value = row.heightWeight,
            range = 0.5f..2f,
            onChange = { v -> editor.editRow("rowHeight") { it.copy(heightWeight = v) } },
            onSettled = editor::settle,
            format = { "%.2f×".format(it) },
        )
        RowDivider()
        SliderRow(
            label = "Row indent",
            value = row.leadingGap,
            range = 0f..2f,
            onChange = { v -> editor.editRow("rowGap") { it.copy(leadingGap = v, trailingGap = v) } },
            onSettled = editor::settle,
            format = { if (it < 0.01f) "None" else "%.2f keys".format(it) },
        )
    }

    if (typesText || key.longPress.isNotEmpty()) {
        Section("Long press") {
            if (key.longPress.isEmpty()) {
                ListRow("Nothing yet", subtitle = "Hold the key to offer these characters")
            } else {
                FlowRow(
                    Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    key.longPress.forEachIndexed { index, option ->
                        LongPressChip(option) {
                            editor.editKey { k -> k.copy(longPress = k.longPress.filterIndexed { i, _ -> i != index }) }
                            editor.settle()
                        }
                    }
                }
            }
            RowDivider(0.dp)
            GroupAction("Add characters…", icon = StudioIcons.Plus) { palette = PaletteMode.LongPress }
        }
    }

    Section("Arrange", footer = if (LayoutEditor.isEssential(key)) {
        "This key keeps the keyboard usable, so it can be moved but not deleted."
    } else null) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuietButton("Move left", enabled = ref.key > 0, modifier = Modifier.weight(1f)) {
                editor.moveSelected(-1)
            }
            QuietButton("Move right", enabled = ref.key < row.keys.size - 1, modifier = Modifier.weight(1f)) {
                editor.moveSelected(1)
            }
        }
        RowDivider(0.dp)
        GroupAction("Add a key after this one", icon = StudioIcons.Plus) { palette = PaletteMode.Insert }
        RowDivider()
        GroupAction("Duplicate", icon = StudioIcons.Copy) { editor.duplicateSelected() }
        if (!LayoutEditor.isEssential(key) && row.keys.size > 1) {
            RowDivider()
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { editor.deleteSelected() }
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StudioIcon(StudioIcons.Trash, tint = c.danger, size = 20.dp)
                Spacer(Modifier.width(14.dp))
                Text("Delete key", style = StudioType.bodyStrong, color = c.danger)
            }
        }
    }

    when (palette) {
        PaletteMode.LongPress -> PaletteDialog(
            title = "Long press",
            multiple = true,
            chosen = key.longPress,
            onPick = { item ->
                editor.editKey { k ->
                    k.copy(longPress = if (item in k.longPress) k.longPress - item else k.longPress + item)
                }
            },
            onDismiss = { palette = null; editor.settle() },
        )
        PaletteMode.Replace -> PaletteDialog(
            title = "Replace key",
            multiple = false,
            chosen = listOf(key.label),
            includeMacros = true,
            onPick = { item ->
                editor.editKey { k -> keyFrom(item, k.id).copy(widthWeight = k.widthWeight, longPress = k.longPress) }
                editor.settle()
            },
            onDismiss = { palette = null },
        )
        PaletteMode.Insert -> PaletteDialog(
            title = "Add a key",
            multiple = false,
            chosen = emptyList(),
            includeMacros = true,
            onPick = { item -> editor.insertAfterSelected(keyFrom(item, "custom")) },
            onDismiss = { palette = null },
        )
        null -> Unit
    }
}

private enum class PaletteMode { LongPress, Replace, Insert }

/** A key that types [item]; a macro if it holds a placeholder. */
private fun keyFrom(item: String, id: String): KeyDef {
    val macro = item.startsWith("%") && item.endsWith("%")
    val single = item.length == 1 && item[0].isLetter()
    return KeyDef(
        id = id,
        label = if (macro) MACRO_LABELS[item] ?: item else item,
        output = if (macro) item else "",
        code = if (macro) KeyCode.Macro else KeyCode.Text,
        shiftLabel = if (single) item.uppercase() else null,
    )
}

private val MACRO_LABELS = mapOf(
    "%DATE%" to "Date", "%TIME%" to "Time", "%DAY%" to "Day", "%CLIPBOARD%" to "Paste",
)

private fun describe(key: KeyDef): String = when (key.code) {
    KeyCode.Text -> when {
        key.label.length == 1 && key.label[0].isLetter() -> "Letter ${key.label}"
        key.label.isEmpty() -> "Key"
        else -> "Key “${key.label}”"
    }
    KeyCode.Macro -> "Macro key"
    else -> "${prettyName(key.code.name)} key"
}

@Composable
private fun LongPressChip(text: String, onRemove: () -> Unit) {
    val c = Studio.colors
    Row(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(c.fill)
            .clickable(onClick = onRemove)
            .padding(start = 11.dp, end = 7.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = StudioType.body, color = c.text)
        Spacer(Modifier.width(4.dp))
        StudioIcon(StudioIcons.Close, tint = c.textTertiary, size = 14.dp)
    }
}

@Composable
private fun QuietButton(text: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Studio.colors
    Box(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(c.fill)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = StudioType.label, color = if (enabled) c.text else c.textTertiary)
    }
}

@Composable
private fun TextFieldRow(
    label: String,
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
) {
    val c = Studio.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = StudioType.body, color = c.text, modifier = Modifier.width(96.dp))
        Box(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(9.dp))
                .background(c.fill)
                .border(1.dp, c.hairline, RoundedCornerShape(9.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            if (value.isEmpty()) Text(placeholder, style = StudioType.body, color = c.textTertiary, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = StudioType.body.copy(color = c.text),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The real keyboard, in edit mode: taps select keys instead of typing. */
@Composable
private fun EditableKeyboard(
    editor: LayoutEditor,
    layer: LayerId,
    profile: io.keystudio.core.model.KeyboardProfile,
) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    AndroidView(
        factory = { ctx ->
            KeyboardView(ctx).apply {
                previewMode = true
                editMode = true
                forceDark = dark
                this.profile = profile
                this.layout = editor.layout
                this.layer = layer
                onKeyPicked = { editor.selection = it }
            }
        },
        update = { view ->
            if (view.profile != profile) view.profile = profile
            if (view.layout != editor.layout) view.layout = editor.layout
            view.layer = layer
            if (view.selectedKey != editor.selection) view.selectedKey = editor.selection
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
