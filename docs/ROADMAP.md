# Roadmap

Ordered style-first, which is the half of Keys Cafe that made it worth copying.
Each milestone is meant to leave the keyboard daily-usable, not half-broken.

## M0 -- It is my keyboard  ✅ DONE

- Gradle/AGP 9 project, `:core` + `:app`, one-script dev harness.
- Registered IME that types: shift and caps lock, auto-capitalisation,
  double-space-to-period, long-press accent popups, key repeat, symbol and
  numeric layers, globe key to switch keyboards.
- Data-driven layout engine with weight-based sizing and nearest-key hit
  testing.
- Renderer: six key shapes, solid/linear/radial fills, per-key overrides,
  strokes, shadows, vector icons, hint glyphs, optical text centring.
- Effects: spring key motion, key colour effects, 9 particle types.
- Animated backgrounds (aurora, drift, bubbles, snow, mesh).
- Seven preset themes; Studio app with setup flow, theme picker and a live
  preview that is the real keyboard view.

## M1 -- The theme editor  ✅ DONE

The Keys Cafe "Style your own keyboard" screen, for real.

- Colour picker (HSV field + hue/alpha bars + hex) wired to every `ColorSpec`
  slot, solid or gradient, editing live as the finger moves.
- Key shape, corner radius, inset, shadow; height, gaps, padding, width.
- Font family, weight and size scale; user font files.
- Background editor: solid, gradient, image from gallery, animated.
- Separate light and dark variants with a sun toggle, as Keys Cafe does.
- Save / duplicate / rename / delete profiles; export and import as JSON.
- Undo and redo, collapsed per gesture rather than per frame.

M2's effects and sound controls were built alongside it, since the editor
needed all four tabs to match the Keys Cafe shape. What remains of M2 is the
part that needs assets and tuning rather than UI.

## M2 -- Effects and sound

- ✅ Key colour, key motion and particle effects chosen independently.
- ✅ Accent colour and multi-colour particle palettes.
- ✅ Intensity and duration sliders.
- ✅ Real key sound (platform effects, per key type) and haptics tuning.
- Sampled sound profiles: Typewriter, Mechanical, Soft, Bubble, Click currently
  fall back to the system click. They need actual audio.
- Per-effect preview tiles, if the pinned live keyboard turns out not to be
  enough on its own.

## M3 -- The layout editor  ✅ DONE (session 3)

The Keys Cafe "Make your own keyboard" screen.

- ✅ Select a key on the real keyboard (edit mode), edit label, output and
  shift label, width, row height and indent.
- ✅ Long-press sets from palettes: symbols, emoji, kaomoji, maths, fractions,
  currency, arrows.
- ✅ Custom text keys and macro keys (`%DATE%`, `%TIME%`, `%DAY%`, `%CLIPBOARD%`).
- ✅ Move left/right, insert, duplicate, delete (essential keys protected).
- ✅ Pinned top row (cursor keys and symbols by default) and number row.
- ✅ Undo/redo, reset to QWERTY.
- Not yet: drag to reposition, moving a key between rows, per-key colours in
  the editor (`KeyStyleOverride` exists in the model), split/one-handed presets,
  saving a layout under a new name.

## M4 -- Typing engine  ✅ MOSTLY DONE (session 3)

- ✅ Suggestion strip UI, drawn by the keyboard in the theme's colours.
- ✅ Completions and keyboard-aware autocorrect (Kotlin, AOSP word list; see
  ARCHITECTURE.md for why not the native engine).
- ✅ Learned words, undo-autocorrect on backspace, smart punctuation.
- ✅ Two-finger rollover and held-shift chording.
- Not yet: next-word prediction (no bigrams in the AOSP list), resuming
  suggestions when the cursor is put back inside an old word, other languages.

## M5 -- Glide typing  ✅ DONE (session 3)

- ✅ Kotlin SHARK2-style decoder over live key geometry, tuned on synthetic
  swipes; alternatives in the strip; automatic spacing between swiped words.
- ✅ Trail rendering; the Trail particle effect follows the swipe.
- Next: tune against *real* swipes from the phone (log traces, replay them in
  a JVM test). Synthetic swipes are a good start but not the truth.

## M6 -- The rest of Keys Cafe

- Emoji layer with search, recents and skin-tone variants.
- Custom sticker packs.
- Clipboard history.
- Gesture bar: two/three-finger swipes for undo, redo, copy, paste, cursor move.
- Typing statistics and typo analysis.
- Typing games (sentence practice, word rain).

## Ideas beyond Keys Cafe

- Share a keyboard design as a single file or QR code.
- Per-app profiles: a different keyboard in the terminal than in chat.
- A code layout with a pinned symbol row, since that is the actual daily pain.
