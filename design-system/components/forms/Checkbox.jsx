import React from "react";
import { Icon } from "../core/Icon.jsx";

export function Checkbox({ label, description, checked = false, onChange, disabled = false, className = "", ...rest }) {
  return (
    <button type="button" role="checkbox" aria-checked={checked} disabled={disabled} onClick={() => onChange && onChange(!checked)} className={["rf-check", className].filter(Boolean).join(" ")} {...rest}>
      <span className="rf-check__box"><Icon name="check" size={18} weight={700} /></span>
      <span className="rf-check__text">
        <span>{label}</span>
        {description ? <span className="rf-check__desc">{description}</span> : null}
      </span>
    </button>
  );
}
