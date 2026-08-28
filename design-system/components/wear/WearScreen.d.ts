/**
 * Wear OS canvas. Renders the watch viewport at true proportions for both round
 * and square devices, with an ambient flag that switches to the black / dim
 * monochrome treatment (no fills, lighter numerals).
 */
export interface WearScreenProps {
  shape?: "round" | "square";
  /** Viewport size in px — 208 ≈ 1.3" round, 192 square. */
  size?: number;
  /** Ambient (always-on) rendering. */
  ambient?: boolean;
  children?: React.ReactNode;
  className?: string;
}
export declare function WearScreen(props: WearScreenProps): JSX.Element;
/** Safe-area content column inside a WearScreen (14% inset round, 9% square). */
export declare function WearBody(props: { children?: React.ReactNode; className?: string }): JSX.Element;
/** Glanceable numeral for the watch: value, quiet unit, one caption line. */
export declare function WearValue(props: { value: React.ReactNode; unit?: string; caption?: string; size?: number; className?: string }): JSX.Element;
