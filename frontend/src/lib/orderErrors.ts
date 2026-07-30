import { ApiError, errorMessage } from '../api/http';

type ItemName = (menuItemId: number) => string | undefined;

const workingHoursPattern = /working hours: (\d{1,2}:\d{2})(?::\d{2})?-(\d{1,2}:\d{2})/;
const menuItemIdPattern = /(?:Menu item|menu item|ID:) (\d+)/;
const limitPattern = /exceeds (\d+)/;

const legacyCodes: [RegExp, string][] = [
  [/^Restaurant \d+ is not active/, 'RESTAURANT_INACTIVE'],
  [/^Restaurant \d+ is closed now/, 'RESTAURANT_CLOSED'],
  [/^Menu item \d+ is not available/, 'ITEM_UNAVAILABLE'],
  [/^Quantity of menu item \d+ exceeds/, 'QUANTITY_LIMIT'],
  [/^Menu item with ID: \d+ was not found/, 'ITEM_NOT_FOUND'],
  [/^The record was changed by another request/, 'CONCURRENT_UPDATE'],
];

function codeOf(error: ApiError) {
  if (error.code) {
    return error.code;
  }
  const detail = error.detail ?? '';
  return legacyCodes.find(([pattern]) => pattern.test(detail))?.[1] ?? null;
}

function dish(detail: string, itemName: ItemName) {
  const id = detail.match(menuItemIdPattern)?.[1];
  const name = id ? itemName(Number(id)) : undefined;
  return name ? `«${name}»` : 'Одно из блюд';
}

function closedMessage(detail: string) {
  const hours = detail.match(workingHoursPattern);
  return hours
    ? `Ресторан сейчас закрыт, заказы принимаются с ${hours[1] ?? ''} до ${hours[2] ?? ''}`
    : 'Ресторан сейчас закрыт, попробуйте позже';
}

function messageByCode(code: string, detail: string, itemName: ItemName): string | null {
  switch (code) {
    case 'RESTAURANT_INACTIVE':
      return 'Ресторан сейчас не принимает заказы';
    case 'RESTAURANT_CLOSED':
      return closedMessage(detail);
    case 'RESTAURANT_NOT_FOUND':
      return 'Этого ресторана больше нет';
    case 'ITEM_UNAVAILABLE':
      return `${dish(detail, itemName)} — нет в наличии. Уберите из корзины и оформите заказ снова`;
    case 'QUANTITY_LIMIT': {
      const limit = detail.match(limitPattern)?.[1];
      return limit
        ? `${dish(detail, itemName)}: можно заказать не больше ${limit} шт.`
        : `${dish(detail, itemName)}: слишком много порций`;
    }
    case 'ITEM_NOT_FOUND':
      return `${dish(detail, itemName)} больше нет в меню, уберите из корзины`;
    case 'CART_ITEM_NOT_FOUND':
      return 'Этой позиции уже нет в корзине';
    case 'CART_OTHER_RESTAURANT':
      return 'В корзине блюда из другого ресторана. Очистите её или замените содержимое';
    case 'CART_EMPTY':
      return 'Корзина пуста';
    case 'CART_TOO_MANY_LINES':
      return 'В корзине слишком много позиций';
    case 'CONCURRENT_UPDATE':
      return 'Данные успели измениться, попробуйте ещё раз';
    default:
      return null;
  }
}

export function orderErrorMessage(error: unknown, itemName: ItemName) {
  if (error instanceof ApiError) {
    const code = codeOf(error);
    const message = code ? messageByCode(code, error.detail ?? '', itemName) : null;
    if (message) {
      return message;
    }
    if (error.status === 409) {
      return 'Заказ не принят, обновите страницу и попробуйте ещё раз';
    }
  }
  return errorMessage(error);
}
