import React from "react";

export function WearArc({ value = 0, tone = "accent", stroke = 3.5, inset = 3, className = "", ...rest }) {
  const size = 100;
  const r = (size - stroke) / 2 - inset;
  const c = 2 * Math.PI * r;
  const clamped = Math.min(1, Math.max(0, value));
  return (
    <div className={["rf-wear-arc", tone === "rest" ? "rf-wear-arc--rest" : "", className].filter(Boolean).join(" ")} {...rest}>
      <svg viewBox={"0 0 " + size + " " + size} preserveAspectRatio="none">
        <circle className="rf-wear-arc__track" cx={size / 2} cy={size / 2} r={r} strokeWidth={stroke} />
        <circle className="rf-wear-arc__fill" cx={size / 2} cy={size / 2} r={r} strokeWidth={stroke} strokeDasharray={c} strokeDashoffset={c * (1 - clamped)} />
      </svg>
    </div>
  );
}
