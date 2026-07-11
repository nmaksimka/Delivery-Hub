import { describe, expect, it } from 'vitest';
import type { Restaurant } from '../api/restaurants';
import { allFilterId, filterRestaurants, isOpenAt, openFilterId } from './restaurantFilters';

function restaurant(overrides: Partial<Restaurant>): Restaurant {
  return {
    id: 1,
    name: 'Пицца на Ленина',
    address: 'пр. Ленина, 36',
    phone: null,
    openTime: '10:00:00',
    closeTime: '22:00:00',
    active: true,
    ...overrides,
  };
}

function at(hours: number, minutes = 0) {
  return new Date(2026, 5, 15, hours, minutes);
}

describe('isOpenAt', () => {
  it('followsDaytimeSchedule', () => {
    const place = restaurant({});

    expect(isOpenAt(place, at(9, 59))).toBe(false);
    expect(isOpenAt(place, at(10))).toBe(true);
    expect(isOpenAt(place, at(22))).toBe(false);
  });

  it('handlesScheduleThatCrossesMidnight', () => {
    const bar = restaurant({ openTime: '18:00:00', closeTime: '02:00:00' });

    expect(isOpenAt(bar, at(1, 30))).toBe(true);
    expect(isOpenAt(bar, at(12))).toBe(false);
  });

  it('treatsMissingScheduleAsAlwaysOpenButRespectsInactiveFlag', () => {
    expect(isOpenAt(restaurant({ openTime: null, closeTime: null }), at(4))).toBe(true);
    expect(isOpenAt(restaurant({ active: false }), at(12))).toBe(false);
  });
});

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

  it('matchesCuisineChipByNameStem', () => {
    expect(filterRestaurants(all, { query: '', filterId: 'pizza', now: at(12) })).toEqual([
      pizzeria,
    ]);
  });
});
