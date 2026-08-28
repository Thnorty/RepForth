/** 48dp checkbox row. The check glyph carries the state, so it survives colour-blind and greyscale viewing. */
export interface CheckboxProps {
  label: string;
  description?: string;
  checked?: boolean;
  onChange?: (next: boolean) => void;
  disabled?: boolean;
  className?: string;
}
export declare function Checkbox(props: CheckboxProps): JSX.Element;
