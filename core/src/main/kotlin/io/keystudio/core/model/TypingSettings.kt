package io.keystudio.core.model

import kotlinx.serialization.Serializable

/**
 * How the keyboard behaves, as opposed to how it looks. Global rather than per
 * profile: switching to a different skin should not change how typing works.
 * Keys Cafe keeps these under "Advanced keyboard settings".
 */
@Serializable
data class TypingSettings(
    /** Draw words by sliding across the letters. */
    val swipeTyping: Boolean = true,
    /** Show the path under the finger while swiping. */
    val swipeTrail: Boolean = true,
    /** Word suggestions in the strip above the keys. */
    val suggestions: Boolean = true,
    /** Replace an unknown word with a close, likely one on space. */
    val autocorrect: Boolean = true,
    /** Capitalise the first letter of a sentence. */
    val autoCapitalize: Boolean = true,
    /** Two spaces become ". ". */
    val doubleSpacePeriod: Boolean = true,
    /** Slide along the space bar to move the cursor. */
    val spaceCursor: Boolean = true,
    /** Slide left from backspace to select whole words, deleted on release. */
    val backspaceSwipe: Boolean = true,
    /** "word ." becomes "word. " after a picked suggestion or swiped word. */
    val smartPunctuation: Boolean = true,
    /** Remember words you use that the dictionary does not know. */
    val learnWords: Boolean = true,
)
