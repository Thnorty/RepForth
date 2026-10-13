# Body Tracking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dated check-ins holding body measurements (built-in and custom) and posed photos, shown under Progress → Body as latest values, trend charts and a photo compare.

**Architecture:** Domain types in `core:model`; four new tables in `core:database` (schema 8); a new `core:body` module owning the repository and the photo files; export format 3 in `core:transfer`; a new `feature:body` module for the screens; `:app` hosts a Workouts | Body switch on the Progress tab and the new destinations. Photos come from the system camera app (`TakePicture`) and the system photo picker — no permission, no new dependency. Charts are drawn on `Canvas`.

**Tech Stack:** Kotlin, Jetpack Compose + Material 3 (1.3.2), Room 2.7.2, Hilt, kotlinx.serialization, Coil 3 (already present) for thumbnails, Robolectric + Roborazzi for JVM UI tests, the `pixel6Api34` managed emulator for instrumentation.

**Spec:** `docs/superpowers/specs/2026-10-13-body-tracking-design.md`

## Global Constraints

- Every user-visible string goes into `values/strings.xml` **and** `values-tr/strings.xml` of its module in the same commit. Turkish apostrophes are escaped `\'`. Each new module with strings gets a `*StringParityTest : StringParityContract()`.
- Figures (values, changes, counts, dates' day numbers where shown as figures) use `RepForthNumeric`, never below `xs` (20sp). Labels never outrank their figure.
- No fixed height on a container holding text; layouts must survive Turkish and 2× font scale. Touch targets `heightIn(min = Target.min)`.
- Motion only through `rfTween`; the chart has no animation.
- Colours, spacing, type only from the token files (`Color.kt`, `Dimens.kt`, `Type.kt`); screens name icons through `RfIcons`.
- **The manifest must not declare `android.permission.CAMERA`.** Declaring it makes `ACTION_IMAGE_CAPTURE` throw `SecurityException` unless granted, and §14 forbids asking.
- Nothing in this feature touches the network. Coach never receives body data.
- Database: explicit migration only; every mutable table has a single `id` primary key plus `created_at` and `updated_at` (INTEGER); every foreign key `ON DELETE CASCADE`; no foreign key to `exercise` (`UserDataSchemaTest`).
- A test that hosts a composable with `createComposeRule` is named `*ComposeTest`, `*ScreenshotTest` or `*AccessibilityTest`.
- Build checks are three separate invocations: `./gradlew assemblePlaceholderDebug`, `./gradlew test`, `./gradlew lint`.
- Commit messages: imperative, intent not file list, **no attribution lines of any kind**.
- Work on branch `body-tracking`; `master` is protected.

## Review Focus

1. **A Samsung camera photo arrives rotated** (EXIF orientation 6 or 8, pixels stored landscape). Expected: stored upright. Pinned in Task 4 (`a rotated photo is stored upright`).
2. **The user changes a check-in's date onto a day that already has one.** Expected: save refused with "There's already a check-in on that date", nothing overwritten. Pinned in Task 3 (`saving onto a taken day is refused`) and Task 8 (view-model test).
3. **Units switched between entering and re-saving a check-in.** Expected: an untouched field keeps its stored value exactly; no lb→kg→lb drift. Pinned in Task 8 (`an untouched field keeps its stored value`).
4. **Importing a version 3 file while check-ins hold photos.** Expected: measurements replaced by the file's; check-ins with photos survive with their photos. Pinned in Task 5 (`import keeps check-ins that hold photos`).
5. **The process is killed while the camera app is open.** Expected: on return the photo lands in the slot it was taken for. Pinned in Task 8 (`a capture survives process death`).

## Rulings made while planning

- **Ruling: built-in types are seeded by the repository, not by the migration** — the spec says "seeded by the migration and on a fresh install"; a fresh install starts from the prepackaged asset, which `import-dataset.py` builds from DDL alone. `BodyRepository` inserts any missing built-in with `INSERT OR IGNORE` before its first read, from the single list `BuiltInMeasurement.entries`. Same effect on both paths, one source of truth. Cost if wrong: none visible to a user.
- **Ruling: `measurement` and `progress_photo` get their own `id`, `created_at`, `updated_at`** with unique indexes on `(check_in_id, type_id)` and `(check_in_id, pose)` — `UserDataSchemaTest` requires it of every mutable table, and these rows are edited. The spec's composite primary keys become unique indexes. Cost if wrong: four columns.
- **Ruling: import keeps photographed check-ins** — import is a clear-then-write restore, and photos are never in the file, so a literal restore would delete every photo. A file carrying body data (`body != null`) replaces all measurement types' tracked flags, custom types and measurements; check-ins absent from the file are deleted unless they hold a photo, in which case they stay with their photos and no measurements. A file with no body section (version 1–2, or `body == null`) leaves body data untouched. Cost if wrong: a user restoring an old backup keeps photos they expected gone.
- **Ruling: "Delete workout data" leaves body data; "Reset app" removes it, photos included** — the row says "Profile, plans and history", and `ResetCoverageTest` requires reset to clear every store `DefaultDataTransfer` holds. Cost if wrong: one more call in `deleteWorkoutData`.
- **Ruling: the orphan sweep runs once per process, on the Body tab's first load** — the app has no application coroutine scope, and the sweep only matters to screens that show photos. Cost if wrong: an orphan file lives until the Body tab is opened.
- **Ruling: the `:app` instrumentation test covers the measurement path; the photo path is proven by a `*ComposeTest` that feeds the screen's real `ActivityResultLauncher` through a test `ActivityResultRegistry`** — a Hilt module cannot substitute an `ActivityResultContract`, and a test cannot drive another app's camera. Cost if wrong: the camera hand-off itself is proven only on hardware (Task 13).

---

### Task 1: Body types in `core:model` and length units

**Files:**
- Create: `core/model/src/main/java/com/repforth/core/model/Body.kt`
- Modify: `core/designsystem/src/main/java/com/repforth/core/designsystem/theme/Units.kt`
- Test: `core/model/src/test/java/com/repforth/core/model/CheckInRulesTest.kt`, `core/designsystem/src/test/java/com/repforth/core/designsystem/theme/LengthUnitsTest.kt`

**Interfaces:**
- Produces:
  - `enum class MeasurementKind { MASS, LENGTH, PERCENT, PLAIN }`
  - `enum class BuiltInMeasurement(val id: String, val kind: MeasurementKind, val trackedByDefault: Boolean)` with entries, in this order: `WEIGHT("weight", MASS, true)`, `BODY_FAT("body_fat", PERCENT, true)`, `NECK("neck")`, `SHOULDERS("shoulders")`, `CHEST("chest")`, `WAIST("waist", LENGTH, true)`, `HIPS("hips")`, `ARM_LEFT("arm_left")`, `ARM_RIGHT("arm_right")`, `FOREARM_LEFT("forearm_left")`, `FOREARM_RIGHT("forearm_right")`, `THIGH_LEFT("thigh_left")`, `THIGH_RIGHT("thigh_right")`, `CALF_LEFT("calf_left")`, `CALF_RIGHT("calf_right")` (unlisted kind = `LENGTH`, unlisted tracked = `false`). `companion fun byId(id: String): BuiltInMeasurement?`
  - `data class MeasurementType(val id: String, val builtIn: BuiltInMeasurement?, val customName: String?, val kind: MeasurementKind, val unitLabel: String?, val tracked: Boolean, val sortOrder: Int)`
  - `enum class Pose { FRONT, SIDE, BACK }`
  - `data class CheckIn(val id: String, val date: LocalDate, val measurements: Map<String, Double>, val photos: Map<Pose, String>)` — measurements keyed by type id, canonical units; photos are file names.
  - `enum class CheckInProblem { EMPTY, FUTURE_DATE, NOT_POSITIVE, PERCENT_OVER_100, UNKNOWN_TYPE, DATE_TAKEN }`
  - `object CheckInRules { fun check(checkIn: CheckIn, today: LocalDate, types: Map<String, MeasurementType>): CheckInProblem? }` — never returns `DATE_TAKEN` (the repository owns that).
  - In `Units.kt`: `fun UnitSystem.fromCentimetres(cm: Double): Double`, `fun UnitSystem.toCentimetres(value: Double): Double` (2.54 cm per inch), `fun UnitSystem.formatLength(cm: Double, locale: Locale): String` (one decimal, locale separator, trailing `.0` dropped like `formatWeight`), `val UnitSystem.lengthSymbol: String` (`cm` / `in`), and `fun readDecimal(text: String): Double?` (comma or point as decimal mark; null for blank or invalid) — `readWeight` is re-expressed through it.

- [ ] **Step 1: Write the failing tests**

`CheckInRulesTest`: `an empty check-in is refused` (`EMPTY`); `a photo alone is enough` (null); `a future date is refused` (`FUTURE_DATE`); `today is allowed`; `zero or negative is refused` (`NOT_POSITIVE` for 0.0 and -1.0); `infinite or NaN is refused` (`NOT_POSITIVE`); `body fat over 100 is refused` (`PERCENT_OVER_100` for 100.1; null for 100.0); `a measurement of an unknown type is refused` (`UNKNOWN_TYPE`); `built-in ids are unique and stable` (asserts the exact id list above in order).

`LengthUnitsTest`: `inches round-trip without drift` (for 50.0..150.0 step 0.1 cm: `toCentimetres(fromCentimetres(cm))` within 1e-9); `metric is identity`; `formatLength shows one decimal in the locale` (`86.0` cm → `"86"`, `86.25` → `"86.3"`, Turkish 86.25 → `"86,3"`, imperial 86.36 cm → `"34"`); `readDecimal accepts comma and point` (`"34,5"` and `"34.5"` → 34.5, `""` → null, `"abc"` → null).

- [ ] **Step 2: Run them and watch them fail**

Run: `./gradlew :core:model:testDebugUnitTest --tests "*CheckInRulesTest" :core:designsystem:testDebugUnitTest --tests "*LengthUnitsTest"`
Expected: compilation fails on the missing types.

- [ ] **Step 3: Implement the types, `CheckInRules.check` and the unit functions**

- [ ] **Step 4: Run the tests again**

Expected: PASS, and `./gradlew :core:designsystem:testDebugUnitTest` still passes (the `readWeight` rewrite).

- [ ] **Step 5: Commit** — `git commit -m "Describe body check-ins and measure lengths in the user's units"`

---

### Task 2: Schema 8 — the four body tables

**Files:**
- Create: `core/database/src/main/java/com/repforth/core/database/entity/BodyEntities.kt`, `core/database/src/main/java/com/repforth/core/database/dao/BodyDao.kt`
- Modify: `core/database/src/main/java/com/repforth/core/database/RepForthDatabase.kt` (entities, `bodyDao()`, `VERSION = 8`, `MIGRATION_7_8`), `core/database/src/main/java/com/repforth/core/database/di/DatabaseModule.kt` (add migration, provide `BodyDao`)
- Generated, committed: `core/database/schemas/com.repforth.core.database.RepForthDatabase/8.json`, rebuilt `core/database/src/main/assets/repforth.db` (and whatever `import-dataset.py` regenerates alongside it)
- Test: `core/database/src/androidTest/java/com/repforth/core/database/MigrationTest.kt`, `core/database/src/androidTest/java/com/repforth/core/database/BodyDaoTest.kt`

**Interfaces:**
- Produces tables (snake_case columns):
  - `measurement_type(id TEXT PK, built_in TEXT?, custom_name TEXT?, kind TEXT, unit_label TEXT?, tracked INTEGER, sort_order INTEGER, created_at, updated_at)`
  - `check_in(id TEXT PK, epoch_day INTEGER UNIQUE, created_at, updated_at)`
  - `measurement(id TEXT PK, check_in_id → check_in CASCADE, type_id → measurement_type CASCADE, value REAL, created_at, updated_at)`, unique `(check_in_id, type_id)`, index `type_id`
  - `progress_photo(id TEXT PK, check_in_id → check_in CASCADE, pose TEXT, file_name TEXT, created_at, updated_at)`, unique `(check_in_id, pose)`
- Produces `BodyDao` with at least: `observeTypes(): Flow<List<MeasurementTypeEntity>>`, `insertTypesIfMissing(List<MeasurementTypeEntity>)` (`OnConflictStrategy.IGNORE`), `upsertType`, `setTracked(id, tracked, now)`, `renameType(id, name, now)`, `deleteType(id)`, `countMeasurements(typeId): Int`, `observeCheckIns(): Flow<List<CheckInWithDetails>>` (newest `epoch_day` first; `@Relation` to measurements and photos), `findCheckIn(id)`, `findCheckInOnDay(epochDay)`, `@Transaction saveCheckIn(checkIn, measurements, photos)` (upsert parent, delete then insert children), `deleteCheckIn(id)`, `photoFileNames(): List<String>`, `deleteAllBody()` (all four tables).

- [ ] **Step 1: Write the failing instrumentation tests**

In `MigrationTest`: `migrating_from_7_to_8_produces_the_schema_room_expects` (create v7, `runMigrationsAndValidate(TEST_DB, 8, true, MIGRATION_7_8)`), and `migrating_from_7_to_8_keeps_existing_plans` (insert a `workout_template` row at v7, read it back at v8).
`BodyDaoTest` (in-memory Room): `deleting_a_check_in_deletes_its_measurements_and_photos`; `deleting_a_type_deletes_its_measurements`; `two_check_ins_cannot_share_a_day` (second insert with the same `epoch_day` throws `SQLiteConstraintException`); `inserting_types_if_missing_keeps_existing_rows` (a row with `tracked = true` is not reset by a second seed with `tracked = false`).

- [ ] **Step 2: Run them and watch them fail**

Run: `./gradlew :core:database:pixel6Api34DebugAndroidTest`
Expected: compilation fails (no `MIGRATION_7_8`, no `BodyDao`).

- [ ] **Step 3: Add the entities, the DAO, the migration and the DI wiring**

`MIGRATION_7_8` creates the four tables and their indexes with exactly the SQL Room expects; copy it from the generated `8.json` `createSql` entries after the first compile, so the migration and the schema cannot disagree. No rows are inserted by the migration (see Rulings).

- [ ] **Step 4: Regenerate the schema and rebuild the packaged asset**

Run: `./gradlew :core:database:compileDebugKotlin` (writes `8.json`), then `python tools/import-dataset.py`.
Expected: `8.json` exists; `./gradlew :core:database:testDebugUnitTest` passes — `PackagedCatalogTest` (asset `user_version` 8 and identity hash), `UserDataSchemaTest` (ids, timestamps, cascades), `SchemaExportTest`. If `UserDataSchemaTest` fails, fix the entity, never the rule.

- [ ] **Step 5: Run the instrumentation tests**

Run: `./gradlew :core:database:pixel6Api34DebugAndroidTest`
Expected: all pass, including every pre-existing migration test. If the run reports `tests="0"`, it is the emulator, not the suite (AGENTS.md).

- [ ] **Step 6: Commit** — `git commit -m "Add the body tracking tables in schema 8"`

---

### Task 3: `core:body` — the repository

**Files:**
- Create module `core/body/` (`build.gradle.kts` with `repforth.android.library`, `repforth.android.hilt`; namespace `com.repforth.core.body`; `api(project(":core:model"))`, `implementation` of `:core:database`, `:core:common`), add `include(":core:body")` to `settings.gradle.kts`
- Create: `core/body/src/main/java/com/repforth/core/body/BodyRepository.kt`, `RoomBodyRepository.kt`, `di/BodyModule.kt`
- Create: `core/testing/src/main/java/com/repforth/core/testing/FakeBodyRepository.kt` (and `api(project(":core:body"))` in `core:testing`)
- Test: `core/body/src/test/java/com/repforth/core/body/RoomBodyRepositoryTest.kt` (against a hand-written `FakeBodyDao` in the same test source set)

**Interfaces:**
- Consumes: Task 1 types, Task 2 `BodyDao`, `TimeSource`, `ZoneId`.
- Produces:

```kotlin
sealed interface SaveCheckIn {
    data class Saved(val id: String) : SaveCheckIn
    data class Refused(val problem: CheckInProblem) : SaveCheckIn
}

interface BodyRepository {
    fun observeTypes(): Flow<List<MeasurementType>>          // sortOrder ascending, built-ins seeded first
    fun observeCheckIns(): Flow<List<CheckIn>>              // newest date first
    suspend fun find(id: String): CheckIn?
    suspend fun findOn(date: LocalDate): CheckIn?
    suspend fun save(checkIn: CheckIn): SaveCheckIn          // runs CheckInRules, then DATE_TAKEN
    suspend fun delete(id: String)                           // rows, then that check-in's photo files
    suspend fun setTracked(typeId: String, tracked: Boolean)
    suspend fun addCustom(name: String, kind: MeasurementKind, unitLabel: String?): MeasurementType
    suspend fun rename(typeId: String, name: String)         // custom only; ignored for built-ins
    suspend fun deleteCustom(typeId: String)                 // custom only; cascades its measurements
    suspend fun entryCount(typeId: String): Int
    suspend fun restoreMeasurements(types: List<MeasurementType>, checkIns: List<CheckIn>)  // import, see Rulings
    suspend fun deleteAll()                                  // rows and every photo file
    suspend fun sweepPhotosOnce()                            // no-op after the first call in a process
}
```

`RoomBodyRepository` takes `BodyDao`, `PhotoStore` (Task 4 — declare the interface in this task with the members listed there and bind the real one in Task 4; until then the test uses a recording fake), `TimeSource`, `ZoneId`. Saving deletes the files of photos the save dropped, after the transaction commits.

- [ ] **Step 1: Write the failing tests**

`RoomBodyRepositoryTest`: `built-ins are seeded once, in order` (first `observeTypes()` emits the 15 built-ins in `BuiltInMeasurement` order with weight, body fat and waist tracked); `seeding does not undo a user's choice` (`setTracked("neck", true)`, re-create the repository over the same DAO, neck still tracked); `an empty check-in is refused` (`Refused(EMPTY)`); `saving onto a taken day is refused` (save A on 2026-10-01, save B with a different id on 2026-10-01 → `Refused(DATE_TAKEN)`, A unchanged); `editing a check-in keeps its day` (save A, save A again with a changed value on the same date → `Saved`); `deleting a check-in deletes its photo files` (recording `PhotoStore` received the check-in's file names); `dropping a photo on save deletes its file`; `a built-in cannot be renamed or deleted`; `deleting a custom type removes its entries` (`entryCount` 0 and the check-in no longer maps it); `restore replaces measurements and keeps photographed check-ins` (existing: X with a photo and a waist, Y with only a weight; restore with Z only → X stays with its photo and no measurements, Y gone, Z present); `deleteAll removes every file`; `the sweep runs once` (two calls → one `sweep` on the store, with the referenced set equal to `photoFileNames()`).

- [ ] **Step 2: Run them and watch them fail**

Run: `./gradlew :core:body:testDebugUnitTest`
Expected: compilation fails on the missing module classes.

- [ ] **Step 3: Implement `BodyRepository`, `RoomBodyRepository`, `BodyModule` (binds the repository as `@Singleton`), and `FakeBodyRepository` (in-memory, same rules via `CheckInRules`)**

- [ ] **Step 4: Run the tests**

Expected: PASS. Then `./gradlew :core:testing:compileDebugKotlin` to confirm the fake compiles.

- [ ] **Step 5: Commit** — `git commit -m "Keep body check-ins and measurement types in a repository"`

---

### Task 4: `core:body` — photo files

**Files:**
- Create: `core/body/src/main/java/com/repforth/core/body/PhotoStore.kt`, `FilePhotoStore.kt`, `core/body/src/main/AndroidManifest.xml` (FileProvider), `core/body/src/main/res/xml/body_photo_paths.xml`
- Modify: `core/body/src/main/java/com/repforth/core/body/di/BodyModule.kt` (bind `FilePhotoStore`), `core/body/build.gradle.kts` (Robolectric test deps as in other Robolectric modules; `androidx.core` for `FileProvider` if not already transitive)
- Create: `core/testing/src/main/java/com/repforth/core/testing/FakePhotoStore.kt`
- Test: `core/body/src/test/java/com/repforth/core/body/FilePhotoStoreTest.kt`

**Interfaces:**
- Produces:

```kotlin
enum class PhotoFailure { UNREADABLE, WRITE_FAILED }
sealed interface PhotoImport {
    data class Stored(val fileName: String) : PhotoImport
    data class Failed(val reason: PhotoFailure) : PhotoImport
}
data class PhotoCapture(val uri: Uri, val path: String)

interface PhotoStore {
    suspend fun importFrom(source: Uri): PhotoImport
    fun newCapture(): PhotoCapture                  // file in cacheDir/camera/, uri from FileProvider
    suspend fun importCapture(path: String): PhotoImport   // imports, then deletes the capture file
    fun fileFor(fileName: String): File             // filesDir/body-photos/<fileName>
    suspend fun delete(fileNames: Collection<String>)
    suspend fun sweep(referenced: Set<String>)      // unreferenced body-photos files; camera/ files older than 24h
    suspend fun deleteAll()
}
```

FileProvider authority: `${applicationId}.bodyphotos`, paths: `<cache-path name="camera" path="camera/" />` only. Import: decode bounds, `inSampleSize` to the largest power of two keeping the long edge ≥ 2048, decode, apply orientation from `android.media.ExifInterface` (platform; minSdk 28 reads from a stream), scale the long edge to 2048 if still larger (never up), compress JPEG quality 90 to `body-photos/.<uuid>.tmp`, rename to `<uuid>.jpg`. Any failure deletes the temporary file. All work on `Dispatchers.IO` (constructor seam, as `DefaultDataTransfer`).

- [ ] **Step 1: Write the failing tests** (Robolectric, `@GraphicsMode(NATIVE)`)

`FilePhotoStoreTest`: `location is stripped` (fixture JPEG with GPS latitude/longitude and `Make` written through `ExifInterface.saveAttributes`; after import the stored file's `ExifInterface` has no `TAG_GPS_LATITUDE` and no `TAG_MAKE`); `a rotated photo is stored upright` (fixture 400×200 pixels with `ORIENTATION_ROTATE_90` → stored 200×400); `a large photo is scaled to 2048` (4096×3072 → 2048×1536); `a small photo is not scaled up` (800×600 stays); `an unreadable file fails cleanly` (random bytes → `Failed(UNREADABLE)`, `body-photos/` holds no file); `a capture is imported and its cache file removed`; `the sweep keeps referenced files only`; `the sweep clears stale captures` (camera file with `lastModified` 25h ago removed, 1h ago kept); `deleteAll empties the folder`.

- [ ] **Step 2: Run them and watch them fail**

Run: `./gradlew :core:body:testDebugUnitTest --tests "*FilePhotoStoreTest"`
Expected: compilation fails.

- [ ] **Step 3: Implement `FilePhotoStore`, the manifest provider and paths, the binding, and `FakePhotoStore` (in-memory names; records deletes and sweeps)**

Replace Task 3's test-only recording fake with `FakePhotoStore` where it fits.

- [ ] **Step 4: Run the module's tests**

Run: `./gradlew :core:body:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Confirm no camera permission reaches the app**

Run: `./gradlew assemblePlaceholderDebug` then `aapt2 dump permissions app/build/outputs/apk/placeholder/debug/app-placeholder-debug.apk`
Expected: no `android.permission.CAMERA`.

- [ ] **Step 6: Commit** — `git commit -m "Store progress photos privately, upright and without location"`

---

### Task 5: Export format 3, import and reset

**Files:**
- Modify: `core/transfer/src/main/java/com/repforth/core/transfer/ExportDocument.kt` (`VERSION = 3`, `body: BodyDto? = null`, DTOs, `ImportPreview.checkIns: Int?`), `TransferMapping.kt`, `DataTransfer.kt` (constructor gains `body: BodyRepository`; export, read, import, resetApp), `core/transfer/build.gradle.kts` (`implementation(project(":core:body"))`)
- Modify: `feature/settings/src/main/res/values/strings.xml`, `values-tr/strings.xml` (`settings_export_sub`, a new `settings_import_body` line), the import confirmation in `SettingsScreen.kt`
- Test: `core/transfer/src/test/java/com/repforth/core/transfer/DataTransferTest.kt`, `Fakes.kt`

**Interfaces:**
- Consumes: `BodyRepository` (Task 3).
- Produces:

```kotlin
@Serializable data class BodyDto(val types: List<MeasurementTypeDto>, val checkIns: List<CheckInDto>)
@Serializable data class MeasurementTypeDto(val id: String, val builtIn: String? = null, val customName: String? = null,
    val kind: String, val unitLabel: String? = null, val tracked: Boolean, val sortOrder: Int)
@Serializable data class CheckInDto(val id: String, val date: String /* ISO-8601 */, val measurements: Map<String, Double>)
```

`ImportPreview` gains `checkIns: Int?` — null when the file carries no body section. Settings shows, when non-null: EN "%1$d body check-ins. They replace your measurements; your photos stay." / TR "%1$d vücut kaydı. Ölçümlerinin yerini alır; fotoğrafların kalır." `settings_export_sub` becomes EN "Your profile, plans, history and body measurements, as one JSON file. Photos aren't included." / TR "Profilin, planların, geçmişin ve vücut ölçülerin, tek bir JSON dosyası olarak. Fotoğraflar dahil değil."

- [ ] **Step 1: Write the failing tests**

`DataTransferTest`: `body measurements round-trip` (export with two check-ins and one custom type, read, import into an empty store → equal measurements and types); `no photo reaches the file` (exported JSON contains no `photo`, no `.jpg`, no file name from the fake); `a version 2 file leaves body data alone` (`body` absent → existing check-ins untouched, `preview.checkIns == null`); `import keeps check-ins that hold photos` (delegates to `restoreMeasurements`; assert through `FakeBodyRepository`'s state as in Task 3's restore test); `an invalid check-in fails the read` (negative value → `ImportOutcome.Failed(Invalid)`); `reset deletes body data`. `ResetCoverageTest` must still pass unchanged — it fails if `body` is not cleared in `resetApp`; watch it fail once by leaving the call out, then add it.

- [ ] **Step 2: Run them and watch them fail**

Run: `./gradlew :core:transfer:testDebugUnitTest`
Expected: compilation fails on `BodyDto` and the constructor.

- [ ] **Step 3: Implement the DTOs, mapping, export, read (validate each check-in with `CheckInRules` against the file's own types, dates parsed with `LocalDate.parse`), import (`body?.let { restoreMeasurements(...) }` inside the existing transaction, after `deleteWorkoutData()`), `resetApp` (`body.deleteAll()`), the preview count, and the Settings strings and line**

- [ ] **Step 4: Run the tests**

Run: `./gradlew :core:transfer:testDebugUnitTest :feature:settings:testDebugUnitTest`
Expected: PASS. Settings goldens that show the export row change: re-record with `-Proborazzi.record=true`, look at each changed image, and commit them.

- [ ] **Step 5: Commit** — `git commit -m "Export body measurements and keep photos out of the file"`

---

### Task 6: Design system — trend chart, segmented choice, icons

**Files:**
- Create: `core/designsystem/src/main/java/com/repforth/core/designsystem/component/TrendChart.kt`, `SegmentedChoice.kt`
- Modify: `tools/icons.txt` (add `photo_camera`, `photo_library`, `compare`), run `tools/fetch-icons.sh` and `tools/svg-to-vector.py` to generate `rf_sym_*.xml`; `RfIcons.kt` (`Camera`, `Gallery`, `Compare`)
- Test: `core/designsystem/src/test/java/com/repforth/core/designsystem/component/TrendLayoutTest.kt`, `TrendChartComposeTest.kt`

**Interfaces:**
- Produces:

```kotlin
data class TrendPoint(val date: LocalDate, val value: Double)

@Composable fun TrendChart(
    points: List<TrendPoint>,
    formatValue: (Double) -> String,
    formatDate: (LocalDate) -> String,
    description: String,                 // the one TalkBack sentence, built by the caller
    modifier: Modifier = Modifier,
)

internal data class TrendLayout(val dots: List<Offset>, val minValue: Double, val maxValue: Double)
internal fun trendLayout(points: List<TrendPoint>, width: Float, height: Float): TrendLayout

@Composable fun <T> RfSegmentedChoice(
    options: List<T>, selected: T, label: @Composable (T) -> String,
    onSelect: (T) -> Unit, modifier: Modifier = Modifier,
)
```

`trendLayout`: x proportional to days since the first point (last point at `width`); y maps `[min - pad, max + pad]` to `[height, 0]` with `pad = max((max - min) * 0.1, 0.5)`; a single point sits at the horizontal and vertical centre. `TrendChart` draws the line and dots in `MaterialTheme.colorScheme.primary`, labels min/max and first/last date in `RepForthNumeric.xs`, and sets `contentDescription = description` with the canvas cleared of other semantics. `RfSegmentedChoice` wraps `SingleChoiceSegmentedButtonRow` with `heightIn(min = Target.min)` per button.

- [ ] **Step 1: Write the failing tests**

`TrendLayoutTest`: `points are spaced by date, not by index` (dates day 0, 1, 31 → x of the second point is `width / 31`); `the range is padded`; `a flat series still has height` (all 80.0 → min 79.5, max 80.5); `a single point is centred`.
`TrendChartComposeTest`: `it announces one sentence` (the node with `description` exists; no node exposes the raw axis text separately).

- [ ] **Step 2: Run them and watch them fail**

Run: `./gradlew :core:designsystem:testDebugUnitTest --tests "*Trend*"`
Expected: compilation fails.

- [ ] **Step 3: Implement both components; fetch and convert the three icons; add the `RfIcons` roles**

- [ ] **Step 4: Run the module's tests**

Run: `./gradlew :core:designsystem:testDebugUnitTest`
Expected: PASS (including the icon and motion guards).

- [ ] **Step 5: Commit** — `git commit -m "Add a trend chart, a segmented choice and three photo icons"`

---

### Task 7: `feature:body` — the Body tab and the Progress switch

**Files:**
- Create module `feature/body/` (`repforth.android.library`, `.compose`, `.hilt`, `.screenshot`; namespace `com.repforth.feature.body`; depends on `:core:designsystem`, `:core:body`, `:core:common`, `:core:datastore` for the unit preference, lifecycle/hilt-navigation-compose, Coil compose; tests on `:core:testing`), add `include(":feature:body")`
- Create: `feature/body/src/main/java/com/repforth/feature/body/BodyScreen.kt` (`BodyRoute`, `BodyScreen`), `BodyViewModel.kt`, `MeasurementText.kt` (`formatMeasurement`, `readMeasurement`, `measurementName`), `res/values/strings.xml`, `res/values-tr/strings.xml`
- Create: `app/src/main/java/com/repforth/app/navigation/ProgressTab.kt`
- Modify: `app/src/main/java/com/repforth/app/navigation/RepForthNavHost.kt` (`composable<Destination.Progress> { ProgressTab(...) }`), `Destination.kt` (add `BodyCheckIn(val checkInId: String? = null)`, `BodyMeasurement(val typeId: String)`, `BodyCompare`, `BodyCheckIns`, `BodyTracking` — none top-level), `app/build.gradle.kts`
- Test: `feature/body/src/test/java/com/repforth/feature/body/BodyViewModelTest.kt`, `BodyScreenshotTest.kt`, `BodyAccessibilityTest.kt`, `BodyStringParityTest.kt`, `MeasurementTextTest.kt`; `app/src/test/.../ProgressTabComposeTest.kt` (if `:app` has no JVM compose test setup, put the switch's test in `feature:body` against a `ProgressSwitch` composable it exports and that `ProgressTab` uses)

**Interfaces:**
- Consumes: `BodyRepository`, `PhotoStore`, `RfSegmentedChoice`, `TrendChart` (not yet), `LocalUnitSystem`.
- Produces:
  - `@Composable fun BodyRoute(onCheckIn: () -> Unit, onOpenMeasurement: (String) -> Unit, onCompare: () -> Unit, onAllCheckIns: () -> Unit, onChooseTracked: () -> Unit, modifier: Modifier = Modifier)`
  - `data class BodyUiState(val loading: Boolean = true, val rows: List<LatestRow> = emptyList(), val latestPhotos: Map<Pose, String> = emptyMap(), val canCompare: Boolean = false)` with `val isEmpty` (not loading, no rows with a value, no photos); `data class LatestRow(val type: MeasurementType, val latest: Double, val change: Double?)`
  - In `MeasurementText.kt`: `@Composable fun MeasurementType.displayName(): String` (built-in → string resource `body_type_<id>`; custom → `customName`), `@Composable fun formatMeasurement(value: Double, type: MeasurementType): String` (with unit), `fun readMeasurement(text: String, kind: MeasurementKind, units: UnitSystem): Double?` (canonical; MASS via `toKilograms`, LENGTH via `toCentimetres`).
  - `ProgressTab(onCheckIn, onOpenMeasurement, onCompare, onAllCheckIns, onChooseTracked)` in `:app`: `RfSegmentedChoice` Workouts | Body above `HistoryRoute()` or `BodyRoute(...)`, selection in `rememberSaveable`.

Body tab layout per the spec: latest rows (name, value in `RepForthNumeric`, change "▲/▼ n" in `onSurfaceVariant`, never red/green), photo strip of the latest front/side/back with **Compare** (disabled unless `canCompare`), links **All check-ins** and **Choose what to track**, **Check in** pinned at the bottom. Empty state: one line and **Check in**. `BodyViewModel.init` calls `sweepPhotosOnce()`. Only tracked types appear in rows; a row needs at least one value.

Strings (EN / TR) include: tab labels "Workouts"/"Antrenmanlar", "Body"/"Vücut"; empty "Check in to start tracking your measurements and photos."/"Ölçülerini ve fotoğraflarını takip etmeye başlamak için kayıt ekle."; "Check in"/"Kayıt ekle"; "Compare"/"Karşılaştır"; "All check-ins"/"Tüm kayıtlar"; "Choose what to track"/"Neleri takip edeceğini seç"; every built-in name (`body_type_weight` "Weight"/"Kilo", `body_type_body_fat` "Body fat"/"Vücut yağı", neck "Neck"/"Boyun", shoulders "Shoulders"/"Omuzlar", chest "Chest"/"Göğüs", waist "Waist"/"Bel", hips "Hips"/"Kalça", `arm_left` "Left arm"/"Sol kol", `arm_right` "Right arm"/"Sağ kol", forearms "Left forearm"/"Sol ön kol" and right, thighs "Left thigh"/"Sol uyluk" and right, calves "Left calf"/"Sol baldır" and right); poses "Front"/"Ön", "Side"/"Yan", "Back"/"Arka". The Progress switch labels live in `:app`'s strings if `ProgressTab` is there.

- [ ] **Step 1: Write the failing tests**

`BodyViewModelTest`: `latest value and change come from the two newest entries of that type` (weight 82.0 on Oct 1, 80.9 on Oct 8 → latest 80.9, change ≈ -1.1); `a first entry has no change`; `untracked types are hidden`; `compare needs two check-ins sharing a pose`; `opening the tab sweeps photos once` (`FakePhotoStore` saw one sweep).
`MeasurementTextTest`: `imperial length reads back to centimetres`; `a Turkish comma reads as a decimal`; `plain and percent are stored as typed`.
`BodyScreenshotTest`: `body_empty`, `body_english`, `body_turkish`, `body_large_text`, `body_turkish_large_text` (fixed fixture, every test sets locale and font scale).
`BodyAccessibilityTest`: `assertScreenIsAccessible("Body")` on the empty and filled states.
A compose test proving the switch reaches the Body tab: tap "Body", `BodyRoute`'s empty text is displayed.

- [ ] **Step 2: Run them and watch them fail**

Run: `./gradlew :feature:body:testDebugUnitTest`
Expected: compilation fails.

- [ ] **Step 3: Implement the module, the screen, the view model, the text helpers, the Progress switch and the destinations (the new destinations may route to a placeholder `Text` until their task lands — replace each in Tasks 8–11)**

- [ ] **Step 4: Record and inspect the goldens, then run the tests**

Run: `./gradlew :feature:body:testDebugUnitTest -Proborazzi.record=true`, open each new image (both locales, both scales, empty state), then `./gradlew :feature:body:testDebugUnitTest :app:testPlaceholderDebugUnitTest`.
Expected: PASS.

- [ ] **Step 5: Commit** — `git commit -m "Add the Body tab under Progress"`

---

### Task 8: Check-in screen

**Files:**
- Create: `feature/body/src/main/java/com/repforth/feature/body/CheckInScreen.kt` (`CheckInRoute`, `CheckInScreen`), `CheckInViewModel.kt`
- Modify: strings (both locales), `RepForthNavHost.kt` (`composable<Destination.BodyCheckIn>`; Body's `onCheckIn` and later screens' "open check-in" navigate here)
- Test: `CheckInViewModelTest.kt`, `CheckInComposeTest.kt`, `CheckInScreenshotTest.kt`, `CheckInAccessibilityTest.kt`

**Interfaces:**
- Consumes: `BodyRepository`, `PhotoStore`, `readMeasurement`, `formatMeasurement`, `LocalUnitSystem`, `SavedStateHandle` (`checkInId` from the route).
- Produces:
  - `@Composable fun CheckInRoute(onDone: () -> Unit, modifier: Modifier = Modifier)`
  - `data class FieldState(val type: MeasurementType, val text: String, val originalText: String, val originalValue: Double?)`
  - `data class CheckInUiState(val date: LocalDate, val fields: List<FieldState>, val photos: Map<Pose, String>, val problem: CheckInProblem? = null, val photoFailure: PhotoFailure? = null, val canDelete: Boolean = false, val dirty: Boolean = false, val loading: Boolean = true)`
  - `CheckInViewModel`: `onDateChange(LocalDate)`, `onFieldChange(typeId: String, text: String)`, `onPhotoPicked(pose: Pose, uri: Uri?)`, `newCapture(pose: Pose): Uri` (stores `pose` and `path` under `SavedStateHandle` key `"pendingCapture"`), `onCaptureResult(success: Boolean)`, `onRemovePhoto(pose)`, `onSave()`, `onDelete()`; one-shot `done` event.

Behaviour: a new check-in for a date that already has one loads that one (`findOn`). Fields show tracked types plus any type the loaded check-in already has a value for. **A field whose `text == originalText` saves `originalValue` unparsed.** A blank field removes that measurement. Save maps `Refused(problem)` to an inline message; `DATE_TAKEN` → EN "There's already a check-in on that date." / TR "O tarihte zaten bir kayıt var." Saving an edited check-in down to nothing asks "Delete this check-in?" and deletes on confirm. Date picker: `DatePickerDialog` with `selectableDates` refusing dates after today. **Take photo** uses `rememberLauncherForActivityResult(ActivityResultContracts.TakePicture())` with `newCapture(pose)`; it is disabled with EN "No camera app found" / TR "Kamera uygulaması bulunamadı" when `Intent(MediaStore.ACTION_IMAGE_CAPTURE).resolveActivity(packageManager)` is null (declare the `<queries>` intent in `feature/body`'s manifest so API 30+ can see it). **Choose photo** uses `PickVisualMedia(ImageOnly)`. Photo failures: EN "Couldn't read that photo." / "Couldn't save that photo. The phone may be out of space." (TR "O fotoğraf okunamadı." / "Fotoğraf kaydedilemedi. Telefonda yer kalmamış olabilir."). Back with `dirty` asks "Discard changes?" (`BackHandler`), confirm leaves. Delete asks first.

- [ ] **Step 1: Write the failing tests**

`CheckInViewModelTest`: `a new check-in opens today's existing one`; `an untouched field keeps its stored value` (stored waist 86.36 cm, units imperial, save without touching → stored value exactly 86.36); `a blank field removes that measurement`; `a capture survives process death` (call `newCapture(FRONT)`, build a new view model from the same `SavedStateHandle`, `onCaptureResult(true)` → `photos[FRONT]` set via `importCapture`); `a failed capture changes nothing`; `saving onto a taken day shows the problem`; `an unreadable photo reports UNREADABLE and leaves the slot empty`.
`CheckInComposeTest` (real `CheckInRoute` with fakes): `typing a waist and saving reaches the repository`; `choosing a photo fills the slot` (provide `LocalActivityResultRegistryOwner` with a registry that answers the picker with a fixed `Uri`; `FakePhotoStore` returns `Stored("a.jpg")`; the slot shows Remove); `back with changes asks first`; `a future date cannot be picked` (open the picker; tomorrow's day is not enabled).
`CheckInScreenshotTest`: en, tr, en-2x, tr-2x of a check-in with three fields and one photo slot filled. `CheckInAccessibilityTest`: `assertScreenIsAccessible("Check-in")`.

- [ ] **Step 2: Run them and watch them fail**

Run: `./gradlew :feature:body:testDebugUnitTest --tests "*CheckIn*"`
Expected: compilation fails.

- [ ] **Step 3: Implement the screen, the view model, the manifest `<queries>`, the strings and the destination**

- [ ] **Step 4: Record and inspect the goldens, then run the module's tests**

Expected: PASS.

- [ ] **Step 5: Commit** — `git commit -m "Let people record a check-in with measurements and photos"`

---

### Task 9: Measurement detail and All check-ins

**Files:**
- Create: `MeasurementDetailScreen.kt`, `MeasurementDetailViewModel.kt`, `CheckInListScreen.kt`, `CheckInListViewModel.kt` in `feature/body/src/main/java/com/repforth/feature/body/`
- Modify: strings, `RepForthNavHost.kt` (`BodyMeasurement`, `BodyCheckIns`)
- Test: `MeasurementDetailViewModelTest.kt`, `MeasurementDetailScreenshotTest.kt`, `CheckInListScreenshotTest.kt`, `BodyListsAccessibilityTest.kt`, `MeasurementDetailComposeTest.kt`

**Interfaces:**
- Produces: `MeasurementDetailRoute(onOpenCheckIn: (String) -> Unit)`, `enum class TrendRange(val months: Long?) { THREE(3), SIX(6), YEAR(12), ALL(null) }`, `data class MeasurementDetailUiState(val type: MeasurementType?, val range: TrendRange, val points: List<TrendPoint>, val entries: List<Entry>)` with `data class Entry(val checkInId: String, val date: LocalDate, val value: Double)`; `CheckInListRoute(onOpenCheckIn: (String) -> Unit)` with rows (date, measurement count, first photo thumbnail if any).

Chart description sentence: EN "%1$s, from %2$s to %3$s over %4$s" (range phrase from resources: "the last 3 months", "the last 6 months", "the last year", "all time") / TR "%1$s, %4$s içinde %2$s değerinden %3$s değerine". Range chips via `RfChoiceChips`. Thumbnails via Coil from `PhotoStore.fileFor`.

- [ ] **Step 1: Write the failing tests**

`MeasurementDetailViewModelTest`: `the range limits the points` (entries at -1, -4, -13 months; THREE → 1 point, YEAR → 2, ALL → 3); `entries are newest first`; `a type with no entries has no points`.
`MeasurementDetailComposeTest`: `tapping an entry opens its check-in` (callback receives the id).
Goldens en/tr/en-2x/tr-2x for detail (with eight points) and en/tr-2x for the list; accessibility on both.

- [ ] **Step 2: Run and watch them fail.** Run: `./gradlew :feature:body:testDebugUnitTest --tests "*MeasurementDetail*" --tests "*CheckInList*"` Expected: compilation fails.
- [ ] **Step 3: Implement both screens, view models, strings, destinations**
- [ ] **Step 4: Record and inspect goldens; run the module's tests.** Expected: PASS.
- [ ] **Step 5: Commit** — `git commit -m "Chart each measurement and list every check-in"`

---

### Task 10: Compare photos

**Files:**
- Create: `ComparePhotosScreen.kt`, `ComparePhotosViewModel.kt` in `feature/body/...`
- Modify: strings, `RepForthNavHost.kt` (`BodyCompare`)
- Test: `ComparePhotosViewModelTest.kt`, `ComparePhotosScreenshotTest.kt`, `ComparePhotosAccessibilityTest.kt`

**Interfaces:**
- Produces: `ComparePhotosRoute()`, `data class CompareSide(val date: LocalDate, val fileName: String, val weightKg: Double?)`, `data class ComparePhotosUiState(val pose: Pose, val dates: List<LocalDate>, val left: CompareSide?, val right: CompareSide?)`; `onPose(Pose)`, `onLeftDate(LocalDate)`, `onRightDate(LocalDate)`.

Defaults: earliest and latest check-ins holding that pose. A pose with fewer than two such check-ins shows EN "Take this pose on two check-ins to compare them." / TR "Karşılaştırmak için bu pozu iki kayıtta çek." Pose switch via `RfSegmentedChoice`. Dates chosen from a menu of `dates`. Tapping a photo opens a full-screen `Dialog` with the image fitted and a close button. Each side's caption: date, and weight via `formatMeasurement` when recorded that day. Images `contentDescription`: EN "%1$s photo, %2$s" (pose, date).

- [ ] **Step 1: Write the failing tests**: `defaults to the earliest and latest with that pose`; `switching pose re-picks both sides`; `one photo of a pose cannot be compared` (both sides null, `dates.size == 1`); `the caption carries that day's weight`. Goldens en/tr/en-2x/tr-2x; accessibility.
- [ ] **Step 2: Run and watch them fail.** Run: `./gradlew :feature:body:testDebugUnitTest --tests "*ComparePhotos*"` Expected: compilation fails.
- [ ] **Step 3: Implement**
- [ ] **Step 4: Record and inspect goldens; run the module's tests.** Expected: PASS.
- [ ] **Step 5: Commit** — `git commit -m "Compare two progress photos of the same pose"`

---

### Task 11: Choose what to track

**Files:**
- Create: `TrackingScreen.kt`, `TrackingViewModel.kt` in `feature/body/...`
- Modify: strings, `RepForthNavHost.kt` (`BodyTracking`)
- Test: `TrackingViewModelTest.kt`, `TrackingComposeTest.kt`, `TrackingScreenshotTest.kt`, `TrackingAccessibilityTest.kt`

**Interfaces:**
- Produces: `TrackingRoute()`, `data class TrackingUiState(val types: List<MeasurementType>, val pendingDelete: PendingDelete? = null)`, `data class PendingDelete(val type: MeasurementType, val entries: Int)`; `onToggle(typeId, tracked)`, `onAdd(name: String, kind: MeasurementKind, unitLabel: String?)`, `onRename(typeId, name)`, `onAskDelete(typeId)`, `onConfirmDelete()`, `onCancelDelete()`.

Built-ins: a switch row each (`Role.Switch`). Custom: switch, rename and delete actions. **Add custom measurement** opens a dialog: name (required, trimmed, max 40 characters), kind via `RfChoiceRows` (Length / Weight / Percent / Number), and a unit label field shown only for Number (required, max 10). Delete confirm: EN "Delete %1$s? Its %2$d entries go with it." (plural resource) / TR "%1$s silinsin mi? %2$d kaydı da silinir."

- [ ] **Step 1: Write the failing tests**: `toggling a type persists it`; `a custom type is added after the built-ins`; `a blank name is refused`; `a number kind needs a unit label`; `delete asks with the entry count, then removes it`; `built-ins offer no delete` (compose test: no delete action on "Weight"). Goldens en/tr/en-2x/tr-2x; accessibility.
- [ ] **Step 2: Run and watch them fail.** Run: `./gradlew :feature:body:testDebugUnitTest --tests "*Tracking*"` Expected: compilation fails.
- [ ] **Step 3: Implement**
- [ ] **Step 4: Record and inspect goldens; run the module's tests.** Expected: PASS.
- [ ] **Step 5: Commit** — `git commit -m "Let people choose which measurements to track and add their own"`

---

### Task 12: Device test, docs, full verification

**Files:**
- Create: `app/src/androidTest/java/com/repforth/app/BodyFlowTest.kt`
- Modify: `app/src/androidTest/java/com/repforth/app/AppText.kt` (strings it needs, read from resources), `PROJECT_GUIDELINE.md` (§3 Phone MVP bullet, §7 body tables and photo storage, §12 Progress line, §14 photos stay on device and no camera permission), `docs/PLAN.md` (new section and status row), `PRIVACY.md` (body data paragraph), `AGENTS.md` (icon count 53 → 56; golden count from `git ls-files "*src/test/screenshots/*.png" | wc -l`)

- [ ] **Step 1: Write the failing instrumentation test**

`BodyFlowTest.checkInShowsOnTheBodyTab`: open Progress, tap Body, tap Check in, type `86` into the Waist field, Save, wait until a node with text containing `86` is displayed under Waist on the Body tab.

- [ ] **Step 2: Run it**

Run: `./gradlew --stop` then `./gradlew :app:pixel6Api34PlaceholderDebugAndroidTest`
Expected: all `:app` instrumentation tests pass. Before believing a failure, check free memory and the emulator log order (AGENTS.md); repeat a failing run rather than trusting one.

- [ ] **Step 3: Update the docs** — record the rulings above in `docs/PLAN.md`'s new section, with test counts measured from the reports, and state that hardware is not yet verified.

- [ ] **Step 4: Full verification, three separate invocations**

Run: `./gradlew assemblePlaceholderDebug`, then `./gradlew test`, then `./gradlew lint`
Expected: each succeeds; record the unit-test total.

- [ ] **Step 5: Commit, push, open the PR**

`git commit -m "Prove a body check-in reaches the Progress tab, and record body tracking"`, `git push -u origin body-tracking`, `gh pr create` with a plain-language body (what it does, how photos are handled, verification, "not yet verified on hardware"), then `gh pr checks --watch`.

---

### Task 13: Hardware check on the Galaxy S23 (with the owner)

Not automatable; do not merge before it.

- [ ] Build `./gradlew :app:assemblePlaceholderRelease`; confirm `apksigner verify --print-certs` shows `CN=RepForth` and the APK holds a string only this branch has (`aapt2 dump strings … | grep "Choose what to track"`).
- [ ] Confirm no workout is running (`adb shell pidof com.repforth` empty or no `WorkoutService`), then `adb install -r --user 0 …`, `adb logcat -b crash -c`.
- [ ] Owner: check in with weight and waist plus a **front photo taken with Samsung's camera app** — it shows upright; a second check-in on another date with a front photo; Compare shows both; the waist chart shows two points; Settings → Export, then import the same file — measurements intact, photos still present.
- [ ] Wait ≥ 30 s, then `adb logcat -b crash -d` (empty) and `adb shell pidof com.repforth` (a pid).
- [ ] Record the result in `docs/PLAN.md`, commit, push, wait for CI, and merge only when the owner says so.
