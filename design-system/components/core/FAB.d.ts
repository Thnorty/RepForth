/** The screen's one floating action. Extended form carries the label — preferred, because Turkish labels need the room. */
export interface FABProps extends Omit<React.ButtonHTMLAttributes<HTMLButtonElement>, "children"> {
  icon: string;
  /** Visible label when extended, accessible name when not. */
  label: string;
  extended?: boolean;
  size?: "regular" | "large";
}
export declare function FAB(props: FABProps): JSX.Element;
