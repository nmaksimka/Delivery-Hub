import { z } from 'zod';
import { request } from './http';

const dailyRevenueSchema = z.object({
  day: z.string(),
  orders: z.number(),
  revenue: z.coerce.number(),
  cumulative: z.coerce.number(),
  movingAverage: z.coerce.number(),
  change: z.coerce.number().nullable(),
});

const topDishSchema = z.object({
  rank: z.number(),
  menuItemId: z.number(),
  name: z.string(),
  quantity: z.number(),
  revenue: z.coerce.number(),
});

const deliveryTimeSchema = z.object({
  day: z.string().nullable(),
  orders: z.number(),
  averageMinutes: z.coerce.number(),
  medianMinutes: z.coerce.number(),
  p90Minutes: z.coerce.number(),
});

const analyticsSchema = z.object({
  from: z.string(),
  to: z.string(),
  restaurantId: z.number().nullable(),
  revenueByDay: z.array(dailyRevenueSchema),
  topDishes: z.array(topDishSchema),
  deliveryTime: deliveryTimeSchema.nullable(),
  deliveryTimeByDay: z.array(deliveryTimeSchema),
});

export type Analytics = z.infer<typeof analyticsSchema>;
export type DailyRevenue = z.infer<typeof dailyRevenueSchema>;
export type TopDish = z.infer<typeof topDishSchema>;
export type DeliveryTime = z.infer<typeof deliveryTimeSchema>;

export type AnalyticsPeriod = { from: string; to: string };

export const analyticsApi = {
  admin(period: AnalyticsPeriod, restaurantId: number | undefined, signal?: AbortSignal) {
    return request('/api/admin/analytics', {
      schema: analyticsSchema,
      query: { ...period, restaurantId },
      signal,
    });
  },
  cabinet(restaurantId: number, period: AnalyticsPeriod, signal?: AbortSignal) {
    return request('/api/cabinet/analytics', {
      schema: analyticsSchema,
      query: { ...period, restaurantId },
      signal,
    });
  },
};
