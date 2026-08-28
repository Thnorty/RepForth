import React from "react";

export function Slider({ label, value, min = 0, max = 100, step = 1, unit, onChange, format, className = "", ...rest }) {
  const shown = format ? format(value) : value + (unit ? " " + unit : "");
  const pct = max === min ? 0 : ((value - min) / (max - min)) * 100;
  return (
    <div className={["rf-slider", className].filter(Boolean).join(" ")}>
      <div className="rf-slider__head">
        <span className="rf-field__label">{label}</span>
        <span className="rf-slider__val">{shown}</span>
      </div>
      <div className="rf-slider__rail">
        <span className="rf-slider__track" />
        <span className="rf-slider__fill" style={{ width: pct + "%" }} />
        <input className="rf-slider__input" type="range" value={value} min={min} max={max} step={step} aria-label={label}
          aria-valuetext={String(shown)} onChange={(e) => onChange && onChange(Number(e.target.value))} {...rest} />
        <span className="rf-slider__knob" style={{ left: pct + "%" }} />
      </div>
    </div>
  );
}
