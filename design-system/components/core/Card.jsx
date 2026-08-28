import React from "react";

export function Card({ variant = "filled", size = "md", flush = false, interactive = false, as, children, className = "", ...rest }) {
  const Tag = as || (interactive ? "button" : "div");
  const cls = ["rf-card", variant !== "filled" ? "rf-card--" + variant : "", size === "lg" ? "rf-card--lg" : "", flush ? "rf-card--flush" : "", interactive ? "rf-card--interactive" : "", className].filter(Boolean).join(" ");
  return <Tag className={cls} {...(Tag === "button" ? { type: "button" } : {})} {...rest}>{children}</Tag>;
}
