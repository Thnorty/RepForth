# Privacy

This describes what the code **currently does**, not what it intends to do. It is
updated when behaviour changes, and the phase markers say what has not been built
yet. If a claim here cannot be checked against the source, it should not be here.

## As of Phase 0

**The app makes no network requests.** Not for analytics, not for crash
reporting, not for updates, not for media. There is no networking library in the
dependency graph. The exercise catalog is a database of roughly 2.6 MB compiled into
the APK, so browsing it works in aeroplane mode on first launch.

**There is no account and no server.** Nothing to sign into, nothing to sign into
it with.

**Nothing is collected.** No identifiers, no usage data, no telemetry of any kind.

### What is stored on the device

| What | Where | Contains |
|---|---|---|
| Exercise catalog | `core/database/src/main/assets/repforth.db`, read-only | The bundled 1,324 exercises |
| Preferences | Preferences DataStore | Theme, language override, units, keep-screen-on, reduced motion, haptics, onboarding-complete |

That is the complete list today. Workout history does not exist yet.

## What changes in later phases

Stated now so the direction is not a surprise.

**Phase 1 — workout history.** Sets, weights and sessions are written to the same
on-device database. They stay there. Export produces a file you choose the
destination of; deletion is real deletion, not a flag.

**Phase 2 — AI, and only if you turn it on.** RepForth has no AI service. If you
configure a provider and supply your own key, then and only then:

- Your generation request — muscles, equipment, exclusions, session length —
  is sent **to that provider**, under **their** terms and **their** privacy
  policy. Not to us; there is no us in the request path.
- Your API key is encrypted with the Android Keystore. It must never appear in
  the database, in ordinary preferences, in logs, in exports, in backups, or in
  any message to the watch. That is a requirement with tests attached, not an
  aspiration.
- The manual builder, sessions, history, and progress remain fully useful with
  no provider configured. Coach generation requires the provider you selected;
  a provider failure never creates a substitute plan locally.

**Phase 3 — media.** Exercise images are fetched from a pinned GitHub commit
when first shown, and cached on the device. **This happens in every build,
including the default one.** An earlier version of this page said the default
flavour performed no network I/O at all; that was never true of the app as
built, and saying so here was the worst place to get it wrong.

What that means for you, stated plainly:

- The host — `raw.githubusercontent.com` — sees your IP address and which
  exercise images you asked for, at the time you asked. That is a weak but real
  signal about what you are training. It goes to GitHub, under **their** terms.
  There is still no us in the request path: no account, no identifier we
  attach, nothing about your sets, weights or history leaves the device.
- Nothing is sent — only fetched. A request says "give me image 0025", never
  what you did with it.
- You can bound it. Settings lists both network destinations the app can reach,
  can restrict media to Wi-Fi, shows the cache size, and can clear it. An
  exercise whose image is not cached simply shows an icon and its full text
  instructions, which is the whole screen minus the picture.

See [`NOTICE.md`](NOTICE.md) for who owns that imagery, which is a separate
question from this one and a sharper one.

**Phase 5 — the watch.** Phone and watch exchange workout state directly over the
Wear Data Layer. That traffic stays between your paired devices.

## Body tracking

Measurements you record -- weight, body fat, tape measurements, and any you
add yourself -- go into the same on-device database as your workouts, and are
part of the JSON export. **Progress photos are handled differently, and are
never part of the export:**

- They come from your phone's own camera app, or from your gallery through the
  system photo picker. RepForth holds **no camera permission and no storage
  permission**. The camera app writes one file into a folder only RepForth can
  read, through a `FileProvider` limited to that folder.
- Each photo is decoded, turned upright, shrunk to at most 2048 px on its long
  edge, and saved as a new JPEG. That copy carries **no EXIF data at all** --
  no location, no camera model, no timestamp -- whatever the original held. A
  photo picked from your gallery is copied; the original is not touched.
- The copy lives in the app's private storage (`body-photos`). It is not sent
  anywhere, and it is not in the export, so moving to a new phone means
  carrying the photos across yourself.
- A photo taken but never saved to a check-in is deleted within a day. A stored
  photo that no check-in refers to is deleted the next time the Body tab opens.
- **Reset app** deletes measurements and photos. **Delete all workout data**
  leaves them alone: it is about workouts.

## Android Auto Backup

Off. `android:allowBackup="false"` in the manifest, held there by
`BackupPolicyTest`. Nothing this app stores is copied to Google's servers, which
is what makes the rest of this page true rather than merely intended — the
default is `true`, and the default would have uploaded the workout database.

That also removes the question of what to do about encrypted key material when
Phase 2 adds it: it cannot leak through a channel that is closed.

The cost is that a new phone does not inherit your history automatically. The
replacement is the versioned JSON export in §7 — deliberately manual, because a
copy you made is a copy you know about.

One practical consequence, found on a device rather than reasoned about: while
backup was on, reinstalling restored the old database over a schema Room had
already moved past, and the app died on launch. Auto Backup restores across
schema versions; Room refuses to open across them.

## Verifying any of this

Every claim above is meant to be checkable rather than believed:

```
./gradlew :app:dependencies
```

No HTTP client appears in the Phase 0 graph. The manifest requests no
`INTERNET` permission. Both are things you can confirm without trusting this
document.
