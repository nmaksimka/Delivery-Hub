import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { sessionStore } from './auth/sessionStore';
import { renderApp, stubApi } from './test/renderApp';

const owner = {
  accessToken: 'jwt-token',
  refreshToken: 'refresh-token',
  user: { id: 9, email: 'owner@example.com', name: 'Владелец', role: 'RESTAURANT_OWNER' },
};

const restaurant = {
  id: 3,
  name: 'Суши Томь',
  active: true,
  openTime: '10:00:00',
  closeTime: '22:00:00',
  rating: 4.5,
  ratingCount: 8,
};

const wallet = { userId: 9, available: 0, held: 0, updatedAt: '2026-03-01T00:00:00Z' };

function menuItem(available: boolean) {
  return {
    id: 11,
    name: 'Ролл Филадельфия',
    price: 450,
    category: 'Роллы',
    available,
    section: 'DISH',
  };
}

afterEach(() => {
  sessionStore.clear();
  vi.unstubAllGlobals();
});

describe('restaurant cabinet', () => {
  it('lists restaurants of the owner with their rating', async () => {
    sessionStore.set(owner);
    stubApi({
      'GET /api/payments/wallet': { body: wallet },
      'GET /api/cabinet/restaurants': { body: [restaurant] },
    });

    renderApp('/cabinet');

    expect(await screen.findByText('Суши Томь')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Открыть' })).toHaveAttribute(
      'href',
      '/cabinet/3/menu',
    );
  });

  it('puts a menu item on the stop list', async () => {
    sessionStore.set(owner);
    let stopped = false;
    const fetchMock = stubApi({
      'GET /api/payments/wallet': { body: wallet },
      'GET /api/cabinet/restaurants/3': { body: restaurant },
      'GET /api/cabinet/restaurants/3/menu': { body: () => [menuItem(!stopped)] },
      'PUT /api/cabinet/restaurants/3/stop-list/11': {
        body: () => {
          stopped = true;
          return menuItem(false);
        },
      },
    });

    renderApp('/cabinet/3/menu');

    const row = (await screen.findByText('Ролл Филадельфия')).closest('li');
    expect(row).not.toBeNull();
    await userEvent.click(within(row as HTMLElement).getByRole('button', { name: 'В стоп-лист' }));

    expect(await screen.findByText(/в стоп-листе/)).toBeInTheDocument();
    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/cabinet/restaurants/3/stop-list/11',
        expect.objectContaining({ method: 'PUT' }),
      );
    });
  });

  it('shows orders of the restaurant', async () => {
    sessionStore.set(owner);
    stubApi({
      'GET /api/payments/wallet': { body: wallet },
      'GET /api/cabinet/restaurants/3': { body: restaurant },
      'GET /api/cabinet/orders': {
        body: {
          content: [
            {
              id: 77,
              restaurantId: 3,
              status: 'CONFIRMED',
              totalAmount: '900.00',
              createdAt: '2026-03-01T10:00:00Z',
              orderItems: [
                {
                  id: 1,
                  menuItemId: 11,
                  menuItemName: 'Ролл Филадельфия',
                  quantity: 2,
                  pricePerUnit: '450.00',
                },
              ],
            },
          ],
          number: 0,
          size: 15,
          totalElements: 1,
          totalPages: 1,
        },
      },
    });

    renderApp('/cabinet/3/orders');

    expect(await screen.findByText(/Заказ № 77/)).toBeInTheDocument();
    expect(screen.getByText(/Ролл Филадельфия × 2/)).toBeInTheDocument();
  });

  it('hands a confirmed order to the courier', async () => {
    sessionStore.set(owner);
    let status = 'CONFIRMED';
    const fetchMock = stubApi({
      'GET /api/payments/wallet': { body: wallet },
      'GET /api/cabinet/restaurants/3': { body: restaurant },
      'GET /api/cabinet/orders': {
        body: () => ({
          content: [
            {
              id: 77,
              restaurantId: 3,
              status,
              totalAmount: '900.00',
              createdAt: '2026-03-01T10:00:00Z',
              orderItems: [],
            },
          ],
          number: 0,
          size: 15,
          totalElements: 1,
          totalPages: 1,
        }),
      },
      'PATCH /api/cabinet/orders/77/status': {
        body: () => {
          status = 'IN_DELIVERY';
          return {
            id: 77,
            restaurantId: 3,
            status,
            totalAmount: '900.00',
            createdAt: '2026-03-01T10:00:00Z',
            orderItems: [],
          };
        },
      },
    });

    renderApp('/cabinet/3/orders');

    await userEvent.click(await screen.findByRole('button', { name: 'Передать курьеру' }));

    expect(await screen.findByText(/В пути/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Передать курьеру' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Отменить' })).not.toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/cabinet/orders/77/status',
      expect.objectContaining({ method: 'PATCH', body: JSON.stringify({ status: 'IN_DELIVERY' }) }),
    );
  });

  it('uploads a photo of a menu item as multipart', async () => {
    sessionStore.set(owner);
    const fetchMock = stubApi({
      'GET /api/payments/wallet': { body: wallet },
      'GET /api/cabinet/restaurants/3': { body: restaurant },
      'GET /api/cabinet/restaurants/3/menu': { body: [menuItem(true)] },
      'PUT /api/cabinet/restaurants/3/menu/11/image': {
        body: { ...menuItem(true), imageUrl: '/media/11.png' },
      },
    });

    renderApp('/cabinet/3/menu');

    const input = await screen.findByLabelText('Фото блюда Ролл Филадельфия');
    const file = new File(['png'], 'roll.png', { type: 'image/png' });
    await userEvent.upload(input, file);

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/cabinet/restaurants/3/menu/11/image',
        expect.objectContaining({ method: 'PUT', body: expect.any(FormData) }),
      );
    });
    const body = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/image'))?.[1]?.body;
    expect((body as FormData).get('file')).toBe(file);
  });

  it('keeps the cabinet closed for customers', async () => {
    sessionStore.set({ ...owner, user: { ...owner.user, role: 'USER' } });
    stubApi({ 'GET /api/payments/wallet': { body: wallet } });

    renderApp('/cabinet');

    expect(await screen.findByText('Раздел недоступен')).toBeInTheDocument();
  });
});
