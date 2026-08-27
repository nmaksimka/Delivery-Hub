import { useMemo, useState } from 'react';
import { errorMessage } from '../api/http';
import { useAdminAnalytics, useRestaurants } from '../api/queries';
import { AnalyticsDashboard } from '../components/AnalyticsDashboard';
import { Button } from '../components/Button';
import { PeriodChips } from '../components/PeriodChips';
import { StateMessage } from '../components/StateMessage';
import { lastDays } from '../lib/analyticsPeriod';
import styles from './AnalyticsPage.module.css';

export function AdminAnalyticsPage() {
  const [days, setDays] = useState(30);
  const [restaurantId, setRestaurantId] = useState<number | undefined>(undefined);
  const period = useMemo(() => lastDays(days), [days]);
  const restaurants = useRestaurants(false);
  const analytics = useAdminAnalytics(period, restaurantId);

  return (
    <>
      <div className={styles.head}>
        <div className={styles.controls}>
          <PeriodChips days={days} onSelect={setDays} />
          <label className={styles.select}>
            <span>Ресторан</span>
            <select
              value={restaurantId ?? ''}
              onChange={(event) => {
                setRestaurantId(event.target.value ? Number(event.target.value) : undefined);
              }}
            >
              <option value="">Все рестораны</option>
              {restaurants.data?.map((restaurant) => (
                <option key={restaurant.id} value={restaurant.id}>
                  {restaurant.name}
                </option>
              ))}
            </select>
          </label>
        </div>
      </div>

      {analytics.isPending && (
        <div className={styles.skeleton} aria-busy="true" aria-label="Загружаем аналитику" />
      )}

      {analytics.isError && (
        <StateMessage
          role="alert"
          title="Не получилось загрузить аналитику"
          text={errorMessage(analytics.error)}
        >
          <Button
            variant="dark"
            onClick={() => {
              void analytics.refetch();
            }}
          >
            Повторить
          </Button>
        </StateMessage>
      )}

      {analytics.data && <AnalyticsDashboard report={analytics.data} />}
    </>
  );
}
