package io.keystudio.core.presets

import io.keystudio.core.model.KeyCode
import io.keystudio.core.model.KeyDef
import io.keystudio.core.model.KeyIcon
import io.keystudio.core.model.KeyRow
import io.keystudio.core.model.KeyboardLayout
import io.keystudio.core.model.LayerId

/**
 * Built-in arrangements. These are ordinary data, identical in kind to anything
 * the layout editor produces, so "reset to QWERTY" is just replacing the object.
 */
object Layouts {

    const val QWERTY_ID = "builtin.qwerty"

    /** Long-press accent sets, roughly matching what AOSP offers for en-US. */
    private val LONG_PRESS = mapOf(
        "a" to listOf("à", "á", "â", "ä", "æ", "ã", "å", "ā"),
        "c" to listOf("ç", "ć", "č"),
        "e" to listOf("è", "é", "ê", "ë", "ē", "ė", "ę"),
        "i" to listOf("î", "ï", "í", "ī", "į", "ì"),
        "l" to listOf("ł"),
        "n" to listOf("ñ", "ń"),
        "o" to listOf("ô", "ö", "ò", "ó", "œ", "ø", "ō", "õ"),
        "s" to listOf("ß", "ś", "š"),
        "u" to listOf("û", "ü", "ù", "ú", "ū"),
        "y" to listOf("ÿ"),
        "z" to listOf("ž", "ź", "ż"),
    )

    private val DIGIT_HINTS = mapOf(
        "q" to "1", "w" to "2", "e" to "3", "r" to "4", "t" to "5",
        "y" to "6", "u" to "7", "i" to "8", "o" to "9", "p" to "0",
    )

    private fun letter(c: String): KeyDef = KeyDef(
        id = "l_$c",
        label = c,
        shiftLabel = c.uppercase(),
        hint = DIGIT_HINTS[c],
        longPress = LONG_PRESS[c].orEmpty(),
    )

    private fun letters(chars: String): List<KeyDef> =
        chars.map { letter(it.toString()) }

    private fun sym(c: String, id: String = "s_$c", longPress: List<String> = emptyList()) =
        KeyDef(id = id, label = c, longPress = longPress)

    private val shiftKey = KeyDef(
        id = "shift", code = KeyCode.Shift, icon = KeyIcon.Shift,
        widthWeight = 1.5f, modifier = true,
    )
    private val backspaceKey = KeyDef(
        id = "backspace", code = KeyCode.Backspace, icon = KeyIcon.Backspace,
        widthWeight = 1.5f, repeatable = true, modifier = true,
    )
    private val enterKey = KeyDef(
        id = "enter", code = KeyCode.Enter, icon = KeyIcon.Enter,
        widthWeight = 1.5f,
    )
    private val spaceKey = KeyDef(
        id = "space", code = KeyCode.Space, icon = KeyIcon.Space,
        output = " ", widthWeight = 5f,
    )
    private val commaKey = KeyDef(
        id = "comma", label = ",", longPress = listOf("!", "?", ";", ":", "'", "\""),
    )
    private val periodKey = KeyDef(
        id = "period", label = ".", longPress = listOf("!", "?", "…", "-", "_", "/"),
    )
    /**
     * The globe switches to the next IME. It earns its place on the bottom row
     * because it is the user's way back to another keyboard if this one misbehaves.
     */
    private val languageKey = KeyDef(
        id = "language", code = KeyCode.Language, icon = KeyIcon.Globe, modifier = true,
    )

    private fun toSymbols(label: String) = KeyDef(
        id = "to_symbols", label = label, code = KeyCode.LayerSymbols,
        widthWeight = 1.5f, modifier = true,
    )

    private fun toLetters() = KeyDef(
        id = "to_letters", label = "ABC", code = KeyCode.LayerLetters,
        widthWeight = 1.5f, modifier = true,
    )

    private fun toSymbolsAlt(label: String) = KeyDef(
        id = "to_symbols_alt", label = label, code = KeyCode.LayerSymbolsAlt,
        widthWeight = 1.5f, modifier = true,
    )

    private fun bottomRow(leftSwitch: KeyDef): KeyRow = KeyRow(
        listOf(leftSwitch, languageKey, commaKey, spaceKey, periodKey, enterKey)
    )

    private val lettersLayer = listOf(
        KeyRow(letters("qwertyuiop")),
        KeyRow(letters("asdfghjkl"), leadingGap = 0.5f, trailingGap = 0.5f),
        KeyRow(listOf(shiftKey) + letters("zxcvbnm") + listOf(backspaceKey)),
        bottomRow(toSymbols("?123")),
    )

    private val symbolsLayer = listOf(
        KeyRow("1234567890".map { sym(it.toString(), id = "n_$it") }),
        KeyRow(
            listOf(
                sym("@"), sym("#"), sym("$", longPress = listOf("¢", "£", "€", "¥", "₹")),
                sym("%"), sym("&"), sym("-", longPress = listOf("_", "–", "—", "·")),
                sym("+", longPress = listOf("±")), sym("("), sym(")"),
            )
        ),
        KeyRow(
            listOf(toSymbolsAlt("=\\<")) + listOf(
                sym("*"), sym("\"", id = "s_dquote", longPress = listOf("“", "”", "„")),
                sym("'", id = "s_squote", longPress = listOf("‘", "’", "‚")),
                sym(":"), sym(";"), sym("!", longPress = listOf("¡")),
                sym("?", longPress = listOf("¿")),
            ) + listOf(backspaceKey)
        ),
        bottomRow(toLetters()),
    )

    private val symbolsAltLayer = listOf(
        KeyRow(
            listOf(
                sym("~"), sym("`"), sym("|"), sym("•"), sym("√"),
                sym("π"), sym("÷"), sym("×"), sym("¶"), sym("∆"),
            )
        ),
        KeyRow(
            listOf(
                sym("£"), sym("¢"), sym("€"), sym("¥"), sym("^"),
                sym("°"), sym("="), sym("{"), sym("}"),
            ),
            leadingGap = 0.5f, trailingGap = 0.5f,
        ),
        KeyRow(
            listOf(toSymbols("?123")) + listOf(
                sym("\\", id = "s_backslash"), sym("©"), sym("®"), sym("™"),
                sym("℅"), sym("["), sym("]"),
            ) + listOf(backspaceKey)
        ),
        bottomRow(toLetters()),
    )

    private val numericLayer = listOf(
        KeyRow(listOf(sym("1", "np_1"), sym("2", "np_2"), sym("3", "np_3"))),
        KeyRow(listOf(sym("4", "np_4"), sym("5", "np_5"), sym("6", "np_6"))),
        KeyRow(listOf(sym("7", "np_7"), sym("8", "np_8"), sym("9", "np_9"))),
        KeyRow(listOf(sym(".", "np_dot"), sym("0", "np_0"), backspaceKey)),
    )

    /** The optional number row that [Metrics.showNumberRow] pins above the letters. */
    val numberRow = KeyRow(
        "1234567890".map { KeyDef(id = "num_$it", label = it.toString()) },
        heightWeight = 0.8f,
    )

    val qwerty = KeyboardLayout(
        id = QWERTY_ID,
        name = "QWERTY",
        layers = mapOf(
            LayerId.Letters to lettersLayer,
            LayerId.Symbols to symbolsLayer,
            LayerId.SymbolsAlt to symbolsAltLayer,
            LayerId.Numeric to numericLayer,
        ),
    )

    val all: List<KeyboardLayout> = listOf(qwerty)

    fun byId(id: String): KeyboardLayout = all.firstOrNull { it.id == id } ?: qwerty
}
