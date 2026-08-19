const baseDelayMs = 1000;
const maxDelayMs = 30000;

export function reconnectDelay(attempt: number) {
  return Math.min(maxDelayMs, baseDelayMs * 2 ** Math.max(0, attempt));
}
