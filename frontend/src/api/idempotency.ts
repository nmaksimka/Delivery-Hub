import { ApiError } from './http';

export const idempotencyKeyHeader = 'Idempotency-Key';

export function isRetryableAttempt(error: unknown) {
  if (!(error instanceof ApiError)) {
    return false;
  }
  return error.status === 0 || error.status >= 500 || error.code === 'IDEMPOTENCY_IN_PROGRESS';
}

export function createAttemptKeys() {
  let current: { payload: string; key: string } | null = null;
  return {
    keyFor(payload: unknown) {
      const serialized = JSON.stringify(payload);
      if (current?.payload !== serialized) {
        current = { payload: serialized, key: crypto.randomUUID() };
      }
      return current.key;
    },
    settle(error: unknown) {
      if (!isRetryableAttempt(error)) {
        current = null;
      }
    },
  };
}
