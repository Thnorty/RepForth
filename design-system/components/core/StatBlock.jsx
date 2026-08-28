import React from "react";

export function StatBlock({ value, unit, label, size = "md", tone = "default", align = "start", className = "", ...rest }) {
  const cls = ["rf-stat", align === "center" ? "rf-stat--center" : "", tone === "accent" ? "rf-stat--accent" : "", className].filter(Boolean).join(" ");
  return (
    <div className={cls} {...rest}>
      <span className="rf-stat__value" style={{ fontSize: "var(--numeric-" + size + ")" }}>
        {value}
        {unit ? <span className="rf-stat__unit">{unit}</span> : null}
      </span>
      {label ? <span className="rf-stat__label">{label}</span> : null}
    </div>
  );
}
