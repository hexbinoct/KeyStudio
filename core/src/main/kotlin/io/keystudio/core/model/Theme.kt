package io.keystudio.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A paint source. Kept as a sealed hierarchy so the theme editor can offer
 * "solid or gradient" anywhere a colour is accepted, exactly like Keys Cafe does.
 * Colours are packed ARGB ints.
 */
@Serializable
sealed interface ColorSpec {

    @Serializable
    @SerialName("solid")
    data class Solid(val color: Int) : ColorSpec

    @Serializable
    @SerialName("linear")
    data class LinearGradient(
        val colors: List<Int>,
        /** Degrees clockwise from "left to right". */
        val angleDeg: Float = 90f,
        val stops: List<Float>? = null,
    ) : ColorSpec

    @Serializable
    @SerialName("radial")
    data class RadialGradient(
        val colors: List<Int>,
        /** Fractions of the bounding box. */
        val centerX: Float = 0.5f,
        val centerY: Float = 0.5f,
        val radiusScale: Float = 0.75f,
    ) : ColorSpec

    companion object {
        fun of(color: Int): ColorSpec = Solid(color)
    }
}

/** The keyboard's backdrop, drawn behind every key. */
@Serializable
sealed interface BackgroundSpec {

    @Serializable
    @SerialName("fill")
    data class Fill(val color: ColorSpec) : BackgroundSpec

    @Serializable
    @SerialName("image")
    data class Image(
        val uri: String,
        /** 0f..1f dim applied over the image so key labels stay readable. */
        val scrim: Float = 0.2f,
        val blurRadius: Float = 0f,
    ) : BackgroundSpec

    /**
     * A procedurally animated backdrop. Rendered by the effects engine rather
     * than as a static paint, so it costs a frame callback while visible.
     */
    @Serializable
    @SerialName("animated")
    data class Animated(
        val kind: AnimatedBackground,
        val colors: List<Int>,
        val speed: Float = 1f,
        val intensity: Float = 1f,
    ) : BackgroundSpec
}

@Serializable
enum class AnimatedBackground {
    /** Slow drifting colour field. Cheap, always safe. */
    Aurora,
    /** Rising bubbles. */
    Bubbles,
    /** Falling particles. */
    Snow,
    /** Two-tone gradient that slowly rotates. */
    Drift,
    /** Subtle moving noise/grain over the base colour. */
    Mesh,
}

@Serializable
enum class ShapeStyle { RoundedRect, Squircle, Pill, Circle, Cut, Flat }

@Serializable
data class KeyShape(
    val style: ShapeStyle = ShapeStyle.RoundedRect,
    val cornerRadiusDp: Float = 10f,
    /** Shrinks the key inside its cell, creating the visual gap between keys. */
    val insetDp: Float = 3f,
)

@Serializable
data class StrokeSpec(
    val color: Int,
    val widthDp: Float = 1f,
)

@Serializable
enum class FontFamily { System, SystemCondensed, Monospace, Serif, RoundedSans }

@Serializable
data class FontSpec(
    val family: FontFamily = FontFamily.System,
    /** Multiplies the size the layout engine derives from key height. */
    val sizeScale: Float = 1f,
    val bold: Boolean = false,
    /** Optional font file in app storage, set by the user. */
    val customFontPath: String? = null,
)

/**
 * Everything about how the keyboard *looks* in one light or dark variant.
 * A [KeyboardProfile] holds two of these.
 */
@Serializable
data class Theme(
    val id: String,
    val name: String,
    val background: BackgroundSpec,
    /** Ordinary character keys. */
    val keyFill: ColorSpec,
    /** Modifier keys: shift, backspace, layer switches. */
    val modifierFill: ColorSpec,
    /** The action key (enter), usually the accent. */
    val accentFill: ColorSpec,
    /** Overlay/target colour while a key is held. */
    val pressedFill: ColorSpec,
    val textColor: Int,
    val modifierTextColor: Int,
    val accentTextColor: Int,
    val hintColor: Int,
    val stroke: StrokeSpec? = null,
    val shape: KeyShape = KeyShape(),
    val font: FontSpec = FontSpec(),
    /** Drop shadow under each key, in dp. 0 disables it. */
    val elevationDp: Float = 0f,
    /** Colour of the popup shown on long press. */
    val popupFill: ColorSpec? = null,
    val popupTextColor: Int? = null,
    val isDark: Boolean = false,
)
