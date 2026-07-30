import { useId, type InputHTMLAttributes } from 'react';
import { cx } from '../lib/cx';
import styles from './Field.module.css';

type FieldProps = Omit<InputHTMLAttributes<HTMLInputElement>, 'id' | 'className'> & {
  label: string;
  error?: string;
  hint?: string;
};

export function Field({ label, error, hint, ...input }: FieldProps) {
  const id = useId();
  const noteId = `${id}-note`;
  const note = error ?? hint;

  return (
    <div className={styles.field}>
      <label htmlFor={id} className={styles.label}>
        {label}
      </label>
      <input
        id={id}
        className={cx(styles.input, error && styles.invalid)}
        aria-invalid={error ? true : undefined}
        aria-describedby={note ? noteId : undefined}
        {...input}
      />
      {note && (
        <p id={noteId} className={error ? styles.error : styles.hint}>
          {note}
        </p>
      )}
    </div>
  );
}
