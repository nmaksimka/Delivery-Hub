import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { sessionStore } from './auth/sessionStore';
import { jsonResponse, renderApp, stubApi } from './test/renderApp';

const session = {
  accessToken: 'jwt-token',
  refreshToken: 'refresh-token',
  user: { id: 5, email: 'max@example.com', name: 'Максим', role: 'USER' },
};

const pizzeria = {
  id: 1,
  name: 'Пицца на Ленина',
  address: null,
  phone: null,
  openTime: null,
  closeTime: null,
  active: true,
};

function orderOf(overrides: Record<string, unknown> = {}) {
  return {
    id: 77,
    userId: 5,
    restaurantId: 1,
    status: 'CREATED',
    cancellationReason: null,
    refundedAt: null,
    totalAmount: '1218.00',
    discount: '0.00',
    promoCode: null,
    createdAt: '2026-07-01T12:00:00Z',
    updatedAt: '2026-07-01T12:00:00Z',
    orderItems: [
      { id: 1, menuItemId: 10, menuItemName: 'Маргарита', quantity: 2, pricePerUnit: 549 },
      { id: 2, menuItemId: 11, menuItemName: 'Морс', quantity: 1, pricePerUnit: 120 },
    ],
    ...overrides,
  };
}

function stepStates() {
  return within(screen.getByRole('list', { name: 'Ход заказа' }))
    .getAllByRole('listitem')
    .map((step) => step.textContent);
}

describe('order page', () => {
  it('pollsSagaStatusUntilItBecomesFinal', async () => {
    sessionStore.set(session);
    const statuses = ['CREATED', 'PAYMENT_HELD', 'DELIVERED'];
    let calls = 0;
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const path = String(input).split('?')[0];
      if (path === '/api/orders/77') {
        const status = statuses[Math.min(calls, statuses.length - 1)];
        calls += 1;
        return Promise.resolve(jsonResponse(orderOf({ status })));
      }
      if (path === '/api/restaurants/1') {
        return Promise.resolve(jsonResponse(pizzeria));
      }
      return Promise.resolve(jsonResponse({ detail: 'Not found' }, 404));
    });
    vi.stubGlobal('fetch', fetchMock);
    renderApp('/orders/77');

    expect(await screen.findByRole('heading', { name: 'Заказ №77' })).toBeInTheDocument();
    expect(stepStates()[0]).toContain('Оплата, сейчас');

    expect(
      await screen.findByText('Оплачен, ищем курьера', {}, { timeout: 4000 }),
    ).toBeInTheDocument();
    expect(stepStates()[0]).toContain('Оплата, готово');
    expect(stepStates()[1]).toContain('Курьер, сейчас');

    expect(
      await screen.findByText('Доставлен', { selector: '[role="status"]' }, { timeout: 4000 }),
    ).toBeInTheDocument();
    const settled = calls;
    await new Promise((resolve) => setTimeout(resolve, 2500));
    expect(calls).toBe(settled);
    expect(screen.queryByRole('button', { name: 'Отменить заказ' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Повторить заказ' })).toBeInTheDocument();
  }, 15_000);

  it('explainsCancellationAndRefund', async () => {
    sessionStore.set(session);
    stubApi({
      'GET /api/orders/77': {
        body: orderOf({
          status: 'CANCELLED',
          cancellationReason: 'PAYMENT_REJECTED',
          refundedAt: '2026-07-01T12:05:00Z',
        }),
      },
    });
    renderApp('/orders/77');

    expect(await screen.findByText(/Оплата не прошла/)).toBeInTheDocument();
    expect(screen.getByText(/Деньги возвращены на кошелёк/)).toBeInTheDocument();
    expect(stepStates().every((step) => step?.includes('остановлено'))).toBe(true);
  });

  it('cancelsOrderAfterConfirmation', async () => {
    sessionStore.set(session);
    let current = orderOf({ status: 'PAYMENT_HELD' });
    const fetchMock = stubApi({
      'GET /api/orders/77': { body: () => current },
      'POST /api/orders/77/cancel': {
        body: () => {
          current = orderOf({ status: 'CANCELLED', cancellationReason: 'CUSTOMER' });
          return current;
        },
      },
    });
    const user = userEvent.setup();
    renderApp('/orders/77');

    await user.click(await screen.findByRole('button', { name: 'Отменить заказ' }));
    const dialog = screen.getByRole('alertdialog', { name: 'Отменить заказ?' });
    await user.click(within(dialog).getByRole('button', { name: 'Отменить заказ' }));

    expect(await screen.findByText('Вы отменили заказ')).toBeInTheDocument();
    expect(
      fetchMock.mock.calls.some(
        ([url, init]) => url === '/api/orders/77/cancel' && init?.method === 'POST',
      ),
    ).toBe(true);
  });

  it('hidesCancelWhileCourierIsOnTheWay', async () => {
    sessionStore.set(session);
    stubApi({ 'GET /api/orders/77': { body: orderOf({ status: 'IN_DELIVERY' }) } });
    renderApp('/orders/77');

    expect(await screen.findByText('В пути', { selector: '[role="status"]' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Отменить заказ' })).not.toBeInTheDocument();
  });
});

describe('order history', () => {
  it('repeatsDeliveredOrderIntoFreshCart', async () => {
    sessionStore.set(session);
    const repeatedCart = {
      restaurant: { id: 1, name: pizzeria.name, active: true, closed: false, workingHours: null },
      items: [
        {
          menuItemId: 10,
          name: 'Маргарита',
          price: 549,
          quantity: 2,
          lineTotal: 1098,
          available: true,
        },
        { menuItemId: 11, name: 'Морс', price: 120, quantity: 1, lineTotal: 120, available: true },
      ],
      subtotal: 1218,
      discount: 0,
      total: 1218,
      promoCode: null,
      promoProblem: null,
      orderable: true,
    };
    const fetchMock = stubApi({
      'GET /api/restaurants': { body: [pizzeria] },
      'GET /api/orders': {
        body: {
          content: [orderOf({ status: 'DELIVERED' })],
          number: 0,
          size: 10,
          totalElements: 1,
          totalPages: 1,
        },
      },
      'DELETE /api/cart': { status: 204, body: null },
      'POST /api/cart/items': { body: repeatedCart },
    });
    const user = userEvent.setup();
    renderApp('/orders');

    await user.click(await screen.findByRole('button', { name: 'Повторить заказ' }));

    const drawer = await screen.findByRole('dialog', { name: 'Корзина' });
    expect(drawer).toHaveTextContent('Морс');
    const cartCalls = fetchMock.mock.calls
      .filter(([url]) => String(url).startsWith('/api/cart'))
      .map(([url, init]) => `${init?.method ?? 'GET'} ${String(url)}`);
    expect(cartCalls).toEqual(
      expect.arrayContaining([
        'DELETE /api/cart',
        'POST /api/cart/items?replace=true',
        'POST /api/cart/items?replace=true',
      ]),
    );
    const firstDelete = cartCalls.indexOf('DELETE /api/cart');
    const firstAdd = cartCalls.indexOf('POST /api/cart/items?replace=true');
    expect(firstDelete).toBeLessThan(firstAdd);
    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Корзина, 3 блюда' })).toBeInTheDocument();
    });
  });

  it('linksActiveOrderToLiveStatusPage', async () => {
    sessionStore.set(session);
    stubApi({
      'GET /api/restaurants': { body: [pizzeria] },
      'GET /api/orders': {
        body: {
          content: [orderOf({ status: 'COURIER_ASSIGNED' })],
          number: 0,
          size: 10,
          totalElements: 1,
          totalPages: 1,
        },
      },
    });
    renderApp('/orders');

    expect(await screen.findByRole('link', { name: 'Следить' })).toHaveAttribute(
      'href',
      '/orders/77',
    );
  });
});
