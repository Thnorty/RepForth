/** Single-line input. Errors always show an icon next to the message, never red text alone. */
export interface TextFieldProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, "onChange" | "value"> {
  label?: string;
  value?: string | number;
  onChange?: (e: React.ChangeEvent<HTMLInputElement>) => void;
  placeholder?: string;
  /** Leading Material Symbols name, e.g. "search". */
  icon?: string;
  /** Trailing unit text, e.g. "kg". */
  suffix?: string;
  helper?: string;
  /** Error message; presence switches the field to the error state. */
  error?: string;
  /** Renders the value in the heavy tabular numeric face. */
  numeric?: boolean;
}
export declare function TextField(props: TextFieldProps): JSX.Element;
