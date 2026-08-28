Confirmations and pickers.

```jsx
<Dialog open={ask} title="Discard this workout?" onDismiss={close}
  actions={<><Button variant="text" onClick={close}>Keep going</Button><Button variant="danger" onClick={discard}>Discard</Button></>}>
  Logged sets stay on this phone. Nothing is uploaded.
</Dialog>
<Dialog sheet open={pick} title="Rest length" onDismiss={close}>…</Dialog>
```
