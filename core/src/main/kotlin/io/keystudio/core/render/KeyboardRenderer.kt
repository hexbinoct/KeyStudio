package io.keystudio.core.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import io.keystudio.core.layout.PlacedKey
import io.keystudio.core.model.ColorSpec
import io.keystudio.core.model.FontFamily
import io.keystudio.core.model.KeyCode
import io.keystudio.core.model.KeyDef
import io.keystudio.core.model.KeyIcon
import io.keystudio.core.model.ShapeStyle
import io.keystudio.core.model.Theme

/**
 * Per-key animation state owned by the view and read by the renderer.
 *
 * Kept as parallel primitive arrays rather than objects so that animating every
 * key costs no allocation and stays cache-friendly on a mid-range GPU.
 */
class KeyVisualState(capacity: Int = 64) {
    var scale = FloatArray(capacity) { 1f }
        private set
    var pressAmount = FloatArray(capacity)
        private set
    var tiltX = FloatArray(capacity)
        private set
    var tiltY = FloatArray(capacity)
        private set
    var offsetY = FloatArray(capacity)
        private set
    /** Lingering tint used by the Footprint effect. */
    var afterglow = FloatArray(capacity)
        private set

    fun ensure(size: Int) {
        if (size <= scale.size) return
        val n = Integer.highestOneBit(size - 1) shl 1
        scale = scale.copyOf(n).also { for (i in size until n) it[i] = 1f }
        pressAmount = pressAmount.copyOf(n)
        tiltX = tiltX.copyOf(n)
        tiltY = tiltY.copyOf(n)
        offsetY = offsetY.copyOf(n)
        afterglow = afterglow.copyOf(n)
    }

    fun reset(size: Int) {
        for (i in 0 until minOf(size, scale.size)) {
            scale[i] = 1f
            pressAmount[i] = 0f
            tiltX[i] = 0f
            tiltY[i] = 0f
            offsetY[i] = 0f
            afterglow[i] = 0f
        }
    }
}

/**
 * Draws a laid-out keyboard onto a [Canvas].
 *
 * The contract is: [prepare] whenever the theme or geometry changes, [draw]
 * every frame. Nothing in [draw] allocates.
 */
class KeyboardRenderer(private var density: Float) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val rect = RectF()
    private val shapePath = Path()
    private val iconPath = Path()
    private val shaderMatrix = Matrix()
    private val fontMetrics = Paint.FontMetrics()

    /** Unit-space shaders (0..1 x 0..1), positioned per key via [shaderMatrix]. */
    private var keyFillShader: Shader? = null
    private var modifierFillShader: Shader? = null
    private var accentFillShader: Shader? = null
    private var pressedFillShader: Shader? = null
    private var backgroundShader: Shader? = null

    private var keyFillColor = Color.LTGRAY
    private var modifierFillColor = Color.GRAY
    private var accentFillColor = Color.BLUE
    private var pressedFillColor = Color.WHITE
    private var backgroundColor = Color.DKGRAY

    private var theme: Theme? = null
    private var cornerRadius = 0f
    private var insetPx = 0f
    private var elevationPx = 0f
    private var strokeWidthPx = 0f
    private var customFontPath: String? = null
    private var customFont: Typeface? = null

    private var viewWidth = 0f
    private var viewHeight = 0f

    fun setDensity(value: Float) {
        density = value
    }

    /** The label typeface in use, so the suggestion strip can match the keys. */
    val typeface: Typeface get() = textPaint.typeface ?: Typeface.DEFAULT

    /** Rebuilds paints and shaders. Call on theme change or size change. */
    fun prepare(theme: Theme, widthPx: Float, heightPx: Float) {
        this.theme = theme
        viewWidth = widthPx
        viewHeight = heightPx

        cornerRadius = theme.shape.cornerRadiusDp * density
        insetPx = theme.shape.insetDp * density
        elevationPx = theme.elevationDp * density
        strokeWidthPx = (theme.stroke?.widthDp ?: 0f) * density

        keyFillShader = unitShader(theme.keyFill)
        modifierFillShader = unitShader(theme.modifierFill)
        accentFillShader = unitShader(theme.accentFill)
        pressedFillShader = unitShader(theme.pressedFill)

        keyFillColor = flatColor(theme.keyFill)
        modifierFillColor = flatColor(theme.modifierFill)
        accentFillColor = flatColor(theme.accentFill)
        pressedFillColor = flatColor(theme.pressedFill)

        theme.stroke?.let {
            strokePaint.color = it.color
            strokePaint.strokeWidth = strokeWidthPx
        }

        val typeface = typefaceFor(theme)
        textPaint.typeface = typeface
        hintPaint.typeface = typeface
        textPaint.color = theme.textColor
        hintPaint.color = theme.hintColor
        iconPaint.color = theme.textColor

        if (elevationPx > 0f) {
            fillPaint.setShadowLayer(elevationPx, 0f, elevationPx * 0.45f, shadowColor(theme))
        } else {
            fillPaint.clearShadowLayer()
        }
    }

    private fun shadowColor(theme: Theme): Int =
        if (theme.isDark) Color.argb(120, 0, 0, 0) else Color.argb(60, 0, 0, 0)

    private fun typefaceFor(theme: Theme): Typeface {
        val base = theme.font.customFontPath?.let { loadCustomFont(it) } ?: when (theme.font.family) {
            FontFamily.System -> Typeface.SANS_SERIF
            FontFamily.SystemCondensed -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
            FontFamily.Monospace -> Typeface.MONOSPACE
            FontFamily.Serif -> Typeface.SERIF
            FontFamily.RoundedSans -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        return if (theme.font.bold) Typeface.create(base, Typeface.BOLD) else base
    }

    /**
     * Cached by path: styling is re-applied on every change while the theme
     * editor is open, and reading a font file off disk sixty times a second to
     * get the same object back would be absurd. A bad path falls through to the
     * chosen family rather than failing the draw.
     */
    private fun loadCustomFont(path: String): Typeface? {
        if (path == customFontPath) return customFont
        customFontPath = path
        customFont = runCatching { Typeface.createFromFile(path) }.getOrNull()
        return customFont
    }

    /**
     * Builds a shader in unit space. A per-key [Matrix] maps it onto the key,
     * which means one shader object serves every key regardless of size.
     */
    private fun unitShader(spec: ColorSpec): Shader? = when (spec) {
        is ColorSpec.Solid -> null
        is ColorSpec.LinearGradient -> {
            val rad = Math.toRadians(spec.angleDeg.toDouble())
            val dx = Math.cos(rad).toFloat()
            val dy = Math.sin(rad).toFloat()
            LinearGradient(
                0.5f - dx * 0.5f, 0.5f - dy * 0.5f,
                0.5f + dx * 0.5f, 0.5f + dy * 0.5f,
                spec.colors.toIntArray(),
                spec.stops?.toFloatArray(),
                Shader.TileMode.CLAMP,
            )
        }
        is ColorSpec.RadialGradient -> RadialGradient(
            spec.centerX, spec.centerY, spec.radiusScale.coerceAtLeast(0.01f),
            spec.colors.toIntArray(), null, Shader.TileMode.CLAMP,
        )
    }

    private fun flatColor(spec: ColorSpec): Int = when (spec) {
        is ColorSpec.Solid -> spec.color
        is ColorSpec.LinearGradient -> spec.colors.firstOrNull() ?: Color.GRAY
        is ColorSpec.RadialGradient -> spec.colors.firstOrNull() ?: Color.GRAY
    }

    /** Applies a unit-space shader to [rect], or falls back to a flat colour. */
    private fun applyFill(shader: Shader?, flat: Int) {
        if (shader != null) {
            shaderMatrix.reset()
            shaderMatrix.setScale(rect.width(), rect.height())
            shaderMatrix.postTranslate(rect.left, rect.top)
            shader.setLocalMatrix(shaderMatrix)
            fillPaint.shader = shader
            fillPaint.color = Color.WHITE
        } else {
            fillPaint.shader = null
            fillPaint.color = flat
        }
    }

    fun drawBackground(canvas: Canvas, color: Int) {
        backgroundPaint.shader = backgroundShader
        backgroundPaint.color = color
        canvas.drawRect(0f, 0f, viewWidth, viewHeight, backgroundPaint)
    }

    fun setBackgroundShader(shader: Shader?, fallback: Int) {
        backgroundShader = shader
        backgroundColor = fallback
    }

    /**
     * Draws every key.
     *
     * @param shifted whether shift labels should be shown.
     * @param state per-key animation values, indexed in the same order as [keys].
     */
    fun draw(
        canvas: Canvas,
        keys: List<PlacedKey>,
        state: KeyVisualState,
        shifted: Boolean,
        accentColor: Int,
    ) {
        val theme = this.theme ?: return

        for (i in keys.indices) {
            val placed = keys[i]
            val key = placed.key
            if (key.code == KeyCode.Spacer) continue

            val scale = state.scale[i]
            val offsetY = state.offsetY[i]

            rect.set(
                placed.left + insetPx,
                placed.top + insetPx + offsetY,
                placed.right - insetPx,
                placed.bottom - insetPx + offsetY,
            )
            if (scale != 1f) {
                val cx = rect.centerX()
                val cy = rect.centerY()
                val hw = rect.width() * 0.5f * scale
                val hh = rect.height() * 0.5f * scale
                rect.set(cx - hw, cy - hh, cx + hw, cy + hh)
            }
            if (rect.width() <= 0f || rect.height() <= 0f) continue

            drawKeyBody(canvas, placed, key, state, i, theme, accentColor)
            drawKeyContent(canvas, placed, key, theme, shifted)
        }
    }

    private fun drawKeyBody(
        canvas: Canvas,
        placed: PlacedKey,
        key: KeyDef,
        state: KeyVisualState,
        index: Int,
        theme: Theme,
        accentColor: Int,
    ) {
        val override = key.style
        when {
            override?.fill != null -> applyFill(unitShaderCached(override.fill), flatColor(override.fill))
            key.modifier -> applyFill(modifierFillShader, modifierFillColor)
            key.code == KeyCode.Enter -> applyFill(accentFillShader, accentFillColor)
            else -> applyFill(keyFillShader, keyFillColor)
        }

        val radius = (override?.cornerRadiusDp?.times(density)) ?: cornerRadius
        buildShape(theme.shape.style, radius)
        canvas.drawPath(shapePath, fillPaint)

        // Pressed tint and lingering afterglow both composite over the base fill.
        val press = state.pressAmount[index]
        val glow = state.afterglow[index]
        val tint = maxOf(press, glow)
        if (tint > 0.001f) {
            fillPaint.shader = null
            val base = if (press > glow) pressedFillColor else accentColor
            fillPaint.color = Color.argb(
                (Color.alpha(base) * tint).toInt().coerceIn(0, 255),
                Color.red(base), Color.green(base), Color.blue(base),
            )
            fillPaint.clearShadowLayer()
            canvas.drawPath(shapePath, fillPaint)
            if (elevationPx > 0f) {
                fillPaint.setShadowLayer(elevationPx, 0f, elevationPx * 0.45f, shadowColor(theme))
            }
        }

        if (strokeWidthPx > 0f) {
            canvas.drawPath(shapePath, strokePaint)
        }
    }

    /** Overrides are rare, so building their shader lazily here is acceptable. */
    private fun unitShaderCached(spec: ColorSpec): Shader? =
        if (spec is ColorSpec.Solid) null else unitShader(spec)

    private fun buildShape(style: ShapeStyle, radius: Float) {
        shapePath.rewind()
        val w = rect.width()
        val h = rect.height()
        when (style) {
            ShapeStyle.Flat -> shapePath.addRect(rect, Path.Direction.CW)
            ShapeStyle.Circle -> {
                val r = minOf(w, h) * 0.5f
                shapePath.addCircle(rect.centerX(), rect.centerY(), r, Path.Direction.CW)
            }
            ShapeStyle.Pill -> {
                val r = minOf(w, h) * 0.5f
                shapePath.addRoundRect(rect, r, r, Path.Direction.CW)
            }
            ShapeStyle.Cut -> {
                val c = radius.coerceAtMost(minOf(w, h) * 0.5f)
                shapePath.moveTo(rect.left + c, rect.top)
                shapePath.lineTo(rect.right - c, rect.top)
                shapePath.lineTo(rect.right, rect.top + c)
                shapePath.lineTo(rect.right, rect.bottom - c)
                shapePath.lineTo(rect.right - c, rect.bottom)
                shapePath.lineTo(rect.left + c, rect.bottom)
                shapePath.lineTo(rect.left, rect.bottom - c)
                shapePath.lineTo(rect.left, rect.top + c)
                shapePath.close()
            }
            ShapeStyle.Squircle -> buildSquircle()
            ShapeStyle.RoundedRect -> {
                val r = radius.coerceAtMost(minOf(w, h) * 0.5f)
                shapePath.addRoundRect(rect, r, r, Path.Direction.CW)
            }
        }
    }

    /**
     * Superellipse approximation using cubic corners. The 0.55 control ratio is
     * the usual circle approximation; 0.28 pulls the corners into the flatter,
     * continuous curvature that reads as a squircle.
     */
    private fun buildSquircle() {
        val w = rect.width()
        val h = rect.height()
        val r = minOf(w, h) * 0.42f
        val c = r * 0.72f
        val l = rect.left
        val t = rect.top
        val rt = rect.right
        val b = rect.bottom

        shapePath.moveTo(l + r, t)
        shapePath.lineTo(rt - r, t)
        shapePath.cubicTo(rt - r + c, t, rt, t + r - c, rt, t + r)
        shapePath.lineTo(rt, b - r)
        shapePath.cubicTo(rt, b - r + c, rt - r + c, b, rt - r, b)
        shapePath.lineTo(l + r, b)
        shapePath.cubicTo(l + r - c, b, l, b - r + c, l, b - r)
        shapePath.lineTo(l, t + r)
        shapePath.cubicTo(l, t + r - c, l + r - c, t, l + r, t)
        shapePath.close()
    }

    private fun drawKeyContent(
        canvas: Canvas,
        placed: PlacedKey,
        key: KeyDef,
        theme: Theme,
        shifted: Boolean,
    ) {
        val color = key.style?.textColor
            ?: when {
                key.code == KeyCode.Enter -> theme.accentTextColor
                key.modifier -> theme.modifierTextColor
                else -> theme.textColor
            }

        if (key.icon != KeyIcon.None) {
            iconPaint.color = color
            iconPaint.strokeWidth = (minOf(rect.width(), rect.height()) * 0.07f)
                .coerceAtLeast(density * 1.5f)
            drawIcon(canvas, key.icon, shifted)
            return
        }

        val label = if (shifted && key.shiftLabel != null) key.shiftLabel else key.label
        if (label.isEmpty()) return

        textPaint.color = color
        textPaint.textSize = textSizeFor(key, rect.height()) * (theme.font.sizeScale)
        if (label.length > 1) {
            // Custom keys can carry a word or a kaomoji; shrink it into the key
            // rather than letting it spill over the neighbours.
            val room = rect.width() * 0.84f
            val measured = textPaint.measureText(label)
            if (measured > room) textPaint.textSize *= room / measured
        }
        textPaint.getFontMetrics(fontMetrics)
        // Optical centring: use the font's ascent/descent rather than the full
        // line height, so a capital letter sits visually centred in the key.
        val baseline = rect.centerY() - (fontMetrics.ascent + fontMetrics.descent) * 0.5f
        canvas.drawText(label, rect.centerX(), baseline, textPaint)

        val hint = key.hint
        if (!hint.isNullOrEmpty()) {
            hintPaint.color = theme.hintColor
            hintPaint.textSize = textPaint.textSize * 0.46f
            hintPaint.getFontMetrics(fontMetrics)
            canvas.drawText(
                hint,
                rect.right - rect.width() * 0.18f,
                rect.top + rect.height() * 0.06f - fontMetrics.ascent,
                hintPaint,
            )
        } else if (key.longPress.isNotEmpty()) {
            // A small dot signals "there is more under this key".
            hintPaint.color = theme.hintColor
            canvas.drawCircle(
                rect.right - rect.width() * 0.13f,
                rect.top + rect.height() * 0.15f,
                density * 1.4f,
                hintPaint,
            )
        }
    }

    private fun textSizeFor(key: KeyDef, keyHeight: Float): Float {
        val base = keyHeight * 0.40f
        return when (key.code) {
            KeyCode.LayerLetters, KeyCode.LayerSymbols, KeyCode.LayerSymbolsAlt -> base * 0.62f
            else -> if (key.label.length > 2) base * 0.62f else base
        }
    }

    private fun drawIcon(canvas: Canvas, icon: KeyIcon, shifted: Boolean) {
        iconPath.rewind()
        val cx = rect.centerX()
        val cy = rect.centerY()
        val s = minOf(rect.width(), rect.height()) * 0.24f

        when (icon) {
            KeyIcon.Shift -> {
                iconPaint.style = if (shifted) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
                iconPath.moveTo(cx, cy - s)
                iconPath.lineTo(cx + s, cy)
                iconPath.lineTo(cx + s * 0.5f, cy)
                iconPath.lineTo(cx + s * 0.5f, cy + s * 0.7f)
                iconPath.lineTo(cx - s * 0.5f, cy + s * 0.7f)
                iconPath.lineTo(cx - s * 0.5f, cy)
                iconPath.lineTo(cx - s, cy)
                iconPath.close()
                canvas.drawPath(iconPath, iconPaint)
                iconPaint.style = Paint.Style.STROKE
            }
            KeyIcon.Backspace -> {
                iconPath.moveTo(cx - s * 1.1f, cy)
                iconPath.lineTo(cx - s * 0.35f, cy - s * 0.75f)
                iconPath.lineTo(cx + s * 1.1f, cy - s * 0.75f)
                iconPath.lineTo(cx + s * 1.1f, cy + s * 0.75f)
                iconPath.lineTo(cx - s * 0.35f, cy + s * 0.75f)
                iconPath.close()
                canvas.drawPath(iconPath, iconPaint)
                iconPath.rewind()
                iconPath.moveTo(cx + s * 0.1f, cy - s * 0.33f)
                iconPath.lineTo(cx + s * 0.75f, cy + s * 0.33f)
                iconPath.moveTo(cx + s * 0.75f, cy - s * 0.33f)
                iconPath.lineTo(cx + s * 0.1f, cy + s * 0.33f)
                canvas.drawPath(iconPath, iconPaint)
            }
            KeyIcon.Enter -> {
                iconPath.moveTo(cx + s, cy - s * 0.8f)
                iconPath.lineTo(cx + s, cy + s * 0.35f)
                iconPath.lineTo(cx - s, cy + s * 0.35f)
                iconPath.moveTo(cx - s * 0.35f, cy - s * 0.3f)
                iconPath.lineTo(cx - s, cy + s * 0.35f)
                iconPath.lineTo(cx - s * 0.35f, cy + s)
                canvas.drawPath(iconPath, iconPaint)
            }
            KeyIcon.Space -> {
                iconPath.moveTo(cx - s * 1.6f, cy)
                iconPath.lineTo(cx + s * 1.6f, cy)
                canvas.drawPath(iconPath, iconPaint)
            }
            KeyIcon.Globe -> {
                canvas.drawCircle(cx, cy, s, iconPaint)
                iconPath.moveTo(cx - s, cy)
                iconPath.lineTo(cx + s, cy)
                canvas.drawPath(iconPath, iconPaint)
                rect.let {
                    val oval = RectF(cx - s * 0.5f, cy - s, cx + s * 0.5f, cy + s)
                    canvas.drawOval(oval, iconPaint)
                }
            }
            KeyIcon.Emoji -> {
                canvas.drawCircle(cx, cy, s, iconPaint)
                canvas.drawCircle(cx - s * 0.38f, cy - s * 0.28f, density * 1.2f, iconPaint)
                canvas.drawCircle(cx + s * 0.38f, cy - s * 0.28f, density * 1.2f, iconPaint)
                iconPath.moveTo(cx - s * 0.45f, cy + s * 0.25f)
                iconPath.quadTo(cx, cy + s * 0.75f, cx + s * 0.45f, cy + s * 0.25f)
                canvas.drawPath(iconPath, iconPaint)
            }
            KeyIcon.ArrowLeft, KeyIcon.ArrowRight, KeyIcon.ArrowUp, KeyIcon.ArrowDown -> {
                val (dx, dy) = when (icon) {
                    KeyIcon.ArrowLeft -> -1f to 0f
                    KeyIcon.ArrowRight -> 1f to 0f
                    KeyIcon.ArrowUp -> 0f to -1f
                    else -> 0f to 1f
                }
                iconPath.moveTo(cx + dx * s, cy + dy * s)
                iconPath.lineTo(cx + dy * s * 0.7f - dx * s * 0.2f, cy + dx * s * 0.7f + dy * s * -0.2f)
                iconPath.moveTo(cx + dx * s, cy + dy * s)
                iconPath.lineTo(cx - dy * s * 0.7f - dx * s * 0.2f, cy - dx * s * 0.7f + dy * s * -0.2f)
                iconPath.moveTo(cx + dx * s, cy + dy * s)
                iconPath.lineTo(cx - dx * s, cy - dy * s)
                canvas.drawPath(iconPath, iconPaint)
            }
            else -> Unit
        }
    }
}
