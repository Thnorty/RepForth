/** Settings toggle: label left, track right, immediate effect (no Save button). */
export interface SwitchProps {
  label: string;
  description?: string;
  checked?: boolean;
  onChange?: (next: boolean) => void;
  disabled?: boolean;
  className?: string;
}
export declare function Switch(props: SwitchProps): JSX.Element;
