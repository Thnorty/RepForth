import React from "react";
import { Icon } from "./Icon.jsx";

export function Chip({ icon, label, count, selected, size = "md", trailingIcon, onClick, className = "", ...rest }) {
  const isToggle = selected !== undefined;
  const isStatic = !onClick && !isToggle;
  const cls = ["rf-chip", size === "sm" ? "rf-chip--sm" : "", isStatic ? "rf-chip--static" : "", selected ? "rf-chip--selected" : "", className].filter(Boolean).join(" ");
  const glyph = size === "sm" ? 16 : 18;
  const inner = (
    <React.Fragment>
      {selected ? <Icon name="check" size={glyph} weight={700} /> : icon ? <Icon name={icon} size={glyph} /> : null}
      <span>{label}</span>
      {count !== undefined ? <span className="rf-chip__count">{count}</span> : null}
      {trailingIcon ? <Icon name={trailingIcon} size={glyph} /> : null}
    </React.Fragment>
  );
  // Read-only tags render as a span: they are often nested inside clickable
  // cards and rows, where a nested <button> would be invalid HTML.
  if (isStatic) return <span className={cls} {...rest}>{inner}</span>;
  return (
    <button type="button" className={cls} aria-pressed={isToggle ? !!selected : undefined} onClick={onClick} {...rest}>{inner}</button>
  );
}
