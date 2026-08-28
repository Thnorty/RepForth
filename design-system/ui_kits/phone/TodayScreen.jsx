const DS_T = window.RepForthDesignSystem_c95a40;

function TodayScreen({ lang, go, startSession, session }) {
  const { TopAppBar, PlanCard, Button, Card, StatBlock, Icon, ProgressBar, Divider, Badge } = DS_T;
  const plan = PLANS[0];
  return (
    <div className="rf-scroll" style={{ flex:1, minHeight:0 }}>
      <TopAppBar large title={t(lang,"today")} subtitle={t(lang,"week")}
        actions={[{ icon:"search", label:t(lang,"search"), onClick:()=>go("catalog") }, { icon:"settings", label:t(lang,"settingsTitle"), onClick:()=>go("settings") }]} />
      <div style={{ padding:"0 var(--gutter-phone) 24px", display:"flex", flexDirection:"column", gap:"var(--section-gap)" }}>
        {session ? (
          <Card size="lg" variant="elevated" style={{ display:"flex", flexDirection:"column", gap:12 }}>
            <div style={{ display:"flex", alignItems:"center", gap:8 }}>
              <Badge tone="accent" icon="bolt" label={lang==="en"?"In progress":"Devam ediyor"} />
              <span className="rf-label">{exName(lang, plan)}</span>
            </div>
            <ProgressBar segmented value={session.doneCount} total={session.totalSets} current={session.doneCount} label={t(lang,"sets")} />
            <Button variant="filled" size="lg" icon="play_arrow" fullWidth onClick={()=>go("session")}>{t(lang,"resume")}</Button>
          </Card>
        ) : (
          <PlanCard today badge={t(lang,"today")} name={exName(lang, plan)} exercises={plan.ids.length} exercisesLabel={t(lang,"exercises")} minutes={plan.minutes} minutesLabel={t(lang,"min")}
            muscles={["chest","shoulders","triceps"].map(k=>({ id:k, label:MUSCLE[k][lang], icon:MUSCLE[k].icon }))}
            action={<Button variant="filled" size="session" icon="play_arrow" fullWidth onClick={startSession}>{t(lang,"start")}</Button>} />
        )}

        <div style={{ display:"flex", flexDirection:"column", gap:12 }}>
          <span className="rf-overline">{t(lang,"progress")}</span>
          <Card style={{ display:"flex", alignItems:"center", justifyContent:"space-between" }}>
            <StatBlock value="14 820" unit="kg" label={t(lang,"volume")} size="sm" />
            <Divider vertical />
            <StatBlock value="3" label={t(lang,"prs")} size="sm" tone="accent" />
            <Divider vertical />
            <StatBlock value="6" label={t(lang,"streak")} size="sm" />
          </Card>
        </div>

        <div style={{ display:"flex", flexDirection:"column", gap:12 }}>
          <span className="rf-overline">{t(lang,"plans")}</span>
          {PLANS.slice(1).map(p => (
            <PlanCard key={p.id} name={exName(lang,p)} exercises={p.ids.length} exercisesLabel={t(lang,"exercises")} minutes={p.minutes} minutesLabel={t(lang,"min")}
              muscles={p.ids.map(id=>byId(id).muscle).map(k=>({ id:k, label:MUSCLE[k][lang], icon:MUSCLE[k].icon }))}
              onClick={()=>go("builder")} />
          ))}
          <Button variant="tonal" icon="auto_awesome" fullWidth onClick={()=>go("builder")}>{t(lang,"generate")}</Button>
        </div>

        <div style={{ display:"flex", gap:8, alignItems:"flex-start", color:"var(--text-quiet)", fontSize:"var(--label-md)", lineHeight:1.5 }}>
          <Icon name="phone_android" size={16} /><span>{t(lang,"local")}</span>
        </div>
      </div>
    </div>
  );
}

Object.assign(window, { TodayScreen });
