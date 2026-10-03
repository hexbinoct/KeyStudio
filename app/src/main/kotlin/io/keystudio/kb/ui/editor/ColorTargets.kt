package io.keystudio.kb.ui.editor

import androidx.compose.runtime.Composable
import io.keystudio.core.model.ColorSpec

/**
 * A colour slot a dialog is currently pointed at.
 *
 * Colour editing is live: the dialog writes straight through to the profile as
 * the finger moves, so [apply] is called many times per second and Cancel works
 * by applying the original value back rather than by withholding the new one.
 */
internal class SpecTarget(
    val title: String,
    val current: ColorSpec,
    val allowGradient: Boolean = true,
    val apply: (ColorSpec) -> Unit,
)

internal class IntTarget(
    val title: String,
    val current: Int,
    val apply: (Int) -> Unit,
)

/** Shows whichever colour dialog is open, and nothing when none is. */
@Composable
internal fun ColorDialogHost(
    specTarget: SpecTarget?,
    intTarget: IntTarget?,
    onClose: () -> Unit,
    onSettled: () -> Unit,
) {
    if (specTarget != null) {
        val original = specTarget.current
        ColorSpecDialog(
            title = specTarget.title,
            initial = original,
            allowGradient = specTarget.allowGradient,
            onDismiss = { specTarget.apply(original); onSettled(); onClose() },
            onLive = specTarget.apply,
            onConfirm = { specTarget.apply(it); onSettled(); onClose() },
        )
    }
    if (intTarget != null) {
        val original = intTarget.current
        ColorPickerDialog(
            title = intTarget.title,
            initial = original,
            onDismiss = { intTarget.apply(original); onSettled(); onClose() },
            onLive = intTarget.apply,
            onConfirm = { intTarget.apply(it); onSettled(); onClose() },
        )
    }
}
