import { useEffect } from 'react';
import { useLocation, useNavigate } from 'react-router';
import { useCreateOrder } from '../api/queries';
import { useAuth } from '../auth/AuthContext';
import { useCart } from '../cart/CartContext';
import { formatPrice } from '../lib/format';
import { orderErrorMessage } from '../lib/orderErrors';
import { Button } from './Button';
import styles from './CartDrawer.module.css';
import { BagIcon, CloseIcon } from './icons';
import { QuantityStepper } from './QuantityStepper';

export function CartDrawer() {
  const cart = useCart();
  const { user } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const createOrder = useCreateOrder();
  const { isOpen, close } = cart;
  const resetOrder = createOrder.reset;

  useEffect(() => {
    if (!isOpen) {
      return;
    }
    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        close();
      }
    }
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    document.addEventListener('keydown', closeOnEscape);
    return () => {
      document.body.style.overflow = previousOverflow;
      document.removeEventListener('keydown', closeOnEscape);
      resetOrder();
    };
  }, [isOpen, close, resetOrder]);

  if (!isOpen) {
    return null;
  }

  const restaurant = cart.restaurant;

  function placeOrder() {
    if (!restaurant) {
      return;
    }
    if (!user) {
      close();
      void navigate('/login', { state: { from: location.pathname } });
      return;
    }
    createOrder.mutate(
      {
        restaurantId: restaurant.id,
        orderItemsRequest: cart.lines.map((line) => ({
          menuItemId: line.menuItemId,
          quantity: line.quantity,
        })),
      },
      {
        onSuccess: (order) => {
          cart.clear();
          close();
          void navigate('/orders', { state: { placedOrderId: order.id } });
        },
      },
    );
  }

  return (
    <div className={styles.overlay} onClick={close}>
      <aside
        className={styles.panel}
        role="dialog"
        aria-modal="true"
        aria-labelledby="cart-title"
        onClick={(event) => {
          event.stopPropagation();
        }}
      >
        <div className={styles.head}>
          <div>
            <h2 id="cart-title" className={styles.title}>
              Корзина
            </h2>
            {restaurant && <p className={styles.restaurant}>{restaurant.name}</p>}
          </div>
          <button
            type="button"
            className={styles.close}
            onClick={close}
            aria-label="Закрыть корзину"
            autoFocus
          >
            <CloseIcon />
          </button>
        </div>

        <div className={styles.body}>
          {restaurant ? (
            <>
              <ul>
                {cart.lines.map((line) => (
                  <li key={line.menuItemId} className={styles.line}>
                    <div>
                      <p className={styles.lineName}>{line.name}</p>
                      <p className={styles.linePrice}>{formatPrice(line.price)} за порцию</p>
                    </div>
                    <div className={styles.lineSide}>
                      <span className={styles.lineTotal}>
                        {formatPrice(line.price * line.quantity)}
                      </span>
                      <QuantityStepper
                        name={line.name}
                        quantity={line.quantity}
                        onIncrement={() => {
                          cart.add(restaurant, line);
                        }}
                        onDecrement={() => {
                          cart.decrement(line.menuItemId);
                        }}
                      />
                    </div>
                  </li>
                ))}
              </ul>
              <button type="button" className={styles.clear} onClick={cart.clear}>
                Очистить корзину
              </button>
            </>
          ) : (
            <div className={styles.empty}>
              <span className={styles.emptyMark} aria-hidden="true">
                <BagIcon size={30} />
              </span>
              <h3 className={styles.emptyTitle}>Пока пусто</h3>
              <p className={styles.emptyText}>
                Добавьте блюда из меню ресторана — они появятся здесь.
              </p>
              <Button variant="dark" onClick={close}>
                Выбрать блюда
              </Button>
            </div>
          )}
        </div>

        {restaurant && (
          <div className={styles.foot}>
            <p className={styles.total}>
              <span>Итого</span>
              <span className={styles.totalValue}>{formatPrice(cart.total)}</span>
            </p>
            {createOrder.isError && (
              <p className={styles.error} role="alert">
                {orderErrorMessage(
                  createOrder.error,
                  (menuItemId) => cart.lines.find((line) => line.menuItemId === menuItemId)?.name,
                )}
              </p>
            )}
            {!user && <p className={styles.hint}>Чтобы оформить заказ, войдите в аккаунт.</p>}
            <Button block onClick={placeOrder} disabled={createOrder.isPending}>
              {user
                ? createOrder.isPending
                  ? 'Оформляем…'
                  : 'Оформить заказ'
                : 'Войти и оформить'}
            </Button>
          </div>
        )}
      </aside>
    </div>
  );
}
