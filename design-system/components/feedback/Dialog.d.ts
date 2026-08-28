/** Modal confirm (discard workout, delete plan) or bottom sheet (pickers, options). Positions absolutely inside the nearest positioned ancestor — the phone frame. */
export interface DialogProps {
  open?: boolean;
  title?: string;
  children?: React.ReactNode;
  /** Buttons, right-aligned; destructive confirm uses variant="danger". */
  actions?: React.ReactNode;
  onDismiss?: () => void;
  /** Bottom-sheet form with a drag grip. */
  sheet?: boolean;
  className?: string;
}
export declare function Dialog(props: DialogProps): JSX.Element;
