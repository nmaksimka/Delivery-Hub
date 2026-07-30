import { useState } from 'react';
import { cx } from '../lib/cx';
import styles from './Cover.module.css';

const tones = [styles.tomato, styles.lime, styles.sun, styles.ink, styles.rose, styles.mint];

type CoverProps = {
  title: string;
  seed: number;
  imageUrl?: string | null;
  className?: string;
};

export function Cover({ title, seed, imageUrl, className }: CoverProps) {
  const [failedUrl, setFailedUrl] = useState<string | null>(null);
  const tone = tones[Math.abs(Math.trunc(seed)) % tones.length];
  const initial = title.trim().charAt(0).toUpperCase();
  const photo = imageUrl && imageUrl !== failedUrl ? imageUrl : null;

  return (
    <span className={cx(styles.cover, tone, className)} aria-hidden="true">
      {photo ? (
        <img
          className={styles.photo}
          src={photo}
          alt=""
          loading="lazy"
          decoding="async"
          onError={() => {
            setFailedUrl(photo);
          }}
        />
      ) : (
        <span className={styles.initial}>{initial}</span>
      )}
    </span>
  );
}
