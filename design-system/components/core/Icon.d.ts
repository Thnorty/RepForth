/** Material Symbols Rounded glyph. Use for every icon in RepForth; never inline hand-drawn SVG. */
export interface IconProps {
  /** Material Symbols Rounded ligature name, e.g. "fitness_center". */
  name: string;
  /** Optical size in px. 20 inside dense rows, 24 default, 28-32 in-session. */
  size?: number;
  /** Filled variant — reserved for selected/active states. */
  fill?: boolean;
  /** 100-700. 500+ for in-session glanceability. */
  weight?: number;
  color?: string;
  className?: string;
  /** Accessible name. Omit for decorative icons (defaults to aria-hidden). */
  label?: string;
  style?: React.CSSProperties;
}
export declare function Icon(props: IconProps): JSX.Element;
