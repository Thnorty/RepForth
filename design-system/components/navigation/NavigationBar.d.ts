/**
 * Bottom navigation, 3–5 destinations, 80dp tall. The active item gets a filled
 * glyph and a tonal pill — never colour alone.
 */
export interface NavItem { value: string; icon: string; label: string }
export interface NavigationBarProps {
  items: NavItem[];
  value?: string;
  onChange?: (next: string) => void;
  className?: string;
}
export declare function NavigationBar(props: NavigationBarProps): JSX.Element;
