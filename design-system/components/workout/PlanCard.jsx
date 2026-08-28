import React from "react";
import { Card } from "../core/Card.jsx";
import { Chip } from "../core/Chip.jsx";
import { Badge } from "../core/Badge.jsx";
import { Icon } from "../core/Icon.jsx";
import { StatBlock } from "../core/StatBlock.jsx";

export function PlanCard({ name, exercises, exercisesLabel = "exercises", minutes, minutesLabel = "min", muscles = [], badge, today = false, onClick, action, className = "", ...rest }) {
  const seen = {};
  const tags = muscles.filter((m) => {
    const k = m.id || m.label;
    if (seen[k]) return false;
    seen[k] = true;
    return true;
  });
  return (
    <Card size="lg" interactive={!!onClick} onClick={onClick} className={["rf-plan", today ? "rf-plan--today" : "", className].filter(Boolean).join(" ")} {...rest}>
      <div className="rf-plan__head">
        <span className="rf-plan__title">{name}</span>
        {badge ? <Badge tone={today ? "accent" : "neutral"} icon={today ? "bolt" : undefined} label={badge} /> : null}
      </div>
      <div className="rf-plan__meta">
        <StatBlock value={exercises} unit={exercisesLabel} size="xs" />
        <span style={{ display: "flex", alignItems: "center", gap: 6, color: "var(--text-quiet)" }}><Icon name="schedule" size={16} /><span className="rf-label">{minutes} {minutesLabel}</span></span>
      </div>
      {tags.length ? (
        <div className="rf-plan__chips">{tags.map((m) => <Chip key={m.id || m.label} icon={m.icon} label={m.label} size="sm" />)}</div>
      ) : null}
      {action}
    </Card>
  );
}
