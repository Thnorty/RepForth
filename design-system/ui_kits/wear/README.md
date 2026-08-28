# Wear OS UI kit — RepForth companion

Three watch canvases side by side: **round 1.3"**, **square 1.2"**, and the same round face in
**ambient (always-on)** rendering. Open `index.html` and switch screen / language at the top.

## What the watch is
A remote for the workout running on the phone — it never owns the session. Consequences the kit
demonstrates:

- One number per screen, set in the numeric face, sized for a glance at arm's length.
- One primary action (56dp) per screen: confirm the set, or skip rest. Adjusting weight, reps or rest length stays on the phone — a round face has no room for a control row that clears the progress arc. List rows 52dp.
- Lists scroll with the rotary bezel/crown; rows truncate to one line rather than wrapping.
- Ambient drops every fill: black background, dim monochrome text, arcs become outlines, actions hide.
- Progress lives on an edge-hugging arc, not a bar — the bezel is the only free space on a watch.

## Files
| File | What it is |
| --- | --- |
| `index.html` | Mounts the kit with screen / language controls |
| `WatchFrame.jsx` | Round and square device bezels |
| `WearRemote.jsx` | The three screens: set remote, rest countdown, exercise list |
