import { cx } from '../lib/cx';
import styles from './Cover.module.css';

const tones = [styles.tomato, styles.lime, styles.sun, styles.ink, styles.rose, styles.mint];

type CoverProps = {
  title: string;
  seed: number;
  className?: string;
};

export function Cover({ title, seed, className }: CoverProps) {
  const tone = tones[Math.abs(Math.trunc(seed)) % tones.length];
  const initial = title.trim().charAt(0).toUpperCase();

  return (
    <span className={cx(styles.cover, tone, className)} aria-hidden="true">
      <span className={styles.initial}>{initial}</span>
    </span>
  );
}
