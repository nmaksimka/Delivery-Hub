import { z } from 'zod';
import { request } from './http';

const optionalText = z
  .string()
  .nullish()
  .transform((value) => value ?? null);

const restaurantSchema = z.object({
  id: z.number(),
  name: z.string(),
  address: optionalText,
  phone: optionalText,
  openTime: optionalText,
  closeTime: optionalText,
  active: z.boolean(),
});

const menuItemSchema = z.object({
  id: z.number(),
  name: z.string(),
  description: optionalText,
  price: z.coerce.number(),
  category: optionalText,
  available: z.boolean(),
});

export type Restaurant = z.infer<typeof restaurantSchema>;
export type MenuItem = z.infer<typeof menuItemSchema>;

export const restaurantsApi = {
  list(options: { activeOnly?: boolean; signal?: AbortSignal } = {}) {
    return request('/api/restaurants', {
      query: { activeOnly: options.activeOnly },
      schema: z.array(restaurantSchema),
      signal: options.signal,
    });
  },
  byId(id: number, signal?: AbortSignal) {
    return request(`/api/restaurants/${id}`, { schema: restaurantSchema, signal });
  },
  menu(restaurantId: number, signal?: AbortSignal) {
    return request(`/api/restaurants/${restaurantId}/menu`, {
      schema: z.array(menuItemSchema),
      signal,
    });
  },
};
