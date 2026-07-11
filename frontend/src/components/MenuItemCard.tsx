import type { MenuItem } from '../api/restaurants';
import { formatPrice } from '../lib/format';
import { Cover } from './Cover';
import { PlusIcon } from './icons';
import styles from './MenuItemCard.module.css';
import { QuantityStepper } from './QuantityStepper';

type MenuItemCardProps = {
  item: MenuItem;
  quantity: number;
  orderable: boolean;
  onAdd: () => void;
  onDecrement: () => void;
};

export function MenuItemCard({ item, quantity, orderable, onAdd, onDecrement }: MenuItemCardProps) {
  return (
    <article className={styles.card}>
      <Cover title={item.name} seed={item.id} className={styles.cover} />
      <div className={styles.body}>
        <h3 className={styles.name}>{item.name}</h3>
        {item.description && <p className={styles.description}>{item.description}</p>}
        <div className={styles.footer}>
          <span className={styles.price}>{formatPrice(item.price)}</span>
          {quantity > 0 ? (
            <QuantityStepper
              name={item.name}
              quantity={quantity}
              onIncrement={onAdd}
              onDecrement={onDecrement}
            />
          ) : (
            <button
              type="button"
              className={styles.add}
              onClick={onAdd}
              disabled={!orderable}
              aria-label={`Добавить в корзину: ${item.name}`}
            >
              <PlusIcon />
            </button>
          )}
        </div>
      </div>
    </article>
  );
}
