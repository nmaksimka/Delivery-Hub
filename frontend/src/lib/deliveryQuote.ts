import type { DeliveryQuote } from '../api/cart';
import { formatPrice } from './format';

const weatherLabels: Record<string, string> = {
  RAIN: 'Дождь',
  SNOW: 'Снег',
  FROST: 'Сильный мороз',
};

export function weatherNote(quote: DeliveryQuote) {
  const label = weatherLabels[quote.weather.condition];
  if (!label || (quote.weather.surcharge <= 0 && quote.weather.extraMinutes <= 0)) {
    return null;
  }
  const parts = [];
  if (quote.weather.surcharge > 0) {
    parts.push(`+${formatPrice(quote.weather.surcharge)}`);
  }
  if (quote.weather.extraMinutes > 0) {
    parts.push(`+${quote.weather.extraMinutes} мин`);
  }
  return `${label}: ${parts.join(', ')}`;
}
