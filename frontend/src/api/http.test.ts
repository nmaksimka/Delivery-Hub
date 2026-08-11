import { describe, expect, it, vi } from 'vitest';
import { z } from 'zod';
import { sessionStore } from '../auth/sessionStore';
import { jsonResponse } from '../test/renderApp';
import { ApiError, request } from './http';
import { pageOf } from './page';

const session = {
  accessToken: 'jwt-token',
  refreshToken: 'refresh-token',
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

  it('rejectsResponseThatDoesNotMatchSchema', async () => {
    stubFetch(jsonResponse({ id: 'not-a-number' }));

    const failure = request('/api/restaurants/1', { schema: z.object({ id: z.number() }) });

    await expect(failure).rejects.toBeInstanceOf(ApiError);
    await expect(failure).rejects.toThrow('Сервер вернул неожиданный ответ');
  });

  it('keepsProblemCodeOfFailedResponse', async () => {
    stubFetch(
      jsonResponse({ detail: 'Restaurant 1 is closed now', code: 'RESTAURANT_CLOSED' }, 409),
    );

    await expect(request('/api/orders', { schema: z.unknown() })).rejects.toMatchObject({
      status: 409,
      detail: 'Restaurant 1 is closed now',
      code: 'RESTAURANT_CLOSED',
    });
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

const renewedTokens = {
  accessToken: 'fresh-token',
  refreshToken: 'fresh-refresh',
  type: 'Bearer',
  expiresIn: 900,
  id: 5,
  email: 'max@example.com',
  name: 'Максим',
  role: 'USER',
};

function stubSequence(...responses: Response[]) {
  const fetchMock = vi.fn<typeof fetch>(() =>
    Promise.resolve(responses.shift() ?? jsonResponse({ detail: 'Unexpected call' }, 500)),
  );
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function authorizationOf(fetchMock: ReturnType<typeof stubSequence>, call: number) {
  return new Headers(fetchMock.mock.calls[call]?.[1]?.headers).get('Authorization');
}

describe('token refresh', () => {
  it('refreshesExpiredTokenOnceAndRepeatsRequest', async () => {
    sessionStore.set(session);
    const fetchMock = stubSequence(
      jsonResponse({ detail: 'Token expired' }, 401),
      jsonResponse(renewedTokens),
      jsonResponse({ ok: true }),
    );

    const result = await request('/api/orders', { schema: z.object({ ok: z.boolean() }) });

    expect(result).toEqual({ ok: true });
    expect(fetchMock).toHaveBeenCalledTimes(3);
    const refreshCall = fetchMock.mock.calls[1];
    expect(refreshCall?.[0]).toBe('/api/auth/refresh');
    expect(refreshCall?.[1]?.body).toBe(JSON.stringify({ refreshToken: 'refresh-token' }));
    expect(authorizationOf(fetchMock, 2)).toBe('Bearer fresh-token');
    expect(sessionStore.get()).toMatchObject({
      accessToken: 'fresh-token',
      refreshToken: 'fresh-refresh',
    });
  });

  it('logsOutWhenRefreshIsRejected', async () => {
    sessionStore.set(session);
    const fetchMock = stubSequence(
      jsonResponse({ detail: 'Token expired' }, 401),
      jsonResponse({ detail: 'Refresh token is invalid' }, 401),
    );

    const failure = request('/api/orders', { schema: z.unknown() });

    await expect(failure).rejects.toMatchObject({ status: 401, detail: 'Token expired' });
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(sessionStore.get()).toBeNull();
    expect(localStorage.getItem('deliveryhub.session')).toBeNull();
  });

  it('logsOutWhenRenewedTokenIsAlsoRejected', async () => {
    sessionStore.set(session);
    const fetchMock = stubSequence(
      jsonResponse({}, 401),
      jsonResponse(renewedTokens),
      jsonResponse({}, 401),
    );

    await expect(request('/api/orders', { schema: z.unknown() })).rejects.toMatchObject({
      status: 401,
    });
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(sessionStore.get()).toBeNull();
  });

  it('sharesOneRefreshBetweenConcurrentRequests', async () => {
    sessionStore.set(session);
    const fetchMock = vi.fn<typeof fetch>((input, init) => {
      if (String(input) === '/api/auth/refresh') {
        return Promise.resolve(jsonResponse(renewedTokens));
      }
      const authorization = new Headers(init?.headers).get('Authorization');
      return Promise.resolve(
        authorization === 'Bearer fresh-token' ? jsonResponse({ ok: true }) : jsonResponse({}, 401),
      );
    });
    vi.stubGlobal('fetch', fetchMock);
    const schema = z.object({ ok: z.boolean() });

    await Promise.all([request('/api/orders', { schema }), request('/api/orders/7', { schema })]);

    const refreshCalls = fetchMock.mock.calls.filter(([url]) => url === '/api/auth/refresh');
    expect(refreshCalls).toHaveLength(1);
  });

  it('doesNotRefreshForAuthEndpoints', async () => {
    sessionStore.set(session);
    const fetchMock = stubSequence(jsonResponse({ detail: 'Bad credentials' }, 401));

    await expect(
      request('/api/auth/login', { method: 'POST', body: {}, schema: z.unknown() }),
    ).rejects.toMatchObject({ status: 401 });
    expect(fetchMock).toHaveBeenCalledTimes(1);
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
