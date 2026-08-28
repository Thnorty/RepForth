/**
 * A saved or scheduled workout. `today` gives it the accent hairline used for
 * the next session on the Today screen.
 */
export interface MuscleTag { label: string; icon: string; /** Stable id used for de-duplication and React keys; falls back to label. */ id?: string }
export interface PlanCardProps {
  name: string;
  /** Exercise count — rendered as a numeral. */
  exercises: number;
  /** Localised unit after the count, e.g. "exercises" / "egzersiz". */
  exercisesLabel?: string;
  /** Estimated duration in minutes. */
  minutes: number;
  /** Localised duration unit, e.g. "min" / "dk". */
  minutesLabel?: string;
  /** Muscle / equipment tags, icon + text. Duplicates are dropped. */
  muscles?: MuscleTag[];
  /** Short status word, e.g. "Today", "Bugün", "Week 4". */
  badge?: string;
  today?: boolean;
  onClick?: () => void;
  /** Optional footer node, usually a Button. */
  action?: React.ReactNode;
  className?: string;
}
export declare function PlanCard(props: PlanCardProps): JSX.Element;
