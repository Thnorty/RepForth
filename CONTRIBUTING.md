# Contributing

## Getting a build

JDK 17 and an Android SDK. Put its path in `local.properties`, which is not
committed:

```
sdk.dir=/path/to/Android/sdk
```

Then:

```
./gradlew assemblePlaceholderDebug
./gradlew test
./gradlew lint
```

Build `placeholder`, not `licensed` — the licensed flavour needs media rights you
almost certainly do not have. See [`NOTICE.md`](NOTICE.md).

There is no emulator or device in the maintainer's environment, so
instrumentation and screenshot tests are **not run before merge**. If you can run
them, saying so in a pull request is genuinely useful.

## Where things go

The rule that generates most of the others: **write it once.** A value that
appears twice is a bug, not a style preference.

Paths in this table are abbreviated: `core/designsystem/theme/Color.kt` means
`core/designsystem/src/main/java/com/repforth/core/designsystem/theme/Color.kt`.
Every module follows that layout.

| Kind of thing | Its one home |
|---|---|
| Colours | `core/designsystem/theme/Color.kt` |
| Type, fonts | `core/designsystem/theme/Type.kt` |
| Spacing, sizes, radii, touch targets | `core/designsystem/theme/Dimens.kt` |
| Icons | `core/designsystem/component/RfIcons.kt` |
| Dependency and SDK versions | `gradle/libs.versions.toml` |
| Build configuration | `build-logic/convention/` |
| User-visible text | `res/values/strings.xml` **and** `values-tr/` |

A module's `build.gradle.kts` says what it *is* and what it *depends on* — never
how the toolchain is configured. That belongs in a convention plugin.

Features depend on `core:exercise-data`, never on `core:database`. A DAO in a
feature module is a storage detail escaping its module.

## Both languages, always

English and Turkish ship in lockstep. A string added to one file and not the
other is an **incomplete change**, not a follow-up — `StringParityTest` and
`TermResourceParityTest` fail the build for it.

Turkish runs 15–30% longer than English. Layouts must survive that and 200% font
scaling. Never put a fixed height on a container holding text.

## Tests

New behaviour needs a test. More importantly:

**A guard test that has never been seen to fail is not known to work.** If you
add one, break the thing it watches, watch it go red, and put it back. This has
caught two real bugs in this repository where a guard silently did not run.

**If a test reads a file at runtime**, that file must be declared as a task
input in `build-logic/convention/src/main/kotlin/GuardTestInputs.kt`. Gradle
cannot see `java.io.File` reads, so without it the task reports UP-TO-DATE and
passes on exactly the change it exists to catch. This has now happened twice;
assume it will happen again.

## Updating the pinned dataset

The upstream commit lives in exactly one place, `dataset-version.toml`.
`tools/verify-dataset-pin.sh` fails the build if it appears anywhere else. Note
that it uses `git grep`, which only sees **tracked** files — run it after
staging, or it passes and tells you nothing.

Changing the pin is its own pull request:

```
tools/fetch-dataset.sh                   # download the new commit
python tools/extract-vocabulary.py       # refresh the observed vocabulary
python tools/generate-term-labels.py     # regenerate the enum-to-label mapping
python tools/import-dataset.py           # rebuild the catalog and the manifest
./gradlew test
```

Expect failures. That is the design: a new categorical value has no Kotlin
constant and no translated display name, so `CategoricalVocabularyTest` fails
rather than the value silently becoming an unknown that drops exercises out of
every filter mentioning it. Add the constant, add both translations, re-run.

Include the diff in `dataset/import-report.json` in the pull request.

## Commits and pull requests

- Imperative subject describing the change's intent, not the file list.
- Explain *why* in the body. The diff already shows what.
- CI runs wrapper validation, a secret-hygiene check, the dataset pin check, a
  placeholder build, unit tests, and lint.

Never commit `local.properties`, an API key, a keystore, or a `.env`. The
secret-hygiene check refuses credential-shaped filenames, but it matches names,
not contents — it will not save you from pasting a key into a Kotlin file.
