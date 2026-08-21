import { useEffect, useState, type ReactNode } from 'react';
import { useLocation, useNavigate } from 'react-router';
import { ApiError } from '../api/http';
import type { CartLine } from '../api/cart';
import {
  useAddresses,
  useChangeQuantity,
  useCheckout,
  useClearCart,
  useDeliveryQuote,
  useRemoveFromCart,
  useTopUp,
  useWallet,
} from '../api/queries';
import { useAuth } from '../auth/AuthContext';
import { useCart } from '../cart/CartContext';
import { cx } from '../lib/cx';
import { weatherNote } from '../lib/deliveryQuote';
import { formatPrice } from '../lib/format';
import { orderErrorMessage } from '../lib/orderErrors';
import { shortageOf, topUpErrorMessage } from '../lib/walletErrors';
import { AddressPicker } from './AddressPicker';
import { Button } from './Button';
import styles from './CartDrawer.module.css';
import { BagIcon, CloseIcon } from './icons';
import { PromoCodeForm } from './PromoCodeForm';
import { QuantityStepper } from './QuantityStepper';

function lineName(line: CartLine) {
  return line.name ?? 'Блюдо убрали из меню';
}

function EmptyState({
  title,
  text,
  children,
}: {
  title: string;
  text: string;
  children: ReactNode;
}) {
  return (
    <div className={styles.empty}>
      <span className={styles.emptyMark} aria-hidden="true">
        <BagIcon size={30} />
      </span>
      <h3 className={styles.emptyTitle}>{title}</h3>
      <p className={styles.emptyText}>{text}</p>
      {children}
    </div>
  );
}

export function CartDrawer() {
  const { cart, isLoading, isOpen, close } = useCart();
  const { user } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const changeQuantity = useChangeQuantity();
  const removeLine = useRemoveFromCart();
  const clearCart = useClearCart();
  const checkout = useCheckout();
  const wallet = useWallet();
  const topUp = useTopUp();
  const resetCheckout = checkout.reset;
  const [addressId, setAddressId] = useState<number | null>(null);
  const addresses = useAddresses();
  const chosenAddressId =
    addressId ?? addresses.data?.find((address) => address.primary)?.id ?? null;
  const restaurantId = cart.items.length > 0 ? (cart.restaurant?.id ?? null) : null;
  const delivery = useDeliveryQuote(restaurantId, chosenAddressId);

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
      resetCheckout();
    };
  }, [isOpen, close, resetCheckout]);

  if (!isOpen) {
    return null;
  }

  const restaurant = cart.items.length > 0 ? cart.restaurant : null;
  const unavailable = cart.items.filter((line) => !line.available);
  const deliveryFee = delivery.data?.deliveryFee ?? 0;
  const total = cart.total + deliveryFee;
  const shortage = wallet.data ? shortageOf(total, wallet.data.available) : 0;
  const weather = delivery.data ? weatherNote(delivery.data) : null;
  const feeChanged =
    checkout.error instanceof ApiError && checkout.error.code === 'DELIVERY_FEE_CHANGED';
  const writeError = changeQuantity.error ?? removeLine.error ?? clearCart.error;
  const itemName = (menuItemId: number) =>
    cart.items.find((line) => line.menuItemId === menuItemId)?.name ?? undefined;

  function goToLogin() {
    close();
    void navigate('/login', { state: { from: location.pathname } });
  }

  function placeOrder() {
    if (chosenAddressId === null) {
      return;
    }
    checkout.mutate(
      { cart, addressId: chosenAddressId, expectedDeliveryFee: delivery.data?.deliveryFee },
      {
        onSuccess: (order) => {
          close();
          void navigate(`/orders/${order.id}`);
        },
        onError: (error) => {
          if (error instanceof ApiError && error.code === 'DELIVERY_FEE_CHANGED') {
            void delivery.refetch();
          }
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
          {!user && (
            <EmptyState
              title="Войдите, чтобы собрать корзину"
              text="Корзина хранится в аккаунте: блюда не потеряются, а заказ можно оформить с любого устройства."
            >
              <Button variant="dark" onClick={goToLogin}>
                Войти
              </Button>
            </EmptyState>
          )}

          {user && isLoading && <p className={styles.loading}>Загружаем корзину…</p>}

          {user && !isLoading && !restaurant && (
            <EmptyState
              title="Пока пусто"
              text="Добавьте блюда из меню ресторана — они появятся здесь."
            >
              <Button variant="dark" onClick={close}>
                Выбрать блюда
              </Button>
            </EmptyState>
          )}

          {user && restaurant && (
            <>
              {!restaurant.active && (
                <p className={styles.warning}>Ресторан временно не принимает заказы.</p>
              )}
              {restaurant.active && restaurant.closed && (
                <p className={styles.warning}>
                  Ресторан сейчас закрыт
                  {restaurant.workingHours ? `, часы работы ${restaurant.workingHours}` : ''}.
                </p>
              )}
              {unavailable.length > 0 && (
                <p className={styles.warning}>
                  Некоторых блюд нет в наличии — уберите их, чтобы оформить заказ.
                </p>
              )}
              <ul>
                {cart.items.map((line) => (
                  <li
                    key={line.menuItemId}
                    className={cx(styles.line, !line.available && styles.lineOff)}
                  >
                    <div>
                      <p className={styles.lineName}>{lineName(line)}</p>
                      {line.available && line.price !== null ? (
                        <p className={styles.linePrice}>{formatPrice(line.price)} за порцию</p>
                      ) : (
                        <p className={styles.lineMissing}>Нет в наличии</p>
                      )}
                    </div>
                    <div className={styles.lineSide}>
                      {line.available ? (
                        <>
                          {line.lineTotal !== null && (
                            <span className={styles.lineTotal}>{formatPrice(line.lineTotal)}</span>
                          )}
                          <QuantityStepper
                            name={lineName(line)}
                            quantity={line.quantity}
                            onIncrement={() => {
                              changeQuantity.mutate({
                                menuItemId: line.menuItemId,
                                quantity: line.quantity + 1,
                              });
                            }}
                            onDecrement={() => {
                              changeQuantity.mutate({
                                menuItemId: line.menuItemId,
                                quantity: line.quantity - 1,
                              });
                            }}
                          />
                        </>
                      ) : (
                        <Button
                          variant="ghost"
                          size="small"
                          onClick={() => {
                            removeLine.mutate(line.menuItemId);
                          }}
                        >
                          Убрать
                        </Button>
                      )}
                    </div>
                  </li>
                ))}
              </ul>
              <button
                type="button"
                className={styles.clear}
                onClick={() => {
                  clearCart.mutate(undefined);
                }}
              >
                Очистить корзину
              </button>
              <div className={styles.promo}>
                <PromoCodeForm cart={cart} />
              </div>
              <AddressPicker
                selectedId={chosenAddressId}
                onSelect={setAddressId}
                onNavigate={close}
              />
            </>
          )}
        </div>

        {user && restaurant && (
          <div className={styles.foot}>
            {cart.discount > 0 && (
              <>
                <p className={styles.row}>
                  <span>Блюда</span>
                  <span>{formatPrice(cart.subtotal)}</span>
                </p>
                <p className={styles.row}>
                  <span>Скидка</span>
                  <span className={styles.discount}>−{formatPrice(cart.discount)}</span>
                </p>
              </>
            )}
            {delivery.data && (
              <>
                <p className={styles.row}>
                  <span>Доставка, ≈ {delivery.data.etaMinutes} мин</span>
                  <span>{formatPrice(delivery.data.deliveryFee)}</span>
                </p>
                {weather && <p className={styles.weather}>{weather}</p>}
              </>
            )}
            {delivery.isError && (
              <p className={styles.error} role="alert">
                {orderErrorMessage(delivery.error, itemName)}
              </p>
            )}
            <p className={styles.total}>
              <span>Итого</span>
              <span className={styles.totalValue}>{formatPrice(total)}</span>
            </p>
            {writeError && (
              <p className={styles.error} role="alert">
                {orderErrorMessage(writeError, itemName)}
              </p>
            )}
            {checkout.isError && (
              <p className={styles.error} role="alert">
                {orderErrorMessage(checkout.error, itemName)}
              </p>
            )}
            {wallet.data && shortage > 0 && (
              <div className={styles.shortage}>
                <p>
                  На кошельке {formatPrice(wallet.data.available)} — не хватает{' '}
                  <strong>{formatPrice(shortage)}</strong>.
                </p>
                <Button
                  variant="dark"
                  size="small"
                  disabled={topUp.isPending}
                  onClick={() => {
                    topUp.mutate(shortage);
                  }}
                >
                  {topUp.isPending ? 'Пополняем…' : `Пополнить на ${formatPrice(shortage)}`}
                </Button>
                {topUp.isError && (
                  <p className={styles.error} role="alert">
                    {topUpErrorMessage(topUp.error)}
                  </p>
                )}
              </div>
            )}
            <Button
              block
              onClick={placeOrder}
              disabled={
                !cart.orderable ||
                shortage > 0 ||
                checkout.isPending ||
                chosenAddressId === null ||
                delivery.isError
              }
            >
              {checkout.isPending
                ? 'Оформляем…'
                : feeChanged
                  ? `Подтвердить за ${formatPrice(total)}`
                  : 'Оформить заказ'}
            </Button>
          </div>
        )}
      </aside>
    </div>
  );
}
