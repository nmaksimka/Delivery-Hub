import { describe, expect, it } from 'vitest';
import { isExpiring } from './accessToken';

function tokenWithExp(exp: number) {
  return `h.${btoa(JSON.stringify({ exp }))}.s`;
}

describe('isExpiring', () => {
  it('detectsExpiredToken', () => {
    expect(isExpiring(tokenWithExp(1000), 2000)).toBe(true);
  });

  it('detectsTokenAboutToExpire', () => {
    expect(isExpiring(tokenWithExp(2010), 2000)).toBe(true);
  });

  it('acceptsFreshToken', () => {
    expect(isExpiring(tokenWithExp(5000), 2000)).toBe(false);
  });

  it('ignoresTokenWithoutReadableExpiry', () => {
    expect(isExpiring('opaque', 2000)).toBe(false);
    expect(isExpiring('a.!!!.c', 2000)).toBe(false);
  });
});
