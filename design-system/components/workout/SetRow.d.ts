/**
 * One logged set inside the active session. Completion springs the check button
 * and tints the row; the check glyph means it never relies on colour.
 */
export interface SetRowProps {
  /** 1-based set number. */
  index: number;
  weight: number | string;
  reps: number | string;
  unit?: "kg" | "lb";
  /** Last session's figures, e.g. "Last: 80 kg × 10". */
  previous?: string;
  done?: boolean;
  /** The set currently being performed — accent outline. */
  active?: boolean;
  onToggle?: () => void;
  className?: string;
}
export declare function SetRow(props: SetRowProps): JSX.Element;
/** Column headers for a SetRow stack. */
export declare function SetRowHeader(props: { weightLabel?: string; repsLabel?: string; className?: string }): JSX.Element;
