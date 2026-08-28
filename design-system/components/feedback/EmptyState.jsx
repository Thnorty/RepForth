import React from "react";
import { Icon } from "../core/Icon.jsx";

export function EmptyState({ icon = "fitness_center", title, body, action, className = "", ...rest }) {
  return (
    <div className={["rf-empty", className].filter(Boolean).join(" ")} {...rest}>
      <div className="rf-empty__icon"><Icon name={icon} size={32} /></div>
      <div className="rf-empty__title">{title}</div>
      {body ? <div className="rf-empty__body">{body}</div> : null}
      {action}
    </div>
  );
}
