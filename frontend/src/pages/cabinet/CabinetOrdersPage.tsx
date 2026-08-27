import { useState } from 'react';
import { useParams } from 'react-router';
import { errorMessage } from '../../api/http';
import { useCabinetOrders } from '../../api/queries';
import { Button } from '../../components/Button';
import { Pager } from '../../components/Pager';
import { StateMessage } from '../../components/StateMessage';
import { formatDateTime, formatPrice } from '../../lib/format';
import { statusView } from '../../lib/orderStatus';
import styles from '../Management.module.css';

export function CabinetOrdersPage() {
  const restaurantId = Number(useParams().restaurantId);
  const [page, setPage] = useState(0);
  const orders = useCabinetOrders(restaurantId, page);

  return (
    <>
      {orders.isPending && (
        <div className={styles.skeleton} aria-busy="true" aria-label="Загружаем заказы" />
      )}

      {orders.isError && (
        <StateMessage
          role="alert"
          title="Не получилось загрузить заказы"
          text={errorMessage(orders.error)}
        >
          <Button
            variant="dark"
            onClick={() => {
              void orders.refetch();
            }}
          >
            Повторить
          </Button>
        </StateMessage>
      )}

      {orders.data?.items.length === 0 && (
        <StateMessage title="Заказов пока нет" text="Новые заказы появятся здесь." />
      )}

      {orders.data && orders.data.items.length > 0 && (
        <>
          <ul className={styles.list}>
            {orders.data.items.map((order) => (
              <li key={order.id} className={styles.row}>
                <div>
                  <p className={styles.rowTitle}>
                    Заказ № {order.id} · {statusView(order.status).label}
                  </p>
                  <p className={styles.rowMeta}>
                    {formatDateTime(order.createdAt)} ·{' '}
                    {order.orderItems
                      .map((item) => `${item.menuItemName} × ${item.quantity}`)
                      .join(', ')}
                  </p>
                </div>
                <strong>{formatPrice(order.totalAmount)}</strong>
              </li>
            ))}
          </ul>
          <Pager
            label="Страницы заказов"
            page={page}
            totalPages={orders.data.totalPages}
            busy={orders.isFetching}
            onChange={setPage}
          />
        </>
      )}
    </>
  );
}
