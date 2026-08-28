import React from "react";

export function Dialog({ open = true, title, children, actions, onDismiss, sheet = false, className = "", ...rest }) {
  if (!open) return null;
  return (
    <div className="rf-dialog-layer" style={sheet ? { alignItems: "flex-end", padding: 0 } : undefined}>
      <div className="rf-scrim" onClick={onDismiss} />
      <div className={[sheet ? "rf-sheet" : "rf-dialog", className].filter(Boolean).join(" ")} role="dialog" aria-modal="true" aria-label={title} {...rest}>
        {sheet ? <div className="rf-sheet__grip" /> : null}
        {title ? <div className="rf-dialog__title">{title}</div> : null}
        {children ? <div className="rf-dialog__body">{children}</div> : null}
        {actions ? <div className="rf-dialog__actions">{actions}</div> : null}
      </div>
    </div>
  );
}
