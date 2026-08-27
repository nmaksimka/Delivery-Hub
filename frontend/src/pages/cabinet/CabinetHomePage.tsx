import { Link } from 'react-router';
import { errorMessage } from '../../api/http';
import { useCabinetRestaurants } from '../../api/queries';
import { Button } from '../../components/Button';
import { StateMessage } from '../../components/StateMessage';
import { formatRating, ratingCountLabel } from '../../lib/rating';
import styles from '../Management.module.css';

export function CabinetHomePage() {
  const restaurants = useCabinetRestaurants();

  return (
    <>
      <div className={styles.head}>
        <h1 className={styles.title}>Кабинет ресторана</h1>
        <p className={styles.lead}>Меню, стоп-лист, заказы и аналитика ваших ресторанов.</p>
      </div>

      {restaurants.isPending && (
        <div className={styles.skeleton} aria-busy="true" aria-label="Загружаем рестораны" />
      )}

      {restaurants.isError && (
        <StateMessage
          role="alert"
          title="Не получилось загрузить рестораны"
          text={errorMessage(restaurants.error)}
        >
          <Button
            variant="dark"
            onClick={() => {
              void restaurants.refetch();
            }}
          >
            Повторить
          </Button>
        </StateMessage>
      )}

      {restaurants.data?.length === 0 && (
        <StateMessage
          title="Пока нет ресторанов"
          text="Администратор ещё не назначил вам ресторан."
        />
      )}

      {restaurants.data && restaurants.data.length > 0 && (
        <ul className={styles.list}>
          {restaurants.data.map((restaurant) => (
            <li key={restaurant.id} className={styles.row}>
              <div>
                <p className={styles.rowTitle}>{restaurant.name}</p>
                <p className={styles.rowMeta}>
                  {formatRating(restaurant.rating, restaurant.ratingCount)} ·{' '}
                  {ratingCountLabel(restaurant.ratingCount)}
                </p>
              </div>
              <Link to={`/cabinet/${restaurant.id}/menu`}>Открыть</Link>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
