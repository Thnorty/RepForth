import React from "react";

export function ProgressRing({ value = 0, size = 200, stroke, tone = "accent", label, children, className = "", ...rest }) {
  const w = stroke || Math.max(6, Math.round(size * 0.06));
  const r = (size - w) / 2;
  const c = 2 * Math.PI * r;
  const clamped = Math.min(1, Math.max(0, value));
  const cls = ["rf-ring", tone === "rest" ? "rf-ring--rest" : tone === "done" ? "rf-ring--done" : "", className].filter(Boolean).join(" ");
  return (
    <div className={cls} style={{ width: size, height: size }} role="img" aria-label={label} {...rest}>
      <svg width={size} height={size} viewBox={"0 0 " + size + " " + size}>
        <circle className="rf-ring__track" cx={size / 2} cy={size / 2} r={r} strokeWidth={w} />
        <circle className="rf-ring__fill" cx={size / 2} cy={size / 2} r={r} strokeWidth={w} strokeDasharray={c} strokeDashoffset={c * (1 - clamped)} />
      </svg>
      <div className="rf-ring__center">{children}</div>
    </div>
  );
}
