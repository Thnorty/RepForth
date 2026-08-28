function WatchFrame({ shape = "round", size = 208, children, label }) {
  const bezel = shape === "round"
    ? { borderRadius:"50%", boxShadow:"0 0 0 10px #1b1f16, 0 0 0 13px #3a3f33, 0 18px 44px -14px rgba(0,0,0,.85)" }
    : { borderRadius:"22%", boxShadow:"0 0 0 9px #1b1f16, 0 0 0 12px #3a3f33, 0 18px 44px -14px rgba(0,0,0,.85)" };
  return (
    <div style={{ display:"flex", flexDirection:"column", alignItems:"center", gap:14 }}>
      <div style={{ position:"relative", width:size, height:size, ...bezel }}>
        {children}
        {shape === "round" ? <div style={{ position:"absolute", right:-13, top:"34%", width:7, height:34, borderRadius:4, background:"#4c5142" }} /> : null}
      </div>
      {label ? <span style={{ font:"700 11px/1 var(--font-ui)", letterSpacing:".08em", textTransform:"uppercase", color:"var(--text-quiet)" }}>{label}</span> : null}
    </div>
  );
}
Object.assign(window, { WatchFrame });
