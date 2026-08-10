import { createContext, useContext } from 'react';
import type { Cart } from '../api/cart';

export type CartContextValue = {
  cart: Cart;
  count: number;
  isLoading: boolean;
  isOpen: boolean;
  quantityOf: (menuItemId: number) => number;
  open: () => void;
  close: () => void;
};

export const CartContext = createContext<CartContextValue | null>(null);

export function useCart() {
  const value = useContext(CartContext);
  if (!value) {
    throw new Error('useCart вызван вне CartProvider');
  }
  return value;
}
