Weight / reps / rest adjuster. Use `size="session"` anywhere it is tapped during a workout.

```jsx
<Stepper label="Weight" value={82.5} unit="kg" step={2.5} onChange={setW} size="session" />
<Stepper label="Reps" value={10} step={1} onChange={setR} />
```

The value is `aria-live="polite"` so screen readers announce changes without moving focus.
