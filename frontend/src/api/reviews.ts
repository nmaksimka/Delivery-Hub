import { z } from 'zod';
import { request } from './http';
import { pageOf } from './page';

const optionalNumber = z
  .number()
  .nullish()
  .transform((value) => value ?? null);

export const reviewSchema = z.object({
  id: z.number(),
  orderId: z.number(),
  restaurantId: z.number(),
  courierId: optionalNumber,
  restaurantRating: z.number(),
  courierRating: optionalNumber,
  comment: z.string(),
  createdAt: z.string(),
});

export type Review = z.infer<typeof reviewSchema>;

export type ReviewInput = {
  restaurantRating: number;
  courierRating: number | null;
  comment: string;
};

export const reviewsApi = {
  mine(signal?: AbortSignal) {
    return request('/api/orders/reviews/mine', {
      query: { size: 200 },
      schema: pageOf(reviewSchema),
      signal,
    });
  },
  submit(orderId: number, input: ReviewInput) {
    return request(`/api/orders/${orderId}/review`, {
      method: 'POST',
      body: input,
      schema: reviewSchema,
    });
  },
};
