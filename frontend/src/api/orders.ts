import { z } from 'zod';
import { request } from './http';
import { pageOf } from './page';

const optionalNumber = z.coerce
  .number()
  .nullish()
  .transform((value) => value ?? null);

const optionalText = z
  .string()
  .nullish()
  .transform((value) => value ?? null);

const orderItemSchema = z.object({
  id: z.number(),
  menuItemId: z.number(),
  menuItemName: z.string(),
  quantity: z.number(),
  pricePerUnit: z.coerce.number(),
});

export const orderSchema = z.object({
  id: z.number(),
  restaurantId: z.number(),
  status: z.string(),
  cancellationReason: optionalText,
  refundedAt: optionalText,
  totalAmount: z.coerce.number(),
  discount: z.coerce
    .number()
    .nullish()
    .transform((value) => value ?? 0),
  promoCode: optionalText,
  deliveryFee: z.coerce
    .number()
    .nullish()
    .transform((value) => value ?? 0),
  etaMinutes: z
    .number()
    .nullish()
    .transform((value) => value ?? null),
  deliveryAddress: optionalText,
  deliveryLatitude: optionalNumber,
  deliveryLongitude: optionalNumber,
  weatherSurcharge: z.coerce
    .number()
    .nullish()
    .transform((value) => value ?? 0),
  createdAt: z.string(),
  updatedAt: optionalText,
  orderItems: z
    .array(orderItemSchema)
    .nullish()
    .transform((items) => items ?? []),
});

export type OrderItem = z.infer<typeof orderItemSchema>;
export type Order = z.infer<typeof orderSchema>;

export const ordersApi = {
  mine(options: { page: number; size: number; signal?: AbortSignal }) {
    return request('/api/orders', {
      query: { page: options.page, size: options.size, sort: 'createdAt,desc' },
      schema: pageOf(orderSchema),
      signal: options.signal,
    });
  },
  byId(id: number, signal?: AbortSignal) {
    return request(`/api/orders/${id}`, { schema: orderSchema, signal });
  },
  cancel(id: number) {
    return request(`/api/orders/${id}/cancel`, { method: 'POST', schema: orderSchema });
  },
};
