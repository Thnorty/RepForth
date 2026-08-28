import React from "react";
import { Icon } from "../core/Icon.jsx";

export function SetRow({ index, weight, reps, unit = "kg", previous, done = false, active = false, onToggle, className = "", ...rest }) {
  const cls = ["rf-set", done ? "rf-set--done" : "", active && !done ? "rf-set--active" : "", className].filter(Boolean).join(" ");
  return (
    <div className={cls} {...rest}>
      <span className="rf-set__idx">{index}</span>
      <span className="rf-set__cell">
        <span className="rf-set__val">{weight}<span className="rf-set__unit">{unit}</span></span>
        {previous ? <span className="rf-set__prev">{previous}</span> : null}
      </span>
      <span className="rf-set__cell">
        <span className="rf-set__val">{reps}<span className="rf-set__unit">reps</span></span>
      </span>
      <button type="button" className="rf-set__check" aria-pressed={done} aria-label={done ? "Set " + index + " completed" : "Complete set " + index} onClick={onToggle}>
        <Icon name={done ? "check" : "radio_button_unchecked"} size={26} weight={done ? 700 : 400} />
      </button>
    </div>
  );
}

export function SetRowHeader({ weightLabel = "Weight", repsLabel = "Reps", className = "" }) {
  return (
    <div className={["rf-set__head", className].filter(Boolean).join(" ")}>
      <span>#</span><span>{weightLabel}</span><span>{repsLabel}</span><span />
    </div>
  );
}
