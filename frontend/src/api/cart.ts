import { z } from 'zod';
import { request } from './http';
import { idempotencyKeyHeader } from './idempotency';
import { orderSchema } from './orders';

const money = z.coerce.number();

const optionalMoney = z.coerce
  .number()
  .nullish()
  .transform((value) => value ?? null);

const cartLineSchema = z.object({
  menuItemId: z.number(),
  name: z
    .string()
    .nullish()
    .transform((value) => value ?? null),
  price: optionalMoney,
  quantity: z.number(),
  lineTotal: optionalMoney,
  available: z.boolean(),
});

const cartRestaurantSchema = z.object({
  id: z.number(),
  name: z.string(),
  active: z.boolean(),
  closed: z.boolean(),
  workingHours: z
    .string()
    .nullish()
    .transform((value) => value ?? null),
});

export const cartSchema = z.object({
  restaurant: cartRestaurantSchema.nullish().transform((value) => value ?? null),
  items: z.array(cartLineSchema),
  subtotal: money,
  discount: money,
  total: money,
  promoCode: z
    .string()
    .nullish()
    .transform((value) => value ?? null),
  promoProblem: z
    .string()
    .nullish()
    .transform((value) => value ?? null),
  orderable: z.boolean(),
});

export type Cart = z.infer<typeof cartSchema>;
export type CartLine = z.infer<typeof cartLineSchema>;
export type CartRestaurant = z.infer<typeof cartRestaurantSchema>;

export type CartItemToAdd = {
  restaurantId: number;
  menuItemId: number;
  quantity: number;
};

export const emptyCart: Cart = {
  restaurant: null,
  items: [],
  subtotal: 0,
  discount: 0,
  total: 0,
  promoCode: null,
  promoProblem: null,
  orderable: false,
};

export const maxQuantity = 100;

export const cartApi = {
  get(signal?: AbortSignal) {
    return request('/api/cart', { schema: cartSchema, signal });
  },
  add(item: CartItemToAdd, replace: boolean) {
    return request('/api/cart/items', {
      method: 'POST',
      body: item,
      query: { replace: replace || undefined },
      schema: cartSchema,
    });
  },
  changeQuantity(menuItemId: number, quantity: number) {
    return request(`/api/cart/items/${menuItemId}`, {
      method: 'PUT',
      body: { quantity },
      schema: cartSchema,
    });
  },
  remove(menuItemId: number) {
    return request(`/api/cart/items/${menuItemId}`, { method: 'DELETE', schema: cartSchema });
  },
  clear() {
    return request('/api/cart', { method: 'DELETE', schema: z.undefined() });
  },
  applyPromo(code: string) {
    return request('/api/cart/promo', { method: 'POST', body: { code }, schema: cartSchema });
  },
  removePromo() {
    return request('/api/cart/promo', { method: 'DELETE', schema: cartSchema });
  },
  checkout(idempotencyKey: string) {
    return request('/api/cart/checkout', {
      method: 'POST',
      headers: { [idempotencyKeyHeader]: idempotencyKey },
      schema: orderSchema,
    });
  },
};
