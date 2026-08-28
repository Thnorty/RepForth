const DS_C = window.RepForthDesignSystem_c95a40;

function CatalogScreen({ lang, go, openExercise }) {
  const { TopAppBar, TextField, Chip, ListItem, Icon, Badge, Tabs, StatBlock } = DS_C;
  const [q, setQ] = React.useState("");
  const [filters, setFilters] = React.useState({ chest:true });
  const [tab, setTab] = React.useState("muscle");
  const keys = tab === "muscle" ? Object.keys(MUSCLE) : Object.keys(EQUIP);
  const src = tab === "muscle" ? MUSCLE : EQUIP;
  const active = Object.keys(filters).filter(k=>filters[k]);
  const list = EXERCISES.filter(e => {
    const okQ = !q || exName(lang,e).toLowerCase().includes(q.toLowerCase());
    const okF = !active.length || active.includes(e.muscle) || active.includes(e.equip);
    return okQ && okF;
  });
  const toggle = (k) => setFilters(f => ({ ...f, [k]: !f[k] }));
  return (
    <div style={{ flex:1, minHeight:0, display:"flex", flexDirection:"column" }}>
      <TopAppBar title={t(lang,"catalog")} subtitle={"1 324 " + t(lang,"exercises")} actions={[{ icon:"tune", label:t(lang,"filters") }]} />
      <div style={{ padding:"0 var(--gutter-phone) 12px", display:"flex", flexDirection:"column", gap:12 }}>
        <TextField icon="search" placeholder={t(lang,"search")} value={q} onChange={e=>setQ(e.target.value)} />
        <Tabs value={tab} onChange={setTab} items={[{ value:"muscle", label:t(lang,"muscle") }, { value:"equipment", label:t(lang,"equipment") }]} />
        <div style={{ display:"flex", gap:8, flexWrap:"wrap" }}>
          {keys.map(k => <Chip key={k} icon={src[k].icon} label={src[k][lang]} size="sm" selected={!!filters[k]} onClick={()=>toggle(k)} />)}
        </div>
      </div>
      <div className="rf-scroll" style={{ flex:1, minHeight:0, padding:"0 var(--space-2) 16px" }}>
        <div style={{ padding:"0 var(--space-2) 8px", display:"flex", alignItems:"baseline", gap:6 }}>
          <StatBlock value={list.length} size="xs" />
          <span className="rf-label">{t(lang,"exercises")}</span>
        </div>
        {list.map(e => {
          const m = MUSCLE[e.muscle], q2 = EQUIP[e.equip];
          return (
            <ListItem key={e.id} mediaIcon="fitness_center" title={exName(lang,e)}
              subtitle={<><Chip icon={m.icon} label={m[lang]} size="sm" /><Chip icon={q2.icon} label={q2[lang]} size="sm" /></>}
              trailing={e.pr ? <Badge tone="accent" icon="trending_up" label="PR" /> : null}
              trailingIcon="chevron_right" onClick={()=>openExercise(e.id)} />
          );
        })}
      </div>
    </div>
  );
}

Object.assign(window, { CatalogScreen });
