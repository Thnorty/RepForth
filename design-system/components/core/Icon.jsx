import React from "react";

/* Material Symbols Rounded wrapper. The only icon system in RepForth. */
export function Icon({ name, size = 24, fill = false, weight = 400, color, className = "", label, style, ...rest }) {
  const cls = ["rf-icon", className].filter(Boolean).join(" ");
  const vf = '"FILL" ' + (fill ? 1 : 0) + ',"wght" ' + weight + ',"GRAD" 0,"opsz" ' + size;
  return (
    <span
      className={cls}
      aria-hidden={label ? undefined : true}
      role={label ? "img" : undefined}
      aria-label={label}
      style={{ fontSize: size, width: size, height: size, color: color, fontVariationSettings: vf, ...style }}
      {...rest}
    >{name}</span>
  );
}
