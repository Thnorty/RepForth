# RepForth Design System

RepForth is a **local-first, open-source exercise planner and workout tracker** for Android
phones and Wear OS watches. Tagline: **"Every rep moves you forward."**

Users browse a 1,324-exercise catalog, build workouts by hand or have one drafted by an AI
provider they supply their own key for, and then run an active session with sets, timers and
rest countdowns. A Wear OS companion acts as a **remote** for the session running on the phone.
The app ships bilingual English and Turkish. There are **no accounts, no backend, no telemetry**
— every number a user logs stays on their device.

## Sources

This system was authored from a written brand + product brief supplied in chat. **No codebase,
Figma file, repository, deck, logo, font binary or image asset was attached**, so:

- Component inventory is a standard Material 3 set sized to RepForth's screens, plus the domain
  components the brief explicitly describes (set rows, rest timer, plan/exercise cards, Wear
  primitives). Nothing here was copied from a real RepForth source — if a real codebase or
  Figma file exists, reconcile against it before treating this as ground truth.
- **Fonts are substitutions** (see VISUAL FOUNDATIONS → Type).
- **Icons are a substitution**: Material Symbols Rounded from the Google Fonts CDN.
- **There is no logo.** The brand name is set in type wherever a mark would go. Nothing was drawn.

## Products / surfaces

| Surface | Kit | Notes |
| --- | --- | --- |
| **Phone app** (Android, 412×892dp, edge-to-edge, phone-only) | `ui_kits/phone/` | Today, catalog, exercise detail, active session + rest, builder (manual + AI draft), settings. Live EN/TR and dark/light switches. |
| **Wear OS companion** (round 1.3", square 1.2", ambient) | `ui_kits/wear/` | Set remote, rest countdown, exercise list. Glanceability over decoration. |

---

## CONTENT FUNDAMENTALS

**Voice: a training partner, not a coach and not a cheerleader.** Plain, second person, present
tense, short. The app states facts and gets out of the way; the user's own numbers do the
motivating.

- **Person.** "You" for the user, never "we" for the app. The app has no personality to refer
  to itself with — no "Let's crush it", no "I'll draft that for you". Copy says what happens:
  "Works offline."
- **Casing.** Sentence case everywhere — buttons, titles, dialogs, settings, snackbars.
  ALL-CAPS is a *typographic* device only (`.rf-overline`, section eyebrows), never authored
  into a string. Title Case never appears.
- **Length.** Buttons 1–3 words in English. Labels one line. Body copy one or two sentences,
  and only where a decision needs it. Empty states get one sentence and one action.
- **Numbers over adjectives.** "82.5 kg × 8" beats "heavy set". "6 exercises · 48 min" beats
  "a solid session". Never "amazing progress", "beast mode", "crushing it".
- **No gamification language.** No streak-shaming, no badges-as-praise, no exclamation marks.
  A PR is marked with a `Badge` reading "PR" — that is the whole celebration.
- **Privacy is stated plainly, repeatedly, without boasting.** The house sentences:
  "Logged sets stay on this phone. No account, no upload." · "Works offline. AI is optional."
  "Read the code. Change it." Never "military-grade", never "we care about your privacy".
- **Never imply the AI runs locally.** Generation calls the third-party provider the user
  configured, using the user's own key. Wherever a request is made, say where it goes:
  "Your request goes to the AI provider you set up." The local-data promise is a *separate*
  claim — logged sets do stay on the phone, and saying so must not blur into the AI boundary.
- **Errors describe the state and the next step**, never blame: "Watch disconnected · Retry".
  Destructive confirmations name the object: "Discard this workout?" with body
  "Logged sets stay on this phone. Nothing is uploaded."
- **Emoji: never.** Not in strings, not in headings, not in celebrations. Meaning comes from
  Material Symbols glyphs paired with words.
- **Units always follow the numeral, quiet and lowercase**: `82.5 kg`, `48 min`, `90s`, `8 reps`.
  Turkish uses a comma decimal and a period thousands separator: `1.324 egzersiz`, `82,5 kg`.

### Bilingual writing rules

Every string ships in **English and Turkish**, and Turkish is not an afterthought:

- Turkish runs **~30–40% longer** ("Start workout" 13 ch → "Antrenmanı başlat" 21 ch;
  "Skip rest" → "Dinlenmeyi atla"). Layouts assume the Turkish width, not the English one.
- Buttons are `fullWidth` or allowed to wrap. **Never truncate a primary action.** Only
  metadata may ellipsize.
- Turkish is agglutinative: never build a sentence by concatenating fragments, and never
  interpolate mid-sentence. Whole strings, one key each, with the number as a leading token.
- Turkish casing traps: dotted/dotless i (İstanbul, açık/AÇIK) — never `toUpperCase()` a
  Turkish string in JS; use CSS `text-transform` with `lang="tr"` set on `<html>`.
- Exercise names are translated, not transliterated ("Cable chest fly" → "Kablo ile göğüs açma"),
  and the English name stays searchable.

---

## VISUAL FOUNDATIONS

### Colour

**Dark-first, single accent.** Charcoal surfaces carry everything; one high-energy
**electric lime** marks the primary action, the active timer and progress — nothing else.
Material 3 semantic roles are the API (`--color-primary`, `--color-surface-container-high`,
`--color-on-surface-variant`, …); raw tones (`--lime-80`, `--n-12`) exist in `tokens/palette.css`
and are never referenced from product code.

- The neutral ramp carries a **faint green cast** (hue ≈ 100, chroma ≈ 4) so charcoal belongs to
  the same family as the lime. Pure grey looks foreign next to the accent.
- **Elevation is tone first, shadow second** in dark: surface → container → high → highest.
  In light theme the shadow does the work. Never both a heavy shadow and a hairline on one card.
- **Accent budget: one lime element per screen.** Two lime things compete and neither reads as
  "the action". Secondary actions are tonal (`--color-primary-container`) or outlined.
- **Semantics:** rest/timer = amber tertiary; completed = sage secondary; informational (watch,
  sync-free) = sky; destructive = red container (never bright red text on charcoal).
- **Light theme is a full peer** — every role re-declared under `[data-theme="light"]`, primary
  drops to lime tone 35 with white on-primary so it clears AA on white.
- **No gradients.** No bluish-purple anything. No colour-only meaning: every state pairs colour
  with a glyph and a word.

### Type

Two roles, and the hierarchy is the product's core idea: **numbers are the hero.**

- **Numeric — Archivo**, weight 800, tabular figures, tight tracking (−0.02em), leading 0.92.
  Reps, weights, set counts, countdowns, volume. Scale: hero 96 / xl 72 / lg 56 / md 40 /
  sm 28 / xs 20px. Nothing numeric goes below 20px.
- **UI — Manrope**, 400–700, Material 3 scale (title-lg 22, body-md 14, label-md 12). Labels,
  metadata and helper text stay quiet: `--text-quiet`, semibold, never larger than the numeral
  they describe. Screen titles use Archivo (`--font-display`) for their weight.
- **One hero per block.** If a name and a number compete, the number wins and the name becomes
  a label.
- **Font scaling to 200%** must not clip: no fixed-height containers around text, rem sizes
  throughout, buttons grow and wrap. See the Type → "Font scaling to 200%" card.
- **SUBSTITUTION — action needed.** No font files were supplied. Archivo and Manrope are Google
  Fonts nearest matches (both cover Turkish Latin-Extended) loaded via `@import` in
  `tokens/fonts.css`. Send the real binaries and they become self-hosted `@font-face` rules.

### Spacing, layout, shape

- **4dp grid** (`--space-1` … `--space-16`). 16dp phone gutter, 8dp between list cards, 24dp
  between titled sections. Content is a single column, max 412dp — **phones, not tablets**.
- **Touch targets are a hard floor**: 48dp phone, **64dp for anything tapped mid-set**
  (`size="session"`), 52dp on Wear, 56dp FAB. 40dp exists only for non-primary controls.
- **Radii:** 12 cards, 16 large cards, 20 large FAB, 28 sheets and dialogs, pill for buttons
  and chips. Wear uses full-bleed arcs instead of radii.
- **Cards** are compact and rounded: `--surface-card` fill, 12px radius, 16px padding, no
  border by default. Outlined variant = hairline `--color-outline-variant` on `--color-surface`.
  The only decorated card is *today's session*: a hairline mixed 35% from the accent.

### Backgrounds, imagery, transparency

- **No background images, no full-bleed photography, no patterns, no textures, no gradients,
  no grain.** Screens are flat charcoal (or flat off-white in light).
- **Exercise media is strictly 1:1** and appears only in its own frame — 48dp in rows, 72dp in
  cards, full-width square on detail. It is **never blurred, never scrimmed, never used as a
  fullscreen background** (`.rf-media-square`).
- Imagery direction, when real assets exist: neutral, cool-charcoal studio backgrounds, no
  warm filters, no motivational-poster crops. Subject centred, square, consistent framing.
- **Transparency and blur:** the scrim behind a dialog (opacity 0.62 dark / 0.42 light) and
  state layers (8% hover, 12% press, mixed from `--color-on-surface`). **No frosted glass** —
  backdrop blur costs battery on an Android app that runs for an hour with the screen on, and
  it hurts legibility of numerals.
- Protection gradients: not used. Text never sits over media, so nothing needs protecting.

### Interaction states

- **Hover** (pointer/emulator only): 8% on-surface state layer; filled buttons darken by mixing
  8% of their on-colour, plus `--elev-1`.
- **Press:** 12% state layer **and** a scale to `--motion-press-scale` (0.97) over 100ms —
  physical, brief. Cards scale to 0.995. Nothing changes hue on press.
- **Selected:** tonal container + **filled glyph** (nav item, toggle icon button) or a **check
  glyph** (chip, segmented button). Colour is never the only signal.
- **Focus:** 3px `--focus-ring` outline, 2px offset, on every interactive element.
- **Disabled:** 12% on-surface fill, 38% on-surface text, no shadow, no border.

### Motion

Native and restrained; tokens live in `tokens/motion.css`.

- **Shared-axis X** for plan → detail (250ms, `--ease-standard`); sheets 400ms.
- **Rings** sweep for set and rest time — the only continuous motion in the app.
- **Spring** (`--ease-spring`) fires once on set completion; `--motion-number` gives changing
  numerals a short rise + cross-fade.
- **Forbidden:** perpetual decorative animation, parallax, gradient drift, and **any large
  movement while the user is actively lifting** — during a set the screen holds still.
- **Reduced motion is defined centrally**, not per component: durations → 1ms, travel → 0,
  spring → linear, and a global `prefers-reduced-motion` block clamps every transition.
  Rings jump to value; the countdown numeral remains the source of truth.

### Accessibility (hard constraints)

WCAG **AA** minimum on every pair shipped (body ≥ 4.5:1, large text and non-text ≥ 3:1 — see the
Colors → "Text on surface" card) · 48dp minimum phone targets, 64dp in-session, 52dp Wear ·
font scaling to **200%** without clipping primary actions · **no meaning by colour alone**
(always glyph + word) · `aria-pressed` / `role="switch"` / `aria-live` on the set and rest
values so changes are announced without moving focus · ambient Wear rendering keeps contrast
with no fills.

---

## ICONOGRAPHY

**One system: Material Symbols Rounded** (variable icon font, Google Fonts), loaded in
`tokens/fonts.css` and used through the `Icon` component or the `.rf-icon` class with the
ligature name as the element's text.

- **SUBSTITUTION — flagged.** No icon assets were supplied. Material Symbols Rounded is the
  Android-native set and matches the Material 3 direction in the brief; the rounded (not sharp
  or outlined) cut matches the compact rounded cards. If RepForth ships its own set, replace
  the `@import` and keep the `Icon` API.
- **Sizes:** 16–18 inside chips and dense rows, 20 in buttons, **24 default**, 28–32 in-session
  and on Wear. Weight 400 normally, 500–600 for in-session glanceability.
- **Fill is state, not style:** `FILL 0` for inactive, `FILL 1` for the selected nav
  destination, active toggle or completed set (`Icon fill`, or `data-fill="1"` in plain HTML).
- **Icons never travel alone where they carry meaning.** Filters, states and badges are
  icon + text; decorative glyphs get `aria-hidden` (the `Icon` default), meaningful ones get
  `label`.
- **No emoji. No unicode glyphs as icons** (no ✓ ▲ ×) — the font has all of them. **No hand-rolled
  SVG icons.** The only SVG in this system is data drawing: the progress ring and the Wear arc.
- Muscle groups and equipment each have a fixed glyph (see `ui_kits/phone/data.jsx`:
  `MUSCLE`, `EQUIP`) so the same concept always shows the same icon in both languages.
- There is **no logo file** — see `assets/README.md` and the Brand → "Wordmark" card.

---

## Index

### Root
| Path | What it is |
| --- | --- |
| `styles.css` | The single entry point consumers link. `@import` lines only. |
| `tokens/` | `fonts.css`, `palette.css`, `colors.css`, `typography.css`, `spacing.css`, `shape.css`, `elevation.css`, `motion.css`, `base.css` |
| `css/` | Component stylesheets (`core`, `forms`, `feedback`, `navigation`, `workout`, `wear`) — the `.rf-*` classes the JSX emits, usable directly in plain HTML |
| `components/` | React primitives (below), each with `.d.ts` props contract + `.prompt.md` usage note |
| `ui_kits/phone/`, `ui_kits/wear/` | Full click-through product recreations, each with its own README |
| `guidelines/` | 20 specimen cards: Colors, Type, Spacing, Shape, Motion, Brand |
| `assets/` | Empty — see `assets/README.md` for what belongs where |
| `thumbnail.html`, `SKILL.md` | Homepage tile; Agent Skills manifest |

### Components

**core/** — `Icon`, `Button`, `IconButton`, `FAB`, `Card`, `Chip`, `Badge`, `Divider`, `StatBlock`

**forms/** — `TextField`, `SelectField`, `Checkbox`, `Radio`, `Switch`, `Stepper`, `Slider`, `SegmentedButtons`

**feedback/** — `ProgressRing`, `ProgressBar`, `Dialog`, `Snackbar`, `EmptyState`

**navigation/** — `TopAppBar`, `NavigationBar`, `ListItem`, `Tabs`

**workout/** — `PlanCard`, `ExerciseCard`, `SetRow` (+ `SetRowHeader`), `RestTimer`

**wear/** — `WearScreen` (+ `WearBody`, `WearValue`), `WearArc`, `WearListItem` (+ `WearList`), `WearAction`

### Intentional additions
Because no source defined the inventory, the set is a standard Material 3 kit plus:

- **`Icon`** — wrapper over the Material Symbols font so size/fill/weight are tokens, not magic strings.
- **`StatBlock`** — the "numbers are the hero" primitive; without it every screen re-implements the numeral treatment.
- **`Stepper`** with a 64dp `session` size, **`SetRow`**, **`RestTimer`**, **`PlanCard`**, **`ExerciseCard`** — the domain objects the brief describes.
- **Wear family** — the second form factor needs its own canvas, arc, row and action primitives; phone components do not scale down to a 1.2" round face.
- **Deliberately omitted:** Tooltip (touch-first product, no hover surface) and Avatar (no accounts, no people).

## Using this system

Link one file, then use the classes or the React components:

```html
<link rel="stylesheet" href="styles.css">
<html data-theme="light"> <!-- omit for the dark default -->
```

```jsx
<PlanCard today badge="Today" name="Push Day A" exercises={6} minutes={48}
  muscles={[{label:"Chest", icon:"accessibility_new"}]}
  action={<Button variant="filled" size="session" icon="play_arrow" fullWidth>Start workout</Button>} />
```
