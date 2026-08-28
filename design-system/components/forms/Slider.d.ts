/** Continuous range with the live value shown in numerals (RPE, rest length, weekly volume target). */
export interface SliderProps {
  label: string;
  value: number;
  min?: number;
  max?: number;
  step?: number;
  unit?: string;
  onChange?: (next: number) => void;
  /** Custom display formatter, e.g. seconds -> "1:30". */
  format?: (v: number) => string;
  className?: string;
}
export declare function Slider(props: SliderProps): JSX.Element;
