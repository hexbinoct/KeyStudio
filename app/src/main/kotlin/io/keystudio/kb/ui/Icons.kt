package io.keystudio.kb.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Line icons for the Studio, drawn from Lucide's path data (ISC licence,
 * https://lucide.dev). A handful of SVG paths is a few hundred bytes; the
 * Material extended icon set is tens of megabytes for the same dozen glyphs.
 * All on a 24-unit grid with a 1.75 stroke, which reads finer than Material's
 * filled icons and sits better next to Inter.
 */
object StudioIcons {
    private fun icon(name: String, vararg paths: String): ImageVector {
        val builder = ImageVector.Builder(
            name = name, defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        )
        for (d in paths) {
            builder.addPath(
                pathData = addPathNodes(d),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }

    val Back = icon("back", "m12 19-7-7 7-7", "M19 12H5")
    val ChevronRight = icon("chevron-right", "m9 18 6-6-6-6")
    val ChevronDown = icon("chevron-down", "m6 9 6 6 6-6")
    val Undo = icon(
        "undo", "M9 14 4 9l5-5",
        "M4 9h10.5a5.5 5.5 0 0 1 5.5 5.5a5.5 5.5 0 0 1-5.5 5.5H11",
    )
    val Redo = icon(
        "redo", "m15 14 5-5-5-5",
        "M20 9H9.5A5.5 5.5 0 0 0 4 14.5A5.5 5.5 0 0 0 9.5 20H13",
    )
    val More = icon(
        "more",
        "M12 11a1 1 0 1 0 0 2a1 1 0 1 0 0-2z",
        "M19 11a1 1 0 1 0 0 2a1 1 0 1 0 0-2z",
        "M5 11a1 1 0 1 0 0 2a1 1 0 1 0 0-2z",
    )
    val Sun = icon(
        "sun", "M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8z",
        "M12 2v2", "M12 20v2", "m4.93 4.93 1.41 1.41", "m17.66 17.66 1.41 1.41",
        "M2 12h2", "M20 12h2", "m6.34 17.66-1.41 1.41", "m19.07 4.93-1.41 1.41",
    )
    val Moon = icon("moon", "M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z")
    val Plus = icon("plus", "M5 12h14", "M12 5v14")
    val Minus = icon("minus", "M5 12h14")
    val Check = icon("check", "M20 6 9 17l-5-5")
    val Close = icon("close", "M18 6 6 18", "m6 6 12 12")
    val Palette = icon(
        "palette",
        "M12 22a1 1 0 0 1 0-20 10 9 0 0 1 10 9 5 5 0 0 1-5 5h-2.25a1.75 1.75 0 0 0-1.4 2.8l.3.4a1.75 1.75 0 0 1-1.4 2.8z",
        "M13.5 6a.5.5 0 1 0 0 1a.5.5 0 1 0 0-1z",
        "M17.5 10a.5.5 0 1 0 0 1a.5.5 0 1 0 0-1z",
        "M6.5 12a.5.5 0 1 0 0 1a.5.5 0 1 0 0-1z",
        "M8.5 7a.5.5 0 1 0 0 1a.5.5 0 1 0 0-1z",
    )
    val Keyboard = icon(
        "keyboard", "M4 4h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z",
        "M6 8h.01", "M10 8h.01", "M14 8h.01", "M18 8h.01",
        "M8 12h.01", "M12 12h.01", "M16 12h.01", "M7 16h10",
    )
    val Layout = icon(
        "layout", "M4 3h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z",
        "M15 3h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1h-5a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z",
        "M15 14h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1h-5a1 1 0 0 1-1-1v-5a1 1 0 0 1 1-1z",
        "M4 14h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-5a1 1 0 0 1 1-1z",
    )
    val Sliders = icon(
        "sliders", "M21 4h-7", "M10 4H3", "M21 12h-9", "M8 12H3", "M21 20h-5", "M12 20H3",
        "M14 2v4", "M8 10v4", "M16 18v4",
    )
    val Sparkles = icon(
        "sparkles",
        "M9.937 15.5A2 2 0 0 0 8.5 14.063l-6.135-1.582a.5.5 0 0 1 0-.962L8.5 9.936A2 2 0 0 0 9.937 8.5l1.582-6.135a.5.5 0 0 1 .963 0L14.063 8.5A2 2 0 0 0 15.5 9.937l6.135 1.581a.5.5 0 0 1 0 .964L15.5 14.063a2 2 0 0 0-1.437 1.437l-1.582 6.135a.5.5 0 0 1-.963 0z",
        "M20 3v4", "M22 5h-4",
    )
    val Sound = icon(
        "sound",
        "M11 4.702a.705.705 0 0 0-1.203-.498L6.413 7.587A1.4 1.4 0 0 1 5.416 8H3a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h2.416a1.4 1.4 0 0 1 .997.413l3.383 3.384A.705.705 0 0 0 11 19.298z",
        "M16 9a5 5 0 0 1 0 6", "M19.364 18.364a9 9 0 0 0 0-12.728",
    )
    val Type = icon("type", "M4 7V4h16v3", "M9 20h6", "M12 4v16")
    val Swipe = icon(
        "swipe", "M3 17c3-6 6-9 9-4s6 2 9-6",
        "M18 7h3v3",
    )
    val Copy = icon(
        "copy", "M10 8h10a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H10a2 2 0 0 1-2-2V10a2 2 0 0 1 2-2z",
        "M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2",
    )
    val Import = icon(
        "import", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "m7 10 5 5 5-5", "M12 15V3",
    )
    val Share = icon(
        "share", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "m17 8-5-5-5 5", "M12 3v12",
    )
    val Trash = icon(
        "trash", "M3 6h18", "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6",
        "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2",
    )
    val Reset = icon("reset", "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8", "M3 3v5h5")
    val Pencil = icon(
        "pencil",
        "M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z",
    )
    val Image = icon(
        "image", "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
        "M9 7a2 2 0 1 0 0 4a2 2 0 1 0 0-4z", "m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21",
    )
    val Globe = icon(
        "globe", "M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20z",
        "M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20", "M2 12h20",
    )
}

@Composable
fun StudioIcon(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    description: String? = null,
) {
    Icon(icon, contentDescription = description, tint = tint, modifier = modifier.size(size))
}
