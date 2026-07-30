import type { Restaurant } from '../api/restaurants';
import { formatClock } from './format';

export const restaurantTimeZone = 'Asia/Tomsk';

const clockParts = new Intl.DateTimeFormat('ru-RU', {
  timeZone: restaurantTimeZone,
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

function minutesOf(time: string | null) {
  if (!time) {
    return null;
  }
  const [hours, minutes] = time.split(':').map(Number);
  if (hours === undefined || minutes === undefined || Number.isNaN(hours + minutes)) {
    return null;
  }
  return hours * 60 + minutes;
}

export function minutesInRestaurantZone(now: Date) {
  const parts = clockParts.formatToParts(now);
  const hours = Number(parts.find((part) => part.type === 'hour')?.value ?? 0);
  const minutes = Number(parts.find((part) => part.type === 'minute')?.value ?? 0);
  return hours * 60 + minutes;
}

export function isOpenAt(restaurant: Restaurant, now: Date) {
  if (!restaurant.active) {
    return false;
  }
  const opens = minutesOf(restaurant.openTime);
  const closes = minutesOf(restaurant.closeTime);
  if (opens === null || closes === null || opens === closes) {
    return true;
  }
  const current = minutesInRestaurantZone(now);
  return opens < closes
    ? current >= opens && current < closes
    : current >= opens || current < closes;
}

export function closedBadge(restaurant: Restaurant, now: Date) {
  if (!restaurant.active) {
    return 'Не принимает заказы';
  }
  if (isOpenAt(restaurant, now)) {
    return null;
  }
  const opensAt = formatClock(restaurant.openTime);
  return opensAt ? `Закрыто до ${opensAt}` : 'Закрыто';
}

export function workingHoursLabel(restaurant: Restaurant) {
  const opensAt = formatClock(restaurant.openTime);
  const closesAt = formatClock(restaurant.closeTime);
  if (!opensAt || !closesAt) {
    return null;
  }
  return opensAt === closesAt ? 'Круглосуточно' : `${opensAt}–${closesAt}`;
}
