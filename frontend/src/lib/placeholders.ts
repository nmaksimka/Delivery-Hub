export function placeholderEta(restaurantId: number) {
  const from = 25 + (restaurantId % 4) * 5;
  return `${from}–${from + 10} мин`;
}
