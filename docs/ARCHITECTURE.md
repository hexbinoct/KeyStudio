# Architecture

## The one idea

**The keyboard is data, not code.** A layout is a list of rows of key
definitions with *weights* rather than pixels; a theme is a bag of colours,
shapes and fonts; effects are an enum plus parameters. Everything the Studio
edits is a serialisable object, and the keyboard is a renderer for those
objects.

That is what makes a Keys Cafe-style editor possible at all. Resizing a key,
repainting one keycap pink, or swapping a key's long-press set is a change to a
data structure, not a change to a layout file or a code path.

## Module split

```
:core   pure rendering and model. No Compose, no IME.
:app    the InputMethodService + the Compose Studio UI. Depends on :core.
```

`KeyboardView` lives in `:core` on purpose. The Studio's live preview embeds the
*same* view the IME uses, in `previewMode`. There is no second renderer that can
drift from the real one, so WYSIWYG is structural rather than aspirational.

## The render path

```
KeyboardLayout ──▶ LayoutEngine ──▶ List<PlacedKey>   (pixel rects)
                                        │
Theme ─────────▶ KeyboardRenderer ──────┤──▶ Canvas
EffectsConfig ─▶ EffectsEngine ─────────┤
BackgroundSpec ▶ BackgroundRenderer ────┘
```

`prepare()` runs on theme or size change and does all the allocation. `draw()`
runs every frame and allocates nothing. That split is the whole performance
story.

### Why no Compose in the keyboard

The IME window must appear the instant a text field is focused, and must hold
120 Hz while a particle system runs. Compose's first-frame cost and
recomposition overhead are wrong for that. The keyboard is a single custom
`View` drawing to a hardware-accelerated `Canvas`, which is what AOSP's own
keyboard does and for the same reasons.

Compose *is* used for the Studio, where there is no latency budget and its
strengths pay off.

### Why no AGSL shaders

`RuntimeShader` needs API 33. The Poco X3 runs Android 12 (API 31), so the glow,
neon and liquid-background effects are built from primitives that an Adreno 618
is actually fast at:

- **Particles** live in parallel `FloatArray`s (structure-of-arrays), with a
  fixed pool of 384 and swap-remove on death. No per-particle objects, no
  allocation in the frame loop.
- **Animated backgrounds** are drawn into a bitmap an eighth of the keyboard's
  size and upscaled with bilinear filtering. The upscale *is* the blur. A
  full-resolution blur would not hold frame rate; this costs almost nothing.
- **Gradients** are built once in unit space (0..1) and positioned per key with
  a reused `Matrix` via `setLocalMatrix`, so one shader object serves every key
  at every size.

### Frame loop

A `Choreographer` callback runs only while something is actually animating: a
spring is still settling, particles are alive, or the background is animated.
When everything settles the loop stops and the view goes idle. Two screenshots
taken five seconds apart while idle are byte-identical -- no wasted redraws,
no battery drain.

## Key motion

Each key has a scale and a vertical offset driven by a damped spring, with
per-key velocity stored alongside the visual state. `Jelly` simply lowers the
damping so it wobbles. Springs settle to exactly the target and then report
"not busy", which is what lets the frame loop stop.

## Input handling

`KeyboardView` never edits text. It reports `KeyAction`s -- `Text`,
`Special`, `Gesture` (a finished swipe), `MoveCursor`, `DeleteWords`,
`PickSuggestion` -- and `KeyStudioInputMethodService` owns every editing
decision.

Touch goes through `TouchRouter` (`core/view`), a pure-Kotlin per-pointer
state machine with JVM tests (`TouchRouterTest`). The rules:

- **Rollover.** A finger landing while another is down commits the earlier
  key *immediately*. Two-thumb typing overlaps presses constantly; waiting for
  each lift drops or delays letters. This was the "must lift the first key"
  complaint.
- **Held shift is a modifier.** Keys pressed while shift is held come out
  capitalised and shift does not toggle on release. The shifted state is
  captured when each key goes *down*, because fast typists lift shift before
  the letter.
- **Slides.** Off a letter key past a threshold: a swipe-typed word. Along the
  space bar: the caret moves. Left from backspace: whole words are selected,
  deleted on release. Off any other key: cancelled.
- Long press opens the accent popup; a second finger dismisses it.

Hit testing falls back to the nearest key within a 12dp slop when a touch lands
in a gap. The suggestion strip is drawn by `KeyboardView` itself (above the key
area, taking the theme's font and colours) and handles its own taps.

## Typing engine

All in `core/typing`, plain Kotlin, run on one background thread by
`TypingEngine` (the decoder and suggester are not re-entrant; one thread keeps
that simple and costs nothing).

- **Dictionary.** AOSP LatinIME's `en_US` wordlist (Apache-2.0), 160k words
  with frequencies, converted by `tools/build_dict.py` into
  `core/src/main/assets/dict/en_US.tsv`. Loaded into flat arrays with a
  sorted-key index (prefix completion) and first/last-letter buckets (swipe).
  About 1.4 s to load on the emulator, off the main thread; until it is loaded
  the keyboard types but does not suggest.
- **Swipe (`SwipeDecoder`).** SHARK2-style template matching. Each candidate
  word's ideal path runs through its letters' *live* key centres, so resized
  or rearranged layouts decode correctly. Cost = alignment (DP-matched
  letter-to-trace distance) + location (resampled point-to-point) + unexplained
  length + endpoint misses, traded against word frequency. Weights were
  grid-searched (`SwipeTuning`, an ignored test) on synthetic human-shaped
  swipes over a Poco X3-sized QWERTY: 97% top-1 on the 300 commonest words,
  92% on mid-frequency words, 98-99% in the top 3. Known limit: collinear
  paths (t-y-p vs t-o-p along the top row) are geometrically identical and
  only frequency separates them.
- **Tap typing (`WordSuggester`).** Completions by prefix; corrections by
  Damerau-Levenshtein with keyboard-aware substitution cost (from the live key
  positions) and cheap doubled-letter slips. Autocorrect only replaces a word
  that is neither in the dictionary nor learned, and not when the typed text
  is the start of a likelier word. Apostrophe-less forms ("dont", "youre")
  map to their contraction. ~7 ms per keystroke on desktop JVM.
- **Learned words (`LearnedWords`).** A word counts as known after two uses,
  or at once when the user insists (taps their own spelling in the strip, or
  backspaces over an autocorrection). Stored in `learned.tsv` beside the
  profiles and fed to the swipe decoder too.

The word being typed is held as composing text, so the strip can replace it,
space can autocorrect it, and a swiped word can be exchanged for an
alternative. Selection updates that arrive within 200 ms of our own edit are
treated as echoes; later ones that leave the word end it.

**Deviation from the original plan, deliberately:** the plan was to vendor
AOSP's native dictionary and FUTO's swipe models via the NDK. The dictionary
*data* is AOSP's, but both engines are Kotlin written here, behind the same
narrow interfaces the plan called for. Vendoring native code with JNI and model
weights was a multi-session job with a licence question attached (FUTO is
source-available, not open source); this got swipe and autocorrect working now,
measured, on real key geometry. A learned decoder can still replace
`SwipeDecoder` without touching anything around it.

## Toolchain notes

- AGP 9 applies the Kotlin plugin itself. Declaring
  `org.jetbrains.kotlin.android` alongside it fails with "Cannot add extension
  with name 'kotlin'". AGP 10 removes the opt-out, so the modules simply do not
  declare it.
- `compileSdk` is pinned to **36**, not 37. 37 is a preview SDK.
- The framework abbreviates the IME component id relative to the package
  (`io.keystudio.kb/.ime.KeyStudioInputMethodService`). `ime enable` rejects the
  fully-qualified spelling, and `Settings.Secure.DEFAULT_INPUT_METHOD` stores
  the short form, so both the harness and the Studio match on the package half.
- The debug build deliberately has **no** `applicationIdSuffix`. A changing
  package id would change the IME component id, forcing a re-enable in system
  settings on every switch between debug and release.

## Process split

The IME runs in `:ime`, separate from the Studio. Two reasons:

- Force-stopping or crashing the Studio must not take the user's keyboard down
  with it. This was found the hard way: `am force-stop` on the shared process
  dropped the keyboard and the system silently fell back to Gboard.
- The keyboard process then never loads the Compose runtime the Studio pulls in.

Because they are separate processes, profiles travel between them as files. The
IME holds a `FileObserver` on the profile directory and reloads on write, which
is what makes editing a theme change the live keyboard immediately instead of
only on the next `onStartInputView`.

## Performance: what is actually known

Measured on the real Poco X3 (Adreno 618, Android 12, 120 Hz panel) using
`dumpsys gfxinfo` against the `:ime` pid alone.

Sustained with an animated background and no input: **634 frames in 10 s
(~63 fps), median frame ~19-20 ms**.

That median did **not** move across any of these:

| Variant                         | median | janky |
|---------------------------------|--------|-------|
| Midnight (per-key shadow blur)  | 19 ms  | 8%    |
| Neon (no elevation)             | 20 ms  | 10%   |
| Aurora animated, allocating     | 19 ms  | 17%   |
| Aurora animated, zero-alloc     | 20 ms  | 29%   |

Two hypotheses were tested and **both were wrong**: per-key `setShadowLayer`
was not the cost, and removing the per-frame shader allocations did not improve
the numbers either. The jank percentage swung 8% -> 29% with no causal link to
the change, while frame time stayed pinned at ~20 ms across workloads that
differ enormously in draw cost.

The conclusion is about the *method*, not the renderer: `gfxinfo` over adb on a
live MIUI phone, driven by injected taps, is too noisy to optimise against.
Injected taps arrive a few per second, so each frame is an isolated wake-up
measured while the SoC is clocked down.

The allocation fix was kept anyway, because allocating ~480 shaders per second
inside a frame loop is wrong on principle, but **it is not validated as a
speed-up and should not be described as one**.

Before optimising anything here, build a real harness: Perfetto traces, or an
on-device microbenchmark that renders N frames in a loop and times them.
