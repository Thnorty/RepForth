# RepForth — build plan

## What this file is

Three documents, three jobs. Keep them separate; when they overlap, delete the copy.

| Document | Answers | Changes when |
|---|---|---|
| `PROJECT_GUIDELINE.md` | *What* to build, and why | A product decision changes |
| `AGENTS.md` | *How* to work in this repo | A convention is established |
| `docs/PLAN.md` (this) | *Where we are* and *what is next* | Every session |

This file does not restate the guideline's phase list (§19). It tracks execution
against it: what is actually built, in what order the rest should land, and
which decisions are closed so they are not reopened.

---

## Status

| Phase | Guideline | State |
|---|---|---|
| 0 — Foundation | §19 | **In progress.** Scaffold, tokens, DI and persistence done; dataset, navigation, CI outstanding |
| 1 — Local workout core | §19 | Not started. Blocked on 0 |
| 2 — AI providers | §19 | Not started |
| 3 — Polished phone | §19 | Not started |
| 4 — Wear remote | §19 | Not started |
| 5 — Release hardening | §19 | Not started |

Nothing runs on a device yet — no device is connected to the development
machine. Everything below is verified by build and JVM unit tests only. Do not
claim instrumentation or screenshot coverage until that changes.

### Built so far

| | Commit |
|---|---|
| Gradle scaffold, design tokens, bilingual string resources | `ebbd5bb` |
| Convention plugins; duplicated module config removed | `7767f2c` |
| Archivo + Manrope replacing platform fallback fonts | `e412888` |
| KSP, Hilt, Room as convention plugins | `8512c31` |
| Exercise catalog schema and the domain types it maps to | `1b6b346` |

Modules today: `app`, `core:model`, `core:database`, `core:designsystem`.
8 unit tests, all passing. Room schema v1 exported and committed.

---

## Phase 0 — remaining work

Ordered by dependency, not by appetite. Each slice names what it unblocks and
how it is verified, because a slice with no check is a slice that silently
half-lands.

### 0.1 — CI

**Why first.** There is no CI at all right now, so every guarantee in this repo
holds only as long as someone remembers to run `./gradlew test` locally. Every
slice after this one is worth more if it lands behind a check.

Start with the subset of §18 that can run today: assemble both flavours, run
unit tests, Android lint. Add secret scanning and dependency review in the same
pass — they are cheap and Phase 2 introduces API keys.

Defer screenshot and migration tests to the phases that create their subjects.

**Done when:** a pull request cannot merge with a failing build, a failing test,
or a new lint error.

### 0.2 — Navigation shell

Four destinations — Today, Plans, Exercises, Progress — with Coach as a mode
inside the builder rather than a tab (§12; settled, see Decisions).

Empty screens are acceptable here and only here: the shell is the frame every
later feature lands into, and building it once beats retrofitting navigation
around four finished screens.

**Unblocks:** every feature module.
**Done when:** all four destinations are reachable, state survives rotation, and
back behaviour is correct from each tab.

### 0.3 — `core:datastore`

Non-secret preferences only (§7). Language override, theme, unit display,
onboarding-complete. API keys never touch this module — that is Phase 2 and a
different storage mechanism entirely.

**Unblocks:** onboarding, settings, and the language switch that §13 requires to
work without a reload.
**Done when:** preferences survive process death and a test proves the default
values, since a wrong default is invisible until a fresh install.

### 0.4 — Dataset pin and import

The largest slice, and the one that makes the app more than a theme.

1. `dataset-version.toml` — the only file naming the pinned commit (§6). Every
   URL and import path derives from it.
2. `exercises.schema.json` and validation that fails loudly.
3. An import task producing the prepackaged Room database.
4. `media-manifest.json` — id, URLs, SHA-256, byte size, media version,
   attribution.
5. `DatabaseModule.provideDatabase` switches to `createFromAsset`, so a cold
   start does not parse 1,324 records on the main thread (§16).

**This is where the categorical vocabulary gets pinned.** `BodyPart`, `Muscle`
and `Equipment` are slug value classes today precisely because the dataset has
not been read yet. Turning them into enums belongs in this slice, with a data
test asserting every value in the dataset maps to a constant — not before, when
the constants would be guesses.

**Done when:** CI fails on duplicate IDs, a missing translation, a bad path, a
missing hash, or schema drift (§6, step 8).

### 0.5 — `feature:exercises`

Bilingual browsing, search and filters against placeholder media. The first
screen that reads real data end to end, and therefore the first real test of
whether the module boundaries hold.

**Done when:** search and filters work in both locales, and the placeholder
flavour renders correctly with `MediaRef.Unavailable` everywhere.

### 0.6 — Repository documents

`README.md`, `CONTRIBUTING.md`, `SECURITY.md`, `PRIVACY.md`, `LICENSE`,
`NOTICE.md`, `.env.example` (§18). None exist yet.

`PRIVACY.md` cannot be written honestly until 0.4 and Phase 2 settle what
actually leaves the device — write it last, and write it from the code.

### Small, unblocked, do when convenient

- **String parity test.** `values/` and `values-tr/` currently differ by exactly
  one key: `app_name`, deliberately untranslated because it is a brand name.
  That is the correct state, so encode it — a test asserting the key sets match
  except for a named allow-list turns §13's lockstep rule into something the
  build enforces rather than something reviewers remember.
- **Icons.** 56 distinct Material Symbols are referenced by the design system.
  Decision made (vector drawables, not the icon font); import is pending assets.

---

## Decisions already made

Closed. Reopen only with a reason, and update the guideline in the same change.

| Decision | Rationale | Where |
|---|---|---|
| Plans is a tab; Coach is a mode inside the builder | Coach is an input method for building a plan, not a place | Guideline §12 |
| Dynamic/wallpaper colour disabled | Breaks the single-accent rule and the measured AA pairs | `Theme.kt` |
| Vector drawables, not the Material Symbols font | 56 icons used; the font ships ~3,300 glyphs, and `res/font` cannot feed `Icon()` | This file, above |
| Static font weights, not variable | 614 KB vs. ~1.5 MB for weights the tokens never ask for | `Type.kt` |
| Media lives in the manifest, not the catalog tables | Keeps the licensing boundary a type seam, not a convention | `ExerciseEntity.kt` |
| Room v1 is catalog-only | User tables ship with the code that writes them, not before | `RepForthDatabase.kt` |
| No destructive migration, ever | Losing the only copy of a user's history is not an upgrade path | `SchemaExportTest.kt` |
| Categorical values stay slugs until the import | Enum constants written before reading the dataset are guesses | `Exercise.kt` |
| Hilt pinned below latest | Newer releases ship a plugin built against a newer Kotlin stdlib | `libs.versions.toml` |

Still open, and fine to leave open (§21): final application ID, accent colour,
app icon, and the exact licence.

---

## Known risks

- **The dataset is unread.** Every count, field name and categorical value in
  the guideline comes from upstream documentation, not from the file. Expect
  0.4 to contradict something, and treat the dataset as the authority when it
  does.
- **No device.** Instrumentation, screenshot, and paired-watch tests cannot run
  here at all. Phase 4 is unverifiable on this machine; plan for that rather
  than discovering it.
- **KSP is pinned to the Kotlin version.** Bumping `kotlin` without bumping
  `ksp` in the same commit fails the build in a way whose message does not
  mention the real cause.
- **Phase 2 introduces secrets.** Key handling must land with its own tests and
  a CI secret scan on day one, not as hardening later — §20 requires keys to be
  absent from Room, DataStore, logs, backups, exports, source, CI, and watch
  messages, and that is far cheaper to build in than to retrofit.
