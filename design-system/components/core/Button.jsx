import React from "react";
import { Icon } from "./Icon.jsx";

export function Button({ variant = "filled", size = "md", icon, trailingIcon, fullWidth = false, disabled = false, children, className = "", type = "button", ...rest }) {
  const cls = ["rf-btn", "rf-btn--" + variant, "rf-btn--" + size, fullWidth ? "rf-btn--block" : "", className].filter(Boolean).join(" ");
  const glyph = size === "session" ? 28 : size === "sm" ? 18 : 20;
  return (
    <button type={type} className={cls} disabled={disabled} {...rest}>
      {icon ? <Icon name={icon} size={glyph} weight={600} /> : null}
      <span className="rf-btn__label">{children}</span>
      {trailingIcon ? <Icon name={trailingIcon} size={glyph} weight={600} /> : null}
    </button>
  );
}
