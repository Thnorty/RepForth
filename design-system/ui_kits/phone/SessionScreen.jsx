const DS_S = window.RepForthDesignSystem_c95a40;

function SessionScreen({ lang, session, setSession, exit, finish }) {
  const { TopAppBar, SetRow, SetRowHeader, Stepper, Button, IconButton, Card, StatBlock, ProgressBar, Chip, Icon, Dialog, Snackbar, RestTimer, Divider } = DS_S;
  const plan = PLANS[0];
  const ex = byId(plan.ids[session.exIndex]);
  const m = MUSCLE[ex.muscle], q = EQUIP[ex.equip];
  const [ask, setAsk] = React.useState(false);
  const [toast, setToast] = React.useState(false);

  React.useEffect(() => {
    if (!session.resting) return;
    const id = setInterval(() => setSession(s => s.resting ? (s.restLeft <= 1 ? { ...s, resting:false, restLeft:s.restTotal } : { ...s, restLeft:s.restLeft - 1 }) : s), 1000);
    return () => clearInterval(id);
  }, [session.resting, setSession]);

  const toggleSet = (i) => setSession(s => {
    const done = { ...s.done, [i]: !s.done[i] };
    const justDone = !s.done[i];
    return { ...s, done, resting: justDone ? true : s.resting, restLeft: justDone ? s.restTotal : s.restLeft };
  });
  const doneCount = Object.values(session.done).filter(Boolean).length;

  if (session.resting) {
    const next = exName(lang, ex) + " · " + t(lang,"set") + " " + Math.min(ex.sets, doneCount + 1);
    return (
      <div style={{ flex:1, minHeight:0, display:"flex", flexDirection:"column", background:"var(--color-surface)" }}>
        <TopAppBar leadingIcon="close" onLeading={()=>setSession(s=>({ ...s, resting:false }))} title={t(lang,"rest")} />
        <div style={{ flex:1, display:"flex", flexDirection:"column", justifyContent:"center" }}>
          <RestTimer remaining={session.restLeft} total={session.restTotal} size={260} label={t(lang,"rest")} skipLabel={t(lang,"skipRest")}
            nextUp={t(lang,"nextUp") + ": " + next}
            onSkip={()=>setSession(s=>({ ...s, resting:false, restLeft:s.restTotal }))}
            onAdd={()=>setSession(s=>({ ...s, restLeft:s.restLeft + 15, restTotal:Math.max(s.restTotal, s.restLeft + 15) }))}
            onSubtract={()=>setSession(s=>({ ...s, restLeft:Math.max(0, s.restLeft - 15) }))} />
        </div>
        <div style={{ padding:"0 var(--gutter-phone) 24px", display:"flex", gap:8, alignItems:"center", justifyContent:"center", color:"var(--text-quiet)" }}>
          <Icon name="watch" size={16} /><span className="rf-label">{lang==="en" ? "Mirrored on your watch" : "Saatinde de görünüyor"}</span>
        </div>
      </div>
    );
  }

  return (
    <div style={{ flex:1, minHeight:0, display:"flex", flexDirection:"column", position:"relative" }}>
      <TopAppBar leadingIcon="close" onLeading={()=>setAsk(true)} title={exName(lang, plan)} subtitle={(session.exIndex+1) + " / " + plan.ids.length + " · " + t(lang,"exercises")}
        actions={[{ icon:"watch", label:t(lang,"watch") }, { icon:"more_vert", label:"More" }]} />
      <div className="rf-scroll" style={{ flex:1, minHeight:0, padding:"0 var(--gutter-phone) 16px", display:"flex", flexDirection:"column", gap:16 }}>
        <ProgressBar segmented value={doneCount} total={ex.sets} current={doneCount} label={t(lang,"sets")} />
        <div style={{ display:"flex", gap:12, alignItems:"center" }}>
          <span className="rf-ex__media" style={{ width:64 }}><Icon name="fitness_center" size={26} /></span>
          <div style={{ flex:1, minWidth:0, display:"flex", flexDirection:"column", gap:6 }}>
            <span style={{ fontSize:"var(--title-lg)", fontWeight:700, color:"var(--text-strong)", lineHeight:1.2 }}>{exName(lang,ex)}</span>
            <div style={{ display:"flex", gap:6, flexWrap:"wrap" }}>
              <Chip icon={m.icon} label={m[lang]} size="sm" /><Chip icon={q.icon} label={q[lang]} size="sm" />
            </div>
          </div>
        </div>

        <Card size="lg" style={{ display:"flex", flexDirection:"column", gap:16 }}>
          <div style={{ display:"flex", alignItems:"flex-end", justifyContent:"space-between", gap:12 }}>
            <StatBlock value={session.weight} unit="kg" label={t(lang,"weight")} size="xl" />
            <StatBlock value={session.reps} label={t(lang,"reps")} size="lg" align="center" />
          </div>
          <Divider />
          <div style={{ display:"flex", flexDirection:"column", gap:10 }}>
            <span className="rf-label">{t(lang,"weight")}</span>
            <Stepper size="session" label={t(lang,"weight")} value={session.weight} unit="kg" step={2.5} onChange={w=>setSession(s=>({ ...s, weight:w }))} />
            <span className="rf-label">{t(lang,"reps")}</span>
            <Stepper size="session" label={t(lang,"reps")} value={session.reps} step={1} onChange={r=>setSession(s=>({ ...s, reps:r }))} />
          </div>
          <Button variant="filled" size="session" icon="check" fullWidth onClick={()=>{ toggleSet(doneCount+1); setToast(true); setTimeout(()=>setToast(false), 2600); }}>
            {t(lang,"logSet")}
          </Button>
        </Card>

        <div style={{ background:"var(--surface-card)", borderRadius:"var(--radius-card)", padding:"12px 8px" }}>
          <SetRowHeader weightLabel={t(lang,"weight")} repsLabel={t(lang,"reps")} />
          {Array.from({ length: ex.sets }, (_, i) => i + 1).map(i => (
            <SetRow key={i} index={i} weight={session.weight} reps={session.reps}
              previous={t(lang,"lastTime") + ": " + (ex.weight - 2.5) + " kg × " + ex.reps}
              done={!!session.done[i]} active={!session.done[i] && i === doneCount + 1}
              onToggle={()=>toggleSet(i)} />
          ))}
        </div>

        <div style={{ display:"flex", gap:10 }}>
          <Button variant="tonal" icon="arrow_back" onClick={()=>setSession(s=>({ ...s, exIndex:Math.max(0, s.exIndex-1), done:{} }))}>{lang==="en"?"Previous":"Önceki"}</Button>
          <Button variant="tonal" trailingIcon="arrow_forward" fullWidth onClick={()=>setSession(s=>({ ...s, exIndex:Math.min(plan.ids.length-1, s.exIndex+1), done:{} }))}>{lang==="en"?"Next exercise":"Sonraki egzersiz"}</Button>
        </div>
        <Button variant="outlined" icon="flag" fullWidth onClick={finish}>{t(lang,"finish")}</Button>
      </div>

      {toast ? (
        <div style={{ position:"absolute", left:12, right:12, bottom:16, zIndex:30 }}>
          <Snackbar icon="check_circle" message={t(lang,"setLogged")} actionLabel={t(lang,"undo")} onAction={()=>setToast(false)} />
        </div>
      ) : null}
      <Dialog open={ask} title={t(lang,"discard")} onDismiss={()=>setAsk(false)}
        actions={<><Button variant="text" onClick={()=>setAsk(false)}>{t(lang,"keepGoing")}</Button><Button variant="danger" onClick={()=>{ setAsk(false); exit(); }}>{t(lang,"discardYes")}</Button></>}>
        {t(lang,"discardBody")}
      </Dialog>
    </div>
  );
}

Object.assign(window, { SessionScreen });
