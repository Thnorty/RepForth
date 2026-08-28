import React from "react";
import { Icon } from "../core/Icon.jsx";

export function WearAction({ actions = [], className = "", ...rest }) {
  return (
    <div className={["rf-wear-act", className].filter(Boolean).join(" ")} {...rest}>
      {actions.map((a) => (
        <button key={a.icon + a.label} type="button" aria-label={a.label} onClick={a.onClick}
          className={["rf-wear-act__btn", a.tone === "primary" ? "rf-wear-act__btn--primary" : a.tone === "danger" ? "rf-wear-act__btn--danger" : ""].filter(Boolean).join(" ")}>
          <Icon name={a.icon} size={a.tone === "primary" ? 30 : 22} weight={600} />
        </button>
      ))}
    </div>
  );
}
