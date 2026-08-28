/** Row of one to three watch actions. Primary 56dp, secondaries 48dp. ONE action on a round face: two or three side by side reach past the progress arc. Weight and rest adjustment belongs on the phone — the watch confirms and skips. Icon-only with accessible labels. */
export interface WearActionItem { icon: string; label: string; tone?: "neutral" | "primary" | "danger"; onClick?: () => void }
export interface WearActionProps {
  actions: WearActionItem[];
  className?: string;
}
export declare function WearAction(props: WearActionProps): JSX.Element;
