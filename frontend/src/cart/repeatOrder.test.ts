import { describe, expect, it, vi } from 'vitest';
import { emptyCart, type Cart, type CartItemToAdd } from '../api/cart';
import { ApiError } from '../api/http';
import type { Order } from '../api/orders';
import { repeatOrder } from './repeatOrder';

const order: Order = {
  id: 77,
  restaurantId: 1,
  status: 'DELIVERED',
  cancellationReason: null,
  refundedAt: null,
  totalAmount: 1218,
  discount: 0,
  promoCode: null,
  deliveryFee: 0,
  etaMinutes: null,
  deliveryAddress: null,
  deliveryLatitude: null,
  deliveryLongitude: null,
  weatherSurcharge: 0,
  createdAt: '2026-07-01T12:00:00Z',
  updatedAt: null,
  orderItems: [
    { id: 1, menuItemId: 10, menuItemName: 'Маргарита', quantity: 2, pricePerUnit: 549 },
    { id: 2, menuItemId: 11, menuItemName: 'Морс', quantity: 1, pricePerUnit: 120 },
  ],
};

function cartWith(count: number): Cart {
  return { ...emptyCart, total: count, orderable: true };
}

describe('repeatOrder', () => {
  it('clearsCartThenAddsEveryItemWithReplace', async () => {
    const steps: string[] = [];
    const clear = vi.fn(() => {
      steps.push('clear');
      return Promise.resolve();
    });
    const add = vi.fn((item: CartItemToAdd, replace: boolean) => {
      steps.push(`add ${item.menuItemId}x${item.quantity} replace=${replace}`);
      return Promise.resolve(cartWith(steps.length));
    });

    const result = await repeatOrder(order, { clear, add });

    expect(steps).toEqual(['clear', 'add 10x2 replace=true', 'add 11x1 replace=true']);
    expect(add).toHaveBeenCalledWith({ restaurantId: 1, menuItemId: 10, quantity: 2 }, true);
    expect(result.cart.total).toBe(3);
    expect(result.skipped).toEqual([]);
  });

  it('skipsDishesThatLeftTheMenu', async () => {
    const add = vi.fn((item: CartItemToAdd) =>
      item.menuItemId === 10
        ? Promise.reject(
            new ApiError(404, 'x', 'Menu item with ID: 10 was not found', 'ITEM_NOT_FOUND'),
          )
        : Promise.resolve(cartWith(1)),
    );

    const result = await repeatOrder(order, { clear: () => Promise.resolve(), add });

    expect(result.skipped).toEqual(['Маргарита']);
    expect(result.cart.total).toBe(1);
  });

  it('stopsOnRestaurantLevelRejection', async () => {
    const add = vi.fn(() =>
      Promise.reject(new ApiError(409, 'x', 'Restaurant 1 is not active', 'RESTAURANT_INACTIVE')),
    );

    await expect(repeatOrder(order, { clear: () => Promise.resolve(), add })).rejects.toMatchObject(
      {
        code: 'RESTAURANT_INACTIVE',
      },
    );
    expect(add).toHaveBeenCalledTimes(1);
  });
});
