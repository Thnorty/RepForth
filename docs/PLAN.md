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
| 0 — Foundation | §19 | **In progress.** Scaffold, tokens, DI, persistence, CI and navigation done; preferences and the dataset outstanding |
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
| Guard tests re-run when the files they guard change | `28f7a26` |
| English/Turkish string parity test | `c61b253` |
| CI: wrapper validation, build, tests, lint, dependency review | `cfb85b6` |
| Icon indirection, with two hand-authored tab icons | `9f795a8` |
| Navigation shell: four tabs, settings, back stack | `1075f23` |

Modules today: `app`, `core:model`, `core:database`, `core:designsystem`.
15 unit tests, all passing. Room schema v1 exported and committed.

---

## Phase 0 — remaining work

Ordered by dependency, not by appetite. Each slice names what it unblocks and
how it is verified, because a slice with no check is a slice that silently
half-lands.

### 0.1 — CI — **done**

`.github/workflows/ci.yml` runs wrapper validation, a secret-hygiene check,
`assemblePlaceholderDebug`, `test`, and `lint`, plus dependency review on pull
requests. `licensed` is deliberately not built: it has no assets yet, and once
it does they must not reach a public runner (§18).

Two things this slice turned up, both worth remembering:

- **`gradlew` was committed without the executable bit**, so `./gradlew` would
  have failed with "Permission denied" on the first Linux runner. Fixed with
  `git update-index --chmod=+x`.
- **The guard tests did not re-run when the files they guard changed.** They
  read those files at runtime through `java.io.File`, which Gradle cannot see,
  so the test task reported UP-TO-DATE and passed on exactly the edit it exists
  to catch. `configureGuardTestInputs()` now declares them. Both guards were
  re-verified by mutation afterwards.

**Still outstanding from §18**, deliberately not done here:

- *Formatting and static analysis.* ktlint or detekt needs a configuration and a
  cleanup pass over existing code; bundling that into the CI slice would have
  hidden a formatting sweep inside an infrastructure change. Its own slice.
- *Screenshot tests and Room migration tests.* Nothing to screenshot yet, and
  only one schema version exists. They belong to the phases that create their
  subjects.

**Requires the maintainer, in GitHub repository settings — a workflow file
cannot do these:**

- Enable secret scanning and push protection (free on public repositories).
  The workflow's secret-hygiene step only catches credential-shaped *filenames*;
  it does not scan file contents for token formats.
- Require the `Build and test` and `Validate Gradle wrapper` checks to pass
  before merge, on a protected `main`. Until that is set, CI reports failures
  but cannot stop them landing.

### 0.2 — Navigation shell — **done**

Four tabs, Settings reached from the top bar, type-safe `@Serializable` routes,
and tab switching that saves and restores per-tab state. `RfIcons` gives the app
one place where an icon is chosen, so the imported set can be dropped in later
without touching call sites.

`NavigationStructureTest` fails if a fifth tab appears. That is deliberate: a
fifth tab is a one-line change that looks like a UI tweak and is actually a
reversal of the §12 Coach decision, so it should have to be argued.

**Not verified here, and cannot be:** back behaviour, rotation, and state
restoration are runtime properties needing an instrumented device. The structure
is unit-tested; the behaviour is not. First thing to check when a device is
available.

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
| CI builds `placeholder` only | §20's claim is that the public source builds with no private credentials; `licensed` assets must not reach a public runner | `.github/workflows/ci.yml` |
| Guard tests declare their files as task inputs | Otherwise the task is UP-TO-DATE and passes on the exact change it guards | `GuardTestInputs.kt` |

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
- **Nothing enforces CI yet.** Branch protection is a repository setting, so
  until a maintainer turns it on, a red build reports the failure but does not
  prevent the merge.
- **Phase 2 introduces secrets.** Key handling must land with its own tests and
  a CI secret scan on day one, not as hardening later — §20 requires keys to be
  absent from Room, DataStore, logs, backups, exports, source, CI, and watch
  messages, and that is far cheaper to build in than to retrofit.
