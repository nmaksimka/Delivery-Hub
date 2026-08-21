import { describe, expect, it } from 'vitest';
import { parseTrackingMessage } from './message';

describe('parseTrackingMessage', () => {
  it('parsesPositionMessageWithCourier', () => {
    const message = parseTrackingMessage(
      JSON.stringify({
        type: 'POSITION',
        orderId: 7,
        deliveryId: 3,
        status: 'IN_PROGRESS',
        courier: { latitude: 56.48, longitude: 84.95, heading: 90, ts: '2026-08-01T10:00:00Z' },
        remainingMeters: 1200,
        etaSeconds: 300,
        estimatedArrival: '2026-08-01T10:05:00Z',
        at: '2026-08-01T10:00:00Z',
      }),
    );
    expect(message?.type).toBe('POSITION');
    expect(message?.courier?.heading).toBe(90);
    expect(message?.etaSeconds).toBe(300);
  });

  it('parsesStatusMessageWithoutCourier', () => {
    const message = parseTrackingMessage(
      JSON.stringify({
        type: 'STATUS',
        orderId: 7,
        deliveryId: 3,
        status: 'DELIVERED',
        courier: null,
        remainingMeters: null,
        etaSeconds: null,
        estimatedArrival: null,
        at: null,
      }),
    );
    expect(message).toMatchObject({ type: 'STATUS', courier: null, etaSeconds: null });
  });

  it('fillsMissingOptionalFieldsWithDefaults', () => {
    const message = parseTrackingMessage(
      JSON.stringify({ type: 'STATUS', orderId: 1, deliveryId: 2, status: 'ASSIGNED' }),
    );
    expect(message).toMatchObject({ courier: null, remainingMeters: null, estimatedArrival: null });
  });

  it('returnsNullForBrokenJson', () => {
    expect(parseTrackingMessage('{oops')).toBeNull();
  });

  it('returnsNullForUnknownMessageType', () => {
    expect(
      parseTrackingMessage(
        JSON.stringify({ type: 'PING', orderId: 1, deliveryId: 1, status: 'X' }),
      ),
    ).toBeNull();
  });
});
