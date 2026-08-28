The action control. One `filled` (lime) button per screen — everything else is tonal, outlined or text.

```jsx
<Button variant="filled" size="session" icon="play_arrow" fullWidth>Start workout</Button>
<Button variant="tonal" icon="auto_awesome">Generate with AI</Button>
<Button variant="text">Cancel</Button>
```

- Sizes: `sm` 40dp (secondary only), `md` 48dp default, `lg` 56dp, `session` 64dp — use `session` for anything tapped mid-set.
- Labels are sentence case, verb-first, and must survive Turkish (~35% longer): keep buttons `fullWidth` or let them wrap rather than truncate.
- `danger` is the container red, never the bright accent.
