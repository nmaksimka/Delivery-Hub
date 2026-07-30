import { describe, expect, it } from 'vitest';
import { ApiError } from '../api/http';
import { orderErrorMessage } from './orderErrors';

const names: Record<number, string> = { 10: 'Маргарита' };

function conflict(detail: string) {
  return new ApiError(409, 'Такая запись уже существует', detail);
}

function messageFor(error: unknown) {
  return orderErrorMessage(error, (menuItemId) => names[menuItemId]);
}

describe('orderErrorMessage', () => {
  it('explainsClosedRestaurantWithWorkingHours', () => {
    expect(
      messageFor(conflict('Restaurant 1 is closed now, working hours: 10:00-22:00 Asia/Tomsk')),
    ).toBe('Ресторан сейчас закрыт, заказы принимаются с 10:00 до 22:00');
    expect(messageFor(conflict('Restaurant 1 is closed now'))).toBe(
      'Ресторан сейчас закрыт, попробуйте позже',
    );
  });

  it('namesDishThatIsOutOfStock', () => {
    expect(messageFor(conflict('Menu item 10 is not available'))).toBe(
      '«Маргарита» — нет в наличии. Уберите из корзины и оформите заказ снова',
    );
    expect(messageFor(conflict('Menu item 99 is not available'))).toMatch(/^Одно из блюд/);
  });

  it('mapsInactiveRestaurantAndQuantityLimit', () => {
    expect(messageFor(conflict('Restaurant 1 is not active'))).toBe(
      'Ресторан сейчас не принимает заказы',
    );
    expect(messageFor(conflict('Quantity of menu item 10 exceeds 50'))).toBe(
      '«Маргарита»: можно заказать не больше 50 шт.',
    );
  });

  it('fallsBackForUnknownConflictAndOtherErrors', () => {
    expect(messageFor(conflict('Something new'))).toBe(
      'Заказ не принят, обновите страницу и попробуйте ещё раз',
    );
    expect(messageFor(new ApiError(503, 'Сервис временно недоступен, попробуйте позже'))).toBe(
      'Сервис временно недоступен, попробуйте позже',
    );
  });
});
