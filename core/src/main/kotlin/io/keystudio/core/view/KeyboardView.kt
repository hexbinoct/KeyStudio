package io.keystudio.core.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import io.keystudio.core.layout.LayoutEngine
import io.keystudio.core.layout.PlacedKey
import io.keystudio.core.model.ColorSpec
import io.keystudio.core.model.KeyCode
import io.keystudio.core.model.KeyDef
import io.keystudio.core.model.KeyRow
import io.keystudio.core.model.KeyboardLayout
import io.keystudio.core.model.KeyboardProfile
import io.keystudio.core.model.LayerId
import io.keystudio.core.model.ParticleEffect
import io.keystudio.core.model.Theme
import io.keystudio.core.presets.Layouts
import io.keystudio.core.render.BackgroundRenderer
import io.keystudio.core.render.EffectsEngine
import io.keystudio.core.render.KeyVisualState
import io.keystudio.core.render.KeyboardRenderer
import io.keystudio.core.typing.GestureTrace
import io.keystudio.core.typing.KeyAnchor

/** What the view reports upward. The view never edits text itself. */
sealed interface KeyAction {
    data class Text(val text: String, val key: KeyDef) : KeyAction
    data class Special(val code: KeyCode, val key: KeyDef) : KeyAction
    /** A finished swipe, to be decoded into a word. */
    class Gesture(val trace: GestureTrace) : KeyAction
    /** Space-bar trackpad. Negative is left. */
    data class MoveCursor(val steps: Int) : KeyAction
    /** Backspace swipe: select [words] back, deleting them when [done]. */
    data class DeleteWords(val words: Int, val done: Boolean) : KeyAction
    /** A tap on the suggestion strip. */
    data class PickSuggestion(val index: Int, val text: String) : KeyAction
}

/**
 * A key addressed the way the layout editor thinks of it: which row of which
 * layer, and which key in that row. [row] is -1 for the layout's pinned top row.
 */
data class KeyRef(val layer: LayerId, val row: Int, val key: Int)

fun interface OnKeyActionListener {
    fun onKeyAction(action: KeyAction)
}

enum class ShiftState { Off, Shifted, Locked }

/**
 * The keyboard surface: geometry, painting, effects and touch.
 *
 * It is deliberately free of any text-editing logic so the Studio can embed the
 * exact same view for live preview. Whatever you see here is what the real
 * keyboard draws, because it *is* the real keyboard.
 */
class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    private companion object {
        /** Extra reach, in dp, for touches landing in the gap between keys. */
        const val HIT_SLOP_DP = 12f
        const val MAX_SUGGESTIONS = 3
        /** How long a stretch of swipe trail stays visible, in ms. */
        const val TRAIL_FADE_MS = 260L
        const val TRAIL_CAPACITY = 512

        val STRIP_SETTINGS = KeyDef(id = "strip_settings", code = KeyCode.Settings)
        val STRIP_HIDE = KeyDef(id = "strip_hide", code = KeyCode.Hide)
    }

    private val density get() = resources.displayMetrics.density

    private val layoutEngine = LayoutEngine()
    private val renderer = KeyboardRenderer(resources.displayMetrics.density)
    private val effects = EffectsEngine(resources.displayMetrics.density)
    private val background = BackgroundRenderer(resources.displayMetrics.density)
    private val visualState = KeyVisualState()

    private val handler = Handler(Looper.getMainLooper())
    private val popupPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val popupTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val popupRect = RectF()
    private val fontMetrics = Paint.FontMetrics()

    var onKeyAction: OnKeyActionListener? = null

    /** Set true in the Studio so preview taps never emit text. */
    var previewMode = false

    var profile: KeyboardProfile = io.keystudio.core.presets.Themes.default
        set(value) {
            val heightChanged = field.metrics.totalHeightDp != value.metrics.totalHeightDp ||
                field.metrics.showSuggestionStrip != value.metrics.showSuggestionStrip
            field = value
            effects.config = value.effects
            if (heightChanged) requestLayout()
            rebuild()
        }

    var layout: KeyboardLayout = Layouts.qwerty
        set(value) {
            field = value
            rebuild()
        }

    var layer: LayerId = LayerId.Letters
        set(value) {
            if (field == value) return
            field = value
            rebuild()
        }

    var shiftState: ShiftState = ShiftState.Off
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /** Forces the dark or light variant; null follows the system. */
    var forceDark: Boolean? = null
        set(value) {
            field = value
            rebuild()
        }

    /** Swipe typing. Off makes every slide behave like a cancelled tap. */
    var gesturesEnabled: Boolean
        get() = router.gesturesEnabled
        set(value) {
            router.gesturesEnabled = value
        }

    /** Sliding along the space bar moves the caret. */
    var spaceCursorEnabled: Boolean
        get() = router.spaceCursorEnabled
        set(value) {
            router.spaceCursorEnabled = value
        }

    /** Slide left from backspace to select words. */
    var backspaceSwipeEnabled: Boolean
        get() = router.backspaceSwipeEnabled
        set(value) {
            router.backspaceSwipeEnabled = value
        }

    /** Draws the swipe path under the finger. */
    var showGestureTrail = true

    /**
     * Layout-editor mode: taps select keys instead of pressing them, and the
     * selected key is outlined. Nothing is typed and no effects play.
     */
    var editMode = false
        set(value) {
            field = value
            router.cancelAll()
            invalidate()
        }

    var selectedKey: KeyRef? = null
        set(value) {
            field = value
            invalidate()
        }

    /** Called with the key tapped in [editMode], or null for a tap on no key. */
    var onKeyPicked: ((KeyRef?) -> Unit)? = null

    /**
     * For each row on screen, where it came from: a row of the layer (>= 0),
     * the pinned top row (-1), or the fixed number row (-2), which the layout
     * editor cannot change.
     */
    private var rowSources = IntArray(0)
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectionRect = RectF()
    private var editDownX = 0f
    private var editDownY = 0f

    /**
     * Called after the keys are laid out afresh, so the typing engine can
     * learn where the letters now are.
     */
    var onGeometryChanged: (() -> Unit)? = null

    /** Where every letter key is right now, for the correction model. */
    fun letterAnchors(): List<KeyAnchor> = layoutEngine.keys
        .filter { touchKeys.startsGesture(it.index) }
        .map { KeyAnchor(it.key.label[0].lowercaseChar(), it.centerX, it.centerY, it.width, it.height) }

    // -- touch ----------------------------------------------------------------

    private val touchKeys = object : TouchKeys {
        override fun hitTest(x: Float, y: Float): Int =
            layoutEngine.hitTest(x, y, HIT_SLOP_DP * density)?.index ?: -1

        override fun contains(index: Int, x: Float, y: Float): Boolean =
            keyAt(index)?.contains(x, y) ?: false

        override fun isShift(index: Int) = keyAt(index)?.key?.code == KeyCode.Shift
        override fun isSpace(index: Int) = keyAt(index)?.key?.code == KeyCode.Space
        override fun isBackspace(index: Int) = keyAt(index)?.key?.code == KeyCode.Backspace
        override fun isRepeatable(index: Int) = keyAt(index)?.key?.repeatable == true
        override fun hasLongPress(index: Int) = keyAt(index)?.key?.longPress?.isNotEmpty() == true

        override fun startsGesture(index: Int): Boolean {
            val key = keyAt(index)?.key ?: return false
            return key.code == KeyCode.Text && layer == LayerId.Letters &&
                key.label.length == 1 && key.label[0].isLetter()
        }
    }

    private val scheduler = object : TouchScheduler {
        override fun post(delayMs: Long, action: () -> Unit): Any {
            val runnable = Runnable(action)
            handler.postDelayed(runnable, delayMs)
            return runnable
        }

        override fun cancel(token: Any) = handler.removeCallbacks(token as Runnable)
    }

    private val touchListener = object : TouchListener {
        override fun keyDown(index: Int, x: Float, y: Float) {
            val placed = keyAt(index) ?: return
            effects.onKeyDown(index, placed, x, y)
            scheduleFrame()
            if (!previewMode) feedback.onKeyDown(profile.sound, placed.key.code)
        }

        override fun keyUp(index: Int) {
            if (index >= 0) effects.onKeyUp(index)
            scheduleFrame()
        }

        override fun commit(index: Int, shifted: Boolean) {
            val placed = keyAt(index) ?: return
            emit(placed.key, shifted)
        }

        override fun repeat(index: Int) {
            val placed = keyAt(index) ?: return
            emit(placed.key, shifted = false)
        }

        override fun openPopup(index: Int, x: Float) {
            val placed = keyAt(index) ?: return
            popupKey = placed
            updatePopupSelection(x)
            invalidate()
        }

        override fun movePopup(x: Float) {
            updatePopupSelection(x)
            invalidate()
        }

        override fun commitPopup() {
            val popup = popupKey ?: return
            val option = popup.key.longPress.getOrNull(popupSelection)
            popupKey = null
            invalidate()
            if (option == null) return
            val shifted = shiftState != ShiftState.Off || shiftHeld
            val text = if (shifted && popup.key.shiftLabel != null) option.uppercase() else option
            send(KeyAction.Text(text, popup.key))
        }

        override fun dismissPopup() {
            popupKey = null
            invalidate()
        }

        override fun shiftHeld(held: Boolean) {
            shiftHeld = held
            invalidate()
        }

        override fun gestureStart(x: Float, y: Float, timeMs: Long) {
            gestureCount = 0
            trailCount = 0
            trailStart = 0
            addGesturePoint(x, y, timeMs)
        }

        override fun gestureMove(x: Float, y: Float, timeMs: Long) {
            addGesturePoint(x, y, timeMs)
        }

        override fun gestureEnd() {
            if (gestureCount >= 2) send(KeyAction.Gesture(snapshotGesture()))
            gestureCount = 0
            scheduleFrame()
        }

        override fun gestureCancel() {
            gestureCount = 0
            trailCount = 0
            trailStart = 0
            invalidate()
        }

        override fun moveCursor(steps: Int) = send(KeyAction.MoveCursor(steps))

        override fun deleteSwipe(steps: Int, done: Boolean) =
            send(KeyAction.DeleteWords(steps, done))
    }

    private val router = TouchRouter(touchKeys, scheduler, touchListener)

    private var shiftHeld = false
    private var popupKey: PlacedKey? = null
    private var popupSelection = 0

    // -- swipe path -----------------------------------------------------------

    private var gestureXs = FloatArray(256)
    private var gestureYs = FloatArray(256)
    private var gestureTs = LongArray(256)
    private var gestureCount = 0

    private val trailX = FloatArray(TRAIL_CAPACITY)
    private val trailY = FloatArray(TRAIL_CAPACITY)
    private val trailT = LongArray(TRAIL_CAPACITY)
    private var trailStart = 0
    private var trailCount = 0
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private var lastParticleX = 0f
    private var lastParticleY = 0f

    // -- suggestion strip -----------------------------------------------------

    private var suggestions: List<String> = emptyList()
    private val stripPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val stripIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val stripPath = Path()
    private val stripRect = RectF()
    private var stripPointerId = -1
    private var stripPressedSlot = -1

    private var frameScheduled = false
    private var lastFrameNanos = 0L

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val feedback = KeyFeedback(this)

    private val frameCallback = Choreographer.FrameCallback { nanos ->
        frameScheduled = false
        val dt = if (lastFrameNanos == 0L) 0.016f
        else ((nanos - lastFrameNanos) / 1_000_000_000.0f)
        lastFrameNanos = nanos

        background.update(dt)
        val busy = effects.update(dt, visualState, layoutEngine.keys)
        val trailBusy = trimTrail(SystemClock.uptimeMillis())
        invalidate()
        if (busy || trailBusy || background.isAnimated) scheduleFrame() else lastFrameNanos = 0L
    }

    init {
        isFocusable = false
        isClickable = true
        effects.config = profile.effects
        applyTouchMetrics()
    }

    private fun applyTouchMetrics() {
        val d = density
        router.touchSlopPx = touchSlop.toFloat()
        router.gestureThresholdPx = 14f * d
        router.cursorStepPx = 11f * d
        router.deleteStepPx = 36f * d
    }

    // -------------------------------------------------------------------------

    private fun keyAt(index: Int): PlacedKey? = layoutEngine.keys.getOrNull(index)

    private fun currentTheme(): Theme {
        val dark = forceDark ?: isSystemDark()
        return profile.themeFor(dark)
    }

    private fun isSystemDark(): Boolean {
        val mode = resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private val stripHeightPx: Float
        get() = if (profile.metrics.showSuggestionStrip) {
            profile.metrics.suggestionStripHeightDp * density
        } else 0f

    /** Rows for the active layer, with the optional number/top rows prepended. */
    private fun activeRows(): List<KeyRow> {
        val base = layout.layer(layer)
        val metrics = profile.metrics
        val sources = ArrayList<Int>(base.size + 2)
        val rows = ArrayList<KeyRow>(base.size + 2)
        if (layer == LayerId.Letters) {
            if (metrics.showTopRow) layout.topRow?.let { rows.add(it); sources.add(-1) }
            if (metrics.showNumberRow) { rows.add(Layouts.numberRow); sources.add(-2) }
        }
        base.forEachIndexed { i, row -> rows.add(row); sources.add(i) }
        rowSources = sources.toIntArray()
        return rows
    }

    private fun refOf(placed: PlacedKey): KeyRef? {
        val source = rowSources.getOrElse(placed.rowIndex) { -2 }
        if (source == -2) return null
        return KeyRef(layer, source, placed.indexInRow)
    }

    private fun rebuild() {
        if (width == 0 || height == 0) {
            invalidate()
            return
        }
        val density = density
        renderer.setDensity(density)
        effects.setDensity(density)
        background.setDensity(density)
        applyTouchMetrics()

        layoutEngine.compute(
            activeRows(), width.toFloat(), height.toFloat(), profile.metrics, density,
            originY = stripHeightPx,
        )
        visualState.ensure(layoutEngine.keys.size)
        visualState.reset(layoutEngine.keys.size)
        effects.ensureCapacity(layoutEngine.keys.size)

        val theme = currentTheme()
        renderer.prepare(theme, width.toFloat(), height.toFloat())
        background.prepare(theme.background, width.toFloat(), height.toFloat())

        if (background.isAnimated) scheduleFrame()
        invalidate()
        if (layer == LayerId.Letters) onGeometryChanged?.invoke()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        // The strip sits on top of the key area rather than eating into it, so
        // turning it on never shrinks the keys the user sized.
        val height = (profile.metrics.totalHeightDp * density).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuild()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        router.cancelAll()
        handler.removeCallbacksAndMessages(null)
        if (frameScheduled) {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
            frameScheduled = false
        }
        background.release()
    }

    private fun scheduleFrame() {
        if (frameScheduled) return
        frameScheduled = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    // -------------------------------------------------------------------------
    // Suggestions
    // -------------------------------------------------------------------------

    /**
     * Words for the strip, best first. The best is drawn in the middle and in
     * bold, the way every major keyboard does, so the eye lands on it.
     */
    fun setSuggestions(words: List<String>) {
        val next = words.take(MAX_SUGGESTIONS)
        if (next == suggestions) return
        suggestions = next
        invalidate()
    }

    /** Which suggestion sits in each of the three slots, left to right. */
    private fun suggestionForSlot(slot: Int): Int = when (suggestions.size) {
        0 -> -1
        1 -> if (slot == 1) 0 else -1
        2 -> if (slot == 1) 0 else if (slot == 0) 1 else -1
        else -> when (slot) {
            0 -> 1
            1 -> 0
            else -> 2
        }
    }

    // -------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        val keys = layoutEngine.keys
        if (keys.isEmpty()) {
            rebuild()
            return
        }
        val theme = currentTheme()

        background.draw(canvas)
        if (stripHeightPx > 0f) drawStrip(canvas, theme)
        renderer.draw(
            canvas, keys, visualState,
            shifted = shiftState != ShiftState.Off || shiftHeld,
            accentColor = profile.effects.accentColor,
        )
        effects.draw(canvas)
        if (editMode) drawSelection(canvas)
        if (showGestureTrail) drawTrail(canvas)
        popupKey?.let { drawLongPressPopup(canvas, it, theme) }
    }

    private fun drawStrip(canvas: Canvas, theme: Theme) {
        val h = stripHeightPx
        val w = width.toFloat()
        val d = density
        val text = theme.textColor

        if (suggestions.isEmpty()) {
            drawStripToolbar(canvas, theme, h, w)
            return
        }

        val slotW = w / MAX_SUGGESTIONS
        stripPaint.typeface = renderer.typeface
        for (slot in 0 until MAX_SUGGESTIONS) {
            val index = suggestionForSlot(slot)
            val left = slotW * slot
            if (slot == stripPressedSlot && index >= 0) {
                popupPaint.color = withAlpha(text, 0.10f)
                stripRect.set(left + 4f * d, 5f * d, left + slotW - 4f * d, h - 5f * d)
                canvas.drawRoundRect(stripRect, 10f * d, 10f * d, popupPaint)
            }
            if (slot > 0) {
                stripIconPaint.color = withAlpha(text, 0.16f)
                stripIconPaint.strokeWidth = 1f * d
                canvas.drawLine(left, h * 0.32f, left, h * 0.68f, stripIconPaint)
            }
            if (index < 0) continue
            val word = suggestions[index]
            val primary = index == 0
            stripPaint.isFakeBoldText = primary
            stripPaint.color = if (primary) text else withAlpha(text, 0.78f)
            var size = minOf(h * 0.40f, 17f * d)
            stripPaint.textSize = size
            val maxW = slotW - 16f * d
            val measured = stripPaint.measureText(word)
            if (measured > maxW) {
                size *= maxW / measured
                stripPaint.textSize = size.coerceAtLeast(9f * d)
            }
            stripPaint.getFontMetrics(fontMetrics)
            val baseline = h / 2f - (fontMetrics.ascent + fontMetrics.descent) / 2f
            canvas.drawText(word, left + slotW / 2f, baseline, stripPaint)
        }
        stripPaint.isFakeBoldText = false
    }

    /** With nothing to suggest, the strip offers the keyboard's own shortcuts. */
    private fun drawStripToolbar(canvas: Canvas, theme: Theme, h: Float, w: Float) {
        val d = density
        val color = withAlpha(theme.textColor, 0.72f)
        stripIconPaint.color = color
        stripIconPaint.strokeWidth = 1.7f * d
        val cy = h / 2f
        val r = 9f * d

        // Pressed feedback for either end.
        if (stripPressedSlot == 0 || stripPressedSlot == 2) {
            val cx = if (stripPressedSlot == 0) h * 0.75f else w - h * 0.75f
            popupPaint.color = withAlpha(theme.textColor, 0.10f)
            canvas.drawCircle(cx, cy, h * 0.42f, popupPaint)
        }

        // Left: a slider glyph -- "tune this keyboard", i.e. open the Studio.
        val lx = h * 0.75f
        for (i in 0..2) {
            val y = cy - r * 0.6f + i * r * 0.6f
            canvas.drawLine(lx - r, y, lx + r, y, stripIconPaint)
        }
        popupPaint.color = color
        canvas.drawCircle(lx - r * 0.35f, cy - r * 0.6f, 2.6f * d, popupPaint)
        canvas.drawCircle(lx + r * 0.45f, cy, 2.6f * d, popupPaint)
        canvas.drawCircle(lx - r * 0.1f, cy + r * 0.6f, 2.6f * d, popupPaint)

        // Right: chevron down, to put the keyboard away.
        val rx = w - h * 0.75f
        stripPath.rewind()
        stripPath.moveTo(rx - r * 0.8f, cy - r * 0.3f)
        stripPath.lineTo(rx, cy + r * 0.45f)
        stripPath.lineTo(rx + r * 0.8f, cy - r * 0.3f)
        canvas.drawPath(stripPath, stripIconPaint)
    }

    private fun drawSelection(canvas: Canvas) {
        val target = selectedKey ?: return
        val placed = layoutEngine.keys.firstOrNull { refOf(it) == target } ?: return
        val d = density
        val inset = currentTheme().shape.insetDp * d * 0.5f
        selectionRect.set(placed.left + inset, placed.top + inset, placed.right - inset, placed.bottom - inset)
        val r = 10f * d
        selectionPaint.style = Paint.Style.FILL
        selectionPaint.color = withAlpha(profile.effects.accentColor, 0.22f)
        canvas.drawRoundRect(selectionRect, r, r, selectionPaint)
        selectionPaint.style = Paint.Style.STROKE
        selectionPaint.strokeWidth = 2.5f * d
        selectionPaint.color = profile.effects.accentColor
        canvas.drawRoundRect(selectionRect, r, r, selectionPaint)
        // Corner handles, so it reads as "selected for editing", not "pressed".
        selectionPaint.style = Paint.Style.FILL
        selectionPaint.color = Color.WHITE
        val hr = 3.5f * d
        for (x in floatArrayOf(selectionRect.left, selectionRect.right)) {
            for (y in floatArrayOf(selectionRect.top, selectionRect.bottom)) {
                canvas.drawCircle(x, y, hr + 1.5f * d, selectionPaint.apply { color = profile.effects.accentColor })
                canvas.drawCircle(x, y, hr, selectionPaint.apply { color = Color.WHITE })
            }
        }
    }

    private fun withAlpha(color: Int, fraction: Float): Int =
        (((Color.alpha(color) * fraction).toInt().coerceIn(0, 255)) shl 24) or (color and 0xFFFFFF)

    private fun drawTrail(canvas: Canvas) {
        if (trailCount - trailStart < 2) return
        val now = SystemClock.uptimeMillis()
        val d = density
        val base = profile.effects.accentColor
        val maxWidth = 7f * d
        for (i in trailStart + 1 until trailCount) {
            val age = now - trailT[i]
            val life = 1f - age.toFloat() / TRAIL_FADE_MS
            if (life <= 0f) continue
            trailPaint.color = withAlpha(base, 0.9f * life)
            trailPaint.strokeWidth = maxWidth * (0.35f + 0.65f * life)
            canvas.drawLine(trailX[i - 1], trailY[i - 1], trailX[i], trailY[i], trailPaint)
        }
    }

    /** Drops expired trail points. Returns true while any remain visible. */
    private fun trimTrail(now: Long): Boolean {
        while (trailStart < trailCount && now - trailT[trailStart] > TRAIL_FADE_MS) trailStart++
        if (trailStart >= trailCount) {
            trailStart = 0
            trailCount = 0
            return gestureCount > 0
        }
        return true
    }

    private fun addGesturePoint(x: Float, y: Float, timeMs: Long) {
        if (gestureCount == gestureXs.size) {
            val n = gestureXs.size * 2
            gestureXs = gestureXs.copyOf(n)
            gestureYs = gestureYs.copyOf(n)
            gestureTs = gestureTs.copyOf(n)
        }
        gestureXs[gestureCount] = x
        gestureYs[gestureCount] = y
        gestureTs[gestureCount] = timeMs
        gestureCount++

        if (trailCount == TRAIL_CAPACITY) {
            // Compact: shift the live part of the trail to the front.
            val live = trailCount - trailStart
            System.arraycopy(trailX, trailStart, trailX, 0, live)
            System.arraycopy(trailY, trailStart, trailY, 0, live)
            System.arraycopy(trailT, trailStart, trailT, 0, live)
            trailStart = 0
            trailCount = live
            if (trailCount == TRAIL_CAPACITY) trailStart = TRAIL_CAPACITY / 2
        }
        trailX[trailCount] = x
        trailY[trailCount] = y
        trailT[trailCount] = SystemClock.uptimeMillis()
        trailCount++

        if (profile.effects.particle == ParticleEffect.Trail) {
            val step = 10f * density
            if (kotlin.math.hypot(x - lastParticleX, y - lastParticleY) > step) {
                lastParticleX = x
                lastParticleY = y
                layoutEngine.hitTest(x, y, Float.MAX_VALUE)?.let { effects.emitAt(x, y, it) }
            }
        }
        scheduleFrame()
    }

    private fun snapshotGesture(): GestureTrace {
        val anchors = letterAnchors()
        return GestureTrace(
            gestureXs.copyOf(gestureCount),
            gestureYs.copyOf(gestureCount),
            gestureTs.copyOf(gestureCount),
            gestureCount,
            anchors,
        )
    }

    private fun drawLongPressPopup(canvas: Canvas, placed: PlacedKey, theme: Theme) {
        val options = placed.key.longPress
        if (options.isEmpty()) return

        val density = density
        val cellW = placed.width.coerceAtLeast(34f * density)
        val cellH = placed.height * 0.92f
        val totalW = cellW * options.size
        val padding = 4f * density

        var left = placed.centerX - totalW / 2f
        left = left.coerceIn(padding, (width - totalW - padding).coerceAtLeast(padding))

        // Prefer above the key; drop below when there is no room (top row).
        val above = placed.top - cellH - padding
        val top = if (above >= 0f) above else placed.bottom + padding

        popupRect.set(left, top, left + totalW, top + cellH)
        val radius = 10f * density

        popupPaint.color = (theme.popupFill as? ColorSpec.Solid)?.color
            ?: if (theme.isDark) 0xFF2E2E38.toInt() else 0xFFFFFFFF.toInt()
        popupPaint.setShadowLayer(6f * density, 0f, 2f * density, 0x55000000)
        canvas.drawRoundRect(popupRect, radius, radius, popupPaint)
        popupPaint.clearShadowLayer()

        popupTextPaint.textSize = cellH * 0.42f
        popupTextPaint.getFontMetrics(fontMetrics)
        val shifted = (shiftState != ShiftState.Off || shiftHeld) && placed.key.shiftLabel != null

        options.forEachIndexed { i, option ->
            val cellLeft = left + cellW * i
            if (i == popupSelection) {
                popupPaint.color = profile.effects.accentColor
                popupRect.set(cellLeft + 2f, top + 2f, cellLeft + cellW - 2f, top + cellH - 2f)
                canvas.drawRoundRect(popupRect, radius * 0.7f, radius * 0.7f, popupPaint)
                popupTextPaint.color = Color.WHITE
            } else {
                popupTextPaint.color = theme.popupTextColor ?: theme.textColor
            }
            val text = if (shifted) option.uppercase() else option
            val baseSize = cellH * 0.42f
            popupTextPaint.textSize = baseSize
            if (text.length > 1) {
                val measured = popupTextPaint.measureText(text)
                if (measured > cellW * 0.86f) popupTextPaint.textSize = baseSize * cellW * 0.86f / measured
            }
            popupTextPaint.getFontMetrics(fontMetrics)
            val baseline = top + cellH / 2f - (fontMetrics.ascent + fontMetrics.descent) / 2f
            canvas.drawText(text, cellLeft + cellW / 2f, baseline, popupTextPaint)
        }
    }

    // -------------------------------------------------------------------------
    // Touch
    // -------------------------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (editMode) return onEditTouch(event)
        val action = event.actionMasked
        val index = event.actionIndex
        val time = event.eventTime

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val id = event.getPointerId(index)
                val x = event.getX(index)
                val y = event.getY(index)
                if (y < stripHeightPx && router.activePointerCount == 0 && stripPointerId < 0) {
                    stripPointerId = id
                    stripPressedSlot = stripSlotAt(x)
                    invalidate()
                } else if (id != stripPointerId) {
                    router.down(id, x, y, time)
                }
            }

            MotionEvent.ACTION_MOVE -> {
                // Historical samples matter for swipe: at 60 Hz a fast stroke
                // crosses a whole key between frames.
                for (h in 0 until event.historySize) {
                    for (p in 0 until event.pointerCount) {
                        val id = event.getPointerId(p)
                        if (id == stripPointerId) continue
                        router.move(
                            id, event.getHistoricalX(p, h), event.getHistoricalY(p, h),
                            event.getHistoricalEventTime(h),
                        )
                    }
                }
                for (p in 0 until event.pointerCount) {
                    val id = event.getPointerId(p)
                    if (id == stripPointerId) {
                        val slot = if (event.getY(p) < stripHeightPx) stripSlotAt(event.getX(p)) else -1
                        if (slot != stripPressedSlot) {
                            stripPressedSlot = slot
                            invalidate()
                        }
                        continue
                    }
                    router.move(id, event.getX(p), event.getY(p), time)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val id = event.getPointerId(index)
                if (id == stripPointerId) {
                    val slot = stripPressedSlot
                    stripPointerId = -1
                    stripPressedSlot = -1
                    invalidate()
                    if (slot >= 0) onStripTap(slot)
                } else {
                    router.up(id, event.getX(index), event.getY(index), time)
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                router.cancelAll()
                stripPointerId = -1
                stripPressedSlot = -1
                effects.cancel()
                scheduleFrame()
            }
        }
        return true
    }

    private fun onEditTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                editDownX = event.x
                editDownY = event.y
            }
            MotionEvent.ACTION_UP -> {
                if (kotlin.math.hypot(event.x - editDownX, event.y - editDownY) < touchSlop * 2) {
                    val placed = layoutEngine.hitTest(event.x, event.y, HIT_SLOP_DP * density)
                    onKeyPicked?.invoke(placed?.let { refOf(it) })
                }
            }
        }
        return true
    }

    private fun stripSlotAt(x: Float): Int {
        if (suggestions.isEmpty()) {
            val h = stripHeightPx
            return when {
                x < h * 1.5f -> 0
                x > width - h * 1.5f -> 2
                else -> -1
            }
        }
        return (x / (width.toFloat() / MAX_SUGGESTIONS)).toInt().coerceIn(0, MAX_SUGGESTIONS - 1)
    }

    private fun onStripTap(slot: Int) {
        if (suggestions.isEmpty()) {
            when (slot) {
                0 -> send(KeyAction.Special(KeyCode.Settings, STRIP_SETTINGS))
                2 -> send(KeyAction.Special(KeyCode.Hide, STRIP_HIDE))
            }
            return
        }
        val index = suggestionForSlot(slot)
        if (index >= 0) send(KeyAction.PickSuggestion(index, suggestions[index]))
    }

    private fun updatePopupSelection(x: Float) {
        val placed = popupKey ?: return
        val options = placed.key.longPress
        if (options.isEmpty()) return
        val density = density
        val cellW = placed.width.coerceAtLeast(34f * density)
        val totalW = cellW * options.size
        var left = placed.centerX - totalW / 2f
        left = left.coerceIn(4f * density, (width - totalW - 4f * density).coerceAtLeast(4f * density))
        popupSelection = (((x - left) / cellW).toInt()).coerceIn(0, options.size - 1)
    }

    private fun emit(key: KeyDef, shifted: Boolean) {
        if (key.code == KeyCode.Text || key.code == KeyCode.Macro) {
            val upper = shifted || shiftState != ShiftState.Off
            val text = if (upper && key.shiftLabel != null) key.shiftLabel else key.effectiveOutput
            if (text.isNotEmpty()) send(KeyAction.Text(text, key))
        } else {
            send(KeyAction.Special(key.code, key))
        }
    }

    private fun send(action: KeyAction) {
        if (previewMode) return
        onKeyAction?.onKeyAction(action)
    }

    /** Re-reads the profile and repaints. Used for live preview from the Studio. */
    fun refresh() = rebuild()
}
