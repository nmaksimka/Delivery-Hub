import { useState, type FormEvent } from 'react';
import type { AddressInput } from '../api/addresses';
import { normalizedAddress, validateAddress, type AddressErrors } from '../lib/addressForm';
import { Button } from './Button';
import styles from './AddressForm.module.css';
import { Field } from './Field';

type AddressFormProps = {
  initial: AddressInput;
  submitLabel: string;
  pending: boolean;
  error: string | null;
  onSubmit: (input: AddressInput) => void;
  onCancel: () => void;
};

export function AddressForm({
  initial,
  submitLabel,
  pending,
  error,
  onSubmit,
  onCancel,
}: AddressFormProps) {
  const [values, setValues] = useState(initial);
  const [errors, setErrors] = useState<AddressErrors>({});

  function bind(field: keyof AddressInput) {
    return {
      value: values[field],
      error: errors[field],
      onChange: (event: { target: { value: string } }) => {
        setValues((current) => ({ ...current, [field]: event.target.value }));
        setErrors((current) => ({ ...current, [field]: undefined }));
      },
    };
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    const found = validateAddress(values);
    setErrors(found);
    if (Object.keys(found).length === 0) {
      onSubmit(normalizedAddress(values));
    }
  }

  return (
    <form className={styles.form} onSubmit={submit} noValidate aria-label="Адрес доставки">
      <Field label="Название" placeholder="Дом, Работа…" {...bind('label')} />
      <Field label="Город" readOnly hint="Пока доставляем только по Томску" {...bind('city')} />
      <div className={styles.pair}>
        <Field label="Улица" autoComplete="address-line1" {...bind('street')} />
        <Field label="Дом" {...bind('house')} />
      </div>
      <div className={styles.triple}>
        <Field label="Квартира" {...bind('apartment')} />
        <Field label="Подъезд" {...bind('entrance')} />
        <Field label="Этаж" {...bind('floor')} />
      </div>
      <Field label="Комментарий курьеру" {...bind('courierComment')} />
      {error && (
        <p className={styles.error} role="alert">
          {error}
        </p>
      )}
      <div className={styles.actions}>
        <Button type="submit" disabled={pending}>
          {pending ? 'Сохраняем…' : submitLabel}
        </Button>
        <Button variant="ghost" onClick={onCancel}>
          Отмена
        </Button>
      </div>
    </form>
  );
}
