import { ApiError, errorMessage } from '../api/http';

type ItemName = (menuItemId: number) => string | undefined;

type Rule = {
  pattern: RegExp;
  message: (match: RegExpMatchArray, itemName: ItemName) => string;
};

function dish(match: RegExpMatchArray, itemName: ItemName) {
  const name = itemName(Number(match[1]));
  return name ? `«${name}»` : 'Одно из блюд';
}

const rules: Rule[] = [
  {
    pattern: /^Restaurant \d+ is not active/,
    message: () => 'Ресторан сейчас не принимает заказы',
  },
  {
    pattern:
      /^Restaurant \d+ is closed now(?:, working hours: (\d{1,2}:\d{2})(?::\d{2})?-(\d{1,2}:\d{2}))?/,
    message: (match) =>
      match[1] && match[2]
        ? `Ресторан сейчас закрыт, заказы принимаются с ${match[1]} до ${match[2]}`
        : 'Ресторан сейчас закрыт, попробуйте позже',
  },
  {
    pattern: /^Menu item (\d+) is not available/,
    message: (match, itemName) =>
      `${dish(match, itemName)} — нет в наличии. Уберите из корзины и оформите заказ снова`,
  },
  {
    pattern: /^Quantity of menu item (\d+) exceeds (\d+)/,
    message: (match, itemName) =>
      `${dish(match, itemName)}: можно заказать не больше ${match[2] ?? ''} шт.`,
  },
  {
    pattern: /^Menu item with ID: (\d+) was not found/,
    message: (match, itemName) => `${dish(match, itemName)} больше нет в меню, уберите из корзины`,
  },
  {
    pattern: /^The record was changed by another request/,
    message: () => 'Данные успели измениться, попробуйте ещё раз',
  },
];

export function orderErrorMessage(error: unknown, itemName: ItemName) {
  if (error instanceof ApiError && error.detail) {
    for (const rule of rules) {
      const match = error.detail.match(rule.pattern);
      if (match) {
        return rule.message(match, itemName);
      }
    }
    if (error.status === 409) {
      return 'Заказ не принят, обновите страницу и попробуйте ещё раз';
    }
  }
  return errorMessage(error);
}
