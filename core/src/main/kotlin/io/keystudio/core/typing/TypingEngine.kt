package io.keystudio.core.typing

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The keyboard's language side, off the main thread.
 *
 * Loading 160,000 words takes long enough to drop frames, and a correction
 * search is a few milliseconds of work per keystroke, so everything here runs
 * on one background thread. One thread, not a pool, because the decoder and
 * suggester keep scratch buffers and are not re-entrant -- serialising them is
 * simpler and just as fast.
 *
 * Until the dictionary has loaded, every query returns nothing; the keyboard
 * still types, it just does not suggest.
 */
class TypingEngine(context: Context, learnedFile: File) {

    private val appContext = context.applicationContext
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "keystudio-typing").apply { priority = Thread.NORM_PRIORITY - 1 }
    }
    private val main = Handler(Looper.getMainLooper())
    private val generation = AtomicInteger()

    val learned = LearnedWords(learnedFile)

    @Volatile
    private var suggester: WordSuggester? = null
    private var decoder: SwipeDecoder? = null
    private var pendingAnchors: List<KeyAnchor>? = null

    val isReady: Boolean get() = suggester != null

    init {
        executor.execute {
            val started = System.nanoTime()
            val dictionary = runCatching {
                appContext.assets.open("dict/en_US.tsv").use { Dictionary.load(it) }
            }.onFailure { Log.e(TAG, "dictionary failed to load", it) }.getOrNull() ?: return@execute
            val s = WordSuggester(dictionary, learned)
            pendingAnchors?.let { s.setKeyAnchors(it) }
            decoder = SwipeDecoder(dictionary).also { it.extraWords = learned.known() }
            suggester = s
            Log.i(TAG, "dictionary: ${dictionary.size} words in ${(System.nanoTime() - started) / 1_000_000} ms")
        }
    }

    /** Where the letters are, for the correction model. */
    fun setKeyAnchors(anchors: List<KeyAnchor>) {
        if (anchors.isEmpty()) return
        executor.execute {
            val s = suggester
            if (s != null) s.setKeyAnchors(anchors) else pendingAnchors = anchors
        }
    }

    /**
     * Suggestions for [typed], delivered on the main thread. A newer request
     * supersedes an older one: only the latest result is ever delivered.
     */
    fun suggest(typed: String, autocorrect: Boolean, callback: (SuggestionResult) -> Unit) {
        val ticket = generation.incrementAndGet()
        executor.execute {
            val result = suggester?.suggest(typed, allowAutocorrect = autocorrect) ?: return@execute
            main.post { if (generation.get() == ticket) callback(result) }
        }
    }

    /** Drops any suggestion still in flight. */
    fun cancelSuggestions() {
        generation.incrementAndGet()
    }

    /**
     * The same, but now: used when the space bar needs a decision and the
     * background result for the latest letter has not arrived yet. Waits at
     * most a few frames; a late answer is treated as no answer.
     */
    fun suggestNow(typed: String, autocorrect: Boolean): SuggestionResult? = runCatching {
        executor.submit<SuggestionResult?> { suggester?.suggest(typed, allowAutocorrect = autocorrect) }
            .get(120, TimeUnit.MILLISECONDS)
    }.getOrNull()

    /** Decodes a swipe; [callback] runs on the main thread, possibly with no words. */
    fun decode(trace: GestureTrace, callback: (List<Candidate>) -> Unit) {
        executor.execute {
            val started = System.nanoTime()
            val words = decoder?.decode(trace, limit = 5).orEmpty()
            Log.d(TAG, "swipe decoded in ${(System.nanoTime() - started) / 1_000_000} ms: $words")
            main.post { callback(words) }
        }
    }

    fun isValid(word: String): Boolean = suggester?.isValid(word) ?: true

    /** The user committed [word]; counts towards learning it. */
    fun used(word: String) {
        if (suggester?.isValid(word) == false) learned.use(word)
        refreshLearned()
    }

    /** The user insisted on [word]. */
    fun teach(word: String) {
        learned.teach(word)
        refreshLearned()
    }

    private fun refreshLearned() {
        executor.execute { decoder?.extraWords = learned.known() }
    }

    fun save() = executor.execute { learned.save() }

    fun close() {
        executor.execute { learned.save() }
        executor.shutdown()
    }

    private companion object {
        const val TAG = "KeyStudio"
    }
}
