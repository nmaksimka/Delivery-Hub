export type StatusTone = 'neutral' | 'active' | 'done' | 'cancelled';

type StatusView = {
  label: string;
  tone: StatusTone;
};

const known: Record<string, StatusView> = {
  CREATED: { label: 'Ждёт оплаты', tone: 'neutral' },
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

const flow = [
  'CREATED',
  'PAYMENT_HELD',
  'COURIER_ASSIGNED',
  'CONFIRMED',
  'IN_DELIVERY',
  'DELIVERED',
] as const;

const stepLabels = ['Оплата', 'Курьер', 'Подтверждён', 'В пути', 'Доставлен'];

export type StepState = 'done' | 'current' | 'pending' | 'stopped';

export type ProgressStep = {
  label: string;
  state: StepState;
};

export function isFinalStatus(status: string) {
  return status === 'DELIVERED' || status === 'CANCELLED';
}

export function canCancel(status: string) {
  return !isFinalStatus(status) && status !== 'IN_DELIVERY';
}

export function orderProgress(status: string): ProgressStep[] {
  if (status === 'CANCELLED') {
    return stepLabels.map((label) => ({ label, state: 'stopped' }));
  }
  const reached = Math.max(flow.indexOf(status as (typeof flow)[number]), 0);
  return stepLabels.map((label, index) => {
    const step = index + 1;
    if (step <= reached) {
      return { label, state: 'done' };
    }
    return { label, state: step === reached + 1 ? 'current' : 'pending' };
  });
}

const cancellationReasons: Record<string, string> = {
  CUSTOMER: 'Вы отменили заказ',
  ADMIN: 'Заказ отменила поддержка',
  PAYMENT_REJECTED: 'Оплата не прошла: на кошельке не хватило денег или платёж отклонён',
  PAYMENT_TIMEOUT: 'Оплата не подтвердилась вовремя',
  DELIVERY_REJECTED: 'Служба доставки не смогла принять заказ',
  DELIVERY_CANCELLED: 'Доставку отменили',
  COURIER_TIMEOUT: 'Не нашли свободного курьера вовремя',
  CONFIRMATION_TIMEOUT: 'Ресторан не подтвердил заказ вовремя',
};

export function cancellationText(reason: string | null) {
  return (reason && cancellationReasons[reason]) ?? 'Заказ отменён';
}
