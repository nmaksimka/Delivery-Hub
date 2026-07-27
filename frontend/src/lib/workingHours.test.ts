import { describe, expect, it } from 'vitest';
import type { Restaurant } from '../api/restaurants';
import { closedBadge, isOpenAt, workingHoursLabel } from './workingHours';

function restaurant(overrides: Partial<Restaurant>): Restaurant {
  return {
    id: 1,
    name: 'Пицца на Ленина',
    address: 'пр. Ленина, 36',
    phone: null,
    cuisine: 'Итальянская',
    city: 'Томск',
    imageUrl: null,
    openTime: '10:00:00',
    closeTime: '22:00:00',
    active: true,
    ...overrides,
  };
}

function tomsk(hours: number, minutes = 0) {
  return new Date(Date.UTC(2026, 5, 15, hours - 7, minutes));
}

describe('isOpenAt', () => {
  it('followsDaytimeScheduleInTomskTime', () => {
    const place = restaurant({});

    expect(isOpenAt(place, tomsk(9, 59))).toBe(false);
    expect(isOpenAt(place, tomsk(10))).toBe(true);
    expect(isOpenAt(place, tomsk(22))).toBe(false);
  });

  it('usesTomskClockRegardlessOfUtcDate', () => {
    const place = restaurant({});

    expect(isOpenAt(place, new Date('2026-06-15T03:30:00Z'))).toBe(true);
    expect(isOpenAt(place, new Date('2026-06-15T15:30:00Z'))).toBe(false);
  });

  it('handlesScheduleThatCrossesMidnight', () => {
    const bar = restaurant({ openTime: '18:00:00', closeTime: '02:00:00' });

    expect(isOpenAt(bar, tomsk(1, 30))).toBe(true);
    expect(isOpenAt(bar, tomsk(12))).toBe(false);
  });

  it('treatsMissingScheduleAsAlwaysOpenButRespectsInactiveFlag', () => {
    expect(isOpenAt(restaurant({ openTime: null, closeTime: null }), tomsk(4))).toBe(true);
    expect(isOpenAt(restaurant({ active: false }), tomsk(12))).toBe(false);
  });
});

describe('closedBadge', () => {
  it('isEmptyWhileRestaurantIsOpen', () => {
    expect(closedBadge(restaurant({}), tomsk(12))).toBeNull();
  });

  it('showsOpeningTimeWhenClosed', () => {
    expect(closedBadge(restaurant({}), tomsk(23, 15))).toBe('Закрыто до 10:00');
    expect(closedBadge(restaurant({ openTime: '09:30:00' }), tomsk(7))).toBe('Закрыто до 09:30');
  });

  it('marksInactiveRestaurantSeparately', () => {
    expect(closedBadge(restaurant({ active: false }), tomsk(12))).toBe('Не принимает заказы');
  });
});

describe('workingHoursLabel', () => {
  it('formatsScheduleWithoutSeconds', () => {
    expect(workingHoursLabel(restaurant({}))).toBe('10:00–22:00');
    expect(workingHoursLabel(restaurant({ openTime: '00:00:00', closeTime: '00:00:00' }))).toBe(
      'Круглосуточно',
    );
    expect(workingHoursLabel(restaurant({ openTime: null }))).toBeNull();
  });
});
