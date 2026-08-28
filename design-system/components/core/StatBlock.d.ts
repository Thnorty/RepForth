/**
 * The numbers-are-the-hero primitive: a big tabular figure with a quiet unit and label.
 * Use it for reps, weight, volume, set counts, streaks and countdowns.
 */
export interface StatBlockProps {
  /** The figure. Pass a string so you control formatting/locale. */
  value: React.ReactNode;
  /** Quiet suffix, e.g. "kg", "reps", "min". */
  unit?: string;
  /** Quiet caption under the figure. */
  label?: string;
  /** Maps to the numeric type scale. hero/xl are session-screen only. */
  size?: "hero" | "xl" | "lg" | "md" | "sm" | "xs";
  tone?: "default" | "accent";
  align?: "start" | "center";
  className?: string;
}
export declare function StatBlock(props: StatBlockProps): JSX.Element;
