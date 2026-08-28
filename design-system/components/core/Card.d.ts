/** Compact rounded container: 12px radius, 16px padding, surface-container fill. */
export interface CardProps {
  /** filled = default charcoal container; outlined = hairline on surface; elevated = raised + shadow. */
  variant?: "filled" | "outlined" | "elevated";
  /** md = 12px radius / 16px pad, lg = 16px radius / 20px pad. */
  size?: "md" | "lg";
  /** Removes padding and clips children — for cards led by 1:1 media. */
  flush?: boolean;
  /** Renders a <button> with press feedback. */
  interactive?: boolean;
  as?: keyof JSX.IntrinsicElements;
  children?: React.ReactNode;
  className?: string;
}
export declare function Card(props: CardProps): JSX.Element;
