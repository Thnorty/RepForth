Numeric hero. The figure is the loudest thing on screen; unit and label stay quiet.

```jsx
<StatBlock value="82.5" unit="kg" label="Working weight" size="lg" />
<StatBlock value="3/5" label="Sets" size="md" align="center" />
<StatBlock value="1:30" label="Rest" size="hero" tone="accent" align="center" />
```

Never shrink a figure below `--numeric-xs`; if it does not fit, cut the label instead.
