/** Square-target icon-only control. `label` is required — it is the only accessible name. */
export interface IconButtonProps extends Omit<React.ButtonHTMLAttributes<HTMLButtonElement>, "children"> {
  /** Material Symbols name. */
  icon: string;
  /** Accessible name, e.g. "Skip rest". Required. */
  label: string;
  variant?: "standard" | "filled" | "tonal" | "outlined";
  /** md 48dp, lg 56dp, session 64dp (squared corners, in-workout). */
  size?: "md" | "lg" | "session";
  /** Makes it a toggle: renders aria-pressed and the filled glyph. */
  selected?: boolean;
  disabled?: boolean;
}
export declare function IconButton(props: IconButtonProps): JSX.Element;
