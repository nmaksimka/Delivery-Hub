import { describe, expect, it } from 'vitest';
import { ApiError } from '../api/http';
import { shortageOf, topUpErrorMessage } from './walletErrors';

describe('shortageOf', () => {
  it('roundsMissingAmountUpToWholeRubles', () => {
    expect(shortageOf(1098, 100.5)).toBe(998);
    expect(shortageOf(100.1, 100)).toBe(1);
  });

  it('isZeroWhenBalanceCoversTotal', () => {
    expect(shortageOf(1098, 1098)).toBe(0);
    expect(shortageOf(0.3, 0.1 + 0.2)).toBe(0);
    expect(shortageOf(500, 5000)).toBe(0);
  });
});

describe('topUpErrorMessage', () => {
  it('explainsWalletLimit', () => {
    const error = new ApiError(
      422,
      'x',
      'Demo wallet can hold at most 100000.00',
      'WALLET_LIMIT_EXCEEDED',
    );

    expect(topUpErrorMessage(error).replace(/\s/g, ' ')).toBe(
      'На демо-кошельке может лежать не больше 100 000 ₽',
    );
  });
});
