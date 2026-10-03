package io.keystudio.core.presets

import io.keystudio.core.model.AnimatedBackground
import io.keystudio.core.model.BackgroundSpec
import io.keystudio.core.model.ColorSpec
import io.keystudio.core.model.EffectsConfig
import io.keystudio.core.model.FontFamily
import io.keystudio.core.model.FontSpec
import io.keystudio.core.model.KeyColorEffect
import io.keystudio.core.model.KeyMotionEffect
import io.keystudio.core.model.KeyShape
import io.keystudio.core.model.KeyboardProfile
import io.keystudio.core.model.Metrics
import io.keystudio.core.model.ParticleEffect
import io.keystudio.core.model.ShapeStyle
import io.keystudio.core.model.SoundConfig
import io.keystudio.core.model.SoundProfile
import io.keystudio.core.model.StrokeSpec
import io.keystudio.core.model.Theme

/**
 * The starter shelf. Every preset here is an ordinary [KeyboardProfile], so a
 * user can open any of them in the editor and save a variant.
 */
object Themes {

    private fun solid(color: Long) = ColorSpec.Solid(color.toInt())

    private fun gradient(vararg colors: Long, angle: Float = 90f) =
        ColorSpec.LinearGradient(colors.map { it.toInt() }, angle)

    // -------------------------------------------------------------------------

    private val midnightDark = Theme(
        id = "midnight.dark",
        name = "Midnight",
        background = BackgroundSpec.Fill(gradient(0xFF0B0A1F, 0xFF161034, angle = 90f)),
        keyFill = gradient(0xFF2A2350, 0xFF211B41, angle = 90f),
        modifierFill = solid(0xFF191333),
        accentFill = gradient(0xFF7C4DFF, 0xFF5B2BE0, angle = 90f),
        pressedFill = solid(0xCC9B7BFF),
        textColor = 0xFFF2EEFF.toInt(),
        modifierTextColor = 0xFFB9AEE8.toInt(),
        accentTextColor = 0xFFFFFFFF.toInt(),
        hintColor = 0x99A79BE0.toInt(),
        shape = KeyShape(ShapeStyle.RoundedRect, cornerRadiusDp = 12f, insetDp = 3f),
        font = FontSpec(FontFamily.RoundedSans),
        elevationDp = 3f,
        isDark = true,
    )

    private val midnightLight = midnightDark.copy(
        id = "midnight.light",
        background = BackgroundSpec.Fill(gradient(0xFFEDEAFB, 0xFFDCD6F7, angle = 90f)),
        keyFill = gradient(0xFFFFFFFF, 0xFFF3F0FF, angle = 90f),
        modifierFill = solid(0xFFDED8F5),
        accentFill = gradient(0xFF7C4DFF, 0xFF5B2BE0, angle = 90f),
        pressedFill = solid(0xCC9B7BFF),
        textColor = 0xFF231C4A.toInt(),
        modifierTextColor = 0xFF4A3F7A.toInt(),
        hintColor = 0x995C5194.toInt(),
        isDark = false,
    )

    private val strawberryLight = Theme(
        id = "strawberry.light",
        name = "Strawberry Milk",
        background = BackgroundSpec.Fill(gradient(0xFFFFE4EE, 0xFFFFD1E3, angle = 90f)),
        keyFill = solid(0xFFFFFFFF),
        modifierFill = solid(0xFFFFC2DA),
        accentFill = gradient(0xFFFF6FA5, 0xFFFF4785, angle = 90f),
        pressedFill = solid(0xCCFF9EC4),
        textColor = 0xFF6B2A44.toInt(),
        modifierTextColor = 0xFF8A3558.toInt(),
        accentTextColor = 0xFFFFFFFF.toInt(),
        hintColor = 0x99A8608A.toInt(),
        shape = KeyShape(ShapeStyle.Squircle, cornerRadiusDp = 14f, insetDp = 3.5f),
        font = FontSpec(FontFamily.RoundedSans),
        elevationDp = 2f,
        isDark = false,
    )

    private val strawberryDark = strawberryLight.copy(
        id = "strawberry.dark",
        background = BackgroundSpec.Fill(gradient(0xFF2B1220, 0xFF3A1729, angle = 90f)),
        keyFill = solid(0xFF4A2036),
        modifierFill = solid(0xFF361624),
        pressedFill = solid(0xCCFF9EC4),
        textColor = 0xFFFFE0EC.toInt(),
        modifierTextColor = 0xFFE8A9C4.toInt(),
        hintColor = 0x99C98AA6.toInt(),
        isDark = true,
    )

    private val neonDark = Theme(
        id = "neon.dark",
        name = "Neon Grid",
        background = BackgroundSpec.Fill(solid(0xFF05060A)),
        keyFill = solid(0xFF0C1018),
        modifierFill = solid(0xFF080B11),
        accentFill = solid(0xFF0C1018),
        pressedFill = solid(0xCC00E5FF),
        textColor = 0xFF5CF2FF.toInt(),
        modifierTextColor = 0xFF2FBFD4.toInt(),
        accentTextColor = 0xFF5CF2FF.toInt(),
        hintColor = 0x882FBFD4.toInt(),
        stroke = StrokeSpec(0xFF1B6C7A.toInt(), widthDp = 1f),
        shape = KeyShape(ShapeStyle.Cut, cornerRadiusDp = 6f, insetDp = 3f),
        font = FontSpec(FontFamily.Monospace, bold = true),
        elevationDp = 0f,
        isDark = true,
    )

    private val graphiteDark = Theme(
        id = "graphite.dark",
        name = "Graphite",
        background = BackgroundSpec.Fill(solid(0xFF121212)),
        keyFill = solid(0xFF2B2B2B),
        modifierFill = solid(0xFF1E1E1E),
        accentFill = solid(0xFF3D6EFF),
        pressedFill = solid(0xCC6E8FFF),
        textColor = 0xFFEDEDED.toInt(),
        modifierTextColor = 0xFFB4B4B4.toInt(),
        accentTextColor = 0xFFFFFFFF.toInt(),
        hintColor = 0x99909090.toInt(),
        shape = KeyShape(ShapeStyle.RoundedRect, cornerRadiusDp = 8f, insetDp = 2.5f),
        font = FontSpec(FontFamily.System),
        elevationDp = 0f,
        isDark = true,
    )

    private val graphiteLight = graphiteDark.copy(
        id = "graphite.light",
        background = BackgroundSpec.Fill(solid(0xFFECEFF3)),
        keyFill = solid(0xFFFFFFFF),
        modifierFill = solid(0xFFD7DCE4),
        pressedFill = solid(0xCC8FAAFF),
        textColor = 0xFF1B1D21.toInt(),
        modifierTextColor = 0xFF4A4F57.toInt(),
        hintColor = 0x996A7079.toInt(),
        elevationDp = 1.5f,
        isDark = false,
    )

    private val auroraDark = Theme(
        id = "aurora.dark",
        name = "Aurora",
        background = BackgroundSpec.Animated(
            kind = AnimatedBackground.Aurora,
            colors = listOf(
                0xFF0F2027.toInt(), 0xFF203A43.toInt(),
                0xFF2C5364.toInt(), 0xFF1B3A4B.toInt(),
            ),
            speed = 0.5f,
        ),
        // Translucent keys let the animated field show through, which is the
        // whole point of a live background.
        keyFill = solid(0x33FFFFFF),
        modifierFill = solid(0x1FFFFFFF),
        accentFill = solid(0x6644E0C8),
        pressedFill = solid(0xAA7FFFE4),
        textColor = 0xFFEAFBFF.toInt(),
        modifierTextColor = 0xFFB6D9E4.toInt(),
        accentTextColor = 0xFF03202A.toInt(),
        hintColor = 0x8896BCC9.toInt(),
        stroke = StrokeSpec(0x2BFFFFFF, widthDp = 0.8f),
        shape = KeyShape(ShapeStyle.Squircle, cornerRadiusDp = 14f, insetDp = 3.5f),
        font = FontSpec(FontFamily.RoundedSans),
        elevationDp = 0f,
        isDark = true,
    )

    private val sunsetLight = Theme(
        id = "sunset.light",
        name = "Sunset",
        background = BackgroundSpec.Fill(
            gradient(0xFFFF9A6C, 0xFFFF6B9D, 0xFF9B5DE5, angle = 115f)
        ),
        keyFill = solid(0x40FFFFFF),
        modifierFill = solid(0x26FFFFFF),
        accentFill = solid(0xFFFFFFFF),
        pressedFill = solid(0xAAFFFFFF),
        textColor = 0xFFFFFFFF.toInt(),
        modifierTextColor = 0xFFFFE9F2.toInt(),
        accentTextColor = 0xFFD64A7A.toInt(),
        hintColor = 0x99FFFFFF.toInt(),
        stroke = StrokeSpec(0x33FFFFFF, widthDp = 0.8f),
        shape = KeyShape(ShapeStyle.Pill, cornerRadiusDp = 20f, insetDp = 3f),
        font = FontSpec(FontFamily.RoundedSans, bold = true),
        elevationDp = 0f,
        isDark = true,
    )

    private val paperLight = Theme(
        id = "paper.light",
        name = "Paper",
        background = BackgroundSpec.Fill(solid(0xFFF4F1EA)),
        keyFill = solid(0xFFFFFDF8),
        modifierFill = solid(0xFFE6E1D6),
        accentFill = solid(0xFF3E3A33),
        pressedFill = solid(0xCCD9D2C4),
        textColor = 0xFF2E2A24.toInt(),
        modifierTextColor = 0xFF5D564B.toInt(),
        accentTextColor = 0xFFFAF7F0.toInt(),
        hintColor = 0x99857D6F.toInt(),
        stroke = StrokeSpec(0x22000000, widthDp = 0.7f),
        shape = KeyShape(ShapeStyle.RoundedRect, cornerRadiusDp = 6f, insetDp = 2.5f),
        font = FontSpec(FontFamily.Serif),
        elevationDp = 1f,
        isDark = false,
    )

    // -------------------------------------------------------------------------

    private fun profile(
        id: String,
        name: String,
        light: Theme,
        dark: Theme,
        effects: EffectsConfig,
        sound: SoundProfile = SoundProfile.System,
    ) = KeyboardProfile(
        id = id,
        name = name,
        layoutId = Layouts.QWERTY_ID,
        light = light,
        dark = dark,
        effects = effects,
        sound = SoundConfig(profile = sound),
        metrics = Metrics(),
    )

    val midnight = profile(
        "preset.midnight", "Midnight", midnightLight, midnightDark,
        EffectsConfig(
            keyColor = KeyColorEffect.Ripple,
            motion = KeyMotionEffect.Bounce,
            particle = ParticleEffect.Spark,
            accentColor = 0xFF9B7BFF.toInt(),
            particleColors = listOf(0xFF9B7BFF.toInt(), 0xFF6BE9FF.toInt(), 0xFFFF7BC8.toInt()),
        ),
    )

    val strawberry = profile(
        "preset.strawberry", "Strawberry Milk", strawberryLight, strawberryDark,
        EffectsConfig(
            keyColor = KeyColorEffect.Flash,
            motion = KeyMotionEffect.Jelly,
            particle = ParticleEffect.Heart,
            accentColor = 0xFFFF6FA5.toInt(),
            particleColors = listOf(0xFFFF6FA5.toInt(), 0xFFFFB3CF.toInt(), 0xFFFFFFFF.toInt()),
        ),
        sound = SoundProfile.Bubble,
    )

    val neon = profile(
        "preset.neon", "Neon Grid", neonDark, neonDark,
        EffectsConfig(
            keyColor = KeyColorEffect.Outline,
            motion = KeyMotionEffect.Travel,
            particle = ParticleEffect.Neon,
            accentColor = 0xFF00E5FF.toInt(),
            particleColors = listOf(0xFF00E5FF.toInt(), 0xFF7CFF6B.toInt()),
            intensity = 1.2f,
        ),
        sound = SoundProfile.Mechanical,
    )

    val graphite = profile(
        "preset.graphite", "Graphite", graphiteLight, graphiteDark,
        EffectsConfig(
            keyColor = KeyColorEffect.Ripple,
            motion = KeyMotionEffect.Shrink,
            particle = ParticleEffect.None,
            accentColor = 0xFF3D6EFF.toInt(),
            intensity = 0.8f,
        ),
    )

    val aurora = profile(
        "preset.aurora", "Aurora", auroraDark, auroraDark,
        EffectsConfig(
            keyColor = KeyColorEffect.Glow,
            motion = KeyMotionEffect.Pop,
            particle = ParticleEffect.Bubble,
            accentColor = 0xFF7FFFE4.toInt(),
            particleColors = listOf(0x887FFFE4.toInt(), 0x88B6D9E4.toInt()),
        ),
        sound = SoundProfile.Soft,
    )

    val sunset = profile(
        "preset.sunset", "Sunset", sunsetLight, sunsetLight,
        EffectsConfig(
            keyColor = KeyColorEffect.Fill,
            motion = KeyMotionEffect.Bounce,
            particle = ParticleEffect.Confetti,
            accentColor = 0xFFFFFFFF.toInt(),
            particleColors = listOf(
                0xFFFFD166.toInt(), 0xFFEF476F.toInt(),
                0xFF06D6A0.toInt(), 0xFFFFFFFF.toInt(),
            ),
        ),
    )

    val paper = profile(
        "preset.paper", "Paper", paperLight, graphiteDark,
        EffectsConfig(
            keyColor = KeyColorEffect.Footprint,
            motion = KeyMotionEffect.Travel,
            particle = ParticleEffect.None,
            accentColor = 0xFF3E3A33.toInt(),
            intensity = 0.7f,
        ),
        sound = SoundProfile.Typewriter,
    )

    val all: List<KeyboardProfile> =
        listOf(midnight, strawberry, aurora, neon, sunset, graphite, paper)

    val default: KeyboardProfile = midnight

    fun byId(id: String): KeyboardProfile = all.firstOrNull { it.id == id } ?: default
}
