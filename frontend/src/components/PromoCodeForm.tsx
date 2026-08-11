import { useState, type FormEvent } from 'react';
import type { Cart } from '../api/cart';
import { useApplyPromo, useRemovePromo } from '../api/queries';
import { formatPrice } from '../lib/format';
import { orderErrorMessage, promoProblemMessage } from '../lib/orderErrors';
import { Button } from './Button';
import styles from './PromoCodeForm.module.css';

export function PromoCodeForm({ cart }: { cart: Cart }) {
  const [code, setCode] = useState('');
  const applyPromo = useApplyPromo();
  const removePromo = useRemovePromo();

  function submit(event: FormEvent) {
    event.preventDefault();
    const trimmed = code.trim();
    if (!trimmed) {
      return;
    }
    applyPromo.mutate(trimmed, {
      onSuccess: () => {
        setCode('');
      },
    });
  }

  if (cart.promoCode) {
    return (
      <div className={styles.applied}>
        <div>
          <p className={styles.code}>
            Промокод <strong>{cart.promoCode}</strong>
          </p>
          {cart.promoProblem ? (
            <p className={styles.problem} role="alert">
              {promoProblemMessage(cart.promoProblem)}
            </p>
          ) : (
            <p className={styles.saving}>Скидка {formatPrice(cart.discount)}</p>
          )}
        </div>
        <Button
          variant="ghost"
          size="small"
          disabled={removePromo.isPending}
          onClick={() => {
            removePromo.mutate(undefined);
          }}
        >
          Убрать
        </Button>
      </div>
    );
  }

  return (
    <form className={styles.form} onSubmit={submit}>
      <label className={styles.field}>
        <span className={styles.label}>Промокод</span>
        <input
          className={styles.input}
          value={code}
          maxLength={64}
          autoComplete="off"
          spellCheck={false}
          placeholder="Например, TOMSK10"
          aria-invalid={applyPromo.isError || undefined}
          onChange={(event) => {
            setCode(event.target.value.toUpperCase());
            applyPromo.reset();
          }}
        />
      </label>
      <Button type="submit" variant="dark" size="small" disabled={applyPromo.isPending || !code}>
        {applyPromo.isPending ? 'Проверяем…' : 'Применить'}
      </Button>
      {applyPromo.isError && (
        <p className={styles.problem} role="alert">
          {orderErrorMessage(applyPromo.error, () => undefined)}
        </p>
      )}
    </form>
  );
}
