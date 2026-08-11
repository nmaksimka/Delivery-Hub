import { useState } from 'react';
import type { Order } from '../api/orders';
import { useRepeatOrder } from '../api/queries';
import { useCart } from '../cart/CartContext';
import { orderErrorMessage } from '../lib/orderErrors';
import { Button } from './Button';
import { ConfirmDialog } from './ConfirmDialog';
import styles from './RepeatOrderButton.module.css';

export function RepeatOrderButton({ order }: { order: Order }) {
  const cart = useCart();
  const repeat = useRepeatOrder();
  const [confirming, setConfirming] = useState(false);
  const itemName = (menuItemId: number) =>
    order.orderItems.find((item) => item.menuItemId === menuItemId)?.menuItemName;

  function run() {
    setConfirming(false);
    repeat.mutate(order, {
      onSuccess: () => {
        cart.open();
      },
    });
  }

  return (
    <div className={styles.box}>
      <Button
        variant="ghost"
        size="small"
        disabled={repeat.isPending || order.orderItems.length === 0}
        onClick={() => {
          if (cart.count > 0) {
            setConfirming(true);
          } else {
            run();
          }
        }}
      >
        {repeat.isPending ? 'Собираем корзину…' : 'Повторить заказ'}
      </Button>
      {repeat.isSuccess && repeat.data.skipped.length > 0 && (
        <p className={styles.note} role="status">
          Не добавили: {repeat.data.skipped.join(', ')} — этих блюд больше нет в меню.
        </p>
      )}
      {repeat.isError && (
        <p className={styles.error} role="alert">
          {orderErrorMessage(repeat.error, itemName)}
        </p>
      )}
      {confirming && (
        <ConfirmDialog
          title="Заменить корзину?"
          text={`Сейчас в корзине другие блюда. Вместо них положим всё из заказа №${order.id}.`}
          confirmLabel="Заменить"
          cancelLabel="Оставить как есть"
          onCancel={() => {
            setConfirming(false);
          }}
          onConfirm={run}
        />
      )}
    </div>
  );
}
