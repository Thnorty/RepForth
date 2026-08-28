const DS_B = window.RepForthDesignSystem_c95a40;

function BuilderScreen({ lang, back, openExercise }) {
  const { TopAppBar, TextField, SegmentedButtons, Slider, Chip, ExerciseCard, Button, Icon, Card, Dialog, Radio, EmptyState, FAB } = DS_B;
  const [mode, setMode] = React.useState("manual");
  const [ids, setIds] = React.useState(["bench","incline","ohp"]);
  const [rest, setRest] = React.useState(90);
  const [goal, setGoal] = React.useState("hyp");
  const [sheet, setSheet] = React.useState(false);
  const fmt = (s) => Math.floor(s/60) + ":" + String(s%60).padStart(2,"0");
  return (
    <div style={{ flex:1, minHeight:0, display:"flex", flexDirection:"column", position:"relative" }}>
      <TopAppBar leadingIcon="arrow_back" onLeading={back} title={t(lang,"builder")} actions={[{ icon:"help", label:"Help" }]} />
      <div className="rf-scroll" style={{ flex:1, minHeight:0, padding:"0 var(--gutter-phone) 24px", display:"flex", flexDirection:"column", gap:16 }}>
        <SegmentedButtons label="Mode" value={mode} onChange={setMode} options={[
          { value:"manual", label:lang==="en"?"Build it":"Kendim kurayım", icon:"edit" },
          { value:"ai", label:lang==="en"?"AI draft":"Yapay zeka", icon:"auto_awesome" }]} />
        {mode === "manual" ? (
          <React.Fragment>
            <TextField label={lang==="en"?"Workout name":"Antrenman adı"} value={lang==="en"?"Push Day B":"İt Günü B"} />
            <Slider label={t(lang,"rest")} value={rest} min={15} max={300} step={15} onChange={setRest} format={fmt} />
            <div style={{ display:"flex", flexDirection:"column", gap:10 }}>
              <span className="rf-overline">{t(lang,"exercises")}</span>
              {ids.length ? ids.map(id => {
                const e = byId(id), m = MUSCLE[e.muscle], q = EQUIP[e.equip];
                return <ExerciseCard key={id} name={exName(lang,e)} sets={e.sets} reps={e.reps}
                  tags={[{ label:m[lang], icon:m.icon }, { label:q[lang], icon:q.icon }]}
                  trailing={<span style={{ display:"flex", gap:2, color:"var(--text-quiet)" }}><Icon name="drag_handle" size={22} /></span>}
                  onClick={()=>openExercise(id)} />;
              }) : <EmptyState icon="event_note" title={lang==="en"?"No exercises yet":"Henüz egzersiz yok"} body={lang==="en"?"Add from the catalog of 1,324.":"1.324 egzersizlik katalogdan ekle."} />}
              <Button variant="tonal" icon="add" fullWidth onClick={()=>setSheet(true)}>{t(lang,"addExercise")}</Button>
            </div>
          </React.Fragment>
        ) : (
          <React.Fragment>
            <Card size="lg" style={{ display:"flex", flexDirection:"column", gap:12 }}>
              <div style={{ display:"flex", gap:8, alignItems:"center" }}>
                <Icon name="auto_awesome" size={20} color="var(--color-primary)" />
                <span style={{ fontWeight:700, color:"var(--text-strong)" }}>{t(lang,"generate")}</span>
              </div>
              <span style={{ fontSize:"var(--body-md)", color:"var(--text-quiet)", lineHeight:1.5 }}>
                {lang==="en" ? "Your request goes to the AI provider you set up. Logged sets stay on this phone." : "İsteğin, ayarladığın yapay zeka sağlayıcısına gönderilir. Kaydedilen setler bu telefonda kalır."}
              </span>
            </Card>
            <div style={{ display:"flex", flexDirection:"column", gap:4 }}>
              <span className="rf-overline">{lang==="en"?"Goal":"Hedef"}</span>
              <Radio label={lang==="en"?"Hypertrophy · 8–12 reps":"Hipertrofi · 8–12 tekrar"} checked={goal==="hyp"} onChange={()=>setGoal("hyp")} />
              <Radio label={lang==="en"?"Strength · 3–6 reps":"Kuvvet · 3–6 tekrar"} checked={goal==="str"} onChange={()=>setGoal("str")} />
              <Radio label={lang==="en"?"Endurance · 15+ reps":"Dayanıklılık · 15+ tekrar"} checked={goal==="end"} onChange={()=>setGoal("end")} />
            </div>
            <div style={{ display:"flex", gap:8, flexWrap:"wrap" }}>
              {["chest","back","legs","shoulders"].map(k => <Chip key={k} icon={MUSCLE[k].icon} label={MUSCLE[k][lang]} size="sm" selected={k==="chest"} onClick={()=>{}} />)}
            </div>
            <Button variant="filled" size="lg" icon="auto_awesome" fullWidth>{lang==="en"?"Draft workout":"Antrenmanı oluştur"}</Button>
          </React.Fragment>
        )}
        <Button variant="filled" size="lg" icon="save" fullWidth>{t(lang,"save")}</Button>
      </div>
      <Dialog sheet open={sheet} title={t(lang,"addExercise")} onDismiss={()=>setSheet(false)}>
        <div style={{ display:"flex", flexDirection:"column", gap:8 }}>
          {["fly","pushdown","lateral"].map(id => {
            const e = byId(id), m = MUSCLE[e.muscle];
            return <Button key={id} variant="outlined" fullWidth icon={m.icon} onClick={()=>{ setIds(x=>x.concat(id)); setSheet(false); }}>{exName(lang,e)}</Button>;
          })}
        </div>
      </Dialog>
    </div>
  );
}

Object.assign(window, { BuilderScreen });
