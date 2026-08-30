import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { sessionStore } from './auth/sessionStore';
import { renderApp, stubApi } from './test/renderApp';

const customer = {
  accessToken: 'jwt-token',
  refreshToken: 'refresh-token',
  user: { id: 4, email: 'a@example.com', name: 'Анна', role: 'USER' },
};

const wallet = { userId: 4, available: 0, held: 0, updatedAt: '2026-03-01T00:00:00Z' };

function notification(read: boolean) {
  return {
    id: 'n-1',
    type: 'ORDER_CREATED',
    orderId: 12,
    title: 'Заказ принят',
    message: 'Ждём подтверждения ресторана',
    read,
    createdAt: '2026-03-01T10:00:00Z',
  };
}

afterEach(() => {
  sessionStore.clear();
  vi.unstubAllGlobals();
});

describe('notification bell', () => {
  it('shows the unread count and marks a notification as read', async () => {
    sessionStore.set(customer);
    let read = false;
    stubApi({
      'GET /api/payments/wallet': { body: wallet },
      'GET /api/notifications': {
        body: () => ({ items: [notification(read)], unread: read ? 0 : 1 }),
      },
      'POST /api/notifications/n-1/read': {
        body: () => {
          read = true;
          return notification(true);
        },
      },
    });

    renderApp('/login');

    const bell = await screen.findByRole('button', { name: 'Уведомления, непрочитанных: 1' });
    await userEvent.click(bell);
    expect(await screen.findByText('Заказ принят')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Прочитано' }));

    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Уведомления' })).toBeInTheDocument(),
    );
    expect(screen.queryByRole('button', { name: 'Прочитано' })).not.toBeInTheDocument();
  });
});
