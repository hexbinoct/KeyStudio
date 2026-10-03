package io.keystudio.kb.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.keystudio.core.model.KeyboardProfile
import io.keystudio.core.model.Theme
import io.keystudio.core.store.ProfileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The document being edited.
 *
 * Two things make this feel like Keys Cafe rather than a settings screen.
 * First, there is no Save button in the way: every change is written to disk,
 * and the keyboard -- which lives in another process and watches those files --
 * follows along while your finger is still on the slider. Second, undo is
 * gesture-shaped: dragging a slider from one end to the other is one undo step,
 * not two hundred.
 */
class ProfileEditor(
    private val store: ProfileStore,
    initial: KeyboardProfile,
    private val scope: CoroutineScope,
    startDark: Boolean = false,
) {
    var profile by mutableStateOf(initial)
        private set

    /** Which of the profile's two skins the tabs are editing. */
    var editingDark by mutableStateOf(startDark)

    private val undoStack = ArrayDeque<KeyboardProfile>()
    private val redoStack = ArrayDeque<KeyboardProfile>()
    private var lastTag: String? = null
    private var writeJob: Job? = null

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val isPreset: Boolean get() = store.isPreset(profile.id)
    val isModifiedPreset: Boolean get() = isPreset && store.hasUserCopy(profile.id)

    /** The skin currently under the tabs. */
    val theme: Theme get() = profile.themeFor(editingDark)

    /**
     * Applies a change.
     *
     * [tag] identifies a continuous gesture -- a slider being dragged, a colour
     * being scrubbed. Consecutive edits sharing a tag collapse into one undo
     * entry. Pass null for a discrete change, which always gets its own entry.
     */
    fun edit(tag: String? = null, block: (KeyboardProfile) -> KeyboardProfile) {
        val next = block(profile)
        if (next == profile) return
        if (tag == null || tag != lastTag) {
            undoStack.addLast(profile)
            if (undoStack.size > MAX_UNDO) undoStack.removeFirst()
            redoStack.clear()
        }
        lastTag = tag
        profile = next
        scheduleWrite()
    }

    /** Edits just the variant being shown, leaving the other one alone. */
    fun editTheme(tag: String? = null, block: (Theme) -> Theme) = edit(tag) { p ->
        if (editingDark) p.copy(dark = block(p.dark)) else p.copy(light = block(p.light))
    }

    /** Ends a gesture, so the next change starts a new undo entry. */
    fun settle() {
        lastTag = null
        flush()
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(profile)
        profile = previous
        lastTag = null
        flush()
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(profile)
        profile = next
        lastTag = null
        flush()
    }

    // -- profile-level operations ---------------------------------------------

    fun rename(name: String) = edit { it.copy(name = name) }

    fun makeActive() {
        flush()
        store.setActiveProfileId(profile.id)
    }

    /** Saves a copy under a new id and switches the editor to it. */
    fun duplicate(name: String = "${profile.name} copy"): KeyboardProfile {
        flush()
        val copy = profile.copy(id = store.newIdFor(name), name = name)
        store.saveProfile(copy)
        undoStack.clear()
        redoStack.clear()
        profile = copy
        return copy
    }

    /** Throws away edits to a preset and reloads the built-in version. */
    fun resetToPreset() {
        val restored = store.resetProfile(profile.id)
        undoStack.addLast(profile)
        redoStack.clear()
        lastTag = null
        profile = restored
        // Deliberately not rewritten: the absence of the file *is* the reset.
        // But the keyboard still has to be told, and the delete already fired
        // an event, so nothing further is needed here.
    }

    fun export(): String = store.exportProfile(profile)

    /** Copies an imported design in under a new id so nothing is overwritten. */
    fun importAsNew(text: String): KeyboardProfile {
        val imported = store.importProfile(text)
        val named = imported.copy(
            id = store.newIdFor(imported.name),
            name = imported.name,
        )
        store.saveProfile(named)
        return named
    }

    // -- persistence ----------------------------------------------------------

    /**
     * Debounced because a slider drag produces a change per frame and each one
     * wakes the keyboard process. 70 ms is under the threshold where a change
     * stops feeling attached to the finger, and coalesces a drag into a handful
     * of writes rather than sixty a second.
     */
    private fun scheduleWrite() {
        writeJob?.cancel()
        writeJob = scope.launch {
            delay(WRITE_DEBOUNCE_MS)
            store.saveProfile(profile)
        }
    }

    /** Writes now, for the points where the next thing to happen reads the file. */
    fun flush() {
        writeJob?.cancel()
        writeJob = null
        store.saveProfile(profile)
    }

    private companion object {
        const val MAX_UNDO = 80
        const val WRITE_DEBOUNCE_MS = 70L
    }
}

@Composable
fun rememberProfileEditor(
    store: ProfileStore,
    profileId: String,
    scope: CoroutineScope,
    startDark: Boolean = false,
): ProfileEditor = remember(profileId) {
    ProfileEditor(store, store.loadProfile(profileId), scope, startDark)
}
