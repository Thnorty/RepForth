import React from "react";
import { Icon } from "../core/Icon.jsx";

export function Switch({ label, description, checked = false, onChange, disabled = false, className = "", ...rest }) {
  return (
    <button type="button" role="switch" aria-checked={checked} disabled={disabled} onClick={() => onChange && onChange(!checked)} className={["rf-switch", className].filter(Boolean).join(" ")} {...rest}>
      <span className="rf-check__text">
        <span>{label}</span>
        {description ? <span className="rf-check__desc">{description}</span> : null}
      </span>
      <span className="rf-switch__track"><span className="rf-switch__thumb"><Icon name="check" size={16} weight={700} /></span></span>
    </button>
  );
}
