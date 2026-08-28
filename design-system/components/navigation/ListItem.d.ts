/** Catalog / plan / settings row. `media` takes a strictly 1:1 exercise thumbnail. */
export interface ListItemProps {
  title: string;
  /** Metadata line — chips, muscle names, set counts. */
  subtitle?: React.ReactNode;
  /** 48x48 media node, e.g. <img src=… alt="" />. */
  media?: React.ReactNode;
  /** Material Symbols name used when there is no media. */
  mediaIcon?: string;
  trailing?: React.ReactNode;
  trailingIcon?: string;
  onClick?: () => void;
  className?: string;
}
export declare function ListItem(props: ListItemProps): JSX.Element;
