package io.keystudio.kb.ime

import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import io.keystudio.core.model.KeyCode
import io.keystudio.core.model.LayerId
import io.keystudio.core.model.TypingSettings
import io.keystudio.core.store.ProfileStore
import io.keystudio.core.typing.Candidate
import io.keystudio.core.typing.GestureTrace
import io.keystudio.core.typing.SuggestionResult
import io.keystudio.core.typing.TypingEngine
import io.keystudio.core.view.KeyAction
import io.keystudio.core.view.KeyboardView
import io.keystudio.core.view.OnKeyActionListener
import io.keystudio.core.view.ShiftState

/**
 * The keyboard service.
 *
 * All drawing, geometry and effects live in [KeyboardView]; this class owns the
 * editing semantics -- what a key press means for the text being edited.
 *
 * The word being typed is held as *composing text*: the editor shows it
 * underlined and it stays replaceable until it is finished, which is what lets
 * the strip swap it for a suggestion, the space bar autocorrect it, and a swipe
 * be exchanged for one of its alternatives.
 */
class KeyStudioInputMethodService : InputMethodService() {

    private companion object {
        /** Two spaces inside this window become ". ". */
        const val DOUBLE_SPACE_WINDOW_MS = 900L
        /**
         * Selection updates arriving this soon after one of our own edits are
         * echoes of it, possibly out of date; only later ones can be the user
         * moving the cursor.
         */
        const val OWN_EDIT_ECHO_MS = 200L
        const val SENTENCE_PUNCTUATION = ".,?!;:"
        /** Characters after which a swiped word needs no leading space. */
        const val NO_SPACE_AFTER = " \n\t([{\"“‘¿¡/@#"
    }

    /** An autocorrection that one backspace should undo. */
    private class Revert(val typed: String, val replacement: String)

    private lateinit var store: ProfileStore
    private lateinit var engine: TypingEngine
    private var settings = TypingSettings()
    private var keyboardView: KeyboardView? = null
    private var profileWatchers: List<FileObserver> = emptyList()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var shiftState = ShiftState.Off
    private var lastShiftTapMs = 0L
    private var lastSpaceMs = 0L
    private var autoCapitalize = true

    // -- word state -----------------------------------------------------------

    /** Suggestions and composing are on for this field. */
    private var predictionOn = false
    private var autocorrectOn = false
    private val composing = StringBuilder()
    /** The composing word came from a swipe: backspace removes it whole. */
    private var composingIsGesture = false
    private var lastResult: SuggestionResult? = null
    /** After a swiped word, the next word starts with an automatic space. */
    private var phantomSpace = false
    /** We put a space after a picked word; punctuation may take its place. */
    private var autoSpaced = false
    private var revert: Revert? = null
    private var lastOwnEditMs = 0L

    /** A swipe is being decoded; key presses wait for it, in order. */
    private var decoding = false
    private val deferred = ArrayDeque<KeyAction>()

    /** Where a backspace swipe started, as an absolute offset; -1 when none. */
    private var deleteAnchor = -1
    private var deleteBefore: CharSequence = ""

    override fun onCreate() {
        super.onCreate()
        store = ProfileStore(this)
        settings = store.loadSettings()
        engine = TypingEngine(this, store.learnedWordsFile)
        startWatchingProfiles()
    }

    /**
     * The Studio runs in a different process, so a theme change reaches the
     * keyboard as a file write. Watching for it is what makes editing feel live
     * rather than "close the keyboard and reopen it to see your change".
     */
    private fun startWatchingProfiles() {
        // DELETE matters as much as the writes: reverting an edited preset is
        // done by removing the user's file, and the keyboard has to notice.
        val mask = FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO or
            FileObserver.MOVED_FROM or FileObserver.CREATE or FileObserver.DELETE
        profileWatchers = store.watchDirs.map { dir ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                object : FileObserver(dir, mask) {
                    override fun onEvent(event: Int, path: String?) = onProfilesChanged(path)
                }
            } else {
                @Suppress("DEPRECATION")
                object : FileObserver(dir.absolutePath, mask) {
                    override fun onEvent(event: Int, path: String?) = onProfilesChanged(path)
                }
            }
        }
        profileWatchers.forEach { it.startWatching() }
    }

    /** Called on the observer's thread; hop to the UI thread before touching views. */
    private fun onProfilesChanged(path: String?) {
        // Our own learned-words file lives in the same directory.
        if (path != null && path.startsWith("learned")) return
        mainHandler.removeCallbacks(reloadProfile)
        // Writes arrive as several events; coalesce them into one reload.
        mainHandler.postDelayed(reloadProfile, 80L)
    }

    private val reloadProfile = Runnable {
        settings = store.loadSettings()
        val view = keyboardView ?: return@Runnable
        applySettings(view)
        val profile = store.activeProfile()
        // The layout editor writes layouts exactly as the theme editor writes
        // profiles, so either may be what changed.
        val layout = store.loadLayout(profile.layoutId)
        if (layout != view.layout) view.layout = layout
        if (profile != view.profile) {
            view.profile = profile
        } else {
            // Same profile id but its contents may have been edited in place.
            view.refresh()
        }
    }

    override fun onDestroy() {
        profileWatchers.forEach { it.stopWatching() }
        profileWatchers = emptyList()
        mainHandler.removeCallbacksAndMessages(null)
        engine.close()
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val view = KeyboardView(this)
        view.profile = store.activeProfile()
        view.layout = store.loadLayout(view.profile.layoutId)
        view.onKeyAction = OnKeyActionListener { handleAction(it) }
        view.onGeometryChanged = { engine.setKeyAnchors(view.letterAnchors()) }
        applySettings(view)
        keyboardView = view
        return view
    }

    private fun applySettings(view: KeyboardView) {
        view.spaceCursorEnabled = settings.spaceCursor
        view.backspaceSwipeEnabled = settings.backspaceSwipe
        view.showGestureTrail = settings.swipeTrail
        view.gesturesEnabled = settings.swipeTyping && swipeAllowed
    }

    private var swipeAllowed = true

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val view = keyboardView ?: return

        // Pick up any edits made in the Studio while the keyboard was hidden.
        settings = store.loadSettings()
        val profile = store.activeProfile()
        if (profile != view.profile) {
            view.profile = profile
            view.layout = store.loadLayout(profile.layoutId)
        }

        resetWordState()
        val textField = info.inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        val secret = variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        val address = variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_URI
        val noSuggestions = info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0

        predictionOn = settings.suggestions && textField && !secret && !address
        autocorrectOn = predictionOn && settings.autocorrect && !noSuggestions
        swipeAllowed = textField && !secret
        applySettings(view)

        autoCapitalize = settings.autoCapitalize && shouldAutoCapitalize(info)
        view.layer = startingLayerFor(info)
        updateShiftFromCursor()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        finishComposing()
        resetWordState()
        setShift(ShiftState.Off)
        engine.save()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        resetWordState()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd,
        )
        val echo = SystemClock.uptimeMillis() - lastOwnEditMs < OWN_EDIT_ECHO_MS
        if (!echo) {
            // The user moved the cursor: whatever word we were building is done
            // with, and so is anything that assumed the cursor was still here.
            val leftTheWord = composing.isNotEmpty() &&
                (candidatesStart < 0 || newSelStart != newSelEnd || newSelEnd != candidatesEnd)
            if (leftTheWord) {
                currentInputConnection?.finishComposingText()
                composing.clear()
                composingIsGesture = false
                showSuggestions(emptyList())
            }
            if (oldSelEnd != newSelEnd) {
                phantomSpace = false
                autoSpaced = false
                revert = null
            }
        }
        if (shiftState != ShiftState.Locked) updateShiftFromCursor()
    }

    // -------------------------------------------------------------------------

    private fun startingLayerFor(info: EditorInfo): LayerId {
        val variation = info.inputType and InputType.TYPE_MASK_CLASS
        return when (variation) {
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE -> LayerId.Numeric
            else -> LayerId.Letters
        }
    }

    private fun shouldAutoCapitalize(info: EditorInfo): Boolean {
        val type = info.inputType
        if (type and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
        val variation = type and InputType.TYPE_MASK_VARIATION
        // Never auto-capitalise things that are case-sensitive identifiers.
        return variation != InputType.TYPE_TEXT_VARIATION_PASSWORD &&
            variation != InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD &&
            variation != InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD &&
            variation != InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS &&
            variation != InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS &&
            variation != InputType.TYPE_TEXT_VARIATION_URI
    }

    /** Asks the editor whether the caret sits at a place that wants a capital. */
    private fun updateShiftFromCursor() {
        if (!autoCapitalize) {
            if (shiftState != ShiftState.Locked) setShift(ShiftState.Off)
            return
        }
        // Mid-word never wants a capital, and the editor cannot see our
        // composing word as a word yet.
        if (composing.isNotEmpty()) {
            if (shiftState == ShiftState.Shifted) setShift(ShiftState.Off)
            return
        }
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo ?: return
        val caps = ic.getCursorCapsMode(info.inputType)
        setShift(if (caps != 0) ShiftState.Shifted else ShiftState.Off)
    }

    private fun setShift(state: ShiftState) {
        shiftState = state
        keyboardView?.shiftState = state
    }

    // -------------------------------------------------------------------------
    // Actions
    // -------------------------------------------------------------------------

    private fun handleAction(action: KeyAction) {
        if (decoding) {
            deferred.addLast(action)
            return
        }
        when (action) {
            is KeyAction.Text -> onText(
                if (action.key.code == KeyCode.Macro) expandMacros(action.text) else action.text,
            )
            is KeyAction.Special -> handleSpecial(action.code)
            is KeyAction.Gesture -> onGesture(action.trace)
            is KeyAction.MoveCursor -> onMoveCursor(action.steps)
            is KeyAction.DeleteWords -> onDeleteWords(action.words, action.done)
            is KeyAction.PickSuggestion -> onPick(action.text)
        }
    }

    /**
     * Macro keys type text with placeholders filled in at the moment of
     * typing, as Keys Cafe's `%TDY` and `%TIME` do.
     */
    private fun expandMacros(text: String): String {
        if ('%' !in text) return text
        val now = java.util.Date()
        var out = text
        if ("%DATE%" in out) {
            out = out.replace("%DATE%", java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(now))
        }
        if ("%TIME%" in out) {
            out = out.replace("%TIME%", android.text.format.DateFormat.getTimeFormat(this).format(now))
        }
        if ("%DAY%" in out) {
            out = out.replace("%DAY%", java.text.SimpleDateFormat("EEEE", java.util.Locale.getDefault()).format(now))
        }
        if ("%CLIPBOARD%" in out) {
            val clip = (getSystemService(CLIPBOARD_SERVICE) as? android.content.ClipboardManager)
                ?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
            out = out.replace("%CLIPBOARD%", clip.orEmpty())
        }
        return out
    }

    private fun ic(): InputConnection? {
        lastOwnEditMs = SystemClock.uptimeMillis()
        return currentInputConnection
    }

    private fun isWordChar(text: String): Boolean =
        text.length == 1 && (text[0].isLetter() || (text[0] == '\'' && composing.isNotEmpty()))

    private fun onText(text: String) {
        val ic = ic() ?: return
        revert = null

        if (predictionOn && isWordChar(text)) {
            if (composingIsGesture) finishComposing()
            if (composing.isEmpty() && phantomSpace && charBeforeCursorIsWordish(ic)) {
                ic.commitText(" ", 1)
            }
            phantomSpace = false
            autoSpaced = false
            composing.append(text)
            ic.setComposingText(composing, 1)
            requestSuggestions()
        } else {
            val punctuation = text.length == 1 && text[0] in SENTENCE_PUNCTUATION
            if (composing.isNotEmpty()) commitComposing(allowAutocorrect = punctuation)
            if (punctuation && autoSpaced && settings.smartPunctuation &&
                ic.getTextBeforeCursor(1, 0) == " "
            ) {
                // "word ." -> "word. ": the space we added belongs after the mark.
                ic.deleteSurroundingText(1, 0)
                ic.commitText("$text ", 1)
                autoSpaced = true
            } else {
                ic.commitText(text, 1)
                autoSpaced = false
            }
            phantomSpace = false
            showSuggestions(emptyList())
        }

        lastSpaceMs = 0L
        // Shift is one-shot unless locked.
        if (shiftState == ShiftState.Shifted) setShift(ShiftState.Off)
        if (shiftState != ShiftState.Locked) updateShiftFromCursor()
    }

    private fun handleSpecial(code: KeyCode) {
        val ic = ic() ?: return
        when (code) {
            KeyCode.Backspace -> handleBackspace(ic)
            KeyCode.Space -> handleSpace(ic)
            KeyCode.Enter -> handleEnter(ic)
            KeyCode.Shift -> handleShift()
            KeyCode.Tab -> {
                finishComposing()
                sendDownUpKeyEvents(KeyEvent.KEYCODE_TAB)
            }

            KeyCode.LayerLetters -> keyboardView?.layer = LayerId.Letters
            KeyCode.LayerSymbols -> keyboardView?.layer = LayerId.Symbols
            KeyCode.LayerSymbolsAlt -> keyboardView?.layer = LayerId.SymbolsAlt
            KeyCode.LayerNumeric -> keyboardView?.layer = LayerId.Numeric

            KeyCode.CursorLeft -> moveBy(KeyEvent.KEYCODE_DPAD_LEFT)
            KeyCode.CursorRight -> moveBy(KeyEvent.KEYCODE_DPAD_RIGHT)
            KeyCode.CursorUp -> moveBy(KeyEvent.KEYCODE_DPAD_UP)
            KeyCode.CursorDown -> moveBy(KeyEvent.KEYCODE_DPAD_DOWN)

            KeyCode.Language -> {
                finishComposing()
                switchToNextInputMethod(false)
            }
            KeyCode.Settings -> openStudio()
            KeyCode.Hide -> requestHideSelf(0)
            KeyCode.Paste -> {
                finishComposing()
                ic.performContextMenuAction(android.R.id.paste)
            }

            KeyCode.Emoji, KeyCode.Text, KeyCode.Macro, KeyCode.Spacer -> Unit
        }
    }

    private fun moveBy(keyCode: Int) {
        finishComposing()
        resetTransientState()
        sendDownUpKeyEvents(keyCode)
    }

    private fun handleBackspace(ic: InputConnection) {
        lastSpaceMs = 0L
        val undo = revert
        revert = null
        if (undo != null && composing.isEmpty()) {
            val expected = undo.replacement + " "
            if (ic.getTextBeforeCursor(expected.length, 0)?.toString() == expected) {
                // Undo the autocorrection: bring the user's own spelling back,
                // still editable, and remember it -- they meant it.
                ic.deleteSurroundingText(expected.length, 0)
                composing.append(undo.typed)
                ic.setComposingText(composing, 1)
                if (settings.learnWords) engine.teach(undo.typed)
                showSuggestions(listOf(undo.typed, undo.replacement))
                return
            }
        }

        if (composing.isNotEmpty()) {
            if (composingIsGesture) {
                // A swiped word goes as it came: whole.
                ic.setComposingText("", 1)
                ic.commitText("", 1)
                composing.clear()
                composingIsGesture = false
                phantomSpace = false
                showSuggestions(emptyList())
            } else {
                val end = composing.length
                val cut = if (end >= 2 && Character.isSurrogatePair(composing[end - 2], composing[end - 1])) 2 else 1
                composing.setLength(end - cut)
                if (composing.isEmpty()) {
                    ic.commitText("", 1)
                    showSuggestions(emptyList())
                } else {
                    ic.setComposingText(composing, 1)
                    requestSuggestions()
                }
            }
        } else {
            val selected = ic.getSelectedText(0)
            if (!selected.isNullOrEmpty()) {
                ic.commitText("", 1)
            } else {
                // Delete by code point so emoji and accented pairs go in one press.
                val before = ic.getTextBeforeCursor(2, 0)
                val count = if (before != null && before.length == 2 &&
                    Character.isSurrogatePair(before[0], before[1])
                ) 2 else 1
                ic.deleteSurroundingText(count, 0)
            }
        }
        phantomSpace = false
        autoSpaced = false
        if (shiftState != ShiftState.Locked) updateShiftFromCursor()
    }

    private fun handleSpace(ic: InputConnection) {
        val now = System.currentTimeMillis()
        phantomSpace = false
        autoSpaced = false

        if (composing.isNotEmpty()) {
            commitComposing(allowAutocorrect = true)
            ic.commitText(" ", 1)
            lastSpaceMs = now
            showSuggestions(emptyList())
            if (shiftState == ShiftState.Shifted) setShift(ShiftState.Off)
            if (shiftState != ShiftState.Locked) updateShiftFromCursor()
            return
        }
        revert = null

        val isDoubleSpace = settings.doubleSpacePeriod && now - lastSpaceMs < DOUBLE_SPACE_WINDOW_MS
        if (isDoubleSpace) {
            val before = ic.getTextBeforeCursor(2, 0)
            // "ab " + space -> "ab. ", but never "a. " + space -> "a.. "
            if (before != null && before.length == 2 &&
                before[1] == ' ' && before[0].isLetterOrDigit()
            ) {
                ic.deleteSurroundingText(1, 0)
                ic.commitText(". ", 1)
                lastSpaceMs = 0L
                if (shiftState != ShiftState.Locked) updateShiftFromCursor()
                return
            }
        }
        ic.commitText(" ", 1)
        lastSpaceMs = now
        if (shiftState == ShiftState.Shifted) setShift(ShiftState.Off)
        if (shiftState != ShiftState.Locked) updateShiftFromCursor()
    }

    private fun handleEnter(ic: InputConnection) {
        if (composing.isNotEmpty()) commitComposing(allowAutocorrect = true)
        resetTransientState()
        showSuggestions(emptyList())
        val info = currentInputEditorInfo
        val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        val hasNoEnterAction = info?.imeOptions?.and(EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0

        if (action != EditorInfo.IME_ACTION_NONE && !hasNoEnterAction) {
            ic.performEditorAction(action)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
        if (shiftState != ShiftState.Locked) updateShiftFromCursor()
    }

    /** Tap shifts; a second tap within the double-tap window locks caps. */
    private fun handleShift() {
        val now = System.currentTimeMillis()
        val doubleTap = now - lastShiftTapMs < 350L
        lastShiftTapMs = now

        setShift(
            when {
                shiftState == ShiftState.Locked -> ShiftState.Off
                doubleTap -> ShiftState.Locked
                shiftState == ShiftState.Shifted -> ShiftState.Off
                else -> ShiftState.Shifted
            }
        )
    }

    // -------------------------------------------------------------------------
    // Words
    // -------------------------------------------------------------------------

    private fun requestSuggestions() {
        if (!predictionOn) return
        val typed = composing.toString()
        engine.suggest(typed, autocorrectOn) { result ->
            if (result.typed != composing.toString() || composingIsGesture) return@suggest
            lastResult = result
            showSuggestions(result.words)
        }
    }

    private fun showSuggestions(words: List<String>) {
        if (words.isEmpty()) engine.cancelSuggestions()
        keyboardView?.setSuggestions(words)
    }

    /**
     * Ends the composing word, autocorrecting it first when allowed and due.
     * The caller adds whatever follows (space, punctuation).
     */
    private fun commitComposing(allowAutocorrect: Boolean) {
        val ic = ic() ?: return
        val typed = composing.toString()
        if (allowAutocorrect && autocorrectOn && !composingIsGesture) {
            val result = lastResult?.takeIf { it.typed == typed } ?: engine.suggestNow(typed, true)
            val fix = result?.autocorrect
            if (fix != null && fix != typed) {
                ic.commitText(fix, 1)
                revert = Revert(typed, fix)
                composing.clear()
                lastResult = null
                return
            }
        }
        ic.finishComposingText()
        if (settings.learnWords && !composingIsGesture) engine.used(typed)
        composing.clear()
        composingIsGesture = false
        lastResult = null
    }

    /** Leaves the composing word exactly as it is. */
    private fun finishComposing() {
        if (composing.isEmpty()) return
        commitComposing(allowAutocorrect = false)
    }

    private fun onPick(word: String) {
        val ic = ic() ?: return
        if (composing.isEmpty()) {
            showSuggestions(emptyList())
            return
        }
        val typed = composing.toString()
        if (composingIsGesture) {
            // Swapping a swiped word for an alternative keeps swipe spacing.
            ic.setComposingText(word, 1)
            ic.finishComposingText()
            phantomSpace = true
            autoSpaced = false
        } else {
            ic.commitText("$word ", 1)
            autoSpaced = true
            phantomSpace = false
            if (settings.learnWords) {
                if (word == typed && !engine.isValid(typed)) engine.teach(typed) else engine.used(word)
            }
        }
        composing.clear()
        composingIsGesture = false
        lastResult = null
        revert = null
        showSuggestions(emptyList())
        if (shiftState == ShiftState.Shifted) setShift(ShiftState.Off)
        if (shiftState != ShiftState.Locked) updateShiftFromCursor()
    }

    private fun onGesture(trace: GestureTrace) {
        if (!settings.swipeTyping) return
        decoding = true
        engine.decode(trace) { words ->
            decoding = false
            applyGesture(words)
            // Keys tapped while the swipe was decoding, in the order they came.
            while (!decoding && deferred.isNotEmpty()) handleAction(deferred.removeFirst())
        }
    }

    private fun applyGesture(candidates: List<Candidate>) {
        if (candidates.isEmpty()) return
        val ic = ic() ?: return
        revert = null

        if (composing.isNotEmpty()) commitComposing(allowAutocorrect = !composingIsGesture)
        val before = ic.getTextBeforeCursor(1, 0)
        if (!before.isNullOrEmpty() && before[0] !in NO_SPACE_AFTER) ic.commitText(" ", 1)

        val shaped = candidates.map { shapeForShift(it.word) }.distinct()
        val word = shaped.first()
        if (predictionOn) {
            composing.append(word)
            composingIsGesture = true
            ic.setComposingText(word, 1)
            showSuggestions(shaped)
        } else {
            ic.commitText(word, 1)
        }
        phantomSpace = true
        autoSpaced = false
        lastSpaceMs = 0L
        if (shiftState == ShiftState.Shifted) setShift(ShiftState.Off)
    }

    private fun shapeForShift(word: String): String = when (shiftState) {
        ShiftState.Locked -> word.uppercase()
        ShiftState.Shifted -> word.replaceFirstChar { it.uppercaseChar() }
        ShiftState.Off -> word
    }

    private fun charBeforeCursorIsWordish(ic: InputConnection): Boolean {
        val before = ic.getTextBeforeCursor(1, 0)
        return !before.isNullOrEmpty() && before[0].isLetterOrDigit()
    }

    // -------------------------------------------------------------------------
    // Slides
    // -------------------------------------------------------------------------

    private fun onMoveCursor(steps: Int) {
        if (steps == 0) return
        finishComposing()
        resetTransientState()
        showSuggestions(emptyList())
        val key = if (steps < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        repeat(kotlin.math.abs(steps)) { sendDownUpKeyEvents(key) }
    }

    /**
     * Backspace swipe. Selects [words] whole words back from where the swipe
     * began, so the user sees exactly what will go; deletes them on release.
     */
    private fun onDeleteWords(words: Int, done: Boolean) {
        val ic = ic() ?: return
        if (deleteAnchor < 0) {
            finishComposing()
            resetTransientState()
            showSuggestions(emptyList())
            val extracted = ic.getExtractedText(ExtractedTextRequest(), 0) ?: return
            deleteAnchor = extracted.startOffset + extracted.selectionEnd
            deleteBefore = ic.getTextBeforeCursor(1000, 0) ?: ""
        }
        val anchor = deleteAnchor
        if (done) {
            if (words > 0) ic.commitText("", 1) else ic.setSelection(anchor, anchor)
            deleteAnchor = -1
            if (shiftState != ShiftState.Locked) updateShiftFromCursor()
            return
        }
        val span = spanOfWordsBefore(deleteBefore, words)
        ic.setSelection(anchor - span, anchor)
    }

    /** Characters covered by the last [words] words of [text], with their spaces. */
    private fun spanOfWordsBefore(text: CharSequence, words: Int): Int {
        var i = text.length
        repeat(words) {
            while (i > 0 && text[i - 1].isWhitespace()) i--
            if (i > 0 && !isWordPart(text[i - 1])) {
                while (i > 0 && !isWordPart(text[i - 1]) && !text[i - 1].isWhitespace()) i--
            } else {
                while (i > 0 && isWordPart(text[i - 1])) i--
            }
        }
        return text.length - i
    }

    private fun isWordPart(c: Char) = c.isLetterOrDigit() || c == '\'' || c == '’'

    // -------------------------------------------------------------------------

    private fun resetTransientState() {
        phantomSpace = false
        autoSpaced = false
        revert = null
        lastSpaceMs = 0L
    }

    private fun resetWordState() {
        composing.clear()
        composingIsGesture = false
        lastResult = null
        decoding = false
        deferred.clear()
        deleteAnchor = -1
        resetTransientState()
        engine.cancelSuggestions()
        keyboardView?.setSuggestions(emptyList())
    }

    private fun openStudio() {
        finishComposing()
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }
}
