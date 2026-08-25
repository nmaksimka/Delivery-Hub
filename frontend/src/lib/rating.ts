import { plural } from './format';

export const ratingValues = [1, 2, 3, 4, 5] as const;

export function formatRating(rating: number | null, count: number) {
  if (rating === null || count === 0) {
    return 'Нет оценок';
  }
  return rating.toLocaleString('ru-RU', { minimumFractionDigits: 1, maximumFractionDigits: 1 });
}

export function ratingCountLabel(count: number) {
  return `${count} ${plural(count, 'оценка', 'оценки', 'оценок')}`;
}
