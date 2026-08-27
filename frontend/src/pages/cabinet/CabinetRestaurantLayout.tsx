import { NavLink, Outlet, useParams } from 'react-router';
import { errorMessage } from '../../api/http';
import { useCabinetRestaurant } from '../../api/queries';
import { StateMessage } from '../../components/StateMessage';
import { cx } from '../../lib/cx';
import { formatClock } from '../../lib/format';
import { formatRating, ratingCountLabel } from '../../lib/rating';
import styles from '../Management.module.css';

const tabs = [
  { to: 'menu', label: 'Меню' },
  { to: 'orders', label: 'Заказы' },
  { to: 'analytics', label: 'Аналитика' },
];

export function CabinetRestaurantLayout() {
  const restaurantId = Number(useParams().restaurantId);
  const restaurant = useCabinetRestaurant(restaurantId);

  if (restaurant.isError) {
    return (
      <StateMessage
        role="alert"
        title="Ресторан недоступен"
        text={errorMessage(restaurant.error)}
      />
    );
  }

  return (
    <>
      <div className={styles.head}>
        <h1 className={styles.title}>{restaurant.data?.name ?? 'Ресторан'}</h1>
        {restaurant.data && (
          <p className={styles.lead}>
            Рейтинг {formatRating(restaurant.data.rating, restaurant.data.ratingCount)} (
            {ratingCountLabel(restaurant.data.ratingCount)})
            {restaurant.data.openTime && restaurant.data.closeTime
              ? `, работает ${formatClock(restaurant.data.openTime)}–${formatClock(restaurant.data.closeTime)}`
              : ''}
          </p>
        )}
      </div>
      <nav className={styles.tabs} aria-label="Разделы ресторана">
        {tabs.map((tab) => (
          <NavLink
            key={tab.to}
            to={tab.to}
            className={({ isActive }) => cx(styles.tab, isActive && styles.tabActive)}
          >
            {tab.label}
          </NavLink>
        ))}
      </nav>
      <Outlet />
    </>
  );
}
