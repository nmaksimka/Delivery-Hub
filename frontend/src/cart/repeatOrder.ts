import { emptyCart, maxQuantity, type Cart, type CartItemToAdd } from '../api/cart';
import { ApiError } from '../api/http';
import type { Order } from '../api/orders';

type CartWriter = {
  clear: () => Promise<unknown>;
  add: (item: CartItemToAdd, replace: boolean) => Promise<Cart>;
};

export type RepeatResult = {
  cart: Cart;
  skipped: string[];
};

const skippableCodes = new Set(['ITEM_NOT_FOUND', 'QUANTITY_LIMIT']);

function isSkippable(error: unknown) {
  return error instanceof ApiError && error.code !== null && skippableCodes.has(error.code);
}

export async function repeatOrder(order: Order, cart: CartWriter): Promise<RepeatResult> {
  await cart.clear();
  let current = emptyCart;
  const skipped: string[] = [];
  for (const item of order.orderItems) {
    try {
      current = await cart.add(
        {
          restaurantId: order.restaurantId,
          menuItemId: item.menuItemId,
          quantity: Math.min(item.quantity, maxQuantity),
        },
        true,
      );
    } catch (error) {
      if (!isSkippable(error)) {
        throw error;
      }
      skipped.push(item.menuItemName);
    }
  }
  return { cart: current, skipped };
}
