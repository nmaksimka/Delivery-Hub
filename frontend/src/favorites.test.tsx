import { screen, waitFor } from '@testing-library/react';
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
  address: null,
  phone: null,
  openTime: null,
  closeTime: null,
  active: true,
};
const sushiBar = { ...pizzeria, id: 2, name: 'Суши Томь' };

function callsTo(fetchMock: ReturnType<typeof stubApi>, method: string, path: string) {
  return fetchMock.mock.calls.filter(
    ([url, init]) => (init?.method ?? 'GET') === method && String(url).split('?')[0] === path,
  );
}

describe('favorites', () => {
  it('hidesHeartsAndFavoritesFilterForGuests', async () => {
    stubApi({ 'GET /api/restaurants': { body: [pizzeria] } });
    renderApp('/');

    expect(await screen.findByRole('heading', { name: 'Пицца на Ленина' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /в избранное/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Избранное' })).not.toBeInTheDocument();
  });

  it('marksFavoriteRestaurantAndAddsAnotherOne', async () => {
    sessionStore.set(session);
    let favorites = [1];
    const fetchMock = stubApi({
      'GET /api/restaurants': { body: [pizzeria, sushiBar] },
      'GET /api/users/me/favorites': { body: () => favorites },
      'PUT /api/users/me/favorites/2': {
        status: 204,
        body: () => {
          favorites = [2, 1];
          return null;
        },
      },
    });
    const user = userEvent.setup();
    renderApp('/');

    expect(
      await screen.findByRole('button', { name: 'Убрать из избранного: Пицца на Ленина' }),
    ).toHaveAttribute('aria-pressed', 'true');
    await user.click(screen.getByRole('button', { name: 'Добавить в избранное: Суши Томь' }));

    expect(
      await screen.findByRole('button', { name: 'Убрать из избранного: Суши Томь' }),
    ).toHaveAttribute('aria-pressed', 'true');
    expect(callsTo(fetchMock, 'PUT', '/api/users/me/favorites/2')).toHaveLength(1);
  });

  it('removesFavoriteRestaurant', async () => {
    sessionStore.set(session);
    const fetchMock = stubApi({
      'GET /api/restaurants': { body: [pizzeria] },
      'GET /api/users/me/favorites': { body: [1] },
      'DELETE /api/users/me/favorites/1': { status: 204, body: null },
    });
    const user = userEvent.setup();
    renderApp('/');

    await user.click(
      await screen.findByRole('button', { name: 'Убрать из избранного: Пицца на Ленина' }),
    );

    await waitFor(() => {
      expect(callsTo(fetchMock, 'DELETE', '/api/users/me/favorites/1')).toHaveLength(1);
    });
  });

  it('filtersCatalogByFavorites', async () => {
    sessionStore.set(session);
    stubApi({
      'GET /api/restaurants': { body: [pizzeria, sushiBar] },
      'GET /api/users/me/favorites': { body: [2] },
    });
    const user = userEvent.setup();
    renderApp('/');

    await user.click(await screen.findByRole('button', { name: 'Избранное' }));

    expect(screen.getByRole('heading', { name: 'Суши Томь' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Пицца на Ленина' })).not.toBeInTheDocument();
  });
});
