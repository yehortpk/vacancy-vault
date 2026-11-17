import { Vacancy } from './type/vacancy.type';

/** Largest unit first, so a date is described in the coarsest unit it spans. */
const RELATIVE_TIME_UNITS: [Intl.RelativeTimeFormatUnit, number][] = [
  ['year', 365 * 24 * 3600],
  ['month', 30 * 24 * 3600],
  ['week', 7 * 24 * 3600],
  ['day', 24 * 3600],
  ['hour', 3600],
  ['minute', 60],
];
const relativeTimeFormat = new Intl.RelativeTimeFormat('en', { numeric: 'auto' });
const salaryFormat = new Intl.NumberFormat('en');

/**
 * Returns the salary as published, e.g. "1,500 – 3,000 USD / month", or null when neither bound
 * is known. The currency and period are added only when the source states them.
 */
export function formatSalary(vacancy: Vacancy): string | null {
  const range = formatSalaryRange(vacancy);
  if (range === null) {
    return null;
  }
  const currency = vacancy.salaryCurrency ? ` ${vacancy.salaryCurrency}` : '';
  const period = vacancy.salaryPeriod ? ` / ${vacancy.salaryPeriod.toLowerCase()}` : '';
  return range + currency + period;
}

function formatSalaryRange(vacancy: Vacancy): string | null {
  const { minSalary, maxSalary } = vacancy;
  if (minSalary != null && maxSalary != null) {
    return minSalary === maxSalary
      ? salaryFormat.format(minSalary)
      : `${salaryFormat.format(minSalary)} – ${salaryFormat.format(maxSalary)}`;
  }
  if (minSalary != null) {
    return `from ${salaryFormat.format(minSalary)}`;
  }
  if (maxSalary != null) {
    return `up to ${salaryFormat.format(maxSalary)}`;
  }
  return null;
}

/** Describes a timestamp relative to now, e.g. "3 hours ago" or "yesterday". */
export function describePostedAt(parsedAt: string): string {
  const elapsedSeconds = (Date.parse(parsedAt) - Date.now()) / 1000;
  for (const [unit, unitSeconds] of RELATIVE_TIME_UNITS) {
    if (Math.abs(elapsedSeconds) >= unitSeconds) {
      return relativeTimeFormat.format(Math.round(elapsedSeconds / unitSeconds), unit);
    }
  }
  return 'just now';
}
