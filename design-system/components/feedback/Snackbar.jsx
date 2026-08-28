import React from "react";
import { Icon } from "../core/Icon.jsx";

export function Snackbar({ message, actionLabel, onAction, icon, tone = "default", className = "", ...rest }) {
  return (
    <div className={["rf-snack", tone === "error" ? "rf-snack--error" : "", className].filter(Boolean).join(" ")} role="status" {...rest}>
      {icon ? <Icon name={icon} size={20} /> : null}
      <span className="rf-snack__msg">{message}</span>
      {actionLabel ? <button type="button" className="rf-snack__action" onClick={onAction}>{actionLabel}</button> : null}
    </div>
  );
}
