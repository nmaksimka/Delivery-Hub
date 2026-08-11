import { maxQuantity } from '../api/cart';
import { MinusIcon, PlusIcon } from './icons';
import styles from './QuantityStepper.module.css';

type QuantityStepperProps = {
  name: string;
  quantity: number;
  onIncrement: () => void;
  onDecrement: () => void;
};

export function QuantityStepper({
  name,
  quantity,
  onIncrement,
  onDecrement,
}: QuantityStepperProps) {
  return (
    <div className={styles.stepper} role="group" aria-label={`Количество: ${name}`}>
      <button
        type="button"
        className={styles.button}
        onClick={onDecrement}
        aria-label={`Убрать одну порцию: ${name}`}
      >
        <MinusIcon size={18} />
      </button>
      <span className={styles.value} aria-live="polite">
        {quantity}
      </span>
      <button
        type="button"
        className={styles.button}
        onClick={onIncrement}
        disabled={quantity >= maxQuantity}
        aria-label={`Добавить ещё одну порцию: ${name}`}
      >
        <PlusIcon size={18} />
      </button>
    </div>
  );
}
