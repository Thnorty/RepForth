/** Exercise entry inside a plan or catalog grid. Media is strictly 1:1 — never blurred, never a full-bleed background. */
export interface ExerciseTag { label: string; icon: string }
export interface ExerciseCardProps {
  name: string;
  /** 1:1 media node, e.g. <img className="rf-media-square" src=… alt="" />. */
  media?: React.ReactNode;
  tags?: ExerciseTag[];
  sets?: number;
  reps?: number;
  /** row = list layout with 72px thumb; stacked = grid card with full-width 1:1 media. */
  layout?: "row" | "stacked";
  trailing?: React.ReactNode;
  onClick?: () => void;
  className?: string;
}
export declare function ExerciseCard(props: ExerciseCardProps): JSX.Element;
