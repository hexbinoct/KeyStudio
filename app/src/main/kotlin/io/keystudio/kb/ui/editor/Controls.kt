package io.keystudio.kb.ui.editor

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.keystudio.core.model.ColorSpec
import io.keystudio.kb.ui.Studio
import io.keystudio.kb.ui.StudioIcon
import io.keystudio.kb.ui.StudioIcons
import io.keystudio.kb.ui.StudioType
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The Studio's vocabulary of controls. Every screen is built only from these,
 * so it stays one consistent thing no matter how many settings land on it --
 * which, going by Keys Cafe, is a lot.
 *
 * The shape of it is Notion's and Bear's: small grey sentence-case headers,
 * white groups outlined by a hairline, rows divided by hairlines, and controls
 * slim enough that the values, not the widgets, are what you see.
 *
 * docs/DESIGN.md is the guide: when to use which component, the tokens, and
 * the checklist for a new screen. Add new controls here, not inline.
 */

val GroupShape = RoundedCornerShape(14.dp)
private val RowPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp)

/** A titled group of rows. The whole editor is a stack of these. */
@Composable
fun Section(
    title: String?,
    modifier: Modifier = Modifier,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) SectionHeader(title)
        Group(content = content)
        if (footer != null) {
            Text(
                footer,
                style = StudioType.caption,
                color = Studio.colors.textSecondary,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
            )
        }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 22.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = StudioType.section,
            color = Studio.colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

/** A white card with a hairline edge; rows inside get hairline separators via [RowDivider]. */
@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = Studio.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(GroupShape)
            .background(c.surface)
            .border(1.dp, c.hairline, GroupShape),
        content = content,
    )
}

@Composable
fun RowDivider(inset: Dp = 16.dp) {
    Box(
        Modifier
            .padding(start = inset)
            .fillMaxWidth()
            .height(1.dp)
            .background(Studio.colors.hairline),
    )
}

/** Kept for the old call sites: a section header without its own group. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) = SectionHeader(text, modifier)

/**
 * The basic row: an optional icon, a title with optional detail beneath, and
 * whatever belongs on the right. Tappable when [onClick] is given.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val c = Studio.colors
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(RowPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            StudioIcon(icon, tint = iconTint ?: c.textSecondary, size = 20.dp)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = StudioType.body,
                color = if (enabled) c.text else c.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(subtitle, style = StudioType.caption, color = c.textSecondary)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

/** A row that leads somewhere: a chevron on the right. */
@Composable
fun NavRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    value: String? = null,
    onClick: () -> Unit,
) = ListRow(title, modifier, subtitle, icon, onClick = onClick) {
    if (value != null) {
        Text(value, style = StudioType.body, color = Studio.colors.textSecondary)
        Spacer(Modifier.width(6.dp))
    }
    StudioIcon(StudioIcons.ChevronRight, tint = Studio.colors.textTertiary, size = 18.dp)
}

// -----------------------------------------------------------------------------
// Slider
// -----------------------------------------------------------------------------

/**
 * A slider with its value always visible. [onChange] fires continuously so the
 * keyboard below moves with the thumb; [onSettled] fires once at the end,
 * which is where undo history and disk writes hang off.
 */
@Composable
fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    format: (Float) -> String = { "%.0f".format(it) },
    onSettled: () -> Unit = {},
) {
    val c = Studio.colors
    Column(modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = StudioType.body, color = c.text, modifier = Modifier.weight(1f))
            Text(
                format(value),
                style = StudioType.value,
                color = c.textSecondary,
                modifier = Modifier
                    .background(c.fill, RoundedCornerShape(6.dp))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
        StudioSlider(
            value = value,
            range = range,
            onChange = onChange,
            steps = steps,
            onSettled = onSettled,
        )
    }
}

/**
 * A slim track and a round thumb, as on macOS and in Bear, rather than
 * Material 3's tall bar. Horizontal drags only: a vertical drag that starts on
 * it still scrolls the page, and a tap jumps straight to the spot.
 */
@Composable
fun StudioSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    onSettled: () -> Unit = {},
) {
    val c = Studio.colors
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    var dragging by remember { mutableStateOf(false) }
    val thumbScale by animateFloatAsState(
        if (dragging) 1.18f else 1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "thumb",
    )
    val currentValue by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    val settled by rememberUpdatedState(onSettled)
    val span = (range.endInclusive - range.start).takeIf { it > 0f } ?: 1f
    val thumbRadius = with(density) { 10.dp.toPx() }
    val pad = thumbRadius * 1.3f

    fun valueAt(x: Float, width: Float): Float {
        val t = ((x - pad) / (width - pad * 2)).coerceIn(0f, 1f)
        var v = range.start + t * span
        if (steps > 0) {
            val stepSize = span / (steps + 1)
            v = range.start + ((v - range.start) / stepSize).roundToInt() * stepSize
        }
        return v
    }

    fun emit(v: Float) {
        if (steps > 0 && v != currentValue) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        change(v)
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(34.dp)
            .pointerInput(range, steps) {
                detectTapGestures(
                    onTap = { emit(valueAt(it.x, size.width.toFloat())); settled() },
                )
            }
            .pointerInput(range, steps) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragging = true
                        emit(valueAt(it.x, size.width.toFloat()))
                    },
                    onDragEnd = { dragging = false; settled() },
                    onDragCancel = { dragging = false; settled() },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        emit(valueAt(change.position.x, size.width.toFloat()))
                    },
                )
            },
    ) {
        val t = ((value - range.start) / span).coerceIn(0f, 1f)
        val cy = size.height / 2f
        val trackH = 4.dp.toPx()
        val left = pad
        val right = size.width - pad
        val x = left + (right - left) * t

        drawRoundRect(
            c.fillStrong, Offset(left, cy - trackH / 2), Size(right - left, trackH),
            CornerRadius(trackH / 2),
        )
        drawRoundRect(
            c.accent, Offset(left, cy - trackH / 2), Size(x - left, trackH),
            CornerRadius(trackH / 2),
        )
        if (steps in 1..24) {
            for (i in 0..steps + 1) {
                val tx = left + (right - left) * i / (steps + 1)
                drawCircle(
                    if (tx <= x) c.onAccent.copy(alpha = 0.7f) else c.textTertiary,
                    radius = 1.5.dp.toPx(), center = Offset(tx, cy),
                )
            }
        }
        val r = thumbRadius * thumbScale
        if (dragging) drawCircle(c.accentSoft, radius = r * 1.9f, center = Offset(x, cy))
        // A soft shadow, then the thumb: two layers read as depth without a blur.
        drawCircle(Color.Black.copy(alpha = if (c.isDark) 0.45f else 0.10f), r + 1.5f, Offset(x, cy + 1.5f))
        drawCircle(Color.Black.copy(alpha = if (c.isDark) 0.25f else 0.06f), r + 3f, Offset(x, cy + 2.5f))
        drawCircle(if (c.isDark) Color(0xFFF2F1EE) else Color.White, r, Offset(x, cy))
        drawCircle(c.hairline, r, Offset(x, cy), style = Stroke(1.dp.toPx()))
    }
}

// -----------------------------------------------------------------------------
// Switch
// -----------------------------------------------------------------------------

@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
) = ListRow(
    title = label,
    modifier = modifier,
    subtitle = subtitle,
    icon = icon,
    onClick = { onChange(!checked) },
) {
    StudioSwitch(checked = checked, onChange = onChange)
}

@Composable
fun StudioSwitch(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = Studio.colors
    val track by animateColorAsState(if (checked) c.accent else c.fillStrong, label = "track")
    val knob by animateDpAsState(
        if (checked) 18.dp else 2.dp,
        spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "knob",
    )
    Box(
        modifier
            .size(width = 42.dp, height = 26.dp)
            .clip(CircleShape)
            .background(track)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onChange(!checked) },
    ) {
        Box(
            Modifier
                .offset(x = knob, y = 2.dp)
                .size(22.dp)
                .shadow(2.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

// -----------------------------------------------------------------------------
// Choices
// -----------------------------------------------------------------------------

/**
 * A segmented control for a few short, mutually exclusive options. The
 * selected segment slides rather than jumping, which is most of what makes it
 * feel like a physical switch.
 */
@Composable
fun <T> Segmented(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    labelOf: (T) -> String = { it.toString() },
) {
    val c = Studio.colors
    val index = options.indexOf(selected).coerceAtLeast(0)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(c.fill, RoundedCornerShape(10.dp))
            .padding(3.dp),
    ) {
        val segment = maxWidth / options.size
        val offset by animateDpAsState(
            segment * index, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
            label = "segment",
        )
        Box(
            Modifier
                .offset(x = offset)
                .width(segment)
                .fillMaxHeight()
                .shadow(if (c.isDark) 0.dp else 1.dp, RoundedCornerShape(8.dp))
                .background(c.elevated, RoundedCornerShape(8.dp)),
        )
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, option ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(option) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        labelOf(option),
                        style = StudioType.label,
                        color = if (i == index) c.text else c.textSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Horizontally scrolling single choice, for the longer enums (shapes, effects,
 * fonts). Selected is a soft accent tag, the way Notion marks a chosen option.
 */
@Composable
fun <T> ChipRow(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    labelOf: (T) -> String = { it.toString() },
) {
    val c = Studio.colors
    val state = rememberLazyListState()
    // A selection off the right-hand edge reads as "nothing is selected", which
    // is wrong and confusing on rows of seven or eight options. Bring it into
    // view when the row first appears and whenever the choice changes.
    LaunchedEffect(selected) {
        val index = options.indexOf(selected)
        if (index >= 0) runCatching { state.animateScrollToItem((index - 1).coerceAtLeast(0)) }
    }
    LazyRow(
        modifier.fillMaxWidth(),
        state = state,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        items(options) { option ->
            val isSelected = option == selected
            val bg by animateColorAsState(if (isSelected) c.accentSoft else c.surface, label = "chip")
            Box(
                Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(bg)
                    .border(1.dp, if (isSelected) c.accent.copy(alpha = 0.55f) else c.hairline, RoundedCornerShape(9.dp))
                    .clickable { onSelect(option) }
                    .padding(horizontal = 13.dp, vertical = 8.dp),
            ) {
                Text(
                    labelOf(option),
                    style = StudioType.label,
                    color = if (isSelected) c.accent else c.text,
                )
            }
        }
    }
}

/** Splits CamelCase enum names for display: "RoundedRect" -> "Rounded rect". */
fun prettyName(name: String): String {
    val spaced = name.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
    return spaced.lowercase().replaceFirstChar { it.uppercaseChar() }
}

// -----------------------------------------------------------------------------
// Colour rows
// -----------------------------------------------------------------------------

/** A named row whose right-hand side is a swatch of whatever paint is set. */
@Composable
fun ColorSlotRow(
    label: String,
    spec: ColorSpec,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) = ListRow(label, modifier, onClick = onClick) {
    if (spec is ColorSpec.Solid) {
        Text(
            spec.color.toHex().takeLast(6),
            style = StudioType.value,
            color = Studio.colors.textTertiary,
        )
        Spacer(Modifier.width(10.dp))
    } else {
        Text(
            if (spec is ColorSpec.LinearGradient) "Linear" else "Radial",
            style = StudioType.caption,
            color = Studio.colors.textTertiary,
        )
        Spacer(Modifier.width(10.dp))
    }
    Swatch(spec)
}

@Composable
fun Swatch(spec: ColorSpec, modifier: Modifier = Modifier, size: Int = 26) {
    Box(
        modifier
            .size(size.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(spec.toBrush())
            .border(1.dp, Studio.colors.hairline, RoundedCornerShape(8.dp)),
    )
}

/** Same, for a flat ARGB int slot such as a text colour. */
@Composable
fun ColorIntRow(
    label: String,
    color: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) = ColorSlotRow(label, ColorSpec.Solid(color), modifier, onClick)

/** Grey explanatory text inside a group. */
@Composable
fun GroupNote(text: String) {
    Text(
        text,
        style = StudioType.caption,
        color = Studio.colors.textSecondary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 12.dp),
    )
}

// -----------------------------------------------------------------------------
// Buttons
// -----------------------------------------------------------------------------

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val c = Studio.colors
    Row(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(c.accent)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            StudioIcon(icon, tint = c.onAccent, size = 18.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = StudioType.label, color = c.onAccent)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val c = Studio.colors
    Row(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(c.surface)
            .border(1.dp, c.hairline, RoundedCornerShape(11.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            StudioIcon(icon, tint = c.text, size = 18.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = StudioType.label, color = c.text)
    }
}

/** A row-width quiet action inside a group, e.g. "Choose a different image". */
@Composable
fun GroupAction(text: String, icon: ImageVector? = null, onClick: () -> Unit) {
    val c = Studio.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(RowPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            StudioIcon(icon, tint = c.accent, size = 20.dp)
            Spacer(Modifier.width(14.dp))
        }
        Text(text, style = StudioType.bodyStrong, color = c.accent)
    }
}

/** A round, borderless icon button for top bars. */
@Composable
fun IconAction(
    icon: ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color? = null,
    onClick: () -> Unit,
) {
    val c = Studio.colors
    Box(
        modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        StudioIcon(
            icon,
            tint = if (enabled) (tint ?: c.text) else c.textTertiary.copy(alpha = 0.6f),
            size = 21.dp,
            description = description,
        )
    }
}

// -----------------------------------------------------------------------------
// Colour helpers
// -----------------------------------------------------------------------------

/**
 * Renders a [ColorSpec] as a Compose brush so a swatch shows the real paint,
 * gradients included, rather than an approximation of it.
 */
fun ColorSpec.toBrush(): Brush = when (this) {
    is ColorSpec.Solid -> Brush.linearGradient(listOf(Color(color), Color(color)))

    is ColorSpec.LinearGradient -> {
        val rad = Math.toRadians(angleDeg.toDouble())
        val dx = cos(rad).toFloat()
        val dy = sin(rad).toFloat()
        // A unit vector scaled past any plausible swatch size; the gradient is
        // clamped to the drawn bounds anyway.
        val span = 512f
        Brush.linearGradient(
            colors = colors.map { Color(it) }.ifEmpty { listOf(Color.Black, Color.Black) },
            start = Offset((0.5f - dx / 2f) * span, (0.5f - dy / 2f) * span),
            end = Offset((0.5f + dx / 2f) * span, (0.5f + dy / 2f) * span),
        )
    }

    is ColorSpec.RadialGradient -> Brush.radialGradient(
        colors = colors.map { Color(it) }.ifEmpty { listOf(Color.Black, Color.Black) },
    )
}

/** `#AARRGGBB`, uppercase, which is what the hex field shows and accepts. */
fun Int.toHex(): String = "#%08X".format(this)

fun parseHex(text: String): Int? {
    val raw = text.trim().removePrefix("#")
    val hex = when (raw.length) {
        6 -> "FF$raw"
        8 -> raw
        3 -> "FF" + raw.map { "$it$it" }.joinToString("")
        else -> return null
    }
    return hex.toLongOrNull(16)?.toInt()
}
