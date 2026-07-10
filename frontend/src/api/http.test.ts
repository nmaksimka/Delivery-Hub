import { describe, expect, it, vi } from 'vitest';
import { z } from 'zod';
import { sessionStore } from '../auth/sessionStore';
import { jsonResponse } from '../test/renderApp';
import { ApiError, request } from './http';
import { pageOf } from './page';

const session = {
  token: 'jwt-token',
  user: { id: 5, email: 'max@example.com', name: 'Максим', role: 'USER' },
};

function stubFetch(response: Response) {
  const fetchMock = vi.fn<typeof fetch>(() => Promise.resolve(response));
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

describe('request', () => {
  it('sendsBearerTokenAndQueryParameters', async () => {
    sessionStore.set(session);
    const fetchMock = stubFetch(jsonResponse({ ok: true }));

    await request('/api/orders', {
      query: { page: 0, size: 10, sort: undefined },
      schema: z.object({ ok: z.boolean() }),
    });

    const [url, init] = fetchMock.mock.calls[0] ?? [];
    expect(url).toBe('/api/orders?page=0&size=10');
    expect(new Headers(init?.headers).get('Authorization')).toBe('Bearer jwt-token');
  });

  it('omitsAuthorizationHeaderForGuests', async () => {
    const fetchMock = stubFetch(jsonResponse([]));

    await request('/api/restaurants', { schema: z.array(z.unknown()) });

    const init = fetchMock.mock.calls[0]?.[1];
    expect(new Headers(init?.headers).has('Authorization')).toBe(false);
  });

  it('dropsSessionWhenServerAnswersUnauthorized', async () => {
    sessionStore.set(session);
    stubFetch(jsonResponse({ detail: 'Token expired' }, 401));

    const failure = request('/api/orders', { schema: z.unknown() });

    await expect(failure).rejects.toMatchObject({ status: 401, detail: 'Token expired' });
    expect(sessionStore.get()).toBeNull();
    expect(localStorage.getItem('deliveryhub.session')).toBeNull();
  });

  it('rejectsResponseThatDoesNotMatchSchema', async () => {
    stubFetch(jsonResponse({ id: 'not-a-number' }));

    const failure = request('/api/restaurants/1', { schema: z.object({ id: z.number() }) });

    await expect(failure).rejects.toBeInstanceOf(ApiError);
    await expect(failure).rejects.toThrow('Сервер вернул неожиданный ответ');
  });

  it('reportsNetworkFailureAsApiError', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.reject(new TypeError('Failed to fetch'))),
    );

    await expect(request('/api/restaurants', { schema: z.unknown() })).rejects.toMatchObject({
      status: 0,
    });
  });
});

describe('pageOf', () => {
  const expected = { items: [{ id: 1 }], page: 0, size: 20, totalElements: 1, totalPages: 1 };
  const schema = pageOf(z.object({ id: z.number() }));

  it('readsClassicSpringPage', () => {
    const parsed = schema.parse({
      content: [{ id: 1 }],
      number: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      first: true,
    });

    expect(parsed).toEqual(expected);
  });

  it('readsPagedModelWithNestedPageInfo', () => {
    const parsed = schema.parse({
      content: [{ id: 1 }],
      page: { number: 0, size: 20, totalElements: 1, totalPages: 1 },
    });

    expect(parsed).toEqual(expected);
  });
});
