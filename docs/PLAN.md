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
| 1 — Local workout core | §19 | **Complete.** Engines, data, all six screens, and Coach |
| 2 — AI providers | §19 | **In progress.** Storage, settings, contracts, transport, and orchestration done; Coach integration remains |
| 3 — Polished phone | §19 | Not started |
| 4 — Wear remote | §19 | Not started |
| 5 — Release hardening | §19 | Not started |

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
one is a direct regression guard for the fifth. Screenshot tests still do not
exist, and remain the only untested category.

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

Modules today: `app`, `core:ai`, `core:common`, `core:database`, `core:datastore`,
`core:designsystem`, `core:exercise-data`, `core:model`, `core:rules`,
`core:testing`, `core:transfer`, `core:user-data`, `core:workout`,
`feature:builder`, `feature:exercises`, `feature:history`, `feature:home`,
`feature:onboarding`, `feature:session`, `feature:settings`.
391 unit tests across 50 classes, plus fourteen instrumentation tests
watched passing on a Galaxy S23 — six in `:app`, eight in `core:secrets`. Room schema v1 exported and
committed.

Counted from the JUnit XML and de-duplicated across build variants. There are 50
classes that contain tests and 51 Kotlin files under `src/test`; the extra file
is `core:transfer`'s shared fakes. The distinction is recorded because a number
nobody can reproduce is worse than no number.

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

### 2.4 — The generation pipeline — **in progress**

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

The orchestration boundary is complete. `RulesEngine.filterCandidates` is now
the one deterministic candidate-level hard-rule pass used before either local
or provider generation, so the provider never sees an excluded, unavailable or
off-target exercise. `AiWorkoutGenerator` resolves provider configuration for
one call, validates the first answer, and permits one repair attempt only for a
malformed or locally invalid response. That attempt receives compact typed error
codes and exercise ids encoded as JSON; it never receives rule-detail strings or
raw provider output. Missing configuration, a missing adapter, every actionable
provider failure, an empty eligible set, and a second invalid answer all return
the existing deterministic `GenerationOutcome` with a typed fallback reason.

Eleven further unit tests cover all five fallback reasons, every provider failure
category, stable local filtering, first-attempt success, both repair paths, typed
feedback transport and deterministic fallback. The malformed-output retry test
was watched failing after the retry condition was deliberately changed, then the
condition was restored. The full placeholder assembly, 391-test suite and lint
all pass after restoration.

**The response contract now uses one exact repetition target.** Version 1 copied
the guideline's original repetition-range wording even though the builder and
saved-plan model both hold one number. Collapsing `8–12` into a midpoint would
discard provider output invisibly, so the specification was corrected and schema
version 2 requires the provider to choose the editable target itself.

Still to build:

1. Builder integration and the user-facing retry/fallback notice. No generation
   request has reached a live provider yet.

### 2.5 — Coach UI

Muscle-specific generation through a provider, arriving in the builder as the
same editable draft the rules engine already produces.

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
| User-installed CAs stay untrusted | A self-signed local server is the other way people ask for LAN support, and the worse one | `network_security_config.xml` |
| The provider key is required only where the provider requires it | Ollama and LM Studio ignore it; demanding one meant typing a throwaway value past a check that protected nothing | `ProviderId.requiresKey` |
| Only `core:ai` may declare an HTTP client | Keeps "where does this app talk out, and why" answerable from one place | `NetworkBoundaryTest.kt` |

Still open, and fine to leave open (§21): final application ID, accent colour,
app icon, and the exact licence.

---

## Known risks

- **Screenshot tests are still the untested category.** Unit tests and
  instrumentation both run; nothing yet asserts what a screen looks like, so
  every layout regression found so far was found by a person holding a phone.
- **Two devices, and they disagree.** A Galaxy S23 (API 34) runs the
  instrumentation suite; the Xiaomi (API 30) hangs on it, because MIUI refuses
  an activity start from instrumentation and the permission that would allow it
  cannot be set over adb. Paired-watch tests still have no hardware at all, so
  Phase 4 remains unverifiable here.
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
- **Nothing enforces CI yet.** Branch protection is a repository setting, so
  until a maintainer turns it on, a red build reports the failure but does not
  prevent the merge.
- **Phase 2 introduces secrets.** Key handling must land with its own tests and
  a CI secret scan on day one, not as hardening later — §20 requires keys to be
  absent from Room, DataStore, logs, backups, exports, source, CI, and watch
  messages, and that is far cheaper to build in than to retrofit.
