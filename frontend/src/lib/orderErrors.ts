import { ApiError, errorMessage } from '../api/http';

type ItemName = (menuItemId: number) => string | undefined;

const workingHoursPattern = /working hours: (\d{1,2}:\d{2})(?::\d{2})?-(\d{1,2}:\d{2})/;
const menuItemIdPattern = /(?:Menu item|menu item|ID:) (\d+)/;
const limitPattern = /exceeds (\d+)/;
const minAmountPattern = /at least (\d+(?:\.\d+)?)/;
const perUserLimitPattern = /only (\d+) time/;

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
    case 'PROMO_NOT_FOUND':
      return 'Такого промокода нет, проверьте написание';
    case 'PROMO_INACTIVE':
      return 'Этот промокод больше не действует';
    case 'PROMO_NOT_STARTED':
      return 'Этот промокод ещё не начал действовать';
    case 'PROMO_EXPIRED':
      return 'Срок действия промокода истёк';
    case 'PROMO_MIN_AMOUNT': {
      const amount = detail.match(minAmountPattern)?.[1];
      return amount
        ? `Промокод действует на заказ от ${amount} ₽`
        : 'Сумма заказа слишком мала для этого промокода';
    }
    case 'PROMO_LIMIT_REACHED':
      return 'Промокод уже использовали максимальное число раз';
    case 'PROMO_USER_LIMIT_REACHED': {
      const times = detail.match(perUserLimitPattern)?.[1];
      return times === '1'
        ? 'Вы уже использовали этот промокод'
        : 'Вы исчерпали лимит использований этого промокода';
    }
    case 'PROMO_OTHER_RESTAURANT':
      return 'Этот промокод действует только в другом ресторане';
    case 'PROMO_CODE_EXISTS':
      return 'Промокод с таким названием уже есть';
    case 'INVALID_STATUS_TRANSITION':
      return 'Заказ уже нельзя отменить: курьер в пути или заказ завершён';
    case 'RESTAURANT_UNAVAILABLE':
      return 'Не получилось связаться с рестораном, попробуйте через минуту';
    case 'CONCURRENT_UPDATE':
      return 'Данные успели измениться, попробуйте ещё раз';
    case 'IDEMPOTENCY_IN_PROGRESS':
      return 'Заказ уже оформляется, подождите пару секунд';
    case 'IDEMPOTENCY_KEY_REUSED':
      return 'Корзина изменилась во время оформления, нажмите «Оформить заказ» ещё раз';
    case 'IDEMPOTENCY_KEY_INVALID':
    case 'IDEMPOTENCY_KEY_REQUIRED':
      return 'Не получилось отправить заказ, обновите страницу и попробуйте снова';
    case 'ADDRESS_NOT_FOUND':
      return 'Этого адреса уже нет, выберите другой';
    case 'ADDRESS_NOT_GEOCODED':
      return 'Адрес ещё не нашёлся на карте — попробуйте через пару минут или выберите другой';
    case 'OUT_OF_DELIVERY_ZONE':
      return 'Ресторан не доставляет по этому адресу — он вне зоны доставки';
    case 'RESTAURANT_LOCATION_UNKNOWN':
      return 'Не знаем, где находится ресторан, поэтому не можем рассчитать доставку';
    case 'USER_SERVICE_UNAVAILABLE':
    case 'DELIVERY_UNAVAILABLE':
      return 'Не получилось рассчитать доставку, попробуйте через минуту';
    case 'REQUEST_TOO_LARGE':
      return 'Слишком большой запрос, сократите комментарий или состав заказа';
    default:
      return null;
  }
}

export function promoProblemMessage(code: string) {
  return messageByCode(code, '', () => undefined) ?? 'Промокод сейчас не действует';
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
