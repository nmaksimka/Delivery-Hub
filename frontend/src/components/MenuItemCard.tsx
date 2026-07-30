import type { MenuItem } from '../api/restaurants';
import { cx } from '../lib/cx';
import { formatPrice } from '../lib/format';
import { nutriScoreGrade, nutritionFacts } from '../lib/nutrition';
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
  const soldOut = !item.available;
  const facts = nutritionFacts(item.nutrition);
  const grade = nutriScoreGrade(item.nutrition);

  return (
    <article className={cx(styles.card, soldOut && styles.soldOut)} aria-disabled={soldOut}>
      <Cover title={item.name} seed={item.id} imageUrl={item.imageUrl} className={styles.cover} />
      <div className={styles.body}>
        <h3 className={styles.name}>{item.name}</h3>
        {item.description && <p className={styles.description}>{item.description}</p>}
        {(facts.length > 0 || grade) && (
          <dl className={styles.nutrition} aria-label="Пищевая ценность на 100 г">
            {facts.map((fact) => (
              <div key={fact.label} className={styles.fact}>
                <dt>{fact.label}</dt>
                <dd>{fact.value}</dd>
              </div>
            ))}
            {grade && (
              <div className={cx(styles.fact, styles.grade)} data-grade={grade}>
                <dt>Nutri-Score</dt>
                <dd>{grade}</dd>
              </div>
            )}
          </dl>
        )}
        <div className={styles.footer}>
          <span className={styles.price}>{formatPrice(item.price)}</span>
          {soldOut ? (
            <span className={styles.soldOutLabel}>Нет в наличии</span>
          ) : quantity > 0 ? (
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
