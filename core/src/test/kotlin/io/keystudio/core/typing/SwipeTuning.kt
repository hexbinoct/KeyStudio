package io.keystudio.core.typing

import org.junit.Ignore
import org.junit.Test
import kotlin.random.Random

/**
 * Grid search over [SwipeDecoder.Weights]. Prints a table rather than asserting;
 * run it by hand after changing the decoder:
 *
 *     python tools/kb.py build :core:testDebugUnitTest --tests "*SwipeTuning*" -i
 *
 * (remove the @Ignore first). Scores are top-1 / top-3 percentages.
 */
@Ignore("tuning tool, not a test")
class SwipeTuning {
    @Test
    fun tune() {
        SwipeDecoderTest.load()
        val dict = SwipeDecoderTest.dictionary
        val plain = dict.words.filter { w -> w.length >= 2 && w.all { it in 'a'..'z' } }
        val common = plain.take(300)
        val mid = plain.drop(2500).filterIndexed { i, _ -> i % 10 == 0 }.take(300)
        val decoder = SwipeDecoder(dict)
        fun accuracy(words: List<String>, seed: Int): Pair<Int, Int> {
            val random = Random(seed)
            var top1 = 0
            var top3 = 0
            for (w in words) {
                val result = decoder.decode(SwipeDecoderTest.swipe(w, random), 3).map { it.word.lowercase() }
                if (result.firstOrNull() == w) top1++
                if (w in result) top3++
            }
            return top1 * 100 / words.size to top3 * 100 / words.size
        }
        for (f in listOf(0.15f, 0.25f, 0.35f, 0.45f)) for (g in listOf(1f, 1.6f, 2.5f)) {
            decoder.weights = SwipeDecoder.Weights(
                align = 4f * g, location = 2.4f * g, frequency = f, length = 1.1f * g, endpoint = 1.6f * g,
            )
            val (c1, c3) = accuracy(common, 42)
            val (m1, m3) = accuracy(mid, 43)
            println("f=$f geometry x$g   common $c1/$c3   mid $m1/$m3")
        }
    }
}
