package io.keystudio.core.typing

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * The word list behind suggestions, autocorrect and swipe typing.
 *
 * Loaded once from `assets/dict/en_US.tsv` (see `tools/build_dict.py`) into flat
 * arrays, best word first. Every lookup the keyboard makes is served from two
 * indexes built at load time:
 *
 * - words sorted by their *key* -- lowercase letters, apostrophes dropped -- for
 *   prefix completion by binary search;
 * - words bucketed by the first and last letter of their key, which is exactly
 *   the pruning a swipe needs: a swipe starts on its first letter and ends on
 *   its last.
 *
 * Construction reads the whole file, so do it off the main thread.
 */
class Dictionary private constructor(
    /** As they should be typed: "I'm", "London", "the". Index 0 is the most common. */
    val words: Array<String>,
    /** Matching form: lowercase, letters only. "I'm" -> "im". */
    val keys: Array<String>,
    /** AOSP's 0..255 log-scale frequency. */
    val freqs: IntArray,
    /** Valid, so never corrected away, but never offered either. */
    val offensive: BooleanArray,
    /** "dont" -> "don't". Typed forms that should always become something else. */
    val shortcuts: Map<String, String>,
) {
    val size: Int get() = words.size

    /** Word indices sorted by key, for prefix ranges. */
    private val byKey: IntArray = (0 until size).sortedWith(compareBy<Int> { keys[it] }.thenBy { it })
        .toIntArray()

    /** First-and-last-letter buckets; see [candidatesFor]. */
    private val buckets: Array<IntArray>

    /** Exact key -> most frequent word index with that key. */
    private val exact = HashMap<String, Int>(size * 2)

    init {
        val lists = Array(27 * 27) { ArrayList<Int>() }
        for (i in 0 until size) {
            val k = keys[i]
            if (k.isEmpty()) continue
            lists[bucketOf(k[0], k[k.length - 1])].add(i)
            exact.putIfAbsent(k, i)
        }
        buckets = Array(lists.size) { lists[it].toIntArray() }
    }

    /** Natural-log probability, roughly: one order of magnitude per 30 frequency steps. */
    fun logProb(index: Int): Float = (freqs[index] - 255) * LOG_PER_STEP

    /** The most frequent word typed as [key], or -1. */
    fun indexOfKey(key: String): Int = exact[key] ?: -1

    fun contains(word: String): Boolean = indexOfKey(keyOf(word)) >= 0

    /** Indices of every word whose key starts with [first] and ends with [last]. */
    fun candidatesFor(first: Char, last: Char): IntArray = buckets[bucketOf(first, last)]

    /**
     * Up to [limit] words starting with [prefix], most frequent first. Offensive
     * words are skipped unless typed out in full.
     */
    fun complete(prefix: String, limit: Int): List<Int> {
        val key = keyOf(prefix)
        if (key.isEmpty()) return emptyList()
        var lo = lowerBound(key)
        val best = IntArray(limit) { -1 }
        while (lo < byKey.size) {
            val i = byKey[lo]
            if (!keys[i].startsWith(key)) break
            if (!offensive[i] || keys[i] == key) insertBest(best, i)
            lo++
        }
        return best.filter { it >= 0 }
    }

    /** Keeps [best] sorted by index, i.e. by frequency, since the file is sorted so. */
    private fun insertBest(best: IntArray, candidate: Int) {
        for (slot in best.indices) {
            val current = best[slot]
            if (current < 0 || candidate < current) {
                System.arraycopy(best, slot, best, slot + 1, best.size - slot - 1)
                best[slot] = candidate
                return
            }
        }
    }

    private fun lowerBound(key: String): Int {
        var lo = 0
        var hi = byKey.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (keys[byKey[mid]] < key) lo = mid + 1 else hi = mid
        }
        return lo
    }

    companion object {
        private const val LOG_PER_STEP = 0.0768f   // ln(10) / 30

        fun keyOf(word: String): String {
            var plain = true
            for (c in word) if (c !in 'a'..'z') { plain = false; break }
            if (plain) return word
            val sb = StringBuilder(word.length)
            for (c in word) if (c.isLetter()) sb.append(c.lowercaseChar())
            return sb.toString()
        }

        private fun bucketOf(first: Char, last: Char): Int = slot(first) * 27 + slot(last)

        private fun slot(c: Char): Int = if (c in 'a'..'z') c - 'a' else 26

        fun load(input: InputStream): Dictionary {
            val words = ArrayList<String>(170_000)
            val freqs = ArrayList<Int>(170_000)
            val offensive = ArrayList<Boolean>(170_000)
            val shortcuts = HashMap<String, String>()
            BufferedReader(InputStreamReader(input, Charsets.UTF_8), 1 shl 16).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) continue
                    val tab = line.indexOf('\t')
                    if (tab <= 0) continue
                    if (line[0] == '@') {
                        shortcuts[line.substring(1, tab)] = line.substring(tab + 1)
                        continue
                    }
                    val tab2 = line.indexOf('\t', tab + 1)
                    words.add(line.substring(0, tab))
                    freqs.add(line.substring(tab + 1, if (tab2 < 0) line.length else tab2).toInt())
                    offensive.add(tab2 >= 0 && line.endsWith("o"))
                }
            }
            val wordArray = words.toTypedArray()
            return Dictionary(
                words = wordArray,
                // Share the String when the key is the word itself, which is most of them.
                keys = Array(wordArray.size) { keyOf(wordArray[it]) },
                freqs = freqs.toIntArray(),
                offensive = offensive.toBooleanArray(),
                shortcuts = shortcuts,
            )
        }
    }
}
