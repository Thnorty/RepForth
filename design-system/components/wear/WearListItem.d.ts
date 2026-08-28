/** Rotary-scrollable watch row, 52dp min, pill shaped. One line of text, optional numeral. */
export interface WearListItemProps {
  icon?: string;
  label: string;
  /** Trailing numeral, e.g. set count. */
  value?: React.ReactNode;
  /** Lime fill for the single primary row. */
  primary?: boolean;
  onClick?: () => void;
  className?: string;
}
export declare function WearListItem(props: WearListItemProps): JSX.Element;
/** Scroll column for WearListItems. */
export declare function WearList(props: { children?: React.ReactNode; className?: string }): JSX.Element;
