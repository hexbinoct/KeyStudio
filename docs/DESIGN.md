# Design

How KeyStudio looks and behaves, why, and how to add to it without it drifting.
Read this before building any new screen or control. It describes what exists
in `app/src/main/kotlin/io/keystudio/kb/ui/`; if the code and this file ever
disagree, fix one of them in the same change.

---

## Where it came from

Three references, each for a different part:

- **Samsung Keys Cafe** decides *what* the app contains and how it is organised:
  a front menu of things to make ("Style your own keyboard", "Make your own
  keyboard", typing settings), the four styling tabs (Colours / Shape / Effects
  / Sound), light and dark variants behind a sun icon, and above all that every
  change shows on the keyboard **while your finger is still moving**. This
  project exists because someone called it "the most wonderful app I have ever
  used"; match its features and its immediacy.
- **Bear** (the macOS/iOS notes app) sets the *feel*: calm, paper-like, typographic,
  warm rather than clinical, one confident red accent, controls that are slim
  and a little physical (a round slider thumb, a segmented control that slides).
- **Notion** sets the *structure*: small grey sentence-case section headers,
  content in white groups outlined by a hairline, rows divided by hairlines,
  hierarchy carried by type weight and colour rather than by boxes and shadows.

Asked for in session 3 ("imagine the famous Bear mac app, or Notion... make the
progress bars and other things look so much better"). It replaced a
Material 3 look with purple tints and chunky controls.

## The principles

1. **The keyboard is the colourful thing on screen. The tool stays out of its
   way.** Neutrals everywhere, one accent, no gradients or tinted surfaces in the
   Studio chrome. Colour on screen should come from the user's keyboard designs.
2. **Show the real thing, not a picture of it.** Previews are the production
   `KeyboardView`. The theme grid draws each theme's actual colours as a
   miniature keyboard, never an abstract swatch. There is one renderer.
3. **No Save button.** Every control writes through immediately (debounced
   ~70 ms) and the keyboard in every app follows. Undo exists instead, grouped
   per gesture: a whole slider drag is one undo step.
4. **Hierarchy by type, not boxes.** Before adding a card, border, shadow or
   colour to make something stand out, try weight (Medium/SemiBold) or colour
   (`text` vs `textSecondary`) first.
5. **Honest copy.** If something is not implemented, say so in a footer rather
   than showing a control that silently does nothing (the Sound tab says the
   sampled profiles have no samples yet). Explain consequences ("Particles keep
   the keyboard drawing every frame").
6. **Calm, physical motion.** Springs for things you drag or toggle, short fades
   for things that swap. Nothing bounces for attention.
7. **Every screen works in light and dark.** Both are first-class; check both.

---

## Tokens (`ui/StudioTheme.kt`)

Read colours through `Studio.colors`, never hard-code them in a screen.

| Token | Light | Dark | Use |
|---|---|---|---|
| `background` | `#F7F6F3` | `#191919` | The page. Warm paper, Notion's dark grey. |
| `surface` | `#FFFFFF` | `#212121` | Groups, cards, text fields sitting on the page. |
| `elevated` | `#FFFFFF` | `#2E2E2E` | Dialogs, menus, the selected segment. |
| `fill` | `#EFEEEA` | `#2A2A2A` | Quiet backgrounds: chips, segmented track, value pills, quiet buttons. |
| `fillStrong` | `#E4E2DD` | `#363636` | Inactive slider track, off switch. |
| `hairline` | `#E6E4DF` | `#303030` | Every 1 px border and divider. |
| `text` | `#2F2E2B` | `#EAE9E6` | Primary text and icons. |
| `textSecondary` | `#7A7873` | `#9D9B96` | Subtitles, section headers, footers, values. |
| `textTertiary` | `#A9A69F` | `#6C6A66` | Placeholders, chevrons, hex codes, disabled. |
| `accent` | `#DD4C4F` | `#F06A6A` | The one accent (Bear's red): primary buttons, active slider track, on switches, links, selection. |
| `accentSoft` | accent @ 12% | accent @ 16% | Selected chip background, slider halo, palette selection. |
| `success` / `danger` | `#3F9E6E` / `#D1453B` | `#5DBB8A` / `#EF6B61` | Setup ticks; Delete, Reset. |

Material 3 components that still appear (dropdown menus, alert dialogs, text
fields) are mapped onto these in `StudioTheme`, so they match. Never give a
Material `Surface` a `tonalElevation`: with a red primary it tints the surface
pink.

**Type** is Inter (bundled variable font, OFL). Each weight sets the `wght` axis
explicitly; declaring `FontWeight` alone renders everything regular.

| Style (`StudioType.`) | Size / weight | Use |
|---|---|---|
| `display` | 30 sp Bold, -0.8 tracking | The app title on Home only. |
| `title` | 19 sp SemiBold | Dialog titles. |
| `headline` | 16 sp SemiBold | Top-bar titles, card headings. |
| `body` / `bodyStrong` | 15 sp Regular / Medium | Row titles, text, field contents. |
| `label` | 14 sp Medium | Buttons, chips, segments. |
| `section` | 13 sp Medium, `textSecondary` | Section headers: sentence case, never ALL CAPS. |
| `caption` | 13 sp Regular | Subtitles, footers, notes. |
| `value` | 13 sp Medium, tabular figures | Numbers that change under the finger, so they do not jiggle. |

**Shape and space.** Groups 14 dp corners; buttons 11 dp; chips 9 dp; segmented
track 10 dp (segment 8 dp); dialogs 20 dp; menus 12 dp. Page gutter 16 dp. Rows
are padded 16 dp horizontally and 13 dp vertically. Dividers inside a group are
inset 16 dp (50 dp when the rows have leading icons, so the line starts under the
text); use inset 0 for a divider under a full-width control such as a chip row.

**Icons** are Lucide line icons (`ui/Icons.kt`, ISC licence) on a 24-unit grid
with a 1.75 stroke: 20–21 dp in rows and top bars, 18 dp inside buttons and
menus. To add one, paste its SVG path strings from lucide.dev into
`StudioIcons`. Do not add the Material icons-extended library (tens of MB) and
do not use text glyphs (`←`, `⋮`) as icons.

---

## Components (`ui/editor/Controls.kt`)

Every screen is built from these. If you need something new, add it here, in
the same style, rather than styling it inline in a screen.

| Component | Use it for |
|---|---|
| `Section(title, footer) { … }` | The basic unit of every settings-like screen: grey header, white hairline group, optional grey footer for explanation. |
| `Group`, `RowDivider(inset)` | A group without a header; the dividers between rows inside one. |
| `ListRow` / `NavRow` | A row with optional icon, title, subtitle and trailing content; `NavRow` adds a chevron and optional value, for rows that open another screen. |
| `SliderRow` / `StudioSlider` | Any continuous number. Label left, value in a `fill` pill right, slim track below. `onChange` fires continuously; `onSettled` ends the undo gesture. Horizontal drags only, so a vertical drag still scrolls; a tap jumps. |
| `SwitchRow` / `StudioSwitch` | Booleans. The whole row is tappable. |
| `Segmented` | 2–4 short mutually exclusive options that switch a *view*: editor tabs, layer picker, background kind, solid/linear/radial. The selection slides. |
| `ChipRow` | Longer enums (shapes, effects, fonts, palettes). Scrolls horizontally, keeps the selection in view. Selected = `accentSoft` tag with accent text. Label with `prettyName(enum.name)`. |
| `ColorSlotRow` / `ColorIntRow` / `Swatch` | A colour setting: shows the hex (or "Linear"/"Radial") and a real swatch, gradients included. |
| `GroupAction` | An action that lives inside a group: accent text with an icon ("Choose a different image", "Add characters…"). |
| `PrimaryButton` / `SecondaryButton` | Standalone buttons. At most one primary per screen area. |
| `IconAction` | Round, borderless top-bar buttons (back, undo, redo, more, sun/moon). |
| `GroupNote` | Grey explanatory text inside a group. |

## Screen patterns

**Settings-style screen** (`TypingScreen` is the simplest example): top bar
(`IconAction` back + `headline` title), then a vertical scroll of `Section`s
with 16 dp gutter, 24–32 dp of space at the end.

**Editor screen** (`EditorScreen`, `LayoutEditorScreen`): top bar with title and
a secondary line of context ("Editing the light version", "QWERTY, edited"),
then undo / redo / more on the right; a full-width `Segmented` under it; a
scrolling column of `Section`s; a 1 dp hairline; and the **live keyboard pinned
at the bottom**, where the thumb and the eye both are. Tab switches cross-fade
(160 ms in, 90 ms out).

**Home**: large `display` title and a one-line description; setup collapses to a
single status line once done; content as grid or grouped `NavRow` menu.

**Empty state** (layout editor with nothing selected): a 52 dp rounded `fill`
tile with a 24 dp icon, a `headline` instruction, one sentence of `body`
explanation, centred.

**Dialogs**: `elevated` surface, 20 dp corners, `title` heading; actions as
text buttons (accent for confirm, `text` colour for cancel, `danger` for
destructive).

**Navigation** is a saveable route string in `MainActivity`
(`home`, `style:<id>`, `layout:<id>`, `typing`). Add a route there for a new
screen; forward navigation slides in a sixth of the width with a fade (260 ms).

**Motion values**: slider thumb grows to 1.18× while dragged (spring 0.6,
medium stiffness); switch knob and segment indicator use springs (0.7 / 0.8
damping, medium-low stiffness); colour changes use `animateColorAsState`.

## Words

- **British spelling** in the UI: Colours, Capitalise, centre.
- **Sentence case** everywhere: headers, buttons, menu items.
- Short, plain, second person: "Tap a key below to edit it", "Slide on space
  to move the cursor". Say what a thing *does*, not what it is called
  internally ("Backspace swipe" is internal; "Slide left on delete to erase
  words" is the label).
- Footers explain consequences and limits, in one or two sentences.

## The keyboard's own UI

The Studio style does **not** apply to the keyboard: the keyboard takes its look
entirely from the user's theme. Anything drawn by `KeyboardView` -- the
suggestion strip, its toolbar icons, the swipe trail, popups, the layout
editor's selection outline -- uses the theme's text colour (at reduced alpha
for secondary things), font and accent (`profile.effects.accentColor`), so it
matches every design automatically. Never draw a fixed colour there.

---

## Checklist for a new screen or feature

1. Built only from `Controls.kt` components and `Studio.colors` / `StudioType`.
2. Settings grouped into `Section`s with sentence-case headers; limits and
   consequences in footers.
3. Every change writes through live; any continuous control collapses into one
   undo step via `onSettled`.
4. If it affects the keyboard, the real keyboard is visible while editing it.
5. Checked on the emulator in **both** light and dark
   (`adb shell cmd uimode night yes|no`), with screenshots, before calling it done.
6. New icons are Lucide paths in `StudioIcons`; no new icon libraries.
