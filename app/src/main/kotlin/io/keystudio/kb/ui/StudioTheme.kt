package io.keystudio.kb.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.keystudio.kb.R

/**
 * The Studio's look: calm, paper-like and typographic, in the manner of Bear
 * and Notion. Warm neutrals rather than tinted greys, hairlines rather than
 * shadows, one accent used sparingly, and hierarchy carried by type weight and
 * colour rather than by boxes. The keyboards are the colourful thing on screen;
 * the tool around them stays out of their way.
 */
@Immutable
data class StudioColors(
    /** The page. */
    val background: Color,
    /** Grouped cards and sheets sitting on the page. */
    val surface: Color,
    /** Something raised within a surface: the selected segment, a menu. */
    val elevated: Color,
    /** Hover-grey fill for quiet controls and inactive tracks. */
    val fill: Color,
    /** A slightly stronger fill, for pressed states. */
    val fillStrong: Color,
    /** 1 px dividers and outlines. */
    val hairline: Color,
    val text: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val onAccent: Color,
    /** The accent at low strength, for selected chips and highlights. */
    val accentSoft: Color,
    val success: Color,
    val danger: Color,
    val isDark: Boolean,
)

private val Light = StudioColors(
    background = Color(0xFFF7F6F3),
    surface = Color(0xFFFFFFFF),
    elevated = Color(0xFFFFFFFF),
    fill = Color(0xFFEFEEEA),
    fillStrong = Color(0xFFE4E2DD),
    hairline = Color(0xFFE6E4DF),
    text = Color(0xFF2F2E2B),
    textSecondary = Color(0xFF7A7873),
    textTertiary = Color(0xFFA9A69F),
    accent = Color(0xFFDD4C4F),
    onAccent = Color.White,
    accentSoft = Color(0x1FDD4C4F),
    success = Color(0xFF3F9E6E),
    danger = Color(0xFFD1453B),
    isDark = false,
)

private val Dark = StudioColors(
    background = Color(0xFF191919),
    surface = Color(0xFF212121),
    elevated = Color(0xFF2E2E2E),
    fill = Color(0xFF2A2A2A),
    fillStrong = Color(0xFF363636),
    hairline = Color(0xFF303030),
    text = Color(0xFFEAE9E6),
    textSecondary = Color(0xFF9D9B96),
    textTertiary = Color(0xFF6C6A66),
    accent = Color(0xFFF06A6A),
    onAccent = Color(0xFF1B0C0C),
    accentSoft = Color(0x29F06A6A),
    success = Color(0xFF5DBB8A),
    danger = Color(0xFFEF6B61),
    isDark = true,
)

/**
 * Inter, bundled as one variable font. Each weight has to set the `wght` axis
 * itself; declaring the weight alone only tells Compose which entry to pick,
 * and every entry would then render at the file's default, regular.
 */
@OptIn(ExperimentalTextApi::class)
private fun inter(weight: Int) = Font(
    R.font.inter,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Inter = FontFamily(inter(400), inter(500), inter(600), inter(700))

/** The type scale. Few sizes, distinguished mostly by weight and colour. */
object StudioType {
    val display = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Bold,
        fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.8).sp,
    )
    val title = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp, lineHeight = 24.sp, letterSpacing = (-0.3).sp,
    )
    val headline = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = (-0.15).sp,
    )
    val body = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 21.sp, letterSpacing = (-0.1).sp,
    )
    val bodyStrong = body.copy(fontWeight = FontWeight.Medium)
    /** Small grey section headers, sentence case, as Notion does them. */
    val section = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Medium,
        fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.sp,
    )
    val caption = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.sp,
    )
    val label = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = (-0.1).sp,
    )
    /** Numbers that change under the finger: tabular so they do not jiggle. */
    val value = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Medium,
        fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = "tnum",
    )
}

private val LocalStudioColors = staticCompositionLocalOf { Light }

object Studio {
    val colors: StudioColors
        @Composable @ReadOnlyComposable get() = LocalStudioColors.current
}

@Composable
fun StudioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val c = if (darkTheme) Dark else Light
    // Material components still appear (menus, text fields, dialogs); map the
    // palette onto them so nothing on screen comes from a different app.
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.onAccent,
            secondary = c.accent, onSecondary = c.onAccent,
            background = c.background, onBackground = c.text,
            surface = c.surface, onSurface = c.text,
            surfaceVariant = c.fill, onSurfaceVariant = c.textSecondary,
            surfaceContainer = c.elevated, surfaceContainerHigh = c.elevated,
            surfaceContainerHighest = c.elevated, surfaceContainerLow = c.surface,
            outline = c.hairline, outlineVariant = c.hairline, error = c.danger,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = c.onAccent,
            secondary = c.accent, onSecondary = c.onAccent,
            background = c.background, onBackground = c.text,
            surface = c.surface, onSurface = c.text,
            surfaceVariant = c.fill, onSurfaceVariant = c.textSecondary,
            surfaceContainer = c.elevated, surfaceContainerHigh = c.elevated,
            surfaceContainerHighest = c.elevated, surfaceContainerLow = c.surface,
            outline = c.hairline, outlineVariant = c.hairline, error = c.danger,
        )
    }
    val base = Typography()
    val typography = Typography(
        displaySmall = StudioType.display,
        headlineSmall = StudioType.title,
        titleLarge = StudioType.title,
        titleMedium = StudioType.headline,
        titleSmall = StudioType.label,
        bodyLarge = StudioType.body,
        bodyMedium = StudioType.body,
        bodySmall = StudioType.caption,
        labelLarge = StudioType.label,
        labelMedium = StudioType.caption,
        labelSmall = base.labelSmall.copy(fontFamily = Inter),
    )
    CompositionLocalProvider(LocalStudioColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
