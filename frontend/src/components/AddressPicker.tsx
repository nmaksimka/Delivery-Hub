import { Link } from 'react-router';
import { useAddresses } from '../api/queries';
import { addressLine } from '../lib/addressForm';
import styles from './AddressPicker.module.css';

type AddressPickerProps = {
  selectedId: number | null;
  onSelect: (id: number) => void;
  onNavigate: () => void;
};

export function AddressPicker({ selectedId, onSelect, onNavigate }: AddressPickerProps) {
  const addresses = useAddresses();

  if (!addresses.isSuccess) {
    return null;
  }

  const list = addresses.data;
  const effectiveId = selectedId ?? list.find((address) => address.primary)?.id ?? null;

  if (list.length === 0) {
    return (
      <section className={styles.box} aria-label="Адрес доставки">
        <h3 className={styles.title}>Адрес доставки</h3>
        <p className={styles.hint}>Адресов пока нет.</p>
        <Link to="/addresses" className={styles.link} onClick={onNavigate}>
          Добавить адрес
        </Link>
      </section>
    );
  }

  return (
    <fieldset className={styles.box}>
      <legend className={styles.title}>Адрес доставки</legend>
      {list.map((address) => (
        <label key={address.id} className={styles.option}>
          <input
            type="radio"
            name="delivery-address"
            className={styles.radio}
            checked={address.id === effectiveId}
            onChange={() => {
              onSelect(address.id);
            }}
          />
          <span>
            <span className={styles.label}>{address.label}</span>
            <span className={styles.line}>{addressLine(address)}</span>
          </span>
        </label>
      ))}
      <Link to="/addresses" className={styles.link} onClick={onNavigate}>
        Управлять адресами
      </Link>
    </fieldset>
  );
}
