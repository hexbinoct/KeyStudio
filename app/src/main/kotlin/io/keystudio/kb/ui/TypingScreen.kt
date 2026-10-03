package io.keystudio.kb.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.keystudio.core.model.TypingSettings
import io.keystudio.core.store.ProfileStore
import io.keystudio.kb.ui.editor.IconAction
import io.keystudio.kb.ui.editor.RowDivider
import io.keystudio.kb.ui.editor.Section
import io.keystudio.kb.ui.editor.SwitchRow

/**
 * How the keyboard behaves, as opposed to how it looks -- Keys Cafe's
 * "Advanced keyboard settings". Every switch is written straight through; the
 * keyboard process picks the file up the same way it follows theme edits.
 */
@Composable
fun TypingScreen(store: ProfileStore, onBack: () -> Unit) {
    val c = Studio.colors
    var settings by remember { mutableStateOf(store.loadSettings()) }
    fun update(block: (TypingSettings) -> TypingSettings) {
        settings = block(settings)
        store.saveSettings(settings)
    }
    BackHandler(onBack = onBack)

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAction(StudioIcons.Back, "Back", onClick = onBack)
            Text("Typing", style = StudioType.headline, color = c.text, modifier = Modifier.padding(start = 6.dp))
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Section(
                "Swipe",
                footer = "Slide across the letters without lifting your finger. " +
                    "Alternatives appear in the strip above the keys.",
            ) {
                SwitchRow(
                    "Swipe typing", settings.swipeTyping,
                    icon = StudioIcons.Swipe,
                    onChange = { on -> update { it.copy(swipeTyping = on) } },
                )
                RowDivider(50.dp)
                SwitchRow(
                    "Show the swipe trail", settings.swipeTrail,
                    icon = StudioIcons.Sparkles,
                    onChange = { on -> update { it.copy(swipeTrail = on) } },
                )
            }

            Section(
                "Suggestions",
                footer = "Autocorrect only changes words that are in neither the dictionary nor " +
                    "the words you use. Backspace straight after a correction undoes it, and " +
                    "the keyboard remembers your spelling.",
            ) {
                SwitchRow(
                    "Suggest words", settings.suggestions,
                    onChange = { on -> update { it.copy(suggestions = on) } },
                )
                RowDivider()
                SwitchRow(
                    "Autocorrect", settings.autocorrect,
                    subtitle = "Fix a mistyped word when you press space",
                    onChange = { on -> update { it.copy(autocorrect = on) } },
                )
                RowDivider()
                SwitchRow(
                    "Learn new words", settings.learnWords,
                    subtitle = "Names and slang you use stop being corrected",
                    onChange = { on -> update { it.copy(learnWords = on) } },
                )
            }

            Section("Writing") {
                SwitchRow(
                    "Capitalise sentences", settings.autoCapitalize,
                    onChange = { on -> update { it.copy(autoCapitalize = on) } },
                )
                RowDivider()
                SwitchRow(
                    "Double space for a full stop", settings.doubleSpacePeriod,
                    onChange = { on -> update { it.copy(doubleSpacePeriod = on) } },
                )
                RowDivider()
                SwitchRow(
                    "Smart punctuation", settings.smartPunctuation,
                    subtitle = "Moves the space after a word to follow . , ? !",
                    onChange = { on -> update { it.copy(smartPunctuation = on) } },
                )
            }

            Section("Gestures") {
                SwitchRow(
                    "Slide on space to move the cursor", settings.spaceCursor,
                    onChange = { on -> update { it.copy(spaceCursor = on) } },
                )
                RowDivider()
                SwitchRow(
                    "Slide left on delete to erase words", settings.backspaceSwipe,
                    onChange = { on -> update { it.copy(backspaceSwipe = on) } },
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
