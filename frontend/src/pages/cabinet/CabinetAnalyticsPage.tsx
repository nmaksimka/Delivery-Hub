import { useMemo, useState } from 'react';
import { useParams } from 'react-router';
import { errorMessage } from '../../api/http';
import { useCabinetAnalytics } from '../../api/queries';
import { AnalyticsDashboard } from '../../components/AnalyticsDashboard';
import { Button } from '../../components/Button';
import { PeriodChips } from '../../components/PeriodChips';
import { StateMessage } from '../../components/StateMessage';
import { lastDays } from '../../lib/analyticsPeriod';
import styles from '../Management.module.css';

export function CabinetAnalyticsPage() {
  const restaurantId = Number(useParams().restaurantId);
  const [days, setDays] = useState(30);
  const period = useMemo(() => lastDays(days), [days]);
  const analytics = useCabinetAnalytics(restaurantId, period);

  return (
    <>
      <div className={styles.periodRow}>
        <PeriodChips days={days} onSelect={setDays} />
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
