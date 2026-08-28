/**
 * Primary action control. "filled" is the single lime action per screen.
 */
export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  /** filled = the one lime action; tonal = secondary; outlined/text = tertiary; danger = destructive. */
  variant?: "filled" | "tonal" | "outlined" | "text" | "danger";
  /** sm 40dp (never a primary action), md 48dp, lg 56dp, session 64dp for in-workout controls. */
  size?: "sm" | "md" | "lg" | "session";
  /** Leading Material Symbols name. */
  icon?: string;
  trailingIcon?: string;
  fullWidth?: boolean;
  disabled?: boolean;
  children?: React.ReactNode;
}
export declare function Button(props: ButtonProps): JSX.Element;
