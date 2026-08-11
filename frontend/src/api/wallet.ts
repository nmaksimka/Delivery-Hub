import { z } from 'zod';
import { request } from './http';
import { idempotencyKeyHeader } from './idempotency';

const walletSchema = z.object({
  userId: z.number(),
  available: z.coerce.number(),
  held: z.coerce.number(),
  updatedAt: z.string(),
});

export type Wallet = z.infer<typeof walletSchema>;

export const minTopUp = 1;
export const maxTopUp = 50_000;

export const walletApi = {
  get(signal?: AbortSignal) {
    return request('/api/payments/wallet', { schema: walletSchema, signal });
  },
  topUp(amount: number, idempotencyKey: string) {
    return request('/api/payments/wallet/top-up', {
      method: 'POST',
      body: { amount },
      headers: { [idempotencyKeyHeader]: idempotencyKey },
      schema: walletSchema,
    });
  },
};
