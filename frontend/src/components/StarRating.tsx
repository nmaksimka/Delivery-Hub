import { cx } from '../lib/cx';
import { ratingValues } from '../lib/rating';
import { StarIcon } from './icons';
import styles from './StarRating.module.css';

type StarRatingProps = {
  label: string;
  value: number | null;
  onChange: (value: number | null) => void;
  clearable?: boolean;
  disabled?: boolean;
};

export function StarRating({
  label,
  value,
  onChange,
  clearable = false,
  disabled,
}: StarRatingProps) {
  return (
    <div className={styles.group} role="radiogroup" aria-label={label}>
      {ratingValues.map((rating) => {
        const checked = value === rating;
        return (
          <button
            key={rating}
            type="button"
            role="radio"
            aria-checked={checked}
            aria-label={`${rating} из 5`}
            disabled={disabled}
            className={cx(styles.star, value !== null && rating <= value && styles.on)}
            onClick={() => {
              onChange(checked && clearable ? null : rating);
            }}
          >
            <StarIcon size={28} />
          </button>
        );
      })}
    </div>
  );
}
