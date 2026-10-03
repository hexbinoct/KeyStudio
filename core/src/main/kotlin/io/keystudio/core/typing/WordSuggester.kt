package io.keystudio.core.typing

import kotlin.math.hypot

/**
 * What the strip should show for the word being typed.
 *
 * [words] is best-first; `words[0]` is what the space bar will commit, and is
 * [autocorrect] when a correction is due. [typedIsValid] tells the service the
 * typed word is a real word, so leaving it alone is safe.
 */
class SuggestionResult(
    val typed: String,
    val words: List<String>,
    val autocorrect: String?,
    val typedIsValid: Boolean,
) {
    companion object {
        val EMPTY = SuggestionResult("", emptyList(), null, false)
    }
}

/**
 * Completions and corrections for tap typing.
 *
 * Corrections use an edit distance whose substitution cost depends on how far
 * apart the two keys actually are on screen -- hitting `n` for `m` is cheap,
 * `p` for `m` is not -- so it follows the user's own layout if they resize or
 * move keys.
 *
 * Autocorrect is deliberately conservative. A keyboard that "fixes" real words
 * is worse than one that fixes nothing: it only ever replaces a word that is in
 * neither the dictionary nor the user's learned words, and only with something
 * close by.
 */
class WordSuggester(
    private val dictionary: Dictionary,
    private val learned: LearnedWords,
) {
    private companion object {
        /** How many of the most frequent words corrections are drawn from. */
        const val CORRECTION_VOCABULARY = 80_000
        const val MAX_COST = 2.0f
        const val COST_WEIGHT = 4.2f
        const val INSERT_COST = 1.0f
        const val DELETE_COST = 1.0f
        const val TRANSPOSE_COST = 0.75f
        /** Missing or doubling a repeated letter -- "helo", "untill" -- is the commonest slip. */
        const val DOUBLE_LETTER_COST = 0.15f
        const val MAX_WORD = 32
    }

    /**
     * Correction vocabulary indexed by first letter, then key length. People
     * rarely miss the first letter of a word by more than a neighbouring key,
     * so a search only visits a handful of first letters -- a fraction of the
     * work of scanning every word of the right length.
     */
    private val byFirstAndLength: Array<Array<IntArray>> = run {
        val lists = Array(27) { Array(MAX_WORD + 1) { ArrayList<Int>() } }
        val n = minOf(dictionary.size, CORRECTION_VOCABULARY)
        for (i in 0 until n) {
            val k = dictionary.keys[i]
            if (k.length in 1..MAX_WORD && !dictionary.offensive[i]) lists[slot(k[0])][k.length].add(i)
        }
        Array(27) { f -> Array(MAX_WORD + 1) { l -> lists[f][l].toIntArray() } }
    }

    private fun slot(c: Char): Int = if (c in 'a'..'z') c - 'a' else 26

    /** Lowercase word -> index, for "is exactly this word valid". */
    private val lowerIndex: HashMap<String, Int> = HashMap<String, Int>(dictionary.size * 2).apply {
        for (i in 0 until dictionary.size) putIfAbsent(dictionary.words[i].lowercase(), i)
    }

    /** Key positions for the proximity model, in key units. */
    private var keyX = FloatArray(128) { Float.NaN }
    private var keyY = FloatArray(128) { Float.NaN }

    init {
        // Plain QWERTY until the keyboard reports its real geometry.
        setKeyAnchors(
            buildList {
                listOf("qwertyuiop" to 0f, "asdfghjkl" to 0.5f, "zxcvbnm" to 1.5f)
                    .forEachIndexed { row, (chars, offset) ->
                        chars.forEachIndexed { i, c -> add(KeyAnchor(c, offset + i + 0.5f, row + 0.5f, 1f, 1f)) }
                    }
            },
        )
    }

    /** Tells the corrector where the keys are. Call when the layout changes. */
    fun setKeyAnchors(anchors: List<KeyAnchor>) {
        if (anchors.isEmpty()) return
        val kw = anchors.map { it.width }.sorted()[anchors.size / 2].coerceAtLeast(1f)
        val kh = anchors.map { it.height }.sorted()[anchors.size / 2].coerceAtLeast(1f)
        val x = FloatArray(128) { Float.NaN }
        val y = FloatArray(128) { Float.NaN }
        for (a in anchors) if (a.char.code < 128) {
            x[a.char.code] = a.x / kw
            y[a.char.code] = a.y / kh
        }
        keyX = x
        keyY = y
    }

    fun isValid(word: String): Boolean {
        val lower = word.lowercase()
        return lowerIndex.containsKey(lower) || learned.isKnown(lower)
    }

    fun suggest(typed: String, limit: Int = 3, allowAutocorrect: Boolean = true): SuggestionResult {
        if (typed.isEmpty()) return SuggestionResult.EMPTY
        val key = Dictionary.keyOf(typed)
        val lower = typed.lowercase()
        val valid = isValid(typed)

        // Scored candidates: word -> score, higher is better.
        val scored = LinkedHashMap<String, Float>()
        var bestCompletion = Float.NEGATIVE_INFINITY
        fun offer(word: String, score: Float) {
            val prev = scored[word]
            if (prev == null || score > prev) scored[word] = score
        }

        if (key.isNotEmpty()) {
            // Completions: longer words beginning with what was typed.
            for (i in dictionary.complete(typed, 6)) {
                val w = dictionary.words[i]
                if (w.equals(typed, ignoreCase = true)) continue
                // A completion saves keystrokes but is a guess about the rest of
                // the word, so it pays a little per missing letter.
                val missing = dictionary.keys[i].length - key.length
                val score = dictionary.logProb(i) - 0.9f * missing
                offer(w, score)
                if (score > bestCompletion) bestCompletion = score
            }
            for (w in learned.complete(lower, 3)) {
                if (!w.equals(typed, ignoreCase = true)) offer(w, -10f)
            }
        }

        var correction: String? = null
        var correctionScore = Float.NEGATIVE_INFINITY
        val shortcut = dictionary.shortcuts[lower]
        val sameKey = dictionary.indexOfKey(key)
        if (shortcut != null && (!valid || isApostropheFix(lower, shortcut))) {
            correction = shortcut
            correctionScore = 0f
        } else if (!valid && sameKey >= 0 && '\'' !in typed) {
            // Typed without the apostrophe: "dont", "youre", "im".
            correction = dictionary.words[sameKey]
            correctionScore = 0f
        } else if (!valid && key.length >= 2 && key.length == typed.count { it.isLetter() }) {
            for ((i, cost) in corrections(key)) {
                val w = dictionary.words[i]
                val score = dictionary.logProb(i) - COST_WEIGHT * cost
                offer(w, score)
                // Short words get only one edit; there are too many neighbours.
                val allowed = if (key.length <= 3) 1.0f else if (key.length <= 5) 1.5f else MAX_COST
                if (cost <= allowed && score > correctionScore) {
                    correction = w
                    correctionScore = score
                }
            }
        }

        val ranked = scored.entries.sortedByDescending { it.value }.map { it.key }
        val shaped = ranked.map { matchCase(it, typed) }
        // Something typed that is the start of a likelier word is unfinished,
        // not misspelled: leave it for the user to finish.
        val unfinished = correctionScore < 0f && bestCompletion >= correctionScore
        val autocorrect = if (allowAutocorrect && correction != null && !unfinished &&
            (!valid || correctionScore == 0f)
        ) matchCase(correction, typed) else null

        val words = ArrayList<String>(limit)
        if (autocorrect != null) {
            words += autocorrect
            words += typed
        } else {
            words += typed
        }
        for (w in shaped) {
            if (words.size >= limit) break
            if (words.none { it == w }) words += w
        }
        return SuggestionResult(typed, words, autocorrect, valid)
    }

    /**
     * "cant" -> "can't" is right even though "cant" is a word; "ill" -> "I'll"
     * is not always ("I feel ill"). Only take an apostrophe shortcut over a
     * real word when the contraction is far more common than the word.
     */
    private fun isApostropheFix(typed: String, target: String): Boolean {
        if (Dictionary.keyOf(target) != typed) return false
        val typedIndex = lowerIndex[typed] ?: return true
        val targetIndex = dictionary.indexOfKey(typed).takeIf { it >= 0 && dictionary.words[it] == target }
            ?: lowerIndex[target.lowercase()] ?: return false
        return dictionary.freqs[targetIndex] >= dictionary.freqs[typedIndex] + 40
    }

    /** Words within [MAX_COST] of [key], with their cost. */
    private fun corrections(key: String): List<Pair<Int, Float>> {
        val out = ArrayList<Pair<Int, Float>>()
        val len = key.length
        val firsts = BooleanArray(27)
        firsts[slot(key[0])] = true
        // "hte" is "the": a transposed or dropped first letter starts with the second.
        firsts[slot(key[1])] = true
        val fx = if (key[0].code < 128) keyX[key[0].code] else Float.NaN
        if (!fx.isNaN()) {
            for (c in 'a'..'z') {
                val cx = keyX[c.code]
                if (!cx.isNaN() && hypot(cx - fx, keyY[c.code] - keyY[key[0].code]) < 1.2f) {
                    firsts[slot(c)] = true
                }
            }
        }
        for (f in 0 until 27) {
            if (!firsts[f]) continue
            for (l in (len - 2).coerceAtLeast(1)..(len + 2).coerceAtMost(MAX_WORD)) {
                for (i in byFirstAndLength[f][l]) {
                    val cost = distance(key, dictionary.keys[i], MAX_COST)
                    if (cost <= MAX_COST) out += i to cost
                }
            }
        }
        return out
    }

    private val rowPrev = FloatArray(MAX_WORD + 2)
    private val rowCur = FloatArray(MAX_WORD + 2)
    private val rowPrev2 = FloatArray(MAX_WORD + 2)

    /**
     * Damerau-Levenshtein with keyboard-aware substitution, abandoning early
     * once every path costs more than [limit].
     */
    private fun distance(a: String, b: String, limit: Float): Float {
        val n = a.length
        val m = b.length
        if (m > MAX_WORD || n > MAX_WORD) return Float.MAX_VALUE
        for (j in 0..m) rowPrev[j] = j * INSERT_COST
        for (i in 1..n) {
            rowCur[0] = i * DELETE_COST
            var rowMin = rowCur[0]
            val ca = a[i - 1]
            for (j in 1..m) {
                val cb = b[j - 1]
                var best = rowPrev[j - 1] + substitution(ca, cb)
                // Deleting a typed letter that repeats the one before it ("untill").
                val del = rowPrev[j] + if (i > 1 && a[i - 2] == ca) DOUBLE_LETTER_COST else DELETE_COST
                if (del < best) best = del
                // Supplying a letter that doubles the previous one ("helo" -> "hello").
                val ins = rowCur[j - 1] + if (j > 1 && b[j - 2] == cb) DOUBLE_LETTER_COST else INSERT_COST
                if (ins < best) best = ins
                if (i > 1 && j > 1 && ca == b[j - 2] && a[i - 2] == cb) {
                    val t = rowPrev2[j - 2] + TRANSPOSE_COST
                    if (t < best) best = t
                }
                rowCur[j] = best
                if (best < rowMin) rowMin = best
            }
            if (rowMin > limit) return Float.MAX_VALUE
            System.arraycopy(rowPrev, 0, rowPrev2, 0, m + 1)
            System.arraycopy(rowCur, 0, rowPrev, 0, m + 1)
        }
        return rowPrev[m]
    }

    /**
     * 0 for the same key, about 0.75 for a neighbour, 1 for anything further.
     *
     * Neighbour slips are the commonest error overall, but any *particular*
     * neighbour is not: a missed double letter ("helo") is far likelier than
     * `o` struck for `p`, which is what lets "hello" beat the more frequent
     * "help" there.
     */
    private fun substitution(a: Char, b: Char): Float {
        if (a == b) return 0f
        val ax = if (a.code < 128) keyX[a.code] else Float.NaN
        val bx = if (b.code < 128) keyX[b.code] else Float.NaN
        if (ax.isNaN() || bx.isNaN()) return 1f
        val d = hypot(ax - bx, keyY[a.code] - keyY[b.code])
        return (0.35f + 0.4f * d).coerceAtMost(1f)
    }

    /** Gives [word] the capitalisation the user typed: "Teh" -> "The", "TEH" -> "THE". */
    private fun matchCase(word: String, typed: String): String {
        if (typed.isEmpty() || word.isEmpty()) return word
        val letters = typed.filter { it.isLetter() }
        return when {
            letters.length > 1 && letters.all { it.isUpperCase() } -> word.uppercase()
            typed[0].isUpperCase() -> word.replaceFirstChar { it.uppercaseChar() }
            else -> word
        }
    }
}
