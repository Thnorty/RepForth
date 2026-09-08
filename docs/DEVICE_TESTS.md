# What still needs a person, a phone and a watch

Everything below is shipped, compiles, and passes on the JVM. **None of it has
run on hardware.** That is not a hedge — this repo has a documented history of
changes that passed CI, merged, installed, and did nothing on the device (`U.1`
in `docs/PLAN.md`), and the whole Wear feature set built between 2026-09-07 and
2026-09-08 has never touched a wrist.

This file is the list of things a test suite structurally cannot answer, and what
each one should look like when it works. Tick them off in whatever order suits;
the setup sections say what you need first.

Keep it current: when something here gets a test that genuinely covers it,
delete the entry rather than leaving it ticked.

---

## Before you start

**Which phone.** The Galaxy S23 is the one paired to the watch. The Xiaomi has
never been paired, and there `Wearable.API` is not idle but *absent* — every
publish fails with `ApiException: 17 API_UNAVAILABLE`. Anything on this page
involving the watch needs the Galaxy.

**Check what you are on:**

```
adb shell getprop ro.product.manufacturer
adb shell getprop ro.build.version.sdk
```

On the Galaxy, adb defaults to **user 150**, so installs need `--user 0`:

```
adb install -r --user 0 app/build/outputs/apk/placeholder/debug/app-placeholder-debug.apk
```

**The watch drops off wireless adb constantly** — it goes offline the moment the
screen sleeps and comes back on a *new* port. Put it on its charger and turn on
**Stay awake while charging** in its developer options first, or the session will
be three reconnections long.

**Install before starting a workout, not during one.** Reinstalling the phone app
kills `WorkoutService` with the process, and nothing restarts it: the session is
still in the database, but it takes a resume from the UI to bring publishing
back.

**Confirm the link is real** before believing any negative result:

```
adb shell dumpsys activity service WearableService
```

It should name the watch with `IsConnected=true`.

---

## 1. Timed sets (#41, #43)

The clock ends a timed set; there is no way to complete one by hand. Build a plan
with a duration-based exercise — a plank, or anything the builder lets you set
seconds on.

- [ ] **The set starts on arrival.** Reaching a timed exercise begins the
      countdown with nothing to press. The big number counts *down*, and is not
      the prescription sitting still.
- [ ] **There is no "Log set".** The phone offers Skip set and Pause, and nothing
      that claims to complete the set.
- [ ] **The clock records the full duration.** Let it run out. History should show
      the prescribed seconds, not a shorter number.
- [ ] **Stopping early is a skip.** Skip at ~40s of a 60s hold. History shows a
      skipped set with no duration — *not* a 40-second set.
- [ ] **A pause costs the set nothing.** Pause at 20s, leave it ten minutes,
      resume. It should still owe 40s.
- [ ] **It survives the process dying.** Start a timed set, force-stop the app,
      reopen. The countdown resumes from where the wall clock says it should be.
- [ ] **A deadline that passed while dead comes back finished.** Start a 60s hold,
      force-stop, wait two minutes, reopen. The set should be recorded at its full
      length rather than restarting.

---

## 2. Both timers make a noise (#40, #43)

- [ ] **Rest ending buzzes and beeps** with the phone face-down on a bench and the
      screen off. This is the case it exists for.
- [ ] **A timed set ending does the same.**
- [ ] **Skipping a rest is silent.** You did that on purpose; the phone should not
      announce it.
- [ ] **The sound is on the media stream.** Play music, then let a rest end — the
      beep should mix with the music rather than duck it or come out of the alarm
      channel at alarm volume. This was changed from the alarm stream on request
      and has never been heard.
- [ ] **Both switches work independently.** Sound off, vibration on → buzz only.
      Vibration off, sound on → beep only. Both off → nothing at all.

---

## 3. The watch, first light

Everything in this section has only ever run in Robolectric.

- [ ] **A workout on the phone appears on the wrist**, with the exercise name and
      set count.
- [ ] **The exercise thumbnail appears** (#47). This is the newest and least
      proven path — bytes cross as a Data Layer `Asset`. If it does not appear,
      check `adb logcat -s WearBridge` for `thumbnail 0 bytes`, which means the
      phone had nothing cached to send rather than the transfer failing.
- [ ] **The attribution appears with it.** `© Gym visual — https://gymvisual.com/`
      below the buttons, reachable by scrolling. Required by the licence wherever
      the imagery is shown — if the picture is there and the notice is not, that is
      a licence problem, not a cosmetic one.
- [ ] **No picture means no notice.** An exercise whose media has not downloaded
      shows neither.
- [ ] **A timed set on the wrist counts down** and offers no Complete button.
- [ ] **The wrist buzzes when either timer reaches zero** (#43). Test with the
      watch on your wrist and the app *not* open — the alert is a message to a
      background service, and that is the case that matters.
- [ ] **Turning phone haptics off silences the watch too.** One switch governs
      both by design; confirm the watch stays quiet.

---

## 4. The watch's controls

- [ ] **Complete set** on the wrist advances the phone.
- [ ] **Skip set** records a skip.
- [ ] **Pause and Resume** both work and the label changes between them.
- [ ] **Next exercise** (#44) leaves the exercise and abandons its remaining sets.
      It is the lowest button and needs a scroll to reach — confirm the scroll
      works by touch **and by the rotating crown**, which has never been tried.
- [ ] **Next exercise on the last exercise finishes the workout** rather than
      being refused.

---

## 5. Return from the watch face (#45)

- [ ] **A chip appears on the watch face** while a workout runs, showing the
      exercise name.
- [ ] **Tapping it opens the app** on the current screen.
- [ ] **It disappears when the workout ends** — both when finished and when
      abandoned. A chip that outlives the session offers a way into a workout that
      no longer exists, and nothing on screen would show it.
- [ ] **Notifications permission.** On a watch running Android 13 or newer, the
      app asks on first launch. **Deny it once** and confirm the app still works
      and simply has no chip; `adb logcat -s WearWorkoutNotification` should say
      notifications are off.

---

## 6. Disconnection (§11, §20)

The disconnected path is the one most likely to be wrong, because reaching it is
awkward.

- [ ] **Bluetooth off is not enough.** Wear OS routes the Data Layer over Wi-Fi
      when both devices share a network. To actually disconnect, put the **phone**
      in airplane mode and keep the watch on Wi-Fi so it stays on adb.
- [ ] **The watch says "Phone not connected"** within a few seconds, without being
      touched. It polls; it should not wait for you to press something.
- [ ] **Every control is disabled**, and the last snapshot stays readable.
- [ ] **It recovers** when the phone comes back.

---

## 7. Shapes and sizes

The goldens render 240dp round, 180dp round and 200dp square. They cannot tell
you how it feels.

- [ ] **Nothing important sits under the clipped corners** on the real round
      display.
- [ ] **The buttons are actually tappable** with a sweaty or gloved finger, which
      is the condition §12 is written for.
- [ ] **200% font scale** on the watch: set the largest text size in the watch's
      accessibility settings and confirm every control is still reachable by
      scrolling rather than cut off.
- [ ] **Turkish**, on both devices, end to end.

---

## 8. Media, now that every build downloads it (#46)

- [ ] **Images appear in a plain `placeholderDebug` build.** This is the decision
      made 2026-09-08 and the documents now describe it; confirm reality agrees.
- [ ] **Wi-Fi-only is respected.** Turn it on in Settings, go to mobile data,
      open an uncached exercise. No download, no crash, icon shown.
- [ ] **Clearing the cache works** and the size in Settings drops.
- [ ] **The attribution shows on the phone's exercise detail** as well as the
      watch.

---

## 9. Things known to be missing or unverified

Not tests — open questions worth confirming on hardware before they are called
done.

- **The watch has no goldens for a paused rest**, because the paused-rest screen
  is still the exercise screen (backlog 14). On hardware, pause during a rest and
  see what the wrist actually shows; the phone shows a frozen remainder and the
  watch probably shows a set panel.
- **`BuilderFlowTest` is flaky in CI in two distinct ways** (see `docs/PLAN.md`),
  neither reproduced on hardware. If you ever see the exercise picker's search
  field stay empty on a real phone, that is the second mode and worth capturing.
- **The rest ring pauses on any device with a reduced animator scale.** Known and
  accepted; the Galaxy has `animator_duration_scale` at 0.5. Check with
  `adb shell settings get global animator_duration_scale` before reporting any
  timing observation from a device.
