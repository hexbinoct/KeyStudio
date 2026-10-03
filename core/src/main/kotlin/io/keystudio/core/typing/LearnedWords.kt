package io.keystudio.core.typing

import java.io.File

/**
 * Words the user has taught the keyboard: names, slang, anything the
 * dictionary lacks.
 *
 * A word is learned by being used, not by being typed once -- a typo committed
 * once should not become permanently "valid" and stop being corrected. So a
 * word counts as known after it has been committed twice, or immediately when
 * the user makes their intent explicit: tapping the typed word in the strip, or
 * backspacing over an autocorrection to get their own spelling back.
 *
 * Stored as `word<TAB>count` lines. Thread-safe; the keyboard reads it from its
 * suggestion thread and writes to it from the main thread.
 */
class LearnedWords(private val file: File?) {

    private companion object {
        const val KNOWN_AT = 2
        const val MAX_WORDS = 5000
    }

    private val counts = HashMap<String, Int>()
    /** Case as the user typed it, keyed by lowercase. */
    private val spelling = HashMap<String, String>()
    private var dirty = false

    init {
        file?.takeIf { it.exists() }?.forEachLine { line ->
            val tab = line.indexOf('\t')
            if (tab > 0) {
                val word = line.substring(0, tab)
                val count = line.substring(tab + 1).toIntOrNull() ?: 1
                counts[word.lowercase()] = count
                spelling[word.lowercase()] = word
            }
        }
    }

    @Synchronized
    fun isKnown(lower: String): Boolean = (counts[lower] ?: 0) >= KNOWN_AT

    /** The user committed [word]; count it. */
    @Synchronized
    fun use(word: String) = bump(word, 1)

    /** The user insisted on [word]; learn it now. */
    @Synchronized
    fun teach(word: String) = bump(word, KNOWN_AT)

    @Synchronized
    fun forget(word: String) {
        val lower = word.lowercase()
        if (counts.remove(lower) != null) {
            spelling.remove(lower)
            dirty = true
        }
    }

    private fun bump(word: String, by: Int) {
        if (word.length < 2 || word.length > 32 || !word.any { it.isLetter() }) return
        val lower = word.lowercase()
        counts[lower] = (counts[lower] ?: 0) + by
        spelling.putIfAbsent(lower, word)
        dirty = true
        if (counts.size > MAX_WORDS) {
            // Drop the least used; a runaway list slows every lookup.
            counts.entries.sortedBy { it.value }.take(counts.size - MAX_WORDS).forEach {
                counts.remove(it.key)
                spelling.remove(it.key)
            }
        }
    }

    @Synchronized
    fun known(): List<String> = counts.filter { it.value >= KNOWN_AT }.keys.map { spelling[it] ?: it }

    @Synchronized
    fun complete(prefix: String, limit: Int): List<String> = counts.entries
        .asSequence()
        .filter { it.value >= KNOWN_AT && it.key.startsWith(prefix) && it.key != prefix }
        .sortedByDescending { it.value }
        .take(limit)
        .map { spelling[it.key] ?: it.key }
        .toList()

    @Synchronized
    fun save() {
        val target = file ?: return
        if (!dirty) return
        dirty = false
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(counts.entries.joinToString("\n") { "${spelling[it.key] ?: it.key}\t${it.value}" })
        if (!tmp.renameTo(target)) {
            target.writeText(tmp.readText())
            tmp.delete()
        }
    }
}
