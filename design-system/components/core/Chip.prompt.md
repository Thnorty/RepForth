Filter / metadata chip. Icon + text always; selection adds a check so state is not colour-only.

```jsx
<Chip icon="fitness_center" label="Dumbbell" count={214} selected={on} onClick={toggle} />
<Chip icon="accessibility_new" label="Chest" size="sm" />
```

- `size="md"` (48dp) for anything tappable; `sm` only for read-only tags inside cards.
- With no `onClick` and no `selected` it renders a `<span>` — safe to nest inside interactive cards and rows.
- Turkish labels run long — let chip rows wrap, never horizontally clip.
