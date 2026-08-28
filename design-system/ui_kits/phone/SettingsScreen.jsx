const DS_ST = window.RepForthDesignSystem_c95a40;

function SettingsScreen({ lang, setLang, theme, setTheme, back }) {
  const { TopAppBar, SegmentedButtons, Switch, ListItem, Divider, Icon, Card, Badge } = DS_ST;
  const [awake, setAwake] = React.useState(true);
  const [sound, setSound] = React.useState(true);
  return (
    <div style={{ flex:1, minHeight:0, display:"flex", flexDirection:"column" }}>
      <TopAppBar leadingIcon="arrow_back" onLeading={back} title={t(lang,"settingsTitle")} />
      <div className="rf-scroll" style={{ flex:1, minHeight:0, padding:"0 var(--gutter-phone) 24px", display:"flex", flexDirection:"column", gap:20 }}>
        <div style={{ display:"flex", flexDirection:"column", gap:10 }}>
          <span className="rf-overline">{t(lang,"language")}</span>
          <SegmentedButtons label={t(lang,"language")} value={lang} onChange={setLang}
            options={[{ value:"en", label:"English", icon:"translate" }, { value:"tr", label:"Türkçe", icon:"translate" }]} />
        </div>
        <div style={{ display:"flex", flexDirection:"column", gap:10 }}>
          <span className="rf-overline">{t(lang,"theme")}</span>
          <SegmentedButtons label={t(lang,"theme")} value={theme} onChange={setTheme}
            options={[{ value:"dark", label:lang==="en"?"Dark":"Koyu", icon:"dark_mode" }, { value:"light", label:lang==="en"?"Light":"Açık", icon:"light_mode" }]} />
        </div>
        <div style={{ display:"flex", flexDirection:"column", gap:10 }}>
          <span className="rf-overline">{lang==="en"?"During a workout":"Antrenman sırasında"}</span>
          <Switch label={t(lang,"keepAwake")} description={t(lang,"keepAwakeSub")} checked={awake} onChange={setAwake} />
          <Divider />
          <Switch label={lang==="en"?"Rest countdown sound":"Dinlenme sesi"} description={lang==="en"?"Beeps at 3 seconds":"3 saniye kala uyarır"} checked={sound} onChange={setSound} />
        </div>
        <div style={{ display:"flex", flexDirection:"column", gap:4 }}>
          <span className="rf-overline">{t(lang,"watch")}</span>
          <ListItem mediaIcon="watch" title={lang==="en"?"Galaxy Watch6":"Galaxy Watch6"} subtitle={t(lang,"watchSub")} trailing={<Badge tone="info" icon="check" label={lang==="en"?"Connected":"Bağlı"} />} onClick={()=>{}} />
          <ListItem mediaIcon="straighten" title={t(lang,"units")} subtitle="kg" trailingIcon="chevron_right" onClick={()=>{}} />
          <ListItem mediaIcon="download" title={lang==="en"?"Export data":"Verileri dışa aktar"} subtitle={lang==="en"?"JSON, on this device":"JSON, bu cihazda"} trailingIcon="chevron_right" onClick={()=>{}} />
          <ListItem mediaIcon="code" title={lang==="en"?"Source code":"Kaynak kod"} subtitle={lang==="en"?"GPL-3.0 · read it, change it":"GPL-3.0 · oku, değiştir"} trailingIcon="open_in_new" onClick={()=>{}} />
        </div>
        <Card style={{ display:"flex", gap:10, alignItems:"flex-start" }}>
          <Icon name="phone_android" size={20} color="var(--color-primary)" />
          <span style={{ fontSize:"var(--label-md)", color:"var(--text-quiet)", lineHeight:1.5 }}>{t(lang,"local")}</span>
        </Card>
      </div>
    </div>
  );
}

Object.assign(window, { SettingsScreen });
