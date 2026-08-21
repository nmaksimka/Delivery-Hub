import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';
import { ApiError, errorMessage } from '../api/http';
import { queryKeys, useCancelOrder, useOrder, useRestaurant } from '../api/queries';
import { Button } from '../components/Button';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { ArrowLeftIcon } from '../components/icons';
import { OrderProgress } from '../components/OrderProgress';
import { RepeatOrderButton } from '../components/RepeatOrderButton';
import { StateMessage } from '../components/StateMessage';
import { TrackingPanel } from '../components/TrackingPanel';
import { cx } from '../lib/cx';
import { formatDateTime, formatPrice } from '../lib/format';
import { orderErrorMessage } from '../lib/orderErrors';
import { canCancel, cancellationText, isFinalStatus, statusView } from '../lib/orderStatus';
import { isTrackedStatus } from '../tracking/trackedStatus';
import { useOrderTracking } from '../tracking/useOrderTracking';
import { NotFoundPage } from './NotFoundPage';
import styles from './OrderPage.module.css';

export function OrderPage() {
  const params = useParams();
  const orderId = Number(params.orderId);
  const order = useOrder(orderId);
  const restaurant = useRestaurant(order.data?.restaurantId ?? NaN);
  const cancel = useCancelOrder();
  const queryClient = useQueryClient();
  const [confirming, setConfirming] = useState(false);
  const finished = order.data ? isFinalStatus(order.data.status) : false;
  const tracked = order.data ? isTrackedStatus(order.data.status) : false;
  const tracking = useOrderTracking(orderId, tracked);

  useEffect(() => {
    if (finished) {
      void queryClient.invalidateQueries({ queryKey: queryKeys.wallet });
    }
  }, [finished, queryClient]);

  const missing =
    !Number.isInteger(orderId) ||
    (order.error instanceof ApiError && [403, 404].includes(order.error.status));
  if (missing) {
    return <NotFoundPage />;
  }

  if (order.isPending) {
    return <div className={styles.skeleton} aria-busy="true" aria-label="Загружаем заказ" />;
  }

  if (order.isError) {
    return (
      <StateMessage
        role="alert"
        title="Не получилось открыть заказ"
        text={errorMessage(order.error)}
      >
        <Button
          variant="dark"
          onClick={() => {
            void order.refetch();
          }}
        >
          Повторить
        </Button>
      </StateMessage>
    );
  }

  const current = order.data;
  const status = statusView(current.status);
  const cancelled = current.status === 'CANCELLED';
  const subtotal = current.totalAmount + current.discount;
  const details = [restaurant.data?.name, formatDateTime(current.createdAt)]
    .filter(Boolean)
    .join(' · ');

  return (
    <>
      <Link to="/orders" className={styles.back}>
        <ArrowLeftIcon size={18} />
        Все заказы
      </Link>

      <header className={styles.head}>
        <div>
          <h1 className={styles.title}>Заказ №{current.id}</h1>
          {details && <p className={styles.meta}>{details}</p>}
        </div>
        <span className={cx(styles.status, styles[status.tone])} role="status">
          {status.label}
        </span>
      </header>

      <section className={styles.progress}>
        <OrderProgress status={current.status} />
        {!finished && (
          <p className={styles.live} aria-live="polite">
            Статус обновляется сам — страницу можно не перезагружать.
          </p>
        )}
      </section>

      {tracked && (
        <TrackingPanel order={current} restaurant={restaurant.data} tracking={tracking} />
      )}

      {cancelled && (
        <div className={styles.cancelBox}>
          <p className={styles.reason}>{cancellationText(current.cancellationReason)}</p>
          {current.refundedAt ? (
            <p className={styles.refund}>
              Деньги возвращены на кошелёк · {formatDateTime(current.refundedAt)}
            </p>
          ) : (
            <p className={styles.refundNote}>
              Если сумма была зарезервирована, резерв снимется автоматически.
            </p>
          )}
        </div>
      )}

      <section className={styles.body}>
        <h2 className={styles.sectionTitle}>Состав</h2>
        <ul className={styles.items}>
          {current.orderItems.map((item) => (
            <li key={item.id} className={styles.item}>
              <span>
                {item.menuItemName} <span className={styles.quantity}>× {item.quantity}</span>
              </span>
              <span className={styles.price}>{formatPrice(item.pricePerUnit * item.quantity)}</span>
            </li>
          ))}
        </ul>
        {current.discount > 0 && (
          <>
            <p className={styles.row}>
              <span>Блюда</span>
              <span>{formatPrice(subtotal)}</span>
            </p>
            <p className={styles.row}>
              <span>Промокод {current.promoCode}</span>
              <span className={styles.discount}>−{formatPrice(current.discount)}</span>
            </p>
          </>
        )}
        <p className={styles.total}>
          <span>Итого</span>
          <span className={styles.totalValue}>{formatPrice(current.totalAmount)}</span>
        </p>
      </section>

      <div className={styles.actions}>
        {canCancel(current.status) && (
          <Button
            variant="ghost"
            disabled={cancel.isPending}
            onClick={() => {
              setConfirming(true);
            }}
          >
            {cancel.isPending ? 'Отменяем…' : 'Отменить заказ'}
          </Button>
        )}
        {finished && <RepeatOrderButton order={current} />}
      </div>
      {cancel.isError && (
        <p className={styles.error} role="alert">
          {orderErrorMessage(cancel.error, () => undefined)}
        </p>
      )}

      {confirming && (
        <ConfirmDialog
          title="Отменить заказ?"
          text="Ресторан перестанет его готовить, а зарезервированные деньги вернутся на кошелёк."
          confirmLabel="Отменить заказ"
          cancelLabel="Не отменять"
          onCancel={() => {
            setConfirming(false);
          }}
          onConfirm={() => {
            setConfirming(false);
            cancel.mutate(current.id);
          }}
        />
      )}
    </>
  );
}
