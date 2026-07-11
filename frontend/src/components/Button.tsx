import type { ButtonHTMLAttributes } from 'react';
import { buttonClass, type ButtonLook } from './buttonClass';

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & ButtonLook;

export function Button({ variant, size, block, className, type = 'button', ...rest }: ButtonProps) {
  return (
    <button type={type} className={buttonClass({ variant, size, block, className })} {...rest} />
  );
}
