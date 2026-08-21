import { describe, expect, it } from 'vitest';
import { reconnectDelay } from './backoff';

describe('reconnectDelay', () => {
  it('doublesDelayWithEveryAttempt', () => {
    expect([0, 1, 2, 3].map(reconnectDelay)).toEqual([1000, 2000, 4000, 8000]);
  });

  it('capsDelayAtThirtySeconds', () => {
    expect(reconnectDelay(5)).toBe(30000);
    expect(reconnectDelay(50)).toBe(30000);
  });

  it('treatsNegativeAttemptAsFirst', () => {
    expect(reconnectDelay(-3)).toBe(1000);
  });
});
