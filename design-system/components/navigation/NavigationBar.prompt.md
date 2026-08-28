Bottom nav for the phone app's four destinations.

```jsx
<NavigationBar value={tab} onChange={setTab} items={[
  {value:"today",icon:"today",label:"Today"},
  {value:"plans",icon:"list_alt",label:"Plans"},
  {value:"catalog",icon:"fitness_center",label:"Exercises"},
  {value:"you",icon:"insights",label:"Progress"}]} />
```

Labels are always visible (Turkish included); truncate with ellipsis rather than hiding them.
