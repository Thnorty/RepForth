# Weekly plans — design and implementation plan

Status: **W1–W3 built; W4 partial, W5 not started.** Written 2026-08-31, revised
after implementation.

Where this document and the code now disagree, the code won and this file has
been corrected — see §5.1, which proposed a route change that was not taken. The
per-slice record of what actually landed is in `docs/PLAN.md` §4.1–4.3; this file
keeps the reasoning.

## What this file is

`docs/PLAN.md` is the state of the build: what exists, what is next, what is
closed. This file is a single feature's design, held separately so that a
proposal is not mistaken for a description of the code. When the first slice
lands, its outcome goes into `docs/PLAN.md`; when the last one lands, this file
either becomes history or is deleted. It is not a second state file — read the
status line above for what is built, and `docs/PLAN.md` for the detail.

The feature: **the AI Coach generates a week of training rather than a single
workout.** Everything else in this document is a consequence of that, including
the parts that look like unrelated UI work.

---

## 0. Decisions already taken

Two forks were put to the maintainer before any code was planned in detail.
Both are answered, and both are load-bearing enough that reversing either
changes the slice order.

| Question | Answer | What follows from it |
|---|---|---|
| Deleting a week — delete its days, or detach them? | **Delete them.** | `workout_template.week_id` is a foreign key with `ON DELETE CASCADE`. The confirmation dialog must name the number of workouts about to go. Sessions performed from those workouts still survive. |
| Does Coach still generate single workouts? | **Yes.** | There is exactly one contract and one generation path. A single workout is a week of one day. Plans keeps two lists, because standalone workouts continue to exist. |

The second answer is the more consequential of the two: it rules out the
tempting shortcut of a separate "week mode" with its own request type, its own
schema, its own validator, and its own prompt. See §2.

---

## 1. The decision everything else follows from

**A week is a container of workouts, not a new kind of workout.**

A day within a week must remain exactly a `WorkoutTemplate`, stored in
`workout_template` and `template_exercise` exactly as it is today.

The reason is blast radius. `SessionEngine`, `SessionController`,
`WorkoutService`, `RoomSessionRepository`, `SessionStatistics`, the history
screen, `core:transfer`'s export and import, the builder, and the not-yet-built
Wear protocol all consume a workout as a `WorkoutTemplate`. If a day inside a
week is a different shape, every one of those grows a second code path for a
concept it already handles. If a day *is* a template, all of them need **no
change at all**, and the work reduces to a grouping layer above something that
already works.

The corollary is worth stating because it will be tempting to break later: **a
week never holds exercises.** It holds days; days hold exercises, through the
existing table. Any design where `training_week` reaches an exercise without
passing through `workout_template` has reintroduced the second path.

---

## 2. Vocabulary

Settle this before a single string is written, in both languages at once.

"Plan" is already taken. `planId` is a template id, `PlansScreen` lists
templates, `plans_empty` describes having no templates, and
`Destination.Builder(planId)` edits one. If a week becomes "the plan", every one
of those names becomes ambiguous, and renaming them afterwards is a sweep across
three feature modules and two string files.

| Concept | Code | English | Turkish |
|---|---|---|---|
| The week | `TrainingWeek` | Week / Weekly plan | Hafta / Haftalık plan |
| One day of it | `WeekDay`, backed by a `WorkoutTemplate` | Day 3 · Pull | 3. gün · Çekiş |
| A workout not in a week | unchanged `WorkoutTemplate` | Workout | Antrenman |
| The act of asking for one | Coach | Coach | Koç |

Turkish runs 15–30% longer than English, and the week card is a dense component —
day chips, a duration, a badge. It is the most likely place in this feature for
a layout to truncate. Nothing in it gets a fixed height.

---

## 3. Data model and the first real migration

### 3.1 Schema

Two additions, no restructuring:

```sql
CREATE TABLE training_week (
    id          TEXT    NOT NULL PRIMARY KEY,
    name        TEXT    NOT NULL,
    notes       TEXT,
    source      TEXT    NOT NULL,          -- MANUAL | AI, same vocabulary as PlanSource
    active      INTEGER NOT NULL,          -- 0/1; at most one row is 1
    created_at  INTEGER NOT NULL,
    updated_at  INTEGER NOT NULL
);

-- added to the existing workout_template
week_id       TEXT    REFERENCES training_week(id) ON DELETE CASCADE,
week_position INTEGER,                     -- 0-based, contiguous within a week
day_of_week   INTEGER                      -- 1..7 (ISO, Monday = 1), or NULL
```

`week_id IS NULL` means "a standalone workout", which is every template that
exists today. That predicate is what keeps `PlansScreen` from listing five extra
rows per generated week, and it is the whole of the migration's data step: every
existing row is already standalone and needs no backfill.

**Why columns on the template rather than a join table.** A join table would
allow one template to belong to two weeks. That is not a feature; it is a trap.
Editing Monday in one week would silently edit a different week's Monday, and
the person doing it would have no way to see that from the builder. Columns say
"a template optionally sits at one position in one week", which is the truth we
want enforced by the schema rather than by a convention someone has to remember.

**Why `active` is a stored flag.** Today has to answer "what do I train now".
Inferring the current week from timestamps — newest, or the one containing the
most recently performed workout — is guessable right up until the user keeps two
weeks around, at which point Today confidently names the wrong workout. A wrong
answer here is worse than an absent one, because Today is believed. One
explicit flag, set when a week is created, changeable from Plans. Enforce
at-most-one in the repository's transaction, not by hoping.

**Why `day_of_week` is nullable.** See §5.4.

### 3.2 Domain types

`core:model` gains:

```kotlin
data class TrainingWeek(
    val id: String,
    val name: String,
    val notes: String? = null,
    val source: PlanSource,
    val active: Boolean,
    val days: List<WeekDay>,
) {
    init {
        require(name.isNotBlank())
        require(days.map { it.position } == days.indices.toList())
        require(days.mapNotNull { it.dayOfWeek }.let { it.size == it.toSet().size })
    }
    val estimatedDurationMs: Long get() = days.sumOf { it.workout.estimatedDurationMs }
}

data class WeekDay(
    val position: Int,
    val title: String,
    val dayOfWeek: DayOfWeek? = null,
    val workout: WorkoutTemplate,
)
```

The contiguity `require` mirrors `WorkoutTemplate`'s, for the same reason: a week
whose days claim positions 0, 1, 3 is a bug that should fail where it is
constructed rather than render as a gap.

`PlanSource` is reused rather than duplicated — a week is `MANUAL` or `AI` for
exactly the reasons a template is, and `RULES` stays legacy-only.

### 3.3 Repository

`core:user-data` gains `WeekRepository` beside `TemplateRepository`:

```kotlin
interface WeekRepository {
    fun observeAll(): Flow<List<TrainingWeek>>
    fun observeActive(): Flow<TrainingWeek?>
    suspend fun find(id: String): TrainingWeek?
    suspend fun save(week: TrainingWeek)        // week + all its days, one transaction
    suspend fun setActive(id: String)
    suspend fun delete(id: String)              // cascades to its templates
    suspend fun deleteAll()
}
```

`save` writes the week row and every day's template and exercises inside one
`@Transaction`, following `TemplateDao.replaceTemplate`'s existing precedent:
children are deleted and re-inserted rather than diffed, because a
partially-applied reorder produces a state the domain type refuses to construct
and therefore surfaces as a crash on read rather than as the mistake it is.

`TemplateRepository.observeAll()` must start filtering to `week_id IS NULL`, or
Plans double-lists. That is a behavioural change to an existing method; give it
its own test rather than letting it ride along.

Two existing call sites must be checked when this lands, because both walk
templates and neither should silently skip or duplicate a week's days:

- `core:transfer`'s export/import — a week must survive a round trip, or the
  export stops being a complete copy of the user's data (§7).
- `DataTransfer`'s delete-all and reset paths, which `ResetCoverageTest` already
  guards by reading the constructor. A new store added there fails that test by
  existing, which is the intended behaviour — extend the reset, do not weaken
  the guard.

### 3.4 Write the migration; do not extend version 1

`RepForthDatabase` is at version 1, and its comment records that the schema has
never been released, so adding tables in place is technically permitted. Do it
as a real `Migration(1, 2)` anyway.

Three reasons:

1. **There is data on the devices now.** Profile, plans, and history on both the
   Galaxy and the Xiaomi. Bumping the identity hash forces an uninstall, and
   `docs/PLAN.md` records what that cost twice — including the Auto Backup launch
   crash, where a clean uninstall was *still* not enough because Android restored
   the old database from Google's servers.
2. **§18 deferred Room migration tests as belonging "to the phase that creates
   their subject".** This is that phase. This is the first schema change with
   something worth keeping on the other side of it.
3. **The first migration is cheapest to get wrong now**, while a mistake costs a
   red test instead of the only copy of someone's training history. `docs/PLAN.md`
   already closes the decision "no destructive migration, ever"; this is the
   slice where that decision starts being exercised rather than merely declared.

The migration is additive — one `CREATE TABLE`, three `ALTER TABLE ADD COLUMN`,
and an index on `week_id`. No data moves.

**Testing it needs care.** `MigrationTestHelper` requires instrumentation, and
`AGENTS.md` records that `connectedAndroidTest` uninstalls the app when it
finishes — including when interrupted — which wipes the data the migration
exists to protect. Export first, or run it on the device whose data does not
matter. This is the same footgun that once looked like "the app uninstalled
itself".

---

## 4. The AI contract: one week-shaped contract, version 3

### 4.1 Replace the workout contract; do not add a second one

Because Coach still generates single workouts (§0), there are two possible
designs, and only one of them is consistent with this repository:

- **Two contracts.** `AiWorkoutRequest`/`Response` stays for a day,
  `AiWeekRequest`/`Response` is added for a week. Two JSON schemas, two prompts,
  two validators, two sets of MockWebServer tests, and two places to update every
  time a limit changes. Rejected: it is the "write it once" rule broken in the
  most expensive possible place, and the two would drift within a phase.
- **One contract, weeks always.** A single workout is a week of one day.
  Everything downstream has one shape to handle. Adopted.

Version bumps from 2 to 3. `AI_WORKOUT_SCHEMA_NAME` derives from the constant
already, so the provider-visible name follows automatically.

### 4.2 Shape

```kotlin
@Serializable
data class AiWeekRequest(
    @SerialName("schema_version") val schemaVersion: Int,   // 3
    val locale: String,
    val goal: String,
    val experience: String,
    val days: Int,                                          // 1..7
    @SerialName("session_duration_minutes") val sessionDurationMinutes: Int,
    @SerialName("max_exercises_per_day") val maxExercisesPerDay: Int,
    @SerialName("primary_muscles") val primaryMuscles: List<String>,
    @SerialName("secondary_muscles") val secondaryMuscles: List<String>,
    @SerialName("excluded_muscles") val excludedMuscles: List<String>,
    @SerialName("excluded_exercise_ids") val excludedExerciseIds: List<String>,
    @SerialName("excluded_movements") val excludedMovements: List<String>,
    val equipment: List<String>,
    @SerialName("candidate_exercises") val candidateExercises: List<AiExerciseCandidate>,
)

@Serializable
data class AiWeekResponse(
    @SerialName("schema_version") val schemaVersion: Int,
    val days: List<AiPlannedDay>,
    val rationale: String,
)

@Serializable
data class AiPlannedDay(
    @SerialName("day_index") val dayIndex: Int,
    val title: String,                                      // "Push", "Lower body"
    @SerialName("focus_muscles") val focusMuscles: List<String> = emptyList(),
    val exercises: List<AiPlannedExercise>,
)
```

**`AiPlannedExercise` does not change.** That is the point of the shape above:
`AiWorkoutValidator`'s per-exercise checks, the `WorkoutLimits`-derived schema
builder, and the `toDrafts` projection in `BuilderViewModel` are all reused
verbatim rather than re-derived for days. `AiExerciseCandidate` is likewise
untouched.

### 4.3 Two corrections to make while the version is already bumping

- **`duration_minutes` becomes `session_duration_minutes`.** Today the field
  means one session's ceiling, and with `days > 1` a model will read the old name
  as the budget for the whole week and return five twelve-minute workouts. This
  is not a hypothetical: the name is genuinely ambiguous once a week exists. A
  schema version bump is the one moment renaming a wire field is free.
- **`AiWorkoutRetryFeedback` gains `day_index`.** The repair prompt can currently
  name the offending `exercise_id` but has no way to say which day it was in, so
  with seven days the model is told an id is wrong and left to find it.

### 4.4 JSON schema

`AiWorkoutJsonSchema` grows one nesting level. The existing per-exercise schema
becomes the `items` of a day's `exercises` array; days become the top-level
array. Every numeric bound still derives from `WorkoutLimits` — no literals — and
the existing guard that fails when the schema and `WorkoutLimits` drift extends
to the new bounds rather than being replaced.

`WorkoutLimits` gains:

```kotlin
val days = 1..7
const val maxExercisesPerDay = 8   // see §4.6
```

`maxExercises` stays as the ceiling for a standalone workout so the builder's
existing limit is unchanged.

### 4.5 Validation

Per day, unchanged: the existing `AiWorkoutValidator` per-exercise checks, then
`RulesEngine.validate` against the session ceiling and the profile's hard
constraints. `RulesEngine.filterCandidates` still runs once, before the call, so
the provider never sees an excluded, unavailable, or off-target exercise.

Four week-level rules on top:

1. `days.size` equals the requested day count.
2. `day_index` values are contiguous from zero.
3. `day_of_week`, where assigned, is unique across the week.
4. **An exercise may repeat across days, and must not repeat within one.**

Rule 4 needs a comment in the code saying why, because the existing rule reads
"no duplicate exercise ids" and lifting it naively to week level would forbid
benching on two days — which is not a violation, it is how programmes are
written. The per-day validator already enforces the within-day half correctly;
the week validator must not re-apply it across days.

A day that fails validation fails the week. There is no partial acceptance: the
repair attempt exists precisely so the model gets one chance to fix the day it
got wrong, and accepting six good days and silently dropping the seventh would
hand the user a week that quietly does not match what they asked for.

### 4.6 The risk that must be measured, not assumed

Five days at ten exercises is a ~50-item strict-schema response. That is a
different animal from today's ~6-item one, and the user most exposed to it is
the one running a local model on their own machine — small models are exactly
where long strict structured output falls over, and that setup is the case §8
names first.

The plan is **one call**, because a model that cannot see the whole week cannot
build a split, and a split is the entire point of a weekly plan. Mitigations
that ship with it:

- `max_exercises_per_day` in the request, defaulting below `maxExercises`.
- The request timeout scales with day count. 60 seconds is a reasonable default
  for one workout and probably not for seven; `ProviderSettings.requestTimeoutSeconds`
  already exists and is user-configurable up to 300, so the scaling is a
  multiplier on the configured value rather than a new setting.
- The prompt states the three-phase structure per day, as it already does per
  workout.

**Measure before deciding anything further.** The documented fallback, if device
testing shows small local models failing, is N sequential per-day calls carrying
the previous days' selections as context. Do not pre-commit to it: it costs N
times the tokens, has N times the failure surface, and gives up the whole-week
view. It is a fallback with a trigger, not a plan.

---

## 5. Routes and UI

### 5.1 Coach becomes a route — **not adopted**

**What was built instead:** Coach stayed a mode inside the builder, exactly where
§12 puts it, and `BuilderUiState` grew `weekDays: List<DraftWeekDay>` beside its
existing `exercises`. `WeekReviewScreen` is a branch of `BuilderRoute`, not a
destination. Nothing in `Destination.kt` or `RepForthNavHost.kt` changed, and §12
therefore needed no amendment — which is a genuine benefit this proposal would
have spent.

The cost this section predicted was real and did arrive: the builder briefly held
twenty-four handlers for twelve actions, one set per list. That was fixed by
giving every editing action a nullable `dayIndex` and routing through a single
`withExercises(dayIndex)` — so the cost is paid once, in one private helper,
rather than by a route change. On balance the not-taken option was the right call
and this section is kept for the argument, not as a description.

The original reasoning follows.

Today `coaching` is a boolean inside `BuilderUiState`, and the builder's entire
state is *one* draft list — name, exercises, targets. A week does not fit in it.

The alternative is making the builder hold `List<DraftDay>`, which means every
one of its twelve event handlers (`onSetsChange`, `onRepsChange`,
`onRestChange`, `onMove`, `onRemove`, …) grows a day index, and the manual
single-workout path — the common case, and the one that works without any
provider — pays the entire cost of the week feature in its own state type. That
is the wrong trade.

```
Plans  ─┐
        ├─→  Destination.Coach       the ask: days · muscles · session length
Today  ─┘         │
                  ├─→  generating    (the existing card, cancel guard, error popups)
                  │
                  └─→  week review   one collapsed card per day
                          │
                          ├─ expand a day → the existing exercise cards, inline
                          │
                          └─ Save → week + N templates, one transaction
```

`Destination.Builder(planId)` is unchanged and still edits a single saved
workout, including one that belongs to a week, reached from the week card in
Plans.

### 5.2 Edit days inline, not by navigating

The review screen expands a day into editable exercise cards in place. It does
**not** navigate into the builder for an unsaved day.

Navigating would mean carrying an unsaved draft across a route boundary — either
a nav-graph-scoped ViewModel or a provisional week written to the database and
cleaned up on abandonment. "Nothing is written until the user saves" is a
property Coach has today and is worth keeping; the provisional-row version of it
is the kind of thing that leaves orphans when a process dies.

The mechanical consequence: **extract the exercise card out of `BuilderScreen.kt`
into a shared composable** that both the builder and the week review screen use.
That is the "components before copies" rule doing exactly its job, and it should
happen as part of W3 rather than as a follow-up.

The generation card, the cancel-generation confirmation, and the error/retry
popups move across from `CoachScreen.kt` unchanged.

### 5.3 Plans becomes two-level

- Weeks first, as expandable cards: name, day count, total estimated minutes,
  which day is next, and a badge on the active week.
- Standalone workouts below, exactly as today.
- Empty state offers both paths — build one by hand, or ask Coach for a week.
- Deleting a week confirms with **the number of workouts it will delete**, per
  §0. "Delete this week?" is not an adequate question when the answer removes
  five workouts.

`week_id IS NULL` is what keeps the two lists disjoint.

### 5.4 Today becomes week-aware, and degrades cleanly

Today gains a week line — "Week · Day 3 · Pull" — with a start button, above the
existing week-progress card.

`recommendNext` gains a sibling for the in-week case, kept in `core:workout`
beside it and pure for the same reason:

- If the active week has a day assigned to today's weekday, that day.
- Otherwise the earliest day not performed since the week became active.
- Otherwise nothing, and Today falls through to today's `recommendNext` over
  standalone plans.

That last fallback is what keeps Today identical for a user who never touches
Coach, which matters: AI is optional, and §12's argument for keeping Coach out of
the bottom bar rests on the app being fully useful without a provider.

### 5.5 Weekdays: ordinal first, calendar optional

The profile knows `trainingDaysPerWeek` (1..7) but not *which* days, and
onboarding does not ask. Two things follow:

- `week_position` is required. A week always has an order.
- `day_of_week` is optional, assigned by the user on the review screen or left
  null.

Generated weeks come back ordinal — "Day 1", "Day 2" — because the model has not
been told which weekdays the user trains and inventing them would be a guess
presented as a plan. If the user assigns weekdays, Today can say "it is leg day";
if they do not, Today says "next up: Day 3", which is still true for someone who
trains when they train rather than on a calendar.

Collecting preferred weekdays in onboarding and the training-profile settings is
a natural follow-up and deliberately **not** in this feature. It changes the
profile schema, and folding a second schema change into the migration slice would
mix two arguments into one commit.

---

## 6. Slice order

Each slice is shippable and names how it is checked, because a slice with no
check is a slice that silently half-lands.

| | Slice | Contents | Verified by |
|---|---|---|---|
| **W1** | Domain and persistence | `TrainingWeek`, `WeekDay`, the two schema additions, `WeekRepository`, the `week_id IS NULL` filter, transfer and reset coverage, `Migration(1, 2)` | Unit tests, plus the migration test on the Galaxy. Export the device's data first. |
| **W2** | Contract v3 | Request, response, JSON schema, prompt, week validator, retry feedback with `day_index`, both adapters carrying the new schema | MockWebServer tests for both adapters; the schema ↔ `WorkoutLimits` guard extended and watched failing |
| **W3** | Coach route and review UI | `Destination.Coach`, day-count control, extracted exercise card, inline day editing, one transactional save | **A real provider on a device — Gemini *and* the local server.** Instrumentation test for generate-and-save |
| **W4** | Plans and Today | Week cards, delete confirmation with count, active week, Today's day, fallbacks | On device, both languages, 200% font scale |
| **W5** | *(optional)* Progress | Week adherence — days done of days planned | Unit tests |

**W1 and W2 both land finished and unreachable.** `docs/PLAN.md` records that
exact shape twice — the rules engine sat complete, tested, and callerless for a
whole phase, and so did `core:secrets`. The mitigation is not a note, it is the
order: W3 follows immediately, before anything else is picked up.

---

## 7. Documents to amend, in the same commits

`AGENTS.md` says the guideline wins when documents disagree, so a code change
that contradicts it is not finished until the guideline moves too. §8's
amendment is the precedent for how: quote the old rule, state the new one, give
the reason, state the cost.

- **§12 — no amendment needed.** Coach stayed a mode inside the builder (§5.1),
  so the section is still an accurate description of the app. `Destination.kt`
  and `RepForthNavHost.kt` are untouched and `NavigationStructureTest` passes
  unchanged.
- **§7 — done.** The table gained `training_week`, `workout_template` gained its
  three columns, and the cascade is stated. §7 still lists `generation_audit` and
  `coach_conversation` as recommended-but-unbuilt; neither arrived with this.
- **§8 — done.** Schema version 3, the week-shaped response, the one-contract
  decision, and the four week-level validation rules including the one most
  likely to be "fixed" into a bug: an exercise may repeat across days.
- **`docs/PLAN.md`** — as each slice lands, per its own contract. It is the file
  that goes stale by doing nothing.
- **Decisions table** in `docs/PLAN.md`, five new rows:

  | Decision | Rationale |
  |---|---|
  | A week contains templates rather than replacing them | Keeps the session engine, history, export and the Wear protocol on one shape |
  | One contract, weeks always; a single workout is a week of one | Two contracts would drift within a phase, and Coach still generates single workouts |
  | Deleting a week deletes its workouts | An orphan named "Day 3 — Pull" is litter; sessions performed from it still survive |
  | Days are ordinal; weekdays are optional | The profile knows how many days, not which; inventing them is a guess presented as a plan |
  | One week is active, by stored flag | Today is believed, and an inferred wrong answer is worse than none |

---

## 8. Tests

### 8.1 Two that will bite immediately

- **`NavigationStructureTest` fails the moment `Destination.Coach` exists.** Its
  title guard reads `Destination.kt` and `RepForthApp.kt` from source and
  requires every non-tab destination to name its own app-bar title, because
  `titleRes` ends in `else -> settings_title`. It has already caught exactly this
  once — the builder shipped titled "Settings". Add the branch. Then **add a
  `coach is reachable but is not a tab` case** beside the existing ones for
  Settings and the builder, so the amended §12 keeps a guard behind it instead of
  losing the one it had.
- **String parity.** Every new string lands in `values/` and `values-tr/` in the
  same change, with plurals for the day counts. If this work creates a new module,
  it needs its own `StringParityContract` subclass on day one — `feature:exercises`
  shipped unguarded Turkish for its entire life because the guard that existed
  was passing.

### 8.2 New coverage

- Week validator: day count, contiguity, weekday uniqueness, per-day ceiling,
  and the cross-day-duplicates-**are**-allowed case, which is the one a future
  reader is most likely to "fix" into a bug.
- Contract v3 round trip, strict unknown-field rejection at both nesting levels.
- Schema ↔ `WorkoutLimits` guard extended to day bounds, watched failing by
  drifting one of them.
- Both adapters against MockWebServer: paths, headers, the exact shared schema,
  a well-formed week, a malformed day, and the repair attempt carrying
  `day_index`.
- `WeekRepository`: transactional save, cascade delete, at-most-one-active.
- Migration v1 → v2 with `MigrationTestHelper` — a new test category for this
  repository.
- `TemplateRepository.observeAll()` filtering week members out.
- Transfer round trip including a week.
- Today's in-week recommendation, including all three fallback branches.

### 8.3 The rule that applies to all of them

**Prove each guard fails.** Break the thing it watches, watch it go red, put it
back. A guard that has never been seen to fail is not known to work, and
`configureGuardTestInputs()` must be extended if any new guard reads a file
Gradle cannot already see — otherwise the task reports UP-TO-DATE and passes on
exactly the change it exists to catch.

---

## 9. Risks

- **Long structured output on small local models** (§4.6). The measured risk, and
  the one most likely to change the design. Test against the real local server,
  not only Gemini.
- **The first Room migration.** `docs/PLAN.md` closes "no destructive migration,
  ever" as a decision; this is where it starts costing something. The migration
  test must exist before the migration ships, and it must not be run on the
  device holding data worth keeping.
- **Regression in the single-workout path.** It stays the common case and it is
  the path that works without a provider. A week of one must not be slower, more
  fragile, or worse-worded than what exists today; W3's device pass covers both.
- **Screenshot tests still do not exist.** Every layout defect in this repository
  so far was found by a person holding a phone — nine of them — and the week card
  in Turkish at 200% font scale is precisely the shape that has failed before.
  W4's verification is manual for that reason, and honest about it.
- **`NavigationStructureTest`'s four-tab assertion.** Coach is not a tab under
  this plan, so the assertion holds. If that ever stops being true, it is a
  product decision and the test is doing its job by blocking it.
