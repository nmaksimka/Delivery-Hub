import { ApiError, errorMessage } from '../api/http';
import { maxTopUp, minTopUp } from '../api/wallet';
import { formatPrice } from './format';

export function topUpErrorMessage(error: unknown) {
  if (error instanceof ApiError) {
    switch (error.code) {
      case 'WALLET_LIMIT_EXCEEDED':
        return `На демо-кошельке может лежать не больше ${formatPrice(100_000)}`;
      case 'VALIDATION_FAILED':
      case 'INVALID_AMOUNT':
        return `Сумма пополнения — от ${formatPrice(minTopUp)} до ${formatPrice(maxTopUp)}`;
      case 'IDEMPOTENCY_KEY_REUSED':
        return 'Это пополнение уже обрабатывается, обновите страницу';
      default:
        break;
    }
  }
  return errorMessage(error);
}

export function shortageOf(total: number, available: number) {
  const missingKopecks = Math.round(total * 100) - Math.round(available * 100);
  return missingKopecks > 0 ? Math.max(Math.ceil(missingKopecks / 100), minTopUp) : 0;
}
