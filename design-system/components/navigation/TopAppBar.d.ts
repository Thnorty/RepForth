/** Screen header. `large` is the home/landing form (display-weight title on its own line); the compact form is for detail screens. */
export interface AppBarAction { icon: string; label: string; onClick?: () => void; selected?: boolean }
export interface TopAppBarProps {
  title: string;
  subtitle?: string;
  /** Leading Material Symbols name, usually "arrow_back" or "close". */
  leadingIcon?: string;
  onLeading?: () => void;
  leadingLabel?: string;
  actions?: AppBarAction[];
  large?: boolean;
  /** Raises the bar onto a container surface once content scrolls under it. */
  scrolled?: boolean;
  className?: string;
}
export declare function TopAppBar(props: TopAppBarProps): JSX.Element;
