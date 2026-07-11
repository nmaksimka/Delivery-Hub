import { useEffect, useMemo, useReducer, useState, type ReactNode } from 'react';
import { CartContext, type CartContextValue } from './CartContext';
import { cartCount, cartReducer, cartTotal } from './cartState';
import { loadCart, saveCart } from './cartStorage';

export function CartProvider({ children }: { children: ReactNode }) {
  const [state, dispatch] = useReducer(cartReducer, undefined, loadCart);
  const [isOpen, setIsOpen] = useState(false);

  useEffect(() => {
    saveCart(state);
  }, [state]);

  const value = useMemo<CartContextValue>(
    () => ({
      restaurant: state.restaurant,
      lines: state.lines,
      count: cartCount(state),
      total: cartTotal(state),
      isOpen,
      quantityOf: (menuItemId) =>
        state.lines.find((line) => line.menuItemId === menuItemId)?.quantity ?? 0,
      add: (restaurant, item) => {
        dispatch({ type: 'added', restaurant, item });
      },
      decrement: (menuItemId) => {
        dispatch({ type: 'decremented', menuItemId });
      },
      remove: (menuItemId) => {
        dispatch({ type: 'removed', menuItemId });
      },
      clear: () => {
        dispatch({ type: 'cleared' });
      },
      open: () => {
        setIsOpen(true);
      },
      close: () => {
        setIsOpen(false);
      },
    }),
    [state, isOpen],
  );

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}
