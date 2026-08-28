/**
 * Rest countdown: amber ring, numeric hero, and three large controls. The only
 * looping motion allowed on the session screen.
 */
export interface RestTimerProps {
  /** Seconds left. */
  remaining: number;
  /** Seconds the rest started from — drives the ring. */
  total?: number;
  /** Localised heading, e.g. "Rest" / "Dinlenme". */
  label?: string;
  /** One line naming what comes next, e.g. "Next: Incline dumbbell press · Set 2". */
  nextUp?: string;
  /** Ring diameter. */
  size?: number;
  onSkip?: () => void;
  onAdd?: () => void;
  onSubtract?: () => void;
  skipLabel?: string;
  className?: string;
}
export declare function RestTimer(props: RestTimerProps): JSX.Element;
