/** Native select in RepForth field chrome — used for units, locale, rest presets. */
export interface SelectOption { value: string; label: string }
export interface SelectFieldProps extends Omit<React.SelectHTMLAttributes<HTMLSelectElement>, "onChange" | "value" | "children"> {
  label?: string;
  value?: string;
  onChange?: (e: React.ChangeEvent<HTMLSelectElement>) => void;
  options: (SelectOption | string)[];
  icon?: string;
  helper?: string;
}
export declare function SelectField(props: SelectFieldProps): JSX.Element;
