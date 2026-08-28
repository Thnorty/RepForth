/**
 * The set / rest timer ring. Sweeps clockwise from 12 o'clock; the centre holds
 * a numeric hero (usually a StatBlock). Reduced motion collapses the sweep to a
 * jump — the countdown text stays authoritative.
 */
export interface ProgressRingProps {
  /** 0–1. */
  value?: number;
  /** Outer diameter in px. 200+ for the session screen, 120 for cards, 160 on Wear. */
  size?: number;
  /** Stroke width; defaults to 6% of size. */
  stroke?: number;
  /** accent = working set, rest = amber countdown, done = completed. */
  tone?: "accent" | "rest" | "done";
  /** Accessible description, e.g. "Rest 1 minute 30 seconds remaining". */
  label?: string;
  /** Centre content — a StatBlock or countdown. */
  children?: React.ReactNode;
  className?: string;
}
export declare function ProgressRing(props: ProgressRingProps): JSX.Element;
