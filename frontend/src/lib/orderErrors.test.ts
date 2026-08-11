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

  it('explainsEveryPromoCodeRejection', () => {
    expect(
      messageFor(new ApiError(404, 'x', 'Promo code NOPE was not found', 'PROMO_NOT_FOUND')),
    ).toBe('Такого промокода нет, проверьте написание');
    expect(messageFor(conflict('Promo code A is not active', 'PROMO_INACTIVE'))).toBe(
      'Этот промокод больше не действует',
    );
    expect(messageFor(conflict('Promo code A is not valid yet', 'PROMO_NOT_STARTED'))).toBe(
      'Этот промокод ещё не начал действовать',
    );
    expect(messageFor(conflict('Promo code A has expired', 'PROMO_EXPIRED'))).toBe(
      'Срок действия промокода истёк',
    );
    expect(messageFor(conflict('Promo code A is used up', 'PROMO_LIMIT_REACHED'))).toBe(
      'Промокод уже использовали максимальное число раз',
    );
    expect(
      messageFor(conflict('Promo code A is valid only for restaurant 3', 'PROMO_OTHER_RESTAURANT')),
    ).toBe('Этот промокод действует только в другом ресторане');
    expect(messageFor(conflict('Promo code A already exists', 'PROMO_CODE_EXISTS'))).toBe(
      'Промокод с таким названием уже есть',
    );
  });

  it('namesMinimumAmountAndPerUserLimitFromDetail', () => {
    expect(
      messageFor(conflict('Promo code A requires an order of at least 30.00', 'PROMO_MIN_AMOUNT')),
    ).toBe('Промокод действует на заказ от 30.00 ₽');
    expect(messageFor(conflict('Promo code A requires', 'PROMO_MIN_AMOUNT'))).toBe(
      'Сумма заказа слишком мала для этого промокода',
    );
    expect(
      messageFor(
        conflict('Promo code A can be used only 1 time(s) per user', 'PROMO_USER_LIMIT_REACHED'),
      ),
    ).toBe('Вы уже использовали этот промокод');
    expect(
      messageFor(
        conflict('Promo code A can be used only 3 time(s) per user', 'PROMO_USER_LIMIT_REACHED'),
      ),
    ).toBe('Вы исчерпали лимит использований этого промокода');
  });

  it('fallsBackToDetailTextWhenCodeIsMissing', () => {
    expect(messageFor(conflict('Restaurant 1 is not active'))).toBe(
      'Ресторан сейчас не принимает заказы',
    );
    expect(messageFor(conflict('Menu item with ID: 10 was not found in restaurant 1'))).toBe(
      '«Маргарита» больше нет в меню, уберите из корзины',
    );
  });

  it('explainsRepeatedOrderAttempts', () => {
    expect(messageFor(conflict('still processing', 'IDEMPOTENCY_IN_PROGRESS'))).toBe(
      'Заказ уже оформляется, подождите пару секунд',
    );
    expect(messageFor(new ApiError(422, 'x', 'reused', 'IDEMPOTENCY_KEY_REUSED'))).toBe(
      'Корзина изменилась во время оформления, нажмите «Оформить заказ» ещё раз',
    );
    expect(messageFor(new ApiError(400, 'x', 'invalid', 'IDEMPOTENCY_KEY_INVALID'))).toBe(
      'Не получилось отправить заказ, обновите страницу и попробуйте снова',
    );
    expect(messageFor(new ApiError(400, 'x', 'required', 'IDEMPOTENCY_KEY_REQUIRED'))).toBe(
      'Не получилось отправить заказ, обновите страницу и попробуйте снова',
    );
  });

  it('explainsTooLargeRequest', () => {
    expect(messageFor(new ApiError(413, 'x', 'too large', 'REQUEST_TOO_LARGE'))).toBe(
      'Слишком большой запрос, сократите комментарий или состав заказа',
    );
  });

  it('usesGenericConflictMessageForUnknownCode', () => {
    expect(messageFor(conflict('Something unexpected happened', 'SOMETHING_NEW'))).toBe(
      'Заказ не принят, обновите страницу и попробуйте ещё раз',
    );
  });
});
