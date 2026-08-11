import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { sessionStore } from './auth/sessionStore';
import { renderApp, stubApi } from './test/renderApp';

const session = {
  accessToken: 'jwt-token',
  refreshToken: 'refresh-token',
  user: { id: 5, email: 'max@example.com', name: 'Максим', role: 'USER' },
};

const pizzeria = {
  id: 1,
  name: 'Пицца на Ленина',
  address: 'пр. Ленина, 36',
  phone: null,
  openTime: null,
  closeTime: null,
  active: true,
};

const sushiBar = { ...pizzeria, id: 2, name: 'Суши Томь' };

const sushiMenu = [
  {
    id: 20,
    name: 'Ролл с лососем',
    description: null,
    price: 410,
    category: 'Роллы',
    available: true,
    restaurantId: 2,
  },
];

function cartOf(overrides: Record<string, unknown> = {}) {
  return {
    restaurant: { id: 1, name: pizzeria.name, active: true, closed: false, workingHours: null },
    items: [
      {
        menuItemId: 10,
        name: 'Маргарита',
        price: '549.00',
        quantity: 2,
        lineTotal: '1098.00',
        available: true,
      },
    ],
    subtotal: '1098.00',
    discount: '0.00',
    total: '1098.00',
    promoCode: null,
    promoProblem: null,
    orderable: true,
    ...overrides,
  };
}

const richWallet = {
  userId: 5,
  available: '5000.00',
  held: '0.00',
  updatedAt: '2026-07-01T10:00:00Z',
};

const placedOrder = {
  id: 77,
  userId: 5,
  restaurantId: 1,
  status: 'CREATED',
  cancellationReason: null,
  refundedAt: null,
  totalAmount: '1098.00',
  discount: '0.00',
  promoCode: null,
  createdAt: '2026-07-01T12:00:00Z',
  updatedAt: '2026-07-01T12:00:00Z',
  orderItems: [
    { id: 1, menuItemId: 10, menuItemName: 'Маргарита', quantity: 2, pricePerUnit: 549 },
  ],
};

function callsTo(fetchMock: ReturnType<typeof stubApi>, method: string, path: string) {
  return fetchMock.mock.calls.filter(
    ([url, init]) => (init?.method ?? 'GET') === method && String(url).split('?')[0] === path,
  );
}

async function openCart(user: ReturnType<typeof userEvent.setup>, label: string) {
  await user.click(await screen.findByRole('button', { name: label }));
  return screen.getByRole('dialog', { name: 'Корзина' });
}

describe('server cart', () => {
  it('replacesCartAfterConfirmWhenDishComesFromAnotherRestaurant', async () => {
    sessionStore.set(session);
    const sushiCart = cartOf({
      restaurant: { id: 2, name: sushiBar.name, active: true, closed: false, workingHours: null },
      items: [
        {
          menuItemId: 20,
          name: 'Ролл с лососем',
          price: 410,
          quantity: 1,
          lineTotal: 410,
          available: true,
        },
      ],
      subtotal: 410,
      total: 410,
    });
    const fetchMock = stubApi({
      'GET /api/restaurants/2': { body: sushiBar },
      'GET /api/restaurants/2/menu': { body: sushiMenu },
      'GET /api/cart': { body: cartOf() },
      'POST /api/cart/items': { body: sushiCart },
    });
    const user = userEvent.setup();
    renderApp('/restaurants/2');

    await screen.findByRole('button', { name: 'Корзина, 2 блюда' });
    await user.click(
      await screen.findByRole('button', { name: 'Добавить в корзину: Ролл с лососем' }),
    );

    const dialog = screen.getByRole('alertdialog', { name: 'Очистить корзину и добавить?' });
    expect(dialog).toHaveTextContent('«Пицца на Ленина»');
    expect(callsTo(fetchMock, 'POST', '/api/cart/items')).toHaveLength(0);

    await user.click(within(dialog).getByRole('button', { name: 'Очистить и добавить' }));

    expect(await screen.findByRole('button', { name: 'Корзина, 1 блюдо' })).toBeInTheDocument();
    const [url, init] = callsTo(fetchMock, 'POST', '/api/cart/items')[0] ?? [];
    expect(String(url)).toContain('replace=true');
    expect(JSON.parse(String(init?.body))).toEqual({
      restaurantId: 2,
      menuItemId: 20,
      quantity: 1,
    });
  });

  it('showsUnavailableLinesAndBlocksCheckout', async () => {
    sessionStore.set(session);
    stubApi({
      'GET /api/restaurants': { body: [pizzeria] },
      'GET /api/payments/wallet': { body: richWallet },
      'GET /api/cart': {
        body: cartOf({
          items: [
            {
              menuItemId: 10,
              name: 'Маргарита',
              price: 549,
              quantity: 1,
              lineTotal: 549,
              available: true,
            },
            {
              menuItemId: 11,
              name: null,
              price: null,
              quantity: 1,
              lineTotal: null,
              available: false,
            },
          ],
          total: 549,
          orderable: false,
        }),
      },
    });
    const user = userEvent.setup();
    renderApp('/');

    const drawer = await openCart(user, 'Корзина, 2 блюда');

    expect(drawer).toHaveTextContent('Блюдо убрали из меню');
    expect(drawer).toHaveTextContent('Некоторых блюд нет в наличии');
    expect(within(drawer).getByRole('button', { name: 'Оформить заказ' })).toBeDisabled();
  });
});

describe('promo code', () => {
  it('appliesPromoAndShowsDiscount', async () => {
    sessionStore.set(session);
    const fetchMock = stubApi({
      'GET /api/restaurants': { body: [pizzeria] },
      'GET /api/payments/wallet': { body: richWallet },
      'GET /api/cart': { body: cartOf() },
      'POST /api/cart/promo': {
        body: cartOf({ promoCode: 'TOMSK10', discount: '109.80', total: '988.20' }),
      },
    });
    const user = userEvent.setup();
    renderApp('/');

    const drawer = await openCart(user, 'Корзина, 2 блюда');
    await user.type(within(drawer).getByRole('textbox', { name: 'Промокод' }), 'tomsk10');
    await user.click(within(drawer).getByRole('button', { name: 'Применить' }));

    expect(await within(drawer).findByText('Скидка 109,8 ₽')).toBeInTheDocument();
    expect(drawer).toHaveTextContent('−109,8 ₽');
    expect(drawer).toHaveTextContent('988,2 ₽');
    const [, init] = callsTo(fetchMock, 'POST', '/api/cart/promo')[0] ?? [];
    expect(JSON.parse(String(init?.body))).toEqual({ code: 'TOMSK10' });
  });

  it('explainsRejectedPromoByCode', async () => {
    sessionStore.set(session);
    stubApi({
      'GET /api/restaurants': { body: [pizzeria] },
      'GET /api/payments/wallet': { body: richWallet },
      'GET /api/cart': { body: cartOf() },
      'POST /api/cart/promo': {
        status: 409,
        body: { detail: 'Promo code SUMMER expired', code: 'PROMO_EXPIRED' },
      },
    });
    const user = userEvent.setup();
    renderApp('/');

    const drawer = await openCart(user, 'Корзина, 2 блюда');
    await user.type(within(drawer).getByRole('textbox', { name: 'Промокод' }), 'summer');
    await user.click(within(drawer).getByRole('button', { name: 'Применить' }));

    expect(await within(drawer).findByRole('alert')).toHaveTextContent(
      'Срок действия промокода истёк',
    );
  });

  it('warnsWhenAppliedPromoStoppedWorking', async () => {
    sessionStore.set(session);
    stubApi({
      'GET /api/restaurants': { body: [pizzeria] },
      'GET /api/cart': { body: cartOf({ promoCode: 'BIG500', promoProblem: 'PROMO_MIN_AMOUNT' }) },
    });
    const user = userEvent.setup();
    renderApp('/');

    const drawer = await openCart(user, 'Корзина, 2 блюда');

    expect(within(drawer).getByRole('alert')).toHaveTextContent(
      'Сумма заказа слишком мала для этого промокода',
    );
  });
});

describe('checkout', () => {
  it('placesOrderWithIdempotencyKeyAndOpensOrderPage', async () => {
    sessionStore.set(session);
    const fetchMock = stubApi({
      'GET /api/restaurants': { body: [pizzeria] },
      'GET /api/restaurants/1': { body: pizzeria },
      'GET /api/payments/wallet': { body: richWallet },
      'GET /api/cart': { body: cartOf() },
      'POST /api/cart/checkout': { status: 201, body: placedOrder },
      'GET /api/orders/77': { body: placedOrder },
    });
    const user = userEvent.setup();
    renderApp('/');

    const drawer = await openCart(user, 'Корзина, 2 блюда');
    await user.click(within(drawer).getByRole('button', { name: 'Оформить заказ' }));

    expect(await screen.findByRole('heading', { name: 'Заказ №77' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Корзина пуста' })).toBeInTheDocument();
    const [, init] = callsTo(fetchMock, 'POST', '/api/cart/checkout')[0] ?? [];
    expect(new Headers(init?.headers).get('Idempotency-Key')).toMatch(/^[0-9a-f-]{36}$/);
  });

  it('explainsOutOfStockRejection', async () => {
    sessionStore.set(session);
    stubApi({
      'GET /api/restaurants': { body: [pizzeria] },
      'GET /api/payments/wallet': { body: richWallet },
      'GET /api/cart': { body: cartOf() },
      'POST /api/cart/checkout': {
        status: 409,
        body: { detail: 'Menu item 10 is not available', code: 'ITEM_UNAVAILABLE' },
      },
    });
    const user = userEvent.setup();
    renderApp('/');

    const drawer = await openCart(user, 'Корзина, 2 блюда');
    await user.click(within(drawer).getByRole('button', { name: 'Оформить заказ' }));

    expect(await within(drawer).findByRole('alert')).toHaveTextContent(
      '«Маргарита» — нет в наличии',
    );
  });
});

describe('wallet', () => {
  it('suggestsTopUpForMissingAmountBeforeCheckout', async () => {
    sessionStore.set(session);
    const fetchMock = stubApi({
      'GET /api/restaurants': { body: [pizzeria] },
      'GET /api/payments/wallet': {
        body: { ...richWallet, available: '100.50' },
      },
      'GET /api/cart': { body: cartOf() },
      'POST /api/payments/wallet/top-up': { body: { ...richWallet, available: '1098.50' } },
    });
    const user = userEvent.setup();
    renderApp('/');

    const drawer = await openCart(user, 'Корзина, 2 блюда');

    expect(await within(drawer).findByText(/не хватает/)).toHaveTextContent('998 ₽');
    expect(within(drawer).getByRole('button', { name: 'Оформить заказ' })).toBeDisabled();

    await user.click(within(drawer).getByRole('button', { name: /^Пополнить на 998\s₽$/ }));

    await waitFor(() => {
      expect(within(drawer).getByRole('button', { name: 'Оформить заказ' })).toBeEnabled();
    });
    const [, init] = callsTo(fetchMock, 'POST', '/api/payments/wallet/top-up')[0] ?? [];
    expect(JSON.parse(String(init?.body))).toEqual({ amount: 998 });
    expect(new Headers(init?.headers).get('Idempotency-Key')).toBeTruthy();
  });

  it('reusesIdempotencyKeyWhenTopUpIsRetriedAfterServerFailure', async () => {
    sessionStore.set(session);
    const fetchMock = stubApi({
      'GET /api/payments/wallet': { body: richWallet },
      'POST /api/payments/wallet/top-up': { status: 503, body: {} },
    });
    const user = userEvent.setup();
    renderApp('/wallet');

    expect(await screen.findByRole('region', { name: 'Баланс' })).toHaveTextContent('5 000 ₽');
    await user.click(screen.getByRole('button', { name: /^500\s₽$/ }));
    await user.click(screen.getByRole('button', { name: 'Пополнить' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Сервис временно недоступен');
    await user.click(screen.getByRole('button', { name: 'Пополнить' }));

    await waitFor(() => {
      expect(callsTo(fetchMock, 'POST', '/api/payments/wallet/top-up')).toHaveLength(2);
    });
    const keys = callsTo(fetchMock, 'POST', '/api/payments/wallet/top-up').map(([, init]) =>
      new Headers(init?.headers).get('Idempotency-Key'),
    );
    expect(keys[0]).toBeTruthy();
    expect(keys[1]).toBe(keys[0]);
  });
});
