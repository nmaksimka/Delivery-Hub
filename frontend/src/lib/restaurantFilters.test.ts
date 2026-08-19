import { describe, expect, it } from 'vitest';
import type { Restaurant } from '../api/restaurants';
import { allFilterId, filterRestaurants, openFilterId } from './restaurantFilters';

function restaurant(overrides: Partial<Restaurant>): Restaurant {
  return {
    id: 1,
    name: 'Пицца на Ленина',
    address: 'пр. Ленина, 36',
    phone: null,
    cuisine: null,
    city: 'Томск',
    imageUrl: null,
    openTime: '10:00:00',
    closeTime: '22:00:00',
    active: true,
    latitude: null,
    longitude: null,
    ...overrides,
  };
}

function at(hours: number, minutes = 0) {
  return new Date(Date.UTC(2026, 5, 15, hours - 7, minutes));
}

describe('filterRestaurants', () => {
  const pizzeria = restaurant({});
  const sushiBar = restaurant({
    id: 2,
    name: 'Суши Томь',
    address: 'ул. Красноармейская, 101',
    openTime: '18:00:00',
    closeTime: '23:00:00',
  });
  const all = [pizzeria, sushiBar];

  it('matchesQueryAgainstNameAndAddressIgnoringCase', () => {
    const byName = filterRestaurants(all, { query: ' СУШИ ', filterId: allFilterId, now: at(12) });
    const byStreet = filterRestaurants(all, {
      query: 'ленина',
      filterId: allFilterId,
      now: at(12),
    });

    expect(byName).toEqual([sushiBar]);
    expect(byStreet).toEqual([pizzeria]);
  });

  it('keepsOnlyOpenPlacesForOpenFilter', () => {
    expect(filterRestaurants(all, { query: '', filterId: openFilterId, now: at(12) })).toEqual([
      pizzeria,
    ]);
  });

  it('searchesAndFiltersByCuisine', () => {
    const trattoria = restaurant({ id: 3, name: 'Белла Виста', cuisine: 'Итальянская' });

    expect(
      filterRestaurants([trattoria, sushiBar], { query: '', filterId: 'pizza', now: at(12) }),
    ).toEqual([trattoria]);
    expect(
      filterRestaurants([trattoria, sushiBar], {
        query: 'итальян',
        filterId: allFilterId,
        now: at(12),
      }),
    ).toEqual([trattoria]);
  });

  it('matchesCuisineChipByNameStem', () => {
    expect(filterRestaurants(all, { query: '', filterId: 'pizza', now: at(12) })).toEqual([
      pizzeria,
    ]);
  });
});
