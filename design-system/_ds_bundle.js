/* @ds-bundle: {"format":4,"namespace":"RepForthDesignSystem_c95a40","components":[{"name":"Badge","sourcePath":"components/core/Badge.jsx"},{"name":"Button","sourcePath":"components/core/Button.jsx"},{"name":"Card","sourcePath":"components/core/Card.jsx"},{"name":"Chip","sourcePath":"components/core/Chip.jsx"},{"name":"Divider","sourcePath":"components/core/Divider.jsx"},{"name":"FAB","sourcePath":"components/core/FAB.jsx"},{"name":"Icon","sourcePath":"components/core/Icon.jsx"},{"name":"IconButton","sourcePath":"components/core/IconButton.jsx"},{"name":"StatBlock","sourcePath":"components/core/StatBlock.jsx"},{"name":"Dialog","sourcePath":"components/feedback/Dialog.jsx"},{"name":"EmptyState","sourcePath":"components/feedback/EmptyState.jsx"},{"name":"ProgressBar","sourcePath":"components/feedback/ProgressBar.jsx"},{"name":"ProgressRing","sourcePath":"components/feedback/ProgressRing.jsx"},{"name":"Snackbar","sourcePath":"components/feedback/Snackbar.jsx"},{"name":"Checkbox","sourcePath":"components/forms/Checkbox.jsx"},{"name":"Radio","sourcePath":"components/forms/Radio.jsx"},{"name":"SegmentedButtons","sourcePath":"components/forms/SegmentedButtons.jsx"},{"name":"SelectField","sourcePath":"components/forms/SelectField.jsx"},{"name":"Slider","sourcePath":"components/forms/Slider.jsx"},{"name":"Stepper","sourcePath":"components/forms/Stepper.jsx"},{"name":"Switch","sourcePath":"components/forms/Switch.jsx"},{"name":"TextField","sourcePath":"components/forms/TextField.jsx"},{"name":"ListItem","sourcePath":"components/navigation/ListItem.jsx"},{"name":"NavigationBar","sourcePath":"components/navigation/NavigationBar.jsx"},{"name":"Tabs","sourcePath":"components/navigation/Tabs.jsx"},{"name":"TopAppBar","sourcePath":"components/navigation/TopAppBar.jsx"},{"name":"WearAction","sourcePath":"components/wear/WearAction.jsx"},{"name":"WearArc","sourcePath":"components/wear/WearArc.jsx"},{"name":"WearListItem","sourcePath":"components/wear/WearListItem.jsx"},{"name":"WearList","sourcePath":"components/wear/WearListItem.jsx"},{"name":"WearScreen","sourcePath":"components/wear/WearScreen.jsx"},{"name":"WearBody","sourcePath":"components/wear/WearScreen.jsx"},{"name":"WearValue","sourcePath":"components/wear/WearScreen.jsx"},{"name":"ExerciseCard","sourcePath":"components/workout/ExerciseCard.jsx"},{"name":"PlanCard","sourcePath":"components/workout/PlanCard.jsx"},{"name":"RestTimer","sourcePath":"components/workout/RestTimer.jsx"},{"name":"SetRow","sourcePath":"components/workout/SetRow.jsx"},{"name":"SetRowHeader","sourcePath":"components/workout/SetRow.jsx"}],"sourceHashes":{"components/core/Badge.jsx":"9f6494d1090a","components/core/Button.jsx":"d2833b41d6c7","components/core/Card.jsx":"615c27ec49c2","components/core/Chip.jsx":"b7adf9542a22","components/core/Divider.jsx":"135f4e310846","components/core/FAB.jsx":"f946a152f909","components/core/Icon.jsx":"3096dfbb1b40","components/core/IconButton.jsx":"136a0f0def2a","components/core/StatBlock.jsx":"50d15864ce54","components/feedback/Dialog.jsx":"13ea69ccc05e","components/feedback/EmptyState.jsx":"d4b24ce5b6f2","components/feedback/ProgressBar.jsx":"31e3b282b1e5","components/feedback/ProgressRing.jsx":"9c90089732ef","components/feedback/Snackbar.jsx":"d94c6a1381b9","components/forms/Checkbox.jsx":"552ca83b4612","components/forms/Radio.jsx":"b1410cb51ec4","components/forms/SegmentedButtons.jsx":"b181bdef3b10","components/forms/SelectField.jsx":"4f7f8c970edf","components/forms/Slider.jsx":"8a6108cc7b75","components/forms/Stepper.jsx":"5238466943c0","components/forms/Switch.jsx":"50baf6d1d15c","components/forms/TextField.jsx":"5285ed359c0c","components/navigation/ListItem.jsx":"420ae38e7bc7","components/navigation/NavigationBar.jsx":"c3f8a3e815a3","components/navigation/Tabs.jsx":"d0a0cde801eb","components/navigation/TopAppBar.jsx":"f22856fd6d57","components/wear/WearAction.jsx":"506a55b6a6b5","components/wear/WearArc.jsx":"14ebf4362ee1","components/wear/WearListItem.jsx":"7c3b9a968f1f","components/wear/WearScreen.jsx":"5b685ba17a40","components/workout/ExerciseCard.jsx":"46eb30e26fbd","components/workout/PlanCard.jsx":"8474a1dbec78","components/workout/RestTimer.jsx":"167ca7cd139f","components/workout/SetRow.jsx":"8017b0027ab1","ui_kits/phone/BuilderScreen.jsx":"5604f00738e6","ui_kits/phone/CatalogScreen.jsx":"6bdf8ec34331","ui_kits/phone/ExerciseDetailScreen.jsx":"f2d746ab8a70","ui_kits/phone/PhoneFrame.jsx":"db075785bd33","ui_kits/phone/SessionScreen.jsx":"863ea42f21f9","ui_kits/phone/SettingsScreen.jsx":"dcb196239bda","ui_kits/phone/TodayScreen.jsx":"7c4e25e5c87b","ui_kits/phone/app.jsx":"0c1e83779994","ui_kits/phone/data.jsx":"a5314bab50c2","ui_kits/wear/WatchFrame.jsx":"08e9cec9a66d","ui_kits/wear/WearRemote.jsx":"7aa19057e20a"},"inlinedExternals":[],"unexposedExports":[]} */

(() => {

const __ds_ns = (window.RepForthDesignSystem_c95a40 = window.RepForthDesignSystem_c95a40 || {});

const __ds_scope = {};

(__ds_ns.__errors = __ds_ns.__errors || []);

// components/core/Card.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
function Card({
  variant = "filled",
  size = "md",
  flush = false,
  interactive = false,
  as,
  children,
  className = "",
  ...rest
}) {
  const Tag = as || (interactive ? "button" : "div");
  const cls = ["rf-card", variant !== "filled" ? "rf-card--" + variant : "", size === "lg" ? "rf-card--lg" : "", flush ? "rf-card--flush" : "", interactive ? "rf-card--interactive" : "", className].filter(Boolean).join(" ");
  return /*#__PURE__*/React.createElement(Tag, _extends({
    className: cls
  }, Tag === "button" ? {
    type: "button"
  } : {}, rest), children);
}
Object.assign(__ds_scope, { Card });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/core/Card.jsx", error: String((e && e.message) || e) }); }

// components/core/Divider.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
function Divider({
  inset = false,
  vertical = false,
  className = "",
  ...rest
}) {
  const cls = ["rf-divider", inset ? "rf-divider--inset" : "", vertical ? "rf-divider--vertical" : "", className].filter(Boolean).join(" ");
  return vertical ? /*#__PURE__*/React.createElement("span", _extends({
    className: cls,
    role: "separator",
    "aria-orientation": "vertical"
  }, rest)) : /*#__PURE__*/React.createElement("hr", _extends({
    className: cls
  }, rest));
}
Object.assign(__ds_scope, { Divider });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/core/Divider.jsx", error: String((e && e.message) || e) }); }

// components/core/Icon.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/* Material Symbols Rounded wrapper. The only icon system in RepForth. */
function Icon({
  name,
  size = 24,
  fill = false,
  weight = 400,
  color,
  className = "",
  label,
  style,
  ...rest
}) {
  const cls = ["rf-icon", className].filter(Boolean).join(" ");
  const vf = '"FILL" ' + (fill ? 1 : 0) + ',"wght" ' + weight + ',"GRAD" 0,"opsz" ' + size;
  return /*#__PURE__*/React.createElement("span", _extends({
    className: cls,
    "aria-hidden": label ? undefined : true,
    role: label ? "img" : undefined,
    "aria-label": label,
    style: {
      fontSize: size,
      width: size,
      height: size,
      color: color,
      fontVariationSettings: vf,
      ...style
    }
  }, rest), name);
}
Object.assign(__ds_scope, { Icon });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/core/Icon.jsx", error: String((e && e.message) || e) }); }

// components/core/Badge.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
function Badge({
  label,
  icon,
  tone = "neutral",
  dot = false,
  className = "",
  ...rest
}) {
  const cls = ["rf-badge", tone !== "neutral" ? "rf-badge--" + tone : "", dot ? "rf-badge--dot" : "", className].filter(Boolean).join(" ");
  if (dot) return /*#__PURE__*/React.createElement("span", _extends({
    className: cls,
    role: "img",
    "aria-label": label
  }, rest));
  return /*#__PURE__*/React.createElement("span", _extends({
    className: cls
  }, rest), icon ? /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: icon,
    size: 14,
    weight: 600
  }) : null, /*#__PURE__*/React.createElement("span", null, label));
}
Object.assign(__ds_scope, { Badge });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/core/Badge.jsx", error: String((e && e.message) || e) }); }

// components/core/Button.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
function Button({
  variant = "filled",
  size = "md",
  icon,
  trailingIcon,
  fullWidth = false,
  disabled = false,
  children,
  className = "",
  type = "button",
  ...rest
}) {
  const cls = ["rf-btn", "rf-btn--" + variant, "rf-btn--" + size, fullWidth ? "rf-btn--block" : "", className].filter(Boolean).join(" ");
  const glyph = size === "session" ? 28 : size === "sm" ? 18 : 20;
  return /*#__PURE__*/React.createElement("button", _extends({
    type: type,
    className: cls,
    disabled: disabled
  }, rest), icon ? /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: icon,
    size: glyph,
    weight: 600
  }) : null, /*#__PURE__*/React.createElement("span", {
    className: "rf-btn__label"
  }, children), trailingIcon ? /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: trailingIcon,
    size: glyph,
    weight: 600
  }) : null);
}
Object.assign(__ds_scope, { Button });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/core/Button.jsx", error: String((e && e.message) || e) }); }

// components/core/Chip.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
function Chip({
  icon,
  label,
  count,
  selected,
  size = "md",
  trailingIcon,
  onClick,
  className = "",
  ...rest
}) {
  const isToggle = selected !== undefined;
  const isStatic = !onClick && !isToggle;
  const cls = ["rf-chip", size === "sm" ? "rf-chip--sm" : "", isStatic ? "rf-chip--static" : "", selected ? "rf-chip--selected" : "", className].filter(Boolean).join(" ");
  const glyph = size === "sm" ? 16 : 18;
  const inner = /*#__PURE__*/React.createElement(React.Fragment, null, selected ? /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: "check",
    size: glyph,
    weight: 700
  }) : icon ? /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: icon,
    size: glyph
  }) : null, /*#__PURE__*/React.createElement("span", null, label), count !== undefined ? /*#__PURE__*/React.createElement("span", {
    className: "rf-chip__count"
  }, count) : null, trailingIcon ? /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: trailingIcon,
    size: glyph
  }) : null);
  // Read-only tags render as a span: they are often nested inside clickable
  // cards and rows, where a nested <button> would be invalid HTML.
  if (isStatic) return /*#__PURE__*/React.createElement("span", _extends({
    className: cls
  }, rest), inner);
  return /*#__PURE__*/React.createElement("button", _extends({
    type: "button",
    className: cls,
    "aria-pressed": isToggle ? !!selected : undefined,
    onClick: onClick
  }, rest), inner);
}
Object.assign(__ds_scope, { Chip });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/core/Chip.jsx", error: String((e && e.message) || e) }); }

// components/core/FAB.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
function FAB({
  icon,
  label,
  extended = false,
  size = "regular",
  className = "",
  ...rest
}) {
  const cls = ["rf-fab", extended ? "rf-fab--extended" : "", size === "large" ? "rf-fab--lg" : "", className].filter(Boolean).join(" ");
  return /*#__PURE__*/React.createElement("button", _extends({
    type: "button",
    className: cls,
    "aria-label": extended ? undefined : label
  }, rest), /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: icon,
    size: size === "large" ? 32 : 24,
    weight: 600
  }), extended ? /*#__PURE__*/React.createElement("span", null, label) : null);
}
Object.assign(__ds_scope, { FAB });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/core/FAB.jsx", error: String((e && e.message) || e) }); }

// components/core/IconButton.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
function IconButton({
  icon,
  label,
  variant = "standard",
  size = "md",
  selected,
  disabled = false,
  className = "",
  ...rest
}) {
  const cls = ["rf-iconbtn", variant !== "standard" ? "rf-iconbtn--" + variant : "", size !== "md" ? "rf-iconbtn--" + size : "", className].filter(Boolean).join(" ");
  const glyph = size === "session" ? 32 : size === "lg" ? 28 : 24;
  return /*#__PURE__*/React.createElement("button", _extends({
    type: "button",
    className: cls,
    "aria-label": label,
    "aria-pressed": selected === undefined ? undefined : selected,
    disabled: disabled
  }, rest), /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: icon,
    size: glyph,
    fill: !!selected,
    weight: selected ? 600 : 400
  }));
}
Object.assign(__ds_scope, { IconButton });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/core/IconButton.jsx", error: String((e && e.message) || e) }); }

// components/core/StatBlock.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
function StatBlock({
  value,
  unit,
  label,
  size = "md",
  tone = "default",
  align = "start",
  className = "",
  ...rest
}) {
  const cls = ["rf-stat", align === "center" ? "rf-stat--center" : "", tone === "accent" ? "rf-stat--accent" : "", className].filter(Boolean).join(" ");
  return /*#__PURE__*/React.createElement("div", _extends({
    className: cls
  }, rest), /*#__PURE__*/React.createElement("span", {
    className: "rf-stat__value",
    style: {
      fontSize: "var(--numeric-" + size + ")"
    }
  }, value, unit ? /*#__PURE__*/React.createElement("span", {
    className: "rf-stat__unit"
  }, unit) : null), label ? /*#__PURE__*/React.createElement("span", {
    className: "rf-stat__label"
  }, label) : null);
}
Object.assign(__ds_scope, { StatBlock });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/core/StatBlock.jsx", error: String((e && e.message) || e) }); }

// components/feedback/Dialog.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
function Dialog({
  open = true,
  title,
  children,
  actions,
  onDismiss,
  sheet = false,
  className = "",
  ...rest
}) {
  if (!open) return null;
  return /*#__PURE__*/React.createElement("div", {
    className: "rf-dialog-layer",
    style: sheet ? {
      alignItems: "flex-end",
      padding: 0
    } : undefined
  }, /*#__PURE__*/React.createElement("div", {
    className: "rf-scrim",
    onClick: onDismiss
  }), /*#__PURE__*/React.createElement("div", _extends({
    className: [sheet ? "rf-sheet" : "rf-dialog", className].filter(Boolean).join(" "),
    role: "dialog",
    "aria-modal": "true",
    "aria-label": title
  }, rest), sheet ? /*#__PURE__*/React.createElement("div", {
    className: "rf-sheet__grip"
  }) : null, title ? /*#__PURE__*/React.createElement("div", {
    className: "rf-dialog__title"
  }, title) : null, children ? /*#__PURE__*/React.createElement("div", {
    className: "rf-dialog__body"
  }, children) : null, actions ? /*#__PURE__*/React.createElement("div", {
    className: "rf-dialog__actions"
  }, actions) : null));
}
Object.assign(__ds_scope, { Dialog });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/feedback/Dialog.jsx", error: String((e && e.message) || e) }); }

// components/feedback/EmptyState.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
function EmptyState({
  icon = "fitness_center",
  title,
  body,
  action,
  className = "",
  ...rest
}) {
  return /*#__PURE__*/React.createElement("div", _extends({
    className: ["rf-empty", className].filter(Boolean).join(" ")
  }, rest), /*#__PURE__*/React.createElement("div", {
    className: "rf-empty__icon"
  }, /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: icon,
    size: 32
  })), /*#__PURE__*/React.createElement("div", {
    className: "rf-empty__title"
  }, title), body ? /*#__PURE__*/React.createElement("div", {
    className: "rf-empty__body"
  }, body) : null, action);
}
Object.assign(__ds_scope, { EmptyState });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/feedback/EmptyState.jsx", error: String((e && e.message) || e) }); }

// components/feedback/ProgressBar.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
function ProgressBar({
  value = 0,
  total,
  label,
  showValue = true,
  segmented = false,
  current,
  className = "",
  ...rest
}) {
  const pct = total ? Math.min(1, value / total) : Math.min(1, Math.max(0, value));
  const cls = ["rf-bar", segmented ? "rf-bar--segmented" : "", className].filter(Boolean).join(" ");
  return /*#__PURE__*/React.createElement("div", _extends({
    className: cls
  }, rest), label || showValue ? /*#__PURE__*/React.createElement("div", {
    className: "rf-bar__head"
  }, label ? /*#__PURE__*/React.createElement("span", {
    className: "rf-bar__label"
  }, label) : null, showValue ? /*#__PURE__*/React.createElement("span", {
    className: "rf-bar__value"
  }, total ? value + " / " + total : Math.round(pct * 100) + "%") : null) : null, /*#__PURE__*/React.createElement("div", {
    className: "rf-bar__track",
    role: "progressbar",
    "aria-valuenow": value,
    "aria-valuemin": 0,
    "aria-valuemax": total || 1,
    "aria-label": label
  }, segmented && total ? Array.from({
    length: total
  }, (_, i) => /*#__PURE__*/React.createElement("span", {
    key: i,
    className: ["rf-bar__seg", i < value ? "rf-bar__seg--on" : "", current === i ? "rf-bar__seg--current" : ""].filter(Boolean).join(" ")
  })) : /*#__PURE__*/React.createElement("div", {
    className: "rf-bar__fill",
    style: {
      width: pct * 100 + "%"
    }
  })));
}
Object.assign(__ds_scope, { ProgressBar });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/feedback/ProgressBar.jsx", error: String((e && e.message) || e) }); }

// components/feedback/ProgressRing.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
function ProgressRing({
  value = 0,
  size = 200,
  stroke,
  tone = "accent",
  label,
  children,
  className = "",
  ...rest
}) {
  const w = stroke || Math.max(6, Math.round(size * 0.06));
  const r = (size - w) / 2;
  const c = 2 * Math.PI * r;
  const clamped = Math.min(1, Math.max(0, value));
  const cls = ["rf-ring", tone === "rest" ? "rf-ring--rest" : tone === "done" ? "rf-ring--done" : "", className].filter(Boolean).join(" ");
  return /*#__PURE__*/React.createElement("div", _extends({
    className: cls,
    style: {
      width: size,
      height: size
    },
    role: "img",
    "aria-label": label
  }, rest), /*#__PURE__*/React.createElement("svg", {
    width: size,
    height: size,
    viewBox: "0 0 " + size + " " + size
  }, /*#__PURE__*/React.createElement("circle", {
    className: "rf-ring__track",
    cx: size / 2,
    cy: size / 2,
    r: r,
    strokeWidth: w
  }), /*#__PURE__*/React.createElement("circle", {
    className: "rf-ring__fill",
    cx: size / 2,
    cy: size / 2,
    r: r,
    strokeWidth: w,
    strokeDasharray: c,
    strokeDashoffset: c * (1 - clamped)
  })), /*#__PURE__*/React.createElement("div", {
    className: "rf-ring__center"
  }, children));
}
Object.assign(__ds_scope, { ProgressRing });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/feedback/ProgressRing.jsx", error: String((e && e.message) || e) }); }

// components/feedback/Snackbar.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
function Snackbar({
  message,
  actionLabel,
  onAction,
  icon,
  tone = "default",
  className = "",
  ...rest
}) {
  return /*#__PURE__*/React.createElement("div", _extends({
    className: ["rf-snack", tone === "error" ? "rf-snack--error" : "", className].filter(Boolean).join(" "),
    role: "status"
  }, rest), icon ? /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: icon,
    size: 20
  }) : null, /*#__PURE__*/React.createElement("span", {
    className: "rf-snack__msg"
  }, message), actionLabel ? /*#__PURE__*/React.createElement("button", {
    type: "button",
    className: "rf-snack__action",
    onClick: onAction
  }, actionLabel) : null);
}
Object.assign(__ds_scope, { Snackbar });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/feedback/Snackbar.jsx", error: String((e && e.message) || e) }); }

// components/forms/Checkbox.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
function Checkbox({
  label,
  description,
  checked = false,
  onChange,
  disabled = false,
  className = "",
  ...rest
}) {
  return /*#__PURE__*/React.createElement("button", _extends({
    type: "button",
    role: "checkbox",
    "aria-checked": checked,
    disabled: disabled,
    onClick: () => onChange && onChange(!checked),
    className: ["rf-check", className].filter(Boolean).join(" ")
  }, rest), /*#__PURE__*/React.createElement("span", {
    className: "rf-check__box"
  }, /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: "check",
    size: 18,
    weight: 700
  })), /*#__PURE__*/React.createElement("span", {
    className: "rf-check__text"
  }, /*#__PURE__*/React.createElement("span", null, label), description ? /*#__PURE__*/React.createElement("span", {
    className: "rf-check__desc"
  }, description) : null));
}
Object.assign(__ds_scope, { Checkbox });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/forms/Checkbox.jsx", error: String((e && e.message) || e) }); }

// components/forms/Radio.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
function Radio({
  label,
  description,
  checked = false,
  onChange,
  name,
  disabled = false,
  className = "",
  ...rest
}) {
  return /*#__PURE__*/React.createElement("button", _extends({
    type: "button",
    role: "radio",
    "aria-checked": checked,
    name: name,
    disabled: disabled,
    onClick: () => onChange && onChange(true),
    className: ["rf-check", className].filter(Boolean).join(" ")
  }, rest), /*#__PURE__*/React.createElement("span", {
    className: "rf-check__box rf-check__box--radio"
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      width: 12,
      height: 12,
      borderRadius: 999,
      background: checked ? "var(--color-primary)" : "transparent"
    }
  })), /*#__PURE__*/React.createElement("span", {
    className: "rf-check__text"
  }, /*#__PURE__*/React.createElement("span", null, label), description ? /*#__PURE__*/React.createElement("span", {
    className: "rf-check__desc"
  }, description) : null));
}
Object.assign(__ds_scope, { Radio });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/forms/Radio.jsx", error: String((e && e.message) || e) }); }

// components/forms/SegmentedButtons.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
function SegmentedButtons({
  options = [],
  value,
  onChange,
  label,
  className = "",
  ...rest
}) {
  return /*#__PURE__*/React.createElement("div", _extends({
    className: ["rf-seg", className].filter(Boolean).join(" "),
    role: "group",
    "aria-label": label
  }, rest), options.map(o => {
    const val = typeof o === "string" ? o : o.value;
    const lab = typeof o === "string" ? o : o.label;
    const icon = typeof o === "string" ? null : o.icon;
    const on = val === value;
    return /*#__PURE__*/React.createElement("button", {
      key: val,
      type: "button",
      className: "rf-seg__item",
      "aria-pressed": on,
      onClick: () => onChange && onChange(val)
    }, on ? /*#__PURE__*/React.createElement(__ds_scope.Icon, {
      name: "check",
      size: 16,
      weight: 700
    }) : icon ? /*#__PURE__*/React.createElement(__ds_scope.Icon, {
      name: icon,
      size: 16
    }) : null, /*#__PURE__*/React.createElement("span", null, lab));
  }));
}
Object.assign(__ds_scope, { SegmentedButtons });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/forms/SegmentedButtons.jsx", error: String((e && e.message) || e) }); }

// components/forms/SelectField.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
function SelectField({
  label,
  value,
  onChange,
  options = [],
  icon,
  helper,
  id,
  className = "",
  ...rest
}) {
  const fid = id || "rf-sel-" + (label || "select").replace(/\s+/g, "-").toLowerCase();
  return /*#__PURE__*/React.createElement("div", {
    className: ["rf-field", className].filter(Boolean).join(" ")
  }, label ? /*#__PURE__*/React.createElement("label", {
    className: "rf-field__label",
    htmlFor: fid
  }, label) : null, /*#__PURE__*/React.createElement("div", {
    className: "rf-field__box"
  }, icon ? /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: icon,
    size: 20
  }) : null, /*#__PURE__*/React.createElement("select", _extends({
    id: fid,
    value: value,
    onChange: onChange
  }, rest), options.map(o => {
    const val = typeof o === "string" ? o : o.value;
    const lab = typeof o === "string" ? o : o.label;
    return /*#__PURE__*/React.createElement("option", {
      key: val,
      value: val
    }, lab);
  })), /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: "expand_more",
    size: 20
  })), helper ? /*#__PURE__*/React.createElement("span", {
    className: "rf-field__help"
  }, helper) : null);
}
Object.assign(__ds_scope, { SelectField });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/forms/SelectField.jsx", error: String((e && e.message) || e) }); }

// components/forms/Slider.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
function Slider({
  label,
  value,
  min = 0,
  max = 100,
  step = 1,
  unit,
  onChange,
  format,
  className = "",
  ...rest
}) {
  const shown = format ? format(value) : value + (unit ? " " + unit : "");
  const pct = max === min ? 0 : (value - min) / (max - min) * 100;
  return /*#__PURE__*/React.createElement("div", {
    className: ["rf-slider", className].filter(Boolean).join(" ")
  }, /*#__PURE__*/React.createElement("div", {
    className: "rf-slider__head"
  }, /*#__PURE__*/React.createElement("span", {
    className: "rf-field__label"
  }, label), /*#__PURE__*/React.createElement("span", {
    className: "rf-slider__val"
  }, shown)), /*#__PURE__*/React.createElement("div", {
    className: "rf-slider__rail"
  }, /*#__PURE__*/React.createElement("span", {
    className: "rf-slider__track"
  }), /*#__PURE__*/React.createElement("span", {
    className: "rf-slider__fill",
    style: {
      width: pct + "%"
    }
  }), /*#__PURE__*/React.createElement("input", _extends({
    className: "rf-slider__input",
    type: "range",
    value: value,
    min: min,
    max: max,
    step: step,
    "aria-label": label,
    "aria-valuetext": String(shown),
    onChange: e => onChange && onChange(Number(e.target.value))
  }, rest)), /*#__PURE__*/React.createElement("span", {
    className: "rf-slider__knob",
    style: {
      left: pct + "%"
    }
  })));
}
Object.assign(__ds_scope, { Slider });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/forms/Slider.jsx", error: String((e && e.message) || e) }); }

// components/forms/Stepper.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
function Stepper({
  value,
  unit,
  step = 2.5,
  min = 0,
  max = 9999,
  onChange,
  size = "md",
  label,
  className = "",
  ...rest
}) {
  const set = v => onChange && onChange(Math.min(max, Math.max(min, Math.round(v * 100) / 100)));
  const cls = ["rf-stepper", size === "session" ? "rf-stepper--session" : "", className].filter(Boolean).join(" ");
  const btn = size === "session" ? 32 : 24;
  return /*#__PURE__*/React.createElement("div", _extends({
    className: cls,
    role: "group",
    "aria-label": label
  }, rest), /*#__PURE__*/React.createElement("button", {
    type: "button",
    className: "rf-stepper__btn",
    "aria-label": "Decrease",
    onClick: () => set(value - step),
    disabled: value <= min
  }, /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: "remove",
    size: btn,
    weight: 600
  })), /*#__PURE__*/React.createElement("span", {
    className: "rf-stepper__value",
    "aria-live": "polite"
  }, value, unit ? /*#__PURE__*/React.createElement("span", {
    className: "rf-stepper__unit"
  }, unit) : null), /*#__PURE__*/React.createElement("button", {
    type: "button",
    className: "rf-stepper__btn",
    "aria-label": "Increase",
    onClick: () => set(value + step),
    disabled: value >= max
  }, /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: "add",
    size: btn,
    weight: 600
  })));
}
Object.assign(__ds_scope, { Stepper });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/forms/Stepper.jsx", error: String((e && e.message) || e) }); }

// components/forms/Switch.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
function Switch({
  label,
  description,
  checked = false,
  onChange,
  disabled = false,
  className = "",
  ...rest
}) {
  return /*#__PURE__*/React.createElement("button", _extends({
    type: "button",
    role: "switch",
    "aria-checked": checked,
    disabled: disabled,
    onClick: () => onChange && onChange(!checked),
    className: ["rf-switch", className].filter(Boolean).join(" ")
  }, rest), /*#__PURE__*/React.createElement("span", {
    className: "rf-check__text"
  }, /*#__PURE__*/React.createElement("span", null, label), description ? /*#__PURE__*/React.createElement("span", {
    className: "rf-check__desc"
  }, description) : null), /*#__PURE__*/React.createElement("span", {
    className: "rf-switch__track"
  }, /*#__PURE__*/React.createElement("span", {
    className: "rf-switch__thumb"
  }, /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: "check",
    size: 16,
    weight: 700
  }))));
}
Object.assign(__ds_scope, { Switch });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/forms/Switch.jsx", error: String((e && e.message) || e) }); }

// components/forms/TextField.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
function TextField({
  label,
  value,
  onChange,
  placeholder,
  icon,
  suffix,
  helper,
  error,
  numeric = false,
  type = "text",
  id,
  className = "",
  ...rest
}) {
  const fid = id || "rf-tf-" + (label || placeholder || "field").replace(/\s+/g, "-").toLowerCase();
  const cls = ["rf-field", numeric ? "rf-field--numeric" : "", error ? "rf-field--error" : "", className].filter(Boolean).join(" ");
  return /*#__PURE__*/React.createElement("div", {
    className: cls
  }, label ? /*#__PURE__*/React.createElement("label", {
    className: "rf-field__label",
    htmlFor: fid
  }, label) : null, /*#__PURE__*/React.createElement("div", {
    className: "rf-field__box"
  }, icon ? /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: icon,
    size: 20
  }) : null, /*#__PURE__*/React.createElement("input", _extends({
    id: fid,
    type: type,
    value: value,
    placeholder: placeholder,
    onChange: onChange,
    inputMode: numeric ? "decimal" : undefined,
    "aria-invalid": error ? true : undefined
  }, rest)), suffix ? /*#__PURE__*/React.createElement("span", {
    className: "rf-field__affix"
  }, suffix) : null), error || helper ? /*#__PURE__*/React.createElement("span", {
    className: "rf-field__help"
  }, error ? /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: "error",
    size: 16
  }) : null, error || helper) : null);
}
Object.assign(__ds_scope, { TextField });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/forms/TextField.jsx", error: String((e && e.message) || e) }); }

// components/navigation/ListItem.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
function ListItem({
  title,
  subtitle,
  media,
  mediaIcon,
  trailing,
  trailingIcon,
  onClick,
  className = "",
  ...rest
}) {
  const Tag = onClick ? "button" : "div";
  return /*#__PURE__*/React.createElement(Tag, _extends({
    type: onClick ? "button" : undefined,
    className: ["rf-row", onClick ? "" : "rf-row--static", className].filter(Boolean).join(" "),
    onClick: onClick
  }, rest), media || mediaIcon ? /*#__PURE__*/React.createElement("span", {
    className: "rf-row__media"
  }, media ? media : /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: mediaIcon,
    size: 24
  })) : null, /*#__PURE__*/React.createElement("span", {
    className: "rf-row__text"
  }, /*#__PURE__*/React.createElement("span", {
    className: "rf-row__title"
  }, title), subtitle ? /*#__PURE__*/React.createElement("span", {
    className: "rf-row__sub"
  }, subtitle) : null), trailing || trailingIcon ? /*#__PURE__*/React.createElement("span", {
    className: "rf-row__trail"
  }, trailing, trailingIcon ? /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: trailingIcon,
    size: 24
  }) : null) : null);
}
Object.assign(__ds_scope, { ListItem });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/navigation/ListItem.jsx", error: String((e && e.message) || e) }); }

// components/navigation/NavigationBar.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
function NavigationBar({
  items = [],
  value,
  onChange,
  className = "",
  ...rest
}) {
  return /*#__PURE__*/React.createElement("nav", _extends({
    className: ["rf-navbar", className].filter(Boolean).join(" ")
  }, rest), items.map(it => {
    const on = it.value === value;
    return /*#__PURE__*/React.createElement("button", {
      key: it.value,
      type: "button",
      className: "rf-navbar__item",
      "aria-current": on ? "page" : undefined,
      onClick: () => onChange && onChange(it.value)
    }, /*#__PURE__*/React.createElement("span", {
      className: "rf-navbar__pill"
    }, /*#__PURE__*/React.createElement(__ds_scope.Icon, {
      name: it.icon,
      size: 24,
      fill: on,
      weight: on ? 600 : 400
    })), /*#__PURE__*/React.createElement("span", {
      className: "rf-navbar__label"
    }, it.label));
  }));
}
Object.assign(__ds_scope, { NavigationBar });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/navigation/NavigationBar.jsx", error: String((e && e.message) || e) }); }

// components/navigation/Tabs.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
function Tabs({
  items = [],
  value,
  onChange,
  scrollable = false,
  className = "",
  ...rest
}) {
  return /*#__PURE__*/React.createElement("div", _extends({
    className: ["rf-tabs", scrollable ? "rf-tabs--scroll" : "", className].filter(Boolean).join(" "),
    role: "tablist"
  }, rest), items.map(it => {
    const on = it.value === value;
    return /*#__PURE__*/React.createElement("button", {
      key: it.value,
      type: "button",
      role: "tab",
      "aria-selected": on,
      className: "rf-tabs__item",
      onClick: () => onChange && onChange(it.value)
    }, it.icon ? /*#__PURE__*/React.createElement(__ds_scope.Icon, {
      name: it.icon,
      size: 18,
      fill: on
    }) : null, /*#__PURE__*/React.createElement("span", null, it.label), it.count !== undefined ? /*#__PURE__*/React.createElement("span", {
      style: {
        fontFamily: "var(--font-numeric)",
        fontVariantNumeric: "tabular-nums",
        opacity: .75
      }
    }, it.count) : null);
  }));
}
Object.assign(__ds_scope, { Tabs });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/navigation/Tabs.jsx", error: String((e && e.message) || e) }); }

// components/navigation/TopAppBar.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
function TopAppBar({
  title,
  subtitle,
  leadingIcon,
  onLeading,
  leadingLabel = "Back",
  actions = [],
  large = false,
  scrolled = false,
  className = "",
  ...rest
}) {
  const cls = ["rf-appbar", large ? "rf-appbar--large" : "", scrolled ? "rf-appbar--scrolled" : "", className].filter(Boolean).join(" ");
  const trailing = actions.map(a => /*#__PURE__*/React.createElement(__ds_scope.IconButton, {
    key: a.icon + a.label,
    icon: a.icon,
    label: a.label,
    onClick: a.onClick,
    selected: a.selected
  }));
  if (large) {
    return /*#__PURE__*/React.createElement("div", _extends({
      className: cls
    }, rest), /*#__PURE__*/React.createElement("div", {
      className: "rf-appbar__row"
    }, leadingIcon ? /*#__PURE__*/React.createElement(__ds_scope.IconButton, {
      icon: leadingIcon,
      label: leadingLabel,
      onClick: onLeading
    }) : /*#__PURE__*/React.createElement("span", {
      style: {
        width: 8
      }
    }), /*#__PURE__*/React.createElement("span", {
      style: {
        flex: 1
      }
    }), trailing), /*#__PURE__*/React.createElement("div", {
      className: "rf-appbar__display"
    }, title), subtitle ? /*#__PURE__*/React.createElement("span", {
      className: "rf-appbar__sub",
      style: {
        padding: "0 var(--space-4)"
      }
    }, subtitle) : null);
  }
  return /*#__PURE__*/React.createElement("div", _extends({
    className: cls
  }, rest), leadingIcon ? /*#__PURE__*/React.createElement(__ds_scope.IconButton, {
    icon: leadingIcon,
    label: leadingLabel,
    onClick: onLeading
  }) : null, /*#__PURE__*/React.createElement("div", {
    className: "rf-appbar__title"
  }, title, subtitle ? /*#__PURE__*/React.createElement("span", {
    className: "rf-appbar__sub"
  }, subtitle) : null), trailing);
}
Object.assign(__ds_scope, { TopAppBar });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/navigation/TopAppBar.jsx", error: String((e && e.message) || e) }); }

// components/wear/WearAction.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
function WearAction({
  actions = [],
  className = "",
  ...rest
}) {
  return /*#__PURE__*/React.createElement("div", _extends({
    className: ["rf-wear-act", className].filter(Boolean).join(" ")
  }, rest), actions.map(a => /*#__PURE__*/React.createElement("button", {
    key: a.icon + a.label,
    type: "button",
    "aria-label": a.label,
    onClick: a.onClick,
    className: ["rf-wear-act__btn", a.tone === "primary" ? "rf-wear-act__btn--primary" : a.tone === "danger" ? "rf-wear-act__btn--danger" : ""].filter(Boolean).join(" ")
  }, /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: a.icon,
    size: a.tone === "primary" ? 30 : 22,
    weight: 600
  }))));
}
Object.assign(__ds_scope, { WearAction });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/wear/WearAction.jsx", error: String((e && e.message) || e) }); }

// components/wear/WearArc.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
function WearArc({
  value = 0,
  tone = "accent",
  stroke = 3.5,
  inset = 3,
  className = "",
  ...rest
}) {
  const size = 100;
  const r = (size - stroke) / 2 - inset;
  const c = 2 * Math.PI * r;
  const clamped = Math.min(1, Math.max(0, value));
  return /*#__PURE__*/React.createElement("div", _extends({
    className: ["rf-wear-arc", tone === "rest" ? "rf-wear-arc--rest" : "", className].filter(Boolean).join(" ")
  }, rest), /*#__PURE__*/React.createElement("svg", {
    viewBox: "0 0 " + size + " " + size,
    preserveAspectRatio: "none"
  }, /*#__PURE__*/React.createElement("circle", {
    className: "rf-wear-arc__track",
    cx: size / 2,
    cy: size / 2,
    r: r,
    strokeWidth: stroke
  }), /*#__PURE__*/React.createElement("circle", {
    className: "rf-wear-arc__fill",
    cx: size / 2,
    cy: size / 2,
    r: r,
    strokeWidth: stroke,
    strokeDasharray: c,
    strokeDashoffset: c * (1 - clamped)
  })));
}
Object.assign(__ds_scope, { WearArc });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/wear/WearArc.jsx", error: String((e && e.message) || e) }); }

// components/wear/WearListItem.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
function WearListItem({
  icon,
  label,
  value,
  primary = false,
  onClick,
  className = "",
  ...rest
}) {
  return /*#__PURE__*/React.createElement("button", _extends({
    type: "button",
    className: ["rf-wear-row", primary ? "rf-wear-row--primary" : "", className].filter(Boolean).join(" "),
    onClick: onClick
  }, rest), icon ? /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: icon,
    size: 18,
    weight: 600
  }) : null, /*#__PURE__*/React.createElement("span", {
    className: "rf-wear-row__text"
  }, label), value !== undefined ? /*#__PURE__*/React.createElement("span", {
    className: "rf-wear-row__num"
  }, value) : null);
}
function WearList({
  children,
  className = "",
  ...rest
}) {
  return /*#__PURE__*/React.createElement("div", _extends({
    className: ["rf-wear-list", className].filter(Boolean).join(" ")
  }, rest), children);
}
Object.assign(__ds_scope, { WearListItem, WearList });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/wear/WearListItem.jsx", error: String((e && e.message) || e) }); }

// components/wear/WearScreen.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
function WearScreen({
  shape = "round",
  size = 208,
  ambient = false,
  children,
  className = "",
  ...rest
}) {
  const cls = ["rf-wear", shape === "round" ? "rf-wear--round" : "rf-wear--square", className].filter(Boolean).join(" ");
  return /*#__PURE__*/React.createElement("div", _extends({
    className: cls,
    "data-ambient": ambient ? "true" : "false",
    style: {
      width: size,
      height: size
    }
  }, rest), children);
}
function WearBody({
  children,
  className = "",
  ...rest
}) {
  return /*#__PURE__*/React.createElement("div", _extends({
    className: ["rf-wear__inner", className].filter(Boolean).join(" ")
  }, rest), children);
}
function WearValue({
  value,
  unit,
  caption,
  size = 44,
  className = "",
  ...rest
}) {
  return /*#__PURE__*/React.createElement("div", _extends({
    className: className
  }, rest, {
    style: {
      display: "flex",
      flexDirection: "column",
      alignItems: "center",
      gap: 2
    }
  }), /*#__PURE__*/React.createElement("span", {
    className: "rf-wear__value",
    style: {
      fontSize: size
    }
  }, value, unit ? /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 13,
      fontFamily: "var(--font-ui)",
      fontWeight: 600,
      marginLeft: 3,
      color: "var(--n-70)"
    }
  }, unit) : null), caption ? /*#__PURE__*/React.createElement("span", {
    className: "rf-wear__sub"
  }, caption) : null);
}
Object.assign(__ds_scope, { WearScreen, WearBody, WearValue });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/wear/WearScreen.jsx", error: String((e && e.message) || e) }); }

// components/workout/ExerciseCard.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
function ExerciseCard({
  name,
  media,
  tags = [],
  sets,
  reps,
  layout = "row",
  trailing,
  onClick,
  className = "",
  ...rest
}) {
  const thumb = /*#__PURE__*/React.createElement("span", {
    className: ["rf-ex__media", layout === "stacked" ? "rf-ex__media--lg" : ""].filter(Boolean).join(" ")
  }, media ? media : /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: "fitness_center",
    size: 28
  }));
  return /*#__PURE__*/React.createElement(__ds_scope.Card, _extends({
    interactive: !!onClick,
    onClick: onClick,
    className: className
  }, rest), /*#__PURE__*/React.createElement("div", {
    className: "rf-ex",
    style: layout === "stacked" ? {
      flexDirection: "column",
      alignItems: "stretch"
    } : undefined
  }, thumb, /*#__PURE__*/React.createElement("div", {
    className: "rf-ex__body"
  }, /*#__PURE__*/React.createElement("span", {
    className: "rf-ex__name"
  }, name), tags.length ? /*#__PURE__*/React.createElement("div", {
    className: "rf-ex__chips"
  }, tags.map(t => /*#__PURE__*/React.createElement(__ds_scope.Chip, {
    key: t.label,
    icon: t.icon,
    label: t.label,
    size: "sm"
  }))) : null, sets !== undefined ? /*#__PURE__*/React.createElement("span", {
    className: "rf-ex__sets"
  }, sets, /*#__PURE__*/React.createElement("span", {
    className: "rf-unit"
  }, "sets"), reps !== undefined ? /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement("span", {
    style: {
      color: "var(--text-quiet)",
      margin: "0 2px"
    }
  }, "\xD7"), reps, /*#__PURE__*/React.createElement("span", {
    className: "rf-unit"
  }, "reps")) : null) : null), trailing ? /*#__PURE__*/React.createElement("span", {
    className: "rf-row__trail"
  }, trailing) : null));
}
Object.assign(__ds_scope, { ExerciseCard });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/workout/ExerciseCard.jsx", error: String((e && e.message) || e) }); }

// components/workout/PlanCard.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
function PlanCard({
  name,
  exercises,
  exercisesLabel = "exercises",
  minutes,
  minutesLabel = "min",
  muscles = [],
  badge,
  today = false,
  onClick,
  action,
  className = "",
  ...rest
}) {
  const seen = {};
  const tags = muscles.filter(m => {
    const k = m.id || m.label;
    if (seen[k]) return false;
    seen[k] = true;
    return true;
  });
  return /*#__PURE__*/React.createElement(__ds_scope.Card, _extends({
    size: "lg",
    interactive: !!onClick,
    onClick: onClick,
    className: ["rf-plan", today ? "rf-plan--today" : "", className].filter(Boolean).join(" ")
  }, rest), /*#__PURE__*/React.createElement("div", {
    className: "rf-plan__head"
  }, /*#__PURE__*/React.createElement("span", {
    className: "rf-plan__title"
  }, name), badge ? /*#__PURE__*/React.createElement(__ds_scope.Badge, {
    tone: today ? "accent" : "neutral",
    icon: today ? "bolt" : undefined,
    label: badge
  }) : null), /*#__PURE__*/React.createElement("div", {
    className: "rf-plan__meta"
  }, /*#__PURE__*/React.createElement(__ds_scope.StatBlock, {
    value: exercises,
    unit: exercisesLabel,
    size: "xs"
  }), /*#__PURE__*/React.createElement("span", {
    style: {
      display: "flex",
      alignItems: "center",
      gap: 6,
      color: "var(--text-quiet)"
    }
  }, /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: "schedule",
    size: 16
  }), /*#__PURE__*/React.createElement("span", {
    className: "rf-label"
  }, minutes, " ", minutesLabel))), tags.length ? /*#__PURE__*/React.createElement("div", {
    className: "rf-plan__chips"
  }, tags.map(m => /*#__PURE__*/React.createElement(__ds_scope.Chip, {
    key: m.id || m.label,
    icon: m.icon,
    label: m.label,
    size: "sm"
  }))) : null, action);
}
Object.assign(__ds_scope, { PlanCard });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/workout/PlanCard.jsx", error: String((e && e.message) || e) }); }

// components/workout/RestTimer.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
function fmt(s) {
  const m = Math.floor(s / 60);
  const r = Math.abs(s % 60);
  return m + ":" + String(r).padStart(2, "0");
}
function RestTimer({
  remaining,
  total = 90,
  label = "Rest",
  nextUp,
  size = 240,
  onSkip,
  onAdd,
  onSubtract,
  skipLabel = "Skip rest",
  className = "",
  ...rest
}) {
  const value = total ? Math.max(0, Math.min(1, remaining / total)) : 0;
  return /*#__PURE__*/React.createElement("div", _extends({
    className: ["rf-rest", className].filter(Boolean).join(" ")
  }, rest), /*#__PURE__*/React.createElement("span", {
    className: "rf-rest__label"
  }, label), /*#__PURE__*/React.createElement(__ds_scope.ProgressRing, {
    value: value,
    size: size,
    tone: "rest",
    label: label + " " + fmt(remaining) + " remaining"
  }, /*#__PURE__*/React.createElement(__ds_scope.StatBlock, {
    value: fmt(remaining),
    size: "xl",
    align: "center"
  })), nextUp ? /*#__PURE__*/React.createElement("span", {
    className: "rf-rest__next"
  }, nextUp) : null, /*#__PURE__*/React.createElement("div", {
    className: "rf-rest__actions"
  }, /*#__PURE__*/React.createElement(__ds_scope.IconButton, {
    icon: "remove",
    label: "Subtract 15 seconds",
    variant: "tonal",
    size: "lg",
    onClick: onSubtract
  }), /*#__PURE__*/React.createElement(__ds_scope.Button, {
    variant: "filled",
    size: "session",
    icon: "skip_next",
    onClick: onSkip
  }, skipLabel), /*#__PURE__*/React.createElement(__ds_scope.IconButton, {
    icon: "add",
    label: "Add 15 seconds",
    variant: "tonal",
    size: "lg",
    onClick: onAdd
  })));
}
Object.assign(__ds_scope, { RestTimer });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/workout/RestTimer.jsx", error: String((e && e.message) || e) }); }

// components/workout/SetRow.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
function SetRow({
  index,
  weight,
  reps,
  unit = "kg",
  previous,
  done = false,
  active = false,
  onToggle,
  className = "",
  ...rest
}) {
  const cls = ["rf-set", done ? "rf-set--done" : "", active && !done ? "rf-set--active" : "", className].filter(Boolean).join(" ");
  return /*#__PURE__*/React.createElement("div", _extends({
    className: cls
  }, rest), /*#__PURE__*/React.createElement("span", {
    className: "rf-set__idx"
  }, index), /*#__PURE__*/React.createElement("span", {
    className: "rf-set__cell"
  }, /*#__PURE__*/React.createElement("span", {
    className: "rf-set__val"
  }, weight, /*#__PURE__*/React.createElement("span", {
    className: "rf-set__unit"
  }, unit)), previous ? /*#__PURE__*/React.createElement("span", {
    className: "rf-set__prev"
  }, previous) : null), /*#__PURE__*/React.createElement("span", {
    className: "rf-set__cell"
  }, /*#__PURE__*/React.createElement("span", {
    className: "rf-set__val"
  }, reps, /*#__PURE__*/React.createElement("span", {
    className: "rf-set__unit"
  }, "reps"))), /*#__PURE__*/React.createElement("button", {
    type: "button",
    className: "rf-set__check",
    "aria-pressed": done,
    "aria-label": done ? "Set " + index + " completed" : "Complete set " + index,
    onClick: onToggle
  }, /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: done ? "check" : "radio_button_unchecked",
    size: 26,
    weight: done ? 700 : 400
  })));
}
function SetRowHeader({
  weightLabel = "Weight",
  repsLabel = "Reps",
  className = ""
}) {
  return /*#__PURE__*/React.createElement("div", {
    className: ["rf-set__head", className].filter(Boolean).join(" ")
  }, /*#__PURE__*/React.createElement("span", null, "#"), /*#__PURE__*/React.createElement("span", null, weightLabel), /*#__PURE__*/React.createElement("span", null, repsLabel), /*#__PURE__*/React.createElement("span", null));
}
Object.assign(__ds_scope, { SetRow, SetRowHeader });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/workout/SetRow.jsx", error: String((e && e.message) || e) }); }

// ui_kits/phone/BuilderScreen.jsx
try { (() => {
const DS_B = window.RepForthDesignSystem_c95a40;
function BuilderScreen({
  lang,
  back,
  openExercise
}) {
  const {
    TopAppBar,
    TextField,
    SegmentedButtons,
    Slider,
    Chip,
    ExerciseCard,
    Button,
    Icon,
    Card,
    Dialog,
    Radio,
    EmptyState,
    FAB
  } = DS_B;
  const [mode, setMode] = React.useState("manual");
  const [ids, setIds] = React.useState(["bench", "incline", "ohp"]);
  const [rest, setRest] = React.useState(90);
  const [goal, setGoal] = React.useState("hyp");
  const [sheet, setSheet] = React.useState(false);
  const fmt = s => Math.floor(s / 60) + ":" + String(s % 60).padStart(2, "0");
  return /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1,
      minHeight: 0,
      display: "flex",
      flexDirection: "column",
      position: "relative"
    }
  }, /*#__PURE__*/React.createElement(TopAppBar, {
    leadingIcon: "arrow_back",
    onLeading: back,
    title: t(lang, "builder"),
    actions: [{
      icon: "help",
      label: "Help"
    }]
  }), /*#__PURE__*/React.createElement("div", {
    className: "rf-scroll",
    style: {
      flex: 1,
      minHeight: 0,
      padding: "0 var(--gutter-phone) 24px",
      display: "flex",
      flexDirection: "column",
      gap: 16
    }
  }, /*#__PURE__*/React.createElement(SegmentedButtons, {
    label: "Mode",
    value: mode,
    onChange: setMode,
    options: [{
      value: "manual",
      label: lang === "en" ? "Build it" : "Kendim kurayım",
      icon: "edit"
    }, {
      value: "ai",
      label: lang === "en" ? "AI draft" : "Yapay zeka",
      icon: "auto_awesome"
    }]
  }), mode === "manual" ? /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement(TextField, {
    label: lang === "en" ? "Workout name" : "Antrenman adı",
    value: lang === "en" ? "Push Day B" : "İt Günü B"
  }), /*#__PURE__*/React.createElement(Slider, {
    label: t(lang, "rest"),
    value: rest,
    min: 15,
    max: 300,
    step: 15,
    onChange: setRest,
    format: fmt
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      flexDirection: "column",
      gap: 10
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "rf-overline"
  }, t(lang, "exercises")), ids.length ? ids.map(id => {
    const e = byId(id),
      m = MUSCLE[e.muscle],
      q = EQUIP[e.equip];
    return /*#__PURE__*/React.createElement(ExerciseCard, {
      key: id,
      name: exName(lang, e),
      sets: e.sets,
      reps: e.reps,
      tags: [{
        label: m[lang],
        icon: m.icon
      }, {
        label: q[lang],
        icon: q.icon
      }],
      trailing: /*#__PURE__*/React.createElement("span", {
        style: {
          display: "flex",
          gap: 2,
          color: "var(--text-quiet)"
        }
      }, /*#__PURE__*/React.createElement(Icon, {
        name: "drag_handle",
        size: 22
      })),
      onClick: () => openExercise(id)
    });
  }) : /*#__PURE__*/React.createElement(EmptyState, {
    icon: "event_note",
    title: lang === "en" ? "No exercises yet" : "Henüz egzersiz yok",
    body: lang === "en" ? "Add from the catalog of 1,324." : "1.324 egzersizlik katalogdan ekle."
  }), /*#__PURE__*/React.createElement(Button, {
    variant: "tonal",
    icon: "add",
    fullWidth: true,
    onClick: () => setSheet(true)
  }, t(lang, "addExercise")))) : /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement(Card, {
    size: "lg",
    style: {
      display: "flex",
      flexDirection: "column",
      gap: 12
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      gap: 8,
      alignItems: "center"
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "auto_awesome",
    size: 20,
    color: "var(--color-primary)"
  }), /*#__PURE__*/React.createElement("span", {
    style: {
      fontWeight: 700,
      color: "var(--text-strong)"
    }
  }, t(lang, "generate"))), /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: "var(--body-md)",
      color: "var(--text-quiet)",
      lineHeight: 1.5
    }
  }, lang === "en" ? "Your request goes to the AI provider you set up. Logged sets stay on this phone." : "İsteğin, ayarladığın yapay zeka sağlayıcısına gönderilir. Kaydedilen setler bu telefonda kalır.")), /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      flexDirection: "column",
      gap: 4
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "rf-overline"
  }, lang === "en" ? "Goal" : "Hedef"), /*#__PURE__*/React.createElement(Radio, {
    label: lang === "en" ? "Hypertrophy · 8–12 reps" : "Hipertrofi · 8–12 tekrar",
    checked: goal === "hyp",
    onChange: () => setGoal("hyp")
  }), /*#__PURE__*/React.createElement(Radio, {
    label: lang === "en" ? "Strength · 3–6 reps" : "Kuvvet · 3–6 tekrar",
    checked: goal === "str",
    onChange: () => setGoal("str")
  }), /*#__PURE__*/React.createElement(Radio, {
    label: lang === "en" ? "Endurance · 15+ reps" : "Dayanıklılık · 15+ tekrar",
    checked: goal === "end",
    onChange: () => setGoal("end")
  })), /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      gap: 8,
      flexWrap: "wrap"
    }
  }, ["chest", "back", "legs", "shoulders"].map(k => /*#__PURE__*/React.createElement(Chip, {
    key: k,
    icon: MUSCLE[k].icon,
    label: MUSCLE[k][lang],
    size: "sm",
    selected: k === "chest",
    onClick: () => {}
  }))), /*#__PURE__*/React.createElement(Button, {
    variant: "filled",
    size: "lg",
    icon: "auto_awesome",
    fullWidth: true
  }, lang === "en" ? "Draft workout" : "Antrenmanı oluştur")), /*#__PURE__*/React.createElement(Button, {
    variant: "filled",
    size: "lg",
    icon: "save",
    fullWidth: true
  }, t(lang, "save"))), /*#__PURE__*/React.createElement(Dialog, {
    sheet: true,
    open: sheet,
    title: t(lang, "addExercise"),
    onDismiss: () => setSheet(false)
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      flexDirection: "column",
      gap: 8
    }
  }, ["fly", "pushdown", "lateral"].map(id => {
    const e = byId(id),
      m = MUSCLE[e.muscle];
    return /*#__PURE__*/React.createElement(Button, {
      key: id,
      variant: "outlined",
      fullWidth: true,
      icon: m.icon,
      onClick: () => {
        setIds(x => x.concat(id));
        setSheet(false);
      }
    }, exName(lang, e));
  }))));
}
Object.assign(window, {
  BuilderScreen
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/phone/BuilderScreen.jsx", error: String((e && e.message) || e) }); }

// ui_kits/phone/CatalogScreen.jsx
try { (() => {
const DS_C = window.RepForthDesignSystem_c95a40;
function CatalogScreen({
  lang,
  go,
  openExercise
}) {
  const {
    TopAppBar,
    TextField,
    Chip,
    ListItem,
    Icon,
    Badge,
    Tabs,
    StatBlock
  } = DS_C;
  const [q, setQ] = React.useState("");
  const [filters, setFilters] = React.useState({
    chest: true
  });
  const [tab, setTab] = React.useState("muscle");
  const keys = tab === "muscle" ? Object.keys(MUSCLE) : Object.keys(EQUIP);
  const src = tab === "muscle" ? MUSCLE : EQUIP;
  const active = Object.keys(filters).filter(k => filters[k]);
  const list = EXERCISES.filter(e => {
    const okQ = !q || exName(lang, e).toLowerCase().includes(q.toLowerCase());
    const okF = !active.length || active.includes(e.muscle) || active.includes(e.equip);
    return okQ && okF;
  });
  const toggle = k => setFilters(f => ({
    ...f,
    [k]: !f[k]
  }));
  return /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1,
      minHeight: 0,
      display: "flex",
      flexDirection: "column"
    }
  }, /*#__PURE__*/React.createElement(TopAppBar, {
    title: t(lang, "catalog"),
    subtitle: "1 324 " + t(lang, "exercises"),
    actions: [{
      icon: "tune",
      label: t(lang, "filters")
    }]
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      padding: "0 var(--gutter-phone) 12px",
      display: "flex",
      flexDirection: "column",
      gap: 12
    }
  }, /*#__PURE__*/React.createElement(TextField, {
    icon: "search",
    placeholder: t(lang, "search"),
    value: q,
    onChange: e => setQ(e.target.value)
  }), /*#__PURE__*/React.createElement(Tabs, {
    value: tab,
    onChange: setTab,
    items: [{
      value: "muscle",
      label: t(lang, "muscle")
    }, {
      value: "equipment",
      label: t(lang, "equipment")
    }]
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      gap: 8,
      flexWrap: "wrap"
    }
  }, keys.map(k => /*#__PURE__*/React.createElement(Chip, {
    key: k,
    icon: src[k].icon,
    label: src[k][lang],
    size: "sm",
    selected: !!filters[k],
    onClick: () => toggle(k)
  })))), /*#__PURE__*/React.createElement("div", {
    className: "rf-scroll",
    style: {
      flex: 1,
      minHeight: 0,
      padding: "0 var(--space-2) 16px"
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      padding: "0 var(--space-2) 8px",
      display: "flex",
      alignItems: "baseline",
      gap: 6
    }
  }, /*#__PURE__*/React.createElement(StatBlock, {
    value: list.length,
    size: "xs"
  }), /*#__PURE__*/React.createElement("span", {
    className: "rf-label"
  }, t(lang, "exercises"))), list.map(e => {
    const m = MUSCLE[e.muscle],
      q2 = EQUIP[e.equip];
    return /*#__PURE__*/React.createElement(ListItem, {
      key: e.id,
      mediaIcon: "fitness_center",
      title: exName(lang, e),
      subtitle: /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement(Chip, {
        icon: m.icon,
        label: m[lang],
        size: "sm"
      }), /*#__PURE__*/React.createElement(Chip, {
        icon: q2.icon,
        label: q2[lang],
        size: "sm"
      })),
      trailing: e.pr ? /*#__PURE__*/React.createElement(Badge, {
        tone: "accent",
        icon: "trending_up",
        label: "PR"
      }) : null,
      trailingIcon: "chevron_right",
      onClick: () => openExercise(e.id)
    });
  })));
}
Object.assign(window, {
  CatalogScreen
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/phone/CatalogScreen.jsx", error: String((e && e.message) || e) }); }

// ui_kits/phone/ExerciseDetailScreen.jsx
try { (() => {
const DS_D = window.RepForthDesignSystem_c95a40;
function ExerciseDetailScreen({
  lang,
  id,
  back,
  addToPlan
}) {
  const {
    TopAppBar,
    Tabs,
    Chip,
    Card,
    StatBlock,
    Button,
    Icon,
    Divider,
    ProgressBar
  } = DS_D;
  const ex = byId(id) || EXERCISES[0];
  const m = MUSCLE[ex.muscle],
    q = EQUIP[ex.equip];
  const [tab, setTab] = React.useState("how");
  return /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1,
      minHeight: 0,
      display: "flex",
      flexDirection: "column",
      animation: "rf-axis-in var(--dur-medium) var(--ease-decelerate)"
    }
  }, /*#__PURE__*/React.createElement(TopAppBar, {
    leadingIcon: "arrow_back",
    onLeading: back,
    title: exName(lang, ex),
    subtitle: m[lang] + " · " + q[lang],
    actions: [{
      icon: "bookmark",
      label: "Save",
      selected: true
    }, {
      icon: "more_vert",
      label: "More"
    }]
  }), /*#__PURE__*/React.createElement("div", {
    className: "rf-scroll",
    style: {
      flex: 1,
      minHeight: 0,
      padding: "0 var(--gutter-phone) 24px",
      display: "flex",
      flexDirection: "column",
      gap: 16
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      width: "100%",
      aspectRatio: "1 / 1",
      borderRadius: "var(--radius-media)",
      background: "var(--color-surface-container-high)",
      display: "grid",
      placeItems: "center",
      color: "var(--text-quiet)",
      gap: 8
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "fitness_center",
    size: 64
  }), /*#__PURE__*/React.createElement("span", {
    className: "rf-label",
    style: {
      textAlign: "center"
    }
  }, "1:1 exercise media", /*#__PURE__*/React.createElement("br", null), "(asset slot \u2014 never blurred, never full-bleed)")), /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      gap: 8,
      flexWrap: "wrap"
    }
  }, /*#__PURE__*/React.createElement(Chip, {
    icon: m.icon,
    label: m[lang],
    size: "sm"
  }), /*#__PURE__*/React.createElement(Chip, {
    icon: q.icon,
    label: q[lang],
    size: "sm"
  }), /*#__PURE__*/React.createElement(Chip, {
    icon: "signal_cellular_alt",
    label: lang === "en" ? "Compound" : "Bileşik",
    size: "sm"
  })), /*#__PURE__*/React.createElement(Card, {
    style: {
      display: "flex",
      alignItems: "center",
      justifyContent: "space-between"
    }
  }, /*#__PURE__*/React.createElement(StatBlock, {
    value: ex.weight || "—",
    unit: ex.weight ? "kg" : undefined,
    label: t(lang, "weight"),
    size: "md"
  }), /*#__PURE__*/React.createElement(Divider, {
    vertical: true
  }), /*#__PURE__*/React.createElement(StatBlock, {
    value: ex.reps,
    label: t(lang, "reps"),
    size: "md"
  }), /*#__PURE__*/React.createElement(Divider, {
    vertical: true
  }), /*#__PURE__*/React.createElement(StatBlock, {
    value: ex.sets,
    label: t(lang, "sets"),
    size: "md",
    tone: "accent"
  })), /*#__PURE__*/React.createElement(Tabs, {
    value: tab,
    onChange: setTab,
    items: [{
      value: "how",
      label: t(lang, "howTo")
    }, {
      value: "history",
      label: t(lang, "history")
    }, {
      value: "records",
      label: t(lang, "records"),
      count: 3
    }]
  }), tab === "how" ? /*#__PURE__*/React.createElement("ol", {
    style: {
      margin: 0,
      paddingLeft: 20,
      display: "flex",
      flexDirection: "column",
      gap: 10,
      color: "var(--text-body)",
      fontSize: "var(--body-md)",
      lineHeight: 1.5
    }
  }, /*#__PURE__*/React.createElement("li", null, lang === "en" ? "Set the bar over your eyes and grip just outside shoulder width." : "Barı gözlerinin üzerine al, omuz genişliğinin biraz dışından kavra."), /*#__PURE__*/React.createElement("li", null, lang === "en" ? "Unrack, lower to mid-chest with elbows tucked to about 45°." : "Barı çıkar, dirsekler yaklaşık 45° içte kalacak şekilde göğüs ortasına indir."), /*#__PURE__*/React.createElement("li", null, lang === "en" ? "Press back up, keeping your feet planted and hips on the bench." : "Ayaklar sabit, kalça sehpada kalacak şekilde yukarı it.")) : tab === "history" ? /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      flexDirection: "column",
      gap: 12
    }
  }, [["18 Aug", 82.5, 8], ["11 Aug", 80, 8], ["4 Aug", 80, 7]].map(([d, w, r]) => /*#__PURE__*/React.createElement("div", {
    key: d,
    style: {
      display: "flex",
      alignItems: "center",
      justifyContent: "space-between"
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "rf-label"
  }, d), /*#__PURE__*/React.createElement("span", {
    className: "rf-numeric rf-numeric-xs"
  }, w, /*#__PURE__*/React.createElement("span", {
    className: "rf-unit"
  }, "kg"), " \xD7 ", r))), /*#__PURE__*/React.createElement(ProgressBar, {
    label: t(lang, "volume"),
    value: 0.72,
    showValue: false
  })) : /*#__PURE__*/React.createElement(Card, {
    style: {
      display: "flex",
      alignItems: "center",
      gap: 20
    }
  }, /*#__PURE__*/React.createElement(StatBlock, {
    value: "90",
    unit: "kg",
    label: lang === "en" ? "Best set" : "En iyi set",
    size: "lg",
    tone: "accent"
  }), /*#__PURE__*/React.createElement(StatBlock, {
    value: "1 320",
    unit: "kg",
    label: lang === "en" ? "Best volume" : "En iyi hacim",
    size: "sm"
  })), /*#__PURE__*/React.createElement(Button, {
    variant: "filled",
    size: "lg",
    icon: "add",
    fullWidth: true,
    onClick: addToPlan
  }, t(lang, "addExercise"))));
}
Object.assign(window, {
  ExerciseDetailScreen
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/phone/ExerciseDetailScreen.jsx", error: String((e && e.message) || e) }); }

// ui_kits/phone/PhoneFrame.jsx
try { (() => {
const {
  Icon
} = window.RepForthDesignSystem_c95a40;
function StatusBar({
  time = "18:42"
}) {
  return /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      alignItems: "center",
      justifyContent: "space-between",
      padding: "0 20px",
      height: 34,
      flex: "none",
      fontFamily: "var(--font-numeric)",
      fontVariantNumeric: "tabular-nums",
      fontWeight: 700,
      fontSize: 13,
      color: "var(--text-body)"
    }
  }, /*#__PURE__*/React.createElement("span", null, time), /*#__PURE__*/React.createElement("span", {
    style: {
      display: "flex",
      gap: 5,
      alignItems: "center",
      color: "var(--text-quiet)"
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "signal_cellular_alt",
    size: 15
  }), /*#__PURE__*/React.createElement(Icon, {
    name: "wifi",
    size: 15
  }), /*#__PURE__*/React.createElement(Icon, {
    name: "battery_5_bar",
    size: 15
  })));
}
function PhoneFrame({
  children,
  theme = "dark",
  label
}) {
  return /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      flexDirection: "column",
      gap: 10,
      alignItems: "center"
    }
  }, /*#__PURE__*/React.createElement("div", {
    "data-theme": theme,
    style: {
      width: 412,
      height: 892,
      borderRadius: 38,
      overflow: "hidden",
      background: "var(--surface-app)",
      display: "flex",
      flexDirection: "column",
      boxShadow: "0 0 0 9px #1b1f16, 0 0 0 11px #34392e, 0 24px 60px -20px rgba(0,0,0,.8)",
      position: "relative"
    }
  }, /*#__PURE__*/React.createElement(StatusBar, null), children), label ? /*#__PURE__*/React.createElement("span", {
    style: {
      font: "700 11px/1 var(--font-ui)",
      letterSpacing: ".08em",
      textTransform: "uppercase",
      color: "var(--text-quiet)"
    }
  }, label) : null);
}
Object.assign(window, {
  PhoneFrame,
  StatusBar
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/phone/PhoneFrame.jsx", error: String((e && e.message) || e) }); }

// ui_kits/phone/SessionScreen.jsx
try { (() => {
const DS_S = window.RepForthDesignSystem_c95a40;
function SessionScreen({
  lang,
  session,
  setSession,
  exit,
  finish
}) {
  const {
    TopAppBar,
    SetRow,
    SetRowHeader,
    Stepper,
    Button,
    IconButton,
    Card,
    StatBlock,
    ProgressBar,
    Chip,
    Icon,
    Dialog,
    Snackbar,
    RestTimer,
    Divider
  } = DS_S;
  const plan = PLANS[0];
  const ex = byId(plan.ids[session.exIndex]);
  const m = MUSCLE[ex.muscle],
    q = EQUIP[ex.equip];
  const [ask, setAsk] = React.useState(false);
  const [toast, setToast] = React.useState(false);
  React.useEffect(() => {
    if (!session.resting) return;
    const id = setInterval(() => setSession(s => s.resting ? s.restLeft <= 1 ? {
      ...s,
      resting: false,
      restLeft: s.restTotal
    } : {
      ...s,
      restLeft: s.restLeft - 1
    } : s), 1000);
    return () => clearInterval(id);
  }, [session.resting, setSession]);
  const toggleSet = i => setSession(s => {
    const done = {
      ...s.done,
      [i]: !s.done[i]
    };
    const justDone = !s.done[i];
    return {
      ...s,
      done,
      resting: justDone ? true : s.resting,
      restLeft: justDone ? s.restTotal : s.restLeft
    };
  });
  const doneCount = Object.values(session.done).filter(Boolean).length;
  if (session.resting) {
    const next = exName(lang, ex) + " · " + t(lang, "set") + " " + Math.min(ex.sets, doneCount + 1);
    return /*#__PURE__*/React.createElement("div", {
      style: {
        flex: 1,
        minHeight: 0,
        display: "flex",
        flexDirection: "column",
        background: "var(--color-surface)"
      }
    }, /*#__PURE__*/React.createElement(TopAppBar, {
      leadingIcon: "close",
      onLeading: () => setSession(s => ({
        ...s,
        resting: false
      })),
      title: t(lang, "rest")
    }), /*#__PURE__*/React.createElement("div", {
      style: {
        flex: 1,
        display: "flex",
        flexDirection: "column",
        justifyContent: "center"
      }
    }, /*#__PURE__*/React.createElement(RestTimer, {
      remaining: session.restLeft,
      total: session.restTotal,
      size: 260,
      label: t(lang, "rest"),
      skipLabel: t(lang, "skipRest"),
      nextUp: t(lang, "nextUp") + ": " + next,
      onSkip: () => setSession(s => ({
        ...s,
        resting: false,
        restLeft: s.restTotal
      })),
      onAdd: () => setSession(s => ({
        ...s,
        restLeft: s.restLeft + 15,
        restTotal: Math.max(s.restTotal, s.restLeft + 15)
      })),
      onSubtract: () => setSession(s => ({
        ...s,
        restLeft: Math.max(0, s.restLeft - 15)
      }))
    })), /*#__PURE__*/React.createElement("div", {
      style: {
        padding: "0 var(--gutter-phone) 24px",
        display: "flex",
        gap: 8,
        alignItems: "center",
        justifyContent: "center",
        color: "var(--text-quiet)"
      }
    }, /*#__PURE__*/React.createElement(Icon, {
      name: "watch",
      size: 16
    }), /*#__PURE__*/React.createElement("span", {
      className: "rf-label"
    }, lang === "en" ? "Mirrored on your watch" : "Saatinde de görünüyor")));
  }
  return /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1,
      minHeight: 0,
      display: "flex",
      flexDirection: "column",
      position: "relative"
    }
  }, /*#__PURE__*/React.createElement(TopAppBar, {
    leadingIcon: "close",
    onLeading: () => setAsk(true),
    title: exName(lang, plan),
    subtitle: session.exIndex + 1 + " / " + plan.ids.length + " · " + t(lang, "exercises"),
    actions: [{
      icon: "watch",
      label: t(lang, "watch")
    }, {
      icon: "more_vert",
      label: "More"
    }]
  }), /*#__PURE__*/React.createElement("div", {
    className: "rf-scroll",
    style: {
      flex: 1,
      minHeight: 0,
      padding: "0 var(--gutter-phone) 16px",
      display: "flex",
      flexDirection: "column",
      gap: 16
    }
  }, /*#__PURE__*/React.createElement(ProgressBar, {
    segmented: true,
    value: doneCount,
    total: ex.sets,
    current: doneCount,
    label: t(lang, "sets")
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      gap: 12,
      alignItems: "center"
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "rf-ex__media",
    style: {
      width: 64
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "fitness_center",
    size: 26
  })), /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1,
      minWidth: 0,
      display: "flex",
      flexDirection: "column",
      gap: 6
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: "var(--title-lg)",
      fontWeight: 700,
      color: "var(--text-strong)",
      lineHeight: 1.2
    }
  }, exName(lang, ex)), /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      gap: 6,
      flexWrap: "wrap"
    }
  }, /*#__PURE__*/React.createElement(Chip, {
    icon: m.icon,
    label: m[lang],
    size: "sm"
  }), /*#__PURE__*/React.createElement(Chip, {
    icon: q.icon,
    label: q[lang],
    size: "sm"
  })))), /*#__PURE__*/React.createElement(Card, {
    size: "lg",
    style: {
      display: "flex",
      flexDirection: "column",
      gap: 16
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      alignItems: "flex-end",
      justifyContent: "space-between",
      gap: 12
    }
  }, /*#__PURE__*/React.createElement(StatBlock, {
    value: session.weight,
    unit: "kg",
    label: t(lang, "weight"),
    size: "xl"
  }), /*#__PURE__*/React.createElement(StatBlock, {
    value: session.reps,
    label: t(lang, "reps"),
    size: "lg",
    align: "center"
  })), /*#__PURE__*/React.createElement(Divider, null), /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      flexDirection: "column",
      gap: 10
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "rf-label"
  }, t(lang, "weight")), /*#__PURE__*/React.createElement(Stepper, {
    size: "session",
    label: t(lang, "weight"),
    value: session.weight,
    unit: "kg",
    step: 2.5,
    onChange: w => setSession(s => ({
      ...s,
      weight: w
    }))
  }), /*#__PURE__*/React.createElement("span", {
    className: "rf-label"
  }, t(lang, "reps")), /*#__PURE__*/React.createElement(Stepper, {
    size: "session",
    label: t(lang, "reps"),
    value: session.reps,
    step: 1,
    onChange: r => setSession(s => ({
      ...s,
      reps: r
    }))
  })), /*#__PURE__*/React.createElement(Button, {
    variant: "filled",
    size: "session",
    icon: "check",
    fullWidth: true,
    onClick: () => {
      toggleSet(doneCount + 1);
      setToast(true);
      setTimeout(() => setToast(false), 2600);
    }
  }, t(lang, "logSet"))), /*#__PURE__*/React.createElement("div", {
    style: {
      background: "var(--surface-card)",
      borderRadius: "var(--radius-card)",
      padding: "12px 8px"
    }
  }, /*#__PURE__*/React.createElement(SetRowHeader, {
    weightLabel: t(lang, "weight"),
    repsLabel: t(lang, "reps")
  }), Array.from({
    length: ex.sets
  }, (_, i) => i + 1).map(i => /*#__PURE__*/React.createElement(SetRow, {
    key: i,
    index: i,
    weight: session.weight,
    reps: session.reps,
    previous: t(lang, "lastTime") + ": " + (ex.weight - 2.5) + " kg × " + ex.reps,
    done: !!session.done[i],
    active: !session.done[i] && i === doneCount + 1,
    onToggle: () => toggleSet(i)
  }))), /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      gap: 10
    }
  }, /*#__PURE__*/React.createElement(Button, {
    variant: "tonal",
    icon: "arrow_back",
    onClick: () => setSession(s => ({
      ...s,
      exIndex: Math.max(0, s.exIndex - 1),
      done: {}
    }))
  }, lang === "en" ? "Previous" : "Önceki"), /*#__PURE__*/React.createElement(Button, {
    variant: "tonal",
    trailingIcon: "arrow_forward",
    fullWidth: true,
    onClick: () => setSession(s => ({
      ...s,
      exIndex: Math.min(plan.ids.length - 1, s.exIndex + 1),
      done: {}
    }))
  }, lang === "en" ? "Next exercise" : "Sonraki egzersiz")), /*#__PURE__*/React.createElement(Button, {
    variant: "outlined",
    icon: "flag",
    fullWidth: true,
    onClick: finish
  }, t(lang, "finish"))), toast ? /*#__PURE__*/React.createElement("div", {
    style: {
      position: "absolute",
      left: 12,
      right: 12,
      bottom: 16,
      zIndex: 30
    }
  }, /*#__PURE__*/React.createElement(Snackbar, {
    icon: "check_circle",
    message: t(lang, "setLogged"),
    actionLabel: t(lang, "undo"),
    onAction: () => setToast(false)
  })) : null, /*#__PURE__*/React.createElement(Dialog, {
    open: ask,
    title: t(lang, "discard"),
    onDismiss: () => setAsk(false),
    actions: /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement(Button, {
      variant: "text",
      onClick: () => setAsk(false)
    }, t(lang, "keepGoing")), /*#__PURE__*/React.createElement(Button, {
      variant: "danger",
      onClick: () => {
        setAsk(false);
        exit();
      }
    }, t(lang, "discardYes")))
  }, t(lang, "discardBody")));
}
Object.assign(window, {
  SessionScreen
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/phone/SessionScreen.jsx", error: String((e && e.message) || e) }); }

// ui_kits/phone/SettingsScreen.jsx
try { (() => {
const DS_ST = window.RepForthDesignSystem_c95a40;
function SettingsScreen({
  lang,
  setLang,
  theme,
  setTheme,
  back
}) {
  const {
    TopAppBar,
    SegmentedButtons,
    Switch,
    ListItem,
    Divider,
    Icon,
    Card,
    Badge
  } = DS_ST;
  const [awake, setAwake] = React.useState(true);
  const [sound, setSound] = React.useState(true);
  return /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1,
      minHeight: 0,
      display: "flex",
      flexDirection: "column"
    }
  }, /*#__PURE__*/React.createElement(TopAppBar, {
    leadingIcon: "arrow_back",
    onLeading: back,
    title: t(lang, "settingsTitle")
  }), /*#__PURE__*/React.createElement("div", {
    className: "rf-scroll",
    style: {
      flex: 1,
      minHeight: 0,
      padding: "0 var(--gutter-phone) 24px",
      display: "flex",
      flexDirection: "column",
      gap: 20
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      flexDirection: "column",
      gap: 10
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "rf-overline"
  }, t(lang, "language")), /*#__PURE__*/React.createElement(SegmentedButtons, {
    label: t(lang, "language"),
    value: lang,
    onChange: setLang,
    options: [{
      value: "en",
      label: "English",
      icon: "translate"
    }, {
      value: "tr",
      label: "Türkçe",
      icon: "translate"
    }]
  })), /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      flexDirection: "column",
      gap: 10
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "rf-overline"
  }, t(lang, "theme")), /*#__PURE__*/React.createElement(SegmentedButtons, {
    label: t(lang, "theme"),
    value: theme,
    onChange: setTheme,
    options: [{
      value: "dark",
      label: lang === "en" ? "Dark" : "Koyu",
      icon: "dark_mode"
    }, {
      value: "light",
      label: lang === "en" ? "Light" : "Açık",
      icon: "light_mode"
    }]
  })), /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      flexDirection: "column",
      gap: 10
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "rf-overline"
  }, lang === "en" ? "During a workout" : "Antrenman sırasında"), /*#__PURE__*/React.createElement(Switch, {
    label: t(lang, "keepAwake"),
    description: t(lang, "keepAwakeSub"),
    checked: awake,
    onChange: setAwake
  }), /*#__PURE__*/React.createElement(Divider, null), /*#__PURE__*/React.createElement(Switch, {
    label: lang === "en" ? "Rest countdown sound" : "Dinlenme sesi",
    description: lang === "en" ? "Beeps at 3 seconds" : "3 saniye kala uyarır",
    checked: sound,
    onChange: setSound
  })), /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      flexDirection: "column",
      gap: 4
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "rf-overline"
  }, t(lang, "watch")), /*#__PURE__*/React.createElement(ListItem, {
    mediaIcon: "watch",
    title: lang === "en" ? "Galaxy Watch6" : "Galaxy Watch6",
    subtitle: t(lang, "watchSub"),
    trailing: /*#__PURE__*/React.createElement(Badge, {
      tone: "info",
      icon: "check",
      label: lang === "en" ? "Connected" : "Bağlı"
    }),
    onClick: () => {}
  }), /*#__PURE__*/React.createElement(ListItem, {
    mediaIcon: "straighten",
    title: t(lang, "units"),
    subtitle: "kg",
    trailingIcon: "chevron_right",
    onClick: () => {}
  }), /*#__PURE__*/React.createElement(ListItem, {
    mediaIcon: "download",
    title: lang === "en" ? "Export data" : "Verileri dışa aktar",
    subtitle: lang === "en" ? "JSON, on this device" : "JSON, bu cihazda",
    trailingIcon: "chevron_right",
    onClick: () => {}
  }), /*#__PURE__*/React.createElement(ListItem, {
    mediaIcon: "code",
    title: lang === "en" ? "Source code" : "Kaynak kod",
    subtitle: lang === "en" ? "GPL-3.0 · read it, change it" : "GPL-3.0 · oku, değiştir",
    trailingIcon: "open_in_new",
    onClick: () => {}
  })), /*#__PURE__*/React.createElement(Card, {
    style: {
      display: "flex",
      gap: 10,
      alignItems: "flex-start"
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "phone_android",
    size: 20,
    color: "var(--color-primary)"
  }), /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: "var(--label-md)",
      color: "var(--text-quiet)",
      lineHeight: 1.5
    }
  }, t(lang, "local")))));
}
Object.assign(window, {
  SettingsScreen
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/phone/SettingsScreen.jsx", error: String((e && e.message) || e) }); }

// ui_kits/phone/TodayScreen.jsx
try { (() => {
const DS_T = window.RepForthDesignSystem_c95a40;
function TodayScreen({
  lang,
  go,
  startSession,
  session
}) {
  const {
    TopAppBar,
    PlanCard,
    Button,
    Card,
    StatBlock,
    Icon,
    ProgressBar,
    Divider,
    Badge
  } = DS_T;
  const plan = PLANS[0];
  return /*#__PURE__*/React.createElement("div", {
    className: "rf-scroll",
    style: {
      flex: 1,
      minHeight: 0
    }
  }, /*#__PURE__*/React.createElement(TopAppBar, {
    large: true,
    title: t(lang, "today"),
    subtitle: t(lang, "week"),
    actions: [{
      icon: "search",
      label: t(lang, "search"),
      onClick: () => go("catalog")
    }, {
      icon: "settings",
      label: t(lang, "settingsTitle"),
      onClick: () => go("settings")
    }]
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      padding: "0 var(--gutter-phone) 24px",
      display: "flex",
      flexDirection: "column",
      gap: "var(--section-gap)"
    }
  }, session ? /*#__PURE__*/React.createElement(Card, {
    size: "lg",
    variant: "elevated",
    style: {
      display: "flex",
      flexDirection: "column",
      gap: 12
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      alignItems: "center",
      gap: 8
    }
  }, /*#__PURE__*/React.createElement(Badge, {
    tone: "accent",
    icon: "bolt",
    label: lang === "en" ? "In progress" : "Devam ediyor"
  }), /*#__PURE__*/React.createElement("span", {
    className: "rf-label"
  }, exName(lang, plan))), /*#__PURE__*/React.createElement(ProgressBar, {
    segmented: true,
    value: session.doneCount,
    total: session.totalSets,
    current: session.doneCount,
    label: t(lang, "sets")
  }), /*#__PURE__*/React.createElement(Button, {
    variant: "filled",
    size: "lg",
    icon: "play_arrow",
    fullWidth: true,
    onClick: () => go("session")
  }, t(lang, "resume"))) : /*#__PURE__*/React.createElement(PlanCard, {
    today: true,
    badge: t(lang, "today"),
    name: exName(lang, plan),
    exercises: plan.ids.length,
    exercisesLabel: t(lang, "exercises"),
    minutes: plan.minutes,
    minutesLabel: t(lang, "min"),
    muscles: ["chest", "shoulders", "triceps"].map(k => ({
      id: k,
      label: MUSCLE[k][lang],
      icon: MUSCLE[k].icon
    })),
    action: /*#__PURE__*/React.createElement(Button, {
      variant: "filled",
      size: "session",
      icon: "play_arrow",
      fullWidth: true,
      onClick: startSession
    }, t(lang, "start"))
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      flexDirection: "column",
      gap: 12
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "rf-overline"
  }, t(lang, "progress")), /*#__PURE__*/React.createElement(Card, {
    style: {
      display: "flex",
      alignItems: "center",
      justifyContent: "space-between"
    }
  }, /*#__PURE__*/React.createElement(StatBlock, {
    value: "14 820",
    unit: "kg",
    label: t(lang, "volume"),
    size: "sm"
  }), /*#__PURE__*/React.createElement(Divider, {
    vertical: true
  }), /*#__PURE__*/React.createElement(StatBlock, {
    value: "3",
    label: t(lang, "prs"),
    size: "sm",
    tone: "accent"
  }), /*#__PURE__*/React.createElement(Divider, {
    vertical: true
  }), /*#__PURE__*/React.createElement(StatBlock, {
    value: "6",
    label: t(lang, "streak"),
    size: "sm"
  }))), /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      flexDirection: "column",
      gap: 12
    }
  }, /*#__PURE__*/React.createElement("span", {
    className: "rf-overline"
  }, t(lang, "plans")), PLANS.slice(1).map(p => /*#__PURE__*/React.createElement(PlanCard, {
    key: p.id,
    name: exName(lang, p),
    exercises: p.ids.length,
    exercisesLabel: t(lang, "exercises"),
    minutes: p.minutes,
    minutesLabel: t(lang, "min"),
    muscles: p.ids.map(id => byId(id).muscle).map(k => ({
      id: k,
      label: MUSCLE[k][lang],
      icon: MUSCLE[k].icon
    })),
    onClick: () => go("builder")
  })), /*#__PURE__*/React.createElement(Button, {
    variant: "tonal",
    icon: "auto_awesome",
    fullWidth: true,
    onClick: () => go("builder")
  }, t(lang, "generate"))), /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      gap: 8,
      alignItems: "flex-start",
      color: "var(--text-quiet)",
      fontSize: "var(--label-md)",
      lineHeight: 1.5
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: "phone_android",
    size: 16
  }), /*#__PURE__*/React.createElement("span", null, t(lang, "local")))));
}
Object.assign(window, {
  TodayScreen
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/phone/TodayScreen.jsx", error: String((e && e.message) || e) }); }

// ui_kits/phone/app.jsx
try { (() => {
const DS_A = window.RepForthDesignSystem_c95a40;
function PhoneApp() {
  const {
    NavigationBar,
    FAB
  } = DS_A;
  const [lang, setLang] = React.useState("en");
  const [theme, setTheme] = React.useState("dark");
  const [tab, setTab] = React.useState("today");
  const [route, setRoute] = React.useState({
    name: "today"
  });
  const [session, setSession] = React.useState(null);
  const go = (name, params) => {
    setRoute({
      name,
      ...params
    });
    if (["today", "plans", "catalog", "progress"].includes(name)) setTab(name);
  };
  const startSession = () => {
    setSession({
      exIndex: 0,
      weight: 82.5,
      reps: 8,
      done: {},
      resting: false,
      restLeft: 90,
      restTotal: 90,
      totalSets: 4,
      doneCount: 0
    });
    go("session");
  };
  const onTab = v => {
    setTab(v);
    setRoute({
      name: v === "progress" ? "today" : v
    });
  };
  let screen = null;
  if (route.name === "session" && session) screen = /*#__PURE__*/React.createElement(SessionScreen, {
    lang: lang,
    session: session,
    setSession: setSession,
    exit: () => {
      setSession(null);
      go("today");
    },
    finish: () => {
      setSession(null);
      go("today");
    }
  });else if (route.name === "catalog") screen = /*#__PURE__*/React.createElement(CatalogScreen, {
    lang: lang,
    go: go,
    openExercise: id => go("exercise", {
      id
    })
  });else if (route.name === "exercise") screen = /*#__PURE__*/React.createElement(ExerciseDetailScreen, {
    lang: lang,
    id: route.id,
    back: () => go("catalog"),
    addToPlan: () => go("builder")
  });else if (route.name === "builder" || route.name === "plans") screen = /*#__PURE__*/React.createElement(BuilderScreen, {
    lang: lang,
    back: () => go("today"),
    openExercise: id => go("exercise", {
      id
    })
  });else if (route.name === "settings") screen = /*#__PURE__*/React.createElement(SettingsScreen, {
    lang: lang,
    setLang: setLang,
    theme: theme,
    setTheme: setTheme,
    back: () => go("today")
  });else screen = /*#__PURE__*/React.createElement(TodayScreen, {
    lang: lang,
    go: go,
    startSession: startSession,
    session: session ? {
      ...session,
      doneCount: Object.values(session.done).filter(Boolean).length
    } : null
  });
  const inSession = route.name === "session";
  return /*#__PURE__*/React.createElement(PhoneFrame, {
    theme: theme
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1,
      minHeight: 0,
      display: "flex",
      flexDirection: "column",
      position: "relative"
    }
  }, screen, !inSession && route.name !== "settings" && route.name !== "exercise" ? /*#__PURE__*/React.createElement("div", {
    style: {
      position: "absolute",
      right: 16,
      bottom: 96,
      zIndex: 20
    }
  }, /*#__PURE__*/React.createElement(FAB, {
    icon: "add",
    label: t(lang, "newWorkout"),
    extended: true,
    onClick: () => go("builder")
  })) : null), !inSession ? /*#__PURE__*/React.createElement(NavigationBar, {
    value: tab,
    onChange: onTab,
    items: [{
      value: "today",
      icon: "today",
      label: t(lang, "today")
    }, {
      value: "plans",
      icon: "list_alt",
      label: t(lang, "plans")
    }, {
      value: "catalog",
      icon: "fitness_center",
      label: t(lang, "catalog")
    }, {
      value: "progress",
      icon: "insights",
      label: t(lang, "progress")
    }]
  }) : null);
}
Object.assign(window, {
  PhoneApp
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/phone/app.jsx", error: String((e && e.message) || e) }); }

// ui_kits/phone/data.jsx
try { (() => {
// Sample content for the phone kit. Every user-facing string exists in EN and TR.
const STR = {
  en: {
    today: "Today",
    plans: "Plans",
    catalog: "Exercises",
    progress: "Progress",
    week: "Week 4 · Push / Pull / Legs",
    start: "Start workout",
    resume: "Resume session",
    newWorkout: "New workout",
    generate: "Generate with AI",
    exercises: "exercises",
    min: "min",
    sets: "Sets",
    weight: "Weight",
    reps: "Reps",
    rest: "Rest",
    skipRest: "Skip rest",
    logSet: "Log set",
    finish: "Finish workout",
    search: "Search 1,324 exercises",
    filters: "Filters",
    muscle: "Muscle",
    equipment: "Equipment",
    howTo: "How to",
    history: "History",
    records: "Records",
    settingsTitle: "Settings",
    theme: "Theme",
    language: "Language",
    units: "Units",
    keepAwake: "Keep screen on",
    keepAwakeSub: "While a workout is running",
    watch: "Wear OS companion",
    watchSub: "Acts as a remote for this phone",
    local: "Logged sets stay on this phone. No account, no upload.",
    nextUp: "Next up",
    done: "Done",
    set: "Set",
    lastTime: "Last time",
    discard: "Discard this workout?",
    discardBody: "Logged sets stay on this phone. Nothing is uploaded.",
    keepGoing: "Keep going",
    discardYes: "Discard",
    setLogged: "Set logged",
    undo: "Undo",
    builder: "Build workout",
    addExercise: "Add exercise",
    save: "Save workout",
    volume: "Volume",
    prs: "PRs this month",
    streak: "Week streak"
  },
  tr: {
    today: "Bugün",
    plans: "Programlar",
    catalog: "Egzersizler",
    progress: "Gelişim",
    week: "Hafta 4 · İt / Çek / Bacak",
    start: "Antrenmanı başlat",
    resume: "Antrenmana dön",
    newWorkout: "Yeni antrenman",
    generate: "Yapay zeka ile oluştur",
    exercises: "egzersiz",
    min: "dk",
    sets: "Set",
    weight: "Ağırlık",
    reps: "Tekrar",
    rest: "Dinlenme",
    skipRest: "Dinlenmeyi atla",
    logSet: "Seti kaydet",
    finish: "Antrenmanı bitir",
    search: "1.324 egzersizde ara",
    filters: "Filtreler",
    muscle: "Kas grubu",
    equipment: "Ekipman",
    howTo: "Nasıl yapılır",
    history: "Geçmiş",
    records: "Rekorlar",
    settingsTitle: "Ayarlar",
    theme: "Tema",
    language: "Dil",
    units: "Birim",
    keepAwake: "Ekranı açık tut",
    keepAwakeSub: "Antrenman sürerken",
    watch: "Wear OS eşlikçisi",
    watchSub: "Telefondaki antrenmanın kumandası",
    local: "Kaydedilen setler yalnızca bu telefonda kalır. Hesap yok, yükleme yok.",
    nextUp: "Sırada",
    done: "Bitti",
    set: "Set",
    lastTime: "Geçen sefer",
    discard: "Bu antrenman silinsin mi?",
    discardBody: "Kaydedilen setler bu telefonda kalır. Hiçbir şey yüklenmez.",
    keepGoing: "Devam et",
    discardYes: "Sil",
    setLogged: "Set kaydedildi",
    undo: "Geri al",
    builder: "Antrenman oluştur",
    addExercise: "Egzersiz ekle",
    save: "Antrenmanı kaydet",
    volume: "Hacim",
    prs: "Bu ayın rekorları",
    streak: "Haftalık seri"
  }
};
const MUSCLE = {
  chest: {
    en: "Chest",
    tr: "Göğüs",
    icon: "accessibility_new"
  },
  back: {
    en: "Back",
    tr: "Sırt",
    icon: "airline_seat_recline_normal"
  },
  shoulders: {
    en: "Shoulders",
    tr: "Omuz",
    icon: "sports_martial_arts"
  },
  triceps: {
    en: "Triceps",
    tr: "Triceps",
    icon: "exercise"
  },
  biceps: {
    en: "Biceps",
    tr: "Biceps",
    icon: "sports_gymnastics"
  },
  legs: {
    en: "Legs",
    tr: "Bacak",
    icon: "directions_run"
  },
  core: {
    en: "Core",
    tr: "Karın",
    icon: "self_improvement"
  }
};
const EQUIP = {
  barbell: {
    en: "Barbell",
    tr: "Halter",
    icon: "fitness_center"
  },
  dumbbell: {
    en: "Dumbbell",
    tr: "Dambıl",
    icon: "exercise"
  },
  cable: {
    en: "Cable",
    tr: "Kablo",
    icon: "cable"
  },
  machine: {
    en: "Machine",
    tr: "Makine",
    icon: "precision_manufacturing"
  },
  bodyweight: {
    en: "Bodyweight",
    tr: "Vücut ağırlığı",
    icon: "accessibility"
  }
};
const EXERCISES = [{
  id: "bench",
  en: "Barbell bench press",
  tr: "Bench press (halter)",
  muscle: "chest",
  equip: "barbell",
  sets: 4,
  reps: 8,
  weight: 82.5,
  pr: true
}, {
  id: "incline",
  en: "Incline dumbbell press",
  tr: "Eğimli dambıl press",
  muscle: "chest",
  equip: "dumbbell",
  sets: 3,
  reps: 12,
  weight: 28
}, {
  id: "fly",
  en: "Cable chest fly",
  tr: "Kablo ile göğüs açma",
  muscle: "chest",
  equip: "cable",
  sets: 3,
  reps: 15,
  weight: 15
}, {
  id: "ohp",
  en: "Standing overhead press",
  tr: "Ayakta omuz press",
  muscle: "shoulders",
  equip: "barbell",
  sets: 4,
  reps: 6,
  weight: 47.5
}, {
  id: "lateral",
  en: "Dumbbell lateral raise",
  tr: "Yana dambıl kaldırma",
  muscle: "shoulders",
  equip: "dumbbell",
  sets: 3,
  reps: 15,
  weight: 10
}, {
  id: "dip",
  en: "Triceps dip",
  tr: "Triceps dalma",
  muscle: "triceps",
  equip: "bodyweight",
  sets: 3,
  reps: 12,
  weight: 0
}, {
  id: "pushdown",
  en: "Cable triceps pushdown",
  tr: "Kablo ile triceps itme",
  muscle: "triceps",
  equip: "cable",
  sets: 3,
  reps: 12,
  weight: 32.5
}, {
  id: "row",
  en: "Barbell row",
  tr: "Halterle kürek çekme",
  muscle: "back",
  equip: "barbell",
  sets: 4,
  reps: 8,
  weight: 70
}, {
  id: "pulldown",
  en: "Lat pulldown",
  tr: "Lat çekiş",
  muscle: "back",
  equip: "machine",
  sets: 3,
  reps: 12,
  weight: 55
}, {
  id: "squat",
  en: "Back squat",
  tr: "Arkadan squat",
  muscle: "legs",
  equip: "barbell",
  sets: 5,
  reps: 5,
  weight: 110
}, {
  id: "plank",
  en: "Plank",
  tr: "Plank",
  muscle: "core",
  equip: "bodyweight",
  sets: 3,
  reps: 60,
  weight: 0
}];
const PLANS = [{
  id: "push",
  en: "Push Day A",
  tr: "İt Günü A",
  minutes: 48,
  ids: ["bench", "incline", "fly", "ohp", "lateral", "pushdown"],
  today: true
}, {
  id: "pull",
  en: "Pull Day A",
  tr: "Çek Günü A",
  minutes: 44,
  ids: ["row", "pulldown", "dip"]
}, {
  id: "legs",
  en: "Leg Day",
  tr: "Bacak Günü",
  minutes: 52,
  ids: ["squat", "plank"]
}];
const t = (lang, key) => STR[lang][key] || key;
const exName = (lang, o) => o[lang];
const byId = id => EXERCISES.find(e => e.id === id);
Object.assign(window, {
  STR,
  MUSCLE,
  EQUIP,
  EXERCISES,
  PLANS,
  t,
  exName,
  byId
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/phone/data.jsx", error: String((e && e.message) || e) }); }

// ui_kits/wear/WatchFrame.jsx
try { (() => {
function WatchFrame({
  shape = "round",
  size = 208,
  children,
  label
}) {
  const bezel = shape === "round" ? {
    borderRadius: "50%",
    boxShadow: "0 0 0 10px #1b1f16, 0 0 0 13px #3a3f33, 0 18px 44px -14px rgba(0,0,0,.85)"
  } : {
    borderRadius: "22%",
    boxShadow: "0 0 0 9px #1b1f16, 0 0 0 12px #3a3f33, 0 18px 44px -14px rgba(0,0,0,.85)"
  };
  return /*#__PURE__*/React.createElement("div", {
    style: {
      display: "flex",
      flexDirection: "column",
      alignItems: "center",
      gap: 14
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      position: "relative",
      width: size,
      height: size,
      ...bezel
    }
  }, children, shape === "round" ? /*#__PURE__*/React.createElement("div", {
    style: {
      position: "absolute",
      right: -13,
      top: "34%",
      width: 7,
      height: 34,
      borderRadius: 4,
      background: "#4c5142"
    }
  }) : null), label ? /*#__PURE__*/React.createElement("span", {
    style: {
      font: "700 11px/1 var(--font-ui)",
      letterSpacing: ".08em",
      textTransform: "uppercase",
      color: "var(--text-quiet)"
    }
  }, label) : null);
}
Object.assign(window, {
  WatchFrame
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/wear/WatchFrame.jsx", error: String((e && e.message) || e) }); }

// ui_kits/wear/WearRemote.jsx
try { (() => {
const DS_W = window.RepForthDesignSystem_c95a40;
const W_STR = {
  en: {
    rest: "Rest",
    set: "Set",
    reps: "reps",
    resume: "Resume",
    skip: "Skip rest",
    complete: "Complete set",
    exercises: "Exercises",
    nextEx: "Next exercise",
    finish: "Finish",
    connected: "Phone connected",
    pick: "Pick exercise"
  },
  tr: {
    rest: "Dinlenme",
    set: "Set",
    reps: "tekrar",
    resume: "Devam et",
    skip: "Dinlenmeyi atla",
    complete: "Seti tamamla",
    exercises: "Egzersizler",
    nextEx: "Sonraki egzersiz",
    finish: "Bitir",
    connected: "Telefon bağlı",
    pick: "Egzersiz seç"
  }
};
const W_EX = [{
  en: "Bench press",
  tr: "Bench press",
  sets: "3/4"
}, {
  en: "Incline press",
  tr: "Eğimli press",
  sets: "0/3"
}, {
  en: "Cable fly",
  tr: "Kablo açma",
  sets: "0/3"
}];
function WearRemote({
  shape = "round",
  size = 208,
  ambient = false,
  lang = "en",
  view,
  setView
}) {
  const {
    WearScreen,
    WearBody,
    WearValue,
    WearArc,
    WearList,
    WearListItem,
    WearAction,
    Icon
  } = DS_W;
  const s = W_STR[lang];
  const [rest, setRest] = React.useState(42);
  const [setNo, setSetNo] = React.useState(3);
  const [weight, setWeight] = React.useState(82.5);
  React.useEffect(() => {
    if (view !== "rest" || ambient) return;
    const id = setInterval(() => setRest(r => r <= 1 ? 90 : r - 1), 1000);
    return () => clearInterval(id);
  }, [view, ambient]);
  const body = () => {
    if (view === "list") {
      return /*#__PURE__*/React.createElement(WearBody, null, /*#__PURE__*/React.createElement(WearList, null, /*#__PURE__*/React.createElement(WearListItem, {
        primary: true,
        icon: "play_arrow",
        label: s.resume,
        onClick: () => setView("set")
      }), W_EX.map(e => /*#__PURE__*/React.createElement(WearListItem, {
        key: e.en,
        icon: "fitness_center",
        label: e[lang],
        value: e.sets,
        onClick: () => setView("set")
      })), /*#__PURE__*/React.createElement(WearListItem, {
        icon: "flag",
        label: s.finish,
        onClick: () => setView("set")
      })));
    }
    if (view === "rest") {
      return /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement(WearArc, {
        value: rest / 90,
        tone: "rest"
      }), /*#__PURE__*/React.createElement(WearBody, null, /*#__PURE__*/React.createElement("span", {
        className: "rf-wear__title"
      }, s.rest + " · " + s.set + " " + setNo), /*#__PURE__*/React.createElement(WearValue, {
        value: "0:" + String(rest).padStart(2, "0"),
        caption: ambient ? W_EX[0][lang] : undefined,
        size: shape === "round" ? 44 : 48
      }), !ambient ? /*#__PURE__*/React.createElement(WearAction, {
        actions: [{
          icon: "skip_next",
          label: s.skip,
          tone: "primary",
          onClick: () => setView("set")
        }]
      }) : null));
    }
    return /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement(WearArc, {
      value: setNo / 4
    }), /*#__PURE__*/React.createElement(WearBody, null, /*#__PURE__*/React.createElement("span", {
      className: "rf-wear__title"
    }, s.set + " " + setNo + " / 4 · × 8 " + s.reps), /*#__PURE__*/React.createElement(WearValue, {
      value: weight,
      unit: "kg",
      caption: ambient ? W_EX[0][lang] : undefined,
      size: shape === "round" ? 42 : 46
    }), !ambient ? /*#__PURE__*/React.createElement(WearAction, {
      actions: [{
        icon: "check",
        label: s.complete,
        tone: "primary",
        onClick: () => {
          setSetNo(n => Math.min(4, n + 1));
          setView("rest");
        }
      }]
    }) : null));
  };
  return /*#__PURE__*/React.createElement(WearScreen, {
    shape: shape,
    size: size,
    ambient: ambient
  }, body());
}
Object.assign(window, {
  WearRemote,
  W_STR
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/wear/WearRemote.jsx", error: String((e && e.message) || e) }); }

__ds_ns.Badge = __ds_scope.Badge;

__ds_ns.Button = __ds_scope.Button;

__ds_ns.Card = __ds_scope.Card;

__ds_ns.Chip = __ds_scope.Chip;

__ds_ns.Divider = __ds_scope.Divider;

__ds_ns.FAB = __ds_scope.FAB;

__ds_ns.Icon = __ds_scope.Icon;

__ds_ns.IconButton = __ds_scope.IconButton;

__ds_ns.StatBlock = __ds_scope.StatBlock;

__ds_ns.Dialog = __ds_scope.Dialog;

__ds_ns.EmptyState = __ds_scope.EmptyState;

__ds_ns.ProgressBar = __ds_scope.ProgressBar;

__ds_ns.ProgressRing = __ds_scope.ProgressRing;

__ds_ns.Snackbar = __ds_scope.Snackbar;

__ds_ns.Checkbox = __ds_scope.Checkbox;

__ds_ns.Radio = __ds_scope.Radio;

__ds_ns.SegmentedButtons = __ds_scope.SegmentedButtons;

__ds_ns.SelectField = __ds_scope.SelectField;

__ds_ns.Slider = __ds_scope.Slider;

__ds_ns.Stepper = __ds_scope.Stepper;

__ds_ns.Switch = __ds_scope.Switch;

__ds_ns.TextField = __ds_scope.TextField;

__ds_ns.ListItem = __ds_scope.ListItem;

__ds_ns.NavigationBar = __ds_scope.NavigationBar;

__ds_ns.Tabs = __ds_scope.Tabs;

__ds_ns.TopAppBar = __ds_scope.TopAppBar;

__ds_ns.WearAction = __ds_scope.WearAction;

__ds_ns.WearArc = __ds_scope.WearArc;

__ds_ns.WearListItem = __ds_scope.WearListItem;

__ds_ns.WearList = __ds_scope.WearList;

__ds_ns.WearScreen = __ds_scope.WearScreen;

__ds_ns.WearBody = __ds_scope.WearBody;

__ds_ns.WearValue = __ds_scope.WearValue;

__ds_ns.ExerciseCard = __ds_scope.ExerciseCard;

__ds_ns.PlanCard = __ds_scope.PlanCard;

__ds_ns.RestTimer = __ds_scope.RestTimer;

__ds_ns.SetRow = __ds_scope.SetRow;

__ds_ns.SetRowHeader = __ds_scope.SetRowHeader;

})();
