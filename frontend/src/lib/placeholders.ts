export function placeholderRating(restaurantId: number) {
  const tenths = 42 + ((restaurantId * 7) % 8);
  return (tenths / 10).toLocaleString('ru-RU', { minimumFractionDigits: 1 });
}

export function placeholderEta(restaurantId: number) {
  const from = 25 + (restaurantId % 4) * 5;
  return `${from}–${from + 10} мин`;
}
