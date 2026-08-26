import { useState } from 'react';
import { Link } from 'react-router';
import { errorMessage } from '../api/http';
import type { Order } from '../api/orders';
import { useMyOrders, useRestaurants } from '../api/queries';
import { Button } from '../components/Button';
import { buttonClass } from '../components/buttonClass';
import { OrderReview } from '../components/OrderReview';
import { RepeatOrderButton } from '../components/RepeatOrderButton';
import { StateMessage } from '../components/StateMessage';
import { cx } from '../lib/cx';
import { formatDateTime, formatPrice } from '../lib/format';
import { cancellationText, isFinalStatus, statusView } from '../lib/orderStatus';
import styles from './OrdersPage.module.css';

const skeletons = [1, 2, 3];

function OrderRow({ order, restaurantName }: { order: Order; restaurantName: string | null }) {
  const status = statusView(order.status);
  const details = [restaurantName, formatDateTime(order.createdAt)].filter(Boolean).join(' · ');

  return (
    <li className={styles.order}>
      <div>
        <div className={styles.orderTop}>
          <h2 className={styles.number}>
            <Link to={`/orders/${order.id}`} className={styles.numberLink}>
              Заказ №{order.id}
            </Link>
          </h2>
          <span className={cx(styles.status, styles[status.tone])}>{status.label}</span>
        </div>
        {details && <p className={styles.meta}>{details}</p>}
        {order.status === 'CANCELLED' && (
          <p className={styles.reason}>
            {cancellationText(order.cancellationReason)}
            {order.refundedAt && ' · деньги возвращены'}
          </p>
        )}
        {order.orderItems.length > 0 && (
          <p className={styles.items}>
            {order.orderItems.map((item) => `${item.menuItemName} × ${item.quantity}`).join(', ')}
          </p>
        )}
      </div>
      <div className={styles.side}>
        <p className={styles.total}>{formatPrice(order.totalAmount)}</p>
        {isFinalStatus(order.status) ? (
          <RepeatOrderButton order={order} />
        ) : (
          <Link to={`/orders/${order.id}`} className={buttonClass({ size: 'small' })}>
            Следить
          </Link>
        )}
      </div>
      <OrderReview order={order} />
    </li>
  );
}

export function OrdersPage() {
  const [page, setPage] = useState(0);
  const orders = useMyOrders(page);
  const restaurants = useRestaurants(false);

  const restaurantNames = new Map(
    (restaurants.data ?? []).map((restaurant) => [restaurant.id, restaurant.name]),
  );

  return (
    <>
      <div className={styles.head}>
        <h1 className={styles.title}>Мои заказы</h1>
      </div>

      {orders.isPending && (
        <div aria-busy="true" aria-label="Загружаем заказы">
          {skeletons.map((key) => (
            <div key={key} className={styles.skeleton} />
          ))}
        </div>
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

      {orders.isSuccess && orders.data.items.length === 0 && (
        <StateMessage
          title="Заказов пока нет"
          text="Выберите ресторан и соберите первую корзину — история появится здесь."
        >
          <Link to="/" className={buttonClass()}>
            К ресторанам
          </Link>
        </StateMessage>
      )}

      {orders.isSuccess && orders.data.items.length > 0 && (
        <>
          <ol className={styles.list}>
            {orders.data.items.map((order) => (
              <OrderRow
                key={order.id}
                order={order}
                restaurantName={restaurantNames.get(order.restaurantId) ?? null}
              />
            ))}
          </ol>
          {orders.data.totalPages > 1 && (
            <nav className={styles.pager} aria-label="Страницы заказов">
              <Button
                variant="ghost"
                size="small"
                disabled={page === 0 || orders.isFetching}
                onClick={() => {
                  setPage(page - 1);
                }}
              >
                Назад
              </Button>
              <span className={styles.pageInfo}>
                {page + 1} из {orders.data.totalPages}
              </span>
              <Button
                variant="ghost"
                size="small"
                disabled={page + 1 >= orders.data.totalPages || orders.isFetching}
                onClick={() => {
                  setPage(page + 1);
                }}
              >
                Вперёд
              </Button>
            </nav>
          )}
        </>
      )}
    </>
  );
}
