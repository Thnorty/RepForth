import React from "react";
import { Icon } from "../core/Icon.jsx";

export function SelectField({ label, value, onChange, options = [], icon, helper, id, className = "", ...rest }) {
  const fid = id || "rf-sel-" + (label || "select").replace(/\s+/g, "-").toLowerCase();
  return (
    <div className={["rf-field", className].filter(Boolean).join(" ")}>
      {label ? <label className="rf-field__label" htmlFor={fid}>{label}</label> : null}
      <div className="rf-field__box">
        {icon ? <Icon name={icon} size={20} /> : null}
        <select id={fid} value={value} onChange={onChange} {...rest}>
          {options.map((o) => {
            const val = typeof o === "string" ? o : o.value;
            const lab = typeof o === "string" ? o : o.label;
            return <option key={val} value={val}>{lab}</option>;
          })}
        </select>
        <Icon name="expand_more" size={20} />
      </div>
      {helper ? <span className="rf-field__help">{helper}</span> : null}
    </div>
  );
}
