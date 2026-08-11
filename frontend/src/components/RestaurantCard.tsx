import { Link } from 'react-router';
import type { Restaurant } from '../api/restaurants';
import { cx } from '../lib/cx';
import { placeholderEta, placeholderRating } from '../lib/placeholders';
import { Cover } from './Cover';
import { FavoriteButton } from './FavoriteButton';
import { ClockIcon, StarIcon } from './icons';
import styles from './RestaurantCard.module.css';

type RestaurantCardProps = {
  restaurant: Restaurant;
  closedBadge: string | null;
};

export function RestaurantCard({ restaurant, closedBadge }: RestaurantCardProps) {
  const place = [restaurant.address, restaurant.city].filter(Boolean).join(', ');

  return (
    <div className={styles.root}>
      <Link to={`/restaurants/${restaurant.id}`} className={styles.card}>
        <span className={styles.media}>
          <Cover
            title={restaurant.name}
            seed={restaurant.id}
            imageUrl={restaurant.imageUrl}
            className={cx(styles.cover, closedBadge && styles.dimmed)}
          />
          {closedBadge && <span className={styles.closed}>{closedBadge}</span>}
          {restaurant.cuisine && <span className={styles.cuisine}>{restaurant.cuisine}</span>}
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
          {place && <p className={styles.address}>{place}</p>}
        </div>
      </Link>
      <FavoriteButton
        restaurantId={restaurant.id}
        restaurantName={restaurant.name}
        className={styles.favorite}
      />
    </div>
  );
}
