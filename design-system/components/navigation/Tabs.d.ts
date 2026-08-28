/** In-screen view switch (Overview / History / Records on an exercise; Muscle / Equipment in filters). */
export interface TabItem { value: string; label: string; icon?: string; count?: number }
export interface TabsProps {
  items: TabItem[];
  value?: string;
  onChange?: (next: string) => void;
  /** Horizontal scroll instead of equal-width tabs — use when Turkish labels overflow. */
  scrollable?: boolean;
  className?: string;
}
export declare function Tabs(props: TabsProps): JSX.Element;
