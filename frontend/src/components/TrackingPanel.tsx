import { lazy, Suspense } from 'react';
import type { Order } from '../api/orders';
import type { Restaurant } from '../api/restaurants';
import { formatPrice } from '../lib/format';
import { arrivalText, formatDistance } from '../tracking/arrival';
import type { TrackingSnapshot } from '../tracking/useOrderTracking';
import styles from './TrackingPanel.module.css';

const CourierMap = lazy(() => import('./CourierMap'));

type TrackingPanelProps = {
  order: Order;
  restaurant: Restaurant | undefined;
  tracking: TrackingSnapshot;
};

function place(latitude: number | null | undefined, longitude: number | null | undefined) {
  return latitude === null ||
    latitude === undefined ||
    longitude === null ||
    longitude === undefined
    ? null
    : { latitude, longitude };
}

function fallbackText(order: Order) {
  if (order.etaMinutes !== null) {
    return `Живое положение пока недоступно. Ориентировочно доставим за ${order.etaMinutes} мин.`;
  }
  return 'Живое положение пока недоступно, статус заказа обновляется сам.';
}

export function TrackingPanel({ order, restaurant, tracking }: TrackingPanelProps) {
  const restaurantPlace = place(restaurant?.latitude, restaurant?.longitude);
  const destination = place(order.deliveryLatitude, order.deliveryLongitude);
  const courier = tracking.courier;
  const hasMap = restaurantPlace !== null || destination !== null || courier !== null;
  const arrival = tracking.latest ? arrivalText(tracking.latest) : null;
  const remaining = tracking.latest?.remainingMeters ?? null;
  const live = tracking.connection === 'live' && courier !== null;

  return (
    <section className={styles.panel} aria-label="Курьер в пути">
      <div className={styles.head}>
        <h2 className={styles.title}>Курьер в пути</h2>
        <span className={live ? styles.liveBadge : styles.idleBadge}>
          {live ? 'на связи' : 'нет сигнала'}
        </span>
      </div>

      {arrival ? <p className={styles.arrival}>{arrival}</p> : null}
      {!arrival && !live ? <p className={styles.fallback}>{fallbackText(order)}</p> : null}

      {(remaining !== null || order.weatherSurcharge > 0) && (
        <dl className={styles.facts}>
          {remaining !== null && (
            <div>
              <dt>Осталось ехать</dt>
              <dd>{formatDistance(remaining)}</dd>
            </div>
          )}
          {order.weatherSurcharge > 0 && (
            <div>
              <dt>Погодная надбавка</dt>
              <dd>{formatPrice(order.weatherSurcharge)}</dd>
            </div>
          )}
        </dl>
      )}

      {hasMap && (
        <div className={styles.map}>
          <Suspense fallback={<div className={styles.mapLoading} aria-busy="true" />}>
            <CourierMap restaurant={restaurantPlace} destination={destination} courier={courier} />
          </Suspense>
        </div>
      )}
    </section>
  );
}
