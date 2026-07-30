import { cx } from '../lib/cx';
import styles from './Chips.module.css';

type ChipOption = {
  id: string;
  label: string;
};

type ChipsProps = {
  label: string;
  options: ChipOption[];
  activeId: string | null;
  onSelect: (id: string) => void;
};

export function Chips({ label, options, activeId, onSelect }: ChipsProps) {
  return (
    <div className={styles.row} role="group" aria-label={label}>
      {options.map((option) => (
        <button
          key={option.id}
          type="button"
          className={cx(styles.chip, option.id === activeId && styles.active)}
          aria-pressed={option.id === activeId}
          onClick={() => {
            onSelect(option.id);
          }}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}
