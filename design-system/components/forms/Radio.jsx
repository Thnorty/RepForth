import React from "react";

export function Radio({ label, description, checked = false, onChange, name, disabled = false, className = "", ...rest }) {
  return (
    <button type="button" role="radio" aria-checked={checked} name={name} disabled={disabled} onClick={() => onChange && onChange(true)} className={["rf-check", className].filter(Boolean).join(" ")} {...rest}>
      <span className="rf-check__box rf-check__box--radio">
        <span style={{ width: 12, height: 12, borderRadius: 999, background: checked ? "var(--color-primary)" : "transparent" }} />
      </span>
      <span className="rf-check__text">
        <span>{label}</span>
        {description ? <span className="rf-check__desc">{description}</span> : null}
      </span>
    </button>
  );
}
