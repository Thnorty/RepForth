import React from "react";
import { Card } from "../core/Card.jsx";
import { Chip } from "../core/Chip.jsx";
import { Icon } from "../core/Icon.jsx";

export function ExerciseCard({ name, media, tags = [], sets, reps, layout = "row", trailing, onClick, className = "", ...rest }) {
  const thumb = (
    <span className={["rf-ex__media", layout === "stacked" ? "rf-ex__media--lg" : ""].filter(Boolean).join(" ")}>
      {media ? media : <Icon name="fitness_center" size={28} />}
    </span>
  );
  return (
    <Card interactive={!!onClick} onClick={onClick} className={className} {...rest}>
      <div className="rf-ex" style={layout === "stacked" ? { flexDirection: "column", alignItems: "stretch" } : undefined}>
        {thumb}
        <div className="rf-ex__body">
          <span className="rf-ex__name">{name}</span>
          {tags.length ? <div className="rf-ex__chips">{tags.map((t) => <Chip key={t.label} icon={t.icon} label={t.label} size="sm" />)}</div> : null}
          {sets !== undefined ? (
            <span className="rf-ex__sets">{sets}<span className="rf-unit">sets</span>{reps !== undefined ? <React.Fragment><span style={{ color: "var(--text-quiet)", margin: "0 2px" }}>×</span>{reps}<span className="rf-unit">reps</span></React.Fragment> : null}</span>
          ) : null}
        </div>
        {trailing ? <span className="rf-row__trail">{trailing}</span> : null}
      </div>
    </Card>
  );
}
