const DS_D = window.RepForthDesignSystem_c95a40;

function ExerciseDetailScreen({ lang, id, back, addToPlan }) {
  const { TopAppBar, Tabs, Chip, Card, StatBlock, Button, Icon, Divider, ProgressBar } = DS_D;
  const ex = byId(id) || EXERCISES[0];
  const m = MUSCLE[ex.muscle], q = EQUIP[ex.equip];
  const [tab, setTab] = React.useState("how");
  return (
    <div style={{ flex:1, minHeight:0, display:"flex", flexDirection:"column", animation:"rf-axis-in var(--dur-medium) var(--ease-decelerate)" }}>
      <TopAppBar leadingIcon="arrow_back" onLeading={back} title={exName(lang,ex)} subtitle={m[lang] + " · " + q[lang]}
        actions={[{ icon:"bookmark", label:"Save", selected:true }, { icon:"more_vert", label:"More" }]} />
      <div className="rf-scroll" style={{ flex:1, minHeight:0, padding:"0 var(--gutter-phone) 24px", display:"flex", flexDirection:"column", gap:16 }}>
        <div style={{ width:"100%", aspectRatio:"1 / 1", borderRadius:"var(--radius-media)", background:"var(--color-surface-container-high)",
          display:"grid", placeItems:"center", color:"var(--text-quiet)", gap:8 }}>
          <Icon name="fitness_center" size={64} />
          <span className="rf-label" style={{ textAlign:"center" }}>1:1 exercise media<br/>(asset slot — never blurred, never full-bleed)</span>
        </div>
        <div style={{ display:"flex", gap:8, flexWrap:"wrap" }}>
          <Chip icon={m.icon} label={m[lang]} size="sm" /><Chip icon={q.icon} label={q[lang]} size="sm" />
          <Chip icon="signal_cellular_alt" label={lang==="en"?"Compound":"Bileşik"} size="sm" />
        </div>
        <Card style={{ display:"flex", alignItems:"center", justifyContent:"space-between" }}>
          <StatBlock value={ex.weight || "—"} unit={ex.weight?"kg":undefined} label={t(lang,"weight")} size="md" />
          <Divider vertical />
          <StatBlock value={ex.reps} label={t(lang,"reps")} size="md" />
          <Divider vertical />
          <StatBlock value={ex.sets} label={t(lang,"sets")} size="md" tone="accent" />
        </Card>
        <Tabs value={tab} onChange={setTab} items={[{ value:"how", label:t(lang,"howTo") }, { value:"history", label:t(lang,"history") }, { value:"records", label:t(lang,"records"), count:3 }]} />
        {tab === "how" ? (
          <ol style={{ margin:0, paddingLeft:20, display:"flex", flexDirection:"column", gap:10, color:"var(--text-body)", fontSize:"var(--body-md)", lineHeight:1.5 }}>
            <li>{lang==="en" ? "Set the bar over your eyes and grip just outside shoulder width." : "Barı gözlerinin üzerine al, omuz genişliğinin biraz dışından kavra."}</li>
            <li>{lang==="en" ? "Unrack, lower to mid-chest with elbows tucked to about 45°." : "Barı çıkar, dirsekler yaklaşık 45° içte kalacak şekilde göğüs ortasına indir."}</li>
            <li>{lang==="en" ? "Press back up, keeping your feet planted and hips on the bench." : "Ayaklar sabit, kalça sehpada kalacak şekilde yukarı it."}</li>
          </ol>
        ) : tab === "history" ? (
          <div style={{ display:"flex", flexDirection:"column", gap:12 }}>
            {[["18 Aug", 82.5, 8],["11 Aug", 80, 8],["4 Aug", 80, 7]].map(([d,w,r]) => (
              <div key={d} style={{ display:"flex", alignItems:"center", justifyContent:"space-between" }}>
                <span className="rf-label">{d}</span>
                <span className="rf-numeric rf-numeric-xs">{w}<span className="rf-unit">kg</span> × {r}</span>
              </div>
            ))}
            <ProgressBar label={t(lang,"volume")} value={0.72} showValue={false} />
          </div>
        ) : (
          <Card style={{ display:"flex", alignItems:"center", gap:20 }}>
            <StatBlock value="90" unit="kg" label={lang==="en"?"Best set":"En iyi set"} size="lg" tone="accent" />
            <StatBlock value="1 320" unit="kg" label={lang==="en"?"Best volume":"En iyi hacim"} size="sm" />
          </Card>
        )}
        <Button variant="filled" size="lg" icon="add" fullWidth onClick={addToPlan}>{t(lang,"addExercise")}</Button>
      </div>
    </div>
  );
}

Object.assign(window, { ExerciseDetailScreen });
