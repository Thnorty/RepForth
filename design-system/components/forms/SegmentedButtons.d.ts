/** 2–3 mutually exclusive views (Plans / Catalog, kg / lb, Round / Square). Selected segment adds a check glyph. */
export interface SegmentOption { value: string; label: string; icon?: string }
export interface SegmentedButtonsProps {
  options: (SegmentOption | string)[];
  value?: string;
  onChange?: (next: string) => void;
  /** Accessible group label. */
  label?: string;
  className?: string;
}
export declare function SegmentedButtons(props: SegmentedButtonsProps): JSX.Element;
