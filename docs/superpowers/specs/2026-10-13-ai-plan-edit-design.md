# Coach edits an existing plan — design

Status: approved in conversation 2026-10-13, awaiting written-spec review.

## What this is for

On a workout or week that already exists, the user tells Coach in plain
language what to change, and Coach proposes a change the user reviews before
it touches the plan. The two driving examples, both from the owner:

- "I don't want to do this exercise, change it to something else that works
  the same muscles."
- "Can you add more chest workouts to this workout routine."

§2 of the guideline already lists "Replace this exercise with something using
body weight" as a Coach request, and §8 *Coach safety* already states the
shape: the model "proposes typed changes that the app validates and the user
confirms". This is the feature those sentences describe.

### Decided

- **Two entry points, one sheet:** a plan-wide "Ask Coach to change this", and
  a Coach button on each exercise card that pins that exercise.
- **Review, then apply.** The proposed changes are listed with Coach's reason;
  Apply or Discard. No per-change checkboxes.
- **The model returns a list of typed changes**, not a rewritten plan.
- **No `move` operation.** Reordering stays manual; the builder already has it.
- **An edit may run a day past the session length** (owner, 2026-10-13, during
  implementation). "Add more chest" on a full day adds chest. The review shows
  each touched day's new length and says when it is over the usual session.

### Assumed

- Builder only. Not available during a running workout.
- Available on every plan the builder holds: saved, freshly generated, or built
  by hand, single workout or week.
- Each edit is one provider call on the user's own key (§8 *Backend decision*).
- Nothing is persisted until the user presses Save.

## Why a change list rather than a rewritten plan

Returning the whole revised plan would reuse the v4 schema unchanged, and was
rejected for three reasons:

1. The model rewrites every day, so it can retune days the user never
   mentioned, and nothing structural stops it.
2. Recovering "what changed" means diffing two exercise lists, which is a
   guess whenever an exercise appears twice or moves.
3. A six-day week is the slow, timeout-prone case generation already suffers
   from; an edit should not cost a whole week's output.

A change list makes untouched exercises untouchable by construction, is small
on the wire, and *is* the review list. The cost is a second response schema.
"Write it once" is kept where it matters: the per-day rules that judge the
result are the existing ones, not a copy.

## Contract (`core:ai`)

### Request

One prompt of headed sections, built like `toGenerationPrompt`:

- **BRIEF** — the user's instruction verbatim, `locale`, goal, experience,
  session minutes. Goal and experience come from the profile; the edit sheet
  has no override controls.
- **CURRENT PLAN** — one row per exercise, giving each a reference
  `day.position` (both 1-based):

  ```
  ref|exercise_id|name|target_muscle|sets|target|weight_kg|rest_seconds
  2.3|0025|barbell bench press|pectorals|4|8 reps|60|90
  ```

  `target` is `<n> reps` or `<n>s`. `weight_kg` is empty when unset. A single
  workout is sent as day 1 of a one-day plan, the convention generation already
  uses on the wire.
- **FOCUS** — `FOCUS: 2.3`, present only when the sheet was opened from an
  exercise card and the pin was not removed.
- **NUMBERS** — the `WorkoutLimits` ranges and the duration formula, the same
  text generation sends (extracted and shared, not copied).
- **CATALOG** — `RulesEngine.filterCandidates` over a `GenerationRequest` with
  no target muscles, so the whole equipment-eligible catalog, in the existing
  delimited table format and order.

### Response — `AiPlanEditResponse`

```json
{
  "changes": [
    { "op": "replace", "day": 2, "position": 3,
      "exercise_id": "0289", "sets": 4, "repetitions": 10,
      "duration_seconds": null, "weight_kg": 22, "rest_seconds": 90 }
  ],
  "rationale": "..."
}
```

- `op` is one of `replace`, `add`, `remove`, `adjust`.
- Every change object has the same flat field set with nullable values, rather
  than one shape per operation: structured output handles nullable fields
  reliably, and alternative shapes are where Gemini's schema support is
  unproven.
- `changes` may be empty — a valid answer meaning "no change suggested".
- At most `PlanEditLimits.maxChanges` = 12 changes.
- Decoded by a strict codec like `AiWorkoutCodec`: unknown fields are
  malformed; weights and durations go through `onTheGrid` rounding at decode.
- The JSON Schema carries shape only, like v4 (Gemini rejects numeric bounds).

### Field rules per operation

| op | `day`,`position` | `exercise_id` | prescription fields |
|---|---|---|---|
| `replace` | the exercise being replaced | required | sets and rest required; exactly one of repetitions/duration; weight optional |
| `add` | insert *after* original position (`0` = start of day) | required | as `replace` |
| `remove` | the exercise to remove | must be null | must all be null |
| `adjust` | the exercise to adjust | must be null | at least one non-null; null means unchanged |

Anything else is a contract violation and gets the repair attempt.

### Applying — `PlanEditApplier` (pure)

Positions always refer to the plan **as sent**. The applier resolves every
change against the original, then rebuilds each day in one pass, so a removal
never shifts what a later change means.

- Two `replace`/`remove`/`adjust` changes on the same position: rejected
  (`CONFLICTING_CHANGES`).
- Several `add`s after the same position keep their response order.
- A `day` outside the plan, or a `position` outside the day: rejected
  (`UNKNOWN_POSITION`).
- An `adjust` that switches between repetitions and duration must match the
  exercise's timed flag (`TARGET_TYPE_MISMATCH`, the existing issue).
- A day the edit leaves empty: rejected (`EMPTY_DAY`). Deleting a day is not an
  edit this feature makes.

Exercises no change touches pass through as the same objects.

### Validating the result

The result is judged on **what the edit introduced**, because the plan being
edited may already break rules the AI must not: a hand-added exercise for
equipment since removed from the profile, or a day already over the session
ceiling, which the builder allows with a warning.

| Check | Applies to |
|---|---|
| Exercise id is in the offered catalog | exercises added or swapped in |
| Sets, reps, duration, rest, weight within `WorkoutLimits` | every changed exercise |
| Target type matches the exercise's timed flag | every changed exercise |
| No exercise twice in one day | every day the edit touched |
| At most `maxExercisesPerDay` | a touched day may not grow past it |

Not applied: day count, the 30% week-fill floor (`WEEK_TOO_SHORT`), rationale
length beyond "present". These judge a generated week, not an edit.

**Nor the session ceiling.** This section originally rejected a change that made
a day longer and left it over the ceiling. The owner reversed that during
implementation: a person asking to add exercises to a full day is asking for a
longer day, and refusing — or steering the model into swapping instead — overrules
them on the one thing they asked. The prompt says a day may run long when asked,
and the review makes the new length visible before Apply.

The per-day checks and the duration estimate come from the existing
`AiWorkoutValidator`/`RulesEngine` code, extracted where they are currently
private, not reimplemented.

### Orchestration — `AiPlanEditor`

Behind an interface, `AiPlanEditService`, so builder tests fake it, and bound in
`AiGenerationModule` beside the generator so `:app`'s `TestGenerationModule`
replaces both.

1. Filter candidates. Empty → `NO_ELIGIBLE_CANDIDATES`.
2. Resolve config for this call; none → `NO_PROVIDER_CONFIGURATION`.
3. `AiProvider.editPlan(config, request, retryFeedback)`, retried on
   `ProviderFailure.SERVER` with the existing backoff (shared, not copied).
4. Apply, then validate. Malformed or invalid → one repair attempt with typed
   issue codes and refs as feedback, never raw provider text.
5. Outcome: `Proposal(edited plan, changes, rationale)` or `Failure` reusing
   `AiGenerationFailureReason`, `ProviderFailure`, `detail`, `deadlineSeconds`.

Deadline: the configured request timeout, not scaled by day count — the answer
does not grow with the week.

`GeminiProvider` and `OpenAiCompatibleProvider` each gain `editPlan` over their
existing request path, with the edit schema from `AiPlanEditSchema`.
`FakeAiProvider` gains a deterministic fixture.

## Builder UI (`feature:builder`)

### Entry points

- **Plan-wide:** an "Ask Coach to change this" action in the builder, visible
  whenever the draft holds at least one exercise.
- **Per exercise:** a Coach icon button on `ExerciseCard` beside move and
  remove, `Target.min` touch target, content description naming the exercise.

Both open the same sheet. Without a configured provider, both stay visible and
the sheet shows Coach's existing "set up a provider" message and Settings link.

### The sheet

A modal bottom sheet with three states:

1. **Ask** — multi-line instruction field. When opened from a card, a removable
   chip "About: ⟨exercise⟩ · ⟨day⟩" and a suggestion chip **"Swap for something
   similar"** that sends a fixed instruction with no typing. Send is enabled
   once there is text or the suggestion is chosen.
2. **Working** — Coach's loading treatment (indeterminate ring, polite live
   region) and Cancel. Back/close while working asks before cancelling.
3. **Review** — Coach's rationale, then one row per change using names and
   thumbnails:
   - replace: `Barbell bench press → Dumbbell floor press`, new numbers, day
   - add: `+ Cable fly`, after which exercise, numbers, day
   - remove: `− Lateral raise`, day
   - adjust: `Squat: 5×5 → 4×6`, day

   Each row is one merged semantics node reading as a sentence. Touched days
   show their new estimated minutes, and say so when that is past the
   profile's session length. Actions: **Apply**, **Discard**. A
   zero-change answer shows the rationale and **Close** only.

Errors use the existing `CoachError` popup. Retry keeps the typed instruction
and the pin.

### Applying to the draft

Apply replaces the draft's exercises (or week days) with the edited copy.
Adjusted exercises keep their `DraftExercise.id`, so the list animates only
what changed. The draft becomes dirty, so Save, the discard prompt and
unsaved-changes behaviour are exactly those of a hand edit. `source` is
unchanged. The edit writes nothing to the database.

### State

One `PlanEditState` in `BuilderUiState`: `Closed`, `Asking(pin, text)`,
`Working(pin, text)`, `Reviewing(proposal)`, `Failed(error, pin, text)`.

The plan sent is snapshotted at send time. If the draft changes by hand while
an edit is working or awaiting review, Apply is replaced by a notice that the
plan changed and an **Ask again** action; a proposal is never applied to a plan
it was not computed from.

### Strings

Every new string in `values/strings.xml` and `values-tr/strings.xml` in the same
change. The suggestion chip's instruction is sent in the active language.

## Testing

**`core:ai`, JVM:**
- `PlanEditApplier`: each op on a workout and a week; positions against the
  original plan including add-after-removed; conflicting changes; unknown day
  and position; empty day; untouched exercises identical.
- Edit validation: catalog check only on introduced exercises; off-equipment
  hand-added exercise survives an unrelated edit; over-ceiling day editable if
  it does not grow, rejected if it does; duplicates; change cap; per-op field
  rules.
- `AiPlanEditor` with `FakeAiProvider`: no config, 5xx retry, repair then
  failure, zero changes valid.
- Both adapters against MockWebServer: path, schema sent, prompt plan table and
  focus line, strict decode, unknown field rejected.
- Guard: edit schema and prompt limits derive from `WorkoutLimits` and
  `PlanEditLimits`; watched failing once.

**`feature:builder`:**
- `BuilderViewModel`: full state cycle, Apply dirties without saving, Discard
  leaves the draft untouched, hand edit voids the proposal, Retry keeps input.
- `PlanEditComposeTest` hosting the real `BuilderScreen` with a faked
  `AiPlanEditService`: tap a card's Coach button → "Swap for something similar"
  → Apply → the card shows the new exercise. This is the test that proves the
  call is reached from the screen.
- `assertScreenIsAccessible` over the sheet in Ask, Working and Review.
- Goldens for Ask and Review, EN and TR, 1.0× and 2.0× font scale; recount and
  update the number in `AGENTS.md`.

**`:app`, managed emulator:** one instrumentation test through the fixture in
`TestGenerationModule`. No key, no network.

**Hardware:** release build on the Galaxy S23 with a real provider: a
swap-for-similar on a workout, and "add more chest" on a week. Recorded in
`docs/PLAN.md` as verified or not.

## Docs

- `PROJECT_GUIDELINE.md` §8: a subsection on the edit contract; §19 Phase 2
  notes that Coach edits as well as generates.
- `docs/PLAN.md`: what was built, where it was verified, what remains
  unverified.

## Out of scope

- Moving exercises, adding or deleting days, renaming the plan or days.
- Multi-turn conversation; each request is independent.
- Editing during a running workout.
- Per-change accept/reject.
