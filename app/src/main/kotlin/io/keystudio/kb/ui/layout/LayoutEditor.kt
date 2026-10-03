package io.keystudio.kb.ui.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.keystudio.core.model.KeyCode
import io.keystudio.core.model.KeyDef
import io.keystudio.core.model.KeyIcon
import io.keystudio.core.model.KeyRow
import io.keystudio.core.model.KeyboardLayout
import io.keystudio.core.model.LayerId
import io.keystudio.core.store.ProfileStore
import io.keystudio.core.view.KeyRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The layout being edited in "Make your own keyboard".
 *
 * Works exactly like the theme editor's [io.keystudio.kb.ui.editor.ProfileEditor]:
 * every change is written through (debounced), so the real keyboard in other
 * apps follows along, and undo is per gesture rather than per frame. A built-in
 * layout is shadowed by the user's copy rather than replaced, so "Reset" is
 * deleting that copy.
 */
class LayoutEditor(
    private val store: ProfileStore,
    initial: KeyboardLayout,
    private val scope: CoroutineScope,
) {
    var layout by mutableStateOf(initial)
        private set

    var selection by mutableStateOf<KeyRef?>(null)

    private val undoStack = ArrayDeque<KeyboardLayout>()
    private val redoStack = ArrayDeque<KeyboardLayout>()
    private var lastTag: String? = null
    private var writeJob: Job? = null
    /** Unsaved changes; only these are ever written, so an untouched built-in stays built-in. */
    private var dirty = false

    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set
    var isModified by mutableStateOf(store.hasUserLayout(initial.id))
        private set
    val isBuiltIn: Boolean get() = store.isBuiltInLayout(layout.id)

    private fun syncFlags() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    fun edit(tag: String? = null, block: (KeyboardLayout) -> KeyboardLayout) {
        val next = block(layout)
        if (next == layout) return
        if (tag == null || tag != lastTag) {
            undoStack.addLast(layout)
            if (undoStack.size > 80) undoStack.removeFirst()
            redoStack.clear()
        }
        lastTag = tag
        layout = next
        dirty = true
        syncFlags()
        scheduleWrite()
    }

    fun settle() {
        lastTag = null
        flush()
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(layout)
        layout = previous
        dirty = true
        lastTag = null
        syncFlags()
        validateSelection()
        flush()
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(layout)
        layout = next
        dirty = true
        lastTag = null
        syncFlags()
        validateSelection()
        flush()
    }

    /** Throws the user's version away and goes back to the built-in one. */
    fun reset() {
        undoStack.addLast(layout)
        redoStack.clear()
        writeJob?.cancel()
        store.deleteLayout(layout.id)
        layout = store.loadLayout(layout.id)
        isModified = false
        dirty = false
        lastTag = null
        syncFlags()
        validateSelection()
    }

    private fun scheduleWrite() {
        writeJob?.cancel()
        writeJob = scope.launch {
            delay(70L)
            write()
        }
    }

    fun flush() {
        writeJob?.cancel()
        writeJob = null
        if (dirty) write()
    }

    private fun write() {
        store.saveLayout(layout)
        isModified = true
        dirty = false
    }

    // -- addressing -----------------------------------------------------------

    fun rowOf(ref: KeyRef): KeyRow? =
        if (ref.row == -1) layout.topRow else layout.layers[ref.layer]?.getOrNull(ref.row)

    fun keyOf(ref: KeyRef): KeyDef? = rowOf(ref)?.keys?.getOrNull(ref.key)

    val selectedKey: KeyDef? get() = selection?.let { keyOf(it) }
    val selectedRow: KeyRow? get() = selection?.let { rowOf(it) }

    private fun validateSelection() {
        val ref = selection ?: return
        if (keyOf(ref) == null) selection = null
    }

    private fun withRow(ref: KeyRef, block: (KeyRow) -> KeyRow?): KeyboardLayout {
        if (ref.row == -1) {
            val row = layout.topRow ?: return layout
            return layout.copy(topRow = block(row))
        }
        val rows = layout.layers[ref.layer] ?: return layout
        val row = rows.getOrNull(ref.row) ?: return layout
        val replaced = block(row) ?: return layout
        val newRows = rows.toMutableList().also { it[ref.row] = replaced }
        return layout.copy(layers = layout.layers + (ref.layer to newRows))
    }

    /** Changes the selected key. */
    fun editKey(tag: String? = null, block: (KeyDef) -> KeyDef) {
        val ref = selection ?: return
        edit(tag) { _ ->
            withRow(ref) { row ->
                val keys = row.keys.toMutableList()
                val key = keys.getOrNull(ref.key) ?: return@withRow row
                keys[ref.key] = block(key)
                row.copy(keys = keys)
            }
        }
    }

    /** Changes the selected key's row. */
    fun editRow(tag: String? = null, block: (KeyRow) -> KeyRow) {
        val ref = selection ?: return
        edit(tag) { _ -> withRow(ref) { block(it) } }
    }

    fun moveSelected(by: Int) {
        val ref = selection ?: return
        val row = rowOf(ref) ?: return
        val target = ref.key + by
        if (target !in row.keys.indices) return
        edit { _ ->
            withRow(ref) { r ->
                val keys = r.keys.toMutableList()
                val k = keys.removeAt(ref.key)
                keys.add(target, k)
                r.copy(keys = keys)
            }
        }
        selection = ref.copy(key = target)
        settle()
    }

    /** Inserts [key] after the selection (or at the end of its row) and selects it. */
    fun insertAfterSelected(key: KeyDef) {
        val ref = selection ?: return
        val at = ref.key + 1
        edit { _ ->
            withRow(ref) { r ->
                val keys = r.keys.toMutableList()
                keys.add(at.coerceAtMost(keys.size), key.copy(id = uniqueId(key.id)))
                r.copy(keys = keys)
            }
        }
        selection = ref.copy(key = at)
        settle()
    }

    fun duplicateSelected() {
        val key = selectedKey ?: return
        insertAfterSelected(key)
    }

    fun deleteSelected() {
        val ref = selection ?: return
        val row = rowOf(ref) ?: return
        if (row.keys.size <= 1) return
        edit { _ ->
            withRow(ref) { r -> r.copy(keys = r.keys.filterIndexed { i, _ -> i != ref.key }) }
        }
        selection = ref.copy(key = (ref.key - 1).coerceAtLeast(0))
        settle()
    }

    /** Creates the pinned top row if there is none; the caller turns it on. */
    fun ensureTopRow() {
        if (layout.topRow != null) return
        edit { it.copy(topRow = DEFAULT_TOP_ROW) }
        settle()
    }

    private fun uniqueId(base: String): String =
        "${base.substringBefore("~")}~${System.currentTimeMillis().toString(36)}${(0..999).random()}"

    companion object {
        /**
         * The keys that keep a keyboard usable. Deleting one of these could
         * strand the user with no way to type a space or leave a layer.
         */
        fun isEssential(key: KeyDef): Boolean = key.code in setOf(
            KeyCode.Space, KeyCode.Enter, KeyCode.Backspace, KeyCode.Shift,
            KeyCode.LayerLetters, KeyCode.LayerSymbols, KeyCode.Language,
        )

        /** Cursor keys and the symbols a programmer reaches for most. */
        val DEFAULT_TOP_ROW = KeyRow(
            keys = listOf(
                KeyDef(id = "top_left", code = KeyCode.CursorLeft, icon = KeyIcon.ArrowLeft, repeatable = true, modifier = true),
                KeyDef(id = "top_at", label = "@"),
                KeyDef(id = "top_hash", label = "#"),
                KeyDef(id = "top_slash", label = "/"),
                KeyDef(id = "top_dash", label = "-"),
                KeyDef(id = "top_under", label = "_"),
                KeyDef(id = "top_lparen", label = "("),
                KeyDef(id = "top_rparen", label = ")"),
                KeyDef(id = "top_q", label = "?"),
                KeyDef(id = "top_right", code = KeyCode.CursorRight, icon = KeyIcon.ArrowRight, repeatable = true, modifier = true),
            ),
            heightWeight = 0.8f,
        )
    }
}

@Composable
fun rememberLayoutEditor(store: ProfileStore, layoutId: String, scope: CoroutineScope): LayoutEditor =
    remember(layoutId) { LayoutEditor(store, store.loadLayout(layoutId), scope) }

/** Display name of a layer in the editor. */
val LayerId.title: String
    get() = when (this) {
        LayerId.Letters -> "Letters"
        LayerId.Symbols -> "Symbols"
        LayerId.SymbolsAlt -> "More"
        LayerId.Numeric -> "Numbers"
    }
