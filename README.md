# RepForth

A local-first exercise planner and tracker for Android, with a Wear OS companion.
No account, no backend, no telemetry. Your training history lives on your phone
and goes nowhere else.

English and Turkish are both first-class. Neither is a translation of the other.

> **Status: 1.0.0.** Everything below works on hardware. See
> [`docs/PLAN.md`](docs/PLAN.md) for how each piece got there and what is still
> known to be rough.

## What it does

**The catalog.** 1,324 exercises packaged in the app, searchable by name and
filtered by body part, equipment and muscle, with a tappable body map beside the
labelled chips. Instructions in English and Turkish, offline from first launch.

**Planning.** Build a workout by hand, or ask Coach for one — a single session or
a whole week — from your goal, experience, equipment and the time you have. Coach
uses a provider key you supply; the manual builder needs nothing.

**Training.** A session screen for someone out of breath: the target as one large
number, one tap to log a set, rest counted down on a ring. Timed exercises end on
their own clock rather than on a button, because a plank stopped early is a skip
and not a shorter set.

**The watch.** A remote for the workout the phone is running, as pages you
swipe between: the set, the controls, and the exercise playing as an animation.
The rim carries progress through the workout during a set and the countdown
during a rest. The phone stays authoritative, so the watch can be out of date
but never wrong.

**Your data.** All of it on the device. Settings writes your profile, plans and
history to one JSON file, reads that file back, and deletes the lot on request.
The catalog stays either way.

## What is still rough

- The body map's tap targets are small on a phone. The labelled chips beside it
  are the reliable way to pick a muscle.
- Asking Coach a second time on a builder screen that has already saved a week
  replaces that week rather than adding another.
- A history row does not say how many exercises the workout held.
- Coach has returned a full week from a hosted provider, by hand. Whether a
  small local model holds the same strict schema is unmeasured.

[`docs/PLAN.md`](docs/PLAN.md) carries the rest, with the reasoning.

## Building

```
./gradlew assemblePlaceholderDebug
```

JDK 17. `compileSdk` and `targetSdk` 36; `minSdk` 28 on phone. The build needs an
Android SDK path in `local.properties`, which is not committed:

```
sdk.dir=/path/to/Android/sdk
```

There are two build flavours on the `media` dimension, `placeholder` (the
default) and `licensed`.

**They do not currently differ in what they fetch.** The dimension was added so
that the default build would carry no exercise imagery, and that is not what it
does: nothing reads the flavour at run time, so every build downloads the
upstream images on demand. This README said otherwise until it was measured.

That matters before you build this, not after: the imagery is **not** licensed to
you, and there is no flavour that avoids the question today. Read
[`NOTICE.md`](NOTICE.md) first. The exercise *text* is MIT-licensed and an
exercise is fully usable from it, so the app works with no images at all — but it
will still ask for them.

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
The exercise data is MIT-licensed. **The imagery is not**, and every flavour
fetches it — read [`NOTICE.md`](NOTICE.md) before building this.

To rebuild the packaged catalog from the pinned commit:

```
tools/fetch-dataset.sh && python tools/import-dataset.py
```

## Licence

**Not yet chosen** — a deliberate open decision, not an oversight, and the one
thing 1.0.0 does not settle. The project guideline recommends Apache-2.0 for the
explicit patent grant, but the choice belongs to the maintainer and no `LICENSE`
file has been added.

Until one exists, treat this source as all rights reserved. Third-party terms
already apply regardless: the dataset text is MIT, the fonts are SIL OFL, and the
exercise imagery is Gym visual's. See [`NOTICE.md`](NOTICE.md).

## Repository map

```
app/                  phone application, navigation shell
wear/                 the Wear OS companion
baselineprofile/      generates the committed startup profile

core/model            domain types; no Android, no storage, no serialization
core/common           the clock, so a test can move time
core/database         Room entities, DAOs, the prepackaged catalog
core/datastore        non-secret preferences
core/secrets          the provider key, and nothing else
core/designtokens     colours, type scale, dimensions: the one home for a value
core/designsystem     theme, type, fonts, shared components
core/exercise-data    the catalog repository — features depend on this, not on Room
core/user-data        profile, plans and history
core/workout          the session engine: what a set is, and when a rest ends
core/rules            what may be planned, and why a candidate was rejected
core/ai               the provider contract, the validator, the repair attempt
core/media            how a build resolves an image, for every flavour
core/transfer         the export and import file format
core/wear-protocol    the messages both apps agree on; no android.* allowed
core/wear-sync        the phone half of the bridge
core/testing          shared fakes and the accessibility assertion

feature/onboarding    the first run
feature/exercises     catalog browsing
feature/builder       the manual builder and Coach
feature/session       the live workout
feature/history       what was done, and progress over it
feature/home          today, and what is next
feature/settings      preferences, the provider, export, import, delete

build-logic/          convention plugins; module build files stay declarative
tools/                dataset import, artwork conversion, repository guards
docs/PLAN.md          what is built, what is next, and which decisions are closed
```

Contributions: see [`CONTRIBUTING.md`](CONTRIBUTING.md).
Security: see [`SECURITY.md`](SECURITY.md).
