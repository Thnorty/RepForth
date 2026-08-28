import React from "react";
import { Icon } from "../core/Icon.jsx";

export function NavigationBar({ items = [], value, onChange, className = "", ...rest }) {
  return (
    <nav className={["rf-navbar", className].filter(Boolean).join(" ")} {...rest}>
      {items.map((it) => {
        const on = it.value === value;
        return (
          <button key={it.value} type="button" className="rf-navbar__item" aria-current={on ? "page" : undefined} onClick={() => onChange && onChange(it.value)}>
            <span className="rf-navbar__pill"><Icon name={it.icon} size={24} fill={on} weight={on ? 600 : 400} /></span>
            <span className="rf-navbar__label">{it.label}</span>
          </button>
        );
      })}
    </nav>
  );
}
