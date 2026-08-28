/**
 * Muscle group / equipment filter and metadata tag. Always icon + text — a chip
 * never communicates through colour alone, and selection also shows a check glyph.
 * With neither onClick nor selected it renders a <span>, so it is safe to nest inside
 * a clickable Card or ListItem.
 */
export interface ChipProps {
  /** Material Symbols name shown when unselected. */
  icon?: string;
  label: string;
  /** Optional result count, rendered in tabular numerals. */
  count?: number;
  /** Present = toggle chip. */
  selected?: boolean;
  /** md = 48dp filter target, sm = 32dp read-only metadata tag. */
  size?: "md" | "sm";
  trailingIcon?: string;
  onClick?: () => void;
  className?: string;
}
export declare function Chip(props: ChipProps): JSX.Element;
