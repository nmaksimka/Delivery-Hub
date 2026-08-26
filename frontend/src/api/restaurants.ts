import { z } from 'zod';
import { request } from './http';

const optionalText = z
  .string()
  .nullish()
  .transform((value) => value ?? null);

const optionalCoordinate = z
  .number()
  .nullish()
  .transform((value) => value ?? null);

const restaurantSchema = z.object({
  id: z.number(),
  name: z.string(),
  address: optionalText,
  phone: optionalText,
  cuisine: optionalText,
  city: optionalText,
  imageUrl: optionalText,
  openTime: optionalText,
  closeTime: optionalText,
  active: z.boolean(),
  latitude: optionalCoordinate,
  longitude: optionalCoordinate,
  rating: optionalCoordinate,
  ratingCount: z
    .number()
    .nullish()
    .transform((value) => value ?? 0),
});

const optionalAmount = z.coerce
  .number()
  .nullish()
  .transform((value) => value ?? null);

const nutritionSchema = z.object({
  energyKcal: optionalAmount,
  proteinGrams: optionalAmount,
  fatGrams: optionalAmount,
  carbsGrams: optionalAmount,
  nutriScore: optionalText,
});

const menuItemSchema = z.object({
  id: z.number(),
  name: z.string(),
  description: optionalText,
  price: z.coerce.number(),
  category: optionalText,
  available: z.boolean(),
  imageUrl: optionalText,
  section: z
    .enum(['DISH', 'PRODUCT', 'DRINK'])
    .nullish()
    .transform((value) => value ?? 'DISH'),
  nutrition: nutritionSchema.nullish().transform((value) => value ?? null),
});

export type Restaurant = z.infer<typeof restaurantSchema>;
export type MenuItem = z.infer<typeof menuItemSchema>;
export type Nutrition = z.infer<typeof nutritionSchema>;

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
      query: { availableOnly: false },
      schema: z.array(menuItemSchema),
      signal,
    });
  },
};
