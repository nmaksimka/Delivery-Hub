import { describe, expect, it } from 'vitest';
import { trackingTopic, trackingUrl } from './stompTransport';

describe('stompTransport helpers', () => {
  it('buildsSecureSocketUrlForHttpsOrigin', () => {
    expect(trackingUrl('https://hub.example', 'abc')).toBe(
      'wss://hub.example/ws/tracking?access_token=abc',
    );
  });

  it('buildsPlainSocketUrlForHttpOriginKeepingPort', () => {
    expect(trackingUrl('http://localhost:5173', 'a b')).toBe(
      'ws://localhost:5173/ws/tracking?access_token=a+b',
    );
  });

  it('buildsOrderTopic', () => {
    expect(trackingTopic(42)).toBe('/topic/orders/42/tracking');
  });
});
