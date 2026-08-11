import { describe, expect, it } from 'vitest';
import { canCancel, cancellationText, isFinalStatus, orderProgress } from './orderStatus';

function states(status: string) {
  return orderProgress(status).map((step) => step.state);
}

describe('orderProgress', () => {
  it('namesFiveSagaStepsInOrder', () => {
    expect(orderProgress('CREATED').map((step) => step.label)).toEqual([
      'Оплата',
      'Курьер',
      'Подтверждён',
      'В пути',
      'Доставлен',
    ]);
  });

  it('waitsForPaymentRightAfterCreation', () => {
    expect(states('CREATED')).toEqual(['current', 'pending', 'pending', 'pending', 'pending']);
  });

  it('marksReachedStepsDoneAndNextOneCurrent', () => {
    expect(states('PAYMENT_HELD')).toEqual(['done', 'current', 'pending', 'pending', 'pending']);
    expect(states('COURIER_ASSIGNED')).toEqual(['done', 'done', 'current', 'pending', 'pending']);
    expect(states('IN_DELIVERY')).toEqual(['done', 'done', 'done', 'done', 'current']);
  });

  it('completesEveryStepWhenDelivered', () => {
    expect(states('DELIVERED')).toEqual(['done', 'done', 'done', 'done', 'done']);
  });

  it('stopsWholeScaleWhenCancelled', () => {
    expect(states('CANCELLED')).toEqual(['stopped', 'stopped', 'stopped', 'stopped', 'stopped']);
  });

  it('treatsUnknownStatusAsJustCreated', () => {
    expect(states('SOMETHING_NEW')).toEqual(states('CREATED'));
  });
});

describe('order actions', () => {
  it('allowsCancelOnlyBeforeCourierLeaves', () => {
    expect(canCancel('CREATED')).toBe(true);
    expect(canCancel('CONFIRMED')).toBe(true);
    expect(canCancel('IN_DELIVERY')).toBe(false);
    expect(canCancel('DELIVERED')).toBe(false);
    expect(canCancel('CANCELLED')).toBe(false);
  });

  it('stopsPollingOnlyOnFinalStatuses', () => {
    expect(isFinalStatus('DELIVERED')).toBe(true);
    expect(isFinalStatus('CANCELLED')).toBe(true);
    expect(isFinalStatus('IN_DELIVERY')).toBe(false);
  });

  it('explainsCancellationReasonInRussian', () => {
    expect(cancellationText('COURIER_TIMEOUT')).toBe('Не нашли свободного курьера вовремя');
    expect(cancellationText('UNKNOWN')).toBe('Заказ отменён');
    expect(cancellationText(null)).toBe('Заказ отменён');
  });
});
