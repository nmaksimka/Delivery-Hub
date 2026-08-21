import { act, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { sessionStore } from './auth/sessionStore';
import { renderApp, stubApi } from './test/renderApp';
import type { Transport, TransportHandlers } from './tracking/connection';

const transports: { handlers: TransportHandlers | null; closed: boolean }[] = [];

vi.mock('./tracking/stompTransport', () => ({
  createStompTransport: (): Transport => {
    const entry: { handlers: TransportHandlers | null; closed: boolean } = {
      handlers: null,
      closed: false,
    };
    transports.push(entry);
    return {
      open: (handlers) => {
        entry.handlers = handlers;
      },
      close: () => {
        entry.closed = true;
      },
    };
  },
}));

vi.mock('./components/CourierMap', () => ({
  default: () => <div data-testid="map" />,
}));

const session = {
  accessToken: 'jwt-token',
  refreshToken: 'refresh-token',
  user: { id: 5, email: 'max@example.com', name: 'Максим', role: 'USER' },
};

const order = {
  id: 77,
  userId: 5,
  restaurantId: 1,
  status: 'IN_DELIVERY',
  totalAmount: '900.00',
  discount: '0.00',
  createdAt: '2026-08-01T12:00:00Z',
  orderItems: [],
};

const restaurant = {
  id: 1,
  name: 'Пицца на Ленина',
  active: true,
  latitude: 56.48,
  longitude: 84.95,
};

const flush = () => new Promise((resolve) => setTimeout(resolve, 0));

function position(etaSeconds: number) {
  return JSON.stringify({
    type: 'POSITION',
    orderId: 77,
    deliveryId: 1,
    status: 'IN_TRANSIT',
    courier: { latitude: 56.485, longitude: 84.96, heading: 30, ts: null },
    remainingMeters: 800,
    etaSeconds,
    estimatedArrival: null,
    at: null,
  });
}

describe('order tracking', () => {
  beforeEach(() => {
    transports.length = 0;
    sessionStore.set(session);
    stubApi({
      'GET /api/orders/77': { body: order },
      'GET /api/restaurants/1': { body: restaurant },
    });
  });

  it('showsEtaFromSocketAndClosesConnectionWhenDelivered', async () => {
    renderApp('/orders/77');
    expect(await screen.findByRole('heading', { name: 'Курьер в пути' })).toBeInTheDocument();
    await act(flush);
    expect(transports).toHaveLength(1);

    await act(async () => {
      transports[0]?.handlers?.onConnected();
      transports[0]?.handlers?.onMessage(position(300));
    });
    expect(screen.getByText('Приедет через 5 минут')).toBeInTheDocument();
    expect(screen.getByText('800 м')).toBeInTheDocument();

    await act(async () => {
      transports[0]?.handlers?.onMessage(
        JSON.stringify({ type: 'STATUS', orderId: 77, deliveryId: 1, status: 'DELIVERED' }),
      );
    });
    expect(transports[0]?.closed).toBe(true);
  });

  it('closesSocketWhenLeavingThePage', async () => {
    const router = renderApp('/orders/77');
    await screen.findByRole('heading', { name: 'Курьер в пути' });
    await act(flush);
    await act(async () => {
      await router.navigate('/orders');
    });
    expect(transports[0]?.closed).toBe(true);
  });
});
