import React from "react";
import { Icon } from "../core/Icon.jsx";

export function WearListItem({ icon, label, value, primary = false, onClick, className = "", ...rest }) {
  return (
    <button type="button" className={["rf-wear-row", primary ? "rf-wear-row--primary" : "", className].filter(Boolean).join(" ")} onClick={onClick} {...rest}>
      {icon ? <Icon name={icon} size={18} weight={600} /> : null}
      <span className="rf-wear-row__text">{label}</span>
      {value !== undefined ? <span className="rf-wear-row__num">{value}</span> : null}
    </button>
  );
}

export function WearList({ children, className = "", ...rest }) {
  return <div className={["rf-wear-list", className].filter(Boolean).join(" ")} {...rest}>{children}</div>;
}
