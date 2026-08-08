export type StatusTone = 'neutral' | 'active' | 'done' | 'cancelled';

type StatusView = {
  label: string;
  tone: StatusTone;
};

const known: Record<string, StatusView> = {
  CREATED: { label: 'Принят', tone: 'neutral' },
  PAYMENT_HELD: { label: 'Оплачен, ищем курьера', tone: 'active' },
  COURIER_ASSIGNED: { label: 'Курьер назначен', tone: 'active' },
  CONFIRMED: { label: 'Готовится', tone: 'active' },
  IN_DELIVERY: { label: 'В пути', tone: 'active' },
  DELIVERED: { label: 'Доставлен', tone: 'done' },
  CANCELLED: { label: 'Отменён', tone: 'cancelled' },
};

export function statusView(status: string): StatusView {
  return known[status] ?? { label: 'Обрабатывается', tone: 'neutral' };
}
