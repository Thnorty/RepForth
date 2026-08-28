import React from "react";
import { Icon } from "./Icon.jsx";

export function FAB({ icon, label, extended = false, size = "regular", className = "", ...rest }) {
  const cls = ["rf-fab", extended ? "rf-fab--extended" : "", size === "large" ? "rf-fab--lg" : "", className].filter(Boolean).join(" ");
  return (
    <button type="button" className={cls} aria-label={extended ? undefined : label} {...rest}>
      <Icon name={icon} size={size === "large" ? 32 : 24} weight={600} />
      {extended ? <span>{label}</span> : null}
    </button>
  );
}
