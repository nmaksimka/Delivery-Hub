import { useMemo, useState, type ReactNode } from 'react';
import { emptyCart } from '../api/cart';
import { useServerCart } from '../api/queries';
import { CartContext, type CartContextValue } from './CartContext';

export function CartProvider({ children }: { children: ReactNode }) {
  const serverCart = useServerCart();
  const [isOpen, setIsOpen] = useState(false);
  const cart = serverCart.data ?? emptyCart;
  const isLoading = serverCart.isLoading;

  const value = useMemo<CartContextValue>(
    () => ({
      cart,
      count: cart.items.reduce((sum, line) => sum + line.quantity, 0),
      isLoading,
      isOpen,
      quantityOf: (menuItemId) =>
        cart.items.find((line) => line.menuItemId === menuItemId)?.quantity ?? 0,
      open: () => {
        setIsOpen(true);
      },
      close: () => {
        setIsOpen(false);
      },
    }),
    [cart, isLoading, isOpen],
  );

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}
