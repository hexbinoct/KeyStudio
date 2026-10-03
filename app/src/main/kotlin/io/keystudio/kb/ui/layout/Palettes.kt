package io.keystudio.kb.ui.layout

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.keystudio.kb.ui.Studio
import io.keystudio.kb.ui.StudioType
import io.keystudio.kb.ui.editor.ChipRow
import io.keystudio.kb.ui.editor.PrimaryButton

/** The palettes Keys Cafe offers when you replace a key or fill its long-press set. */
enum class Palette(val title: String, val items: List<String>) {
    Symbols(
        "Symbols",
        "! @ # $ % ^ & * ( ) - _ = + [ ] { } ; : ' \" , . < > / ? \\ | ` ~ … • · ¿ ¡ « » “ ” ‘ ’ – — § ¶ © ® ™ °"
            .split(" "),
    ),
    Emoji(
        "Emoji",
        "😀 😂 🥹 😊 😍 🥰 😘 😎 🤔 😅 😭 🥲 😡 😴 🤯 🙃 👍 👎 👏 🙏 💪 👀 🔥 ✨ 🎉 ❤️ 🧡 💛 💚 💙 💜 🖤 💯 ✅ ❌ ⭐ 🌙 ☀️ 🌈 🍕 ☕ 🎂 🚀"
            .split(" "),
    ),
    Maths(
        "Maths",
        "+ − × ÷ = ≠ ≈ ± √ ∛ ∞ π ∑ ∏ ∫ ∆ ∇ ≤ ≥ < > % ‰ ∂ ∈ ∉ ∩ ∪ ⊂ ⊃ ∀ ∃ ¬ ∧ ∨ ⊕ θ λ μ σ Ω".split(" "),
    ),
    Fractions(
        "Fractions",
        "½ ⅓ ⅔ ¼ ¾ ⅕ ⅖ ⅗ ⅘ ⅙ ⅚ ⅛ ⅜ ⅝ ⅞ ⁰ ¹ ² ³ ⁴ ⁵ ⁶ ⁷ ⁸ ⁹ ⁿ ₀ ₁ ₂ ₃".split(" "),
    ),
    Currency(
        "Currency",
        "$ € £ ¥ ₹ ₨ ¢ ₩ ₽ ₺ ₿ ₦ ₱ ₫ ฿ ₴ ₪ ₡ ₲ ₵ ₸ ₼ ﷼".split(" "),
    ),
    Arrows(
        "Arrows",
        "← → ↑ ↓ ↔ ↕ ↖ ↗ ↘ ↙ ⇐ ⇒ ⇑ ⇓ ⇔ ↩ ↪ ↺ ↻ ➜ ➔ ➤ ⤴ ⤵ ▲ ▼ ◀ ▶".split(" "),
    ),
    Kaomoji(
        "Kaomoji",
        listOf(
            "¯\\_(ツ)_/¯", "(╯°□°)╯︵ ┻━┻", "( ͡° ͜ʖ ͡°)", "(｡•̀ᴗ-)✧", "ʕ•ᴥ•ʔ", "(づ｡◕‿‿◕｡)づ",
            "(ಥ﹏ಥ)", "٩(◕‿◕)۶", "(≧◡≦)", "♪(´▽｀)", "(ᵔᴥᵔ)", "(•_•)", "(¬‿¬)", "ヽ(•‿•)ノ",
            "(ﾉ◕ヮ◕)ﾉ*:･ﾟ✧", "(っ˘ڡ˘ς)", "(✿◠‿◠)", "(⌐■_■)",
        ),
    ),
    Macros(
        "Macros",
        listOf("%DATE%", "%TIME%", "%DAY%", "%CLIPBOARD%"),
    ),
}

/** What the macro placeholders become, for the palette's explanation. */
val MACRO_HELP = mapOf(
    "%DATE%" to "Today's date",
    "%TIME%" to "The time now",
    "%DAY%" to "Today's weekday",
    "%CLIPBOARD%" to "What you last copied",
)

/**
 * Picks characters from the palettes.
 *
 * [multiple] keeps the sheet open so a long-press set can be filled in one go;
 * otherwise the first tap is the answer. [chosen] marks what is already there.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaletteDialog(
    title: String,
    multiple: Boolean,
    chosen: List<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    includeMacros: Boolean = false,
) {
    val c = Studio.colors
    val palettes = Palette.entries.filter { includeMacros || it != Palette.Macros }
    var palette by remember { mutableStateOf(palettes.first()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = c.elevated) {
            Column(Modifier.padding(vertical = 18.dp)) {
                Text(
                    title,
                    style = StudioType.title,
                    color = c.text,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                ChipRow(
                    options = palettes,
                    selected = palette,
                    onSelect = { palette = it },
                    labelOf = { it.title },
                )
                Column(
                    Modifier
                        .heightIn(max = 340.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                ) {
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (item in palette.items) {
                            val isChosen = item in chosen
                            val wide = item.length > 2
                            Box(
                                Modifier
                                    .heightIn(min = 44.dp)
                                    .widthIn(min = 44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isChosen) c.accentSoft else c.fill)
                                    .border(
                                        1.dp,
                                        if (isChosen) c.accent.copy(alpha = 0.6f) else c.hairline,
                                        RoundedCornerShape(10.dp),
                                    )
                                    .clickable {
                                        onPick(item)
                                        if (!multiple) onDismiss()
                                    }
                                    .padding(horizontal = if (wide) 10.dp else 0.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        item,
                                        fontSize = if (wide) 14.sp else 20.sp,
                                        color = c.text,
                                        textAlign = TextAlign.Center,
                                    )
                                    MACRO_HELP[item]?.let {
                                        Text(it, style = StudioType.caption, color = c.textSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
                if (multiple) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 16.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        PrimaryButton("Done", onClick = onDismiss)
                    }
                }
            }
        }
    }
}
