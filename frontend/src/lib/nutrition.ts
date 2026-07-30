import type { Nutrition } from '../api/restaurants';

export type NutritionFact = {
  label: string;
  value: string;
};

const amountFormat = new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 1 });

const nutriScoreGrades = ['A', 'B', 'C', 'D', 'E'];

export function nutritionFacts(nutrition: Nutrition | null): NutritionFact[] {
  if (!nutrition) {
    return [];
  }
  const facts: [string, number | null, string][] = [
    ['Ккал', nutrition.energyKcal, ''],
    ['Белки', nutrition.proteinGrams, ' г'],
    ['Жиры', nutrition.fatGrams, ' г'],
    ['Углеводы', nutrition.carbsGrams, ' г'],
  ];
  return facts.flatMap(([label, amount, unit]) =>
    amount === null ? [] : [{ label, value: `${amountFormat.format(amount)}${unit}` }],
  );
}

export function nutriScoreGrade(nutrition: Nutrition | null) {
  const grade = nutrition?.nutriScore?.trim().toUpperCase();
  return grade && nutriScoreGrades.includes(grade) ? grade : null;
}
