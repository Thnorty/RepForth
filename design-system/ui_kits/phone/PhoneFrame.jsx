const { Icon } = window.RepForthDesignSystem_c95a40;

function StatusBar({ time = "18:42" }) {
  return (
    <div style={{ display:"flex", alignItems:"center", justifyContent:"space-between", padding:"0 20px", height:34, flex:"none",
      fontFamily:"var(--font-numeric)", fontVariantNumeric:"tabular-nums", fontWeight:700, fontSize:13, color:"var(--text-body)" }}>
      <span>{time}</span>
      <span style={{ display:"flex", gap:5, alignItems:"center", color:"var(--text-quiet)" }}>
        <Icon name="signal_cellular_alt" size={15} /><Icon name="wifi" size={15} /><Icon name="battery_5_bar" size={15} />
      </span>
    </div>
  );
}

function PhoneFrame({ children, theme = "dark", label }) {
  return (
    <div style={{ display:"flex", flexDirection:"column", gap:10, alignItems:"center" }}>
      <div data-theme={theme} style={{ width:412, height:892, borderRadius:38, overflow:"hidden", background:"var(--surface-app)",
        display:"flex", flexDirection:"column", boxShadow:"0 0 0 9px #1b1f16, 0 0 0 11px #34392e, 0 24px 60px -20px rgba(0,0,0,.8)", position:"relative" }}>
        <StatusBar />
        {children}
      </div>
      {label ? <span style={{ font:"700 11px/1 var(--font-ui)", letterSpacing:".08em", textTransform:"uppercase", color:"var(--text-quiet)" }}>{label}</span> : null}
    </div>
  );
}

Object.assign(window, { PhoneFrame, StatusBar });
