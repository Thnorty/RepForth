The watch canvas. Compose `WearScreen > WearArc? > WearBody > WearValue / WearAction`.

```jsx
<WearScreen shape="round" size={208}>
  <WearArc value={0.7} tone="rest" />
  <WearBody>
    <span className="rf-wear__title">Rest</span>
    <WearValue value="0:42" caption="Bench press · Set 3" />
  </WearBody>
</WearScreen>
<WearScreen shape="square" ambient size={192}>…</WearScreen>
```

Rules: one number per screen, never more than two actions, nothing below 52dp, and ambient mode drops every fill.
