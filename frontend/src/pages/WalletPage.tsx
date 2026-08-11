import { useState, type FormEvent } from 'react';
import { errorMessage } from '../api/http';
import { useTopUp, useWallet } from '../api/queries';
import { maxTopUp, minTopUp } from '../api/wallet';
import { Button } from '../components/Button';
import { Chips } from '../components/Chips';
import { Field } from '../components/Field';
import { StateMessage } from '../components/StateMessage';
import { formatPrice } from '../lib/format';
import { topUpErrorMessage } from '../lib/walletErrors';
import styles from './WalletPage.module.css';

const presets = [500, 1000, 3000, 5000];

function parseAmount(value: string) {
  const amount = Number(value.replace(',', '.').replace(/\s/g, ''));
  return Number.isFinite(amount) ? Math.round(amount * 100) / 100 : NaN;
}

export function WalletPage() {
  const wallet = useWallet();
  const topUp = useTopUp();
  const [amount, setAmount] = useState('1000');
  const [toppedUp, setToppedUp] = useState<number | null>(null);
  const parsed = parseAmount(amount);
  const invalid = !(parsed >= minTopUp && parsed <= maxTopUp);

  function submit(event: FormEvent) {
    event.preventDefault();
    if (invalid) {
      return;
    }
    setToppedUp(null);
    topUp.mutate(parsed, {
      onSuccess: () => {
        setToppedUp(parsed);
      },
    });
  }

  return (
    <>
      <div className={styles.head}>
        <h1 className={styles.title}>Кошелёк</h1>
        <p className={styles.lead}>
          Демо-счёт для оплаты заказов. При оформлении сумма резервируется, а списывается, когда
          ресторан подтвердит заказ.
        </p>
      </div>

      {wallet.isPending && (
        <div className={styles.skeleton} aria-busy="true" aria-label="Загружаем кошелёк" />
      )}

      {wallet.isError && (
        <StateMessage
          role="alert"
          title="Не получилось загрузить кошелёк"
          text={errorMessage(wallet.error)}
        >
          <Button
            variant="dark"
            onClick={() => {
              void wallet.refetch();
            }}
          >
            Повторить
          </Button>
        </StateMessage>
      )}

      {wallet.isSuccess && (
        <div className={styles.layout}>
          <section className={styles.balance} aria-label="Баланс">
            <p className={styles.caption}>Доступно</p>
            <p className={styles.amount}>{formatPrice(wallet.data.available)}</p>
            <p className={styles.held}>
              В резерве под заказы: <strong>{formatPrice(wallet.data.held)}</strong>
            </p>
          </section>

          <form className={styles.form} onSubmit={submit} noValidate>
            <h2 className={styles.formTitle}>Пополнить</h2>
            <Chips
              label="Быстрые суммы"
              options={presets.map((preset) => ({
                id: String(preset),
                label: formatPrice(preset),
              }))}
              activeId={presets.includes(parsed) ? String(parsed) : null}
              onSelect={(id) => {
                setAmount(id);
                topUp.reset();
              }}
            />
            <Field
              label="Сумма, ₽"
              inputMode="decimal"
              value={amount}
              error={
                amount && invalid
                  ? `От ${formatPrice(minTopUp)} до ${formatPrice(maxTopUp)}`
                  : undefined
              }
              onChange={(event) => {
                setAmount(event.target.value);
                topUp.reset();
              }}
            />
            {topUp.isError && (
              <p className={styles.error} role="alert">
                {topUpErrorMessage(topUp.error)}
              </p>
            )}
            {toppedUp !== null && (
              <p className={styles.success} role="status">
                Зачислено {formatPrice(toppedUp)}
              </p>
            )}
            <Button type="submit" disabled={invalid || topUp.isPending}>
              {topUp.isPending ? 'Пополняем…' : 'Пополнить'}
            </Button>
          </form>
        </div>
      )}
    </>
  );
}
