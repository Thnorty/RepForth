import React from "react";

export function Divider({ inset = false, vertical = false, className = "", ...rest }) {
  const cls = ["rf-divider", inset ? "rf-divider--inset" : "", vertical ? "rf-divider--vertical" : "", className].filter(Boolean).join(" ");
  return vertical ? <span className={cls} role="separator" aria-orientation="vertical" {...rest} /> : <hr className={cls} {...rest} />;
}
