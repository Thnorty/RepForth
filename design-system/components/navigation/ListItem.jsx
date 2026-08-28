import React from "react";
import { Icon } from "../core/Icon.jsx";

export function ListItem({ title, subtitle, media, mediaIcon, trailing, trailingIcon, onClick, className = "", ...rest }) {
  const Tag = onClick ? "button" : "div";
  return (
    <Tag type={onClick ? "button" : undefined} className={["rf-row", onClick ? "" : "rf-row--static", className].filter(Boolean).join(" ")} onClick={onClick} {...rest}>
      {media || mediaIcon ? (
        <span className="rf-row__media">{media ? media : <Icon name={mediaIcon} size={24} />}</span>
      ) : null}
      <span className="rf-row__text">
        <span className="rf-row__title">{title}</span>
        {subtitle ? <span className="rf-row__sub">{subtitle}</span> : null}
      </span>
      {trailing || trailingIcon ? (
        <span className="rf-row__trail">{trailing}{trailingIcon ? <Icon name={trailingIcon} size={24} /> : null}</span>
      ) : null}
    </Tag>
  );
}
