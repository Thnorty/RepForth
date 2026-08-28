import React from "react";
import { IconButton } from "../core/IconButton.jsx";

export function TopAppBar({ title, subtitle, leadingIcon, onLeading, leadingLabel = "Back", actions = [], large = false, scrolled = false, className = "", ...rest }) {
  const cls = ["rf-appbar", large ? "rf-appbar--large" : "", scrolled ? "rf-appbar--scrolled" : "", className].filter(Boolean).join(" ");
  const trailing = actions.map((a) => <IconButton key={a.icon + a.label} icon={a.icon} label={a.label} onClick={a.onClick} selected={a.selected} />);
  if (large) {
    return (
      <div className={cls} {...rest}>
        <div className="rf-appbar__row">
          {leadingIcon ? <IconButton icon={leadingIcon} label={leadingLabel} onClick={onLeading} /> : <span style={{ width: 8 }} />}
          <span style={{ flex: 1 }} />
          {trailing}
        </div>
        <div className="rf-appbar__display">{title}</div>
        {subtitle ? <span className="rf-appbar__sub" style={{ padding: "0 var(--space-4)" }}>{subtitle}</span> : null}
      </div>
    );
  }
  return (
    <div className={cls} {...rest}>
      {leadingIcon ? <IconButton icon={leadingIcon} label={leadingLabel} onClick={onLeading} /> : null}
      <div className="rf-appbar__title">{title}{subtitle ? <span className="rf-appbar__sub">{subtitle}</span> : null}</div>
      {trailing}
    </div>
  );
}
