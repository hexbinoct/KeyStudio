package io.keystudio.core.view

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import io.keystudio.core.model.KeyCode
import io.keystudio.core.model.SoundConfig
import io.keystudio.core.model.SoundProfile

/**
 * What a key press feels and sounds like.
 *
 * Sound uses the platform's own keyboard effects rather than bundled samples.
 * They respect the user's system-wide "keyboard sounds" switch and their media
 * volume, which a raw `MediaPlayer` would not, and the platform already gives
 * separate effects for space, delete and enter -- so a keyboard that uses them
 * sounds correct instead of clicking identically on every key.
 *
 * The named sampled profiles ([SoundProfile.Typewriter] and friends) have no
 * audio behind them yet and fall back to the system click; they are wired for
 * the effects milestone rather than faked here.
 */
class KeyFeedback(private val view: View) {

    private val audio: AudioManager? =
        view.context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (view.context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
            ?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        view.context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun onKeyDown(config: SoundConfig, code: KeyCode) {
        playSound(config, code)
        vibrate(config)
    }

    private fun playSound(config: SoundConfig, code: KeyCode) {
        if (config.profile == SoundProfile.None) return
        val manager = audio ?: return
        val effect = when (code) {
            KeyCode.Space -> AudioManager.FX_KEYPRESS_SPACEBAR
            KeyCode.Backspace -> AudioManager.FX_KEYPRESS_DELETE
            KeyCode.Enter -> AudioManager.FX_KEYPRESS_RETURN
            else -> AudioManager.FX_KEYPRESS_STANDARD
        }
        manager.playSoundEffect(effect, config.volume.coerceIn(0f, 1f))
    }

    private fun vibrate(config: SoundConfig) {
        if (!config.hapticsEnabled) return
        val ms = config.hapticDurationMs
        if (ms <= 0) return

        // The view's own haptic constant is what the system tunes per device, so
        // it is the better default; an explicit duration is treated as the user
        // overriding that on purpose.
        if (ms == DEFAULT_HAPTIC_MS) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            return
        }
        val device = vibrator ?: run {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            return
        }
        if (!device.hasVibrator()) return
        runCatching {
            device.vibrate(
                VibrationEffect.createOneShot(
                    ms.toLong(),
                    VibrationEffect.DEFAULT_AMPLITUDE,
                ),
            )
        }
    }

    private companion object {
        /** Matches [SoundConfig.hapticDurationMs]'s default. */
        const val DEFAULT_HAPTIC_MS = 12
    }
}
