package io.keystudio.kb.ui.editor

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.keystudio.core.store.ProfileStore
import io.keystudio.core.view.KeyboardView
import io.keystudio.kb.ui.Studio
import io.keystudio.kb.ui.StudioIcon
import io.keystudio.kb.ui.StudioIcons
import io.keystudio.kb.ui.StudioType

private enum class EditorTab(val title: String) {
    Colours("Colours"), Shape("Shape"), Effects("Effects"), Sound("Sound"),
}

/**
 * The Keys Cafe "Style your own keyboard" screen.
 *
 * The keyboard along the bottom is the production [KeyboardView], not a picture
 * of one, and every control writes through to the profile immediately -- so the
 * thing you are designing is the thing you are looking at, and if this profile
 * is the active one, the real keyboard in every other app changes with it.
 */
@Composable
fun EditorScreen(
    profileId: String,
    store: ProfileStore,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val editor = rememberProfileEditor(store, profileId, scope, startDark = systemDark)
    val c = Studio.colors

    var tab by rememberSaveable { mutableStateOf(EditorTab.Colours) }
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    // Leaving must not lose the tail of a debounced write.
    fun leave() {
        editor.flush()
        onClose()
    }
    BackHandler { leave() }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAction(StudioIcons.Back, "Back", onClick = ::leave)
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { renaming = true }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    editor.profile.name,
                    style = StudioType.headline,
                    color = c.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (editor.editingDark) "Editing the dark version" else "Editing the light version",
                    style = StudioType.caption,
                    color = c.textSecondary,
                )
            }
            IconAction(
                icon = if (editor.editingDark) StudioIcons.Moon else StudioIcons.Sun,
                description = "Light or dark version",
                onClick = { editor.editingDark = !editor.editingDark },
            )
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
                    MenuItem("Use this keyboard", StudioIcons.Check) { editor.makeActive(); menuOpen = false }
                    MenuItem("Duplicate", StudioIcons.Copy) { editor.duplicate(); menuOpen = false }
                    MenuItem("Rename", StudioIcons.Pencil) { renaming = true; menuOpen = false }
                    MenuItem("Share design", StudioIcons.Share) {
                        menuOpen = false
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TITLE, "${editor.profile.name}.json")
                            putExtra(Intent.EXTRA_TEXT, editor.export())
                        }
                        context.startActivity(Intent.createChooser(send, "Share keyboard"))
                    }
                    if (editor.isModifiedPreset) {
                        MenuItem("Reset to original", StudioIcons.Reset) {
                            editor.resetToPreset(); menuOpen = false
                        }
                    }
                    if (!editor.isPreset) {
                        MenuItem("Delete", StudioIcons.Trash, danger = true) {
                            confirmingDelete = true; menuOpen = false
                        }
                    }
                }
            }
        }

        Segmented(
            options = EditorTab.entries.toList(),
            selected = tab,
            onSelect = { tab = it },
            labelOf = { it.title },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )

        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(90)) },
            modifier = Modifier.weight(1f),
            label = "tab",
        ) { current ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                when (current) {
                    EditorTab.Colours -> ColorsTab(editor, store)
                    EditorTab.Shape -> ShapeTab(editor, store)
                    EditorTab.Effects -> EffectsTab(editor)
                    EditorTab.Sound -> SoundTab(editor)
                }
                Box(Modifier.height(24.dp))
            }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
        LivePreview(editor, store)
    }

    if (renaming) {
        RenameDialog(
            initial = editor.profile.name,
            onDismiss = { renaming = false },
            onConfirm = { editor.rename(it); editor.settle(); renaming = false },
        )
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            containerColor = c.elevated,
            shape = RoundedCornerShape(20.dp),
            title = { Text("Delete ${editor.profile.name}?", style = StudioType.title) },
            text = { Text("This removes the design. It cannot be undone.", style = StudioType.body) },
            confirmButton = {
                TextButton(onClick = {
                    store.deleteProfile(editor.profile.id)
                    confirmingDelete = false
                    onClose()
                }) { Text("Delete", color = c.danger, style = StudioType.label) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text("Cancel", color = c.text, style = StudioType.label)
                }
            },
        )
    }
}

@Composable
private fun MenuItem(text: String, icon: ImageVector, danger: Boolean = false, onClick: () -> Unit) {
    val c = Studio.colors
    val tint = if (danger) c.danger else c.text
    DropdownMenuItem(
        text = { Text(text, style = StudioType.body, color = tint) },
        leadingIcon = { StudioIcon(icon, tint = if (danger) c.danger else c.textSecondary, size = 18.dp) },
        onClick = onClick,
    )
}

/**
 * The keyboard itself, pinned under the controls.
 *
 * It is handed the in-memory profile rather than re-reading the file, so it
 * tracks the sliders with no disk round trip; the file write happens in
 * parallel for the keyboard living in the other process.
 */
@Composable
private fun LivePreview(editor: ProfileEditor, store: ProfileStore) {
    val profile = editor.profile
    val dark = editor.editingDark
    val layout = remember(profile.layoutId) { store.loadLayout(profile.layoutId) }

    AndroidView(
        factory = { ctx ->
            KeyboardView(ctx).apply {
                previewMode = true
                this.profile = profile
                this.layout = layout
                forceDark = dark
            }
        },
        update = { view ->
            if (view.forceDark != dark) view.forceDark = dark
            if (view.layout !== layout) view.layout = layout
            if (view.profile != profile) view.profile = profile
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun RenameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    title: String = "Name",
) {
    val c = Studio.colors
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.elevated,
        shape = RoundedCornerShape(20.dp),
        title = { Text(title, style = StudioType.title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = StudioType.body,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim().ifEmpty { initial }) }) {
                Text("Save", style = StudioType.label, color = c.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", style = StudioType.label, color = c.text) }
        },
    )
}
