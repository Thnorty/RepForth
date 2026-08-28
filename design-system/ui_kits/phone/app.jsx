const DS_A = window.RepForthDesignSystem_c95a40;

function PhoneApp() {
  const { NavigationBar, FAB } = DS_A;
  const [lang, setLang] = React.useState("en");
  const [theme, setTheme] = React.useState("dark");
  const [tab, setTab] = React.useState("today");
  const [route, setRoute] = React.useState({ name:"today" });
  const [session, setSession] = React.useState(null);

  const go = (name, params) => {
    setRoute({ name, ...params });
    if (["today","plans","catalog","progress"].includes(name)) setTab(name);
  };
  const startSession = () => {
    setSession({ exIndex:0, weight:82.5, reps:8, done:{}, resting:false, restLeft:90, restTotal:90, totalSets:4, doneCount:0 });
    go("session");
  };
  const onTab = (v) => { setTab(v); setRoute({ name: v === "progress" ? "today" : v }); };

  let screen = null;
  if (route.name === "session" && session) screen = <SessionScreen lang={lang} session={session} setSession={setSession} exit={()=>{ setSession(null); go("today"); }} finish={()=>{ setSession(null); go("today"); }} />;
  else if (route.name === "catalog") screen = <CatalogScreen lang={lang} go={go} openExercise={(id)=>go("exercise",{ id })} />;
  else if (route.name === "exercise") screen = <ExerciseDetailScreen lang={lang} id={route.id} back={()=>go("catalog")} addToPlan={()=>go("builder")} />;
  else if (route.name === "builder" || route.name === "plans") screen = <BuilderScreen lang={lang} back={()=>go("today")} openExercise={(id)=>go("exercise",{ id })} />;
  else if (route.name === "settings") screen = <SettingsScreen lang={lang} setLang={setLang} theme={theme} setTheme={setTheme} back={()=>go("today")} />;
  else screen = <TodayScreen lang={lang} go={go} startSession={startSession} session={session ? { ...session, doneCount:Object.values(session.done).filter(Boolean).length } : null} />;

  const inSession = route.name === "session";

  return (
    <PhoneFrame theme={theme}>
      <div style={{ flex:1, minHeight:0, display:"flex", flexDirection:"column", position:"relative" }}>
        {screen}
        {!inSession && route.name !== "settings" && route.name !== "exercise" ? (
          <div style={{ position:"absolute", right:16, bottom:96, zIndex:20 }}>
            <FAB icon="add" label={t(lang,"newWorkout")} extended onClick={()=>go("builder")} />
          </div>
        ) : null}
      </div>
      {!inSession ? (
        <NavigationBar value={tab} onChange={onTab} items={[
          { value:"today", icon:"today", label:t(lang,"today") },
          { value:"plans", icon:"list_alt", label:t(lang,"plans") },
          { value:"catalog", icon:"fitness_center", label:t(lang,"catalog") },
          { value:"progress", icon:"insights", label:t(lang,"progress") }]} />
      ) : null}
    </PhoneFrame>
  );
}

Object.assign(window, { PhoneApp });
