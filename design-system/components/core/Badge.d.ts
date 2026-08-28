/** Small status marker: PR, week counter, sync-free notice, unread dot. */
export interface BadgeProps {
  /** Visible text; for a dot badge this becomes the accessible name. */
  label: string;
  icon?: string;
  tone?: "neutral" | "accent" | "amber" | "error" | "info";
  /** Renders a 10px dot with no text. */
  dot?: boolean;
  className?: string;
}
export declare function Badge(props: BadgeProps): JSX.Element;
