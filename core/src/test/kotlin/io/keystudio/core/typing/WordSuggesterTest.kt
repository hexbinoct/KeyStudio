package io.keystudio.core.typing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class WordSuggesterTest {

    companion object {
        lateinit var suggester: WordSuggester
        lateinit var learned: LearnedWords

        @BeforeClass
        @JvmStatic
        fun load() {
            val dict = File("src/main/assets/dict/en_US.tsv").inputStream().use { Dictionary.load(it) }
            learned = LearnedWords(null)
            suggester = WordSuggester(dict, learned)
        }
    }

    private fun autocorrect(typed: String) = suggester.suggest(typed).autocorrect

    @Test
    fun fixesCommonTypos() {
        assertEquals("the", autocorrect("teh"))
        assertEquals("hello", autocorrect("helo"))
        assertEquals("keyboard", autocorrect("keybaord"))
        assertEquals("because", autocorrect("becuase"))
        assertEquals("people", autocorrect("peopel"))
        // Neighbouring-key slips.
        assertEquals("the", autocorrect("thw"))
        assertEquals("with", autocorrect("wirh"))
    }

    @Test
    fun appliesShortcuts() {
        assertEquals("don't", autocorrect("dont"))
        assertEquals("I'm", autocorrect("im"))
        assertEquals("you're", autocorrect("youre"))
        assertEquals("can't", autocorrect("cant"))
        // As AOSP and Gboard do: "I'll" is far more common than "ill", and a
        // backspace straight after gets "ill" back (and teaches it).
        assertEquals("I'll", autocorrect("ill"))
    }

    @Test
    fun keepsTheUsersCapitalisation() {
        assertEquals("The", autocorrect("Teh"))
        assertEquals("THE", autocorrect("TEH"))
    }

    @Test
    fun neverCorrectsARealWord() {
        for (w in listOf("the", "form", "from", "abrasive", "quay", "its", "ok", "I")) {
            assertNull("'$w' is a word and must be left alone", autocorrect(w))
        }
    }

    @Test
    fun leavesGibberishAloneRatherThanGuessingWildly() {
        assertNull(autocorrect("xqzvw"))
    }

    @Test
    fun learnedWordsStopBeingCorrected() {
        assertEquals("Keystudio is not a dictionary word yet", true, autocorrect("keystudio") != null ||
            !suggester.isValid("keystudio"))
        learned.teach("KeyStudio")
        assertNull(autocorrect("keystudio"))
        assertTrue(suggester.isValid("keystudio"))
        learned.forget("KeyStudio")
    }

    @Test
    fun offersCompletions() {
        val result = suggester.suggest("keyb")
        assertTrue("keyboard among ${result.words}", "keyboard" in result.words)
        val partial = suggester.suggest("beautif")
        assertNull("the start of a likely word is unfinished, not misspelled", partial.autocorrect)
        assertEquals("beautif", partial.words[0])
        assertTrue("beautiful" in partial.words)
    }

    @Test
    fun doubleLetterSlipsAreCheap() {
        assertEquals("until", autocorrect("untill"))
        assertEquals("tomorrow", autocorrect("tommorow"))
    }

    @Test
    fun isFastEnoughToRunPerKeystroke() {
        val words = listOf("t", "th", "tha", "than", "thank", "thanl", "keybaord", "becuase", "xqzvw", "peopel")
        suggester.suggest("warmup")
        val started = System.nanoTime()
        repeat(5) { for (w in words) suggester.suggest(w) }
        val msPer = (System.nanoTime() - started) / 1e6 / (words.size * 5)
        println("suggest: %.2f ms per keystroke".format(msPer))
        assertTrue("suggest took $msPer ms", msPer < 25.0)
    }
}
