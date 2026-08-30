# RepForth

A local-first exercise planner and tracker for Android, with a Wear OS companion.
No account, no backend, no telemetry. Your training history lives on your phone
and goes nowhere else.

English and Turkish are both first-class. Neither is a translation of the other.

> **Status: in development.** Phase 0 of six is complete — the app builds, ships
> a 1,324-exercise catalog, and browses it. Nothing logs a workout yet. See
> [`docs/PLAN.md`](docs/PLAN.md) for what is built and what is next.

## What works today

- Browse the full bundled catalog of 1,324 exercises
- Search by name, filter by body part, equipment, and muscle
- A tappable body map for choosing muscles, alongside labelled chips
- Instructions in English and Turkish, packaged offline
- Dark and light themes, with a theme preference that persists

## What does not work yet

Logging a workout, saving plans, progress history, the Wear OS companion, and the
AI coach. Those are Phases 1 to 4.

## Building

```
./gradlew assemblePlaceholderDebug
```

JDK 17. `compileSdk` and `targetSdk` 36; `minSdk` 28 on phone. The build needs an
Android SDK path in `local.properties`, which is not committed:

```
sdk.dir=/path/to/Android/sdk
```

There are two build flavours on the `media` dimension:

| Flavour | What it ships |
|---|---|
| `placeholder` *(default)* | Exercise text and metadata only. No exercise imagery. |
| `licensed` | Adds the upstream exercise imagery. **Requires your own licence** — see [`NOTICE.md`](NOTICE.md). |

`placeholder` is the default deliberately, and it is the only flavour that can be
built and distributed from this source without obtaining media rights of your
own. An exercise is fully usable from its text in that flavour.

## Bring your own AI key

RepForth has no AI service behind it. Coach calls **the provider you configure
with a key you supply** — Gemini, or any
OpenAI-compatible endpoint. There is no shared key, no proxy, and no default
provider.

That has consequences worth knowing before you enable it: your workout request
is sent to that provider under their terms and their privacy policy, and you pay
for the tokens. The manual builder, sessions, history, and progress work without
it; Coach generation itself requires the provider you configured. See
[`PRIVACY.md`](PRIVACY.md).

## The exercise dataset

The catalog comes from [hasaneyldrm/exercises-dataset](https://github.com/hasaneyldrm/exercises-dataset),
pinned to one immutable commit in [`dataset-version.toml`](dataset-version.toml).
The exercise data is MIT-licensed. **The imagery is not** — read
[`NOTICE.md`](NOTICE.md) before touching the `licensed` flavour.

To rebuild the packaged catalog from the pinned commit:

```
tools/fetch-dataset.sh && python tools/import-dataset.py
```

## Licence

**Not yet chosen** — this is a deliberate open decision, not an oversight. The
project guideline recommends Apache-2.0 for the explicit patent grant, but the
choice belongs to the maintainer and no `LICENSE` file has been added.

Until one exists, treat this source as all rights reserved. Third-party terms
already apply regardless: the dataset text is MIT, the fonts are SIL OFL, and the
exercise imagery is Gym visual's. See [`NOTICE.md`](NOTICE.md).

## Repository map

```
app/                 phone application, navigation shell
core/model           domain types; no Android, no storage, no serialization
core/database        Room entities, DAOs, the prepackaged catalog
core/datastore       non-secret preferences
core/designsystem    theme, tokens, fonts, shared components
core/exercise-data   the catalog repository — features depend on this, not on Room
feature/exercises    catalog browsing
build-logic/         convention plugins; module build files stay declarative
tools/               dataset import, artwork conversion, repository guards
docs/PLAN.md         what is built, what is next, and which decisions are closed
```

Contributions: see [`CONTRIBUTING.md`](CONTRIBUTING.md).
Security: see [`SECURITY.md`](SECURITY.md).
