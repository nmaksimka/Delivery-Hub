import { useState, type FormEvent } from 'react';
import { errorMessage } from '../../api/http';
import { useCouriers, useRegisterCourier } from '../../api/queries';
import { Button } from '../../components/Button';
import { Field } from '../../components/Field';
import { Pager } from '../../components/Pager';
import { StateMessage } from '../../components/StateMessage';
import styles from '../Management.module.css';

const statusLabels = {
  OFFLINE: 'Не на смене',
  AVAILABLE: 'Свободен',
  BUSY: 'Занят',
};

export function AdminCouriersPage() {
  const [page, setPage] = useState(0);
  const couriers = useCouriers(page);
  const register = useRegisterCourier();
  const [userId, setUserId] = useState('');
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const parsedUserId = Number(userId);
  const invalid =
    !Number.isInteger(parsedUserId) || parsedUserId <= 0 || !name.trim() || !phone.trim();

  function submit(event: FormEvent) {
    event.preventDefault();
    if (invalid) {
      return;
    }
    register.mutate(
      { userId: parsedUserId, name: name.trim(), phone: phone.trim() },
      {
        onSuccess: () => {
          setUserId('');
          setName('');
          setPhone('');
        },
      },
    );
  }

  return (
    <>
      <form className={styles.form} onSubmit={submit} aria-label="Новый курьер">
        <h2 className={styles.formTitle}>Добавить курьера</h2>
        <Field
          label="ID пользователя"
          inputMode="numeric"
          value={userId}
          onChange={(event) => {
            setUserId(event.target.value);
          }}
        />
        <Field
          label="Имя"
          value={name}
          maxLength={128}
          onChange={(event) => {
            setName(event.target.value);
          }}
        />
        <Field
          label="Телефон"
          type="tel"
          value={phone}
          maxLength={32}
          onChange={(event) => {
            setPhone(event.target.value);
          }}
        />
        {register.isError && (
          <p className={styles.error} role="alert">
            {errorMessage(register.error)}
          </p>
        )}
        <div className={styles.formActions}>
          <Button type="submit" disabled={invalid || register.isPending}>
            Добавить
          </Button>
        </div>
      </form>

      {couriers.isPending && (
        <div className={styles.skeleton} aria-busy="true" aria-label="Загружаем курьеров" />
      )}

      {couriers.isError && (
        <StateMessage
          role="alert"
          title="Не получилось загрузить курьеров"
          text={errorMessage(couriers.error)}
        >
          <Button
            variant="dark"
            onClick={() => {
              void couriers.refetch();
            }}
          >
            Повторить
          </Button>
        </StateMessage>
      )}

      {couriers.data?.items.length === 0 && (
        <StateMessage title="Курьеров пока нет" text="Добавьте первого курьера формой выше." />
      )}

      {couriers.data && couriers.data.items.length > 0 && (
        <>
          <ul className={styles.list}>
            {couriers.data.items.map((courier) => (
              <li key={courier.id} className={styles.row}>
                <div>
                  <p className={styles.rowTitle}>{courier.name}</p>
                  <p className={styles.rowMeta}>
                    {courier.phone} · рейтинг{' '}
                    {courier.rating === null || courier.ratingCount === 0
                      ? 'пока нет'
                      : courier.rating.toFixed(1)}
                  </p>
                </div>
                <span>{statusLabels[courier.status]}</span>
              </li>
            ))}
          </ul>
          <Pager
            label="Страницы курьеров"
            page={page}
            totalPages={couriers.data.totalPages}
            busy={couriers.isFetching}
            onChange={setPage}
          />
        </>
      )}
    </>
  );
}
