import React from "react";
import { Icon } from "../core/Icon.jsx";

export function SegmentedButtons({ options = [], value, onChange, label, className = "", ...rest }) {
  return (
    <div className={["rf-seg", className].filter(Boolean).join(" ")} role="group" aria-label={label} {...rest}>
      {options.map((o) => {
        const val = typeof o === "string" ? o : o.value;
        const lab = typeof o === "string" ? o : o.label;
        const icon = typeof o === "string" ? null : o.icon;
        const on = val === value;
        return (
          <button key={val} type="button" className="rf-seg__item" aria-pressed={on} onClick={() => onChange && onChange(val)}>
            {on ? <Icon name="check" size={16} weight={700} /> : icon ? <Icon name={icon} size={16} /> : null}
            <span>{lab}</span>
          </button>
        );
      })}
    </div>
  );
}
