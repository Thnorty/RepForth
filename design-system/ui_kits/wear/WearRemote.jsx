const DS_W = window.RepForthDesignSystem_c95a40;

const W_STR = {
  en: { rest:"Rest", set:"Set", reps:"reps", resume:"Resume", skip:"Skip rest", complete:"Complete set",
    exercises:"Exercises", nextEx:"Next exercise", finish:"Finish", connected:"Phone connected", pick:"Pick exercise" },
  tr: { rest:"Dinlenme", set:"Set", reps:"tekrar", resume:"Devam et", skip:"Dinlenmeyi atla", complete:"Seti tamamla",
    exercises:"Egzersizler", nextEx:"Sonraki egzersiz", finish:"Bitir", connected:"Telefon bağlı", pick:"Egzersiz seç" }
};
const W_EX = [
  { en:"Bench press", tr:"Bench press", sets:"3/4" },
  { en:"Incline press", tr:"Eğimli press", sets:"0/3" },
  { en:"Cable fly", tr:"Kablo açma", sets:"0/3" }
];

function WearRemote({ shape = "round", size = 208, ambient = false, lang = "en", view, setView }) {
  const { WearScreen, WearBody, WearValue, WearArc, WearList, WearListItem, WearAction, Icon } = DS_W;
  const s = W_STR[lang];
  const [rest, setRest] = React.useState(42);
  const [setNo, setSetNo] = React.useState(3);
  const [weight, setWeight] = React.useState(82.5);

  React.useEffect(() => {
    if (view !== "rest" || ambient) return;
    const id = setInterval(() => setRest(r => (r <= 1 ? 90 : r - 1)), 1000);
    return () => clearInterval(id);
  }, [view, ambient]);

  const body = () => {
    if (view === "list") {
      return (
        <WearBody>
          <WearList>
            <WearListItem primary icon="play_arrow" label={s.resume} onClick={()=>setView("set")} />
            {W_EX.map(e => <WearListItem key={e.en} icon="fitness_center" label={e[lang]} value={e.sets} onClick={()=>setView("set")} />)}
            <WearListItem icon="flag" label={s.finish} onClick={()=>setView("set")} />
          </WearList>
        </WearBody>
      );
    }
    if (view === "rest") {
      return (
        <React.Fragment>
          <WearArc value={rest / 90} tone="rest" />
          <WearBody>
            <span className="rf-wear__title">{s.rest + " · " + s.set + " " + setNo}</span>
            <WearValue value={"0:" + String(rest).padStart(2,"0")} caption={ambient ? W_EX[0][lang] : undefined} size={shape === "round" ? 44 : 48} />
            {!ambient ? <WearAction actions={[{ icon:"skip_next", label:s.skip, tone:"primary", onClick:()=>setView("set") }]} /> : null}
          </WearBody>
        </React.Fragment>
      );
    }
    return (
      <React.Fragment>
        <WearArc value={setNo / 4} />
        <WearBody>
          <span className="rf-wear__title">{s.set + " " + setNo + " / 4 · × 8 " + s.reps}</span>
          <WearValue value={weight} unit="kg" caption={ambient ? W_EX[0][lang] : undefined} size={shape === "round" ? 42 : 46} />
          {!ambient ? <WearAction actions={[{ icon:"check", label:s.complete, tone:"primary", onClick:()=>{ setSetNo(n=>Math.min(4,n+1)); setView("rest"); } }]} /> : null}
        </WearBody>
      </React.Fragment>
    );
  };

  return (
    <WearScreen shape={shape} size={size} ambient={ambient}>{body()}</WearScreen>
  );
}
Object.assign(window, { WearRemote, W_STR });
