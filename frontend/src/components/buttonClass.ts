import { cx } from '../lib/cx';
import styles from './Button.module.css';

export type ButtonLook = {
  variant?: 'primary' | 'dark' | 'ghost';
  size?: 'regular' | 'small';
  block?: boolean;
  className?: string;
};

export function buttonClass({
  variant = 'primary',
  size = 'regular',
  block = false,
  className,
}: ButtonLook = {}) {
  return cx(
    styles.button,
    styles[variant],
    size === 'small' && styles.small,
    block && styles.block,
    className,
  );
}
