package io.keystudio.core.typing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Swipe decoding against synthetic but human-shaped swipes on a Poco X3-sized
 * QWERTY: each letter aimed at with some scatter, corners cut by smoothing,
 * start and end a little off-centre.
 */
class SwipeDecoderTest {

    companion object {
        lateinit var dictionary: Dictionary

        // Poco X3: 1080 px wide; keys ~108 x 150 px in the letter rows.
        const val KW = 108f
        const val KH = 150f

        val anchors: List<KeyAnchor> = buildList {
            fun row(chars: String, y: Float, offset: Float) {
                chars.forEachIndexed { i, c ->
                    add(KeyAnchor(c, offset + KW * i + KW / 2f, y, KW, KH))
                }
            }
            row("qwertyuiop", KH * 0.5f, 0f)
            row("asdfghjkl", KH * 1.5f, KW / 2f)
            row("zxcvbnm", KH * 2.5f, KW * 1.5f)
        }
        private val byChar = anchors.associateBy { it.char }

        @BeforeClass
        @JvmStatic
        fun load() {
            dictionary = File("src/main/assets/dict/en_US.tsv").inputStream().use { Dictionary.load(it) }
        }

        /** A swipe through [word]'s letters, with human error drawn from [random]. */
        fun swipe(word: String, random: Random, scatter: Float = 0.22f): GestureTrace {
            val targets = ArrayList<Pair<Float, Float>>()
            var prev = ' '
            for (c in word.lowercase()) {
                if (!c.isLetter() || c == prev) continue
                prev = c
                val a = byChar.getValue(c)
                targets += (a.x + gauss(random) * scatter * KW) to (a.y + gauss(random) * scatter * KH)
            }
            if (targets.size == 1) {
                // A one-letter path still has to move to count as a swipe.
                val (x, y) = targets[0]
                targets += (x + KW * 0.3f) to y
            }
            // Dense polyline, then a moving average to round the corners the way
            // a thumb does.
            val xs = ArrayList<Float>()
            val ys = ArrayList<Float>()
            for (i in 1 until targets.size) {
                val (x0, y0) = targets[i - 1]
                val (x1, y1) = targets[i]
                val steps = (hypot(x1 - x0, y1 - y0) / 12f).toInt().coerceAtLeast(1)
                for (s in 0 until steps) {
                    val t = s.toFloat() / steps
                    xs += x0 + (x1 - x0) * t
                    ys += y0 + (y1 - y0) * t
                }
            }
            xs += targets.last().first
            ys += targets.last().second
            val window = 3
            val sx = FloatArray(xs.size)
            val sy = FloatArray(ys.size)
            for (i in xs.indices) {
                var ax = 0f; var ay = 0f; var n = 0
                for (k in (i - window)..(i + window)) {
                    if (k in xs.indices) { ax += xs[k]; ay += ys[k]; n++ }
                }
                sx[i] = ax / n
                sy[i] = ay / n
            }
            sx[0] = xs[0]; sy[0] = ys[0]
            sx[xs.size - 1] = xs.last(); sy[ys.size - 1] = ys.last()
            return GestureTrace(sx, sy, LongArray(sx.size) { it * 8L }, sx.size, anchors)
        }

        private fun gauss(r: Random): Float {
            // Box-Muller, clipped so a single sample never lands two keys away.
            val u = r.nextDouble().coerceAtLeast(1e-9)
            val v = r.nextDouble()
            val g = kotlin.math.sqrt(-2.0 * kotlin.math.ln(u)) * kotlin.math.cos(2 * Math.PI * v)
            return g.toFloat().coerceIn(-2f, 2f)
        }
    }

    private val decoder = SwipeDecoder(dictionary)

    @Test
    fun cleanSwipesOfCommonWords() {
        val random = Random(7)
        // Not "typing": t-y-p and t-o-p are the same straight line along the top
        // row, so "topping" is geometrically identical and only frequency can
        // choose. That is a real limit of shape matching, not something to tune.
        for (word in listOf("hello", "the", "keyboard", "people", "because", "world", "swipe", "thanks")) {
            val result = decoder.decode(swipe(word, random, scatter = 0f))
            assertEquals("clean '$word' -> $result", word, result.firstOrNull()?.word)
        }
    }

    /**
     * The headline number: of the 400 most common swipeable words, how many
     * come out first, and how many land in the strip's three slots.
     */
    @Test
    fun accuracyOnCommonWords() {
        val random = Random(42)
        val words = dictionary.words.asSequence()
            .filter { w -> w.length >= 2 && w.all { it in 'a'..'z' } }
            .take(400).toList()
        var top1 = 0
        var top3 = 0
        val misses = ArrayList<String>()
        val started = System.nanoTime()
        for (w in words) {
            val result = decoder.decode(swipe(w, random), limit = 3).map { it.word.lowercase() }
            if (result.firstOrNull() == w) top1++ else misses += "$w->${result.joinToString("/")}"
            if (w in result) top3++
        }
        val msPer = (System.nanoTime() - started) / 1e6 / words.size
        println("swipe accuracy: top1 ${top1 * 100 / words.size}%, top3 ${top3 * 100 / words.size}%, " +
            "%.1f ms/word".format(msPer))
        println("misses: " + misses.take(60).joinToString(", "))
        assertTrue("top-1 accuracy ${top1 * 100 / words.size}%", top1 >= words.size * 0.90)
        assertTrue("top-3 accuracy ${top3 * 100 / words.size}%", top3 >= words.size * 0.97)
    }

    /**
     * Less common words, where over-trusting frequency shows up: each of these
     * has a more popular word with a vaguely similar shape to lose to.
     */
    @Test
    fun accuracyOnMidFrequencyWords() {
        val random = Random(43)
        val words = dictionary.words.asSequence()
            .filter { w -> w.length >= 2 && w.all { it in 'a'..'z' } }
            .drop(2500).filterIndexed { i, _ -> i % 10 == 0 }
            .take(300).toList()
        var top1 = 0
        var top3 = 0
        for (w in words) {
            val result = decoder.decode(swipe(w, random), limit = 3).map { it.word.lowercase() }
            if (result.firstOrNull() == w) top1++
            if (w in result) top3++
        }
        println("mid-frequency swipe accuracy: top1 ${top1 * 100 / words.size}%, top3 ${top3 * 100 / words.size}%")
        assertTrue("top-1 accuracy ${top1 * 100 / words.size}%", top1 >= words.size * 0.85)
        assertTrue("top-3 accuracy ${top3 * 100 / words.size}%", top3 >= words.size * 0.95)
    }

    @Test
    fun longerWords() {
        val random = Random(3)
        val words = listOf(
            "beautiful", "important", "information", "different", "something", "government",
            "understand", "experience", "everything", "question", "keyboard", "wonderful",
        )
        var hit = 0
        for (w in words) {
            val result = decoder.decode(swipe(w, random), limit = 3).map { it.word.lowercase() }
            if (w in result) hit++ else println("missed long word $w -> $result")
        }
        assertTrue("long words in top 3: $hit/${words.size}", hit >= words.size - 1)
    }
}
