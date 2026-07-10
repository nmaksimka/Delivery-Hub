import { createContext, useContext } from 'react';
import type { CartItem, CartLine, CartRestaurant } from './cartState';

export type CartContextValue = {
  restaurant: CartRestaurant | null;
  lines: CartLine[];
  count: number;
  total: number;
  isOpen: boolean;
  quantityOf: (menuItemId: number) => number;
  add: (restaurant: CartRestaurant, item: CartItem) => void;
  decrement: (menuItemId: number) => void;
  remove: (menuItemId: number) => void;
  clear: () => void;
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
