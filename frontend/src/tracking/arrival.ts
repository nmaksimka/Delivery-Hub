import { plural } from '../lib/format';
import type { TrackingMessage } from './message';

const clockFormat = new Intl.DateTimeFormat('ru-RU', { hour: '2-digit', minute: '2-digit' });

export function formatDistance(meters: number) {
  if (meters < 1000) {
    return `${Math.max(10, Math.round(meters / 10) * 10)} м`;
  }
  return `${(meters / 1000).toFixed(1).replace('.', ',')} км`;
}

export function arrivalText(message: Pick<TrackingMessage, 'etaSeconds' | 'estimatedArrival'>) {
  const { etaSeconds, estimatedArrival } = message;
  if (etaSeconds === null) {
    return null;
  }
  const minutes = Math.ceil(etaSeconds / 60);
  const clock = estimatedArrival ? new Date(estimatedArrival) : null;
  const at = clock && !Number.isNaN(clock.getTime()) ? clockFormat.format(clock) : null;
  if (minutes <= 1) {
    return at ? `Приедет совсем скоро, к ${at}` : 'Приедет совсем скоро';
  }
  const relative = `Приедет через ${minutes} ${plural(minutes, 'минуту', 'минуты', 'минут')}`;
  return at ? `${relative}, к ${at}` : relative;
}
