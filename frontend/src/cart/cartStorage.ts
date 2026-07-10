import { z } from 'zod';
import { emptyCart, maxQuantity, type CartState } from './cartState';

const storageKey = 'deliveryhub.cart';

const cartSchema = z.object({
  restaurant: z.object({ id: z.number(), name: z.string() }).nullable(),
  lines: z.array(
    z.object({
      menuItemId: z.number(),
      name: z.string(),
      price: z.number().nonnegative(),
      quantity: z.number().int().min(1).max(maxQuantity),
    }),
  ),
});

export function loadCart(): CartState {
  try {
    const raw = localStorage.getItem(storageKey);
    if (!raw) {
      return emptyCart;
    }
    const parsed = cartSchema.safeParse(JSON.parse(raw));
    if (!parsed.success || !parsed.data.restaurant || parsed.data.lines.length === 0) {
      return emptyCart;
    }
    return parsed.data;
  } catch {
    return emptyCart;
  }
}

export function saveCart(state: CartState) {
  try {
    localStorage.setItem(storageKey, JSON.stringify(state));
  } catch {
    return;
  }
}
