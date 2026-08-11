import { useState } from 'react';
import { maxAddresses, type Address } from '../api/addresses';
import { errorMessage } from '../api/http';
import {
  useAddresses,
  useDeleteAddress,
  useMakePrimaryAddress,
  useSaveAddress,
} from '../api/queries';
import { AddressForm } from '../components/AddressForm';
import { Button } from '../components/Button';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { StateMessage } from '../components/StateMessage';
import {
  addressErrorMessage,
  addressLine,
  emptyAddressInput,
  inputFromAddress,
} from '../lib/addressForm';
import styles from './AddressesPage.module.css';

type Editing = { id: number | null; address: Address | null };

export function AddressesPage() {
  const addresses = useAddresses();
  const save = useSaveAddress();
  const remove = useDeleteAddress();
  const makePrimary = useMakePrimaryAddress();
  const [editing, setEditing] = useState<Editing | null>(null);
  const [deleting, setDeleting] = useState<Address | null>(null);

  const list = addresses.data ?? [];
  const limitReached = list.length >= maxAddresses;

  function closeForm() {
    setEditing(null);
    save.reset();
  }

  return (
    <>
      <div className={styles.head}>
        <div>
          <h1 className={styles.title}>Мои адреса</h1>
          <p className={styles.lead}>
            До {maxAddresses} адресов в Томске. Основной подставится при оформлении заказа.
          </p>
        </div>
        {!editing && (
          <Button
            disabled={limitReached || !addresses.isSuccess}
            onClick={() => {
              setEditing({ id: null, address: null });
            }}
          >
            Добавить адрес
          </Button>
        )}
      </div>

      {editing && (
        <div className={styles.formWrap}>
          <AddressForm
            key={editing.id ?? 'new'}
            initial={editing.address ? inputFromAddress(editing.address) : emptyAddressInput}
            submitLabel={editing.id === null ? 'Сохранить адрес' : 'Сохранить изменения'}
            pending={save.isPending}
            error={save.isError ? addressErrorMessage(save.error) : null}
            onSubmit={(input) => {
              save.mutate({ id: editing.id, input }, { onSuccess: closeForm });
            }}
            onCancel={closeForm}
          />
        </div>
      )}

      {addresses.isPending && (
        <div className={styles.skeleton} aria-busy="true" aria-label="Загружаем адреса" />
      )}

      {addresses.isError && (
        <StateMessage
          role="alert"
          title="Не получилось загрузить адреса"
          text={errorMessage(addresses.error)}
        >
          <Button
            variant="dark"
            onClick={() => {
              void addresses.refetch();
            }}
          >
            Повторить
          </Button>
        </StateMessage>
      )}

      {addresses.isSuccess && list.length === 0 && !editing && (
        <StateMessage
          title="Адресов пока нет"
          text="Добавьте адрес, и его можно будет выбрать при оформлении заказа."
        />
      )}

      {(makePrimary.isError || remove.isError) && (
        <p className={styles.error} role="alert">
          {addressErrorMessage(makePrimary.error ?? remove.error)}
        </p>
      )}

      {list.length > 0 && (
        <ul className={styles.list}>
          {list.map((address) => (
            <li key={address.id} className={styles.item}>
              <div>
                <div className={styles.itemTop}>
                  <h2 className={styles.label}>{address.label}</h2>
                  {address.primary && <span className={styles.badge}>Основной</span>}
                </div>
                <p className={styles.line}>
                  {address.city}, {addressLine(address)}
                </p>
                {(address.entrance || address.floor) && (
                  <p className={styles.meta}>
                    {[
                      address.entrance && `подъезд ${address.entrance}`,
                      address.floor && `этаж ${address.floor}`,
                    ]
                      .filter(Boolean)
                      .join(', ')}
                  </p>
                )}
                {address.courierComment && <p className={styles.meta}>{address.courierComment}</p>}
              </div>
              <div className={styles.actions}>
                {!address.primary && (
                  <Button
                    variant="dark"
                    size="small"
                    disabled={makePrimary.isPending}
                    aria-label={`Сделать основным: ${address.label}`}
                    onClick={() => {
                      makePrimary.mutate(address.id);
                    }}
                  >
                    Сделать основным
                  </Button>
                )}
                <Button
                  variant="ghost"
                  size="small"
                  aria-label={`Изменить: ${address.label}`}
                  onClick={() => {
                    save.reset();
                    setEditing({ id: address.id, address });
                  }}
                >
                  Изменить
                </Button>
                <Button
                  variant="ghost"
                  size="small"
                  aria-label={`Удалить: ${address.label}`}
                  onClick={() => {
                    setDeleting(address);
                  }}
                >
                  Удалить
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}

      {deleting && (
        <ConfirmDialog
          title="Удалить адрес?"
          text={`«${deleting.label}» исчезнет из списка адресов.`}
          confirmLabel="Удалить"
          cancelLabel="Оставить"
          onCancel={() => {
            setDeleting(null);
          }}
          onConfirm={() => {
            remove.mutate(deleting.id);
            setDeleting(null);
          }}
        />
      )}
    </>
  );
}
