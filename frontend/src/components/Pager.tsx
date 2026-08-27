import { Button } from './Button';
import styles from './Pager.module.css';

type PagerProps = {
  label: string;
  page: number;
  totalPages: number;
  busy: boolean;
  onChange: (page: number) => void;
};

export function Pager({ label, page, totalPages, busy, onChange }: PagerProps) {
  if (totalPages <= 1) {
    return null;
  }
  return (
    <nav className={styles.pager} aria-label={label}>
      <Button
        variant="ghost"
        size="small"
        disabled={page === 0 || busy}
        onClick={() => {
          onChange(page - 1);
        }}
      >
        Назад
      </Button>
      <span className={styles.info}>
        {page + 1} из {totalPages}
      </span>
      <Button
        variant="ghost"
        size="small"
        disabled={page + 1 >= totalPages || busy}
        onClick={() => {
          onChange(page + 1);
        }}
      >
        Вперёд
      </Button>
    </nav>
  );
}
