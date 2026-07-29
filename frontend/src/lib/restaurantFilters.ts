import type { Restaurant } from '../api/restaurants';
import { isOpenAt } from './workingHours';

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
  { id: 'pizza', label: 'Пицца', stems: ['пицц', 'pizz', 'итальян'] },
  { id: 'sushi', label: 'Суши и роллы', stems: ['суши', 'ролл', 'sushi', 'японск'] },
  { id: 'burger', label: 'Бургеры', stems: ['бургер', 'burger', 'американ'] },
  { id: 'grill', label: 'Шашлык и гриль', stems: ['шашлы', 'гриль', 'мангал', 'grill'] },
  {
    id: 'asia',
    label: 'Азия',
    stems: ['вок', 'wok', 'рамен', 'лапш', 'азия', 'китайск', 'тайск', 'вьетнам'],
  },
  { id: 'coffee', label: 'Кофе и выпечка', stems: ['коф', 'пекарн', 'булоч', 'coffee'] },
];

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
    const cuisine = restaurant.cuisine?.toLowerCase() ?? '';
    const searchable = [name, cuisine, restaurant.address?.toLowerCase() ?? ''];

    if (query && !searchable.some((text) => text.includes(query))) {
      return false;
    }
    if (criteria.filterId === openFilterId) {
      return isOpenAt(restaurant, criteria.now);
    }
    if (filter?.stems) {
      return filter.stems.some((stem) => name.includes(stem) || cuisine.includes(stem));
    }
    return true;
  });
}
