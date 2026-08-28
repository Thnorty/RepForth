import React from "react";
import { Icon } from "./Icon.jsx";

export function Badge({ label, icon, tone = "neutral", dot = false, className = "", ...rest }) {
  const cls = ["rf-badge", tone !== "neutral" ? "rf-badge--" + tone : "", dot ? "rf-badge--dot" : "", className].filter(Boolean).join(" ");
  if (dot) return <span className={cls} role="img" aria-label={label} {...rest} />;
  return (
    <span className={cls} {...rest}>
      {icon ? <Icon name={icon} size={14} weight={600} /> : null}
      <span>{label}</span>
    </span>
  );
}
