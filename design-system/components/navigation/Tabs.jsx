import React from "react";
import { Icon } from "../core/Icon.jsx";

export function Tabs({ items = [], value, onChange, scrollable = false, className = "", ...rest }) {
  return (
    <div className={["rf-tabs", scrollable ? "rf-tabs--scroll" : "", className].filter(Boolean).join(" ")} role="tablist" {...rest}>
      {items.map((it) => {
        const on = it.value === value;
        return (
          <button key={it.value} type="button" role="tab" aria-selected={on} className="rf-tabs__item" onClick={() => onChange && onChange(it.value)}>
            {it.icon ? <Icon name={it.icon} size={18} fill={on} /> : null}
            <span>{it.label}</span>
            {it.count !== undefined ? <span style={{ fontFamily: "var(--font-numeric)", fontVariantNumeric: "tabular-nums", opacity: .75 }}>{it.count}</span> : null}
          </button>
        );
      })}
    </div>
  );
}
