import type { Restaurant } from '../api/restaurants';

export type QuickFilter = {
  id: string;
  label: string;
  stems?: string[];
};

export const allFilterId = 'all';
export const openFilterId = 'open';

export const quickFilters: QuickFilter[] = [
  { id: allFilterId, label: 'Все' },
  { id: openFilterId, label: 'Открыто сейчас' },
  { id: 'pizza', label: 'Пицца', stems: ['пицц', 'pizz'] },
  { id: 'sushi', label: 'Суши и роллы', stems: ['суши', 'ролл', 'sushi'] },
  { id: 'burger', label: 'Бургеры', stems: ['бургер', 'burger'] },
  { id: 'grill', label: 'Шашлык и гриль', stems: ['шашлы', 'гриль', 'мангал', 'grill'] },
  { id: 'asia', label: 'Азия', stems: ['вок', 'wok', 'рамен', 'лапш', 'азия'] },
  { id: 'coffee', label: 'Кофе и выпечка', stems: ['коф', 'пекарн', 'булоч', 'coffee'] },
];

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

export function isOpenAt(restaurant: Restaurant, now: Date) {
  if (!restaurant.active) {
    return false;
  }
  const opens = minutesOf(restaurant.openTime);
  const closes = minutesOf(restaurant.closeTime);
  if (opens === null || closes === null || opens === closes) {
    return true;
  }
  const current = now.getHours() * 60 + now.getMinutes();
  return opens < closes
    ? current >= opens && current < closes
    : current >= opens || current < closes;
}

type Criteria = {
  query: string;
  filterId: string;
  now: Date;
};

export function filterRestaurants(restaurants: Restaurant[], criteria: Criteria) {
  const query = criteria.query.trim().toLowerCase();
  const filter = quickFilters.find((candidate) => candidate.id === criteria.filterId);

  return restaurants.filter((restaurant) => {
    const name = restaurant.name.toLowerCase();
    const address = restaurant.address?.toLowerCase() ?? '';

    if (query && !name.includes(query) && !address.includes(query)) {
      return false;
    }
    if (criteria.filterId === openFilterId) {
      return isOpenAt(restaurant, criteria.now);
    }
    if (filter?.stems) {
      return filter.stems.some((stem) => name.includes(stem));
    }
    return true;
  });
}
