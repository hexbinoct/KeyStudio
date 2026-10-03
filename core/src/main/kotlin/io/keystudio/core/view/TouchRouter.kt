package io.keystudio.core.view

import kotlin.math.abs
import kotlin.math.hypot

/**
 * What the router needs to know about the keys under the fingers. Kept free of
 * Android types so the whole touch model runs, and is tested, on a plain JVM.
 */
interface TouchKeys {
    /** Index of the key at (x,y), with hit slop applied, or -1 for none. */
    fun hitTest(x: Float, y: Float): Int
    fun contains(index: Int, x: Float, y: Float): Boolean
    fun isShift(index: Int): Boolean
    fun isSpace(index: Int): Boolean
    fun isBackspace(index: Int): Boolean
    fun isRepeatable(index: Int): Boolean
    fun hasLongPress(index: Int): Boolean
    /** Whether a slide starting on this key may become a swipe-typed word. */
    fun startsGesture(index: Int): Boolean
}

/** Timers, abstracted so tests can drive time by hand. */
interface TouchScheduler {
    fun post(delayMs: Long, action: () -> Unit): Any
    fun cancel(token: Any)
}

/** Everything the router decides, reported upward. It never touches pixels itself. */
interface TouchListener {
    /** A finger landed on [index]: animate it and give feedback. */
    fun keyDown(index: Int, x: Float, y: Float)
    /** Visual release only. Whether the key fired is reported separately. */
    fun keyUp(index: Int)
    /**
     * The key fires, exactly once per tap. [shifted] is true when a held shift
     * key was down at the moment this key was pressed -- captured then, not now,
     * because fast typists routinely lift shift before the letter it capitalised.
     */
    fun commit(index: Int, shifted: Boolean)
    /** Another tick of a held repeatable key. */
    fun repeat(index: Int)
    fun openPopup(index: Int, x: Float)
    fun movePopup(x: Float)
    fun commitPopup()
    fun dismissPopup()
    /** Shift is being held down as a modifier while other keys are typed. */
    fun shiftHeld(held: Boolean)
    fun gestureStart(x: Float, y: Float, timeMs: Long)
    fun gestureMove(x: Float, y: Float, timeMs: Long)
    fun gestureEnd()
    fun gestureCancel()
    /** Space-bar trackpad: move the caret by [steps] characters (negative is left). */
    fun moveCursor(steps: Int)
    /** Backspace swipe: select [steps] words back (negative undoes), committed on release. */
    fun deleteSwipe(steps: Int, done: Boolean)
}

/**
 * Turns raw pointers into key presses, the way a real phone keyboard does.
 *
 * The rule that matters most is **rollover**: a second finger landing while the
 * first is still down commits the first key immediately. Fast two-thumb typing
 * constantly overlaps presses -- the next thumb is down before the previous one
 * has lifted -- and a keyboard that waits for each lift drops or delays letters.
 * Shift is the exception: held, it acts as a modifier for the keys typed under
 * it and does not fire itself on release.
 *
 * One finger sliding off a letter key becomes a swipe-typed word; a finger
 * sliding along the space bar moves the caret; one sliding left from backspace
 * selects words to delete. A finger sliding off any other key cancels it,
 * rather than typing a letter the user visibly moved away from.
 */
class TouchRouter(
    private val keys: TouchKeys,
    private val scheduler: TouchScheduler,
    private val listener: TouchListener,
) {
    /** Movement that still counts as a tap, in px. */
    var touchSlopPx = 12f
    /** How far a finger must travel off its key before a swipe starts, in px. */
    var gestureThresholdPx = 36f
    /** Horizontal travel per caret step on the space bar, in px. */
    var cursorStepPx = 14f
    /** Horizontal travel per word on the backspace swipe, in px. */
    var deleteStepPx = 44f
    var longPressMs = 300L
    var repeatStartMs = 400L
    var repeatIntervalMs = 50L
    var gesturesEnabled = true
    var spaceCursorEnabled = true
    var backspaceSwipeEnabled = true

    private enum class Mode { Key, Popup, Gesture, Cursor, DeleteSwipe, Done }

    private class Pointer(val id: Int) {
        var key = -1
        var mode = Mode.Key
        var downX = 0f
        var downY = 0f
        var anchorX = 0f
        var steps = 0
        var repeated = false
        var chorded = false
        var shiftedByHold = false
        var longPressToken: Any? = null
        var repeatToken: Any? = null
    }

    private val pointers = ArrayList<Pointer>(4)
    private var shiftHeldReported = false

    val activePointerCount: Int get() = pointers.size

    fun down(id: Int, x: Float, y: Float, timeMs: Long) {
        // A new finger settles every key already down, in the order they landed.
        for (p in pointers) rollOver(p)

        val p = Pointer(id)
        p.downX = x
        p.downY = y
        p.anchorX = x
        pointers.add(p)

        val index = keys.hitTest(x, y)
        if (index < 0) {
            p.mode = Mode.Done
            return
        }
        p.key = index
        listener.keyDown(index, x, y)

        if (keys.isShift(index)) {
            // A shift already held turns this one into a plain tap; nothing to do.
            return
        }
        if (heldShift() != null) {
            p.shiftedByHold = true
            reportShiftHeld(true)
        }

        if (keys.isRepeatable(index)) {
            p.repeatToken = scheduler.post(repeatStartMs) { repeatTick(p) }
        }
        if (keys.hasLongPress(index)) {
            p.longPressToken = scheduler.post(longPressMs) { longPress(p, x) }
        }
    }

    fun move(id: Int, x: Float, y: Float, timeMs: Long) {
        val p = find(id) ?: return
        when (p.mode) {
            Mode.Done -> return
            Mode.Popup -> listener.movePopup(x)
            Mode.Gesture -> listener.gestureMove(x, y, timeMs)
            Mode.Cursor -> cursorMove(p, x)
            Mode.DeleteSwipe -> deleteMove(p, x)
            Mode.Key -> keyMove(p, x, y, timeMs)
        }
    }

    fun up(id: Int, x: Float, y: Float, timeMs: Long) {
        val p = find(id) ?: return
        pointers.remove(p)
        cancelTimers(p)
        when (p.mode) {
            Mode.Popup -> {
                listener.commitPopup()
                listener.keyUp(p.key)
            }
            Mode.Gesture -> listener.gestureEnd()
            Mode.Cursor -> listener.keyUp(p.key)
            Mode.DeleteSwipe -> {
                listener.deleteSwipe(p.steps, done = true)
                listener.keyUp(p.key)
            }
            Mode.Key -> {
                val isShift = keys.isShift(p.key)
                if (!(isShift && p.chorded) && !p.repeated) listener.commit(p.key, p.shiftedByHold)
                listener.keyUp(p.key)
            }
            Mode.Done -> Unit
        }
        if (shiftHeldReported && heldShift() == null) reportShiftHeld(false)
    }

    /** The system took the touch stream away. Nothing fires. */
    fun cancelAll() {
        for (p in pointers) {
            cancelTimers(p)
            when (p.mode) {
                Mode.Popup -> {
                    listener.dismissPopup()
                    listener.keyUp(p.key)
                }
                Mode.Gesture -> listener.gestureCancel()
                Mode.DeleteSwipe -> {
                    listener.deleteSwipe(0, done = true)
                    listener.keyUp(p.key)
                }
                Mode.Key, Mode.Cursor -> listener.keyUp(p.key)
                Mode.Done -> Unit
            }
        }
        pointers.clear()
        if (shiftHeldReported) reportShiftHeld(false)
    }

    // -------------------------------------------------------------------------

    /** Settles a finger that is still down because another one just landed. */
    private fun rollOver(p: Pointer) {
        when (p.mode) {
            Mode.Key -> {
                if (keys.isShift(p.key)) {
                    // Shift held while typing is a modifier, not a tap.
                    p.chorded = true
                    return
                }
                cancelTimers(p)
                if (!p.repeated) listener.commit(p.key, p.shiftedByHold)
                listener.keyUp(p.key)
                p.mode = Mode.Done
            }
            Mode.Popup -> {
                cancelTimers(p)
                listener.dismissPopup()
                listener.keyUp(p.key)
                p.mode = Mode.Done
            }
            Mode.Gesture -> {
                // A second finger mid-swipe means the swipe was not deliberate.
                listener.gestureCancel()
                p.mode = Mode.Done
            }
            Mode.Cursor -> {
                listener.keyUp(p.key)
                p.mode = Mode.Done
            }
            Mode.DeleteSwipe -> {
                listener.deleteSwipe(p.steps, done = true)
                listener.keyUp(p.key)
                p.mode = Mode.Done
            }
            Mode.Done -> Unit
        }
    }

    private fun keyMove(p: Pointer, x: Float, y: Float, timeMs: Long) {
        val dx = x - p.downX
        val moved = hypot(dx, y - p.downY)
        if (moved <= touchSlopPx) return

        if (spaceCursorEnabled && keys.isSpace(p.key) && abs(dx) > touchSlopPx) {
            cancelTimers(p)
            p.mode = Mode.Cursor
            p.anchorX = p.downX
            cursorMove(p, x)
            return
        }
        if (backspaceSwipeEnabled && keys.isBackspace(p.key) && dx < -touchSlopPx * 2f && !p.repeated) {
            cancelTimers(p)
            p.mode = Mode.DeleteSwipe
            p.anchorX = p.downX
            deleteMove(p, x)
            return
        }

        val offKey = !keys.contains(p.key, x, y)
        if (gesturesEnabled && pointers.size == 1 && keys.startsGesture(p.key) &&
            offKey && moved > gestureThresholdPx
        ) {
            cancelTimers(p)
            listener.keyUp(p.key)
            p.mode = Mode.Gesture
            listener.gestureStart(p.downX, p.downY, timeMs)
            listener.gestureMove(x, y, timeMs)
            return
        }

        // Sliding off a key cancels it rather than typing the wrong letter. A
        // swipe-capable key waits for the gesture threshold before giving up.
        if (offKey && (!gesturesEnabled || !keys.startsGesture(p.key) || pointers.size > 1)) {
            cancelTimers(p)
            listener.keyUp(p.key)
            p.mode = Mode.Done
        }
    }

    private fun cursorMove(p: Pointer, x: Float) {
        val steps = ((x - p.anchorX) / cursorStepPx).toInt()
        if (steps != 0) {
            listener.moveCursor(steps)
            p.anchorX += steps * cursorStepPx
        }
    }

    private fun deleteMove(p: Pointer, x: Float) {
        val steps = ((p.anchorX - x) / deleteStepPx).toInt().coerceAtLeast(0) + 1
        if (steps != p.steps) {
            p.steps = steps
            listener.deleteSwipe(steps, done = false)
        }
    }

    private fun repeatTick(p: Pointer) {
        if (p.mode != Mode.Key || p !in pointers) return
        p.repeated = true
        listener.repeat(p.key)
        p.repeatToken = scheduler.post(repeatIntervalMs) { repeatTick(p) }
    }

    private fun longPress(p: Pointer, x: Float) {
        p.longPressToken = null
        if (p.mode != Mode.Key || p !in pointers) return
        p.repeatToken?.let { scheduler.cancel(it) }
        p.repeatToken = null
        p.mode = Mode.Popup
        listener.openPopup(p.key, x)
    }

    private fun cancelTimers(p: Pointer) {
        p.longPressToken?.let { scheduler.cancel(it) }
        p.repeatToken?.let { scheduler.cancel(it) }
        p.longPressToken = null
        p.repeatToken = null
    }

    private fun heldShift(): Pointer? =
        pointers.firstOrNull { it.mode == Mode.Key && it.key >= 0 && keys.isShift(it.key) }

    private fun reportShiftHeld(held: Boolean) {
        if (shiftHeldReported == held) return
        shiftHeldReported = held
        listener.shiftHeld(held)
    }

    private fun find(id: Int): Pointer? = pointers.firstOrNull { it.id == id }
}
