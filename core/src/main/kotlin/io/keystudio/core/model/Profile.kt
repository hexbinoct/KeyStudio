package io.keystudio.core.model

import kotlinx.serialization.Serializable

/**
 * Physical sizing of the keyboard, independent of which keys are on it.
 * All values are dp; the layout engine converts once per size change.
 */
@Serializable
data class Metrics(
    /** Height of the key area, excluding the suggestion strip. */
    val heightDp: Float = 220f,
    val sidePaddingDp: Float = 4f,
    val topPaddingDp: Float = 4f,
    /** Extra space below the last row, useful above a gesture nav bar. */
    val bottomPaddingDp: Float = 6f,
    /** Gap between adjacent keys, on top of the shape inset. */
    val keyGapDp: Float = 0f,
    /** Shifts every key horizontally; lets one-handed mode reuse the layout. */
    val horizontalOffsetDp: Float = 0f,
    /** 0f..1f of available width. 1f is full width. */
    val widthFraction: Float = 1f,
    val showSuggestionStrip: Boolean = true,
    val suggestionStripHeightDp: Float = 44f,
    val showNumberRow: Boolean = false,
    val showTopRow: Boolean = false,
) {
    /** Total height the IME window needs. */
    val totalHeightDp: Float
        get() = heightDp + if (showSuggestionStrip) suggestionStripHeightDp else 0f
}

/**
 * One saved keyboard: its arrangement, its light and dark skins, its effects
 * and its sound. This is the unit the Studio edits, exports and shares.
 */
@Serializable
data class KeyboardProfile(
    val id: String,
    val name: String,
    val layoutId: String,
    val light: Theme,
    val dark: Theme,
    val effects: EffectsConfig = EffectsConfig(),
    val sound: SoundConfig = SoundConfig(),
    val metrics: Metrics = Metrics(),
    /** Schema version, so old saved profiles can be migrated. */
    val version: Int = CURRENT_VERSION,
) {
    fun themeFor(dark: Boolean): Theme = if (dark) this.dark else light

    companion object {
        const val CURRENT_VERSION = 1
    }
}
