Range control; the current value is always visible in tabular numerals.

```jsx
<Slider label="Rest" value={90} min={15} max={300} step={15} format={s=>Math.floor(s/60)+":"+String(s%60).padStart(2,"0")} onChange={setRest} />
```
