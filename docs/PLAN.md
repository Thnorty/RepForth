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
| 0 — Foundation | §19 | **Complete.** All six slices done |
| 1 — Local workout core | §19 | **Complete.** Engines, data, all six screens, manual builder, and local constraints |
| 2 — AI providers | §19 | **Complete.** Storage, settings, contracts, transport, orchestration, and Coach UI |
| 3 — Polished phone | §19 | **In progress.** 3.1 downloader and cache, 3.2 media display, 3.3 accessibility, 3.4 motion tokens done. Motion *applied*, progress visuals and baseline profiles remain |
| 4 — Weekly plans | §19 | **Complete.** Schema and migration, contract v4, Coach, review, Plans and Today, and a week that reopens |
| 5 — Wear remote | §19 | Not started. No hardware to test against |
| 6 — Release hardening | §19 | **In progress**, deliberately early. 6.1–6.3: 50 goldens and enforced CI |

**Two devices have been used, and they disagree.** A Galaxy S23 on Android 14
and a Xiaomi on Android 11 — `AGENTS.md` carries the differences, which are
larger than they sound. Every screen has been exercised on hardware by hand and
by `adb input tap`.

**Nine defects have been found on a device and by nothing else:** the launch
crash from Auto Backup restoring an old database, onboarding drawing under the
camera cutout, a slider whose sixth value could not be selected, the Hilt crash
when the locale was overridden, and the builder's Save button sitting behind the
keyboard — enabled, invisible, and untappable, so a plan could not be saved at
all; and three on the AI settings screen (2.3b): a keyboard that rewrote a
typed URL, switch rows that only responded on the switch itself, and a
disclosure that looked like a heading; and a bad Gemini key reported as a
parsing failure rather than a rejected key (2.3c), which no local-server test
could have caught because Gemini answers 400 where the shared mapping expected
401.

Fourteen instrumentation tests now exist and pass on the Galaxy S23 — six in
`:app`, eight in `core:secrets`. Of the six, three open screens and would not
have caught any of the nine; three interact — type, tap, save — and the keyboard
one is a direct regression guard for the fifth. Fifty screenshot goldens now
cover ten screens in both languages at both font scales (6.1, 6.2, 6.3), and
recording them found five more defects.

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
| Body map: 19 regions, authored artwork, Compose component | `b58a631`, `f6c6c82` |
| Dataset import: prepackaged catalog, media manifest, data tests | `d91241b` |
| Exercise catalog screen: search, filters, bilingual terms | `3eb99aa` |
| Repository documents; media licence terms corrected | `a0d9ba2` |
| **Phase 1** — user-data schema | `59ceb30` |
| **Phase 1** — profile data layer, two clocks | `af8ddcf` |
| **Phase 1** — rules engine and validator | `ff9db3c` |
| **Phase 1** — workout state machine and persistence | `4be4548` |
| **Phase 1** — shared target mapping | `14681f5` |
| Auto Backup turned off; it restored a database Room refuses to open | `b3a9ca2` |
| `codex` subagent wrapper, on a report contract shared with `agy` | `8f0d64d` |
| **Phase 1** — onboarding questionnaire, and the profile gate | `5ab9eb0` |
| Onboarding insets, the unreachable sixth day, wording gaps | `fe36aaa` |
| Equipment explained and grouped; muscles on the body map | `be97627` |
| Two bugs from a `codex` audit; the synonym rule put in one place | `815b43d` |
| A review of every answer before the profile is written | `5ba58aa` |
| Body weight pre-selected, so nothing is translated on save | `77162c3` |
| **Phase 1** — the workout builder and the plan library | `c445c76` |
| **Phase 1** — the running workout screen | `614f638` |
| **Phase 1** — export, import and the two deletes | `98ea28b` |
| **Phase 1** — the Progress tab | `77a71bf` |
| A second run of a plan no longer erases the first one's history | `91333a9` |
| Workouts survive the screen going off, behind one owner of the engine | `68d4d5e` |
| Notification permission asked for in onboarding, with its reason | `24a0a91` |
| The icon set, a launcher icon, and back that asks before ending | `7360ce0` |
| **Phase 1** — the Settings screen | `a9b6bde` |
| Language and units do something; reset starts over | `b954a5e` |
| The activity stays reachable when the locale is overridden | `032feee` |
| Instrumentation tests, watched failing | `7c32abc`, `7ccea64` |
| **Phase 1** — the Today tab; the last placeholder deleted | `75ba4a7` |
| Pinned footers kept out from under the keyboard | `07c9c97` |
| Counted nouns use plurals, in both languages | `1607efc` |
| **Phase 1** — Coach: the rules engine reaches the catalog | `9a4b829` |
| Instrumentation tests that interact, and a FAB nobody could name | `2d0ba1c` |
| **Phase 2** — secret storage, and a CI scan for key-shaped content | `2eb17e0` |
| **Phase 2** — provider settings, and the endpoint rule that guards them | `1113597` |
| **Phase 2** — the two provider adapters, and a connection test that explains itself | `2eb5796` |
| **Phase 2** — cleartext to the user's own network, guarded in code | `181e5cc` |
| Three defects the phone found on the provider screen | `8c42019` |
| The keyboard test stopped measuring the frame before the padding | `e5090e7` |
| A key only where a key is needed; a bad one named correctly | `ef43cc2` |
| **§8 amended** — the address rule removed entirely | `7ce01b1` |
| **Phase 2** — validated AI workout contract and corrected CI trigger | `5bb27bc` |
| **Phase 2** — structured provider generation over one shared schema | `ee9d19e` |
| **Phase 2** — Make Coach generation AI-only and retryable | `a2c5775` |
| **Phase 3** — on-demand media downloader and bounded cache | `eda895a` |
| **Phase 3** — image display and GIF playback in catalog, builder and session | `22383c9` |
| **Phase 3** — the accessibility pass, and a guard lint could not be | [#2][pr2] |
| **Phase 3** — motion tokens, and a reduced-motion switch that works | [#3][pr3] |
| **Phase 4** — a week of training, Room v2, contract v3, Today and Plans | `f9ce1de` |
| **Phase 4** — the request restructured; names sent, derivable fields dropped | `19aa2dd` |
| **Phase 4** — a week's day stops being saved out of its week | `163de35` |
| **Phase 4** — five fixes in Settings, two of them layout bugs on a phone | `0250825` |
| **Phase 4** — an editable schedule, and Coach showing what it builds | `fca0200` |
| **Phase 4** — a saved week reopens, and Today follows one | `1a91425` |
| **Phase 4** — discard asked only on a real change; Coach led with | `2e1abc1` |
| **Phase 6** — screenshot tests, and the two defects they found | `d9fcd8e` |
| **Phase 6** — the remaining screens, and three more defects | `e1188bf`, `1c809f9` |
| **Phase 6** — CI enforced, and goldens that survive the runner | `cc5cec7`, `4654808` |

[pr2]: https://github.com/Thnorty/RepForth/pull/2
[pr3]: https://github.com/Thnorty/RepForth/pull/3

Rows above this one name a commit because they were pushed straight to
`master`. From 4.11 onward `master` only accepts squash merges, whose hash is
not knowable while the change is being written — so newer rows name the pull
request instead.

Modules today: `app`, `core:ai`, `core:common`, `core:database`, `core:datastore`,
`core:designsystem`, `core:exercise-data`, `core:media`, `core:model`, `core:rules`,
`core:testing`, `core:transfer`, `core:user-data`, `core:workout`,
`feature:builder`, `feature:exercises`, `feature:history`, `feature:home`,
`feature:onboarding`, `feature:session`, `feature:settings`.
439 unit tests across 61 classes, plus fourteen instrumentation tests
watched passing on a Galaxy S23 — six in `:app`, eight in `core:secrets` — and
five migration tests in `core:database` that have **never been run**, because no
device has been attached since they were written. Room schema v2 exported and
committed.

Counted from the JUnit XML and de-duplicated across build variants, by the class
name each `TEST-*.xml` reports. Reproduce it with:

```
./gradlew test && python -c "import glob;from xml.etree import ElementTree as ET;s={ET.parse(f).getroot().get('name'):int(ET.parse(f).getroot().get('tests')) for f in glob.glob('**/build/test-results/**/TEST-*.xml',recursive=True)};print(len(s),sum(s.values()))"
```

The method is written down because the count was briefly stated three different
ways in this file — 436, 427 and 428 — and none of them matched the suite. A
number nobody can reproduce is worse than no number.

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

The workflow originally watched pushes to `main` while the repository's default
and only branch is `master`, so it had never run on a push. The trigger and the
Gradle shared-cache writer now name `master`; pull-request behaviour is unchanged.
The first corrected push run, `33303060115`, passed wrapper validation, assemble,
unit tests, and lint on `master`.

That run also reported maintenance debt which is not a failure today: GitHub is
forcing several Node-20 actions onto Node 24, and `actions/setup-java@v4` is now
deprecated in favour of v5. Upgrade those actions as a separate CI-only change.

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
  The workflow catches credential-shaped filenames and the two provider-key
  formats this app accepts in tracked content; GitHub's scanner covers broader
  formats and push protection can stop a secret before it enters history.
- Require the `Build and test` and `Validate Gradle wrapper` checks to pass
  before merge, on a protected `master`. Until that is set, CI reports failures
  but cannot stop them landing.

### 0.2 — Navigation shell — **done**

Four tabs, Settings reached from the top bar, type-safe `@Serializable` routes,
and tab switching that saves and restores per-tab state. `RfIcons` gives the app
one place where an icon is chosen, so the imported set can be dropped in later
without touching call sites.

`NavigationStructureTest` fails if a fifth tab appears. That is deliberate: a
fifth tab is a one-line change that looks like a UI tweak and is actually a
reversal of the §12 Coach decision, so it should have to be argued.

**Verified on a Galaxy S23 (Android 14):** tab state survives switching, back
from Settings returns to the previous tab, and back from the start destination
exits rather than cycling.

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

### 0.4 — Dataset pin and import — **done**

`dataset-version.toml` holds the pin and `tools/verify-dataset-pin.sh` keeps it
the only copy. `tools/fetch-dataset.sh` downloads the repository as one tarball
into a gitignored cache. `tools/import-dataset.py` validates every record
against the schema upstream ships, then emits:

| Artifact | Size | Committed |
|---|---|---|
| `core/database/src/main/assets/repforth.db` | 2.5 MB | yes — Room copies it on first launch |
| `dataset/media-manifest.json` | 0.45 MB | yes — 2,648 SHA-256 hashes and sizes |
| `dataset/import-report.json` | 1 KB | yes — what normalisation did (§6) |

The DDL comes from Room's own exported schema, identity hash included, so a
packaged database built from different entities is refused rather than half-read.
`PackagedCatalogTest` inspects the asset over JDBC — 10 data tests, no device.

**Where the manifest lives is temporary.** It belongs to `core:media`, which does
not exist yet, so it sits in `dataset/` and is not yet an app asset. Nothing
reads it until the media layer lands in Phase 3.

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

#### Settled: synonymous muscles are merged for filtering

`Muscle` keeps one constant per upstream string — all 50 — so storage never
loses which word the source used. A reviewed map collapses nine unambiguous
synonym pairs for filtering: `abdominals`→`abs`, `quadriceps`→`quads`,
`latissimus dorsi`→`lats`, `trapezius`→`traps`, `deltoids`/`shoulders`→`delts`,
`chest`→`pectorals`, `inner thighs`→`adductors`, `ankle stabilizers`→`ankles`.

Terms that are *nested* rather than synonymous stay separate: `lower abs`,
`rear deltoids`, `soleus`, `upper chest`, `rhomboids`, `brachialis`,
`wrist extensors`/`wrist flexors`, `grip muscles`.

For scale: 88 categorical terms in total — 10 body parts, 28 equipment, 50
muscles. The 50 are a union of `target` (19), `muscle_group` (29) and
`secondary_muscles` (40), which share 38 terms between them.

#### Proposed: a body map for muscle selection

Show *where* a muscle is while the user picks it, rather than a text chip alone.
Not scheduled — recorded so the decision is not re-derived.

The approach that fits this project is an authored vector body map: front and
back silhouettes as vector drawables with one path per region, tinted from the
colour tokens so it themes for free and costs a few KB. The alternatives are
worse — third-party anatomy art brings a licence this project would have to
honour, and the dataset itself ships only exercise thumbnails and animations, no
anatomy diagrams at all.

Two things it depends on:

- **It needs the synonym merge**, which is why the two decisions above belong
  together. A body map has exactly one abs region; `abs` and `abdominals` cannot
  be separate highlightable areas because they are the same place.
- **Regions are fewer than muscles.** Roughly 15–20 drawable areas cover the 50
  terms, and some do not map to a place on a body at all — `cardiovascular
  system`, `grip muscles`, `ankle stabilizers`. Those need a chip fallback, so
  the map supplements the chips rather than replacing them.

It also must not become colour-only selection (§12) or an unlabelled graphic:
every region needs its text label and a content description.

The artwork is a design asset, so it should be authored in Claude Design
alongside the rest of the system and ported, rather than hand-drawn in Kotlin.

### 0.5 — `feature:exercises` — **done**

Search plus body-part, equipment and muscle filters over the packaged catalog,
in both languages. `core:exercise-data` owns the repository so features never
touch a DAO; `feature:exercises` owns the screen.

The 88 categorical terms have display names in both locales, generated into an
exhaustive `when` rather than resolved by name at runtime.

**Two limitations, stated in the UI rather than hidden.** Exercise names are
English in every locale — the dataset translates instructions into ten languages
but ships one `name` per record, so search matches English names. And the body
map is paired with muscle chips rather than replacing them, because
`cardiovascular system` is not a place on a body.

**Verified on a Galaxy S23 (Android 14):** the packaged catalog loads and reports
1,324 exercises, which is the proof that Room accepted its identity hash on real
hardware. Filters compose correctly — chest gives 163, chest plus dumbbell gives
44. Search is smooth at the 250 ms debounce, Turkish reads correctly, and nothing
clips at maximum system font size.

Device testing found two defects, both fixed. Rotating with the list scrolled
down collapsed the filter panel, because its expanded flag was `rememberSaveable`
inside a LazyColumn item and LazyColumn evicts saved state for items disposed
long enough. And a body-map tap looked like it did nothing: it selected several
muscles scattered through a row of 41 chips, so selected muscles now get their
own row above the rest.

It also disproved a worry. The neck region measures about 35 dp, under the 48 dp
minimum, and I expected it to be hard to hit. On the device it is not.

### 0.6 — Repository documents — **done**

README, NOTICE, PRIVACY, CONTRIBUTING, SECURITY, `.env.example`.

**No `LICENSE` file, deliberately.** The project licence is an open decision
(§21) and belongs to the maintainer; README says so rather than one being chosen
by default. To adopt the recommended Apache-2.0, fetch the canonical text —
do not let it be typed from memory.

#### The media terms are stricter than §6 assumed

Reading upstream's `NOTICE.md` at the pinned commit: the imagery is Gym visual's,
redistributed upstream under a **separate written permission granted to that
project**, at 180×180, with attribution required. Upstream states plainly that
cloning its repository is not a licence.

**So RepForth holds no rights to the media.** The `placeholder` flavour is a
legal requirement, not a convenience, and `licensed` cannot be distributed
without RepForth obtaining its own licence from Gym visual. §6 has been corrected
to say this.

### Small, unblocked, do when convenient

- **Icons.** 56 distinct Material Symbols are referenced by the design system.
  Decision made (vector drawables, not the icon font); import is pending assets.

---

## Phase 1 — Local workout core

The phase that makes RepForth useful. §19 requires it to be **fully useful
offline**: profile, manual builder, rules engine, the active-session state
machine, timers, history, and export/delete.

Ordered by dependency. Each slice is shippable on its own — the app should be
usable at every step, not only at the end.

### Status after the first overnight run

**Done, and unit-tested without a device:**

| Slice | State |
|---|---|
| 1.1 User-data schema | ✅ nine tables, five structural guards |
| 1.2 Profile | ✅ model, DAO, repository, and the onboarding screen — 8 steps, device-tested |
| 1.3 Templates | ✅ model, DAO, repository, and the builder — plus a real Plans library |
| 1.4 Rules engine | ✅ complete — 26 tests |
| 1.5 Session engine | ✅ state machine, persistence, **and the running workout screen**. No service yet |
| 1.6 History | ✅ statistics in `core:workout`, Progress tab in `feature:history` |
| 1.7 Export / import / delete | ✅ `core:transfer`, **and the Settings screen that calls it** |
| 1.8 Coach (rules-only) | ✅ the engine reaches the catalog, and the builder reaches the engine |

**Deliberately not attempted without a device.** The engines are provable on the
JVM; screens are not. Four things remain, and each needs hardware to be worth
trusting:

1. ~~**Onboarding screen**~~ — done, and tested on the phone through two rounds
   of feedback. Eight steps ending in a review of every answer, because the
   first version gave no way to check what it had recorded.
2. ~~**Workout builder screen**~~ — built, with the Plans library that opens
   it. Not yet seen on a device.
3. ~~**Active workout screen**~~ — built on the tested engine. Not yet seen on
   a device.
4. ~~**Settings screen**~~ — built. Appearance, workout behaviour, and the four
   data actions. The file picker itself is the system's and needs a device.
5. ~~**Today tab**~~ — built. The workout in progress if there is one, otherwise
   the stalest saved plan with a quick start, otherwise a way to build one.
   `PlaceholderScreen` is deleted: every destination is now a real screen.
6. ~~**Foreground service and ongoing notification**~~ (§10) — built and
   tested on hardware. A `specialUse` foreground service owned by a singleton
   engine, so the screen and the service cannot disagree about the session, and
   the countdown keeps running with the screen off.
7. ~~**Coach, the rules-only half**~~ (§3, §8) — built. See 1.8 below.

**The version bump nobody can skip.** Adding the user tables changed Room's
identity hash. Anyone with the previous build installed must uninstall before
installing again — Room refuses a database whose hash disagrees with the code,
which is the protection that makes this safe after release and an inconvenience
before it.

**And uninstalling was not enough.** Verified on the device: a clean uninstall
and reinstall still crashed at launch, because `android:allowBackup` was at its
default of `true` and Android restored the previous schema's database from
Google's servers into a directory Room had just been told to fill from the
packaged asset. Room found hash `e66cc39…` where it wanted `0020ed9c…` and threw.

Fixed by turning Auto Backup off, which §4 wanted anyway — cloud backup is an
explicit MVP non-goal, and §7 asked for this decision to be made rather than
defaulted. `BackupPolicyTest` now holds it, and the guard was proven to fail on
an XML-only edit. `PRIVACY.md` no longer says backup behaviour is unspecified.

Worth noting what this says about the guards: every schema test passed, the
packaged asset was correct inside the APK, and the app still could not start.
The tests cover what the build produces, not what the platform does to it
afterwards. That gap is what instrumentation tests are for.

### 1.8 — Coach, the rules-only half — **done**

The rules engine was finished, tested twenty-six ways, and had no callers. Its
`generate` takes `List<ExerciseCandidate>` and `ExerciseRepository` had no method
that produced one, so the app could not build a workout at all — §3 lists
rules-only generation as MVP, and it was the last thing missing from it.

Three pieces, all small, which is what made it easy to leave undone:

- `ExerciseDao.candidates()` — a projection one column wider than the catalog
  list's and every relation narrower than the detail query's. Unfiltered on
  purpose: the engine's own filters are what produce §8's audit trail, and a
  `WHERE` clause here would discard the reasoning before anyone could be shown
  it.
- `ExerciseRepository.candidates()` — two flat reads grouped once, rather than a
  join that returns a row per secondary muscle and leaves the grouping to do
  anyway.
- Coach itself, inside the builder, because §12 already decided it is a mode
  rather than a screen. It asks one question — which muscles, optional — and
  drops the result in as ordinary editable rows through the same `toDrafts`
  the saved-plan path uses. Nothing is written until the user saves.

**It asks one question and no more.** The profile already holds the goal, the
experience, the session length and the equipment. Asking again would be asking
someone to repeat themselves, and letting the answers disagree with onboarding
would be worse than not asking.

**A failure names the constraint that caused it.** The engine records a reason
per rejected candidate; Coach shows the one that dominated, so someone whose
whole catalog was refused on equipment is told about equipment rather than
"nothing matched". Verified on the device: profile defaults produced a 2×5×5
plan at 180s rest, 32 minutes against the session ceiling, saved and listed.

### Known risks in this phase

- **1.5 is where the schedule goes wrong.** Process-death recovery and
  foreground-service compliance are both areas where the correct answer changed
  across recent Android versions. Budget for reading current documentation
  rather than recalling it.
- **The device is a Galaxy S23 on Android 14.** Samsung's background-execution
  behaviour is stricter than stock. A workout notification that survives on a
  Pixel may not survive here, and this is the device that matters.
- **`run-as` is blocked by Knox**, so the app's database cannot be pulled off the
  device for inspection. Debugging persistence bugs will need an in-app path or
  an export.
- **Rest does not advance while nothing is watching.** The countdown ticks
  from the screen, so a backgrounded workout stops counting down. That is the
  gap §10's foreground service closes, and until it exists the running workout
  is only correct while it is on screen. It is stated in `SessionViewModel`
  rather than hidden, and it is the main reason the service is the next slice
  rather than a polish item.
- **A test that only opens a screen proves almost nothing.** The three original
  instrumentation tests were green while a workout could not be saved at all.
  The button composed, measured, reported itself enabled, and sat under the
  keyboard — and `assertIsDisplayed()` would have agreed it was fine, because
  the window is never resized when the IME opens, so Compose's root keeps its
  full height and the button stays inside it. Only the window insets know the
  difference. Any future guard for something the platform draws over has to ask
  the platform, not the composition.
- **`waitForIdle()` does not wait for this app to have decided what to show.**
  Until the profile is read, `MainActivity` renders nothing on purpose, and that
  state comes from a Room flow that Compose's idling resource cannot see — so
  `waitForIdle()` returns onto a root node with no children. Three tests raced
  it, asked whether onboarding was showing, were told no because *nothing* was
  showing, and walked into an undrawn screen. `awaitFirstScreen()` exists for
  this; use it before asking what is on screen.
- **String parity was guarded in one module out of three.** `feature:exercises`
  shipped unguarded Turkish for its whole life, and nobody noticed because the
  guard that existed was passing. The checks now live in `core:testing` as a
  contract each module subclasses; string and plurals keys share the same
  parity and duplicate checks, and plurals must declare matching quantity sets
  in both locales. The lesson generalises: a guard that covers one module is not
  a guard on the rule, and adding a module is the moment to ask which guards it
  is missing.
- **Three of the last four defects were invisible to the JVM.** The Auto Backup
  crash, the insets, and the unselectable sixth day were all found on hardware,
  and the unit test written for the last of them passes with the bug present.
  Anything that renders or that the platform touches is currently unguarded.
- **A UI promise and a stored value can disagree without either looking wrong.**
  The equipment step said "body weight only" while saving a set the rules engine
  read as unrestricted. Both halves were internally consistent, both had tests,
  and the contradiction lived in the space between them. When a screen states a
  consequence, something should assert the consequence, not the wording.

---

### What the interaction tests found

Writing them turned up a defect nothing else had: the app's only floating action
button reached accessibility services as an unnamed "Button". Material3 wraps an
extended FAB's icon and text in `clearAndSetSemantics {}`, so the words drawn
across it never reach the merged node — the test could not find the button by
the label written on it, and neither could TalkBack. The label is now set on the
button as well as drawn inside it.

Worth generalising: a test that cannot find a control by its visible name is
usually reporting an accessibility bug rather than a test-writing problem.

---

## Phase 2 — AI providers

§19 wants secure key storage and provider settings, a Gemini adapter, a generic
OpenAI-compatible adapter, structured contracts with validation, a local
fallback, and the coach UI. Ordered so that nothing handles a key before there
is somewhere safe to put it.

### 2.1 — `core:secrets` — **done**

Tink AEAD under a non-exportable Android Keystore master key. Ciphertext in a
file under `filesDir`, deliberately not DataStore and not Room: §20 requires
keys to be absent from both, and "it is only the encrypted form" is the argument
that erodes a rule like that.

The secret's id is the AEAD's associated data, so ciphertext copied from one
provider's slot to another fails to decrypt instead of quietly answering as the
wrong key.

Eight instrumentation tests, because there is nothing to test on the JVM: the
whole point is that the master key lives in platform storage, and mocking that
away leaves the assertion "Tink encrypts things", which is Google's test rather
than ours. Two were watched failing — removing the associated data fails the
slot-binding test, and writing plaintext fails the assertion that the raw secret
never reaches disk.

CI now greps tracked *content* for the two key shapes this app accepts, not just
credential-shaped filenames. Verified against a planted key.

**Nothing calls it yet, and that is the risk to watch.** This is exactly the
shape the rules engine was in for a whole phase — finished, tested, unreachable.
2.2 is what makes it reachable, and it should follow immediately rather than
after anything else.

### 2.2 — Provider settings — **done**

`core:secrets` is reachable. A new `core:ai` module holds `ProviderRepository`,
the one place a provider's settings and its key are brought together; nothing
else in the app touches `SecretStore`.

**The persisted type and the in-flight type are separate, and that is the whole
design.** `ProviderSettings` — provider, model, address, timeout, cleartext flag
— goes into plain-text DataStore. `ProviderConfig` is settings plus key, built
per call and written nowhere. Two guards hold the line: one fails if a field
whose name reads like a credential appears on the persisted type, the other
fails if `ProviderConfig` ever prints its key, which a `data class` would do by
default and which would put the key in every log line that touched it.

**Superseded by 2.3d — `EndpointPolicy` was deleted and §8 amended. Kept for the
reasoning, not as a description of the code.**

**`EndpointPolicy` lives in `core:model`, not in the settings screen.** §8 allows
a developer setting for cleartext to a local model server; the trap is reading
that as "cleartext is allowed now". It is not — `http://` to a public host stays
refused whether the switch is on or off, and hostnames are never resolved to
decide what is local, because a name an attacker controls can answer differently
the second time. A check that lived only in the text field would be bypassed by
the next caller.

**Reset now deletes provider keys, and `ResetCoverageTest` keeps it that way.**
The comment promising that test had been in `DataTransfer.kt` for a phase; the
test did not exist. It reads the constructor, so a store added later fails by
existing rather than by being remembered. The failure it prevents is silent and
has a consequence off the phone: a device that is reset and handed on would
otherwise still hold the previous owner's API key, billable to their account.

The Settings screen gained an AI section leading to a screen of its own, with
the §8 disclosure first rather than in small print — this is the one place in the
app where something the user typed leaves the device, and the rest of RepForth
promises loudly that nothing does. The key field is write-only: the UI state has
no field for a stored key, so "never shown again in full" is a property of the
type rather than a rule the screen has to remember. It is cleared the moment it
is saved.

Six new test classes, 43 tests. Watched failing: the persisted-type guard (with
an `apiKey` field added), the redaction guard (with `ProviderConfig` made a data
class), the locality check (with cleartext then allowed to five public hosts),
the clear-scope test (with `clear()` wiping the shared preferences file), the
reset guard and the reset behaviour test (both, with `providers.deleteAll()`
removed), the field-clearing test (with the clear removed from `onSaveKey`), and
the new title guard.

**That title guard caught something on its first run.** `titleRes` in the app
shell ends in `else -> settings_title`, and its own comment says every
destination is named explicitly — `Destination.Settings` was not, it was riding
the fallback. So was the new screen. Both are named now.

**Not in this slice: "Test connection".** §8 lists it under settings, but it
needs a real HTTP call to mean anything, and a button that reports success
against a fake would be worse than no button. It lands with 2.3.

### 2.3 — Provider adapters — **done**

`AiProvider`, `GeminiProvider`, `OpenAiCompatibleProvider`, `FakeAiProvider`,
and one OkHttp client behind `ProviderHttp`. The app now has an `INTERNET`
permission, declared in the app manifest rather than in `core:ai` so that "does
this thing talk to the internet, and why" is answerable from the one file an
auditor opens.

**"Test connection" lists models rather than generating anything.** It costs no
tokens, so the button cannot spend the user's money, and it tells the three
possible problems apart — unreachable, key rejected, model not offered — where a
failed generation would only say that something went wrong. A server with no
model list is reported as "connected, could not confirm the model", because
several OpenAI-compatible servers do not implement the endpoint and calling that
a failure sends the user to fix a working setup.

**Only `testConnection` is on the interface.** §8's version also has
`generateWorkout` and `coach`; their contracts are 2.4's subject, and writing
them now would mean guessing a shape that slice derives properly and then having
two of them.

Twenty-three new tests. The adapters run against a local `MockWebServer`, so the
whole networking layer is verifiable on the JVM — it would otherwise have been
the second thing here that needs a phone in hand.

#### Three things this slice got wrong first

- **The adapter tests were talking to Google.** `GeminiProvider` took its URL
  from `ProviderConfig`, which for Gemini resolves to the fixed endpoint — so
  every run sent a fake key to `generativelanguage.googleapis.com` and asserted
  against its 400. It looked like a MockWebServer bug for a while. The endpoint
  is now a constructor parameter defaulting to the constant, and a test asserts
  Gemini ignores any stored address, which is a safety property: if it honoured
  one, anything that could write that preference could redirect the key.
- **A key typed straight after switching provider went to the old provider.**
  `onSaveKey` read the provider from `uiState`, which lags the write by an
  emission. It now reads the stored value inside the coroutine. A fast tap does
  exactly this, and the key would have been invisible in the UI and undeletable
  except by delete-everything.
- **Cleartext was narrowed to loopback, which broke the main use case.** See
  2.3a below; it was reversed the same day.

#### Deliberately not done

- **Custom request headers.** §8 puts them "behind an advanced section" as
  optional. Nothing needs them yet, and an allowlist with no caller is an
  allowlist nobody has tested.
- **A real provider has never answered.** Every test here is against a local
  server. Whether Gemini's live response shape matches what `GeminiProvider`
  parses is unverified, and stays unverified until someone puts a real key in
  the app — which is a thing only the maintainer can do.

### 2.3a — Cleartext to the user's own network — **superseded by 2.3d**

A correction to 2.3, itself reversed a day later when the whole address rule was
removed. Kept because the reasoning is what made 2.3d's case: this is the round
that established the platform cannot express the rule, which is why enforcing it
in Kotlin was the only option, which is why removing it entirely became the
honest alternative to a validation layer nobody wanted.

2.3 shipped cleartext restricted to loopback, on the grounds that a
network-security configuration lists hosts and cannot express "any
192.168.x.x" — so permitting a LAN address in Kotlin would mean permitting
something the platform then refuses at the socket. That reasoning about the
platform is correct. The conclusion drawn from it was not.

**The user's model server is on their desktop.** That is the case §8 names
first, and loopback-only means it does not work — the workarounds are a USB
`adb reverse` tunnel, or putting a real TLS certificate in front of Ollama.
Both are fine for whoever wrote this and neither is a product.

So the trade is inverted. The manifest permits cleartext, and `EndpointPolicy`
narrows it: `http://` only when the user has switched it on, only to a numeric
address in `127/8`, `10/8`, `172.16/12`, `192.168/16` or `169.254/16`. A name
gets its own refusal telling the user to type the numeric address — resolving it
would mean a DNS lookup inside a validation function, answerable differently the
second time by whoever controls the name.

**That moves a platform guarantee into application code, so the app has to be
the only door.** `CleartextGuardTest` asserts the three things that make it one:
exactly one module declares an HTTP client, exactly one file turns a request
into a call, and that file consults `EndpointPolicy` first. All three watched
failing.

The fourth thing worth recording is about the build, not the code. That guard
reads every module's build file, which Gradle cannot see — so
`configureGuardTestInputs()` now declares them, scoped to `:app`. Verified the
way the rule in `AGENTS.md` asks: the task reported `UP-TO-DATE`, a build file
outside `:app`'s classpath was edited, and the task then ran and failed. Without
that declaration the guard would have been silent on exactly the change it
exists to catch.

### 2.3b — What the device found

The AI settings screen reached a Galaxy S23 for the first time. It rendered, it
did not crash, and the whole cleartext path works end to end: a LAN address is
accepted with the switch on, refused with it off, and a public address is
refused either way. Three defects, none of which any JVM test could have seen.

- **Samsung's keyboard rewrote the address.** `http://api.openai.com/v1/`
  arrived as `http://api. openai. com/v1/` — a space after every dot.
  `KeyboardType.Uri` does not stop autocorrect on this keyboard; the field now
  sets `autoCorrectEnabled = false` and `KeyboardCapitalization.None`, as the
  key field already did. The symptom was an address the user typed correctly
  being rejected as malformed, with nothing on screen to explain it. The model
  field had the same hole and is fixed too.
- **Switch rows could only be tapped on the switch.** `SwitchRow` put the
  listener on the `Switch`, so the label and the explanation — most of the row —
  did nothing. Now the row is `toggleable`, which also merges it into one
  accessibility node instead of announcing an unnamed switch after the text.
  This was never specific to this screen: Keep screen on, Vibration and Reduce
  motion had it too, and have had since Phase 1.
- **"Advanced" read as a heading, not a control.** An `ActionRow` with the
  detail line "Request timeout" and no affordance — nothing suggested the
  timeout and the local-server switch were behind it, so they were effectively
  missing. It has a chevron now, and a subtitle that says what is inside.

A fourth thing was wrong and only the device made it obvious: the refusal for a
cleartext *name* said "type the numeric address of the machine", which is sound
advice for `my-desktop.local` and bad advice for `api.openai.com`. One message
now covers both — numeric address for your own network, https for anything else.

**A fifth thing turned up in the instrumentation suite, and it was the test's
fault, not the app's.** `theSaveButtonStaysAboveTheKeyboard` failed on a build
whose screenshot, taken minutes earlier on the same device, plainly showed the
button above the keyboard. Cause: `rootWindowInsets` reports the IME's final
height the moment its window is created, several frames before Compose has
re-laid-out around it — so `waitUntil { imeHeight() > 0 }` returned and the
assertion read the button's position from before the padding applied. It now
waits for the bounds to stop moving as well.

Worth noting because the two readings are indistinguishable: the flake reported
`2287.0`, and so did a deliberately broken build with the shell padding removed.
A settled-bounds wait is not the same as waiting for the assertion to pass — a
broken build settles immediately at the wrong place — and that was re-checked by
removing the padding and watching it stay red.

**All 14 instrumentation tests now pass on the Galaxy S23:** the six in `:app`,
and the eight in `core:secrets` for the first time since they were written.

### 2.3c — What pressing the button found

The maintainer configured a real Gemini key and got a successful connection
test. **That is the first confirmation that a live provider's response shape
matches what `GeminiProvider` parses**, and no test in this repo could have
given it — every one of them answers from a local server this project wrote.

Three changes came out of that round.

**A key is no longer required to test the generic provider.** Ollama and LM
Studio ignore the field entirely, so demanding one meant typing a throwaway
value to satisfy a check that protected nothing. `ProviderId.requiresKey` is
false for `OPENAI_COMPATIBLE`; the button now wants an *address* instead. The
adapter omits the `Authorization` header altogether when there is no key, rather
than sending `Bearer ` — a malformed credential some servers reject and none
read as "none offered".

**A bare host name says what it is missing.** `laptop-tulpar` — a Tailscale
MagicDNS name, a LAN hostname — used to be "that is not a web address". It now
reports `MISSING_SCHEME` and asks for `https://` in front. Nothing is guessed on
the user's behalf: which scheme it should be is the security-relevant half of
the answer. The same name *with* `https://` is accepted with nothing switched
on, which is how a Tailscale or reverse-proxied server is reached.

**A bad Gemini key was reported as a parsing failure.** This is the one worth
remembering. Gemini answers an invalid key with `400 INVALID_ARGUMENT` and
`"reason": "API_KEY_INVALID"` — not 401 — so the shared status mapping fell
through to its `else` and said "the provider answered with something this app
could not read". The likeliest mistake anyone can make got the least useful of
the seven messages. Verified against the live endpoint with a deliberately
invalid key, fixed locally in `GeminiProvider` rather than in `failureForStatus`
(a generation request has a body, and a body can be wrong on its own account),
and watched failing with the branch removed.

Found by pressing the button on a device. Nothing else would have.

The result now carries a check or an error mark beside the sentence, from
`RfIcons` rather than literal emoji so it takes the theme's colours and scales
with the font.

**Verified since:** a successful connection with a real Gemini key, reported on
the device with a tick. The failure path was confirmed here with a deliberately
invalid key. No generation request has been made — that is 2.4.

### 2.3d — The address rule was removed, and §8 amended

The maintainer's decision, taken after the risk was put to them explicitly, and
recorded here with the reasoning rather than as a diff.

**The app no longer inspects the address.** `EndpointPolicy` is deleted, along
with the cleartext switch, the scheme check, the private-address rule and every
message that went with them. Whatever is typed is sent, over whatever scheme is
typed. `http://laptop-tulpar:11434/v1/` now works; so does plain `http` to
anything on the internet. The network security config permits cleartext
unconditionally and nothing narrows it.

§8 has been amended in the same change — `PROJECT_GUIDELINE.md` is the
specification and `AGENTS.md` says it wins, so leaving the old rule there while
the code did the opposite would have been the worst of the three options. The
amendment states the accepted cost in the guideline itself: **an API key sent to
an `http://` endpoint is readable in transit and the app will not warn about
it.** Anyone who later proposes reinstating a check should argue against that
paragraph rather than around it.

The case for removal, fairly put: the original rule could not express the case
it named first. A network-security configuration lists hosts, not ranges, so
"any address on the user's own network" is not writable in it. Enforcing it in
Kotlin instead produced a validation layer that refused bare machine names,
demanded numeric addresses, and still could not tell a Tailscale name from a
public one — with all of the friction landing on the person running a local
model server, who was the entire reason the setting existed. Two rounds of this
file were spent narrowing and re-widening that rule before it was dropped.

Two protections survive and are not incidental: Gemini's endpoint is fixed in
code, so a Gemini key cannot be redirected by a stored address; and
user-installed certificate authorities stay untrusted, so a self-signed local
server is not a supported setup.

`CleartextGuardTest` became `NetworkBoundaryTest`. It can no longer claim the
manifest is safe because the app narrows it — that check is gone — so it claims
something smaller and true: one module reaches the network, through one file.
That is a scope control rather than a cleartext control, and the file says so.

### 2.4 — The generation pipeline — **complete and live-provider verified**

The first boundary is built: versioned, serializable workout request and response
contracts; a compact request mapper that sends constraints and candidate metadata
but not profile identity, exercise names or instructions; strict response decoding;
and local validation before a response can become a plan. Numeric limits now live
once in `WorkoutLimits`, shared by the builder, rules engine and provider validator,
so an answer cannot pass one boundary and be silently clamped by the next.

The validator checks schema version, order, offered and duplicate exercise ids,
sets, repetition targets, durations, rest, target type and rationale. Only then
does it project the answer into the existing `WorkoutTemplate` shape and delegate
the user's equipment, exclusions, muscles and session ceiling to `RulesEngine`.
The strict unknown-field check was watched failing by temporarily allowing unknown
keys. Fourteen new unit tests cover this slice.

The provider boundary is now complete too. One versioned JSON Schema is built from
`WorkoutLimits` and supplied to both structured-output APIs: Gemini receives it as
`generationConfig.responseJsonSchema`, while the generic adapter sends it through
Chat Completions as strict `response_format.json_schema`. Provider envelopes remain
forward-compatible and internal; the workout inside them is decoded by the strict
shared codec, and raw provider bodies or refusal text never enter diagnostic results.
The adapters join a base URL and path in one place, omit empty bearer credentials,
and return typed auth, quota, network, model, server, endpoint, or format outcomes.

Twelve further unit tests exercise both POST requests against MockWebServer, including
their paths, headers, prompts, exact shared schema, successful response extraction,
unknown envelope fields, strict workout fields, refusals, Gemini's unusual invalid-key
response, quota mapping, unusable addresses, and the fake provider. The
schema-to-`WorkoutLimits` guard was watched failing by drifting `maxItems` and
then restored.

The orchestration boundary is complete. `RulesEngine.filterCandidates` is the
single candidate-level hard-rule pass before provider generation, so the
provider never sees an excluded, unavailable or off-target exercise.
`AiWorkoutGenerator` resolves provider configuration for one call, validates the
first answer, and permits one repair attempt only for malformed or locally
invalid output. That attempt receives compact typed error codes and exercise ids
encoded as JSON; it never receives rule-detail strings or raw provider output.

**The response contract now uses one exact repetition target.** Version 1 copied
the guideline's original repetition-range wording even though the builder and
saved-plan model both hold one number. Collapsing `8–12` into a midpoint would
discard provider output invisibly, so the specification was corrected and schema
version 2 requires the provider to choose the editable target itself.

The builder consumes that boundary, passes the active app language, maps a
validated response into editable draft cards, and preserves exact repetition,
duration, and starting-weight targets. The provider's localized rationale is
shown above the cards.

**The local plan generator and every automatic fallback have now been removed by
product decision.** `core:rules` still filters candidates and validates provider
answers locally, but it no longer arranges or prescribes a workout. Missing
configuration, timeout, network, authentication, quota, adapter, empty-candidate,
and invalid-response outcomes leave the builder untouched. A timeout has its own
typed category and popup; the popup retains muscle selections and offers Retry.
`PlanSource.RULES` remains only so plans saved by earlier development builds can
still be read.

**Live evidence:** the configured provider generated a workout successfully on
the Xiaomi Android 11/API 30 device, confirmed by the maintainer. That same run
exposed the faint loading treatment that motivated the UI change below.

### 2.5 — Coach UI — **complete**

Muscle-specific generation flows through the configured provider and arrives as
an editable draft. During generation, the selector and action button give way to
a high-contrast primary-container card: an 80dp indeterminate ring around the
Coach icon, a clear heading, and explanatory text. It uses the system-respecting
Material progress animation rather than a custom glow, and the card is announced
as a polite live region. Back gestures and close actions remain guarded by the
cancel-generation confirmation. Provider errors appear in a popup; retryable
errors keep the request intact and provide Retry, while configuration errors give
one clear dismissal action.

Device verification remains for the redesigned loading card and timeout popup.
Placeholder assembly, all 409 unit tests, and lint pass for the complete current
worktree after the local generator removal.

---

## Phase 3 — Polished phone

§19 covers on-demand exercise media downloading and disk caching, media UI in catalog
and active workout session, live session UX polish, full audio cues, theme switching,
and localized strings.

### 3.1 — Media Downloader & Bounded Disk Cache — **done**

The media delivery pipeline (§9) is built in a dedicated `core:media` module.
Media metadata and checksums come from `media-manifest.json` (SHA-256 + exact byte count).
`MediaDownloader` streams downloads with incremental SHA-256 verification and byte-size
checks into temp files, performing an atomic rename to promote verified assets into the
durable cache. Tampered or truncated payloads fail with `MediaIntegrityException` and are
immediately removed.

`MediaCacheManager` maintains the on-disk cache hierarchy under
`exercise_media/<mediaVersion>/<exerciseId>/<mediaType>/<sha256>.bin`, tracks total byte size
reactively, implements LRU eviction when exceeding the 250 MB cap, and supports clearing.
`RepForthImageLoader` configures a shared Coil 3 `ImageLoader` with animated GIF decoding.
`ExerciseMedia` provides a 1:1 Compose component supporting `SMALL` (48dp), `MEDIUM` (72dp),
and `FLUSH` full-width layouts with fallback to `RfIcons.Exercises`.

In `feature:settings`, a **Media** section exposes the Wi-Fi only toggle switch and a
clear cache action row with live size formatted in MB and a confirmation dialog.
`NetworkBoundaryTest` was updated and asserts that network clients are bounded strictly
to `core:ai` and `core:media`.

### 3.2 — Exercise Media Display & Session Integration — **done**

Exercise media rendering and background prefetching are integrated across all exercise and workout workflows:
- **Shared Detail Sheet (`core:media`)**:
  - `ExerciseDetailSheet` provides a reusable modal sheet displaying a `FLUSH` aspect-ratio-locked `ExerciseMedia` hero, legal attribution text (`© Gym visual — https://gymvisual.com/`), primary and secondary muscle chips, equipment chips, and localized step-by-step instructions.
  - Inset below system status bars and camera punch holes (`statusBarsPadding()`).
  - Supports a pinned, non-scrolling bottom action container so actions remain always visible regardless of instruction length.
- **Exercise Catalog (`feature:exercises`)**:
  - `ExerciseRow` displays 1:1 `SMALL` (48dp) exercise thumbnails alongside exercise title and target muscle / equipment chips.
  - Tapping an exercise opens `ExerciseDetailSheet` with default "Close" button.
  - Obeys `reducedMotion` preference (disables animated GIF autoplay and displays static thumbnail instead).
- **Running Session Screen (`feature:session`)**:
  - `TargetPanel` renders prominent hero `ExerciseMedia` for active exercise (spanning ~92% screen width with 1.1 aspect ratio) stacked above the numeric hero.
  - `RestPanel` displays a "Next up" preview card with thumbnail (`ExerciseMediaSize.SMALL`) and name for the next exercise.
  - `SessionViewModel` prefetches media on a background IO coroutine for the current and next 2 upcoming exercises via `MediaDownloader.prefetch()`.
- **Workout Builder & Picker (`feature:builder`)**:
  - `ExercisePicker` lists exercises with `SMALL` thumbnails. Tapping an exercise opens `ExerciseDetailSheet` with a pinned, always-visible **"Add to workout"** button.
  - Builder cards (`ExerciseCard`) display the selected exercise thumbnail beside the exercise name and set configuration.
- **Training Profile Settings (`feature:settings`)**:
  - Added dedicated **Training profile** section to Settings allowing users to view and update their onboarding settings: Focus / Goal (`ChoiceRow`), Training Years / Experience (`ChoiceRow`), Available Equipment (`ActionRow` with interactive `EquipmentDialog` checkbox selector), and Schedule (`InfoRow`).
  - Shared domain labels (`TrainingGoal.labelRes`, `TrainingGoal.detailRes`, `ExperienceLevel.labelRes`) moved into `core:exercise-data`'s `ProfileTerms.kt` and `exercise_terms.xml` ("Write it once").
- **Week Progress, Workout Weight & Coach Structure Polish**:
  - `ProgressSummary` now tracks `daysThisWeek` (distinct calendar training days in the current week) so "X of Y days" correctly represents distinct training days (preventing "7 of 3 days" when multiple workouts occur on the same day).
  - Active workout `TargetPanel` renders prescribed target weight prominently alongside rep counts (with `Bodyweight` fallback), and set input fields offer placeholder text from the targets.
  - AI Coach prompt (`AiWorkoutSchema.toGenerationPrompt`) instructs structured three-phase workout programming: warm-up/activation first, core resistance sets, and stretching/cool-down finish.
  - AI Coach wire contract (`AiPlannedExercise`), JSON schema (`weight_kg`), validator, and builder mapping now support prescribing starting baseline weights based on user experience level and goal.
- Verified as part of the 427-test suite (`AiWorkoutContractTest`, `AiWorkoutJsonSchemaTest`, `AiWorkoutValidatorTest`, `SessionStatisticsTest`, `TodayViewModelTest`, `SettingsViewModelTest`, `ExercisesViewModelTest`, `SessionViewModelTest`, `PickerViewModelTest`, `MediaStringParityTest`, `TrainingWeekTest`, `RoomWeekRepositoryTest`, `RoomTemplateRepositoryTest`, `DataTransferTest`), plus `./gradlew assemblePlaceholderDebug` and `./gradlew lint`.

### 3.3 — The accessibility pass — **done; nine defects, and a guard that can see them**

§20 requires English and Turkish to pass accessibility checks. Nothing had ever
run one, and the reason it looked fine is the interesting part.

**`./gradlew lint` reports zero accessibility issues across the whole repo, and
always would have.** Lint's `ContentDescription`, `TouchTargetSizeCheck` and
`ClickableViewAccessibility` read XML layouts and `View` subclasses; this app is
entirely Compose. A guard that appears to run and cannot see the thing it names
is the exact failure shape this repo already has a rule about, and it had been
sitting in CI passing since Phase 0.

So the check was built where it can look: `assertScreenIsAccessible` walks the
merged semantics tree — what an accessibility service actually reads — asserting
that every clickable node announces something, has a 48dp touch target, and that
anything carrying state declares a `Role` or a `stateDescription`. 29 tests
across seven feature modules, in both languages, on the JVM beside the goldens.

A `gemini-3.8-flash-high` sweep found ten defects and every one was checked
against the file and line before anything was changed; the guard then found
three more that no audit had reached, and disagreed with itself twice before it
was right:

- **Two expand/collapse chevrons had no name** (Plans, week review), so a screen
  reader offered an unnamed button. Invisible to a golden — the picture is
  identical either way.
- **Two `Text("OK")` literals** in dialogs, which is a localisation defect as
  much as an accessibility one: the Turkish build said "OK" too.
- **The equipment rows were a clickable row wrapping a checkbox that also
  handled clicks** — two focus stops for one control, neither naming the other,
  and no role on either. `SwitchRow`'s comment had warned about this exact shape
  a phase earlier.
- **Coach's muscle chips were 32dp.** Compose does expand a chip's touch bounds
  to 48dp; a `LazyRow` sized to its tallest 32dp child clips the expansion back
  off. Identical chips in a `FlowRow` were fine, which is why this needed
  measuring rather than reading.
- **`RfChoiceChips` asked for `Target.icon`** — 40dp, the icon token — where
  `Target.min` is the 48dp touch-target token that exists for it.
- **Onboarding's "Show 18 more" was 40dp**, a Material text button with no
  taller sibling to stop its parent clipping the expansion.
- **The set counter changes with nobody touching the screen** — `RestElapsed`
  fires off a clock tick and advances it — and was a plain `Text`, so it was
  never announced. Now a polite live region.

**The check was wrong twice before it was right, and both are recorded in the
source.** Reading `touchBoundsInRoot` alone failed a 48dp Settings row at 40dp
because it sat at the bottom edge of a scrolling list; reading `size` alone
would have failed every Material `IconButton`. It takes the larger of the two.

All three checks have been watched failing on the thing each one watches, and
the first two attempts at that proved nothing — the breaks landed in an
`InputChip` that only renders once a muscle is selected, and in the equipment
dialog, which no test reaches. A guard proven against unreachable code is not
proven.

---

### 3.4 — The motion system — **done; the setting now controls something**

§19 asks for a "final motion system". There wasn't a first one. A grep for every
Compose animation API across all 131 source files returns **one file**:
`CoachScreen`, whose generate button pulses. Everything else in the app is
instant.

More to the point, **the reduced-motion switch controlled nothing that moved.**
It had exactly one effect — swapping an animated GIF for a thumbnail — while the
only actual animation in the app ignored it entirely. A user who turned it on
got the same pulsing button.

`Motion.kt` ports the token set that was already sitting in
`design-system/tokens/motion.css`: six durations, five easings, three travel
distances, two scales, and the product rules the CSS carries as a comment —
shared-axis for plan to detail, spring for set completion, rotation only for an
active timer ring, and **no large motion while a set is in progress**, which is
a rule about someone holding a barbell rather than a matter of taste.

`RepForthTheme` provides `LocalReducedMotion`, following `LocalUnitSystem`: a
display decision that reaches every screen and that no ViewModel has another
reason to carry. `rfTween` and `rfTravel` read it, so honouring the setting is
the default and ignoring it takes effort.

**Two guards, both watched failing.** `MotionTokenTest` rejects a literal
`durationMillis` outside the design system, and requires any file using
`rememberInfiniteTransition` to read `LocalReducedMotion` — because `tween(0)`
inside an `infiniteRepeatable` repeats instantly and forever rather than not
animating, so that case has to branch. Breaking the first one also proved the
guard-input declaration: editing a *feature* file correctly invalidated `:app`'s
test task, which is the blind spot that has caught this repo twice before.

What is not done: the shared-axis navigation transitions and the set-completion
spring the CSS names. The tokens and the switch exist for them now; nothing
reads them yet beyond the one button.

---

## Phase 4 — Weekly plans

A plan became a week of training days rather than a single workout. This was
built before §19 listed it; the guideline now does, and §20 requires it, so v1
cannot be declared done without a week that fills its budget and survives being
reopened.

### 4.1 — Weekly Plans: Domain and Persistence (Slice W1) — **done, migration proven on a device**

Domain and persistence foundation for multi-day weekly plans (§1, §3, `docs/WEEKLY_PLANS.md`):
- **Domain Layer (`core:model`)**:
  - Added `TrainingWeek` and `WeekDay` enforcing non-blank titles, contiguity of day positions from zero, and unique assigned days of the week (`java.time.DayOfWeek`).
  - Total estimated duration across all days computed automatically.
- **Database Schema & Migration (`core:database`)**:
  - `training_week` table (`TrainingWeekEntity`): `id`, `name`, `notes`, `source`, `active`, `created_at`, `updated_at`.
  - `workout_template` table updated with `week_id` (foreign key to `training_week(id)` with `ON DELETE CASCADE`), `week_position` (0-based index), and `day_of_week`.
  - Added `WeekDao` providing reactive observation (`observeAll`, `observeActive`), transactional week saving (`replaceWeek`), and single-active-week switching (`setActive`).
  - Updated `TemplateDao.observeAll()` to filter `WHERE week_id IS NULL` so standalone template queries never double-list days belonging to a weekly plan.
  - Bumped database version to `2` with additive `MIGRATION_1_2` registered in `DatabaseModule`. Prepackaged `repforth.db` asset updated to v2.
- **Repository & Reset (`core:user-data`, `core:transfer`)**:
  - Added `WeekRepository` interface and `RoomWeekRepository` implementation bound in `UserDataModule`.
  - Wired `weeks.deleteAll()` into `DefaultDataTransfer.deleteWorkoutData()`, which `resetApp()` calls, so both paths clear weeks. `ResetCoverageTest` guards it.
  - Weeks and the workouts inside them are exported and imported (`WeekDto`, `WeekDayDto`, export format version 2). This did **not** land with the rest of W1: `TemplateDao.observeAll()` had already been narrowed to `week_id IS NULL` while the export still read it, so for one commit a generated week was silently absent from the only backup this app has. A version 1 file still imports and simply has no weeks.
- Verified by `./gradlew test`, `assemblePlaceholderDebug` and `lint` (`TrainingWeekTest`, `RoomWeekRepositoryTest`, `RoomTemplateRepositoryTest`, `DataTransferTest`, `SchemaExportTest`, `UserDataSchemaTest`, `PackagedCatalogTest`).
- **`MigrationTest` (`core:database`, instrumentation) has run and passes.** Five tests: that Room validates the migrated schema, that an existing plan and its exercises survive, that `training_week` arrives empty and usable, that deleting a week cascades to its days, and that a standalone workout is not collateral damage. Executed on a Galaxy S23 (SM-S911B, Android 14) on 2026-08-31: five tests, no failures, recorded in `core/database/build/outputs/androidTest-results/`. This entry said "has not been run" for two days after it had, which is exactly the way `docs/PLAN.md` goes wrong — it goes stale by nobody doing anything.

### 4.2 — Weekly Plans: Contract v3 & Multi-Day Validation (Slice W2) — **done**, no live provider has yet returned a week

Wire contract, schema, prompt, retry feedback, and host validator upgraded to Schema Version 3 for weekly plans (§4, `docs/WEEKLY_PLANS.md`):
- **Unified AI Wire Contract (`core:ai`)**:
  - Bumped `AI_WORKOUT_SCHEMA_VERSION` to `3`.
  - `AiWorkoutRequest`: added `days: Int`, `session_duration_minutes: Int`, `max_exercises_per_day: Int`.
  - `AiWorkoutResponse`: returns `days: List<AiPlannedDay>` where each day specifies `day_index: Int`, `title: String`, `focus_muscles: List<String>`, and `exercises: List<AiPlannedExercise>`.
  - `WorkoutLimits`: added `days = 1..7` and `maxExercisesPerDay = 8`.
- **JSON Schema v3 & Prompt**:
  - Updated `AiWorkoutJsonSchema` to generate structured multi-day definitions matching `WorkoutLimits`.
  - Prompt instructs multi-day weekly programming, day-specific three-phase warm-up / core / stretch structure, target weight recommendations, and cross-day exercise repetition support.
  - `AiWorkoutRetryIssue` gains `day_index: Int?` to localize validation error feedback to specific days for model self-correction.
- **Host Validation (`AiWorkoutValidator`)**:
  - Validates day count matches requested days, day indices are contiguous from 0, and non-empty titles.
  - Validates per-day exercises (sets, reps/durations, rest, weight, offered candidates).
  - Enforces duplicate exercise prohibition within the same day while allowing cross-day exercise repetition.
  - Computes total estimated duration across the week and validates each day through `RulesEngine`.
- Verified by `./gradlew test`, `assemblePlaceholderDebug` and `lint` (`AiWorkoutContractTest`, `AiWorkoutJsonSchemaTest`, `AiWorkoutValidatorTest`, `ProviderGenerationTest`, `AiWorkoutGeneratorTest`, `BuilderViewModelTest`).

### 4.3 — Weekly Plans: Coach, review and Plans (Slice W3) — **used on a Galaxy S23; two defects found there, fixed in 4.5 and 4.8**

- **Coach asks how many days.** A row of seven chips seeded from the profile's
  `trainingDaysPerWeek`, with a line underneath saying which of the two outcomes
  the current choice produces. Without it the day count was the profile's and
  nothing else, so a user who trains four days a week had no way to ask for one
  workout — which contradicted a decision the maintainer had just taken.
- **One day is a workout, not a week of one.** The wire contract still always
  speaks in days, so there is one schema and one validator; the *storage* differs.
  A single-day answer populates the standalone draft and saves through
  `TemplateRepository`; two or more populate `weekDays` and save through
  `WeekRepository`. The two lists are mutually exclusive, which also removed a
  stale second copy of day one that had been living in `exercises`.
- **Deleting a week asks first, and names the count.** The cascade is the chosen
  behaviour; a one-tap icon that removes five workouts with no undo was not.
- **Twenty-four handlers became twelve.** Every editing action took a nullable
  `dayIndex` instead of existing twice, and `withExercises(dayIndex)` is the one
  place that knows whether a standalone draft or a day of a week is being edited.
- **Day titles and the default week name come from resources**, resolved in the
  screen and passed in, as `onSave` already did for the plan name. They were
  English literals in the ViewModel, and a day title is *saved*, so the English
  reached the database rather than only the screen.
- **A day keeps its template id across saves** (`DraftWeekDay.templateId`), and a
  saved week keeps its own id in `BuilderUiState.weekId` rather than borrowing
  `planId`. Both were silent: fresh template ids each save reset Today's "which
  day have I not done yet", and reusing `planId` as a week id made every re-save
  write a second week.
- **A new week only becomes active when no week is active.** It used to force
  itself active on every save, silently changing what Today offered.

Still open in this slice: `TodayScreen` shows the recommended day as an ordinary
plan card, with no indication that it belongs to a week or which day it is.
`BuilderViewModel.load()` still only loads templates, so a saved week cannot be
reopened for editing from Plans.

### 4.4 — The AI request, restructured (schema version 4) — **generated a real week on a Galaxy S23**

A pass over what the app actually sends and asks for, driven by measuring the
payload rather than reading it. Everything below was counted against the
packaged catalog.

**Exercise names are now sent, and the request got smaller.** The v3 candidate
carried `{id, target, equipment, target_type}` and no name, which read as a
privacy measure and was not one: names are public catalog data and the ids
already identify them exactly. What it actually did was make **1,265 of the
1,324 catalog exercises indistinguishable from some other exercise on the
wire** — 89 arrived as the identical `{abs, body weight, repetitions}` — so
choosing between them was arbitrary, and the prompt's existing instruction to
open with warm-ups and close with stretches could not be acted on at all. The
model's real contribution was volume and prescription; selection was noise.

The catalog now travels as a delimited table instead of an array of JSON
objects, because repeating field names on every row was most of what the request
weighed. Over all 1,324 catalog exercises: **112,110 characters carrying four
fields each, down to 90,205 carrying six.** Around 5,500 tokens cheaper *and*
strictly more informative — which is how `name`, `secondary_muscles` and the R/T marking were
all added without the request growing. Rows are ordered by muscle then name, so
a model choosing chest work reads a contiguous run rather than a list ordered by
upstream row number.

**Three request fields were removed because they were already true.**
`excluded_exercise_ids`, `excluded_muscles` and `equipment` all name constraints
`RulesEngine.filterCandidates` has already applied, so sending them asked the
model to avoid exercises it could not see.

**`excluded_movements` was decorative, and now is not.** It was the one
exclusion the catalog filter could not express, so it was sent to the provider
as advice — and checked by nothing on the way back. Someone who wrote "overhead
press" was told the exclusion applied and could still be programmed one.
`RulesEngine` now matches it against the exercise name. That is coarse (a user
who writes "press" gets a very short catalog and the "nothing matched" screen
explaining why) but it is what the field claims to do.

**Four response fields were removed because none could carry information.**
`schema_version` was a constant the app had just sent, echoed back; `day_index`
and `order` restated array positions; `tempo` was generated, validated,
normalised and read by nothing. Each was a way for a whole week's generation to
fail on a fact the JSON structure cannot get wrong — `DAY_INDEX_ORDER` and
`ORDER` are gone with them. Array position is the order, and always was.

**The prompt now states the duration formula the validator enforces.** A day is
rejected when `sets × (repetitions × 3 + rest_seconds)`, summed and counting the
last set's rest, exceeds the session ceiling. The model was never told that, so
it was failing a check it had no way to pass — and spending the single repair
attempt on it.

**Retry feedback is sentences, not codes.** It used to be
`{"kind":"rule","code":"no_time_left"}`. Those names are this codebase's, and a
model given one could not tell whether the fix was fewer exercises, fewer sets
or less rest. The mapping is authored locally — our enum to our sentence — so
nothing the provider said comes back to it as instruction, which was the reason
codes were used in the first place. A rule violation now also carries which day
it happened on; the rules engine validates one day at a time and cannot know, so
`AiWorkoutValidator` stamps it.

**The prompt is one document rather than prose wrapped around a JSON dump.**
Every constraint used to be stated twice — once in a sentence, once inside the
encoded request appended below it — leaving the model to work out which copy
governed. It is now headed sections: brief, day shape, week shape, time budget,
numeric limits, catalog.

**`-Drepforth.regenerate=true` never reached the test JVM.** `SchemaDumpGuardTest`
documents it as the way to rewrite `tools/gemini-schema.json`; the flag stopped
at the Gradle daemon, so the documented command silently reran the failing
assertion. Forwarded in `configureGuardTestInputs()`, and watched working:
corrupt the file, guard fails; run with the flag, guard passes.

Guards proven by breaking them: the movement-exclusion filter (disabled it,
watched two tests go red, restored), and the schema dump (corrupted the JSON,
watched the guard fail).

Verified by `./gradlew test`, `./gradlew lint` and `./gradlew
assemblePlaceholderDebug`, each run separately, and then **by generating a real
six-day week against live Gemini on a Galaxy S23**. It succeeded first try, and
the maintainer confirmed the thing the change existed for: days now open and
close correctly, and the exercise choices are visibly deliberate rather than
arbitrary. That is the first live evidence for §8 that a provider both accepts
the v4 schema and can act on the catalog now that it carries names.

**A stale scratch file cost a round trip, and now warns.**
`tools/probe-cases.generated.ps1` is gitignored scratch that overrides the
probe's default cases. One left over from the previous session still carried the
v3 schema, so a probe run reported `PASS the fixed real schema` while v4 had
never been sent at all — the same stale-artifact failure `SchemaDumpGuardTest`
exists to prevent for the dump, in the one file that had no guard. The probe now
warns when the staged cases are older than `tools/gemini-schema.json`.

### 4.5 — What the first real week exposed — **installed; the session start/end was confirmed on screen**

Two bugs reported by the maintainer on the Galaxy S23, on the first week a live
provider ever returned. Neither is about the AI; both are places the week path
had never been used. A third, worse one fell out of fixing the second.

**"Day 1: Day 1: Chest".** Two screens rendered a day's name and both appended
the title to a "Day N" header — `PlansScreen` joining with `": "`,
`WeekReviewScreen` with `" · "`, neither noticing the title very often already
began with the day number. It arrived carrying one by two separate routes:
nothing told the model not to write "Day 1: Push" in a field called `title`, and
the app's own fallback for a blank title (`coach_day_default_title`, "Day %1$d")
has always been character-for-character what `week_day_header` renders beside
it. `weekDayLabel()` is now the one place that knows how to name a day; it drops
the redundant prefix, case-insensitively, because Turkish writes `1. Gün` as the
header and `1. gün` as the fallback. The prompt also now tells the model the app
supplies the number, which is the actual fix — the stripping catches a model
that does it anyway.

The separator check inside it is load-bearing and was watched failing: "Day 10
recap" starts with "Day 1" and is not day one.

**A day of a week could not be opened from Plans.** Tapping "Day 1 · Chest" did
nothing. It was the only row in the app that looked like a plan and behaved like
a label — `PlanCard` makes the whole card `clickable` to edit, and the day rows
inside `WeeklyPlanCard` had no click at all, with a Start button beside them
making the omission read as deliberate. They now open the same way a standalone
plan does.

**Which uncovered the real problem.** Wiring that tap up would have destroyed
data. `RoomTemplateRepository.save()` built its entity from `WorkoutTemplate`
alone — the standalone-plan shape, which carries nothing about weeks — and
`replaceTemplate` upserts with `OnConflictStrategy.REPLACE`. So every save reset
`week_id`, `week_position` and `day_of_week` to null. Editing one day of a saved
week and saving it would have taken that day out of the week and left it loose
in the plan library, with no way back and nothing on screen to say it had
happened. The three columns are now carried forward from the existing row,
exactly as `created_at` already was. Watched failing.

Nothing could reach this before, which is why it survived: the only writer of
week days was `WeekRepository`, and no screen offered a way to save one on its
own.

**An exercise inside a plan could not be looked up.** Found while misreading the
report above, and worth keeping: `ExerciseDetailSheet` was reachable from the
catalog tab and from the picker and nowhere else, so once a row was in a plan
there was no way to see how to perform it. Invisible while every plan was built
by hand, and immediately obvious on a generated week, where nobody chose the
exercises. The thumbnail and name in `ExerciseCard` are now one tap target that
opens the sheet, which covers the standalone builder and the week review
together because both render that card. The full `Exercise` loads on tap rather
than being held: a plan of eight would otherwise pull eight sets of instruction
steps in both languages to show one. No bottom action on the sheet — the
exercise is already in the plan, so "Add" is the one thing it must not offer.

**Every number in the AI contract was a ceiling, so the model minimised.** Seven
days at forty-five minutes each came back as seven eight-minute days totalling
56 minutes — and broke no rule the app had. Read the prompt from the model's
side and it is the correct answer: "keep each day at or under 2700 seconds", "at
most 8 exercises per day, at least 1", "a day over budget is rejected", and
nothing anywhere saying a day could be too small. The safest answer to a set of
one-directional constraints is the smallest one.

The prompt now states the session as a **band** — "aim each day at 2160-2700
seconds", both ends named, plus "usually 4-6 working exercises" and "8 per day is
the hard maximum, not the target". That alone took the next seven-day week from
eight-minute days to 19-26 minute days.

**Which surfaced the thing underneath: the estimator and the model disagree by
about 40%, and they are measuring different things.** Benchmarked against
sessions of known length — StrongLifts 5x5 reads 26 minutes against a real 45-60;
a six-exercise 3x10 day at 60s rest reads 27 against a real 45-55. The formula
counts work and rest and nothing else: no setup, no walking to the rack, no
loading plates, no warm-up sets, and 3s/rep is light for loaded work. So a model
programming a genuine 45-minute session scores 22 by our arithmetic and looks
like it under-filled.

The dangerous fix would have been to push harder on the prompt: reaching 2700s
under this formula takes 22-30 working sets, which is a 75-90 minute session in a
gym. Handing someone twice the workout they asked for, to satisfy a number.

**Decision (maintainer): the app's formula is the definition, not real gym
time.** The session length is a ceiling and the arithmetic is what it is
measured with; the coach must compute in those terms rather than from experience
of how long training takes. So the estimator is untouched, and the prompt now
says outright that the formula "is the only definition of a day's length here"
and that setup and walking are not counted, on purpose.

**And a shorter day stays the coach's call.** `AiPlanFill.MIN_WEEK_FRACTION` is
enforced but set at 30% of the week's budget — a safety net against the budget
being ignored outright, not a quota. A light day, a deload, a short session
between two hard ones are all things a week is supposed to contain, and the app
must not overrule them: at 30% the 56-minute week (18%) still fails while a real
generated week (around 50%) passes with room. It is across the week rather than
per day for the same reason, and skipped entirely when the offered catalog holds
fewer exercises than a day may contain — someone whose filters leave a handful
cannot fill a long session however hard the model tries, and failing their
generation over it would be the app blaming the model for the user's own
constraints.

Being a contract violation rather than a rule one means the single repair
attempt gets it, with a sentence saying what to add.

Guards proven by breaking them: the day-number stripping (three tests red), the
detail lookup (one red), the week-ownership carry-forward (one red), and the
under-fill floor (two red, and two more asserting that a half-full week and a
week with one light day are both left alone).

Verified by `./gradlew test`, `./gradlew lint` and `./gradlew
assemblePlaceholderDebug` run separately, then installed and launched on the
S23 — alive, empty crash buffer.

**Nobody has looked at any of it on screen.** Two deserve a real check: the
detachment fix (save an edit to one day of a week, confirm the week still has
all its days) and the fill floor (generate seven days at forty-five minutes and
see whether the days are now full).

### 4.6 — Settings, reviewed — **fixed and installed; two verified on screen**

Two reported by the maintainer, three more found by reading the screen around
them.

**It opened already scrolled.** The profile section was five keyed items inside
`state.profile?.let`, and the profile arrives one frame after the screen does —
so five items were *prepended* to a keyed `LazyColumn` that had already drawn.
Keys then did exactly what they are for: they kept the item that was on screen
("Appearance") at the top, which put the whole Profile section above the
viewport. It read as a scroll-position bug and was a list-diffing one. Now one
item that is present from the first frame and changes height instead.

**Pills broke onto two lines inside themselves.** `ChoiceRow` was a
`SingleChoiceSegmentedButtonRow` filling the width, which divides it equally
between the options: four goals on a phone is about 80dp each, and
"Hypertrophy", "General fitness" and "More than 3 years" all wrapped. Equal
fixed shares of a fixed width is the one layout that cannot respond to its text
growing — Turkish is longer again, and at 200% font scale it cannot be made to
work at all. Now chips in a `FlowRow`: each is as wide as its own label and the
row wraps. Both fixes were seen working on a Galaxy S23.

**The import dialog never mentioned weeks.** `ImportPreview` has counted
`newWeeks` and `replacedWeeks` since export format 2, and `isEmpty` accounts for
them, but the dialog listed only profile, plans and sessions — so a file
carrying five weeks was described as though it carried none, on the one screen
whose whole job is saying what is about to be overwritten. The same shape as the
export bug in 4.1: the data layer knew about weeks and the surface did not.

**The media cache cap was written in three places.** `MediaCacheManager` holds
`DEFAULT_MAX_CACHE_BYTES`, and "250 MB cap" was typed into the English string
and again into the Turkish one. Changing the constant would have left two
translations quietly lying. Both numbers are parameters now, and the unit stays
in the resource where a translator can reach it.

**`InfoRow` could not survive its own text.** Two unconstrained `Text`s in a
`SpaceBetween` row have nowhere to go when they stop fitting; at 200% font scale
"Schedule" and "3 days / week · 45 min" run past each other. Both halves are
weighted now. Not yet seen on a device at 200%.

**Found and not fixed: the schedule cannot be changed after onboarding.**
`trainingDaysPerWeek` and `sessionLengthMs` are written by `feature:onboarding`
and by nothing else; Settings renders them as a read-only `InfoRow`. Goal,
experience and equipment are all editable there — the schedule is the one that
is not, and it is the most consequential of the four: `sessionLengthMs` is the
entire time budget the coach programmes against (4.5). Someone whose training
time changes has to reset the app, which wipes their history. It needs a control
that does not exist yet rather than a fix, so it is left as a decision.

### 4.7 — The schedule became editable, and Coach shows what it is building — **installed and reviewed on screen**

**Settings can change the schedule.** `trainingDaysPerWeek` and `sessionLengthMs`
were written by `feature:onboarding` and by nothing else, so the only way to say
"I train four days now, not three" was to reset the app and lose the history.
The read-only row is an `ActionRow` opening a dialog of two sliders. It holds a
draft and applies on Save, like the equipment dialog beside it: every other
control on that screen writes as you touch it because every other control is
reversible at a glance, and dragging a slider would otherwise write the profile
a hundred times on the way to 45 minutes.

**Coach shows the plan's shape, and can override it.** It asked exactly one
question — which muscles — on the reasoning that the profile already knew the
goal, the experience and the session length. Right about the asking, wrong about
the showing: those three shape every generated week and none of them were on the
screen doing the generating, so a week built for 45 minutes and one built for 90
looked identical until the plan arrived. All three are now seeded from the
profile and changeable for one plan, with **Save as default** as the separate
act that makes a change stick — wanting one endurance week is not announcing
that you have stopped training for strength. The button is disabled while Coach
agrees with the profile, because a button that writes what is already stored
looks broken in the way that makes people press it twice.

`GenerationRequest` grew `goalOverride` and `experienceOverride` to match the
three overrides it already had, and `AiWorkoutRequest` now reads goal and
experience from the request rather than reaching past it into the profile.

**Experience reads as a level.** "Under a year", "1 to 3 years" and "More than 3
years" were the definition rather than the name: three times the width of the
word they stand for, and they made the reader do arithmetic to find which end
was which. They are Beginner / Intermediate / Advanced now, with the spans moved
to `ExperienceLevel.detailRes` where onboarding still shows them at the moment
the question is asked.

**Three things stopped being written twice**, which is what made the above
tractable:

- `RfChoiceChips` and `RfValueSlider` moved into `core:designsystem`. Settings,
  Coach and onboarding all wanted both; the chip row had already been written
  twice by 4.6.
- `WorkoutLimits` owns the session range and step. `DAYS_RANGE = 1..7` in
  onboarding had been a duplicate of `WorkoutLimits.days` all along.
- `MS_PER_MINUTE` came out of a private companion the ViewModel could not see.

**`ValueSliderConversionTest` moved with the slider it guards**, from
`feature:onboarding` to `core:designsystem`. It records a real device bug — day
six of seven was unreachable because the conversion truncated — and a guard left
behind while its code moves is worse than no guard, because it goes on passing.
It also gained a case the old one could not have: the shared helper snaps to
legal stops, where the version it replaced rounded straight to an integer and
would answer "48 minutes" to a slider whose stops are multiples of five. That
never showed, because Compose snapped before calling back — the helper was only
correct because of its caller.

"Save as default" is an `OutlinedButton` with a save icon rather than the
`TextButton` it started as, which read as a link. Outlined and not full width on
purpose: full width is the shape "Build it" has at the bottom of the same
screen, and there is one primary action here.

Verified by `./gradlew test`, `./gradlew lint` and `./gradlew
assemblePlaceholderDebug` run separately, and installed on the S23. The override
wiring was watched failing. The maintainer has seen the Coach screen; the
Settings schedule dialog, the Turkish on the new strings, and 200% font scale
have not been looked at.

### 4.8 — A week can be reopened, and Today says it is following one — **installed; not yet confirmed on screen**

The two gaps the previous section listed as next, closed together because they
are the same feature finishing itself.

**A saved week reopens for editing.** `BuilderViewModel.load()` handled
templates and nothing else, so a week could be generated, saved, and never
edited again. 4.5 made that worse rather than better: making a week's *day* rows
tappable meant the app answered half the question — day three could be edited
while the week it belonged to could not be renamed, reordered or given another
day. `loadWeek(weekId)` fills `weekDays` the same way Coach does, and Plans
grew an edit control on the week card, because tapping the header has to go on
meaning "expand".

`Destination.Builder` took a second argument rather than gaining a sibling: the
builder already renders a week as days of the identical cards, so a separate
destination would be a second route to one screen.

Two things it is careful about, both of which have bitten this code before. The
day's saved template id is **carried, not regenerated** — a fresh id per load
would detach every day from the workout history recorded against it, which is
how Today knows what has been done. And the week's id does not go in `planId`,
which is a template id; letting them share is what made every re-save mint a
second week back in 4.3.

**Today says which week and which day.** It rendered a week's day as an ordinary
plan card with nothing naming the week or the position — on the screen whose
entire purpose is following one. The card now reads "PPL Week · Day 1 of 7"
under the workout name, and only when the recommendation actually came out of
the active week: an active week with no days, or a standalone plan recommended
alongside one, must not be labelled as a day of it.

**And the week card counts against the right number.** It compared this week's
completed days to `profile.trainingDaysPerWeek` whatever week was running, so
the maintainer's seven-day week read **"0 of 3 days"** — a target with nothing
to do with the week being followed. `TodayUiState.weeklyTarget` is the active
week's own length when there is one, and the profile's standing answer
otherwise.

Guards proven by breaking them: the template id carry-forward (one red) and the
weekly target (one red).

Verified by `./gradlew test`, `./gradlew lint` and `./gradlew
assemblePlaceholderDebug` run separately, then installed and launched on the
S23 — alive, empty crash buffer. Nothing here has been looked at on a screen.

## Phase 6 — Release hardening, started early

Out of order on purpose. Goldens and enforced CI were the cheapest way to stop
the phases above regressing while they were still moving, and every one of these
slices found defects in work that was already considered finished. The rest of
the phase — Play Store packaging, the media permission review, beta feedback —
has not started.

### 6.1 — Screenshot tests — **17 goldens, and two defects found recording them**

The only untested category, and the one this project's whole defect history
argues for: nine bugs found on a device and by nothing else, five of them in the
last two sections, and most of them a layout that could not hold its own text.
Unit tests asserted the state was right — it was — and passed throughout.

Roborazzi through Robolectric, so the matrix `AGENTS.md` asks for runs on the
JVM as part of `./gradlew test`: **English and Turkish, 1x and 2x font scale**,
across Settings, Today, Plans and the week review. Seventeen goldens, about
1.8 MB, committed beside the screens they are of.

**Two real defects surfaced while recording, which is the argument in
miniature.**

The first was in the harness and worth keeping anyway: a screen rendered outside
the app's `Scaffold` draws its dark-theme text onto a white window, so the first
Settings golden was pale green on pale grey. `RepForthPreviewHost` supplies the
ground the `Scaffold` normally does — public and in `main`, because `@Preview`
needs the same thing and a preview that lies about contrast is the same failure
by another route.

The second was in the app. `weekDayLabel` stripped a redundant "Day 1:" only
when it matched the *current* locale's header — but a title is written by the
model when the week is generated and kept, while the header is rendered in
whatever language the app is in now. Generate in English, read in Turkish, and
Plans showed **"1. Gün · Day 1: Chest and triceps"**: the exact double prefix
4.5 was supposed to have fixed, surviving a unit test that only ever compared a
title with its own language's header. It now strips a day number in either
language, and only when the number is that day's own.

**Three things about the setup are load-bearing**, each found by it going wrong:

- Every test states its own locale *and* font scale, including the defaults.
  Robolectric carries qualifiers across test methods in one JVM, so tests that
  set only what they changed passed alone and failed in suite order.
- They are excluded from the release unit tests. The host activity arrives via
  `debugImplementation`, so the release copy failed with no launcher activity —
  and a rendered composable does not differ by variant, so running them twice
  bought nothing.
- The goldens are declared as a task input. They are read through `java.io.File`,
  so without that, deleting every golden and re-running reported UP-TO-DATE and
  wrote nothing — the same blind spot `configureGuardTestInputs` exists for, in
  a new place.

Guard proven by breaking it: lengthening one English string turned both English
Settings goldens red and correctly left the Turkish pair alone.

Verified by `./gradlew test`, `./gradlew lint` and `./gradlew
assemblePlaceholderDebug` run separately.

### 6.2 — Screenshots for the rest of the screens — **42 goldens, three more defects**

6.1 covered the four screens every recent defect had been in. This covers the
rest: Session, Progress, Exercises and onboarding, same matrix. **42 goldens,
3.9 MB**, and the first render of each screen found something.

**Progress ran three labels together.** `ProgressPanel` put three unweighted
`Figure` columns in a `SpaceBetween` row, so at 200% font scale in Turkish the
labels butted straight into each other: `AntrenmanBu haftaHaftalık seri`, no gap
anywhere. Exactly the shape of the `InfoRow` bug in 4.6, in a screen nobody had
thought to look at. Weighted and spaced now.

**Progress printed English dates in a Turkish UI.** `formatDate` used
`DateTimeFormatter.ofLocalizedDate` with no locale, which formats in
`Locale.getDefault()` — the JVM's, not the one the composition is rendering in,
and this app lets the user pick a language independently of the system. The
Turkish golden read "Jan 1, 2026" above "18 set · 45 dk". It reads the
configuration locale now, as the rest of the screen already did; the volume
figure had the same bug in `NumberFormat` and is fixed with it.

**Session showed the target weight twice.** The label under the big number
appended "· 60 kg" while the line directly below it said "60 kg" in the accent
colour — the same field, rendered twice, one line apart. The accent line stays,
because it is the prominent one and it is what says "Bodyweight" when there is
no load.

Guard proven again on the new modules: lengthening one English string turned the
two English Session goldens red and left the Turkish pair alone.

Verified by `./gradlew test`, `./gradlew lint` and `./gradlew
assemblePlaceholderDebug` run separately.

Coach gained its own goldens afterwards, closing a gap the test's own comment
had claimed was already closed.

**Five defects have now been found by screenshot tests in two sittings**, on
screens that unit tests, instrumentation tests and a person holding a phone had
all passed. Every one was a layout that could not hold its own text, or a string
that was not in the language around it — the two categories nothing else in this
repo looks at.

### 6.3 — CI is enforced, and the goldens survive the runner — **done**

`master` is protected: `Validate Gradle wrapper` and `Build and test` are
required, the branch must be up to date, force pushes and deletions are off,
and **admins are not exempt**. Direct pushes to `master` are no longer possible
by anyone; work goes through a pull request whose checks passed on the exact
commit being merged. No review is required, so a solo change is still one
command to merge.

**Turning it on exposed that CI had been failing for two pushes, and protection
went on before that was checked** — for about half an hour `master` was gated by
a check that could not pass. The order was wrong and is worth recording as
such: verify the gate is green, then close it.

**Every golden failed on the Ubuntu runner**, and the cause was worth the
detour. The side-by-side showed reference and render looking identical with an
empty diff panel, so all 31 CI renders were compared against their goldens
numerically: **at most 0.069% of pixels different, by at most 4 of 255**.
Antialiasing along glyph edges, nothing more.

Two fixes missed before the third worked, and the misses are the useful part:

- `roborazzi.compare.changeThreshold` as a system property does nothing here.
  Roborazzi reads it through its Gradle plugin, which this repo does not apply.
- `SimpleImageComparator(maxDistance = 0.02f)` — comfortably above the worst
  4/255 — also did nothing, because `CompareOptions.resultValidator` has the
  last word regardless of what the comparator tolerated.

The tolerance is now stated as the validator: at most 0.1% of pixels may
differ. That is 1.45x the measured noise, against roughly 0.25% for a one-word
label change and whole percent for a wrapped line. The lower margin is thin and
the comment says so — a platform whose text rendering drifts further needs this
re-measured rather than nudged.

---

## Next

In the order they are worth doing, and why.

1. **Two surfaces still have no golden, and both are dialogs.** The Settings
   schedule dialog and the equipment dialog are opened by state held inside
   `SettingsScreen`, so a screenshot test cannot reach them without hoisting
   that state — which is a change to the screen for the sake of the test, and
   worth thinking about rather than doing reflexively. The AI provider screen
   has none either; it was left out because its interesting states are a typed
   key and a connection result rather than a layout under pressure.

   **3.3 raised the price of this.** The equipment dialog is not merely
   unphotographed, it is unreachable by the accessibility checks too — a
   deliberate break of its `Role.Checkbox` was not caught, because no test can
   open it. Hoisting that state now buys two guards rather than one.
2. **Progress computes three figures it never draws.** `daysThisWeek` and
   `totalSets` are calculated in `SessionStatistics.kt` — the first with a
   distinct-calendar-day pass over the week — and no composable in
   `feature/history` reads either. `WorkoutSummary.exerciseCount` is the same
   story per row. `ProgressSummary.topMuscles` is a fourth, though that one is
   documented as empty until the catalog is joined. That is most of "progress
   visuals" already sitting in memory with nothing drawing it.
3. **Motion exists as tokens and is applied to one button.** 3.4 built the
   scale and the reduced-motion switch; the shared-axis plan-to-detail
   transition and the set-completion spring the design system names are still
   unwritten.
4. **Baseline profiles have not been started**, and are the last named item in
   §19's Phase 3.
5. **The screenshot tolerance has a thin lower margin.** 0.1% against 0.069%
   of measured noise. It holds for Windows and this Ubuntu runner; a third
   platform, a Robolectric bump or a font change could close the gap, and the
   answer then is to re-measure rather than raise the number.

---

## Decisions already made

Closed. Reopen only with a reason, and update the guideline in the same change.

| Decision | Rationale | Where |
|---|---|---|
| Plans is a tab; Coach is a mode inside the builder | Coach is an input method for building a plan, not a place | Guideline §12 |
| Dynamic/wallpaper colour disabled | Breaks the single-accent rule and the measured AA pairs | `Theme.kt` |
| Vector drawables, not the Material Symbols font | 53 icons used; the font ships ~3,300 glyphs, and `res/font` cannot feed `Icon()` | This file, above |
| Static font weights, not variable | 614 KB vs. ~1.5 MB for weights the tokens never ask for | `Type.kt` |
| Media lives in the manifest, not the catalog tables | Keeps the licensing boundary a type seam, not a convention | `ExerciseEntity.kt` |
| Room v1 is catalog-only | User tables ship with the code that writes them, not before | `RepForthDatabase.kt` |
| No destructive migration, ever | Losing the only copy of a user's history is not an upgrade path | `SchemaExportTest.kt` |
| Categorical values stay slugs until the import | Enum constants written before reading the dataset are guesses | `Exercise.kt` |
| Android Auto Backup is off | Cloud backup is an MVP non-goal (§4), and a restore across a schema change is a guaranteed launch crash | `BackupPolicyTest.kt` |
| Hilt pinned below latest | Newer releases ship a plugin built against a newer Kotlin stdlib | `libs.versions.toml` |
| CI builds `placeholder` only | §20's claim is that the public source builds with no private credentials; `licensed` assets must not reach a public runner | `.github/workflows/ci.yml` |
| Guard tests declare their files as task inputs | Otherwise the task is UP-TO-DATE and passes on the exact change it guards | `GuardTestInputs.kt` |
| The app does not inspect the provider address | Every rule that could be written refused the local model server it existed for; the cost — a key readable over `http://` — is accepted and stated | Guideline §8, amended |
| Gemini's endpoint is fixed in the adapter | A stored address must not be able to redirect a Gemini key, and it is the only address protection left | `GeminiProvider.kt` |
| Weekly plans are a phase in the guideline, not a footnote | The feature was shipped before it was specified, so §20 could have declared v1 done without it; Wear moved 4→5 and hardening 5→6 to keep delivery order | Guideline §19, §20 |
| Release hardening starts early and out of order | Goldens and enforced CI stop finished phases regressing while later ones move; every slice of it found a defect in work already called done | Phase 6, below |
| User-installed CAs stay untrusted | A self-signed local server is the other way people ask for LAN support, and the worse one | `network_security_config.xml` |
| The provider key is required only where the provider requires it | Ollama and LM Studio ignore it; demanding one meant typing a throwaway value past a check that protected nothing | `ProviderId.requiresKey` |
| Only `core:ai` and `core:media` may declare an HTTP client | Keeps network access bounded strictly to AI generation and on-demand media downloads | `NetworkBoundaryTest.kt` |
| A week contains templates rather than replacing them | Keeps the session engine, history, export, and Wear protocol on one shape | `TrainingWeek.kt`, `WEEKLY_PLANS.md` |
| One contract, weeks always; a single workout is a week of one | Two contracts would drift within a phase, and Coach still generates single workouts | `WEEKLY_PLANS.md` §4.1 |
| Deleting a week deletes its workouts | An orphan named "Day 3 — Pull" is litter; sessions performed from it still survive | `TemplateEntity.kt`, `WEEKLY_PLANS.md` |
| Days are ordinal; weekdays are optional | The profile knows how many days, not which; inventing them is a guess presented as a plan | `TrainingWeek.kt`, `WEEKLY_PLANS.md` |
| One week is active, by stored flag | Today is believed, and an inferred wrong answer is worse than none | `WeekDao.kt`, `WEEKLY_PLANS.md` |

Still open, and fine to leave open (§21): final application ID, accent colour,
app icon, and the exact licence.

---

## Known risks

- **Body map touch target ergonomics on phone.** Physical device testing identified
  that the body map's tap targets on phone screens feel small for consistent,
  accurate touch input. The artwork is sound, but needs an expanded presentation,
  dedicated zoom/full-screen sheet, or enlarged touch bounds in a follow-up iteration.
- **The v1 to v2 migration is proven; no later one exists yet.** `MigrationTest`
  ran on a Galaxy S23 on 2026-08-31, five tests, no failures. The risk that
  remains is the next schema change, not this one. Re-run with
  `./gradlew :core:database:connectedAndroidTest` — and read the warning in
  `AGENTS.md` first, because `connectedAndroidTest` uninstalls the app when it
  finishes and uninstalling wipes exactly the data the migration protects.
  Export first, every time.
- **A live provider has now returned a week**, on schema version 4: six days
  from Gemini on a Galaxy S23, first try. Every *automated* multi-day test still
  answers from MockWebServer, so the shape is confirmed by hand and not by the
  suite. Whether a **small local model** holds a strict seven-day schema remains
  unmeasured, and stays the risk most likely to change the design;
  `docs/WEEKLY_PLANS.md` §4.6 records the fallback and its trigger.
- **Screenshot tests cover every screen but AI settings, and no dialog.** 46
  goldens across nine screens, both languages, both font scales; the Settings
  schedule and equipment dialogs open from state held inside the screen, so
  nothing renders them. A layout regression in the
  AI provider screen is still something only a person holding a phone would
  notice.
- **A golden agrees with whatever it was last shown.** Re-recording is one flag
  away, and a re-record that nobody looked at turns the guard into a rubber
  stamp. Read the diff before committing a changed image.
- **Two devices, and they disagree.** A Galaxy S23 (API 34) runs the
  instrumentation suite; the Xiaomi (API 30) hangs on it, because MIUI refuses
  an activity start from instrumentation and the permission that would allow it
  cannot be set over adb. Paired-watch tests still have no hardware at all, so
  Phase 5 remains unverifiable here.
- **Cleartext is permitted and nothing narrows it (2.3d, §8 amended).** There is
  no address policy any more: a base URL typed as `http://` is sent as `http://`,
  to any host, and the API key rides in a header in clear text. That is the
  maintainer's decision, taken with the consequence stated, and §8 carries the
  reasoning. What it means for anyone changing this code is that there is no
  second line of defence — nothing downstream will catch an address a screen
  lets through, because nothing downstream looks.
- **`NetworkBoundaryTest` is a scope control, not a security one.** It asserts
  one module reaches the network through one file, which is what keeps "where
  does this app talk out" answerable. Do not read it as protecting cleartext; it
  used to, and no longer does.
- **uiautomator is not a reliable oracle near the bottom of the screen.** It
  reports the legacy application frame as the window (1080x2266 on a 1080x2400
  phone) and clips anything below it to `bounds=[0,0][0,0]`, whether or not the
  system reserves that space. The bottom navigation labels report zero bounds
  while being plainly visible and tappable. A zero-bounds reading down there
  means take a screenshot, not that the element is missing — chasing one as a
  layout bug cost a wrong diagnosis and a build.
- **KSP is pinned to the Kotlin version.** Bumping `kotlin` without bumping
  `ksp` in the same commit fails the build in a way whose message does not
  mention the real cause.
- **CI is enforced as of 6.3.** `master` requires both checks and exempts
  nobody. What follows was written before that and is kept for the reasoning:
  branch protection is a repository setting, so
  until a maintainer turns it on, a red build reports the failure but does not
  prevent the merge.
- **Phase 2 introduces secrets.** Key handling must land with its own tests and
  a CI secret scan on day one, not as hardening later — §20 requires keys to be
  absent from Room, DataStore, logs, backups, exports, source, CI, and watch
  messages, and that is far cheaper to build in than to retrofit.
