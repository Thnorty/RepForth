import React from "react";
import { ProgressRing } from "../feedback/ProgressRing.jsx";
import { StatBlock } from "../core/StatBlock.jsx";
import { Button } from "../core/Button.jsx";
import { IconButton } from "../core/IconButton.jsx";

function fmt(s) {
  const m = Math.floor(s / 60);
  const r = Math.abs(s % 60);
  return m + ":" + String(r).padStart(2, "0");
}

export function RestTimer({ remaining, total = 90, label = "Rest", nextUp, size = 240, onSkip, onAdd, onSubtract, skipLabel = "Skip rest", className = "", ...rest }) {
  const value = total ? Math.max(0, Math.min(1, remaining / total)) : 0;
  return (
    <div className={["rf-rest", className].filter(Boolean).join(" ")} {...rest}>
      <span className="rf-rest__label">{label}</span>
      <ProgressRing value={value} size={size} tone="rest" label={label + " " + fmt(remaining) + " remaining"}>
        <StatBlock value={fmt(remaining)} size="xl" align="center" />
      </ProgressRing>
      {nextUp ? <span className="rf-rest__next">{nextUp}</span> : null}
      <div className="rf-rest__actions">
        <IconButton icon="remove" label="Subtract 15 seconds" variant="tonal" size="lg" onClick={onSubtract} />
        <Button variant="filled" size="session" icon="skip_next" onClick={onSkip}>{skipLabel}</Button>
        <IconButton icon="add" label="Add 15 seconds" variant="tonal" size="lg" onClick={onAdd} />
      </div>
    </div>
  );
}
