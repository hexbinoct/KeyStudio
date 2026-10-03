package io.keystudio.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What a key *does*. [KeyCode.Text] is the common case and emits [KeyDef.output];
 * everything else is handled by the input service.
 */
@Serializable
enum class KeyCode {
    Text,
    Shift,
    Backspace,
    Enter,
    Space,
    Tab,
    LayerLetters,
    LayerSymbols,
    LayerSymbolsAlt,
    LayerNumeric,
    Emoji,
    Settings,
    Language,
    CursorLeft,
    CursorRight,
    CursorUp,
    CursorDown,

    /** Types [KeyDef.output] after expanding macros such as `%DATE%`. */
    Macro,

    /** Closes the keyboard. */
    Hide,

    /** Pastes the clipboard. */
    Paste,

    /** Draws nothing and swallows touches. Used for spacing in custom layouts. */
    Spacer,
}

/** Vector glyphs the renderer can draw without a font. */
@Serializable
enum class KeyIcon {
    None, Shift, ShiftLocked, ShiftHeld, Backspace, Enter, Space, Globe, Emoji,
    Settings, ArrowLeft, ArrowRight, ArrowUp, ArrowDown,
}

/**
 * A single key. Geometry is expressed in *weights*, not pixels: a row's keys
 * share the row width in proportion to [widthWeight], which is what lets the
 * layout editor resize a key without touching any other key's definition.
 */
@Serializable
data class KeyDef(
    val id: String,
    val label: String = "",
    /** Text emitted on tap. Defaults to [label] when blank and [code] is Text. */
    @SerialName("out") val output: String = "",
    val code: KeyCode = KeyCode.Text,
    /** Label shown when shift is active; also what gets typed. */
    val shiftLabel: String? = null,
    /** Small corner glyph, e.g. the `1` above `q`. */
    val hint: String? = null,
    val icon: KeyIcon = KeyIcon.None,
    val widthWeight: Float = 1f,
    /** Multiplies the row height. 1f fills the row. */
    val heightWeight: Float = 1f,
    /** Characters offered in the long-press popup. */
    val longPress: List<String> = emptyList(),
    /** Fires repeatedly while held (backspace, arrows). */
    val repeatable: Boolean = false,
    /** Treated as a modifier for theming purposes (darker fill by convention). */
    val modifier: Boolean = false,
    /** Per-key visual override. This is what makes "paint one key pink" possible. */
    val style: KeyStyleOverride? = null,
) {
    /** The text this key actually types, honouring the [output]/[label] fallback. */
    val effectiveOutput: String get() = output.ifEmpty { label }

    companion object {
        /** Convenience for a plain character key with optional long-press set. */
        fun char(
            c: String,
            hint: String? = null,
            longPress: List<String> = emptyList(),
            width: Float = 1f,
        ) = KeyDef(
            id = "k_$c",
            label = c,
            shiftLabel = c.uppercase(),
            hint = hint,
            longPress = longPress,
            widthWeight = width,
        )
    }
}

/** Per-key colour/shape override, serialized inside the layout. */
@Serializable
data class KeyStyleOverride(
    val fill: ColorSpec? = null,
    val pressedFill: ColorSpec? = null,
    val textColor: Int? = null,
    val cornerRadiusDp: Float? = null,
)

@Serializable
data class KeyRow(
    val keys: List<KeyDef>,
    /** Relative height of this row against the other rows. */
    val heightWeight: Float = 1f,
    /** Extra inset applied to both ends of the row, in weight units. */
    val leadingGap: Float = 0f,
    val trailingGap: Float = 0f,
)

@Serializable
enum class LayerId { Letters, Symbols, SymbolsAlt, Numeric }

/**
 * A complete keyboard arrangement across all its layers.
 * Layouts are pure data and are what the "Make your own keyboard" editor writes.
 */
@Serializable
data class KeyboardLayout(
    val id: String,
    val name: String,
    val layers: Map<LayerId, List<KeyRow>>,
    /** Optional user row pinned above the main rows (emoji, macros, arrows). */
    val topRow: KeyRow? = null,
) {
    fun layer(id: LayerId): List<KeyRow> = layers[id] ?: layers.getValue(LayerId.Letters)
}
