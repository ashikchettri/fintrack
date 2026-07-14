/** Money + number formatting for the dashboard. */

/**
 * Format a signed amount as currency. Uses Intl so the symbol/grouping match
 * the currency (AUD → "$1,234.50"). Falls back to a plain number if the code
 * isn't a valid ISO 4217 currency.
 */
export function formatMoney(amount: number, currency: string | null | undefined): string {
  const code = currency ?? 'AUD';
  try {
    return new Intl.NumberFormat(undefined, {
      style: 'currency',
      currency: code,
      currencyDisplay: 'narrowSymbol',
    }).format(amount);
  } catch {
    return amount.toFixed(2);
  }
}

/** A 0–1 share as a whole-number percent, e.g. 0.6086 → "61%". */
export function formatPercent(share: number): string {
  return `${Math.round(share * 100)}%`;
}

/** "2026-07" → "Jul 2026" for month axis labels. */
export function formatMonth(month: string): string {
  const [year, m] = month.split('-').map(Number);
  if (!year || !m) return month;
  const date = new Date(year, m - 1, 1);
  return date.toLocaleDateString(undefined, { month: 'short', year: 'numeric' });
}

/** "2026-07-11" → a short, locale-aware date for table rows. */
export function formatDate(iso: string): string {
  const date = new Date(iso);
  return Number.isNaN(date.getTime())
    ? iso
    : date.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
}
