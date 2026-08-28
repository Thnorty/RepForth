Set log row for the active session. Numbers are the hero; the 48dp check is the only action.

```jsx
<SetRowHeader weightLabel="Ağırlık" repsLabel="Tekrar" />
<SetRow index={1} weight={82.5} reps={8} previous="Last: 80 kg × 8" done onToggle={t} />
<SetRow index={2} weight={82.5} reps={8} active onToggle={t} />
```
