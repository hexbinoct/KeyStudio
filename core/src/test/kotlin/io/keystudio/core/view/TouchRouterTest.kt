package io.keystudio.core.view

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * The touch model, driven by hand. One row of 100 px keys:
 *
 *     0:q  1:w  2:e  3:shift  4:space(300 wide)  5:backspace
 */
class TouchRouterTest {

    private class Key(val left: Float, val right: Float, val kind: String, val longPress: Boolean = false)

    private val row = listOf(
        Key(0f, 100f, "q"), Key(100f, 200f, "w"), Key(200f, 300f, "e", longPress = true),
        Key(300f, 400f, "shift"), Key(400f, 700f, "space"), Key(700f, 800f, "backspace"),
    )

    private val keys = object : TouchKeys {
        override fun hitTest(x: Float, y: Float): Int =
            if (y !in 0f..100f) -1 else row.indexOfFirst { x >= it.left && x < it.right }

        override fun contains(index: Int, x: Float, y: Float): Boolean =
            y in 0f..100f && x >= row[index].left && x < row[index].right

        override fun isShift(index: Int) = row[index].kind == "shift"
        override fun isSpace(index: Int) = row[index].kind == "space"
        override fun isBackspace(index: Int) = row[index].kind == "backspace"
        override fun isRepeatable(index: Int) = row[index].kind == "backspace"
        override fun hasLongPress(index: Int) = row[index].longPress
        override fun startsGesture(index: Int) = row[index].kind.length == 1
    }

    /** A clock that only moves when the test says so. */
    private inner class ManualScheduler : TouchScheduler {
        var now = 0L
        private val tasks = mutableListOf<Pair<Long, () -> Unit>>()

        override fun post(delayMs: Long, action: () -> Unit): Any {
            val task = (now + delayMs) to action
            tasks.add(task)
            return task
        }

        override fun cancel(token: Any) {
            tasks.remove(token)
        }

        fun advance(ms: Long) {
            val target = now + ms
            while (true) {
                val next = tasks.filter { it.first <= target }.minByOrNull { it.first } ?: break
                tasks.remove(next)
                now = next.first
                next.second()
            }
            now = target
        }
    }

    private val log = mutableListOf<String>()
    private lateinit var scheduler: ManualScheduler
    private lateinit var router: TouchRouter

    private val listener = object : TouchListener {
        override fun keyDown(index: Int, x: Float, y: Float) = Unit
        override fun keyUp(index: Int) = Unit
        override fun commit(index: Int, shifted: Boolean) {
            log += if (shifted) row[index].kind.uppercase() else row[index].kind
        }
        override fun repeat(index: Int) { log += "repeat:${row[index].kind}" }
        override fun openPopup(index: Int, x: Float) { log += "popup:${row[index].kind}" }
        override fun movePopup(x: Float) = Unit
        override fun commitPopup() { log += "popup-commit" }
        override fun dismissPopup() { log += "popup-dismiss" }
        override fun shiftHeld(held: Boolean) { log += "shiftHeld:$held" }
        override fun gestureStart(x: Float, y: Float, timeMs: Long) { log += "gesture-start" }
        override fun gestureMove(x: Float, y: Float, timeMs: Long) = Unit
        override fun gestureEnd() { log += "gesture-end" }
        override fun gestureCancel() { log += "gesture-cancel" }
        override fun moveCursor(steps: Int) { log += "cursor:$steps" }
        override fun deleteSwipe(steps: Int, done: Boolean) { log += "delete:$steps:$done" }
    }

    @Before
    fun setUp() {
        scheduler = ManualScheduler()
        router = TouchRouter(keys, scheduler, listener).apply {
            touchSlopPx = 10f
            gestureThresholdPx = 30f
            cursorStepPx = 20f
            deleteStepPx = 50f
        }
    }

    private fun centre(i: Int) = (row[i].left + row[i].right) / 2f

    @Test
    fun singleTapCommitsOnRelease() {
        router.down(0, centre(0), 50f, 0)
        assertEquals(emptyList<String>(), log)
        router.up(0, centre(0), 50f, 60)
        assertEquals(listOf("q"), log)
    }

    @Test
    fun secondFingerCommitsTheFirstImmediately() {
        router.down(0, centre(0), 50f, 0)
        router.down(1, centre(1), 50f, 40)
        // q fired the moment w landed, without waiting for q's finger to lift.
        assertEquals(listOf("q"), log)
        router.up(0, centre(0), 50f, 80)
        assertEquals("lifting q afterwards must not type it twice", listOf("q"), log)
        router.up(1, centre(1), 50f, 120)
        assertEquals(listOf("q", "w"), log)
    }

    @Test
    fun threeOverlappingFingersKeepTheirOrder() {
        router.down(0, centre(0), 50f, 0)
        router.down(1, centre(1), 50f, 20)
        router.down(2, centre(2), 50f, 40)
        router.up(1, centre(1), 50f, 60)
        router.up(0, centre(0), 50f, 70)
        router.up(2, centre(2), 50f, 90)
        assertEquals(listOf("q", "w", "e"), log)
    }

    @Test
    fun heldShiftCapitalisesAndDoesNotToggle() {
        router.down(0, centre(3), 50f, 0)           // shift down
        router.down(1, centre(0), 50f, 30)          // q while shift held
        router.up(1, centre(0), 50f, 60)
        router.down(1, centre(1), 50f, 90)          // w while shift still held
        router.up(1, centre(1), 50f, 120)
        router.up(0, centre(3), 50f, 150)           // release shift: no toggle
        assertEquals(listOf("shiftHeld:true", "Q", "W", "shiftHeld:false"), log)
    }

    @Test
    fun letterStaysCapitalWhenShiftLiftsFirst() {
        router.down(0, centre(3), 50f, 0)
        router.down(1, centre(0), 50f, 30)
        router.up(0, centre(3), 50f, 50)            // shift lifted before the letter
        router.up(1, centre(0), 50f, 70)
        assertEquals(listOf("shiftHeld:true", "shiftHeld:false", "Q"), log)
    }

    @Test
    fun shiftTappedAloneStillFires() {
        router.down(0, centre(3), 50f, 0)
        router.up(0, centre(3), 50f, 50)
        assertEquals(listOf("shift"), log)
    }

    @Test
    fun rollingOntoAHeldRepeatStopsItWithoutAnExtraDelete() {
        router.down(0, centre(5), 50f, 0)
        scheduler.advance(470)                      // 400 ms start + one 50 ms tick
        assertEquals(listOf("repeat:backspace", "repeat:backspace"), log)
        router.down(1, centre(0), 50f, 470)
        scheduler.advance(500)
        router.up(1, centre(0), 50f, 1000)
        router.up(0, centre(5), 50f, 1000)
        assertEquals(listOf("repeat:backspace", "repeat:backspace", "q"), log)
    }

    @Test
    fun longPressOpensPopupAndReleaseCommitsIt() {
        router.down(0, centre(2), 50f, 0)
        scheduler.advance(320)
        router.up(0, centre(2), 50f, 400)
        assertEquals(listOf("popup:e", "popup-commit"), log)
    }

    @Test
    fun secondFingerDismissesAnOpenPopup() {
        router.down(0, centre(2), 50f, 0)
        scheduler.advance(320)
        router.down(1, centre(0), 50f, 400)
        router.up(1, centre(0), 50f, 450)
        router.up(0, centre(2), 50f, 460)
        assertEquals(listOf("popup:e", "popup-dismiss", "q"), log)
    }

    @Test
    fun slidingAcrossLettersBecomesAGesture() {
        router.down(0, centre(0), 50f, 0)
        router.move(0, 120f, 50f, 20)
        router.move(0, 250f, 50f, 40)
        router.up(0, 250f, 50f, 60)
        assertEquals(listOf("gesture-start", "gesture-end"), log)
    }

    @Test
    fun smallDriftStillTypesTheOriginalKey() {
        router.down(0, 90f, 50f, 0)
        router.move(0, 105f, 50f, 20)               // just over the edge, under threshold
        router.up(0, 105f, 50f, 40)
        assertEquals(listOf("q"), log)
    }

    @Test
    fun noGestureWhenDisabled() {
        router.gesturesEnabled = false
        router.down(0, centre(0), 50f, 0)
        router.move(0, 250f, 50f, 40)
        router.up(0, 250f, 50f, 60)
        assertEquals(emptyList<String>(), log)
    }

    @Test
    fun spaceSlideMovesTheCaretAndTypesNoSpace() {
        router.down(0, 550f, 50f, 0)
        router.move(0, 600f, 50f, 20)               // +50 px -> 2 steps of 20
        router.move(0, 510f, 50f, 40)               // back 90 -> 4 steps left, 10 px carried
        router.up(0, 510f, 50f, 60)
        assertEquals(listOf("cursor:2", "cursor:-4"), log)
    }

    @Test
    fun backspaceSlideLeftSelectsWordsThenDeletes() {
        router.down(0, 790f, 50f, 0)
        router.move(0, 760f, 50f, 20)               // past 2x slop: one word
        router.move(0, 700f, 50f, 40)               // 90 px: two words
        router.up(0, 700f, 50f, 60)
        assertEquals(listOf("delete:1:false", "delete:2:false", "delete:2:true"), log)
    }

    @Test
    fun cancelFiresNothing() {
        router.down(0, centre(0), 50f, 0)
        router.cancelAll()
        router.up(0, centre(0), 50f, 40)
        assertEquals(emptyList<String>(), log)
    }
}
