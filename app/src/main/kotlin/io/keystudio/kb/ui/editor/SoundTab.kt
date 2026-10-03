package io.keystudio.kb.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.keystudio.core.model.SoundProfile

/**
 * "Sound" -- the third Keys Cafe tab.
 *
 * Honest about its state: the sampled profiles have no audio behind them yet,
 * so they are labelled rather than quietly behaving like System.
 */
@Composable
fun SoundTab(editor: ProfileEditor) {
    val sound = editor.profile.sound

    Column(Modifier.fillMaxWidth()) {
        Section(
            "Sound",
            footer = when {
                sound.profile == SoundProfile.None -> null
                sound.profile != SoundProfile.System ->
                    "${sound.profile.name} has no samples yet and plays the system click."
                else -> "Keyboard sound also follows the system setting and your media volume."
            },
        ) {
            ChipRow(
                options = SoundProfile.entries.toList(),
                selected = sound.profile,
                labelOf = { prettyName(it.name) },
                onSelect = { p ->
                    editor.edit { it.copy(sound = it.sound.copy(profile = p)) }
                    editor.settle()
                },
            )
            if (sound.profile != SoundProfile.None) {
                RowDivider(0.dp)
                SliderRow(
                    label = "Volume",
                    value = sound.volume,
                    range = 0f..1f,
                    onChange = { v -> editor.edit("volume") { it.copy(sound = it.sound.copy(volume = v)) } },
                    onSettled = editor::settle,
                    format = { "${(it * 100).toInt()}%" },
                )
            }
        }

        Section(
            "Vibration",
            footer = "The Studio's preview stays silent and still, so editing does not click at you. " +
                "Try it in the field on the home screen.",
        ) {
            SwitchRow(
                label = "Vibrate on key press",
                checked = sound.hapticsEnabled,
                onChange = { on ->
                    editor.edit { it.copy(sound = it.sound.copy(hapticsEnabled = on)) }
                    editor.settle()
                },
            )
            if (sound.hapticsEnabled) {
                RowDivider()
                SliderRow(
                    label = "Strength",
                    value = sound.hapticDurationMs.toFloat(),
                    range = 0f..45f,
                    onChange = { v ->
                        editor.edit("haptic") {
                            it.copy(sound = it.sound.copy(hapticDurationMs = v.toInt()))
                        }
                    },
                    onSettled = editor::settle,
                    format = {
                        when {
                            it < 1f -> "Off"
                            it.toInt() == 12 -> "System"
                            else -> "${it.toInt()} ms"
                        }
                    },
                )
            }
        }
    }
}

