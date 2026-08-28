import React from "react";

export function WearScreen({ shape = "round", size = 208, ambient = false, children, className = "", ...rest }) {
  const cls = ["rf-wear", shape === "round" ? "rf-wear--round" : "rf-wear--square", className].filter(Boolean).join(" ");
  return (
    <div className={cls} data-ambient={ambient ? "true" : "false"} style={{ width: size, height: size }} {...rest}>
      {children}
    </div>
  );
}

export function WearBody({ children, className = "", ...rest }) {
  return <div className={["rf-wear__inner", className].filter(Boolean).join(" ")} {...rest}>{children}</div>;
}

export function WearValue({ value, unit, caption, size = 44, className = "", ...rest }) {
  return (
    <div className={className} {...rest} style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 2 }}>
      <span className="rf-wear__value" style={{ fontSize: size }}>{value}{unit ? <span style={{ fontSize: 13, fontFamily: "var(--font-ui)", fontWeight: 600, marginLeft: 3, color: "var(--n-70)" }}>{unit}</span> : null}</span>
      {caption ? <span className="rf-wear__sub">{caption}</span> : null}
    </div>
  );
}
