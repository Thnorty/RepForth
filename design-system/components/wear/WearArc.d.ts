/** Edge-hugging progress arc for the watch — set progress or rest countdown. Goes dim monochrome in ambient. */
export interface WearArcProps {
  /** 0–1. */
  value?: number;
  tone?: "accent" | "rest";
  /** Stroke width in viewBox units (viewBox is 100x100); default 3.5 ≈ 7px on a 200px face. */
  stroke?: number;
  /** Distance from the screen edge in viewBox units. */
  inset?: number;
  className?: string;
}
export declare function WearArc(props: WearArcProps): JSX.Element;
