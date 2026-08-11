import { cx } from '../lib/cx';
import { orderProgress, type StepState } from '../lib/orderStatus';
import { CheckIcon } from './icons';
import styles from './OrderProgress.module.css';

const stateLabels: Record<StepState, string> = {
  done: 'готово',
  current: 'сейчас',
  pending: 'впереди',
  stopped: 'остановлено',
};

export function OrderProgress({ status }: { status: string }) {
  const steps = orderProgress(status);

  return (
    <ol className={styles.steps} aria-label="Ход заказа">
      {steps.map((step, index) => (
        <li
          key={step.label}
          className={cx(styles.step, styles[step.state])}
          aria-current={step.state === 'current' ? 'step' : undefined}
        >
          <span className={styles.mark} aria-hidden="true">
            {step.state === 'done' ? <CheckIcon size={16} /> : index + 1}
          </span>
          <span className={styles.label}>
            {step.label}
            <span className={styles.visuallyHidden}>, {stateLabels[step.state]}</span>
          </span>
        </li>
      ))}
    </ol>
  );
}
