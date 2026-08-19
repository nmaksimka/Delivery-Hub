import { describe, expect, it } from 'vitest';
import { arrivalText, formatDistance } from './arrival';

function clockOf(iso: string) {
  return new Intl.DateTimeFormat('ru-RU', { hour: '2-digit', minute: '2-digit' }).format(
    new Date(iso),
  );
}

describe('arrivalText', () => {
  it('showsMinutesAndClockTime', () => {
    const iso = '2026-08-01T10:12:00Z';
    expect(arrivalText({ etaSeconds: 600, estimatedArrival: iso })).toBe(
      `Приедет через 10 минут, к ${clockOf(iso)}`,
    );
  });

  it('roundsPartialMinutesUp', () => {
    expect(arrivalText({ etaSeconds: 121, estimatedArrival: null })).toBe('Приедет через 3 минуты');
  });

  it('declinesMinuteWordCorrectly', () => {
    expect(arrivalText({ etaSeconds: 21 * 60, estimatedArrival: null })).toBe(
      'Приедет через 21 минуту',
    );
  });

  it('saysSoonWhenLessThanMinuteLeft', () => {
    expect(arrivalText({ etaSeconds: 30, estimatedArrival: null })).toBe('Приедет совсем скоро');
  });

  it('returnsNullWithoutEta', () => {
    expect(arrivalText({ etaSeconds: null, estimatedArrival: null })).toBeNull();
  });

  it('skipsClockForInvalidDate', () => {
    expect(arrivalText({ etaSeconds: 300, estimatedArrival: 'вчера' })).toBe(
      'Приедет через 5 минут',
    );
  });
});

describe('formatDistance', () => {
  it('formatsShortDistanceInMeters', () => {
    expect(formatDistance(347)).toBe('350 м');
  });

  it('formatsLongDistanceInKilometersWithComma', () => {
    expect(formatDistance(1260)).toBe('1,3 км');
  });

  it('neverShowsZeroMeters', () => {
    expect(formatDistance(2)).toBe('10 м');
  });
});
