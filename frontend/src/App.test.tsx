import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { sessionStore } from './auth/sessionStore';
import { renderApp, stubApi } from './test/renderApp';

const pizzeria = {
  id: 1,
  name: 'Пицца на Ленина',
  address: 'пр. Ленина, 36',
  phone: '+73822000000',
  openTime: null,
  closeTime: null,
  active: true,
};

const sushiBar = { ...pizzeria, id: 2, name: 'Суши Томь', address: 'ул. Учебная, 8' };

const menu = [
  { id: 10, name: 'Маргарита', description: 'Томаты и моцарелла', price: 549, category: 'Пицца' },
  { id: 11, name: 'Морс', description: null, price: 120, category: 'Напитки' },
].map((item) => ({ ...item, available: true, restaurantId: 1 }));

const session = {
  accessToken: 'jwt-token',
  refreshToken: 'refresh-token',
  user: { id: 5, email: 'max@example.com', name: 'Максим', role: 'USER' },
};

describe('home page', () => {
  it('filtersRestaurantGridBySearchQuery', async () => {
    stubApi({ 'GET /api/restaurants': { body: [pizzeria, sushiBar] } });
    const user = userEvent.setup();
    renderApp('/');

    expect(await screen.findByRole('heading', { name: 'Пицца на Ленина' })).toBeInTheDocument();
    expect(screen.getByText('2 места')).toBeInTheDocument();

    await user.type(screen.getByRole('searchbox', { name: 'Поиск ресторана' }), 'суши');

    expect(screen.queryByRole('heading', { name: 'Пицца на Ленина' })).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Суши Томь' })).toBeInTheDocument();
  });

  it('offersRetryWhenRestaurantsFailToLoad', async () => {
    stubApi({ 'GET /api/restaurants': { status: 503, body: {} } });
    renderApp('/');

    expect(await screen.findByRole('alert')).toHaveTextContent('Сервис временно недоступен');
    expect(screen.getByRole('button', { name: 'Повторить' })).toBeInTheDocument();
  });
});

describe('restaurant page', () => {
  it('addsDishToPersistedCartAndSendsGuestToLoginOnCheckout', async () => {
    stubApi({
      'GET /api/restaurants/1': { body: pizzeria },
      'GET /api/restaurants/1/menu': { body: menu },
    });
    const user = userEvent.setup();
    renderApp('/restaurants/1');

    expect(await screen.findByRole('heading', { level: 2, name: 'Напитки' })).toBeInTheDocument();
    await user.click(await screen.findByRole('button', { name: 'Добавить в корзину: Маргарита' }));
    await user.click(screen.getByRole('button', { name: 'Добавить ещё одну порцию: Маргарита' }));

    const cartButton = screen.getByRole('button', { name: 'Корзина, 2 блюда' });
    expect(localStorage.getItem('deliveryhub.cart')).toContain('"quantity":2');

    await user.click(cartButton);
    const drawer = screen.getByRole('dialog', { name: 'Корзина' });
    expect(drawer).toHaveTextContent('1 098 ₽');

    await user.click(screen.getByRole('button', { name: 'Войти и оформить' }));

    expect(await screen.findByRole('heading', { name: 'С возвращением' })).toBeInTheDocument();
  });

  it('placesOrderForSignedInUserAndClearsCart', async () => {
    sessionStore.set(session);
    localStorage.setItem(
      'deliveryhub.cart',
      JSON.stringify({
        restaurant: { id: 1, name: pizzeria.name },
        lines: [{ menuItemId: 10, name: 'Маргарита', price: 549, quantity: 2 }],
      }),
    );
    const order = {
      id: 77,
      userId: 5,
      restaurantId: 1,
      status: 'CREATED',
      totalAmount: 1098,
      createdAt: '2026-06-15T12:00:00Z',
      orderItems: [
        { id: 1, menuItemId: 10, menuItemName: 'Маргарита', quantity: 2, pricePerUnit: 549 },
      ],
    };
    const fetchMock = stubApi({
      'GET /api/restaurants': { body: [pizzeria] },
      'POST /api/orders': { status: 201, body: order },
      'GET /api/orders': {
        body: { content: [order], number: 0, size: 10, totalElements: 1, totalPages: 1 },
      },
    });
    const user = userEvent.setup();
    renderApp('/');

    await user.click(screen.getByRole('button', { name: 'Корзина, 2 блюда' }));
    await user.click(screen.getByRole('button', { name: 'Оформить заказ' }));

    expect(await screen.findByRole('status')).toHaveTextContent('Заказ №77 принят');
    expect(screen.getByRole('button', { name: 'Корзина пуста' })).toBeInTheDocument();

    const post = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST');
    expect(JSON.parse(String(post?.[1]?.body))).toEqual({
      restaurantId: 1,
      orderItemsRequest: [{ menuItemId: 10, quantity: 2 }],
    });
  });
});

describe('catalog details', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  const scheduled = { ...pizzeria, openTime: '10:00:00', closeTime: '22:00:00' };

  it('showsClosedBadgeUntilOpeningTimeInTomsk', async () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-06-15T16:30:00Z'));
    stubApi({
      'GET /api/restaurants/1': { body: { ...scheduled, cuisine: 'Итальянская', city: 'Томск' } },
      'GET /api/restaurants/1/menu': { body: menu },
    });
    renderApp('/restaurants/1');

    expect(await screen.findByText('Закрыто до 10:00')).toBeInTheDocument();
    expect(screen.getByText('Итальянская')).toBeInTheDocument();
    expect(screen.getByText('пр. Ленина, 36, Томск')).toBeInTheDocument();
    expect(
      await screen.findByRole('button', { name: 'Добавить в корзину: Маргарита' }),
    ).toBeDisabled();
  });

  it('keepsStopListDishVisibleButNotOrderable', async () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-06-15T05:00:00Z'));
    const stopList = [{ ...menu[0], available: false }, menu[1]];
    const fetchMock = stubApi({
      'GET /api/restaurants/1': { body: scheduled },
      'GET /api/restaurants/1/menu': { body: stopList },
    });
    renderApp('/restaurants/1');

    expect(await screen.findByText('Нет в наличии')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Маргарита' })).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: 'Добавить в корзину: Маргарита' }),
    ).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Добавить в корзину: Морс' })).toBeEnabled();
    const menuCall = fetchMock.mock.calls.find(([url]) => String(url).includes('/menu'));
    expect(String(menuCall?.[0])).toContain('availableOnly=false');
  });

  it('showsProductNutritionFromOpenFoodFacts', async () => {
    const product = {
      id: 30,
      name: 'Йогурт',
      description: 'Danone, 120 г',
      price: 89,
      category: 'Молочное',
      available: true,
      imageUrl: 'https://images.openfoodfacts.org/yogurt.jpg',
      section: 'PRODUCT',
      nutrition: {
        energyKcal: 72.5,
        proteinGrams: 3.2,
        fatGrams: 2.5,
        carbsGrams: null,
        nutriScore: 'b',
      },
      restaurantId: 1,
    };
    stubApi({
      'GET /api/restaurants/1': { body: pizzeria },
      'GET /api/restaurants/1/menu': { body: [product] },
    });
    renderApp('/restaurants/1');

    const nutrition = await screen.findByLabelText('Пищевая ценность на 100 г');
    expect(nutrition).toHaveTextContent('Ккал72,5');
    expect(nutrition).toHaveTextContent('Белки3,2 г');
    expect(nutrition).not.toHaveTextContent('Углеводы');
    expect(nutrition).toHaveTextContent('Nutri-ScoreB');
  });

  it('explainsOutOfStockRejectionWhenPlacingOrder', async () => {
    sessionStore.set(session);
    localStorage.setItem(
      'deliveryhub.cart',
      JSON.stringify({
        restaurant: { id: 1, name: pizzeria.name },
        lines: [{ menuItemId: 10, name: 'Маргарита', price: 549, quantity: 1 }],
      }),
    );
    stubApi({
      'GET /api/restaurants': { body: [pizzeria] },
      'POST /api/orders': { status: 409, body: { detail: 'Menu item 10 is not available' } },
    });
    const user = userEvent.setup();
    renderApp('/');

    await user.click(await screen.findByRole('button', { name: 'Корзина, 1 блюдо' }));
    await user.click(screen.getByRole('button', { name: 'Оформить заказ' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '«Маргарита» — нет в наличии. Уберите из корзины и оформите заказ снова',
    );
  });
});

describe('protected routes', () => {
  it('redirectsGuestFromOrdersToLogin', async () => {
    stubApi({});
    renderApp('/orders');

    expect(await screen.findByRole('heading', { name: 'С возвращением' })).toBeInTheDocument();
  });

  it('logsOutAndReturnsToLoginWhenTokenIsRejected', async () => {
    sessionStore.set(session);
    stubApi({ 'GET /api/orders': { status: 401, body: { detail: 'Token expired' } } });
    renderApp('/orders');

    expect(await screen.findByRole('heading', { name: 'С возвращением' })).toBeInTheDocument();
    await waitFor(() => {
      expect(sessionStore.get()).toBeNull();
    });
  });

  it('revokesRefreshTokenOnLogout', async () => {
    sessionStore.set(session);
    const fetchMock = stubApi({
      'GET /api/restaurants': { body: [] },
      'POST /api/auth/logout': { status: 204, body: null },
    });
    const user = userEvent.setup();
    renderApp('/');

    await user.click(await screen.findByRole('button', { name: 'Выйти из аккаунта Максим' }));

    expect(sessionStore.get()).toBeNull();
    const logout = fetchMock.mock.calls.find(([url]) => url === '/api/auth/logout');
    expect(JSON.parse(String(logout?.[1]?.body))).toEqual({ refreshToken: 'refresh-token' });
  });

  it('showsNotFoundPageForUnknownAddress', async () => {
    stubApi({});
    renderApp('/no-such-page');

    expect(await screen.findByRole('heading', { name: 'Такой страницы нет' })).toBeInTheDocument();
  });
});
