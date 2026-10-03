# Where this project is

Read this first. It is the handoff between sessions and should be enough to
resume with no other context.

**Building any UI?** Read [`DESIGN.md`](DESIGN.md) too: the inspiration
(Keys Cafe for content, Bear for feel, Notion for structure), the tokens, the
component vocabulary and the checklist every new screen follows.

Last updated: 2026-09-24 (end of session 3).

---

## The aim

A few days with a Samsung Galaxy S24 were enough to fall in love with **Keys
Cafe**, Samsung's Good Lock module for customising the keyboard: colours, key
shapes, key layout, key sizes, press effects and animated backgrounds. It is
Samsung-only. The target here is a **Poco X3 NFC**, with that same experience.

So: build a keyboard and its designer from scratch, good enough to daily-drive.
Efficiency and polish matter — this is meant to be genuinely nice, not a demo.

Keys Cafe itself is *not* a keyboard. It skins the Samsung Keyboard, which
Samsung ships in the ROM and cannot modify per-user. We do not have that
constraint, so **KeyStudio is one app with two entry points**: the IME (appears
in the keyboard picker) and the Studio (appears in the app drawer). They run in
**separate processes**, so the Studio can never take the keyboard down.

### What Keys Cafe actually contains (from researching its real screens)

Five menu items:

1. **Make your own keyboard** — key layout editor. Select a key, resize it
   (Width/Height sliders), edit its long-press set, replace it from palettes
   (symbols, emoji, maths, fractions, arrows), add custom text and macro keys
   (`%TDY`, `%TIME`).
2. **Style your own keyboard** — tabs: **Colors / Effects / Sound**. Effects
   splits into *keyboard colour effect*, *key colour effect*, *key motion
   effect*. Themes have light and dark variants toggled by a sun icon.
3. **My Sticker** — custom sticker packs.
4. **Play keyboard game** — Sentence Practice, Word Rain.
5. **Advanced keyboard setting** — gestures, auto-replace sensitivity, delete
   accelerator, typing stats and typo analysis.

---

## What works right now

### The keyboard (verified on the real Poco X3, session 1)

- A registered system IME that types: shift and caps lock, auto-capitalisation,
  double-space-to-period, long-press accent popups, key repeat, symbol and
  numeric layers, globe key to switch back to another keyboard.
- Data-driven layout engine: weight-based sizing, nearest-key hit testing with
  12dp slop.
- Renderer: six key shapes, solid/linear/radial fills, per-key style overrides,
  strokes, shadows, vector icons, hint glyphs. Allocation-free draw path.
- Spring-driven key motion, key colour effects, nine particle types.
- Animated backgrounds, drawn at 1/8 resolution and upscaled.
- Seven preset themes.
- **Typing (session 3)**: two-finger rollover, held shift as a modifier,
  swipe typing with trail, suggestion strip, completions, autocorrect with
  backspace-to-undo, learned words, smart punctuation, space-bar cursor,
  backspace word-swipe, macro keys. Settings in the Studio's Typing screen.
- **Key sound and haptics** (session 2): platform keyboard sound effects, with
  the right one per key — space, delete and enter each sound different — at the
  profile's volume, plus vibration honouring the profile's duration. The
  Studio's preview is deliberately silent.

### Session 3 (2026-09-24): typing, restyle, layout editor

Session 3 opened with M1 confirmed working on the phone and the theming
"looking really great". Asked for, in order:

1. **A restyle of the Studio** toward Bear / Notion. Done: `ui/StudioTheme.kt`
   (tokens, Inter type scale), `ui/Icons.kt` (Lucide line icons),
   `ui/editor/Controls.kt` (the whole control vocabulary: `Section` groups,
   slim `StudioSlider`, `StudioSwitch`, sliding `Segmented`, tag-style
   `ChipRow`, buttons). Every screen is built only from these.
2. **Multi-touch typing** -- "the other key does not register until the first
   is lifted". Fixed with `TouchRouter` rollover; see ARCHITECTURE.md.
3. **Then the next milestone, and swipe typing.** Both done: M3 layout editor,
   M4 suggestions/autocorrect, M5 swipe.

Verified on the emulator (`KeyStudio_API31`) by driving it with adb:
swipe "hello" then "world" gives "hello world" with alternatives in the strip;
"teh" + space autocorrects to "the" and one backspace restores "teh"; space
slide moves the caret; backspace slide deletes whole words; a key widened and
an emoji key added in the layout editor appear on the *real* keyboard in the
other process; light and dark Studio both checked. Two-finger rollover cannot
be injected through adb (`input motionevent` is single-pointer) and rests on
15 JVM tests -- **not yet felt on real hardware.**

Tests: `python tools/kb.py build :core:testDebugUnitTest` -- 29 tests (router,
swipe accuracy, suggester), all passing.

### The theme editor — M1 (verified on the emulator, session 2; on the phone, session 3)

The Keys Cafe "Style your own keyboard" screen, with the four tabs it has:

- **Colours** — background (solid/gradient, animated, or an image from the
  gallery with dim and blur), key fills, text colours, key outline, popup
  colours. Every slot opens a picker that offers Solid / Linear / Radial and
  keeps your colours when you switch between them.
- **Shape** — key shape, corner radius, inset, shadow; font family, size and
  weight, or a font file of your own; keyboard height, key gap, side and bottom
  padding, width fraction and one-handed offset; number row and suggestion strip.
- **Effects** — key motion, key colour and particle effects chosen
  independently, accent colour, particle palette, intensity, duration.
- **Sound** — sound profile, volume, vibration on/off and strength.

Plus: a colour picker (HSV field, hue bar, alpha bar with chequerboard, hex
field), undo/redo, rename, duplicate, delete, reset-to-original, share as JSON,
import from a file, and a sun/moon toggle that switches which of the profile's
two skins you are editing.

**Everything is live.** There is no Save button: each change is written to disk
(debounced 70 ms) and the keyboard follows while your finger is still on the
slider — including the real keyboard in other apps, because it watches the
files from its own process. Verified by editing the profile on disk while the
keyboard was on screen in another app and watching it repaint.

Presets are **shadowed, not replaced**: editing "Aurora" writes a user file
with the same id, and "Reset to original" deletes that file. Verified that the
delete also reaches the running keyboard.

---

## Environment

Everything goes through `tools/kb.py`, which pins the toolchain in one place.

```
python tools/kb.py doctor                 # verify JDK/SDK/emulator/wrapper
python tools/kb.py connect                # adb over wifi to the Poco X3
python tools/kb.py use <serial>           # pin commands to one device
python tools/kb.py install                # build + install
python tools/kb.py ime-on / ime-off       # select / restore the keyboard
python tools/kb.py shot out.png
python tools/kb.py emu                    # boot the clean AVD
```

Facts that cost time to discover; do not rediscover them:

- **JDK 21**, set as `kb.java.home` in `local.properties` (git-ignored, next to
  `sdk.dir`). An older JDK on PATH is too old for Gradle 9 and for `avdmanager`.
- **AGP 9.3.1 + Gradle 9.5.0 + Kotlin 2.2.10**. AGP 9 applies the Kotlin plugin itself — declaring
  `org.jetbrains.kotlin.android` fails with "Cannot add extension with name
  'kotlin'". The modules deliberately do not declare it.
- **compileSdk is 36, not 37.** 37 is a preview SDK.
- **No `applicationIdSuffix` on debug.** A changing package id changes the IME
  component id and forces a re-enable in system settings every time.
- The framework abbreviates the IME id relative to the package:
  `io.keystudio.kb/.ime.KeyStudioInputMethodService`. `ime enable` **rejects**
  the fully-qualified spelling. `kb.py` reads the real id off the device.
- Use the project's own AVD, **`KeyStudio_API31`** (`kb.py avd-create`). A
  shared AVD was once found whose package manager resolved no third-party
  component at all — apps installed but could never launch.
- Poco X3 wireless debugging: pair once
  (`kb.py pair <ip:pairingPort> <code>`), then `kb.py connect`. The pairing port
  differs from the connect port, and **the connect port changes every time
  wireless debugging is toggled**.
- **Wireless debugging on this phone is not worth fighting.** Session 2 lost a
  long stretch to it and never got a connection. What was actually learned:
  - The saved address goes stale when the phone changes network;
    `.kb-device` survives the move and lies.
  - **Pairing succeeded and the device still never listened.** Immediately
    after a successful `pair`, a scan of *every* port 1024-65535 on the phone
    found nothing open, and every probe was refused instantly — a live host
    with no listener. MIUI appears to stop the adb listeners as soon as the
    Wireless debugging screen is not in the foreground.
  - `adb mdns services` returns nothing on networks whose access points block
    multicast.
  - A refused port and an unpaired host look different: refusal means nothing
    is listening; unpaired gives an authentication failure. `kb.py connect`
    prints "not paired yet" for both, which is misleading — do not trust it.
  - **Prefer USB, or sideload an APK.** See "Installing a build without adb" below.
- The Bash tool eats backslashes inside heredocs and chokes on long ones. Write
  Kotlin/Python/Markdown with the Write and Edit tools, not `cat <<EOF`.
- **`adb push` to `/sdcard/...` from the Bash tool** gets its path mangled by
  MSYS into `C:/Program Files/Git/sdcard/...`. Use the PowerShell tool for
  pushes, or any path that starts with `//`.
- Driving the UI with `adb input tap` **needs a wait before the screenshot**;
  a screenshot taken immediately shows the previous frame and looks like the
  tap did nothing. `adb shell "input tap X Y; sleep 2"` and then shoot.

### Installing a build without adb

```
python tools/kb.py build
cp app/build/outputs/apk/debug/app-debug.apk ../KeyStudio-<version>-debug.apk
```

Copy it to the phone and tap it. This works because the debug APK is signed
with the build machine's standard Android debug key, the same key every earlier
install used — so it **updates in place** and keeps the profiles and the
keyboard selection. The IME component id has not changed, so nothing needs
re-enabling in system settings. A build from a different machine has a
different debug key and must be uninstalled first.

Two things to remember every time this route is used: the update lands **live**
on a keyboard that may be in daily use, and a debug build is `debuggable` and
unoptimised, so it is not a fair basis for any performance impression.

---

## Decisions already made — do not re-litigate

- One app, two entry points, two processes. Reasoning above.
- The IME renders with a custom `View` + `Canvas`, **not** Compose: the IME
  window must appear instantly and hold frame rate. Compose is for the Studio
  only.
- **No AGSL / `RuntimeShader`** — it needs API 33 and the Poco X3 is API 31.
  Effects use particles in parallel float arrays and low-res upscaled
  backgrounds instead. Image blur is done by downscale-and-upscale for the same
  reason: `RenderEffect` is API 33 and RenderScript is gone.
- `KeyboardView` lives in `:core` so the Studio's preview *is* the real
  keyboard. There must never be a second renderer that can drift from it.
- Profiles are the unit of sharing and are plain JSON on disk. Files are also
  the IPC channel between the Studio and the keyboard process.
- ~~The typing engine will be vendored~~ -- **changed in session 3**: the word *list* is AOSP's (Apache-2.0), but
  autocorrect and swipe are Kotlin written here, behind narrow interfaces, so
  a vendored/learned engine can still replace them. Reason: working, measured
  swipe now versus a multi-session NDK + model-weights job with a licence
  question (FUTO is source-available). ARCHITECTURE.md has the detail.
- The Studio's look is **Bear/Notion**: warm neutrals, hairline groups, one
  accent (#DD4C4F), Inter. Build new screens from `Controls.kt` only, following
  `docs/DESIGN.md`.
- Typing settings are **global** (`settings.json`), not per profile.
- The suggestion strip is **drawn by `KeyboardView`**, not a separate view, so
  it always matches the theme. It sits *above* the key area: turning it on
  makes the keyboard taller rather than shrinking the keys.
- Build order is **style first**, then the layout editor, then the typing engine.

---

## Known gaps and honest caveats

- **Nothing from session 3 has met the phone.** Build
  `KeyStudio-0.2.0-debug.apk` was sideloaded. The first thing next session is
  the verdict on: rollover while typing fast, swipe accuracy on *real* swipes,
  autocorrect (too eager? too timid?), the restyle.
- **The keyboard is 44 dp taller than before** for anyone with the strip on,
  which is every existing profile (`showSuggestionStrip` defaulted to true
  but drew nothing until now). Toggle: Style › Shape › Rows › Suggestion strip.
- **Swipe tuning is synthetic.** 97% / 92% top-1 is on generated swipes. Real
  thumbs differ; the next step is logging real traces and replaying them
  in a JVM test.
- No next-word prediction (the AOSP list has no bigrams). English only.
- Layout editor: no drag-to-move, no moving between rows, no per-key colour
  UI, layouts cannot be saved under a new name -- editing QWERTY edits it for
  every profile that uses it (it shadows the built-in, "Reset" restores it).
- No emoji layer yet; emoji only via custom keys and long-press sets.
- **Sampled sound profiles are still empty** (Typewriter etc. play the system
  click); the Sound tab says so.
- Dictionary load is ~1.4 s on the emulator, in the background; the first
  second after the keyboard process starts has no suggestions.
- **Performance is still not characterised.** See ARCHITECTURE.md. Build a
  Perfetto or microbenchmark harness before optimising anything.

---

## Bugs found and fixed

Session 3:

1. An edited preset jumped to the end of the theme grid (user copies were
   listed after presets). It now keeps its place.
2. Labels longer than one character spilled over neighbouring keys, and long
   long-press options overflowed the popup. Both now shrink to fit.
3. Inter is bundled as a *variable* font; declaring `FontWeight` alone renders
   every weight as regular. Each weight must set the `wght` axis.

Session 2: `FileObserver` is not recursive (watch each directory); profile
writes must be atomic (temp + rename); image backgrounds must clip to the
view; `customFontPath` and `blurRadius` are implemented.

---

## Resume here

1. **Ask how 0.2.0 went on the phone** -- specifically rollover, swipe,
   autocorrect, and the look. That decides whether the next session is tuning
   or new features.
2. If swipe needs work: add a debug switch that logs `GestureTrace`s to a file,
   swipe a list of words on the phone, pull the file, and turn it into a replay
   test next to `SwipeDecoderTest`.
3. Otherwise, M6 (see ROADMAP): an **emoji layer** is the most visible gap,
   then clipboard history, then next-word prediction.

### State of the phone

Unverified. `KeyStudio-0.2.0-debug.apk` (versionCode 2) was sideloaded; it
updates 0.1.0 in place (same debug key) and keeps the profiles. As always: it
replaces the phone's keyboard live, and a debug build is not a fair read on
speed.

### State of the emulator

`KeyStudio_API31`, KeyStudio selected as the IME, light mode, no user
profiles or layouts (test edits were deleted). A throwaway driver script was
used this session (adb taps, multi-point swipes via `input motionevent`,
EditText dump via uiautomator) -- worth promoting into `tools/kb.py` if UI
testing continues.

**Harness trap:** `am force-stop io.keystudio.kb` also kills the IME process,
and Android then silently resets the default keyboard to Gboard. Run
`kb.py ime-on` again after any force-stop or reinstall.
