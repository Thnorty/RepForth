/** Single-choice row. Group 2–5 of them under a field label; more than that, use SelectField. */
export interface RadioProps {
  label: string;
  description?: string;
  checked?: boolean;
  onChange?: (next: true) => void;
  name?: string;
  disabled?: boolean;
  className?: string;
}
export declare function Radio(props: RadioProps): JSX.Element;
