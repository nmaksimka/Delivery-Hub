import { useFavorites, useToggleFavorite } from '../api/queries';
import { useAuth } from '../auth/AuthContext';
import { cx } from '../lib/cx';
import styles from './FavoriteButton.module.css';
import { HeartIcon } from './icons';

type FavoriteButtonProps = {
  restaurantId: number;
  restaurantName: string;
  className?: string;
};

export function FavoriteButton({ restaurantId, restaurantName, className }: FavoriteButtonProps) {
  const { user } = useAuth();
  const favorites = useFavorites();
  const toggle = useToggleFavorite();

  if (!user || !favorites.isSuccess) {
    return null;
  }

  const favorite = favorites.data.includes(restaurantId);

  return (
    <button
      type="button"
      className={cx(styles.button, favorite && styles.active, className)}
      aria-pressed={favorite}
      aria-label={
        favorite
          ? `Убрать из избранного: ${restaurantName}`
          : `Добавить в избранное: ${restaurantName}`
      }
      onClick={() => {
        toggle.mutate({ restaurantId, favorite: !favorite });
      }}
    >
      <HeartIcon filled={favorite} />
    </button>
  );
}
