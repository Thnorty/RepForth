/** Linear progress for workout completion. `segmented` renders one pip per set — the preferred in-session form. */
export interface ProgressBarProps {
  /** Completed count when `total` is set, otherwise a 0–1 fraction. */
  value?: number;
  total?: number;
  label?: string;
  showValue?: boolean;
  /** One pip per unit instead of a continuous bar. */
  segmented?: boolean;
  /** Zero-based index of the in-progress pip. */
  current?: number;
  className?: string;
}
export declare function ProgressBar(props: ProgressBarProps): JSX.Element;
