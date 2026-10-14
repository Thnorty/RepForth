# Body tracking — design

Status: approved in conversation 2026-10-13, awaiting written-spec review.

## What this is for

The owner asked for "a feature and screens to let users track themselves —
photos, waist sizes, arm sizes etc." Today the Progress tab records only
workouts: history, streaks, volume and muscle activity. Nothing records the
body, and the profile holds no body weight.

This adds dated **check-ins**, each holding any measurements and up to three
posed photos, and shows them as latest values, trend charts and a side-by-side
photo compare.

### Decided

Each of these was the owner's answer in conversation.

- **What can be measured:** body weight, body fat %, a fixed set of tape
  measurements, and custom measurements the user names.
- **Photos:** posed sets (front, side, back) per check-in, taken with the
  camera or picked from the gallery, with a compare screen.
- **Export:** measurements are exported; photos are not, and Settings says so.
- **Placement:** a Workouts | Body switch at the top of the Progress tab.
- **Approach A:** the system camera app and photo picker, no camera
  permission, no new dependency; charts drawn by hand on `Canvas`; new
  `core:body` and `feature:body` modules. An in-app CameraX camera with a pose
  overlay was considered and left as a possible follow-up.
- Sections 1–3 of this design were each approved as written below.

### Assumed

- Everything stays on the device. Nothing here uses the network, and Coach
  never sees it.
- One check-in per calendar day.
- The change since the previous entry is shown in a neutral colour, never red
  or green, because whether up is good depends on what is measured.

## Fit with the guideline

- §14 forbids camera and storage-wide permissions. `TakePicture` hands the
  capture to the system camera app and needs neither, **provided the manifest
  does not declare `CAMERA`** — Android makes `ACTION_IMAGE_CAPTURE` fail with
  a `SecurityException` when an app declares the permission and has not been
  granted it. The photo picker needs no permission on any API level the app
  supports (on 28–29 it falls back to `ACTION_OPEN_DOCUMENT`).
- §3's non-goals exclude Health Connect and camera pose estimation. This
  feature uses neither.
- §7: the database takes an explicit migration with a migration test, like
  every version before it.
- `allowBackup="false"` is already set, so Android's cloud backup takes none of
  this either.

## Data (`core:database`, schema 7 → 8)

### Tables

**`measurement_type`**

| Column | Type | Notes |
|---|---|---|
| `id` | TEXT PK | Built-ins use fixed ids (`weight`, `body_fat`, `neck`, …); custom types a UUID |
| `builtIn` | TEXT? | The built-in key, null for custom |
| `name` | TEXT? | Custom only; built-in names come from string resources |
| `kind` | TEXT | `MASS`, `LENGTH`, `PERCENT`, `PLAIN` |
| `unitLabel` | TEXT? | `PLAIN` only, the user's own unit, never converted |
| `tracked` | INTEGER | Shown on the check-in form |
| `sortOrder` | INTEGER | |

Built-ins, seeded by the migration and on a fresh install, in this order:
weight (`MASS`), body fat (`PERCENT`), then `LENGTH`: neck, shoulders, chest,
waist, hips, left arm, right arm, left forearm, right forearm, left thigh,
right thigh, left calf, right calf. Weight, waist and body fat start tracked.

**`check_in`**: `id` TEXT PK, `epochDay` INTEGER UNIQUE (local date),
`createdAt` INTEGER, `updatedAt` INTEGER.

**`measurement`**: `checkInId` FK → `check_in` ON DELETE CASCADE, `typeId` FK
→ `measurement_type` ON DELETE CASCADE, `value` REAL. Primary key
`(checkInId, typeId)`. Index on `typeId`.

**`progress_photo`**: `checkInId` FK ON DELETE CASCADE, `pose` TEXT (`FRONT`,
`SIDE`, `BACK`), `fileName` TEXT. Primary key `(checkInId, pose)`.

### Units

Stored canonically: kg, cm, %. `PLAIN` is stored as entered. Display converts
through the existing `UnitSystem`: lengths as cm or in to one decimal, weight
through `formatWeight` and the unit symbol. Input is read back through the same
conversion. Switching units never rewrites stored data, and a value entered,
shown, and saved again unchanged keeps its stored value (no drift on re-save:
an unedited field is not re-parsed).

### Rules held by the repository

- A check-in must hold at least one measurement or one photo. Saving an empty
  one is refused; removing the last thing from an existing one is a delete,
  and the screen asks first.
- The date may not be in the future.
- Turning a type off hides it from the form and the Body tab; its history
  stays.
- Deleting a custom type deletes its measurements (cascade). Built-ins cannot
  be deleted, only turned off.
- Values must be finite and positive; percent at most 100.

## Photos (`core:body`)

`PhotoStore` owns the files, in `filesDir/body-photos/`.

- **Import** reads the source URI, applies EXIF orientation, scales the long
  edge down to 2048px (never up), and re-encodes as JPEG quality 90 into a
  temporary file, then renames it into place. Re-encoding writes no EXIF, so
  GPS position and camera details are gone. A failure at any step deletes the
  temporary file and returns a typed failure: `Unreadable` or `NoSpace`.
- **Camera capture** goes to a file in `cacheDir/camera/` exposed through a
  `FileProvider` scoped to that directory only. The pending path is kept in
  `SavedStateHandle`, so a process killed while the camera app is open still
  finds its photo on return. The captured file is then imported like any
  other and the cache file deleted.
- **Delete** of a photo or check-in deletes the file after the row.
- **Orphan sweep** on startup removes files in `body-photos/` that no row
  names, and everything in `cacheDir/camera/` older than a day.

## Export (`core:transfer`, format version 3)

Adds `measurementTypes`, `checkIns` (each with its measurements) to
`ExportDocument`. Photos are not exported; a check-in's photo rows are not
written either, since a row without its file means nothing on another phone.
Version 2 and 1 files still import and simply carry no body data. Import of
built-in types merges onto the seeded rows by id rather than duplicating
them.

Settings' export row gains the line "Photos aren't included".

## Screens (`feature:body`)

### Progress tab

A two-segment switch at the top: **Workouts** (today's screen, unchanged) and
**Body**. The choice is remembered for the session.

### Body tab

- Empty state: one line on what this is for, and **Check in**.
- Latest values: each tracked type with a value, its latest value, and the
  change since its previous entry ("▼ 1.1"), in a neutral colour. Tapping a
  row opens Measurement detail.
- Photos: the latest front, side and back thumbnails, and **Compare** (enabled
  once two check-ins share a pose).
- Links: **All check-ins**, **Choose what to track**.
- **Check in**, pinned at the bottom.

### Check-in

One screen for new and edit. Opening it for a date that already has a
check-in edits that one.

- Date: today by default; a date picker that refuses future dates.
- One field per tracked type, in the user's units, using the builder's number
  entry rules (a comma is a decimal mark in Turkish).
- Three photo slots, Front, Side and Back: **Take photo**, **Choose photo**,
  and **Remove** once filled. With no camera app, **Take photo** is disabled
  and says why.
- **Save**; when editing, **Delete check-in**, which asks first.
- Back with unsaved changes asks "Discard changes?".

### Measurement detail

`TrendChart` with range chips (3 months, 6 months, 1 year, All), then every
entry newest first; tapping one opens its check-in.

### Compare photos

A pose switch and two date pickers, defaulting to the earliest and latest
check-ins with that pose. The two photos side by side, each with its date and
that day's weight if recorded. Tapping one opens it full screen.

### All check-ins

Newest first: date, number of measurements, a thumbnail. Tapping opens the
check-in.

### Choose what to track

Built-in types with switches. **Add custom measurement**: name plus kind
(length, weight, percent, or a number with a unit label). Custom types can be
renamed or deleted; delete asks first and says how many entries go with it.

### Rules carried over

Every string in `values/` and `values-tr/` together. Figures in
`RepForthNumeric`, nothing numeric below xs. Layouts wrap at 2× text rather
than truncate; no fixed heights on text. Touch targets at `Target.min`.
Motion through `rfTween`.

## `TrendChart` (`core:designsystem`)

Drawn on `Canvas` with the existing tokens.

- Points placed by date, not by index.
- One line in the accent colour, a dot per entry, the latest value labelled.
- First and last date on the x axis; lowest and highest value on the y axis,
  in `RepForthNumeric`.
- A single entry draws as one dot with its value. No entries draws nothing;
  the screen shows its empty text instead.
- No animation.
- Semantics: one sentence, e.g. "Waist, from 90 to 86 cm over the last
  3 months". The entries are listed below it on every screen that uses it.

## Testing

- **`core:database`**: migration 7 → 8 on the managed emulator, including the
  seeded built-ins; DAO tests for cascade deletes and the unique day.
- **`core:body`**, Robolectric: `PhotoStore` strips EXIF (GPS written into the
  fixture is absent after import), applies rotation, downscales to 2048px,
  never upscales, leaves no temporary file on failure, and the sweep removes
  only unreferenced files. Repository: empty check-in refused, future date
  refused, one per day, deleting a check-in deletes its files.
- **Units**: conversion round trips; an unedited field is not re-parsed.
- **`core:transfer`**: version 3 round trip; version 2 file imports with no
  body data; no photo field in the output; built-ins merge rather than
  duplicate.
- **`feature:body`**: view-model tests; `*ComposeTest` hosting the real
  screens — Save reaches the repository, the Progress switch reaches the Body
  tab, the discard prompt appears; `assertScreenIsAccessible` on every screen;
  goldens in English and Turkish at 1× and 2× for the Body tab (empty and
  filled), Check-in, Measurement detail, Compare and Choose what to track.
- **`:app`**, managed emulator: Progress → Body → Check in → enter a waist →
  Save → the value is shown. A Hilt test module stands in for photo capture,
  since a test cannot drive another app's camera.
- **Galaxy S23**: a real photo from Samsung's camera app shows upright, a
  compare, and an export that imports cleanly. Knox blocks `run-as`, so the
  stored file cannot be pulled to check its EXIF on the phone; the `PhotoStore`
  test is the evidence that location is gone.

## Docs

- `PROJECT_GUIDELINE.md`: a body-tracking subsection in §3 and §7, and a
  sentence in §14 that photos stay on the device.
- `docs/PLAN.md`: a new section with its state.
- `PRIVACY.md`: what is stored, that photos are stripped of location and never
  leave the phone, and that exports leave them out.

## Out of scope

- An in-app camera with a pose overlay (possible follow-up on top of this).
- Exporting photos.
- Reminders to check in.
- Health Connect, smart scales, or anything that reads a sensor.
- Showing body data to Coach.
- Goals or targets per measurement.
