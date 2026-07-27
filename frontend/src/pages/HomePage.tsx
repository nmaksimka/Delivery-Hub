import { useState } from 'react';
import { useSearchParams } from 'react-router';
import { errorMessage } from '../api/http';
import { useRestaurants } from '../api/queries';
import { Button } from '../components/Button';
import { Chips } from '../components/Chips';
import { SearchIcon } from '../components/icons';
import { RestaurantCard } from '../components/RestaurantCard';
import { StateMessage } from '../components/StateMessage';
import { cx } from '../lib/cx';
import { plural } from '../lib/format';
import { allFilterId, filterRestaurants, quickFilters } from '../lib/restaurantFilters';
import { isOpenAt } from '../lib/workingHours';
import { useNow } from '../lib/useNow';
import styles from './HomePage.module.css';

const skeletons = [1, 2, 3, 4, 5, 6];

export function HomePage() {
  const restaurants = useRestaurants();
  const now = useNow();
  const [searchParams, setSearchParams] = useSearchParams();
  const [filterId, setFilterId] = useState(allFilterId);
  const query = searchParams.get('q') ?? '';

  const visible = filterRestaurants(restaurants.data ?? [], { query, filterId, now });

  function changeQuery(value: string) {
    setSearchParams(value ? { q: value } : {}, { replace: true });
  }

  function resetFilters() {
    setFilterId(allFilterId);
    changeQuery('');
  }

  return (
    <>
      <section className={styles.hero}>
        <div>
          <p className={styles.eyebrow}>
            <span className={styles.eyebrowDot} aria-hidden="true" />
            Доставка по Томску
          </p>
          <h1 className={styles.title}>
            Голодны? <span className={styles.titleAccent}>Привезём</span> горячим
          </h1>
          <p className={styles.lead}>
            Рестораны и кафе города в одном месте. Соберите корзину — заказ сразу уйдёт на кухню.
          </p>
          <form
            className={styles.search}
            role="search"
            onSubmit={(event) => {
              event.preventDefault();
            }}
          >
            <SearchIcon size={22} className={styles.searchIcon} />
            <input
              type="search"
              className={styles.searchInput}
              placeholder="Ресторан или улица"
              aria-label="Поиск ресторана"
              value={query}
              onChange={(event) => {
                changeQuery(event.target.value);
              }}
            />
          </form>
        </div>
        <div className={styles.art} aria-hidden="true">
          <span className={cx(styles.artBlock, styles.artTomato)} />
          <span className={cx(styles.artBlock, styles.artLime)} />
          <span className={cx(styles.artBlock, styles.artInk)} />
          <span className={cx(styles.artBlock, styles.artSun)} />
          <span className={cx(styles.artBlock, styles.artRose)} />
        </div>
      </section>

      <div className={styles.filters}>
        <Chips
          label="Быстрые фильтры"
          options={quickFilters}
          activeId={filterId}
          onSelect={setFilterId}
        />
      </div>

      <section aria-labelledby="restaurants-title">
        <div className={styles.sectionHead}>
          <h2 id="restaurants-title" className={styles.sectionTitle}>
            Рестораны
          </h2>
          {restaurants.isSuccess && (
            <span className={styles.count}>
              {visible.length} {plural(visible.length, 'место', 'места', 'мест')}
            </span>
          )}
        </div>

        {restaurants.isPending && (
          <div className={styles.grid} aria-busy="true" aria-label="Загружаем рестораны">
            {skeletons.map((key) => (
              <div key={key} className={styles.skeleton} />
            ))}
          </div>
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

        {restaurants.isSuccess && visible.length === 0 && (
          <StateMessage
            title="Ничего не нашлось"
            text="Попробуйте другой запрос или сбросьте фильтры."
          >
            <Button variant="ghost" onClick={resetFilters}>
              Показать все
            </Button>
          </StateMessage>
        )}

        {visible.length > 0 && (
          <ul className={styles.grid}>
            {visible.map((restaurant) => (
              <li key={restaurant.id}>
                <RestaurantCard restaurant={restaurant} open={isOpenAt(restaurant, now)} />
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  );
}
