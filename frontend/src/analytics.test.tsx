import { screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { sessionStore } from './auth/sessionStore';
import { renderApp, stubApi } from './test/renderApp';

const admin = {
  accessToken: 'jwt-token',
  refreshToken: 'refresh-token',
  user: { id: 1, email: 'admin@example.com', name: 'Админ', role: 'ADMIN' },
};

const report = {
  from: '2026-03-01',
  to: '2026-03-30',
  restaurantId: null,
  revenueByDay: [
    {
      day: '2026-03-01',
      orders: 2,
      revenue: 160,
      cumulative: 160,
      movingAverage: 160,
      change: null,
    },
    {
      day: '2026-03-02',
      orders: 1,
      revenue: 120,
      cumulative: 280,
      movingAverage: 140,
      change: -40,
    },
  ],
  topDishes: [{ rank: 1, menuItemId: 5, name: 'Суши', quantity: 4, revenue: 160 }],
  deliveryTime: { day: null, orders: 3, averageMinutes: 40, medianMinutes: 40, p90Minutes: 48 },
  deliveryTimeByDay: [
    { day: '2026-03-01', orders: 2, averageMinutes: 40, medianMinutes: 40, p90Minutes: 48 },
  ],
};

describe('analytics dashboard', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe = vi.fn();
        unobserve = vi.fn();
        disconnect = vi.fn();
      },
    );
  });

  afterEach(() => {
    sessionStore.clear();
    vi.unstubAllGlobals();
  });

  it('shows totals and top dishes for an administrator', async () => {
    sessionStore.set(admin);
    stubApi({
      'GET /api/admin/analytics': { body: report },
      'GET /api/restaurants': { body: [] },
      'GET /api/payments/wallet': { body: { userId: 1, available: 0, held: 0, updatedAt: '' } },
    });

    renderApp('/admin/analytics');

    expect(await screen.findByRole('heading', { name: 'Топ блюд' })).toBeInTheDocument();
    expect(screen.getByText('Суши')).toBeInTheDocument();
    expect(screen.getByText('Доставка, медиана')).toBeInTheDocument();
  });

  it('refuses the page to a regular user', async () => {
    sessionStore.set({ ...admin, user: { ...admin.user, role: 'USER' } });
    stubApi({
      'GET /api/payments/wallet': { body: { userId: 1, available: 0, held: 0, updatedAt: '' } },
    });

    renderApp('/admin/analytics');

    expect(await screen.findByText('Раздел недоступен')).toBeInTheDocument();
  });
});
