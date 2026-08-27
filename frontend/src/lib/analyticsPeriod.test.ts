import { describe, expect, it } from 'vitest';
import { formatMinutes, lastDays } from './analyticsPeriod';

describe('lastDays', () => {
  it('includes today and counts back the requested number of days', () => {
    expect(lastDays(7, new Date(2026, 2, 10))).toEqual({ from: '2026-03-04', to: '2026-03-10' });
  });

  it('crosses month boundaries', () => {
    expect(lastDays(30, new Date(2026, 2, 5))).toEqual({ from: '2026-02-04', to: '2026-03-05' });
  });
});

describe('formatMinutes', () => {
  it('rounds to whole minutes', () => {
    expect(formatMinutes(34.6)).toBe('35 мин');
  });
});
