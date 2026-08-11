import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { vi } from 'vitest';
import { AuthProvider } from '../auth/AuthProvider';
import { CartProvider } from '../cart/CartProvider';
import { routes } from '../routes';

type Reply = {
  status?: number;
  body: unknown;
};

export function jsonResponse(body: unknown, status = 200) {
  if (status === 204) {
    return new Response(null, { status });
  }
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

export function stubApi(replies: Record<string, Reply>) {
  const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input).split('?')[0] ?? '';
    const reply = replies[`${init?.method ?? 'GET'} ${path}`];
    return Promise.resolve(
      reply
        ? jsonResponse(
            typeof reply.body === 'function' ? (reply.body as () => unknown)() : reply.body,
            reply.status,
          )
        : jsonResponse({ detail: 'Not found' }, 404),
    );
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

export function renderApp(path: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter(routes, { initialEntries: [path] });

  render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <CartProvider>
          <RouterProvider router={router} />
        </CartProvider>
      </AuthProvider>
    </QueryClientProvider>,
  );
  return router;
}
