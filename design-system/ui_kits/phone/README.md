# Phone UI kit — RepForth for Android

A click-through recreation of the phone app at 412 × 892 dp (edge-to-edge, phone-only —
no tablet layouts). Open `index.html`.

## Flow
Today → **Start workout** → active session (log sets, weight/reps steppers) → rest countdown
(ring + 64dp controls) → next exercise → finish. Bottom nav reaches Exercises (catalog with
muscle/equipment filters) → exercise detail (1:1 media slot, how-to / history / records) and
the builder (manual, or an AI draft sent to the provider you configure). Settings toggles
**language (EN/TR)** and **theme (dark/light)** live — both are the point of the kit, not
decoration.

## Files
| File | What it is |
| --- | --- |
| `index.html` | Mounts the kit; loads `styles.css` + `_ds_bundle.js` |
| `app.jsx` | State, routing, bottom nav, FAB |
| `PhoneFrame.jsx` | Device frame + status bar |
| `TodayScreen.jsx` | Home: next session, resume card, weekly numbers, saved plans |
| `CatalogScreen.jsx` | Search + filter chips over the exercise catalog |
| `ExerciseDetailScreen.jsx` | Media slot, prescription, tabs (how-to / history / records) |
| `SessionScreen.jsx` | Active workout: set rows, session steppers, rest timer, discard dialog |
| `BuilderScreen.jsx` | Manual builder and AI draft, bottom-sheet picker |
| `SettingsScreen.jsx` | Language, theme, in-workout options, watch, export, source |
| `data.jsx` | Sample exercises/plans and the full EN + TR string table |

## Known placeholders
Exercise media is an empty 1:1 slot with an icon — no photography or illustration was supplied
with the brief. Drop real 1:1 assets into `assets/media/` and swap the slot for
`<img className="rf-media-square">`. The catalog shows 11 sample exercises standing in for 1,324.
