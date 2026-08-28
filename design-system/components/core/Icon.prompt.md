Material Symbols Rounded glyph wrapper — use it for every icon; never hand-roll an SVG.

```jsx
<Icon name="fitness_center" size={24} />
<Icon name="check_circle" size={20} fill color="var(--color-primary)" label="Set complete" />
```

- `fill` marks active/selected only (nav item, completed set).
- `label` makes it an image with an accessible name; leave it off for decoration next to text.
- Icon + text always travel together for filters and states — RepForth never encodes meaning in colour alone.
