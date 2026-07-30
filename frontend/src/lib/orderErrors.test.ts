import { describe, expect, it } from 'vitest';
import { ApiError } from '../api/http';
import { orderErrorMessage } from './orderErrors';

const names: Record<number, string> = { 10: 'Маргарита' };

function conflict(detail: string, code: string | null = null) {
  return new ApiError(409, 'Такая запись уже существует', detail, code);
}

function messageFor(error: unknown) {
  return orderErrorMessage(error, (menuItemId) => names[menuItemId]);
}

describe('orderErrorMessage', () => {
  it('explainsClosedRestaurantWithWorkingHours', () => {
    expect(
      messageFor(
        conflict(
          'Restaurant 1 is closed now, working hours: 10:00-22:00 Asia/Tomsk',
          'RESTAURANT_CLOSED',
        ),
      ),
    ).toBe('Ресторан сейчас закрыт, заказы принимаются с 10:00 до 22:00');
    expect(messageFor(conflict('Restaurant 1 is closed now', 'RESTAURANT_CLOSED'))).toBe(
      'Ресторан сейчас закрыт, попробуйте позже',
    );
  });

  it('namesDishThatIsOutOfStock', () => {
    expect(messageFor(conflict('Menu item 10 is not available', 'ITEM_UNAVAILABLE'))).toBe(
      '«Маргарита» — нет в наличии. Уберите из корзины и оформите заказ снова',
    );
    expect(messageFor(conflict('Menu item 99 is not available', 'ITEM_UNAVAILABLE'))).toMatch(
      /^Одно из блюд/,
    );
  });

  it('mapsInactiveRestaurantAndQuantityLimit', () => {
    expect(messageFor(conflict('Restaurant 1 is not active', 'RESTAURANT_INACTIVE'))).toBe(
      'Ресторан сейчас не принимает заказы',
    );
    expect(messageFor(conflict('Quantity of menu item 10 exceeds 50', 'QUANTITY_LIMIT'))).toBe(
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

  it('choosesMessageByCodeEvenWhenDetailIsUnrelated', () => {
    expect(messageFor(conflict('anything', 'CART_OTHER_RESTAURANT'))).toMatch(/другого ресторана/);
    expect(messageFor(conflict('anything', 'CONCURRENT_UPDATE'))).toBe(
      'Данные успели измениться, попробуйте ещё раз',
    );
    expect(messageFor(new ApiError(404, 'Ничего не нашлось', 'x', 'CART_ITEM_NOT_FOUND'))).toBe(
      'Этой позиции уже нет в корзине',
    );
  });

  it('fallsBackToDetailTextWhenCodeIsMissing', () => {
    expect(messageFor(conflict('Restaurant 1 is not active'))).toBe(
      'Ресторан сейчас не принимает заказы',
    );
    expect(messageFor(conflict('Menu item with ID: 10 was not found in restaurant 1'))).toBe(
      '«Маргарита» больше нет в меню, уберите из корзины',
    );
  });

  it('usesGenericConflictMessageForUnknownCode', () => {
    expect(messageFor(conflict('Order 1 cannot change status', 'INVALID_STATUS_TRANSITION'))).toBe(
      'Заказ не принят, обновите страницу и попробуйте ещё раз',
    );
  });
});
