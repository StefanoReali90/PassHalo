import type { PassHaloEvent } from './types';

export function isEventBookable(event: Pick<PassHaloEvent, 'eventState' | 'endDateTime'>): boolean {
  if (event.eventState === 'FINISHED') return false;

  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Europe/Rome',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(new Date());
  const value = (type: string) => parts.find((part) => part.type === type)?.value ?? '';
  const nowInRome = `${value('year')}-${value('month')}-${value('day')}T${value('hour')}:${value('minute')}:${value('second')}`;
  return event.endDateTime.slice(0, 19) > nowInRome;
}
