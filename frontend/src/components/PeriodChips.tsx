import { periodOptions } from '../lib/analyticsPeriod';
import { Chips } from './Chips';

type PeriodChipsProps = {
  days: number;
  onSelect: (days: number) => void;
};

export function PeriodChips({ days, onSelect }: PeriodChipsProps) {
  return (
    <Chips
      label="Период"
      options={periodOptions.map((option) => ({ id: String(option.days), label: option.label }))}
      activeId={String(days)}
      onSelect={(id) => {
        onSelect(Number(id));
      }}
    />
  );
}
