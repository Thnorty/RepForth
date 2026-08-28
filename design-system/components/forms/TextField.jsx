import React from "react";
import { Icon } from "../core/Icon.jsx";

export function TextField({ label, value, onChange, placeholder, icon, suffix, helper, error, numeric = false, type = "text", id, className = "", ...rest }) {
  const fid = id || "rf-tf-" + (label || placeholder || "field").replace(/\s+/g, "-").toLowerCase();
  const cls = ["rf-field", numeric ? "rf-field--numeric" : "", error ? "rf-field--error" : "", className].filter(Boolean).join(" ");
  return (
    <div className={cls}>
      {label ? <label className="rf-field__label" htmlFor={fid}>{label}</label> : null}
      <div className="rf-field__box">
        {icon ? <Icon name={icon} size={20} /> : null}
        <input id={fid} type={type} value={value} placeholder={placeholder} onChange={onChange} inputMode={numeric ? "decimal" : undefined} aria-invalid={error ? true : undefined} {...rest} />
        {suffix ? <span className="rf-field__affix">{suffix}</span> : null}
      </div>
      {error || helper ? (
        <span className="rf-field__help">
          {error ? <Icon name="error" size={16} /> : null}
          {error || helper}
        </span>
      ) : null}
    </div>
  );
}
