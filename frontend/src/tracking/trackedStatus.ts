const tracked = new Set(['PAYMENT_HELD', 'COURIER_ASSIGNED', 'CONFIRMED', 'IN_DELIVERY']);

export function isTrackedStatus(status: string) {
  return tracked.has(status);
}
