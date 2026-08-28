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
| 0 — Foundation | §19 | **In progress.** Everything but the dataset is done; 0.4 and 0.5 remain |
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
| Non-secret preferences, with the theme setting wired through | `4bb0bbe` |
| Dataset pin, enforced as the single source of the commit | `b962bc3` |
| Real categorical vocabulary: BodyPart, Equipment, Muscle | `3c77849` |

Modules today: `app`, `core:model`, `core:database`, `core:datastore`,
`core:designsystem`. 31 unit tests, all passing. Room schema v1 exported and committed.

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

### 0.3 — `core:datastore` — **done**

Preferences DataStore holding theme, language override, units, keep-screen-on,
reduced motion, haptics and onboarding-complete. Types live in `core:model`, so
a screen reading the theme does not depend on the storage module.

`MainActivity` applies `themeMode`, which makes this the first preference that
visibly does something. `keepScreenOn` is deliberately *not* wired: the string
says "while a workout is running", so applying it app-wide would be the wrong
behaviour. It belongs to the active workout screen in Phase 1.

Every read falls back to its default instead of throwing — a renamed enum
constant leaves an unparseable value on devices that wrote the old one, and
those users should get the default, not a crash.

**Tests run against an in-memory DataStore, not a file.** A file-backed store
fails on a Windows host: DataStore renames a temp file onto the target and
Windows refuses that once the target exists, so every second write threw. A test
that passes on CI and fails on the maintainer's machine is worse than one
covering slightly less. What that gives up is proof of real persistence — that
is DataStore's guarantee rather than this project's, and confirming it needs a
device. Add it to the on-device checklist with the navigation behaviour.

### 0.4 — Dataset pin and import — **in progress**

**Done:** `dataset-version.toml` holds the pin, and `tools/verify-dataset-pin.sh`
fails the build if the SHA appears anywhere else (it found two copies in the
guideline on its first run). `tools/fetch-dataset.sh` downloads the whole
repository as one tarball into a gitignored cache. The categorical vocabulary is
now real enums, locked to the data by a committed vocabulary file.

**Remaining:** the importer itself —

1. Validate `exercises.json` against the upstream `data/exercises.schema.json`.
2. Emit the prepackaged Room database. Build the DDL from Room's own exported
   schema JSON, including `room_master_table` and its identity hash, so the
   packaged file cannot drift from the entity definitions.
3. Emit `media-manifest.json`: id, both URLs, SHA-256, byte size, media version,
   attribution once.
4. Switch `provideDatabase` to `createFromAsset`.
5. Data tests per §17: unique IDs, both languages present, every manifest entry
   referenced, every referenced file hashed.

#### What reading the dataset actually changed

The guideline's field names and counts came from the upstream README. Reading the
data corrected several:

| Assumption | Reality |
|---|---|
| We write `exercises.schema.json` | It ships upstream, so validation uses its author's own contract |
| GIFs live in `gifs/` | `videos/` |
| Two languages | Ten (`en es it tr ru zh hi pl ko fr`), all required; we keep two per §6 |
| `instructions` is a summary | Exactly `steps.joinToString(" ")` in all 1,324 records |
| `attribution` varies per record | One identical string across all 1,324 |
| Categoricals share a vocabulary | `target`, `muscle_group` and `secondary_muscles` disagree with each other |

Confirmed as documented: 1,324 records, unique IDs, both languages non-empty
everywhere, and every referenced image and video present in the tree.

#### Open for review: the muscle synonym map

`Muscle` has one constant per upstream string (50), and a hand-written map
collapses nine unambiguous synonym pairs — `abdominals`→`abs`,
`quadriceps`→`quads`, `latissimus dorsi`→`lats`, `trapezius`→`traps`,
`deltoids`/`shoulders`→`delts`, `chest`→`pectorals`, `inner thighs`→`adductors`,
`ankle stabilizers`→`ankles`.

Deliberately **not** merged, because these are nested rather than synonymous and
merging them is a product decision about filter behaviour: `lower abs` vs `abs`,
`rear deltoids` vs `delts`, `soleus` vs `calves`, `upper chest` vs `pectorals`,
`rhomboids` vs `upper back`, `brachialis` vs `biceps`, `wrist extensors` and
`wrist flexors` vs `forearms`, `grip muscles` vs `hands`.

Worth a decision before `feature:exercises` ships filters: should a user asking
for "abs" see `lower abs` exercises too?

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
