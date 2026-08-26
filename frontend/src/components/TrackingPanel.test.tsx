import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { Order } from '../api/orders';
import type { Restaurant } from '../api/restaurants';
import type { TrackingMessage } from '../tracking/message';
import type { TrackingSnapshot } from '../tracking/useOrderTracking';
import { TrackingPanel } from './TrackingPanel';

vi.mock('./CourierMap', () => ({
  default: (props: { courier: unknown }) => (
    <div data-testid="map">{props.courier ? 'с курьером' : 'без курьера'}</div>
  ),
}));

const order: Order = {
  id: 7,
  restaurantId: 1,
  status: 'IN_DELIVERY',
  cancellationReason: null,
  refundedAt: null,
  totalAmount: 900,
  discount: 0,
  promoCode: null,
  deliveryFee: 150,
  etaMinutes: 35,
  deliveryAddress: 'пр. Ленина, 1',
  deliveryLatitude: 56.49,
  deliveryLongitude: 84.97,
  weatherSurcharge: 0,
  createdAt: '2026-08-01T10:00:00Z',
  updatedAt: null,
  orderItems: [],
};

const restaurant: Restaurant = {
  id: 1,
  name: 'Пицца',
  address: null,
  phone: null,
  cuisine: null,
  city: null,
  imageUrl: null,
  openTime: null,
  closeTime: null,
  active: true,
  latitude: 56.48,
  longitude: 84.95,
  rating: null,
  ratingCount: 0,
};

const message: TrackingMessage = {
  type: 'POSITION',
  orderId: 7,
  deliveryId: 1,
  status: 'IN_TRANSIT',
  courier: { latitude: 56.485, longitude: 84.96, heading: 45, ts: null },
  remainingMeters: 1260,
  etaSeconds: 420,
  estimatedArrival: null,
  at: null,
};

describe('TrackingPanel', () => {
  it('showsEtaDistanceAndMapForLiveCourier', async () => {
    const tracking: TrackingSnapshot = {
      connection: 'live',
      latest: message,
      courier: message.courier,
    };
    render(<TrackingPanel order={order} restaurant={restaurant} tracking={tracking} />);
    expect(screen.getByText('Приедет через 7 минут')).toBeInTheDocument();
    expect(screen.getByText('1,3 км')).toBeInTheDocument();
    expect(screen.getByText('на связи')).toBeInTheDocument();
    expect(await screen.findByTestId('map')).toHaveTextContent('с курьером');
  });

  it('showsWeatherSurchargeWhenOrderHasIt', () => {
    const tracking: TrackingSnapshot = { connection: 'live', latest: null, courier: null };
    render(
      <TrackingPanel
        order={{ ...order, weatherSurcharge: 40 }}
        restaurant={restaurant}
        tracking={tracking}
      />,
    );
    expect(screen.getByText('Погодная надбавка')).toBeInTheDocument();
  });

  it('fallsBackToPolledEtaWhenSocketIsDown', () => {
    const tracking: TrackingSnapshot = { connection: 'reconnecting', latest: null, courier: null };
    render(<TrackingPanel order={order} restaurant={restaurant} tracking={tracking} />);
    expect(screen.getByText(/Ориентировочно доставим за 35 мин/)).toBeInTheDocument();
    expect(screen.getByText('нет сигнала')).toBeInTheDocument();
  });

  it('hidesMapWhenNoCoordinatesAreKnown', () => {
    const tracking: TrackingSnapshot = { connection: 'connecting', latest: null, courier: null };
    render(
      <TrackingPanel
        order={{ ...order, deliveryLatitude: null, deliveryLongitude: null }}
        restaurant={{ ...restaurant, latitude: null, longitude: null }}
        tracking={tracking}
      />,
    );
    expect(screen.queryByTestId('map')).not.toBeInTheDocument();
  });
});
