package io.keystudio.core.typing

import kotlin.math.hypot
import kotlin.math.sqrt

/** A decoded word and how much the decoder believes it, higher is better. */
class Candidate(val word: String, val score: Float, val index: Int = -1) {
    override fun toString() = "$word(${"%.2f".format(score)})"
}

/**
 * Turns a swipe into words.
 *
 * A template matcher in the SHARK² family: every plausible word has an *ideal*
 * path -- the polyline through its letters' key centres -- and the swipe is
 * compared against each one. Two measures are combined, both in key-sized
 * units so they mean the same thing on any keyboard size:
 *
 * - **alignment**: walking the word's letters in order, how close the swipe
 *   comes to each one (a dynamic-programming fit, so a letter can only be
 *   matched after the one before it). This is what makes "the swipe passed
 *   through every letter" count.
 * - **location**: both paths resampled to the same number of points and
 *   compared point by point. This penalises detours the word does not explain.
 *
 * Geometry is then weighed against how common the word is. Only words whose
 * first and last letters lie near the swipe's two ends are considered at all,
 * which cuts 160,000 words to a few thousand before any real work happens.
 *
 * It works from the key rectangles captured at the moment of the swipe, so a
 * resized or rearranged layout decodes correctly without retraining anything.
 * Deliberately behind a small interface: a learned decoder can replace it later.
 */
class SwipeDecoder(private val dictionary: Dictionary) {

    /** Words the user taught the keyboard, decoded alongside the dictionary. */
    var extraWords: List<String> = emptyList()

    /**
     * How the evidence is weighed. Tuned by grid search in `SwipeDecoderTest`
     * over both very common and mid-frequency words -- tuning on common words
     * alone rewards trusting frequency over the finger, which then loses every
     * less common word to a popular one with a vaguely similar shape.
     */
    class Weights(
        val align: Float = 6.4f,
        val location: Float = 3.84f,
        val frequency: Float = 0.25f,
        val length: Float = 1.76f,
        /** People start and finish a swipe more precisely than they trace its middle. */
        val endpoint: Float = 2.56f,
    )

    var weights = Weights()

    private companion object {
        const val SAMPLES = 48
        /** Keys within this many key-widths of an end of the swipe may start/end the word. */
        const val END_RADIUS = 1.25f
        const val MAX_LETTERS = 24
    }

    // Scratch, reused across calls; decode runs on one thread at a time.
    private val traceX = FloatArray(SAMPLES)
    private val traceY = FloatArray(SAMPLES)
    private val idealX = FloatArray(SAMPLES)
    private val idealY = FloatArray(SAMPLES)
    private val letterX = FloatArray(MAX_LETTERS)
    private val letterY = FloatArray(MAX_LETTERS)
    private val dp = FloatArray(SAMPLES)
    private val dpNext = FloatArray(SAMPLES)

    fun decode(trace: GestureTrace, limit: Int = 5): List<Candidate> {
        if (trace.size < 2 || trace.anchors.isEmpty()) return emptyList()

        // Work in key units: x in key widths, y in key heights. Keys are taller
        // than wide on a phone, and a vertical miss of half a key is no worse
        // than a horizontal one.
        val kw = trace.keyWidth.coerceAtLeast(1f)
        val kh = trace.anchors.map { it.height }.sorted()[trace.anchors.size / 2].coerceAtLeast(1f)
        val anchorX = FloatArray(128) { Float.NaN }
        val anchorY = FloatArray(128) { Float.NaN }
        for (a in trace.anchors) {
            if (a.char.code < 128) {
                anchorX[a.char.code] = a.x / kw
                anchorY[a.char.code] = a.y / kh
            }
        }

        val length = resample(trace, kw, kh)
        if (length < 0.35f) return emptyList()

        val starts = nearbyLetters(traceX[0], traceY[0], anchorX, anchorY)
        val ends = nearbyLetters(traceX[SAMPLES - 1], traceY[SAMPLES - 1], anchorX, anchorY)

        val best = ArrayList<Candidate>(limit + 1)
        fun offer(word: String, key: String, logProb: Float, index: Int) {
            val geometry = geometryCost(key, length, anchorX, anchorY)
            if (geometry == Float.MAX_VALUE) return
            val score = -geometry + weights.frequency * logProb
            if (best.size == limit && score <= best.last().score) return
            if (best.any { it.word.equals(word, ignoreCase = true) }) return
            val c = Candidate(word, score, index)
            val at = best.indexOfFirst { it.score < score }.let { if (it < 0) best.size else it }
            best.add(at, c)
            if (best.size > limit) best.removeAt(best.lastIndex)
        }

        for (s in starts) for (e in ends) {
            for (i in dictionary.candidatesFor(s, e)) {
                if (dictionary.offensive[i]) continue
                offer(dictionary.words[i], dictionary.keys[i], dictionary.logProb(i), i)
            }
        }
        for (w in extraWords) {
            val key = Dictionary.keyOf(w)
            if (key.isEmpty() || key[0] !in starts || key[key.length - 1] !in ends) continue
            // A learned word ranks like a moderately common one.
            offer(w, key, -9f, -1)
        }
        return best
    }

    private fun nearbyLetters(x: Float, y: Float, ax: FloatArray, ay: FloatArray): CharArray {
        val out = StringBuilder()
        var nearest = ' '
        var nearestD = Float.MAX_VALUE
        for (c in 'a'..'z') {
            val cx = ax[c.code]
            if (cx.isNaN()) continue
            val d = hypot(cx - x, ay[c.code] - y)
            if (d < END_RADIUS) out.append(c)
            if (d < nearestD) {
                nearestD = d
                nearest = c
            }
        }
        if (out.isEmpty() && nearest != ' ') out.append(nearest)
        return out.toString().toCharArray()
    }

    /** Resamples the swipe to [SAMPLES] evenly spaced points. Returns its length. */
    private fun resample(trace: GestureTrace, kw: Float, kh: Float): Float {
        val n = trace.size
        var total = 0f
        for (i in 1 until n) {
            total += hypot((trace.xs[i] - trace.xs[i - 1]) / kw, (trace.ys[i] - trace.ys[i - 1]) / kh)
        }
        val step = total / (SAMPLES - 1)
        traceX[0] = trace.xs[0] / kw
        traceY[0] = trace.ys[0] / kh
        var out = 1
        var carried = 0f
        var i = 1
        var px = traceX[0]
        var py = traceY[0]
        while (i < n && out < SAMPLES) {
            val qx = trace.xs[i] / kw
            val qy = trace.ys[i] / kh
            val seg = hypot(qx - px, qy - py)
            if (seg > 0f && carried + seg >= step) {
                val t = (step - carried) / seg
                px += (qx - px) * t
                py += (qy - py) * t
                traceX[out] = px
                traceY[out] = py
                out++
                carried = 0f
            } else {
                carried += seg
                px = qx
                py = qy
                i++
            }
        }
        while (out < SAMPLES) {
            traceX[out] = trace.xs[n - 1] / kw
            traceY[out] = trace.ys[n - 1] / kh
            out++
        }
        return total
    }

    /**
     * How badly [key]'s ideal path explains the swipe, or [Float.MAX_VALUE] when
     * it cannot possibly be the word.
     */
    private fun geometryCost(key: String, traceLength: Float, ax: FloatArray, ay: FloatArray): Float {
        // Letters with repeats collapsed: "hello" is swiped h-e-l-o.
        var m = 0
        var prev = ' '
        for (c in key) {
            if (c == prev) continue
            val code = c.code
            if (code >= 128 || ax[code].isNaN()) return Float.MAX_VALUE
            if (m == MAX_LETTERS) return Float.MAX_VALUE
            letterX[m] = ax[code]
            letterY[m] = ay[code]
            m++
            prev = c
        }
        if (m == 0) return Float.MAX_VALUE

        var ideal = 0f
        for (j in 1 until m) ideal += hypot(letterX[j] - letterX[j - 1], letterY[j] - letterY[j - 1])
        // A swipe is never much shorter than the word's path, and rarely more
        // than about twice as long; anything outside that is another word.
        if (ideal > traceLength * 1.45f + 1.2f) return Float.MAX_VALUE
        if (ideal < traceLength * 0.4f - 1.5f) return Float.MAX_VALUE

        val align = alignment(m)
        if (align > 1.6f) return Float.MAX_VALUE
        val location = location(m, ideal)
        // Swipe the word could not have produced: "there" drawn and read as
        // "the" leaves the e-r-e loop unexplained. Real swipes run a little long
        // (curves, overshoot), so only length beyond that allowance counts.
        val excess = (traceLength - ideal * 1.15f - 0.3f).coerceAtLeast(0f) +
            (ideal * 0.85f - traceLength - 0.3f).coerceAtLeast(0f)
        val startMiss = dist(letterX[0], letterY[0], 0)
        val endMiss = dist(letterX[m - 1], letterY[m - 1], SAMPLES - 1)
        val w = weights
        return w.align * align * align + w.location * location * location +
            w.length * excess * excess +
            w.endpoint * (startMiss * startMiss + endMiss * endMiss)
    }

    /**
     * Mean distance from each letter to the swipe, matching letters to swipe
     * points in order. First and last letters are pinned to the ends.
     */
    private fun alignment(m: Int): Float {
        // dp[i] = best total cost with the current letter matched at point i.
        for (i in 0 until SAMPLES) dp[i] = Float.MAX_VALUE
        dp[0] = dist(letterX[0], letterY[0], 0)
        for (j in 1 until m) {
            var runningMin = Float.MAX_VALUE
            val last = j == m - 1
            for (i in 0 until SAMPLES) {
                // Two letters may share a point only if they are the same point
                // on the keyboard; otherwise the match must move forward.
                if (dp[i] < runningMin) runningMin = dp[i]
                dpNext[i] = if (runningMin == Float.MAX_VALUE || (last && i != SAMPLES - 1)) {
                    Float.MAX_VALUE
                } else runningMin + dist(letterX[j], letterY[j], i)
            }
            System.arraycopy(dpNext, 0, dp, 0, SAMPLES)
        }
        val total = if (m == 1) dp[0] + dist(letterX[0], letterY[0], SAMPLES - 1) else dp[SAMPLES - 1]
        return total / maxOf(m, 2)
    }

    /** Mean point-to-point distance between the swipe and the word's ideal path. */
    private fun location(m: Int, idealLength: Float): Float {
        if (m == 1 || idealLength <= 0f) {
            for (i in 0 until SAMPLES) { idealX[i] = letterX[0]; idealY[i] = letterY[0] }
        } else {
            val step = idealLength / (SAMPLES - 1)
            var seg = 0
            var segStart = 0f
            var segLen = hypot(letterX[1] - letterX[0], letterY[1] - letterY[0])
            for (i in 0 until SAMPLES) {
                val target = step * i
                while (seg < m - 2 && target > segStart + segLen) {
                    segStart += segLen
                    seg++
                    segLen = hypot(letterX[seg + 1] - letterX[seg], letterY[seg + 1] - letterY[seg])
                }
                val t = if (segLen > 0f) ((target - segStart) / segLen).coerceIn(0f, 1f) else 0f
                idealX[i] = letterX[seg] + (letterX[seg + 1] - letterX[seg]) * t
                idealY[i] = letterY[seg] + (letterY[seg + 1] - letterY[seg]) * t
            }
        }
        var sum = 0f
        for (i in 0 until SAMPLES) sum += hypot(idealX[i] - traceX[i], idealY[i] - traceY[i])
        return sum / SAMPLES
    }

    private fun dist(x: Float, y: Float, i: Int): Float {
        val dx = x - traceX[i]
        val dy = y - traceY[i]
        return sqrt(dx * dx + dy * dy)
    }
}
