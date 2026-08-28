/** Transient confirmation with one optional undo action. Never used for errors that need a decision — that is a Dialog. */
export interface SnackbarProps {
  message: string;
  actionLabel?: string;
  onAction?: () => void;
  icon?: string;
  tone?: "default" | "error";
  className?: string;
}
export declare function Snackbar(props: SnackbarProps): JSX.Element;
