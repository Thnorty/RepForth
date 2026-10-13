# Coach Edits an Existing Plan — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user tell Coach, in plain language, how to change a workout or week already in the builder, review the typed changes it proposes, and apply them to the draft.

**Architecture:** `core:ai` gains a second structured contract: the model returns a list of `replace`/`add`/`remove`/`adjust` changes addressed by `day.position` in the plan as sent. A pure applier builds the edited plan, a validator judges only what the edit introduced, and `AiPlanEditor` orchestrates the provider call, retries and one repair attempt, mirroring `AiWorkoutGenerator`. `feature:builder` adds a bottom sheet (Ask → Working → Review) reached from a plan-wide action and from each exercise card. Apply swaps precomputed drafts into the builder state, which makes the plan dirty without saving it.

**Tech Stack:** Kotlin, kotlinx.serialization, OkHttp + MockWebServer, Hilt, Jetpack Compose + Material 3, Robolectric + Roborazzi, JUnit4.

**Spec:** `docs/superpowers/specs/2026-10-13-ai-plan-edit-design.md`. Read it before any task. This plan decides names and files, and the spec decides behaviour.

## Global Constraints

- Every user-visible string goes into `values/strings.xml` **and** `values-tr/strings.xml` in the same commit. `BuilderStringParityTest` enforces this.
- No `tween(`: use `rfTween`/`rfTravel`. No literal `durationMillis`. Touch targets are `Target.min` (48dp). Icons come through `RfIcons`, never a drawable.
- Numerals use `RepForthNumeric`. Nothing numeric renders below `xs` (20sp).
- A test hosting a composable with `createComposeRule` is named `*ComposeTest`, `*ScreenshotTest` or `*AccessibilityTest`.
- Every screenshot test states its own locale and font scale.
- Numeric limits come from `WorkoutLimits` and `PlanEditLimits`. They are never retyped in a prompt, schema, validator or test.
- JSON Schemas carry shape only: no `minimum`, `maximum`, `minItems`, `maxItems` or `enum`.
- No provider text is ever fed back to the model. Repair feedback is built from this app's own enums and sentences only.
- Run `./gradlew assemblePlaceholderDebug`, `./gradlew test` and `./gradlew lint` as **separate** invocations.
- Commit subjects are imperative and describe intent. **No `Co-Authored-By`, no tool names, no attribution of any kind.**
- Work on branch `ai-plan-edit`. `master` is protected, so the change lands through a pull request.

## Review Focus

1. **The model numbers positions from 0.** `replace` at position 0 must come back as `UNKNOWN_POSITION` and get the repair attempt, never an index exception. *(Task 2)*
2. **An `add` after the last position.** `position == day.size` is a legal append, not out of range. *(Task 2)*
3. **The plan holds an exercise the catalog no longer has.** Building the prompt must not crash. The row uses the id as its name and `unknown` as its muscle. *(Task 4)*
4. **Progression carry under `adjust`.** Changing only sets keeps an exercise's `carriedProgressKg`. Changing its weight drops it, through the existing `loadedWeightKg` rule. *(Task 7)*
5. **Blank or pasted-novel instructions.** A whitespace-only instruction can't be sent. Text longer than `PlanEditLimits.maxInstructionChars` is cut at the field, and the request never carries more. *(Task 7)*

---

### Task 1: The edit contract: types, strict codec, schema, dump guard

**Files:**
- Create: `core/ai/src/main/java/com/repforth/core/ai/AiPlanEditContract.kt`
- Create: `core/ai/src/main/java/com/repforth/core/ai/JsonSchemaShapes.kt`
- Modify: `core/ai/src/main/java/com/repforth/core/ai/AiWorkoutSchema.kt`: move the private `stringSchema`/`integerSchema`/`arraySchema`/`objectSchema`/`nullable` into `JsonSchemaShapes.kt` as `internal` functions, and add `AiPlanEditJsonSchema`
- Modify: `core/ai/src/main/java/com/repforth/core/ai/AiWorkoutContract.kt`: extract the strict `Json` into `internal val strictContractJson`, used by both codecs
- Modify: `core/ai/src/test/java/com/repforth/core/ai/SchemaDumpGuardTest.kt`, `build-logic/convention/src/main/kotlin/GuardTestInputs.kt`
- Create: `tools/gemini-plan-edit-schema.json` (generated)
- Test: `core/ai/src/test/java/com/repforth/core/ai/AiPlanEditContractTest.kt`

**Interfaces:**
- Produces, all in `com.repforth.core.ai`:
  - `object PlanEditLimits { const val maxChanges = 12; const val maxInstructionChars = 500 }`
  - `data class PlanRef(val day: Int, val position: Int)`. Both values are 1-based, as on the wire.
  - `data class EditablePlan(val days: List<EditableDay>)`
  - `data class EditableDay(val title: String, val exercises: List<EditableExercise>)`
  - `data class EditableExercise(val key: String?, val exerciseId: String, val sets: Int, val repetitions: Int?, val durationSeconds: Int?, val weightKg: Double?, val restSeconds: Int)`. `key` is the caller's identity for an exercise and is `null` for one the edit introduced.
  - `@Serializable enum class PlanEditOp { @SerialName("replace") REPLACE, @SerialName("add") ADD, @SerialName("remove") REMOVE, @SerialName("adjust") ADJUST }`
  - `@Serializable data class AiPlanChange(val op: PlanEditOp, val day: Int, val position: Int, @SerialName("exercise_id") val exerciseId: String?, val sets: Int?, val repetitions: Int?, @SerialName("duration_seconds") val durationSeconds: Int?, @SerialName("weight_kg") val weightKg: Double?, @SerialName("rest_seconds") val restSeconds: Int?)`. Every field is required on the wire, including the nullable ones. No defaults.
  - `@Serializable data class AiPlanEditResponse(val changes: List<AiPlanChange>, val rationale: String)`
  - `sealed interface AiPlanEditDecodeResult { data class Ok(val response: AiPlanEditResponse); data object Malformed }`
  - `object AiPlanEditCodec { fun decodeResponse(value: String): AiPlanEditDecodeResult }`. Weights and durations are rounded with `WorkoutLimits.roundWeightKg`/`roundDurationSeconds`, as `onTheGrid` does.
  - `const val AI_PLAN_EDIT_SCHEMA_VERSION = 1`, `const val AI_PLAN_EDIT_SCHEMA_NAME = "repforth_plan_edit_v$AI_PLAN_EDIT_SCHEMA_VERSION"`, `object AiPlanEditJsonSchema { val value: JsonObject }`

- [ ] **Step 1: Write the failing tests** in `AiPlanEditContractTest`:
  - `a complete change list decodes`: one change of each op round-trips.
  - `an unknown op is malformed`: `"op":"move"` gives `Malformed`.
  - `an unknown field is malformed`: an extra `"tempo"` on a change gives `Malformed`.
  - `a missing nullable field is malformed`: omitting `weight_kg` gives `Malformed`.
  - `weights and durations land on the grid`: `weight_kg` 62.5 decodes to `WorkoutLimits.roundWeightKg(62.5)`, and `duration_seconds` 47 to `roundDurationSeconds(47)`.
  - `edit schema is closed, requires every property and carries no bounds`: walk `AiPlanEditJsonSchema.value` and assert every object has `additionalProperties=false` and `required` equal to its property keys, with no `minimum`/`maximum`/`minItems`/`maxItems`/`enum` anywhere.
  - `op is a plain string in the schema`: `properties.changes.items.properties.op == {"type":"string"}`.
  - `nullable fields use a type array`: `exercise_id`, `sets`, `repetitions`, `duration_seconds`, `weight_kg`, `rest_seconds` all have `type == ["<t>","null"]`.

- [ ] **Step 2: Run them and see them fail**

  Run: `./gradlew :core:ai:testDebugUnitTest --tests "com.repforth.core.ai.AiPlanEditContractTest"`
  Expected: compilation failure (types not defined).

- [ ] **Step 3: Implement.** Move the shape helpers to `JsonSchemaShapes.kt` unchanged. `AiWorkoutJsonSchemaTest` must stay green with no edits; it proves the move changed nothing. Write the types above and `AiPlanEditJsonSchema`: root `{changes: array(change), rationale: string}`, where change has `op` string, `day` integer, `position` integer, and the six nullable fields.

- [ ] **Step 4: Extend the dump guard.** Add a second test to `SchemaDumpGuardTest`, `the dumped edit schema matches the schema the app sends`, against `../../tools/gemini-plan-edit-schema.json`, honouring `-Drepforth.regenerate=true` the same way. Add that path to `guardedFiles` in `GuardTestInputs.kt` with a one-line comment. Generate the file with `./gradlew :core:ai:testDebugUnitTest --tests "*SchemaDumpGuardTest*" -Drepforth.regenerate=true` (check the test's comment for the exact property plumbing).

- [ ] **Step 5: Prove the guard fails.** Hand-edit one character in `tools/gemini-plan-edit-schema.json`, run `./gradlew :core:ai:testDebugUnitTest --tests "*SchemaDumpGuardTest*"`, and expect a FAIL that is not UP-TO-DATE. Restore the file and rerun to see it PASS.

- [ ] **Step 6: Run the module's tests**

  Run: `./gradlew :core:ai:testDebugUnitTest`
  Expected: PASS, with `AiWorkoutJsonSchemaTest` and `AiWorkoutContractTest` unchanged and green.

- [ ] **Step 7: Commit:** `git commit -m "Add the plan-edit contract, its strict codec and schema"`

---

### Task 2: `PlanEditApplier`: changes against the plan as sent

**Files:**
- Create: `core/ai/src/main/java/com/repforth/core/ai/PlanEditApplier.kt`
- Modify: `core/ai/src/main/java/com/repforth/core/ai/AiWorkoutValidator.kt`: new `AiWorkoutIssue` values and their `explain()` sentences, plus `position`
- Test: `core/ai/src/test/java/com/repforth/core/ai/PlanEditApplierTest.kt`

**Interfaces:**
- Consumes: Task 1 types.
- Produces:
  - New `AiWorkoutIssue` values: `TOO_MANY_CHANGES`, `UNKNOWN_POSITION`, `CONFLICTING_CHANGES`, `OPERATION_SHAPE`, `DAY_GREW_PAST_CEILING`. Each gets a model-facing sentence in `explain()` that quotes `PlanEditLimits.maxChanges` where relevant.
  - `AiWorkoutContractViolation` and `AiWorkoutRetryIssue` gain `val position: Int? = null`, the wire's 1-based value. `describe()` renders it as `"day D, position P"`.
  - `AiWorkoutRetryFeedback.fromContract(violations: List<AiWorkoutContractViolation>): AiWorkoutRetryFeedback`. The existing `from(result)` delegates to it for its contract half.
  - `data class ExerciseSlot(val dayIndex: Int, val index: Int)`, 0-based positions in the **edited** plan.
  - `data class AppliedEdit(val plan: EditablePlan, val changed: Set<ExerciseSlot>, val touchedDays: Set<Int>)`
  - `sealed interface PlanEditApplication { data class Applied(val edit: AppliedEdit); data class Rejected(val violations: List<AiWorkoutContractViolation>) }`
  - `object PlanEditApplier { fun apply(plan: EditablePlan, changes: List<AiPlanChange>): PlanEditApplication }`

- [ ] **Step 1: Write the failing tests.** The fixture is a two-day plan: day 1 holds A, B, C and day 2 holds D, E, every exercise with a non-null key.
  - `replace swaps the exercise and drops its key`: `replace 1.2 → X` gives day 1 = A, X(key=null), C, and `changed == {ExerciseSlot(0,1)}`.
  - `remove drops the exercise`: `remove 2.1` gives day 2 = E.
  - `adjust changes only the given fields and keeps the key`: `adjust 1.1 sets=5` keeps A's key and other fields and sets `sets=5`.
  - `positions refer to the plan as sent`: `remove 1.1` together with `add after 1.2 → Y` gives A removed and Y directly after B.
  - `add at 0 inserts at the start of the day`
  - `add after the last position appends`: `add after 1.3` puts the new exercise last. **Review focus 2.**
  - `several adds after one position keep response order`
  - `a 0-based position is an unknown position`: `replace 1.0` gives `Rejected` with `UNKNOWN_POSITION`, `dayIndex=0`, `position=0`. **Review focus 1.**
  - `a day outside the plan is an unknown position`: `remove 3.1`.
  - `two changes to one position conflict`: `adjust 1.2` together with `remove 1.2` gives `CONFLICTING_CHANGES`.
  - `remove carrying an exercise id breaks the operation shape`
  - `adjust with nothing to change breaks the operation shape`
  - `replace without sets breaks the operation shape`
  - `adjust giving duration to a repetition exercise is a target type mismatch`
  - `emptying a day is rejected`: removing D and E gives `EMPTY_PLAN` with `dayIndex=1`.
  - `more than maxChanges is rejected before anything is applied`: `PlanEditLimits.maxChanges + 1` changes give exactly one violation, `TOO_MANY_CHANGES`.
  - `untouched exercises are the same instances`: `assertSame` on C after an edit to day 1.

- [ ] **Step 2: Run them and see them fail**

  Run: `./gradlew :core:ai:testDebugUnitTest --tests "com.repforth.core.ai.PlanEditApplierTest"`
  Expected: compilation failure.

- [ ] **Step 3: Implement `PlanEditApplier.apply`.** Validate everything first and return `Rejected` with all violations found. Check order: change count; then per change its shape, its address and target-type conflicts; then duplicate addresses. Only then build each day:

  ```
  emit adds with position == 0, in response order
  for k in 1..day.size:
      emit original exercise k: removed → skip; replaced → new (key = null);
                                 adjusted → copy with the non-null fields; untouched → same instance
      emit adds with position == k, in response order
  ```

  Record a slot for each added, replaced and adjusted exercise, and every day touched. A day that ends up empty gives `EMPTY_PLAN`.

- [ ] **Step 4: Run them and see them pass.** Run `./gradlew :core:ai:testDebugUnitTest` and expect PASS, with `AiWorkoutValidatorTest` unchanged.

- [ ] **Step 5: Commit:** `git commit -m "Apply Coach's plan changes against the plan as sent"`

---

### Task 3: `PlanEditValidator`: judge only what the edit introduced

**Files:**
- Create: `core/ai/src/main/java/com/repforth/core/ai/PlanEditValidator.kt`
- Modify: `core/ai/src/main/java/com/repforth/core/ai/AiWorkoutValidator.kt`: extract the per-exercise number checks into `prescriptionIssues`
- Test: `core/ai/src/test/java/com/repforth/core/ai/PlanEditValidatorTest.kt`

**Interfaces:**
- Consumes: `AppliedEdit`, `EditablePlan`, `AiWorkoutContractViolation` (Task 2).
- Produces:
  - `internal fun prescriptionIssues(sets: Int, repetitions: Int?, durationSeconds: Int?, weightKg: Double?, restSeconds: Int, isTimed: Boolean?): List<AiWorkoutIssue>`. Same checks and same order as the loop body in `AiWorkoutValidator.validate` today, which now calls it.
  - `internal fun EditableDay.estimatedDurationMs(): Long`, built from `PlannedExercise.estimatedDurationMs` in `core:model`, not a new formula.
  - `object PlanEditValidator { fun validate(original: EditablePlan, edit: AppliedEdit, rationale: String, offered: Map<String, ExerciseCandidate>, sessionLengthMs: Long): List<AiWorkoutContractViolation> }`

- [ ] **Step 1: Write the failing tests.** Fixtures are an offered catalog of body-weight candidates and a ceiling of 30 minutes.
  - `an introduced exercise must be offered`: a replace to an id outside `offered` gives `EXERCISE_NOT_OFFERED`.
  - `an off-catalog exercise the edit did not touch survives`: the plan holds a barbell exercise not in `offered`, and an unrelated `adjust` elsewhere gives no violations.
  - `introduced numbers are range-checked`: `sets = WorkoutLimits.sets.last + 1` gives `SETS_OUT_OF_RANGE`.
  - `an introduced timed exercise needs a duration`: a T candidate given repetitions gives `TARGET_TYPE_MISMATCH`.
  - `a duplicate within a touched day is rejected`: adding B to a day that already holds B gives `DUPLICATE_EXERCISE`.
  - `an untouched duplicate does not block an edit`: a hand-built day already has B twice, and an adjust to another exercise in it gives no violation.
  - `a day may not grow past the ceiling`: a day at 25 min grows to 35 and gives `DAY_GREW_PAST_CEILING` with its `dayIndex`.
  - `a day already over the ceiling may be edited if it does not grow`: 40 → 38 gives no violation.
  - `a day already over the ceiling may not grow`: 40 → 42 is rejected.
  - `a day may not grow past maxExercisesPerDay`
  - `a blank rationale is rejected`: `RATIONALE_MISSING`.
  - `no week-level checks apply`: a plan whose total is under `AiPlanFill.MIN_WEEK_FRACTION` of the budget, edited, gives no `WEEK_TOO_SHORT`.

- [ ] **Step 2: Run them and see them fail.** Run `./gradlew :core:ai:testDebugUnitTest --tests "com.repforth.core.ai.PlanEditValidatorTest"` and expect compilation failure.

- [ ] **Step 3: Extract `prescriptionIssues`.** Refactor `AiWorkoutValidator.validate` to call it, then run `./gradlew :core:ai:testDebugUnitTest --tests "com.repforth.core.ai.AiWorkoutValidatorTest"` and expect PASS with the test file unchanged.

- [ ] **Step 4: Implement `PlanEditValidator.validate`.**
  - For each changed slot: an introduced exercise (`key == null`) must be in `offered`, and its numbers are checked with `isTimed = offered[id]?.isTimed`. An adjusted exercise is checked with `isTimed = (durationSeconds != null)`.
  - For each touched day: duplicates are reported only if a changed slot is part of the duplicate. Exercise count and duration violate only when the day both grew and ended over the limit.

- [ ] **Step 5: Run them and see them pass.** Run `./gradlew :core:ai:testDebugUnitTest` and expect PASS.

- [ ] **Step 6: Commit:** `git commit -m "Validate a plan edit on what it introduced, not on the plan it found"`

---

### Task 4: The edit request and its prompt

**Files:**
- Modify: `core/ai/src/main/java/com/repforth/core/ai/AiPlanEditContract.kt`: add the request types
- Modify: `core/ai/src/main/java/com/repforth/core/ai/AiWorkoutSchema.kt`: extract the shared prompt sections and add `toPlanEditPrompt`
- Test: `core/ai/src/test/java/com/repforth/core/ai/AiPlanEditPromptTest.kt`

**Interfaces:**
- Consumes: Task 1 types, `AiExerciseCandidate`, `RulesEngine.filterCandidates`.
- Produces:
  - `data class PlanEditRequest(val profile: UserProfile, val plan: EditablePlan, val instruction: String, val focus: PlanRef?)`
  - `data class AiPlanRow(val ref: PlanRef, val exerciseId: String, val name: String, val target: String, val sets: Int, val repetitions: Int?, val durationSeconds: Int?, val weightKg: Double?, val restSeconds: Int) { fun toRow(): String }`. The row format is `ref|exercise_id|name|target_muscle|sets|target|weight_kg|rest_seconds`, with `ref` written `d.p`, `target` written `"<n> reps"` or `"<n>s"`, and an empty `weight_kg` when it is null.
  - `data class AiPlanEditRequest(val locale: String, val goal: String, val experience: String, val sessionDurationMinutes: Int, val instruction: String, val focus: PlanRef?, val dayTitles: List<String>, val rows: List<AiPlanRow>, val candidates: List<AiExerciseCandidate>) { companion object { fun from(request: PlanEditRequest, locale: Language, catalog: List<ExerciseCandidate>, eligible: List<ExerciseCandidate>): AiPlanEditRequest } }`. `instruction` is `take(PlanEditLimits.maxInstructionChars)`. Names and targets are looked up by id in `catalog`; a missing id gives `name = id` and `target = "unknown"`. Candidates are sorted as `AiWorkoutRequest.from` sorts them.
  - `internal fun AiPlanEditRequest.toPlanEditPrompt(retryFeedback: AiWorkoutRetryFeedback?): String`
  - `internal fun StringBuilder.appendDurationFormula()` and `internal fun StringBuilder.appendNumberLimits()`, extracted from `toGenerationPrompt`, which now calls them.

- [ ] **Step 1: Write the failing tests.**
  - `the instruction is sent verbatim, inside the brief`
  - `every plan exercise is one addressed row`: for a two-day plan, assert the row `2.1|0025|barbell bench press|pectorals|4|8 reps|60|90`.
  - `a timed row says seconds and an empty weight`: `…|3|30s||45`.
  - `the focus line appears only when there is a focus`: `FOCUS: 2.1` present, then absent with `focus = null`.
  - `day titles are listed so a day can be named`: `DAY 1: Push`.
  - `an exercise the catalog no longer has still makes a row`: an id missing from `catalog` gives `|<id>|unknown|`, with no exception. **Review focus 3.**
  - `the prompt states the limits from WorkoutLimits and PlanEditLimits`: assert the `sets a-b`, `rest_seconds a-b` and `at most ${PlanEditLimits.maxChanges} changes` substrings, built from the constants.
  - `positions are explained as the plan as sent`: the prompt contains `add inserts after` and `0 means`.
  - `the instruction is capped`: a 2,000-character instruction gives `request.instruction.length == PlanEditLimits.maxInstructionChars`.
  - `retry feedback is listed, and no provider text is`: feedback built with `fromContract` appears as `- day 1, position 0: …`.

- [ ] **Step 2: Run them and see them fail.** Run `./gradlew :core:ai:testDebugUnitTest --tests "com.repforth.core.ai.AiPlanEditPromptTest"` and expect compilation failure.

- [ ] **Step 3: Extract and implement.**
  - Extract `appendDurationFormula`/`appendNumberLimits` first, then run `./gradlew :core:ai:testDebugUnitTest --tests "*AiWorkoutJsonSchemaTest*" --tests "*AiWorkoutContractTest*"` and expect PASS. The generation prompt must be byte-identical, so before extracting, add a temporary assertion comparing old and new output on `sampleRequest()`, then delete it.
  - Write `toPlanEditPrompt` with sections in this order: an opening line saying the job is "change this person's existing plan as they ask, and nothing else"; `BRIEF`; `DAYS`; `CURRENT PLAN` (header plus rows); `FOCUS` (optional); `HOW TO ANSWER`; the shared time and number sections; the retry feedback, headed `YOUR PREVIOUS ANSWER WAS REJECTED. Fix exactly these, then return the whole list of changes again:`; then `CATALOG`.
  - `HOW TO ANSWER` gives the op table from the spec in prose: positions refer to the plan as sent, `add` inserts after the position with 0 meaning the start, return no change for anything the person did not ask about, and `changes` may be empty, in which case `rationale` says why.

- [ ] **Step 4: Run them and see them pass.** Run `./gradlew :core:ai:testDebugUnitTest` and expect PASS.

- [ ] **Step 5: Commit:** `git commit -m "Describe the plan and the person's request to the model"`

---

### Task 5: Providers carry the edit

**Files:**
- Modify: `core/ai/src/main/java/com/repforth/core/ai/AiProvider.kt`, `GeminiProvider.kt`, `OpenAiCompatibleProvider.kt`, `FakeAiProvider.kt`
- Test: `core/ai/src/test/java/com/repforth/core/ai/ProviderPlanEditTest.kt` (MockWebServer; copy the setup of `ProviderGenerationTest`)

**Interfaces:**
- Consumes: `AiPlanEditRequest.toPlanEditPrompt`, `AiPlanEditJsonSchema`, `AiPlanEditCodec`.
- Produces:
  - `sealed interface ProviderPlanEditResult { data class Ok(val response: AiPlanEditResponse); data class Failed(val failure: ProviderFailure, val detail: String? = null) }`
  - `AiProvider.editPlan(config: ProviderConfig, request: AiPlanEditRequest, retryFeedback: AiWorkoutRetryFeedback? = null): ProviderPlanEditResult`
  - `FakeAiProvider` gains `var nextEdit: ProviderPlanEditResult` (defaulting to `Failed(FORMAT, "No edit fixture configured")`), `val editCalls: MutableList<Pair<ProviderConfig, AiPlanEditRequest>>` and `val editRetryFeedback: MutableList<AiWorkoutRetryFeedback?>`.

- [ ] **Step 1: Write the failing tests.**
  - `gemini sends the edit schema and prompt through its native envelope`: assert the path `models/<model>:generateContent`, that `generationConfig.responseJsonSchema == AiPlanEditJsonSchema.value`, and that the prompt text contains `CURRENT PLAN`.
  - `openai compatible edit uses strict structured output under the edit schema name`: `response_format.json_schema.name == AI_PLAN_EDIT_SCHEMA_NAME`, with `strict == true`.
  - `the edit uses the configured timeout, not one scaled by days`: for a seven-day plan and a 30s setting, the deadline is 30s. Assert through the same mechanism `GenerationDeadlineReachesSocketTest` uses.
  - `a strict edit answer decodes`, `an unknown field in the edit answer is a format failure`, and `an openai refusal is a format failure without its text`
  - `edit status failures keep the actionable categories`: 401 gives `AUTHENTICATION`, 429 `QUOTA`, 503 `SERVER`, and Gemini 400 with `API_KEY_INVALID` gives `AUTHENTICATION`.

- [ ] **Step 2: Run them and see them fail.** Run `./gradlew :core:ai:testDebugUnitTest --tests "com.repforth.core.ai.ProviderPlanEditTest"` and expect compilation failure.

- [ ] **Step 3: Refactor each adapter before adding to it.** Split `generateWorkout` into a private `structuredCall(config, prompt: String, schemaName: String, schema: JsonObject, timeoutSeconds: Int): StructuredReply` that returns the model's JSON text, and the codec step after it. `internal sealed interface StructuredReply { data class Text(val value: String); data class Failed(val failure: ProviderFailure, val detail: String?) }` goes in `AiProvider.kt`. Detail strings say "structured answer" instead of "workout".

  Run `./gradlew :core:ai:testDebugUnitTest --tests "*ProviderGenerationTest*" --tests "*GenerationDeadlineReachesSocketTest*" --tests "*ProviderAdapterTest*"` and expect PASS before going further.

- [ ] **Step 4: Implement `editPlan` in both adapters and in the fake.** Each adapter's `editPlan` is `structuredCall` with the edit prompt, schema and name, and `timeoutSeconds = config.settings.requestTimeoutSeconds`, followed by `AiPlanEditCodec`.

- [ ] **Step 5: Run the module's tests.** Run `./gradlew :core:ai:testDebugUnitTest` and expect PASS.

- [ ] **Step 6: Commit:** `git commit -m "Send plan edits through both provider adapters"`

---

### Task 6: `AiPlanEditor`: orchestration and its seam

**Files:**
- Create: `core/ai/src/main/java/com/repforth/core/ai/AiPlanEditor.kt`
- Create: `core/ai/src/main/java/com/repforth/core/ai/TransientRetry.kt`
- Modify: `core/ai/src/main/java/com/repforth/core/ai/AiWorkoutGenerator.kt`: `callProvider` uses `retryingServerErrors`
- Modify: `core/ai/src/main/java/com/repforth/core/ai/di/AiGenerationModule.kt`
- Modify: `app/src/androidTest/java/com/repforth/app/TestGenerationModule.kt`: add the edit binding (fixture in Task 9; here a stub that returns `Failure` so the module still compiles)
- Test: `core/ai/src/test/java/com/repforth/core/ai/AiPlanEditorTest.kt`

**Interfaces:**
- Consumes: Tasks 1–5.
- Produces:
  - `internal suspend fun <T> retryingServerErrors(isServerError: (T) -> Boolean, call: suspend () -> T): T`. Three attempts in total, with backoff of 1 000ms doubling; the constants move here from the generator.
  - `sealed interface AiPlanEditOutcome { data class Proposal(val edited: EditablePlan, val changes: List<AiPlanChange>, val rationale: String, val attempts: Int); data class Failure(val failure: AiWorkoutGenerationOutcome.Failure) }`. The generation failure is wrapped so the builder's `toCoachError` is reused unchanged.
  - `interface AiPlanEditService { suspend fun edit(request: PlanEditRequest, locale: Language, candidates: List<ExerciseCandidate>): AiPlanEditOutcome }`
  - `@Singleton class AiPlanEditor @Inject constructor(repository: ProviderRepository, providers: Map<ProviderId, @JvmSuppressWildcards AiProvider>) : AiPlanEditService`
  - `AiGenerationModule.providePlanEditService(editor: AiPlanEditor): AiPlanEditService`

- [ ] **Step 1: Write the failing tests** (`FakeAiProvider` plus the same repository setup `AiWorkoutGeneratorTest` uses).
  - `no configuration fails before any call`: `NO_PROVIDER_CONFIGURATION`, `attempts=0`, no `editCalls`.
  - `no eligible candidates fails before any call`
  - `a valid proposal is returned with the applied plan`: `Proposal.edited` equals what `PlanEditApplier` produces for the same changes.
  - `zero changes is a proposal, not a failure`
  - `a server error is retried without spending the repair attempt`: 503 then OK gives `Proposal`, `attempts=1`, and `editRetryFeedback == [null, null]`.
  - `an invalid answer gets one repair attempt with typed feedback`: the first answer replaces at position 0, the second is valid, giving `attempts=2`. The second feedback contains a `CONTRACT`/`unknown_position` issue.
  - `still invalid after repair is INVALID_RESPONSE`
  - `malformed output gets the repair attempt`: `FORMAT` then OK gives `Proposal`.
  - `the candidates offered are the equipment-filtered catalog`: assert `editCalls.single().second.candidates` excludes a barbell candidate for a body-weight-only profile.
  - `a timeout reports the configured deadline`: `deadlineSeconds == settings.requestTimeoutSeconds`.

- [ ] **Step 2: Run them and see them fail.** Run `./gradlew :core:ai:testDebugUnitTest --tests "com.repforth.core.ai.AiPlanEditorTest"` and expect compilation failure.

- [ ] **Step 3: Implement.** Extract `retryingServerErrors`, then run `./gradlew :core:ai:testDebugUnitTest --tests "*AiWorkoutGeneratorTest*"` and expect PASS. Write `AiPlanEditor.edit`:
  1. Filter with `RulesEngine.filterCandidates(GenerationRequest(profile = request.profile), candidates)`.
  2. Resolve the config.
  3. Call the provider.
  4. On `Ok`, run `PlanEditApplier.apply`, then `PlanEditValidator.validate` with `offered = eligible.associateBy { it.id.value }`. (No session length: see the 2026-10-13 ruling in the ledger; edits may run long.)
  5. On failure, make one repair attempt with `AiWorkoutRetryFeedback.fromContract`, or `Malformed` for `FORMAT`.

  Bind it in `AiGenerationModule`, and add a stub binding to `TestGenerationModule`.

- [ ] **Step 4: Run the module's tests and check the app's instrumentation still compiles.**

  Run: `./gradlew :core:ai:testDebugUnitTest`, then separately `./gradlew :app:compilePlaceholderDebugAndroidTestKotlin`
  Expected: PASS, then BUILD SUCCESSFUL.

- [ ] **Step 5: Commit:** `git commit -m "Orchestrate a plan edit: one call, one repair, typed outcome"`

---

### Task 7: Builder state: ask, work, review, apply

**Files:**
- Create: `feature/builder/src/main/java/com/repforth/feature/builder/PlanEditState.kt`
- Create: `feature/builder/src/main/java/com/repforth/feature/builder/PlanEditMapping.kt`
- Modify: `feature/builder/src/main/java/com/repforth/feature/builder/BuilderViewModel.kt`: constructor, `BuilderUiState.planEdit`, handlers. `toAiDrafts` and `toCoachError` become `internal` so the mapping file can reuse them.
- Modify: `feature/builder/src/test/java/com/repforth/feature/builder/BuilderFixtures.kt`: move `FakeExercises`, `FakeWorkoutGenerator` and `AlwaysConfigured` out of `BuilderViewModelTest.kt` as `internal`, and add `FakePlanEditService`
- Modify: `feature/builder/src/test/java/com/repforth/feature/builder/BuilderViewModelTest.kt`: both construction sites
- Test: `feature/builder/src/test/java/com/repforth/feature/builder/PlanEditMappingTest.kt`, `PlanEditViewModelTest.kt`

**Interfaces:**
- Consumes: `AiPlanEditService`, `AiPlanEditOutcome`, `EditablePlan`, `PlanRef`, `AiPlanChange`, `PlanEditLimits` (Tasks 1–6).
- Produces:
  - `data class Prescription(val sets: Int, val reps: Int?, val durationSeconds: Int?, val weightKg: Double?, val restSeconds: Int)`
  - `sealed interface PlanEditRow { val dayNumber: Int? }`, where `dayNumber` is 1-based and `null` for a single workout. Variants:
    - `Replaced(dayNumber, from: String, to: String, thumbnail: MediaRef, after: Prescription)`
    - `Added(dayNumber, name: String, thumbnail: MediaRef, afterName: String?, prescription: Prescription)`
    - `Removed(dayNumber, name: String, thumbnail: MediaRef)`
    - `Adjusted(dayNumber, name: String, thumbnail: MediaRef, before: Prescription, after: Prescription)`
  - `data class PinnedExercise(val ref: PlanRef, val name: String, val dayNumber: Int?)`
  - `sealed interface PlanEditState`, with `pin` on every variant except `Closed`:
    - `data object Closed`
    - `data class Asking(val pin: PinnedExercise?, val text: String)`
    - `data class Working(val pin: PinnedExercise?, val instruction: String, val basis: BuilderContent)`
    - `data class Reviewing(val pin: PinnedExercise?, val instruction: String, val basis: BuilderContent, val rationale: String, val rows: List<PlanEditRow>, val dayMinutes: Map<Int, Int>, val proposedExercises: List<DraftExercise>, val proposedWeekDays: List<DraftWeekDay>)`
    - `data class Failed(val pin: PinnedExercise?, val instruction: String, val error: CoachError)`
  - `BuilderUiState.planEdit: PlanEditState = PlanEditState.Closed`; `val BuilderUiState.planEditIsStale: Boolean`, which is true when `Reviewing`/`Working` and `editableContent != basis`.
  - In `PlanEditMapping.kt`:
    - `internal fun BuilderUiState.toEditablePlan(): EditablePlan`. A single workout becomes one day titled `name`. The key is `DraftExercise.id`. `timed` gives `durationSeconds`, otherwise `repetitions`.
    - `internal fun BuilderUiState.proposalDrafts(edited: EditablePlan, summaries: Map<ExerciseId, ExerciseSummary>): Pair<List<DraftExercise>, List<DraftWeekDay>>`. A keyed exercise keeps its original `DraftExercise` (id, name, thumbnail, timed, both target halves, `progressKg`, `loadedWeightKg`) with the edited numbers. A null key goes through `toAiDrafts`. Week days keep `templateId`, `title`, `dayOfWeek`, `notes`, `focusMuscles` and `isExpanded`.
    - `internal fun reviewRows(original: EditablePlan, changes: List<AiPlanChange>, originalNames: Map<String, Pair<String, MediaRef>>, summaries: Map<ExerciseId, ExerciseSummary>, isWeek: Boolean): List<PlanEditRow>`
  - `BuilderViewModel` gains the constructor parameter `planEditor: AiPlanEditService`, placed after `generator`, and these handlers:
    - `onPlanEditOpen(pin: PlanRef?)`, `onPlanEditTextChange(text: String)`, `onPlanEditUnpin()`
    - `onPlanEditSend(instruction: String, locale: Language)`, `onPlanEditRetry(locale: Language)`, `onPlanEditCancel()`
    - `onPlanEditApply()`, `onPlanEditDismiss()`

- [ ] **Step 1: Write the failing mapping tests** in `PlanEditMappingTest`.
  - `a single workout is sent as one day titled with the plan name`
  - `each exercise is keyed by its draft id and sends only its active target`
  - `an adjusted exercise keeps its draft identity and its other target half`
  - `adjusting only sets keeps the progression carry`: `progressKg=2.5, loadedWeightKg=40, weightKg=40` with sets changed gives `carriedProgressKg == 2.5`. **Review focus 4.**
  - `adjusting the weight drops the carry`: weight 40 → 45 gives `carriedProgressKg == 0.0`. **Review focus 4.**
  - `a replaced exercise is a new draft with the catalog's name and thumbnail`
  - `week days keep their template id, weekday and notes`
  - `review rows read the plan as sent`: one row of each kind, with names, `afterName` for an add, and `dayNumber` null for a single workout and 1-based for a week.

- [ ] **Step 2: Write the failing view-model tests** in `PlanEditViewModelTest`, using `FakePlanEditService` (records requests; returns a configurable outcome).
  - `opening from a card pins that exercise`: `onPlanEditOpen(PlanRef(2,1))` on a week gives `Asking(pin.name == <day 2's first exercise>, dayNumber == 2)`.
  - `a blank instruction is not sent`: `onPlanEditSend("   ", EN)` makes no request and stays `Asking`. **Review focus 5.**
  - `typed text is capped at maxInstructionChars`: `onPlanEditTextChange("x".repeat(2000))` gives `Asking.text.length == PlanEditLimits.maxInstructionChars`. **Review focus 5.**
  - `send carries the plan, the pin and the locale`: the request's `focus == PlanRef(2,1)` and `plan` equals `toEditablePlan()`.
  - `a proposal is reviewed, not applied`: after the outcome the state is `Reviewing`, `state.exercises` is unchanged, and `isDirty == false`.
  - `apply puts the proposal in the draft and makes it dirty without saving`: after `onPlanEditApply()` the exercises equal `proposedExercises`, `isDirty`, `planEdit == Closed` and `templates.saved` is empty.
  - `discard leaves the draft exactly as it was`
  - `a hand edit while reviewing makes the proposal stale and apply does nothing`: `onSetsChange` during `Reviewing` gives `planEditIsStale`, and `onPlanEditApply()` leaves the exercises untouched.
  - `a failure keeps the instruction and pin, and retry resends them`
  - `cancel while working stops the job and returns to asking with the text kept`
  - `zero changes reviews with no rows`

- [ ] **Step 3: Run them and see them fail.** Run `./gradlew :feature:builder:testDebugUnitTest --tests "*PlanEdit*"` and expect compilation failure.

- [ ] **Step 4: Implement.** Move the fakes to `BuilderFixtures`, add the constructor parameter, and update both construction sites in `BuilderViewModelTest`. Then:
  - `onPlanEditSend` trims, ignores blank, snapshots `basis = editableContent`, launches `planEditor.edit(...)` on its own `Job` with `profiles.getProfile()` and `exercises.candidates()`, and on `Proposal` fetches `exercises.summaries(introduced ids)` and builds `Reviewing` from the mapping functions.
  - `Failure` is turned into a `CoachError` with the existing `toCoachError()` and becomes `Failed`.
  - A missing profile gives `Failed(error = CoachError(titleRes = R.string.coach_error_failed_title, messageRes = R.string.coach_failed_no_profile, canRetry = false))`. Both strings already exist.

- [ ] **Step 5: Run the module's tests.** Run `./gradlew :feature:builder:testDebugUnitTest` and expect PASS, with every existing `BuilderViewModelTest` case green.

- [ ] **Step 6: Commit:** `git commit -m "Hold a Coach plan edit as a reviewable proposal in the builder"`

---

### Task 8: The sheet, the entry points, and proof the screen reaches them

**Files:**
- Create: `feature/builder/src/main/java/com/repforth/feature/builder/PlanEditSheet.kt`
- Modify: `feature/builder/src/main/java/com/repforth/feature/builder/BuilderScreen.kt`: `ExerciseCard` gains `onAskCoach: (() -> Unit)? = null`; `BuilderScreen` gains `onAskCoach: (PlanRef?) -> Unit` and the plan-wide button; `BuilderRoute` renders `PlanEditSheet` beside `ExerciseDetailSheet`
- Modify: `feature/builder/src/main/java/com/repforth/feature/builder/WeekReviewScreen.kt`: the same two entry points
- Modify: `feature/builder/src/main/java/com/repforth/feature/builder/CoachScreen.kt`: make `ProviderResponseBlock`, `CoachSetupNotice` and the error `AlertDialog` body reusable as `internal` (`CoachErrorDialog(error, onDismiss, onRetry)`), and use them from both places
- Modify: `feature/builder/src/main/res/values/strings.xml`, `values-tr/strings.xml`
- Test: `feature/builder/src/test/java/com/repforth/feature/builder/PlanEditComposeTest.kt`, plus additions to `BuilderAccessibilityTest.kt` and `BuilderScreenshotTest.kt`

**Interfaces:**
- Consumes: `PlanEditState`, `PlanEditRow`, `Prescription`, the Task 7 handlers, `BuilderUiState.planEditIsStale`, `providerConfigured`.
- Produces: `@Composable internal fun PlanEditSheet(state: BuilderUiState, onTextChange: (String) -> Unit, onUnpin: () -> Unit, onSend: (String) -> Unit, onCancel: () -> Unit, onApply: () -> Unit, onDismiss: () -> Unit, onRetry: () -> Unit, onOpenProviderSettings: () -> Unit)`

**Strings** (key, then the English text). Write the Turkish yourself as Turkish, not as a word-for-word translation, and keep the same keys:

| Key | English |
|---|---|
| `plan_edit_open` | Ask Coach to change this |
| `plan_edit_open_exercise` | Ask Coach about %1$s |
| `plan_edit_title` | Change this plan |
| `plan_edit_hint` | What should change? |
| `plan_edit_about` | About: %1$s |
| `plan_edit_about_day` | About: %1$s · Day %2$d |
| `plan_edit_unpin` | Remove %1$s from the request |
| `plan_edit_swap_similar` | Swap for something similar |
| `plan_edit_swap_similar_instruction` | Replace this exercise with a different one that works the same muscles. |
| `plan_edit_send` | Ask Coach |
| `plan_edit_working_title` | Coach is reworking your plan |
| `plan_edit_working_body` | This usually takes a few seconds. |
| `plan_edit_cancel` | Cancel |
| `plan_edit_cancel_confirm_title` | Stop this request? |
| `plan_edit_review_title` | Coach suggests |
| `plan_edit_no_changes` | Coach suggests no changes. |
| `plan_edit_apply` | Apply |
| `plan_edit_discard` | Discard |
| `plan_edit_close` | Close |
| `plan_edit_stale` | The plan changed while Coach was working, so these suggestions no longer fit it. |
| `plan_edit_ask_again` | Ask again |
| `plan_edit_day_minutes` | Day %1$d: about %2$d min |
| `plan_edit_day_minutes_over` | Day %1$d: about %2$d min, past your usual %3$d |
| `plan_edit_row_replaced` | %1$s → %2$s |
| `plan_edit_row_added` | Add %1$s |
| `plan_edit_row_added_after` | Add %1$s after %2$s |
| `plan_edit_row_removed` | Remove %1$s |
| `plan_edit_row_adjusted` | Change %1$s |

- [ ] **Step 1: Write the failing compose tests** in `PlanEditComposeTest`. Host `BuilderRoute(onSaved = {}, viewModel = BuilderViewModel(... FakePlanEditService ...))`, a real view model on the Task 7 fakes, in `RepForthPreviewHost`, with the locale and font scale stated.
  - `a card's Coach button, the swap chip and Apply change that card`: load a plan holding exercise A; tap `Ask Coach about A`, then `Swap for something similar`, then `Apply`. Assert the text of B is displayed, A's is not, and the request's `focus == PlanRef(1,1)` and `instruction` equals the English `plan_edit_swap_similar_instruction`. **This is the test that proves the screen reaches the call.**
  - `the plan-wide action sends without a pin`: tap `Ask Coach to change this`, type `add more chest`, and tap `Ask Coach`. The request's `focus == null`.
  - `discard leaves the cards as they were`
  - `without a provider the sheet offers settings and no send`: `AlwaysConfigured` is replaced by a not-configured fake, and the setup notice is displayed while `Ask Coach` is not enabled.
  - `a week card pins its own day`: tap the button on day 2's first card and assert `focus == PlanRef(2,1)`.

- [ ] **Step 2: Run them and see them fail.** Run `./gradlew :feature:builder:testDebugUnitTest --tests "*PlanEditComposeTest*"` and expect FAIL or compilation failure.

- [ ] **Step 3: Implement the sheet and the entry points.**
  - The sheet is a `ModalBottomSheet`, with the content `when (state.planEdit)` following the spec's UI section.
    - The text field uses `maxLines` but no fixed height, and the swap chip has `heightIn(min = Target.min)`.
    - The Working state reuses Coach's loading treatment, made `internal` if it isn't already.
    - Each review row is `Modifier.semantics(mergeDescendants = true)` and reads as one sentence.
  - Back and close while `Working` ask `plan_edit_cancel_confirm_title` first.
  - The card button uses `RfIcons.Generate` and a `contentDescription` of `plan_edit_open_exercise`.
  - The plan-wide button is shown when the draft has at least one exercise.
  - The screen resolves `plan_edit_swap_similar_instruction` and passes it to `onSend`. The view model never reads resources.

- [ ] **Step 4: Run them and see them pass.** Run `./gradlew :feature:builder:testDebugUnitTest --tests "*PlanEditComposeTest*"` and expect PASS.

- [ ] **Step 5: Prove the reach test fails when the path is cut.** Temporarily make the card's Coach button call `{}`, run the test and expect the first test to FAIL. Restore it and run it to PASS.

- [ ] **Step 6: Accessibility and goldens.**
  - Add `assertScreenIsAccessible` cases for the sheet in Asking (pinned), Working and Reviewing (one row of each kind) to `BuilderAccessibilityTest`.
  - Add `BuilderScreenshotTest` cases for Asking (pinned) and Reviewing in `en` and `tr` at font scale 1.0 and 2.0, eight in all.
  - Record with `./gradlew :feature:builder:testDebugUnitTest -Proborazzi.record=true`, open each new PNG, and check: no truncated Turkish, nothing clipped at 2.0×, numerals in `RepForthNumeric`.

- [ ] **Step 7: Run the module's tests.** Run `./gradlew :feature:builder:testDebugUnitTest` and expect PASS, including `BuilderStringParityTest`.

- [ ] **Step 8: Commit:** `git commit -m "Let the builder ask Coach to change a plan, from the plan or from one exercise"`

---

### Task 9: On a device: the edit through the fixture

**Files:**
- Modify: `app/src/androidTest/java/com/repforth/app/TestGenerationModule.kt`: replace the Task 6 stub with `FixturePlanEditService`
- Modify: `app/src/androidTest/java/com/repforth/app/BuilderFlowTest.kt`

**Interfaces:**
- Consumes: `AiPlanEditService`, `AiPlanEditOutcome.Proposal`, `PlanEditApplier`.
- Produces: `TestGenerationModule.SWAP_EXERCISE_ID`, a real id from the pinned catalog chosen by reading `core/exercise-data`'s bundled dataset. Its English name is what the test asserts.

- [ ] **Step 1: Write the failing test** `coachEditSwapsAnExerciseOnlyAfterApply`.
  1. Generate the fixture workout, as `coachFillsTheBuilderButSavesNothingUntilAsked` does.
  2. Tap the first card's Coach button, then the swap chip.
  3. Wait for `Apply`, then tap it.
  4. Wait until the swap exercise's name is displayed and the first exercise's name is gone.
  5. Leave without saving and assert Plans holds nothing new.

  Wait with `waitUntil`, never a single look.

- [ ] **Step 2: Implement `FixturePlanEditService`.** It answers `replace` at the focus with `SWAP_EXERCISE_ID`, applied with `PlanEditApplier` so the fixture can't disagree with production about positions.

- [ ] **Step 3: Run it on the managed emulator.**

  Check free memory first: `powershell "(Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory/1MB"`, and run `./gradlew --stop` if it is under ~4GB.
  Run: `./gradlew :app:pixel6Api34PlaceholderDebugAndroidTest`
  Expected: all tests pass, now ten. If a run reports `tests="0"`, it is an emulator attach failure, not a result. Kill `qemu-system-x86_64-headless` and rerun. Run the new test **three times**: a single green run is not evidence on this emulator.

- [ ] **Step 4: Commit:** `git commit -m "Prove a Coach edit reaches the builder on a device"`

---

### Task 10: Docs, whole-repo verification, pull request

**Files:**
- Modify: `PROJECT_GUIDELINE.md`: §8 gets a subsection "Editing an existing plan" summarising the contract (ops, positions as sent, judged on what the edit introduced, no week-level checks), and the §19 Phase 2 bullets note that Coach edits as well as generates.
- Modify: `docs/PLAN.md`: a section under Phase 2 recording what was built, the test counts, the emulator runs, and **"a live provider has not yet answered the edit contract"** until the hardware step says otherwise.
- Modify: `AGENTS.md`: the golden count in the Git section. Count with `git ls-files "*src/test/screenshots/*.png" | wc -l`; it was 91 before this branch, not the 76 the file says.

- [ ] **Step 1: Update the three documents** as listed.

- [ ] **Step 2: Verify the whole repo**, running each command on its own:

  ```
  ./gradlew assemblePlaceholderDebug
  ./gradlew test
  ./gradlew lint
  ```

  Expected: BUILD SUCCESSFUL for each. If `test` fails only in `testReleaseUnitTest`, a new composable test is misnamed.

- [ ] **Step 3: Commit, push, open the PR and watch CI.**

  ```
  git commit -m "Record Coach plan edits in the guideline and the plan"
  git push -u origin ai-plan-edit
  gh pr create --fill
  gh pr checks --watch
  ```

  Expected: `Validate Gradle wrapper` and `Build and test` both pass. Do not merge until the owner has seen the hardware result.

- [ ] **Step 4: On hardware, with the owner.**
  - Build `./gradlew :app:assemblePlaceholderRelease` and check the output is `app-placeholder-release.apk` (signed), not `-unsigned`.
  - Install on the Galaxy S23: `adb install -r --user 0 …`. **Install before starting any workout**, since reinstalling ends it.
  - With the owner's configured provider:
    - (a) on a single workout, use a card's swap chip and Apply;
    - (b) on a week, the plan-wide "add more chest" and Apply.
  - After each, read `adb logcat -b crash -d` after waiting at least 30 seconds, and check `adb shell pidof com.repforth`.
  - If Gemini returns `400 INVALID_ARGUMENT`, run the probe scripts against `tools/gemini-plan-edit-schema.json` before changing anything.
  - Record the outcome in `docs/PLAN.md`, commit, and push to the PR.

- [ ] **Step 5: Merge** once the owner approves: `gh pr merge --squash --delete-branch`.
