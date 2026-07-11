import { Link } from 'react-router';
import type { Restaurant } from '../api/restaurants';
import { cx } from '../lib/cx';
import { formatClock } from '../lib/format';
import { placeholderEta, placeholderRating } from '../lib/placeholders';
import { Cover } from './Cover';
import { ClockIcon, StarIcon } from './icons';
import styles from './RestaurantCard.module.css';

type RestaurantCardProps = {
  restaurant: Restaurant;
  open: boolean;
};

export function RestaurantCard({ restaurant, open }: RestaurantCardProps) {
  const opensAt = formatClock(restaurant.openTime);

  return (
    <Link to={`/restaurants/${restaurant.id}`} className={styles.card}>
      <span className={styles.media}>
        <Cover
          title={restaurant.name}
          seed={restaurant.id}
          className={cx(styles.cover, !open && styles.dimmed)}
        />
        {!open && (
          <span className={styles.closed}>{opensAt ? `Закрыто до ${opensAt}` : 'Закрыто'}</span>
        )}
      </span>
      <div>
        <h3 className={styles.name}>{restaurant.name}</h3>
        <div className={styles.meta}>
          <span className={styles.metaItem}>
            <StarIcon size={16} className={styles.star} />
            {placeholderRating(restaurant.id)}
          </span>
          <span className={styles.metaItem}>
            <ClockIcon size={16} />
            {placeholderEta(restaurant.id)}
          </span>
        </div>
        {restaurant.address && <p className={styles.address}>{restaurant.address}</p>}
      </div>
    </Link>
  );
}
