import { z } from 'zod';
import { request } from './http';
import { pageOf } from './page';

const nullableNumber = z
  .number()
  .nullish()
  .transform((value) => value ?? null);

const courierSchema = z.object({
  id: z.number(),
  userId: z.number(),
  name: z.string(),
  phone: z.string(),
  status: z.enum(['OFFLINE', 'AVAILABLE', 'BUSY']),
  rating: nullableNumber,
  ratingCount: z
    .number()
    .nullish()
    .transform((value) => value ?? 0),
});

const importJobSchema = z.object({
  id: z.string(),
  source: z.string(),
  status: z.string(),
  total: z.number(),
  processed: z.number(),
  progressPercent: z.number(),
  restaurantsCreated: z.number(),
  itemsCreated: z.number(),
  itemsUpdated: z.number(),
  error: z
    .string()
    .nullish()
    .transform((value) => value ?? null),
  createdAt: z.string(),
  finishedAt: z
    .string()
    .nullish()
    .transform((value) => value ?? null),
});

export type Courier = z.infer<typeof courierSchema>;
export type ImportJob = z.infer<typeof importJobSchema>;
export type ImportKind = 'meals' | 'products';

export type CourierInput = {
  userId: number;
  name: string;
  phone: string;
};

export const adminApi = {
  couriers(options: { page: number; size: number; signal?: AbortSignal }) {
    return request('/api/admin/couriers', {
      query: { page: options.page, size: options.size },
      schema: pageOf(courierSchema),
      signal: options.signal,
    });
  },
  registerCourier(input: CourierInput) {
    return request('/api/admin/couriers', { method: 'POST', body: input, schema: courierSchema });
  },
  importJobs(signal?: AbortSignal) {
    return request('/api/admin/imports', { schema: z.array(importJobSchema), signal });
  },
  startImport(kind: ImportKind) {
    return request(`/api/admin/imports/${kind}`, { method: 'POST', schema: importJobSchema });
  },
};
