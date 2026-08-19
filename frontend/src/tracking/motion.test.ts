import { describe, expect, it } from 'vitest';
import { interpolate, shortestTurn } from './motion';

const start = { latitude: 56, longitude: 84, heading: 0 };
const end = { latitude: 57, longitude: 86, heading: 90 };

describe('interpolate', () => {
  it('returnsStartAtZeroAndEndAtOne', () => {
    expect(interpolate(start, end, 0)).toEqual(start);
    expect(interpolate(start, end, 1)).toEqual(end);
  });

  it('movesLinearlyBetweenPoints', () => {
    expect(interpolate(start, end, 0.5)).toEqual({ latitude: 56.5, longitude: 85, heading: 45 });
  });

  it('clampsProgressOutsideRange', () => {
    expect(interpolate(start, end, 3)).toEqual(end);
    expect(interpolate(start, end, -1)).toEqual(start);
  });

  it('turnsThroughNorthInsteadOfFullCircle', () => {
    const left = interpolate({ ...start, heading: 350 }, { ...end, heading: 10 }, 0.5);
    expect(left.heading).toBe(0);
  });
});

describe('shortestTurn', () => {
  it('picksShorterDirection', () => {
    expect(shortestTurn(350, 10)).toBe(20);
    expect(shortestTurn(10, 350)).toBe(-20);
    expect(shortestTurn(0, 90)).toBe(90);
  });
});
