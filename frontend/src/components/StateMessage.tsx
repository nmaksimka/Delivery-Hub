import type { ReactNode } from 'react';
import styles from './StateMessage.module.css';

type StateMessageProps = {
  title: string;
  text?: string;
  role?: 'alert' | 'status';
  children?: ReactNode;
};

export function StateMessage({ title, text, role, children }: StateMessageProps) {
  return (
    <div className={styles.box} role={role}>
      <h2 className={styles.title}>{title}</h2>
      {text && <p className={styles.text}>{text}</p>}
      {children && <div className={styles.action}>{children}</div>}
    </div>
  );
}
