import { describe, expect, it, vi } from 'vitest';
import { jsonResponse } from '../test/renderApp';
import { ApiError } from './http';
import { createAttemptKeys, isRetryableAttempt } from './idempotency';
import { ordersApi } from './orders';

const order = { restaurantId: 1, orderItemsRequest: [{ menuItemId: 10, quantity: 2 }] };

describe('createAttemptKeys', () => {
  it('reusesKeyWhenSameOrderIsRetriedAfterNetworkOrServerFailure', () => {
    const attempts = createAttemptKeys();
    const first = attempts.keyFor(order);

    attempts.settle(new ApiError(0, 'Нет связи с сервером'));
    expect(attempts.keyFor(order)).toBe(first);

    attempts.settle(new ApiError(503, 'Сервис временно недоступен'));
    expect(attempts.keyFor({ ...order })).toBe(first);

    attempts.settle(new ApiError(409, 'x', 'still processing', 'IDEMPOTENCY_IN_PROGRESS'));
    expect(attempts.keyFor(order)).toBe(first);
  });

  it('startsNewAttemptAfterSuccessOrRejection', () => {
    const attempts = createAttemptKeys();
    const first = attempts.keyFor(order);

    attempts.settle(null);
    const second = attempts.keyFor(order);
    expect(second).not.toBe(first);

    attempts.settle(new ApiError(409, 'x', 'closed', 'RESTAURANT_CLOSED'));
    expect(attempts.keyFor(order)).not.toBe(second);
  });

  it('startsNewAttemptWhenOrderChanges', () => {
    const attempts = createAttemptKeys();
    const first = attempts.keyFor(order);

    attempts.settle(new ApiError(0, 'Нет связи с сервером'));

    expect(
      attempts.keyFor({ ...order, orderItemsRequest: [{ menuItemId: 10, quantity: 3 }] }),
    ).not.toBe(first);
  });

  it('treatsOnlyTransientFailuresAsRetryable', () => {
    expect(isRetryableAttempt(new ApiError(502, 'x'))).toBe(true);
    expect(isRetryableAttempt(new ApiError(422, 'x', null, 'IDEMPOTENCY_KEY_REUSED'))).toBe(false);
    expect(isRetryableAttempt(new Error('boom'))).toBe(false);
  });
});

describe('ordersApi.create', () => {
  it('sendsIdempotencyKeyHeader', async () => {
    const fetchMock = vi.fn<typeof fetch>(() =>
      Promise.resolve(
        jsonResponse(
          {
            id: 7,
            restaurantId: 1,
            status: 'CREATED',
            totalAmount: 25,
            createdAt: '2026-07-10T10:00:00Z',
          },
          201,
        ),
      ),
    );
    vi.stubGlobal('fetch', fetchMock);

    await ordersApi.create(order, 'attempt-1');

    const init = fetchMock.mock.calls[0]?.[1];
    expect(new Headers(init?.headers).get('Idempotency-Key')).toBe('attempt-1');
    expect(new Headers(init?.headers).get('Accept')).toBe('application/json');
  });
});
