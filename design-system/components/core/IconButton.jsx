import React from "react";
import { Icon } from "./Icon.jsx";

export function IconButton({ icon, label, variant = "standard", size = "md", selected, disabled = false, className = "", ...rest }) {
  const cls = ["rf-iconbtn", variant !== "standard" ? "rf-iconbtn--" + variant : "", size !== "md" ? "rf-iconbtn--" + size : "", className].filter(Boolean).join(" ");
  const glyph = size === "session" ? 32 : size === "lg" ? 28 : 24;
  return (
    <button type="button" className={cls} aria-label={label} aria-pressed={selected === undefined ? undefined : selected} disabled={disabled} {...rest}>
      <Icon name={icon} size={glyph} fill={!!selected} weight={selected ? 600 : 400} />
    </button>
  );
}
