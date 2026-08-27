import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { sessionStore } from './auth/sessionStore';
import { renderApp, stubApi } from './test/renderApp';

const admin = {
  accessToken: 'jwt-token',
  refreshToken: 'refresh-token',
  user: { id: 1, email: 'admin@example.com', name: 'Админ', role: 'ADMIN' },
};

const wallet = { userId: 1, available: 0, held: 0, updatedAt: '2026-03-01T00:00:00Z' };

const courier = {
  id: 4,
  userId: 20,
  name: 'Иван Курьеров',
  phone: '+7 913 000-00-00',
  status: 'AVAILABLE',
  rating: 4.8,
  ratingCount: 5,
};

afterEach(() => {
  sessionStore.clear();
  vi.unstubAllGlobals();
});

describe('admin panel', () => {
  it('lists couriers with their status', async () => {
    sessionStore.set(admin);
    stubApi({
      'GET /api/payments/wallet': { body: wallet },
      'GET /api/admin/couriers': {
        body: { content: [courier], number: 0, size: 15, totalElements: 1, totalPages: 1 },
      },
    });

    renderApp('/admin/couriers');

    expect(await screen.findByText('Иван Курьеров')).toBeInTheDocument();
    expect(screen.getByText('Свободен')).toBeInTheDocument();
  });

  it('registers a courier from the form', async () => {
    sessionStore.set(admin);
    const fetchMock = stubApi({
      'GET /api/payments/wallet': { body: wallet },
      'GET /api/admin/couriers': {
        body: { content: [], number: 0, size: 15, totalElements: 0, totalPages: 0 },
      },
      'POST /api/admin/couriers': { body: courier, status: 201 },
    });

    renderApp('/admin/couriers');

    await userEvent.type(await screen.findByLabelText('ID пользователя'), '20');
    await userEvent.type(screen.getByLabelText('Имя'), 'Иван Курьеров');
    await userEvent.type(screen.getByLabelText('Телефон'), '+7 913 000-00-00');
    await userEvent.click(screen.getByRole('button', { name: 'Добавить' }));

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/admin/couriers',
        expect.objectContaining({ method: 'POST' }),
      );
    });
  });

  it('starts a meal import and shows its progress', async () => {
    sessionStore.set(admin);
    const job = {
      id: 'a1',
      source: 'MEALDB',
      status: 'COMPLETED',
      total: 10,
      processed: 10,
      progressPercent: 100,
      restaurantsCreated: 2,
      itemsCreated: 9,
      itemsUpdated: 1,
      error: null,
      createdAt: '2026-03-01T10:00:00Z',
      finishedAt: '2026-03-01T10:01:00Z',
    };
    const fetchMock = stubApi({
      'GET /api/payments/wallet': { body: wallet },
      'GET /api/admin/imports': { body: [job] },
      'POST /api/admin/imports/meals': { body: job, status: 202 },
    });

    renderApp('/admin/imports');

    expect(await screen.findByText('Блюда (TheMealDB)')).toBeInTheDocument();
    expect(screen.getByRole('progressbar', { name: 'Ход импорта' })).toHaveAttribute(
      'aria-valuenow',
      '100',
    );
    await userEvent.click(screen.getByRole('button', { name: 'Импортировать блюда' }));

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/admin/imports/meals',
        expect.objectContaining({ method: 'POST' }),
      );
    });
  });
});
