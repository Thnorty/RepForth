Icon-only control with a real touch target (48dp min, 64dp in-session).

```jsx
<IconButton icon="more_vert" label="Workout options" />
<IconButton icon="skip_next" label="Skip rest" variant="tonal" size="session" />
<IconButton icon="bookmark" label="Save exercise" selected={saved} onClick={toggle} />
```

Passing `selected` turns it into a toggle (aria-pressed + filled glyph, so state is not colour-only).
