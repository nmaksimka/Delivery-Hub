import { z } from 'zod';
import { request } from './http';
import { idempotencyKeyHeader } from './idempotency';
import { pageOf } from './page';

const orderItemSchema = z.object({
  id: z.number(),
  menuItemId: z.number(),
  menuItemName: z.string(),
  quantity: z.number(),
  pricePerUnit: z.coerce.number(),
});

const orderSchema = z.object({
  id: z.number(),
  restaurantId: z.number(),
  status: z.string(),
  cancellationReason: z.string().nullish(),
  totalAmount: z.coerce.number(),
  createdAt: z.string(),
  orderItems: z
    .array(orderItemSchema)
    .nullish()
    .transform((items) => items ?? []),
});

export type OrderItem = z.infer<typeof orderItemSchema>;
export type Order = z.infer<typeof orderSchema>;

export type NewOrder = {
  restaurantId: number;
  orderItemsRequest: { menuItemId: number; quantity: number }[];
};

export const ordersApi = {
  create(order: NewOrder, idempotencyKey: string) {
    return request('/api/orders', {
      method: 'POST',
      body: order,
      headers: { [idempotencyKeyHeader]: idempotencyKey },
      schema: orderSchema,
    });
  },
  mine(options: { page: number; size: number; signal?: AbortSignal }) {
    return request('/api/orders', {
      query: { page: options.page, size: options.size, sort: 'createdAt,desc' },
      schema: pageOf(orderSchema),
      signal: options.signal,
    });
  },
};
