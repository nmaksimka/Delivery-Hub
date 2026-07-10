export type StatusTone = 'neutral' | 'active' | 'done' | 'cancelled';

type StatusView = {
  label: string;
  tone: StatusTone;
};

const known: Record<string, StatusView> = {
  CREATED: { label: 'Принят', tone: 'neutral' },
  CONFIRMED: { label: 'Готовится', tone: 'active' },
  IN_DELIVERY: { label: 'В пути', tone: 'active' },
  DELIVERED: { label: 'Доставлен', tone: 'done' },
  CANCELLED: { label: 'Отменён', tone: 'cancelled' },
};

export function statusView(status: string): StatusView {
  return known[status] ?? { label: 'Обрабатывается', tone: 'neutral' };
}
