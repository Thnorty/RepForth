import React from "react";
import { Icon } from "../core/Icon.jsx";

export function Stepper({ value, unit, step = 2.5, min = 0, max = 9999, onChange, size = "md", label, className = "", ...rest }) {
  const set = (v) => onChange && onChange(Math.min(max, Math.max(min, Math.round(v * 100) / 100)));
  const cls = ["rf-stepper", size === "session" ? "rf-stepper--session" : "", className].filter(Boolean).join(" ");
  const btn = size === "session" ? 32 : 24;
  return (
    <div className={cls} role="group" aria-label={label} {...rest}>
      <button type="button" className="rf-stepper__btn" aria-label="Decrease" onClick={() => set(value - step)} disabled={value <= min}><Icon name="remove" size={btn} weight={600} /></button>
      <span className="rf-stepper__value" aria-live="polite">{value}{unit ? <span className="rf-stepper__unit">{unit}</span> : null}</span>
      <button type="button" className="rf-stepper__btn" aria-label="Increase" onClick={() => set(value + step)} disabled={value >= max}><Icon name="add" size={btn} weight={600} /></button>
    </div>
  );
}
