import React from "react";

export function ProgressBar({ value = 0, total, label, showValue = true, segmented = false, current, className = "", ...rest }) {
  const pct = total ? Math.min(1, value / total) : Math.min(1, Math.max(0, value));
  const cls = ["rf-bar", segmented ? "rf-bar--segmented" : "", className].filter(Boolean).join(" ");
  return (
    <div className={cls} {...rest}>
      {label || showValue ? (
        <div className="rf-bar__head">
          {label ? <span className="rf-bar__label">{label}</span> : null}
          {showValue ? <span className="rf-bar__value">{total ? value + " / " + total : Math.round(pct * 100) + "%"}</span> : null}
        </div>
      ) : null}
      <div className="rf-bar__track" role="progressbar" aria-valuenow={value} aria-valuemin={0} aria-valuemax={total || 1} aria-label={label}>
        {segmented && total
          ? Array.from({ length: total }, (_, i) => (
              <span key={i} className={["rf-bar__seg", i < value ? "rf-bar__seg--on" : "", current === i ? "rf-bar__seg--current" : ""].filter(Boolean).join(" ")} />
            ))
          : <div className="rf-bar__fill" style={{ width: pct * 100 + "%" }} />}
      </div>
    </div>
  );
}
