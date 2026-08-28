/**
 * Plus/minus numeric control for weight, reps and rest. The value is a numeric
 * hero; the buttons are 48dp (md) or 64dp (session, for sweaty hands mid-set).
 */
export interface StepperProps {
  value: number;
  unit?: string;
  /** 2.5 for kg, 1 for reps, 15 for rest seconds. */
  step?: number;
  min?: number;
  max?: number;
  onChange?: (next: number) => void;
  size?: "md" | "session";
  /** Accessible group label, e.g. "Weight". */
  label?: string;
  className?: string;
}
export declare function Stepper(props: StepperProps): JSX.Element;
